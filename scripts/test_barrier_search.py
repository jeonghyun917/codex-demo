import unittest
from barrier_search import permits, choose, mature_before, grid


class SearchTests(unittest.TestCase):
    def test_deep_drawdown_inclusive(self):
        rule=dict(band='20P',vol=None,trend='ANY',signal=None)
        row=dict(high_ratio=80,vol20=2,trend50=True,trend200=False,signal=50)
        self.assertTrue(permits(row,rule))
        row['high_ratio']=80.01
        self.assertFalse(permits(row,rule))

    def test_combined_filters_are_and(self):
        rule=dict(band='20P',vol=3,trend='UP50',signal=55)
        row=dict(high_ratio=70,vol20=3,trend50=True,trend200=False,signal=55)
        self.assertTrue(permits(row,rule))
        row['trend50']=False
        self.assertFalse(permits(row,rule))

    def test_future_outcomes_excluded(self):
        rows=[dict(date='2025-01-01',maturity='2025-02-01'),
              dict(date='2025-02-01',maturity='2025-03-01')]
        self.assertEqual(mature_before(rows,'2025-03-01'),rows[:1])

    def test_return_leader_must_pass_risk_and_sample_constraints(self):
        base=dict(total_return_pct=10,max_drawdown_pct=-5,stop_pct=50,
                  funded_trades=100,decision_months=8)
        candidates={'BASE':base,'RISKY':dict(base,total_return_pct=30,max_drawdown_pct=-10),
                    'TINY':dict(base,total_return_pct=40,funded_trades=5),
                    'OK':dict(base,total_return_pct=12,max_drawdown_pct=-4)}
        self.assertEqual(choose(candidates,{'BASE':0,'RISKY':1,'TINY':1,'OK':1}), 'OK')

    def test_no_improvement_falls_back_to_baseline(self):
        base=dict(total_return_pct=10,max_drawdown_pct=-5,stop_pct=50,
                  funded_trades=100,decision_months=8)
        self.assertEqual(choose({'BASE':base,'LOW':dict(base,total_return_pct=9)},
                                {'BASE':0,'LOW':1}), 'BASE')

    def test_finite_unique_grid(self):
        rules=grid()
        self.assertEqual(len(rules),480)
        self.assertIn('BASE',rules)


if __name__=='__main__':
    unittest.main()
