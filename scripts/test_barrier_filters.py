import unittest
from barrier_filters import accepts, mature_history, choose_rule, summarize


class FilterTests(unittest.TestCase):
    def test_inclusive_threshold_and_null(self):
        self.assertTrue(accepts({'signal':60,'er':.5},(60,.5)))
        self.assertFalse(accepts({'signal':60,'er':None},(60,0)))
        self.assertTrue(accepts({'signal':None,'er':None},(None,None)))

    def test_future_and_equal_day_outcomes_excluded(self):
        rows=[{'date':'2025-01-01','maturity':'2025-02-01'},
              {'date':'2025-02-01','maturity':'2025-03-01'}]
        self.assertEqual(mature_history(rows,'2025-03-01'),rows[:1])

    def test_low_sample_rule_cannot_win(self):
        rows=[dict(date='2025-%02d-01'%(i%6+1), maturity='2025-08-01',
                   signal=70 if i==0 else 40, er=1, net=15 if i==0 else -5,
                   reason='TP' if i==0 else 'SL',symbol=str(i),days=5,ambiguous=False)
              for i in range(60)]
        self.assertEqual(choose_rule(rows)[0],'NONE')

    def test_cash_slots_and_empty_not_zero_trade_return(self):
        rows=[dict(date='2025-01-01',symbol='A',net=10,reason='TP',days=2,ambiguous=False)]
        stats=summarize(rows,4)
        self.assertEqual(stats['mean_net'],10)
        self.assertEqual(stats['slot_mean_net'],2.5)
        self.assertEqual(summarize([],4)['mean_net'],None)
        self.assertEqual(summarize([],4)['slot_mean_net'],0)

    def test_rule_with_sufficient_support_can_be_chosen(self):
        rows=[dict(date='2025-%02d-01'%(i%6+1),maturity='2025-08-01',
                   signal=60 if i<60 else 40,er=.5,net=10 if i<60 else -5,
                   reason='TP' if i<60 else 'SL',symbol=str(i),days=5,ambiguous=False)
              for i in range(90)]
        name,eligible=choose_rule(rows)
        self.assertNotEqual(name,'NONE')
        self.assertTrue(all(r['n']>=50 and r['stop_pct']==0 for r in eligible))

    def test_changing_future_outcomes_cannot_change_selection(self):
        rows=[dict(date='2025-01-01',maturity='2025-02-01',symbol='A',net=-5,
                   reason='SL',days=5,ambiguous=False,signal=60,er=0),
              dict(date='2025-03-01',maturity='2025-04-01',symbol='B',net=15,
                   reason='TP',days=5,ambiguous=False,signal=60,er=0)]
        before=choose_rule(mature_history(rows,'2025-03-01'))
        rows[1]['net']=-99
        self.assertEqual(before,choose_rule(mature_history(rows,'2025-03-01')))


if __name__=='__main__':
    unittest.main()
