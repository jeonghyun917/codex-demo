import unittest
from datetime import date,timedelta
from barrier_portfolio import portfolio, context_features


def row(symbol='A', **kw):
    return dict(symbol=symbol,date='2025-01-01',entry='2025-01-02',exit='2025-01-03',
                entry_price=100.,exit_price=110.,reason='TIME',**kw)


class PortfolioTests(unittest.TestCase):
    def test_cash_and_costs(self):
        prices={('A','2025-01-02'):{'open':100.,'close':100.},
                ('A','2025-01-03'):{'open':100.,'close':110.}}
        result=portfolio([row()],prices,['2025-01-02','2025-01-03'],{'2025-01-01':2})
        expected=50000+50000/1.001*1.1*.999
        self.assertAlmostEqual(result['ending_equity'],expected)
        self.assertGreater(result['average_cash_pct'],49)

    def test_empty_stays_cash(self):
        result=portfolio([],{},['2025-01-02','2025-01-03'],{'2025-01-01':2})
        self.assertEqual(result['total_return_pct'],0)
        self.assertEqual(result['average_cash_pct'],100)

    def test_marks_unrealized_loss_for_drawdown(self):
        prices={('A','2025-01-02'):{'open':100.,'close':90.},
                ('A','2025-01-03'):{'open':90.,'close':110.}}
        result=portfolio([row()],prices,['2025-01-02','2025-01-03'],{'2025-01-01':1})
        self.assertLess(result['max_drawdown_pct'],-10)
        self.assertGreater(result['total_return_pct'],9)

    def test_no_borrowing_when_old_position_uses_cash(self):
        a=row(); b=dict(a,symbol='B',date='2025-01-02',entry='2025-01-03',exit='2025-01-03')
        prices={(s,d):{'open':100.,'close':110.} for s in ('A','B')
                for d in ('2025-01-02','2025-01-03')}
        result=portfolio([a,b],prices,['2025-01-02','2025-01-03'],
                         {'2025-01-01':1,'2025-01-02':1})
        self.assertEqual(len(result['fills']),1)
        self.assertGreaterEqual(min(r['cash'] for r in result['equity']),0)

    def test_missing_mark_fails_instead_of_hiding_loss(self):
        with self.assertRaises(ValueError):
            portfolio([row()],{},['2025-01-02','2025-01-03'],{'2025-01-01':1})

    def test_open_gap_exit_can_fund_same_open_orders(self):
        a=dict(row(),reason='SL_GAP',exit_price=90.)
        b=dict(row(),symbol='B',date='2025-01-02',entry='2025-01-03')
        prices={(s,d):{'open':100.,'close':100.} for s in ('A','B')
                for d in ('2025-01-02','2025-01-03')}
        prices['A','2025-01-03']['open']=90.
        result=portfolio([a,b],prices,['2025-01-02','2025-01-03'],
                         {'2025-01-01':1,'2025-01-02':1})
        self.assertEqual(len(result['fills']),2)
        self.assertAlmostEqual(result['ending_equity'],100000*.9*.999/1.001*1.1*.999/1.001)

    def test_high_and_trend_use_only_past_dates(self):
        bars=[dict(date=str(date(2024,1,1)+timedelta(days=i)),close=100.,high=110.,source='x')
              for i in range(300)]
        benchmark=[dict(date=b['date'],level=100+i) for i,b in enumerate(bars)]
        decision=bars[250]['date']
        expected=context_features(bars,benchmark,decision)
        self.assertAlmostEqual(expected['high_ratio'],100*100/110)
        self.assertTrue(expected['trend50'])
        bars[251]['high']=99999
        benchmark[251]['level']=1
        self.assertEqual(expected,context_features(bars,benchmark,decision))


if __name__=='__main__':
    unittest.main()
