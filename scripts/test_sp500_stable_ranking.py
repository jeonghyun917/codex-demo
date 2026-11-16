import unittest
import pandas as pd
from sp500_stable_ranking import policy_rankings, EXCLUDED


def frame(rows):
    return pd.DataFrame([dict(date=d,session=i,symbol=s,probability=p) for d,i,s,p in rows])


class StableRankingTests(unittest.TestCase):
    def test_band_preserves_close_previous_priority(self):
        f=frame([('a',1,'A',.20),('a',1,'B',.19),('b',2,'A',.20),('b',2,'B',.201)])
        self.assertEqual(policy_rankings(f,'BAND05')['b'][0]['symbol'],'A')
        self.assertEqual(policy_rankings(f,'RAW')['b'][0]['symbol'],'B')

    def test_large_difference_overrides_priority(self):
        f=frame([('a',1,'A',.20),('a',1,'B',.19),('b',2,'A',.20),('b',2,'B',.21)])
        self.assertEqual(policy_rankings(f,'BAND05')['b'][0]['symbol'],'B')

    def test_ema_and_gap_reset(self):
        f=frame([('a',1,'A',.3),('b',2,'A',.0),('c',4,'A',.1)])
        r=policy_rankings(f,'EMA5')
        self.assertAlmostEqual(r['b'][0]['score'],.2)
        self.assertAlmostEqual(r['c'][0]['score'],.1)

    def test_future_cannot_change_past_and_exclusions(self):
        f=frame([('a',1,'A',.2),('a',1,'B',.2),('a',1,'FBIN',.99),('b',2,'B',.99)])
        for rule in ('RAW','EMA5','BAND05','EMA5_BAND05'):
            self.assertEqual(policy_rankings(f,rule)['a'],policy_rankings(f[f.date=='a'],rule)['a'])
            self.assertEqual(policy_rankings(f,rule)['a'][0]['symbol'],'A')
            self.assertNotIn('FBIN',[r['symbol'] for r in policy_rankings(f,rule)['a']])


if __name__=='__main__':unittest.main()
