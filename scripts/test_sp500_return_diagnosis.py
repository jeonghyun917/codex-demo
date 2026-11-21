import unittest
import pandas as pd
from diagnose_sp500_return_change import score, rankings, FEATURES


class DiagnosticTests(unittest.TestCase):
    def test_reconstructed_probability_preserves_rows(self):
        frame=pd.DataFrame([dict(symbol='A',date='2023-01-03',probability=.2,**dict.fromkeys(FEATURES,0.))])
        model=dict(mean=[0.]*7,scale=[1.]*7,coef=[1.]*7,intercept=0.,cal_coef=1.,cal_intercept=0.)
        actual=score(frame,{'2023-01':model})
        self.assertEqual(actual.iloc[0].probability,.5)
        self.assertEqual(frame.iloc[0].probability,.2)

    def test_rank_ties_and_top_twenty_percent(self):
        frame=pd.DataFrame([dict(symbol=s,date='2023-01-03',probability=.5) for s in 'FEDCBA'])
        actual=rankings(frame)['2023-01-03']
        self.assertEqual([r['symbol'] for r in actual],['A','B'])


if __name__=='__main__':unittest.main()
