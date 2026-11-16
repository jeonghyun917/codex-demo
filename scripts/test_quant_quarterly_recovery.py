"""Offline source-contract tests; fixtures never contain market outcomes."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).with_name('quant_quarterly_recovery.py')
if MODULE.exists():
    spec = importlib.util.spec_from_file_location('quarter_recovery', MODULE)
    recovery = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(recovery)
else:
    recovery = None


def obs(start='2019-04-01', end='2019-06-30', value=2, **changes):
    result = dict(start=start, end=end, val=value, accn='0000000001-19-000001',
                  filed='2019-07-31', fy=2019, fp='Q2', form='10-Q')
    result.update(changes)
    return result


def company(entries):
    facts = {}
    for concept, unit, observation in entries:
        facts.setdefault(concept, {'units': {}})['units'].setdefault(unit, []).append(observation)
    return {'cik': 1, 'entityName': 'Fixture', 'facts': {'us-gaap': facts}}


class SourceContractTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(recovery, 'Offline recovery implementation is missing')

    def extract(self, entries):
        return recovery.recover_company(company(entries), 'FIX', '0000000001', 'a' * 64)

    def test_exact_same_accession_period_proofs_and_direct_dollars(self):
        entries = [('EarningsPerShareDiluted', 'USD/shares', obs()),
                   ('Revenues', 'USD', obs(value=100)),
                   ('NetIncomeLoss', 'USD', obs(value=20)),
                   ('OperatingIncomeLoss', 'USD', obs(value=30)),
                   ('Revenues', 'USD', obs(value=999, accn='other')),
                   ('OperatingIncomeLoss', 'USD', obs(start='2019-01-01', value=500))]
        rows, stats = self.extract(entries)
        row = next(r for r in rows if r['accessionNumber'] == '0000000001-19-000001')
        self.assertEqual((row['revenue'], row['netIncome'], row['operatingIncome']), (100, 20, 30))
        self.assertEqual((row['fiscalPeriod'], row['fiscalYear'], row['periodDays']), ('Q2', 2019, 91))
        self.assertIsNone(row['id'])
        self.assertEqual(row['_epsConcept'], 'EarningsPerShareDiluted')
        proof = row['_proofs']['epsDiluted']
        self.assertEqual(proof['archiveSha256'], 'a' * 64)
        self.assertEqual(proof['observations'], [obs()])
        self.assertEqual(row['_phaseEvidence']['originalFP'], ['Q2'])
        self.assertEqual(stats['rows'], 2)

    def test_fiscal_phase_uses_filing_report_end_not_calendar_frame_or_instant(self):
        current = obs('2019-10-01', '2019-12-31', fp='Q1', fy=2020,
                      filed='2020-02-01', frame='CY2019Q4')
        previous = dict(current, start='2018-10-01', end='2018-12-31', val=1)
        wrong_phase = dict(current, start='2019-07-01', end='2019-09-30')
        instant = dict(current, start=None, end='2020-01-20')
        rows, _ = self.extract([('EarningsPerShareDiluted', 'USD/shares', x)
                                for x in [current, previous, wrong_phase]] +
                               [('CommonStockSharesOutstanding', 'shares', instant)])
        self.assertEqual([(r['endDate'], r['fiscalPeriod'], r['fiscalYear']) for r in rows],
                         [('2018-12-31', 'Q1', 2019), ('2019-12-31', 'Q1', 2020)])

    def test_current_report_anchor_comes_from_duration_income_not_eps_max(self):
        old = obs('2019-01-01', '2019-03-31')
        current = obs('2019-01-01', '2019-06-30', value=100)
        rows, stats = self.extract([('EarningsPerShareDiluted', 'USD/shares', old),
                                   ('Revenues', 'USD', current)])
        self.assertEqual(rows, [])
        self.assertGreater(stats['rejectedAmbiguousPhase'], 0)

    def test_nearby_same_year_end_is_not_current_report_quarter(self):
        current = obs()
        nearby = obs(end='2019-06-29', value=3)
        rows, _ = self.extract([('EarningsPerShareDiluted', 'USD/shares', x)
                                for x in [current, nearby]])
        self.assertEqual([r['endDate'] for r in rows], ['2019-06-30'])

    def test_53_week_fiscal_comparative_across_calendar_year_boundary(self):
        current = obs('2019-10-07', '2020-01-05', fp='Q1', fy=2020, filed='2020-02-01')
        previous = dict(current, start='2018-10-01', end='2018-12-30', val=1)
        rows, _ = self.extract([('EarningsPerShareDiluted', 'USD/shares', x)
                                for x in [current, previous]])
        self.assertEqual([(r['endDate'], r['fiscalYear']) for r in rows],
                         [('2018-12-30', 2019), ('2020-01-05', 2020)])

    def test_ytd_is_not_a_quarter_and_q4_is_never_eps_subtraction(self):
        entries = [('EarningsPerShareDiluted', 'USD/shares', obs('2019-01-01', '2019-06-30')),
                   ('EarningsPerShareDiluted', 'USD/shares', obs('2019-01-01', '2019-12-31',
                      fp='FY', form='10-K', filed='2020-02-01', value=10))]
        rows, stats = self.extract(entries)
        self.assertEqual([r['fiscalPeriod'] for r in rows], ['FY'])
        self.assertEqual(rows[0]['epsDiluted'], 10)
        self.assertGreater(stats['rejectedDuration'], 0)

    def test_direct_q4_requires_same_accession_true_annual_end(self):
        annual = obs('2019-01-01', '2019-12-31', fp='FY', form='10-K', filed='2020-02-01', value=10)
        fourth = dict(annual, start='2019-10-01', val=4)
        bogus = dict(annual, start='2019-07-01', end='2019-09-30', val=3)
        rows, stats = self.extract([('EarningsPerShareDiluted', 'USD/shares', x)
                                    for x in [annual, fourth, bogus]])
        self.assertEqual([(r['fiscalPeriod'], r['epsDiluted']) for r in rows], [('FY', 10), ('Q4', 4)])
        self.assertEqual(rows[1]['_phaseEvidence']['originalFP'], ['FY'])
        self.assertEqual(stats['directQ4Rows'], 1)
        isolated, _ = self.extract([('EarningsPerShareDiluted', 'USD/shares', fourth),
                                   ('Revenues', 'USD', dict(annual, accn='different'))])
        self.assertFalse(any(r['fiscalPeriod'] == 'Q4' for r in isolated))

    def test_conflicts_fail_closed_without_falling_back_to_alternate_concept(self):
        rows, stats = self.extract([('EarningsPerShareDiluted', 'USD/shares', obs()),
             ('RevenueFromContractWithCustomerExcludingAssessedTax', 'USD', obs(value=100)),
             ('RevenueFromContractWithCustomerExcludingAssessedTax', 'USD', obs(value=101)),
             ('Revenues', 'USD', obs(value=999)),
             ('EarningsPerShareDiluted', 'USD/shares', obs(value=3))])
        self.assertIsNone(rows[0]['revenue'])
        self.assertIsNone(rows[0]['epsDiluted'])
        self.assertEqual(stats['conflictingFields'], 2)
        self.assertEqual(len(rows[0]['_proofs']['revenue']['observations']), 2)

    def test_priority_is_one_source_concept_and_bank_identity_is_preserved(self):
        rows, _ = self.extract([('RevenuesNetOfInterestExpense', 'USD', obs(value=40)),
                               ('NetIncomeLossAvailableToCommonStockholdersBasic', 'USD', obs(value=9))])
        self.assertEqual(rows[0]['revenue'], 40)
        self.assertEqual(rows[0]['_fieldConcepts']['revenue'], 'RevenuesNetOfInterestExpense')
        self.assertIsNone(rows[0]['netIncome'])

    def test_dow_tax_inclusive_total_is_accepted_with_distinct_identity(self):
        data = company([('RevenueFromContractWithCustomerIncludingAssessedTax', 'USD', obs(value=100))])
        rows, _ = recovery.recover_company(data, 'DOW', '1', 'a' * 64)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['revenue'], 100)
        self.assertEqual(rows[0]['_fieldConcepts']['revenue'],
                         'RevenueFromContractWithCustomerIncludingAssessedTax')
        self.assertEqual(rows[0]['_proofs']['revenue']['observations'], [obs(value=100)])

    def test_dow_tax_exclusive_total_retains_priority_over_inclusive_total(self):
        data = company([('RevenueFromContractWithCustomerIncludingAssessedTax', 'USD', obs(value=110)),
                        ('RevenueFromContractWithCustomerExcludingAssessedTax', 'USD', obs(value=100))])
        rows, _ = recovery.recover_company(data, 'DOW', '1', 'a' * 64)
        self.assertEqual(rows[0]['revenue'], 100)
        self.assertEqual(rows[0]['_fieldConcepts']['revenue'],
                         'RevenueFromContractWithCustomerExcludingAssessedTax')

    def test_new_dow_total_does_not_authorize_components_or_other_issuer_fallback(self):
        data = company([('EarningsPerShareDiluted', 'USD/shares', obs()),
                        ('RevenueFromContractWithCustomerIncludingAssessedTax', 'USD', obs(value=100)),
                        ('SalesRevenueGoodsNet', 'USD', obs(value=60)),
                        ('SalesRevenueServicesNet', 'USD', obs(value=40))])
        rows, _ = recovery.recover_company(data, 'FIX', '1', 'a' * 64)
        self.assertIsNone(rows[0]['revenue'])

    def test_boundaries_forms_units_and_diluted_only(self):
        invalid = [obs(start='2012-12-31'), obs(end='2019-07-31'), obs(filed='2020-08-29'),
                   obs(form='8-K'), obs(start='2019-07-01', end='2019-06-30')]
        rows, _ = self.extract([('EarningsPerShareDiluted', 'USD/shares', x) for x in invalid] +
                              [('EarningsPerShareBasic', 'USD/shares', obs()),
                               ('EarningsPerShareDiluted', 'EUR/shares', obs()),
                               ('Revenues', 'EUR', obs())])
        self.assertEqual(rows, [])

    def test_conflicting_reported_phase_rejects_filing(self):
        rows, stats = self.extract([('EarningsPerShareDiluted', 'USD/shares', obs()),
                                   ('Revenues', 'USD', obs(fp='Q3'))])
        self.assertEqual(rows, [])
        self.assertGreater(stats['rejectedAmbiguousPhase'], 0)

    def test_issuer_and_original_archive_hash_are_enforced(self):
        with self.assertRaisesRegex(ValueError, 'CIK'):
            recovery.recover_company(company([]), 'FIX', '2', 'a' * 64)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'original.json'
            path.write_text(json.dumps(company([])), encoding='utf-8')
            record = {'archivePath': str(path), 'sha256': '0' * 64, 'cik': '1', 'symbol': 'FIX'}
            with self.assertRaisesRegex(ValueError, 'SHA'):
                recovery.load_verified_archive(record)
            record['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
            self.assertEqual(recovery.load_verified_archive(record)['cik'], 1)

    def test_existing_output_is_refused_before_any_input_read_or_write(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / 'sealed-pack'
            output.mkdir()
            sentinel = output / 'receipt.json'
            sentinel.write_bytes(b'original sealed receipt\n')
            error = None
            try:
                recovery.prepare(root / 'absent-input-root', output)
            except OSError as observed:
                error = observed
            self.assertIsInstance(error, FileExistsError)
            self.assertEqual(sentinel.read_bytes(), b'original sealed receipt\n')
            self.assertEqual([p.name for p in output.iterdir()], ['receipt.json'])


if __name__ == '__main__':
    unittest.main()
