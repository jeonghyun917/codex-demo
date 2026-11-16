import unittest
import pandas as pd
from sp500_risk_filters import filtered_rankings


def sample():
    return pd.DataFrame([dict(symbol=s,date='2023-01-03',probability=.9-i*.1,vol20=v,index_ma50=.01)
                         for i,(s,v) in enumerate(zip('ABCDE',[.1,.01,.02,.03,.04]))])


class RiskFilterTests(unittest.TestCase):
    def test_vol_filter_does_not_backfill_top_twenty(self):
        frame=sample()
        self.assertEqual(filtered_rankings(frame,'RAW')[0]['2023-01-03'][0]['symbol'],'A')
        self.assertEqual(filtered_rankings(frame,'VOL75')[0]['2023-01-03'],[])

    def test_trend_requires_strictly_above_ma(self):
        for level in (0.,-.01):
            frame=sample();frame['index_ma50']=level
            self.assertEqual(filtered_rankings(frame,'TREND50')[0]['2023-01-03'],[])
        self.assertEqual(len(filtered_rankings(sample(),'TREND50')[0]['2023-01-03']),1)

    def test_combined_and_causal(self):
        frame=sample();frame.loc[0,'vol20']=.02
        for policy in ('RAW','TREND50','VOL75','TREND50_VOL75'):
            current=filtered_rankings(frame,policy)[0]
            future=frame.assign(date='2023-01-04',vol20=999.,probability=.001)
            self.assertEqual(current['2023-01-03'],filtered_rankings(pd.concat([frame,future]),policy)[0]['2023-01-03'])
        self.assertEqual(filtered_rankings(frame,'TREND50_VOL75')[0]['2023-01-03'][0]['symbol'],'A')

    def test_excluded_and_invalid(self):
        frame=sample();frame.loc[0,'symbol']='FBIN'
        self.assertEqual(filtered_rankings(frame,'RAW')[0]['2023-01-03'][0]['symbol'],'B')
        frame.loc[1,'vol20']=float('nan')
        with self.assertRaises(ValueError):filtered_rankings(frame,'VOL75')


if __name__=='__main__':unittest.main()
