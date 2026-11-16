import unittest
from sp500_probability_bins import aggregate, join_trades, bucket_index


def fill(symbol='A', reason='TP', exit_price=115, fee=.001):
    budget=100*(1+fee)
    proceeds=exit_price*(1-fee)
    return dict(symbol=symbol,decision='2025-01-02',entry='2025-01-03',exit='2025-01-06',
                entry_price=100,exit_price=exit_price,shares=1,budget=budget,
                proceeds=proceeds,pnl=proceeds-budget,reason=reason,fee=fee)


class ProbabilityBinTests(unittest.TestCase):
    def test_boundaries_and_invalid_probability(self):
        for p,index in [(0,0),(.099999,0),(.1,1),(.3,3),(.9,9),(1,9)]:
            self.assertEqual(bucket_index(p),index)
        for p in [-.01,1.01,float('nan'),float('inf')]:
            with self.assertRaises(ValueError):bucket_index(p)

    def test_rates_include_gap_stop_and_time_and_costs_once(self):
        fills=[fill(),fill('B','SL_GAP',90),fill('C','TIME',105)]
        pred={('2025-01-02',s):.32 for s in 'ABC'}
        trades=join_trades(fills,pred)
        result=aggregate(trades)
        row=result['bins'][3]
        self.assertEqual(row['count'],3)
        self.assertEqual((row['tpCount'],row['slCount'],row['timeCount']),(1,1,1))
        self.assertAlmostEqual(row['tpRatePct'],100/3)
        expected=sum((r['proceeds']/r['budget']-1)*100 for r in fills)/3
        self.assertAlmostEqual(row['meanNetReturnPct'],expected)
        self.assertEqual(row['meanProbabilityPct'],32)
        self.assertIsNone(result['bins'][0]['tpRatePct'])
        self.assertIsNone(result['bins'][0]['meanNetReturnPct'])
        self.assertEqual(result['summary']['count'],3)

    def test_missing_duplicate_and_bad_trade_fail_closed(self):
        with self.assertRaises(ValueError):join_trades([fill()],{})
        pred={('2025-01-02','A'):.2}
        with self.assertRaises(ValueError):join_trades([fill(),fill()],pred)
        for update in [dict(budget=0),dict(pnl=0),dict(reason='UNKNOWN'),dict(entry='2025-01-02')]:
            with self.assertRaises(ValueError):join_trades([dict(fill(),**update)],pred)

    def test_return_is_trade_mean_not_capital_weighted(self):
        trades=[dict(probability=.2,reason='TP',netReturnPct=15),
                dict(probability=.2,reason='SL',netReturnPct=-5)]
        self.assertEqual(aggregate(trades)['bins'][2]['meanNetReturnPct'],5)


if __name__=='__main__':unittest.main()
