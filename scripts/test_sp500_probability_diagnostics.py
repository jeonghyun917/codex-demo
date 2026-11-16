import unittest
import numpy as np
import pandas as pd
from diagnose_sp500_probability_quality import forward_outcomes, block_intervals, matched_comparison


class DiagnosticTests(unittest.TestCase):
    def test_barrier_order_and_full_maturity(self):
        bars=np.tile([100.,101.,99.,100.,1.],(23,1))
        clean=np.ones(23,dtype=bool)
        bars[1]=[100.,116.,94.,101.,1.]
        result=forward_outcomes(bars,clean,np.array([0]),.001)
        self.assertEqual(result['reason'][0],'SL')
        self.assertEqual(result['age'][0],1)
        self.assertAlmostEqual(result['netReturnPct'][0],100*(.95*.999/1.001-1))
        bars[1]=[100.,101.,99.,100.,1.];bars[2]=[90.,95.,89.,94.,1.]
        self.assertEqual(forward_outcomes(bars,clean,np.array([0]),.001)['reason'][0],'SL_GAP')
        clean[20]=False
        self.assertTrue(np.isnan(forward_outcomes(bars,clean,np.array([0]),.001)['actual'][0]))
        self.assertEqual(forward_outcomes(bars,clean,np.array([0]),.001,False)['reason'][0],'SL_GAP')
        clean[1]=False
        self.assertTrue(np.isnan(forward_outcomes(bars,clean,np.array([0]),.001,False)['actual'][0]))
        self.assertTrue(np.isnan(forward_outcomes(bars,clean,np.array([3]),.001)['actual'][0]))

    def test_target_and_timeout(self):
        bars=np.tile([100.,101.,99.,100.,1.],(21,1));clean=np.ones(21,dtype=bool)
        self.assertEqual(forward_outcomes(bars,clean,np.array([0]),.001)['reason'][0],'TIME')
        bars[2]=[120.,121.,119.,120.,1.]
        out=forward_outcomes(bars,clean,np.array([0]),.001)
        self.assertEqual(out['reason'][0],'TP')
        self.assertAlmostEqual(out['netReturnPct'][0],100*(1.15*.999/1.001-1))

    def test_bootstrap_includes_empty_days_and_is_reproducible(self):
        frame=pd.DataFrame(dict(date=['d0','d4'],probability=[.05,.25],actual=[1.,0.],netReturnPct=[15.,-5.]))
        result=block_intervals(frame,['d'+str(i) for i in range(10)],block=2,reps=200)
        repeat=block_intervals(frame,['d'+str(i) for i in range(10)],block=2,reps=200)
        self.assertEqual(result,repeat)
        self.assertEqual(result['bins'][0]['count'],1)
        self.assertEqual(result['bins'][1]['count'],0)
        self.assertIsNone(result['bins'][1]['tpCI'])

    def test_matched_comparison_restricts_to_overlap(self):
        frame=pd.DataFrame(dict(probability=[.05,.25,.05],actual=[1.,0.,0.],netReturnPct=[15.,-5.,-5.],
                                stratum=['same','same','low_only']))
        result=matched_comparison(frame,'stratum')
        self.assertEqual(result['sharedStrata'],1)
        self.assertEqual(result['matchedRows'],2)
        self.assertEqual(result['standardizedTpDifferencePp'],-100)
        self.assertEqual(result['standardizedReturnDifferencePp'],-20)


if __name__=='__main__':unittest.main()
