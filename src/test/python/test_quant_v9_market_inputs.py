"""Catches future/current share substitution, EPS denominators, conflicts and ADR mixups."""
import importlib.util,sys,unittest
from pathlib import Path
from decimal import Decimal

PATH=Path(__file__).resolve().parents[3]/'scripts/quant_v9_market_inputs.py'
sys.path.insert(0,str(PATH.parent))

class V9MarketInputsTest(unittest.TestCase):
    def module(self):
        self.assertTrue(PATH.exists(),'Missing historical-share input selector')
        spec=importlib.util.spec_from_file_location('v9_market_inputs',PATH)
        m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
    def fixture(self):
        r=dict(end='2024-04-26',filed='2024-05-01',accn='q1',form='10-Q',val=100)
        return dict(cik=1013462,facts={'dei':{'EntityCommonStockSharesOutstanding':{'units':{'shares':[r]}}},
            'us-gaap':{'WeightedAverageNumberOfDilutedSharesOutstanding':{'units':{'shares':[dict(r,val=999)]}},
                       'CommonStockSharesIssued':{'units':{'shares':[dict(r,val=888)]}}}})
    def test_only_actual_shares_and_exact_issuer(self):
        m=self.module();out=m.xbrl_share_rows(self.fixture(),'ANSS')
        self.assertEqual([100],[r['totalCommonShares'] for r in out])
        with self.assertRaises(ValueError):m.xbrl_share_rows(self.fixture(),'ILMN')
    def test_same_day_and_later_filing_cannot_replace_known_count(self):
        m=self.module();out=m.xbrl_share_rows(self.fixture(),'ANSS')
        out += [dict(out[0],measurementDate='2024-07-26',filedDate='2024-07-31',totalCommonShares=200),
                dict(out[0],measurementDate='2024-04-26',filedDate='2025-01-01',totalCommonShares=500)]
        self.assertEqual(100,m.select_share(out,'ANSS','2024-07-31')['totalCommonShares'])
        self.assertEqual(200,m.select_share(out,'ANSS','2024-08-30')['totalCommonShares'])
    def test_same_accession_conflict_nonfinite_or_fractional_count_rejected(self):
        m=self.module();p=self.fixture();rr=p['facts']['dei']['EntityCommonStockSharesOutstanding']['units']['shares']
        rr.append(dict(rr[0],val=101))
        with self.assertRaises(ValueError):m.xbrl_share_rows(p,'ANSS')
        for value in ('NaN',1.5,0,-1):
            p=self.fixture();p['facts']['dei']['EntityCommonStockSharesOutstanding']['units']['shares'][0]['val']=value
            with self.assertRaises(ValueError):m.xbrl_share_rows(p,'ANSS')
    def test_wrong_units_and_weighted_average_not_fallbacks(self):
        m=self.module();p=self.fixture();r=p['facts']['dei']['EntityCommonStockSharesOutstanding']['units'].pop('shares')
        p['facts']['dei']['EntityCommonStockSharesOutstanding']['units']['USD']=r
        self.assertEqual([],m.xbrl_share_rows(p,'ANSS'))
    def test_filing_scope_excludes_irrelevant_zero_history_but_not_invalid_in_scope(self):
        m=self.module();p=self.fixture();rows=p['facts']['dei']['EntityCommonStockSharesOutstanding']['units']['shares']
        rows.extend([dict(rows[0],end='2012-12-31',filed='2014-02-27',accn='old',val=0),
                     dict(rows[0],filed='2025-05-30',accn='future',val=0)])
        result=m.xbrl_share_rows(p,'ANSS',filed_from='2023-01-01',filed_before='2025-05-30')
        self.assertEqual([100],[r['totalCommonShares'] for r in result])
        rows.append(dict(rows[0],filed='2024-05-02',accn='invalid',val=0))
        with self.assertRaises(ValueError):
            m.xbrl_share_rows(p,'ANSS',filed_from='2023-01-01',filed_before='2025-05-30')
    def test_dei_priority_retains_reported_precision_not_average(self):
        m=self.module();p=dict(cik=1709048,facts={'dei':{'EntityCommonStockSharesOutstanding':{'units':{'shares':[dict(end='2023-12-31',filed='2024-04-29',accn='fy',form='20-F',val=553548190)]}}},
            'ifrs-full':{'NumberOfSharesOutstanding':{'units':{'shares':[dict(end='2023-12-31',filed='2024-04-29',accn='fy',form='20-F',val=553548000)]}}}})
        out=m.xbrl_share_rows(p,'GFS')
        self.assertEqual(553548190,m.select_share(out,'GFS','2024-07-31')['totalCommonShares'])
    def test_azn_uses_ordinary_equivalent_not_unverified_ads_basis(self):
        m=self.module();share=dict(symbol='AZN',measurementDate='2023-12-31',filedDate='2024-02-20',totalCommonShares=10,unit='shares',sourceClassification='PRIMARY_TEXT_CURATED')
        self.assertEqual(Decimal('1461.80'),m.market_cap('AZN','146.18',share,'ORDINARY_EQUIVALENT_ARCHIVE'))
        with self.assertRaises(ValueError):m.market_cap('AZN','73.09',share,'ADS_UNKNOWN')
    def test_unknown_issuer_and_no_eligible_observation_fail_closed(self):
        m=self.module()
        with self.assertRaises(ValueError):m.select_share([], 'ANSS','2024-07-31')
        with self.assertRaises(ValueError):m.xbrl_share_rows(self.fixture(),'UNKNOWN')

if __name__=='__main__':unittest.main()
