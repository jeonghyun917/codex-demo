import unittest
import pandas as pd
from sp500_v9_shadow import matched_panel, rankings


class V9ShadowTest(unittest.TestCase):
    def setUp(self):
        self.p = pd.DataFrame([dict(date='2025-01-02', symbol=s, probability=10-i,
            vol20=v, index_ma50=1) for i,(s,v) in enumerate(zip('ABCDEFGHIJ',range(1,11)))])
        self.v = pd.DataFrame([dict(date='2025-01-02',symbol=s,v9_absolute=20-i,
            v9_excess=i,v9_control=i) for i,s in enumerate('ABCDE')])

    def test_exact_join_and_full_universe_vol_cap(self):
        p=matched_panel(self.p,self.v)
        self.assertEqual(len(p),5)
        self.assertTrue((p.vol_cap==7.75).all())
        later=self.v.assign(date='2025-01-03')
        self.assertTrue(matched_panel(self.p,later).empty)

    def test_top_before_filter_no_backfill(self):
        p=matched_panel(self.p,self.v)
        p.loc[p.symbol=='A','vol20']=9
        ranks=rankings(p,'probability')
        self.assertEqual(ranks['2025-01-02'],[])

    def test_negative_return_valid_and_tie_stable(self):
        p=matched_panel(self.p,self.v)
        p['v9_absolute']=-1
        self.assertEqual(rankings(p,'v9_absolute')['2025-01-02'][0]['symbol'],'A')
        p['index_ma50']=0
        self.assertEqual(rankings(p,'v9_absolute')['2025-01-02'],[])

    def test_duplicates_and_invalid_scores_rejected(self):
        with self.assertRaises(ValueError):matched_panel(self.p,pd.concat([self.v,self.v]))
        p=matched_panel(self.p,self.v);p.loc[0,'v9_absolute']=float('nan')
        with self.assertRaises(ValueError):rankings(p,'v9_absolute')

    def test_unresolved_corporate_names_excluded(self):
        p=self.p.copy();v=self.v.copy()
        p.loc[0,'symbol']='VMRK';v.loc[0,'symbol']='VMRK'
        self.assertNotIn('VMRK',matched_panel(p,v).symbol.tolist())


if __name__=='__main__':unittest.main()
