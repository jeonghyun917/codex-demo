"""Catches wrong issuer, future filings, YTD quarter fill and short/gapped price history."""
import importlib.util,sys,unittest
from pathlib import Path
PATH=Path(__file__).resolve().parents[3]/'scripts/quant_older_inputs.py'
sys.path.insert(0,str(PATH.parent))

class OlderInputsTest(unittest.TestCase):
    def module(self):
        self.assertTrue(PATH.exists(),'Missing older-window input adapter')
        spec=importlib.util.spec_from_file_location('older_inputs',PATH);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
    def fixture(self):
        r=dict(start='2023-01-01',end='2023-12-31',val=100,filed='2024-02-20',accn='fy',form='10-K',fp='FY',fy=2023)
        return dict(cik=1013462,facts={'us-gaap':{'Revenues':{'units':{'USD':[r]}}}})
    def test_issuer_identity_and_filing_date_precede_decision(self):
        m=self.module();p=self.fixture()
        self.assertEqual([],m.us_rows(p,'ANSS','2024-02-20'))
        self.assertEqual('100',m.us_rows(p,'ANSS','2024-05-31')[0]['revenue'])
        with self.assertRaises(ValueError):m.us_rows(p,'MDB','2024-05-31')
    def test_no_ytd_as_standalone_quarter_or_future_restatement(self):
        m=self.module();p=self.fixture();rows=p['facts']['us-gaap']['Revenues']['units']['USD']
        rows.append(dict(rows[0],start='2024-01-01',end='2024-06-30',fp='Q2',filed='2024-07-20',accn='q',val=60))
        rows.append(dict(rows[0],filed='2025-01-01',accn='later',val=999))
        self.assertEqual(['100'],[x['revenue'] for x in m.us_rows(p,'ANSS','2024-08-01')])
    def test_missing_fields_and_wrong_units_not_filled(self):
        m=self.module();p=self.fixture();p['facts']['us-gaap']['EarningsPerShareDiluted']={'units':{'EUR/shares':[dict(p['facts']['us-gaap']['Revenues']['units']['USD'][0],val=4)]}}
        r=m.us_rows(p,'ANSS','2024-05-31')[0]
        self.assertIsNone(r['epsDiluted']);self.assertIsNone(r['assets'])
    def test_eighty_history_and_every_holding_session_required(self):
        m=self.module();history={f'2024-01-{i:02}':1 for i in range(1,81)}
        # Calendar keys are supplied separately; this gate counts distinct already-parsed observations.
        sessions=['entry','middle','exit'];prices=dict(history,entry=2,middle=2,exit=3)
        self.assertTrue(m.price_eligible(history,sessions,prices))
        self.assertFalse(m.price_eligible(dict(list(history.items())[:79]),sessions,prices))
        del prices['middle'];self.assertFalse(m.price_eligible(history,sessions,prices))

if __name__=='__main__':unittest.main()
