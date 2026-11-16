import unittest
from recover_sp500_daily_dataset import validate_chart, compare_bar


class RecoveryTests(unittest.TestCase):
    def test_pre_epoch_first_trade_date_on_windows(self):
        c=self.chart()
        c['meta']['firstTradeDate']=-252460800
        self.assertEqual(validate_chart('A',c)['2021-06-07']['close_price'],10)

    def chart(self):
        return dict(meta=dict(symbol='A', currency='USD', instrumentType='EQUITY',
                             exchangeTimezoneName='America/New_York', dataGranularity='1d', firstTradeDate=0),
                    timestamp=[1623072600], indicators=dict(quote=[dict(open=[10], high=[11], low=[9], close=[10], volume=[100])]))

    def test_exact_identity(self):
        c = self.chart()
        self.assertEqual(len(validate_chart('A', c)), 1)
        with self.assertRaises(ValueError):
            validate_chart('OTHER', c)
        c['meta']['currency'] = 'EUR'
        with self.assertRaises(ValueError):
            validate_chart('A', c)

    def test_duplicates_and_array_alignment(self):
        c = self.chart()
        c['timestamp'] *= 2
        with self.assertRaises(ValueError):
            validate_chart('A', c)
        for arr in c['indicators']['quote'][0].values():
            arr *= 2
        with self.assertRaises(ValueError):
            validate_chart('A', c)

    def test_no_adjusted_close_transformation(self):
        c = self.chart()
        c['indicators']['adjclose'] = [dict(adjclose=[5])]
        self.assertEqual(validate_chart('A', c)['2021-06-07']['close_price'], 10)

    def test_comparison_and_precision(self):
        a = dict(open_price=10, high_price=11, low_price=9, close_price=10, volume=100)
        self.assertTrue(compare_bar(a, dict(a, close_price=10.000001)))
        self.assertFalse(compare_bar(a, dict(a, close_price=5)))
        self.assertFalse(compare_bar(a, dict(a, volume=99)))


if __name__ == '__main__':
    unittest.main()
