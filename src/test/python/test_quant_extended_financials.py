"""Breaks caught: foreign forms dropped, YTD leaked, ADR EPS mixed, conflicts hidden."""
import importlib.util
import sys
import unittest
from pathlib import Path

PATH=Path(__file__).resolve().parents[3]/'scripts/quant_extended_financials.py'
sys.path.insert(0,str(PATH.parent))

class ForeignFinancialsTest(unittest.TestCase):
    def module(self):
        self.assertTrue(PATH.exists(),'Missing foreign filing extractor')
        spec=importlib.util.spec_from_file_location('extended_financials',PATH)
        m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

    def fixture(self,cik=1709048):
        annual=dict(start='2024-01-01',end='2024-12-31',val=100,filed='2025-03-20',accn='fy',form='20-F',fp='FY',fy=2024)
        quarter=dict(start='2025-04-01',end='2025-06-30',val=30,filed='2025-08-05',accn='q2',form='6-K',fp=None,fy=None)
        ytd=dict(quarter,start='2025-01-01',val=60)
        return dict(cik=cik,facts={'ifrs-full':{'Revenue':{'units':{'USD':[annual,quarter,ytd]}},
            'DilutedEarningsLossPerShare':{'units':{'USD/shares':[dict(annual,val=4)]}}}})

    def test_foreign_annual_and_untagged_standalone_quarter_have_correct_periods(self):
        rows=self.module().foreign_rows(self.fixture(),'GFS','2025-08-29','ORDINARY')
        self.assertEqual(['Q2','FY'],[r['fiscalPeriod'] for r in rows])
        self.assertEqual('30',rows[0]['revenue']);self.assertEqual(91,rows[0]['periodDays'])
        self.assertEqual('4',rows[1]['epsDiluted'])

    def test_filing_cutoff_excludes_same_day_and_later_restatements(self):
        m=self.module();p=self.fixture()
        self.assertEqual(['FY'],[r['fiscalPeriod'] for r in m.foreign_rows(p,'GFS','2025-08-05','ORDINARY')])
        p['facts']['ifrs-full']['Revenue']['units']['USD'].append(dict(p['facts']['ifrs-full']['Revenue']['units']['USD'][0],filed='2026-01-01',val=999,accn='amended'))
        self.assertEqual('100',m.foreign_rows(p,'GFS','2025-08-05','ORDINARY')[0]['revenue'])

    def test_azn_ordinary_equivalent_archive_not_mixed_with_ads_eps(self):
        m=self.module();p=self.fixture(901832)
        self.assertEqual('4',m.foreign_rows(p,'AZN','2025-08-29','ORDINARY_EQUIVALENT_ARCHIVE')[1]['epsDiluted'])
        with self.assertRaises(ValueError):m.foreign_rows(p,'AZN','2025-08-29','ADS_UNVERIFIED')

    def test_missing_units_and_same_accession_conflicts_fail_safely(self):
        m=self.module();p=self.fixture();p['facts']['ifrs-full']['ProfitLoss']={'units':{'EUR':[p['facts']['ifrs-full']['Revenue']['units']['USD'][0]]}}
        self.assertIsNone(m.foreign_rows(p,'GFS','2025-08-29','ORDINARY')[0]['netIncome'])
        p['facts']['ifrs-full']['Revenue']['units']['USD'].append(dict(p['facts']['ifrs-full']['Revenue']['units']['USD'][0],val=101))
        with self.assertRaises(ValueError):m.foreign_rows(p,'GFS','2025-08-29','ORDINARY')

    def test_wrong_identity_rejected_and_noncalendar_untagged_quarter_not_guessed(self):
        m=self.module();p=self.fixture(1)
        with self.assertRaises(ValueError):m.foreign_rows(p,'GFS','2025-08-29','ORDINARY')
        p=self.fixture();p['facts']['ifrs-full']['Revenue']['units']['USD'][1]['start']='2025-03-15'
        self.assertEqual(['FY'],[r['fiscalPeriod'] for r in m.foreign_rows(p,'GFS','2025-08-29','ORDINARY')])

if __name__=='__main__':unittest.main()
