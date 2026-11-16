import unittest

from build_sp500_daily_dataset import price_flags, members_on


class DatasetTests(unittest.TestCase):
    def test_membership_endpoints_and_no_extension(self):
        rows = [dict(symbol='A', start_date='2021-01-04', end_date='2021-01-05', is_member='true')]
        self.assertEqual(set(members_on(rows, '2021-01-04')), {'A'})
        self.assertEqual(set(members_on(rows, '2021-01-05')), {'A'})
        self.assertEqual(members_on(rows, '2021-01-06'), {})

    def test_overlap_fails(self):
        r = dict(symbol='A', start_date='2021-01-04', end_date='2021-01-05', is_member='true')
        with self.assertRaises(ValueError):
            members_on([r, r], '2021-01-04')

    def test_price_quality(self):
        good = dict(open_price='10', high_price='11', low_price='9', close_price='10', volume='100')
        self.assertEqual(price_flags(good), [])
        self.assertIn('INVALID_OHLC', price_flags(dict(good, open_price='0')))
        self.assertIn('INVALID_OHLC', price_flags(dict(good, high_price='9')))
        self.assertIn('INVALID_OHLC', price_flags(dict(good, close_price='NaN')))
        self.assertIn('ZERO_VOLUME', price_flags(dict(good, volume='0')))
        self.assertIn('INVALID_VOLUME', price_flags(dict(good, volume='NULL')))
        self.assertEqual(price_flags(None), ['MISSING_BAR'])


if __name__ == '__main__':
    unittest.main()
