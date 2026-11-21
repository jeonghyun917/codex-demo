from datetime import datetime, timezone
import importlib.util
from pathlib import Path
import sys
import json
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scripts'))


class ValidationStatusTest(unittest.TestCase):
    def service(self):
        import quant_validation_status
        return quant_validation_status

    def fixture(self):
        cohort = dict(indexCode='DOW30', anchorSession='2026-10-30', entrySession='2026-11-02',
                      exitSession='2026-12-01', predictions=[dict(symbol='AAA')])
        manifest = dict(protocol=dict(calendar=[dict(session='2026-12-01', closeUtc='2026-12-01T21:00:00Z')]), cohorts=[cohort])
        report = dict(asOfUtc='2026-11-03T12:00:00Z', observations=[dict(indexCode='DOW30',
            anchorSession='2026-10-30', symbol='AAA', status='PENDING_OUTCOME')])
        diagnostic = dict(cohorts=[dict(indexCode='DOW30', anchorSession='2026-10-30', expectedNames=1,
                                     counts=dict(NON_TRADABLE_AT_ENTRY=1))])
        return [(manifest, report)], diagnostic

    def test_pending_never_becomes_zero_error_and_preserves_denominator(self):
        pairs, diagnostic = self.fixture()
        value = self.service().summarize('DOW30', pairs, diagnostic, '2026-11-03T12:00:00Z')
        self.assertEqual(value['status'], 'AWAITING_MATURITY')
        self.assertEqual(value['forward']['originalObservations'], 1)
        self.assertEqual(value['forward']['entryNonTradable'], 1)
        self.assertIsNone(value['forward']['rawMae'])
        self.assertFalse(value['promotionAllowed'])

    def test_mature_metrics_come_only_from_original_scored_observations(self):
        pairs, diagnostic = self.fixture()
        report = pairs[0][1]
        report['observations'][0].update(status='MATURE', predictedAbsolutePct=2., realizedAbsolutePct=2.,
                                        predictedExcessPct=1., realizedExcessPct=-1.)
        with self.assertRaisesRegex(ValueError, 'maturity'):
            self.service().summarize('DOW30', pairs, diagnostic, '2026-11-03T12:00:00Z')
        report['asOfUtc'] = '2026-12-02T12:00:00Z'
        value = self.service().summarize('DOW30', pairs, diagnostic, '2026-12-02T12:00:00Z')
        self.assertEqual(value['forward']['rawMae'], 0.)
        self.assertEqual(value['forward']['excessMae'], 2.)
        self.assertEqual(value['status'], 'MATURED')

    def test_duplicate_or_lost_names_are_rejected(self):
        pairs, diagnostic = self.fixture()
        pairs[0][1]['observations'] *= 2
        with self.assertRaises(ValueError):
            self.service().summarize('DOW30', pairs, diagnostic, '2026-11-03T12:00:00Z')

    def test_publication_is_rebuilt_and_not_merely_hash_linked(self):
        s = self.service()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            prices = root/'prices.csv'
            prices.write_text('symbol,date,close\nAAA,2026-11-02,100\n')
            evaluations, diagnostics = [], []
            for index in ('DOW30', 'SP500', 'NASDAQ100'):
                pairs, diag = self.fixture()
                manifest, report = pairs[0]
                manifest['cohorts'][0]['indexCode'] = index
                manifest['protocol'] = s.forward.seal(manifest['protocol'])
                s.forward.seal(manifest)
                report['observations'][0]['indexCode'] = index
                report['manifestContentSha256'] = manifest['contentSha256']
                report['pricesInput'] = dict(path=str(prices), sha256=s.forward.digest(prices.read_bytes()))
                s.forward.seal(report)
                mp, rp = root/(index+'-manifest.json'), root/(index+'-report.json')
                mp.write_text(json.dumps(manifest)); rp.write_text(json.dumps(report))
                evaluations.append(dict(manifest=str(mp), report=str(rp)))
                diag['cohorts'][0]['indexCode'] = index
                diagnostics += diag['cohorts']
            result = root/'result.json'
            result.write_text(json.dumps(dict(status='COMPLETED', promotion=False,
                evaluationAsOfUtc='2026-11-03T12:00:00Z', evaluations=evaluations)))
            (root/'eligibility-diagnostics.json').write_text(json.dumps(s.forward.seal(dict(
                asOfUtc='2026-11-03T12:00:00Z', cohorts=diagnostics))))
            publication = root/'publication.json'
            body = s.build(result)
            publication.write_text(json.dumps(body))
            s.verify_publication(publication)
            body['payloads'][0]['forward']['originalObservations'] = 999
            publication.write_text(json.dumps(body))
            with self.assertRaises(ValueError):
                s.verify_publication(publication)
            body.pop('contentSha256')
            s.forward.seal(body)
            publication.write_text(json.dumps(body))
            with self.assertRaisesRegex(ValueError, 'rebuild'):
                s.verify_publication(publication)
