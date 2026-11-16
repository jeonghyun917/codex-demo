import importlib.util
import unittest
from pathlib import Path

PATH=Path(__file__).resolve().parents[3]/'scripts/quant_team_financials.py'

class TeamFinancialsTest(unittest.TestCase):
    def module(self):
        self.assertTrue(PATH.exists(),'Missing filing-bounded financial extractor')
        spec=importlib.util.spec_from_file_location('team_financials',PATH)
        m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

    def fixture(self):
        rows=[dict(start='2024-07-01',end='2025-06-30',val=100,filed='2025-08-15',accn='old',form='10-K',fp='FY',fy=2025),
              dict(start='2024-07-01',end='2025-06-30',val=999,filed='2026-02-01',accn='restated',form='10-K/A',fp='FY',fy=2025),
              dict(start='2025-07-01',end='2025-12-31',val=80,filed='2026-02-06',accn='q2',form='10-Q',fp='Q2',fy=2026),
              dict(start='2025-10-01',end='2025-12-31',val=45,filed='2026-02-06',accn='q2',form='10-Q',fp='Q2',fy=2026)]
        return dict(cik=1650372,facts={'us-gaap':{'Revenues':{'units':{'USD':rows}}}})

    def test_later_restatement_and_same_day_filing_cannot_enter_earlier_decision(self):
        m=self.module();p=self.fixture()
        rows=m.financial_rows(p,'2026-02-01')
        self.assertEqual(1,len(rows));self.assertEqual('100',rows[0]['revenue'])
        self.assertEqual('old',rows[0]['accessionNumber'])

    def test_quarter_uses_standalone_three_months_not_six_month_ytd(self):
        m=self.module();rows=m.financial_rows(self.fixture(),'2026-02-27')
        q=[r for r in rows if r['fiscalPeriod']=='Q2'][0]
        self.assertEqual('45',q['revenue']);self.assertEqual('2025-10-01',q['startDate'])

    def test_missing_fields_stay_null_and_wrong_unit_is_not_used(self):
        m=self.module();p=self.fixture()
        p['facts']['us-gaap']['NetIncomeLoss']={'units':{'EUR':[p['facts']['us-gaap']['Revenues']['units']['USD'][0]]}}
        r=m.financial_rows(p,'2026-01-30')[0]
        self.assertIsNone(r['netIncome']);self.assertIsNone(r['epsDiluted'])

    def test_conflicting_same_filing_period_is_rejected(self):
        m=self.module();p=self.fixture();r=dict(p['facts']['us-gaap']['Revenues']['units']['USD'][0]);r['val']=101
        p['facts']['us-gaap']['Revenues']['units']['USD'].append(r)
        with self.assertRaises(ValueError):m.financial_rows(p,'2026-01-30')

    def test_wrong_company_and_future_period_are_rejected_or_excluded(self):
        m=self.module();p=self.fixture();p['cik']=1
        with self.assertRaises(ValueError):m.financial_rows(p,'2026-01-30')
        p=self.fixture();p['facts']['us-gaap']['Revenues']['units']['USD'][0]['end']='2027-06-30'
        self.assertEqual([],m.financial_rows(p,'2026-01-30'))

if __name__=='__main__':unittest.main()
