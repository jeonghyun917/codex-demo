import unittest
import numpy as np
import pandas as pd
from sp500_expected_return_shadow import mature_training, ranking_variants, fee_adjusted_return


class ExpectedReturnShadowTests(unittest.TestCase):
    def test_training_uses_only_full_maturity_before_month(self):
        frame=pd.DataFrame(dict(date=['2024-01-01']*5,session=[5,10,15,16,20],
            maturity=['2024-12-31','2025-01-01','2025-01-02','2024-12-01','2024-12-01'],
            netReturnPct=[1.,2.,3.,4.,np.nan]))
        self.assertEqual(mature_training(frame,'2025-01-01').session.tolist(),[5])

    def test_fee_conversion_includes_both_sides(self):
        original=100*(1.15*.999/1.001-1)
        self.assertAlmostEqual(fee_adjusted_return(original,.002),100*(1.15*.998/1.002-1))
        self.assertAlmostEqual(fee_adjusted_return(-5.18981018981019,.001),-5.18981018981019)

    def test_negative_scores_keep_existing_filters_and_do_not_backfill(self):
        frame=pd.DataFrame(dict(date=['2025-01-02']*10,symbol=list('ABCDEFGHIJ'),
            probability=[.9,.8,.7,.6,.5,.4,.3,.2,.1,.05],expectedReturnPct=[-8,-7,-1,-2,-3,-4,-5,-6,-9,-10],
            matchedProbability=[.1,.2,.3,.4,.5,.6,.7,.8,.9,.95],
            vol20=[.01,.01,.10,.01,.01,.01,.01,.01,.01,.01],index_ma50=[.01]*10))
        result=ranking_variants(frame)
        day='2025-01-02'
        self.assertEqual([r['symbol'] for r in result['PROBABILITY'][day]],['A','B'])
        self.assertEqual([r['symbol'] for r in result['EXPECTED_RETURN'][day]],['D'])
        self.assertEqual([r['symbol'] for r in result['EXPECTED_RETURN_FIXED_POOL'][day]],['B','A'])
        self.assertEqual([r['symbol'] for r in result['MATCHED_HGB_PROBABILITY'][day]],['J','I'])
        frame['index_ma50']=0
        self.assertTrue(all(not rows[day] for rows in ranking_variants(frame).values()))

    def test_bad_scores_are_rejected(self):
        frame=pd.DataFrame(dict(date=['d'],symbol=['A'],probability=[.2],expectedReturnPct=[np.nan],
            matchedProbability=[.2],vol20=[.01],index_ma50=[.01]))
        with self.assertRaises(ValueError):ranking_variants(frame)


if __name__=='__main__':unittest.main()
