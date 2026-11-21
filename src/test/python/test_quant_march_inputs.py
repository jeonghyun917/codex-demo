"""Catches wrong currency/ADS valuation, future filings, 40-F loss and half-year quarters."""
import importlib.util,json,sys,unittest
from pathlib import Path
PATH=Path(__file__).resolve().parents[3]/'scripts/quant_march_inputs.py'
sys.path.insert(0,str(PATH.parent))

class MarchInputsTest(unittest.TestCase):
    def module(self):
        self.assertTrue(PATH.exists(),'Missing March input staging')
        spec=importlib.util.spec_from_file_location('march_inputs',PATH);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
    def fixture(self):
        annual=dict(start='2025-01-01',end='2025-12-31',filed='2026-03-05',form='40-F',accn='annual',fy=2025,fp='FY',val=100)
        half=dict(annual,end='2025-06-30',form='6-K',filed='2025-08-07',accn='half',val=50)
        quarter=dict(half,start='2025-04-01',accn='q2',val=25)
        return dict(cik=1075124,facts={'ifrs-full':{'Revenue':{'units':{'USD':[annual,half,quarter]}},
            'DilutedEarningsLossPerShare':{'units':{'USD/shares':[dict(annual,val=3.33)]}}}})
    def test_40f_annual_is_admitted_but_half_year_is_not_quarter(self):
        m=self.module();rows=m.foreign_rows(self.fixture(),'TRI','2026-03-13')
        self.assertEqual(['FY','Q2'],[r['fiscalPeriod'] for r in rows])
        self.assertEqual(['100','25'],[r['revenue'] for r in rows])
        self.assertEqual('3.33',rows[0]['epsDiluted'])
    def test_same_day_future_and_wrong_issuer_are_rejected(self):
        m=self.module();p=self.fixture()
        self.assertEqual(['Q2'],[r['fiscalPeriod'] for r in m.foreign_rows(p,'TRI','2026-03-05')])
        with self.assertRaises(ValueError):m.foreign_rows(p,'FER','2026-03-13')
    def test_same_accession_conflicting_values_are_not_averaged(self):
        m=self.module();p=self.fixture();rr=p['facts']['ifrs-full']['Revenue']['units']['USD'];rr.append(dict(rr[0],val=999))
        with self.assertRaises(ValueError):m.foreign_rows(p,'TRI','2026-03-13')
    def test_euro_eps_is_omitted_not_compared_to_dollar_quote(self):
        m=self.module();row=dict(symbol='ASML',filedAt='2026-02-25',endDate='2025-12-31',currency='EUR',epsUnit='EUR/shares',epsDiluted='24.71',rawJson='{}')
        out=m.valuation_units([row],'ASML','2026-03-13',1)[0]
        self.assertIsNone(out['epsDiluted']);self.assertEqual('24.71',json.loads(out['rawJson'])['valuationUnitPolicy']['reportedEps'])
        self.assertEqual('24.71',row['epsDiluted'],'Original input must remain unchanged')
    def test_ads_ratio_scales_reported_usd_eps_without_inventing_precision(self):
        m=self.module();row=dict(symbol='PDD',filedAt='2025-04-28',endDate='2024-12-31',currency='USD',epsUnit='USD/shares',epsDiluted='2.60',rawJson='{}')
        out=m.valuation_units([row],'PDD','2026-03-13',4)[0]
        self.assertEqual('10.40',out['epsDiluted']);self.assertEqual('USD/quoted-security',out['epsUnit'])
        with self.assertRaises(ValueError):m.valuation_units([row],'PDD','2026-03-13',None)
    def test_staged_rows_cannot_be_current_filing_or_other_symbol(self):
        m=self.module();r=dict(symbol='PDD',filedAt='2026-03-13',endDate='2025-12-31',currency='USD',epsUnit='USD/shares',epsDiluted='2',rawJson='{}')
        with self.assertRaises(ValueError):m.valuation_units([r],'PDD','2026-03-13',4)
        r['filedAt']='2026-03-01'
        with self.assertRaises(ValueError):m.valuation_units([r],'ARM','2026-03-13',1)
    def test_nine_month_fact_labeled_fy_cannot_displace_previous_real_annual(self):
        m=self.module();rows=[dict(fiscalPeriod='FY',startDate='2025-01-01',endDate='2025-12-31',filedAt='2026-02-12'),
            dict(fiscalPeriod='FY',startDate='2025-01-01',endDate='2025-09-30',filedAt='2026-02-12'),
            dict(fiscalPeriod='FY',startDate='2024-01-01',endDate='2024-12-31',filedAt='2025-02-12')]
        self.assertEqual(['2025-12-31','2024-12-31'],[r['endDate'] for r in m.latest_inputs(rows)])
    def test_cross_currency_previous_year_cannot_create_false_growth_or_skip_a_year(self):
        m=self.module();rows=[dict(fiscalPeriod='FY',startDate='2024-01-01',endDate='2024-12-31',filedAt='2025-04-28',currency='USD',revenue='53955324000',rawJson='{}'),
            dict(fiscalPeriod='FY',startDate='2023-01-01',endDate='2023-12-31',filedAt='2024-04-25',currency='CNY',revenue='247639205000',rawJson='{}'),
            dict(fiscalPeriod='FY',startDate='2022-01-01',endDate='2022-12-31',filedAt='2023-04-25',currency='USD',revenue='18900000000',rawJson='{}')]
        out=m.latest_inputs(rows)
        self.assertEqual(['2024-12-31'],[r['endDate'] for r in out])
        self.assertEqual('INCOMPARABLE_PRIOR_CURRENCY_EXISTING_MISSING_FACTOR_POLICY',json.loads(out[0]['rawJson'])['pairedPeriodPolicy']['action'])
        self.assertEqual('{}',rows[0]['rawJson'],'Do not mutate frozen source rows')

if __name__=='__main__':unittest.main()
