import unittest
from build_sp500_top20_latest import extend_membership, approve_new_symbol


class LatestDatasetTests(unittest.TestCase):
    def test_membership_effective_date_and_original_preservation(self):
        old = [dict(index_code='SP500', symbol=s, start_date='2020-01-01', end_date='2026-10-30',
                    source='old', provenance_ids='old', is_member='true') for s in ('A', 'KVUE')]
        rows = extend_membership(old, {'A', 'OKTA'}, {})
        self.assertEqual(old[0]['end_date'], '2026-10-30')
        self.assertEqual(next(r for r in rows if r['symbol'] == 'KVUE')['end_date'], '2026-11-03')
        self.assertEqual(next(r for r in rows if r['symbol'] == 'OKTA')['start_date'], '2026-11-04')
        self.assertEqual(next(r for r in rows if r['symbol'] == 'A')['end_date'], '2026-11-13')
        with self.assertRaises(ValueError):
            extend_membership(old, {'A', 'WRONG'}, {})

    def test_new_symbol_requires_valid_causal_warmup(self):
        bar = dict(open_price=10, high_price=11, low_price=9, close_price=10, volume=100)
        dates = [f'2026-08-{i:02d}' for i in range(1, 21)]
        with self.assertRaises(ValueError):
            approve_new_symbol({d:bar for d in dates}, dates)
        dates = [str(i) for i in range(63)]
        self.assertEqual(len(approve_new_symbol({d:bar for d in dates}, dates)), 63)
        broken = {d:bar for d in dates}
        broken[dates[-1]] = dict(bar, volume=0)
        with self.assertRaises(ValueError):
            approve_new_symbol(broken, dates)


if __name__ == '__main__':
    unittest.main()
