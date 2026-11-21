"""Focused offline contract checks for the evidence-only recovery audit."""
import hashlib
import json
import unittest
from sp500_v9_action_recovery_audit import OUT, ROOT, TARGET, digest, ohlc_equal, rows


class RecoveryAuditTests(unittest.TestCase):
    def test_exact_target_coverage(self):
        audit = list(rows(OUT / 'symbol_audit.csv'))
        self.assertEqual(len(audit), 55)
        self.assertEqual({r['symbol'] for r in audit}, {r['symbol'] for r in rows(TARGET)})

    def test_no_false_certification_or_multipliers(self):
        for row in rows(OUT / 'symbol_audit.csv'):
            self.assertEqual(row['price_basis_certified'], 'False')
            self.assertTrue(row['blockers'])
        for row in rows(OUT / 'vendor_split_entries.csv'):
            self.assertEqual(row['price_multiplier'], '')
            self.assertEqual(row['decision'], 'NO_ADJUSTMENT_AUTHORIZED')

    def test_source_integrity_and_inputs_unchanged(self):
        manifest = json.loads((OUT / 'verification.json').read_text())
        for path, expected in manifest['input_sha256'].items():
            self.assertEqual(digest(ROOT / path), expected, path)
        for item in json.loads((OUT / 'source_evidence.json').read_text()):
            self.assertEqual(hashlib.sha256(item['summary'].encode()).hexdigest(), item['summary_sha256'])
            self.assertTrue(item['url'].startswith('https://'))
            self.assertIsNone(item['price_multiplier'])

    def test_economic_classification(self):
        events = {r['symbol']: r for r in rows(OUT / 'vendor_split_entries.csv')}
        self.assertEqual(len(events), 11)
        for symbol in ('CRWD', 'CVNA', 'GOOG', 'GOOGL', 'MKC', 'TTD'):
            self.assertEqual(events[symbol]['legal_kinds'], 'LITERAL_SHARE_SPLIT')
        for symbol in ('BWA', 'DELL', 'ILMN', 'LEN'):
            self.assertEqual(events[symbol]['legal_kinds'], 'SPINOFF_PARENT')
        self.assertEqual(events['MTCH']['legal_kinds'], 'COMPLEX_SEPARATION')

    def test_all_ohlc_fields_and_missing_values(self):
        row = dict(open_price='10', high_price='12', low_price='9', close_price='11')
        quote = dict(open=[10], high=[12], low=[9], close=[11])
        self.assertTrue(ohlc_equal(row, quote, 0))
        for field in quote:
            changed = dict(quote, **{field: [99]})
            self.assertFalse(ohlc_equal(row, changed, 0))
        self.assertFalse(ohlc_equal(row, dict(quote, close=[None]), 0))

    def test_when_issued_separation_and_ticker_boundary(self):
        audit = {r['symbol']: r for r in rows(OUT / 'symbol_audit.csv')}
        self.assertEqual(audit['FDXF']['pre_regular_way_rows'], '3')
        self.assertEqual(audit['HONA']['pre_regular_way_rows'], '9')
        self.assertIn('WHEN_ISSUED', audit['FDXF']['blockers'])
        self.assertIn('OLD_TICKER_ROWS_AFTER_RENAME', audit['SATS']['blockers'])

    def test_reused_sats_and_p_identity(self):
        identities = {r['symbol']: r for r in rows(OUT / 'identity_audit.csv')}
        self.assertEqual(len(identities), 55)
        self.assertEqual(identities['SATS']['current_ticker_cik'], '1920406')
        self.assertEqual(identities['SATS']['target_interval_cik'], '1415404')
        self.assertEqual(identities['P']['target_interval_cik'], '1474432')
        self.assertEqual(identities['P']['status'], 'PSTG_TO_P_SAME_ISSUER_CONFIRMED')


if __name__ == '__main__':
    unittest.main()
