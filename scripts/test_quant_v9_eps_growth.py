import copy
import importlib
import unittest
from datetime import date, timedelta
import numpy as np

try:
    q=importlib.import_module('quant_v9_eps_growth')
except ModuleNotFoundError:
    q=None

def statement(end,eps,fp='Q2',filed=None,cik='1',**kw):
    start={'Q1':'01-01','Q2':'04-01','Q3':'07-01','Q4':'10-01','FY':'01-01'}[fp]
    year=end[:4];begin=year+'-'+start
    n=dict(symbol='A',cik=cik,endDate=end,startDate=begin,periodDays=(date.fromisoformat(end)-date.fromisoformat(begin)).days+1,
           fiscalPeriod=fp,fiscalYear=2099,form='10-K' if fp=='FY' else '10-Q',filedAt=filed or (date.fromisoformat(end)+timedelta(days=20)).isoformat(),
           epsDiluted=eps,epsUnit='USD/shares',currency='USD',accessionNumber=end,id=int(end.replace('-','')),
           revenue=100.,netIncome=10.,assets=None,equity=None,_epsConcept='EarningsPerShareDiluted')
    n.update(kw);return n

class EpsGrowthTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(q,'EPS growth research behavior not implemented')

    def test_yoy_uses_actual_period_not_fiscal_year_metadata(self):
        rows=[statement('2018-06-30',2.),statement('2019-06-30',4.)]
        f,e=q.financial_features(rows,'2019-09-01',[],stock_ret20=10.)
        self.assertAlmostEqual(f['quarter_yoy_symmetric'],2/3)
        self.assertAlmostEqual(f['quarter_yoy_positive'],1.)
        self.assertEqual(f['quarter_yoy_missing'],0)
        self.assertAlmostEqual(f['eps_momentum_confirmation'],1/15)
        self.assertEqual(e['quarter']['endDate'],'2019-06-30')

    def test_publication_equal_day_and_future_restatement_not_available(self):
        old=statement('2019-06-30',2.,filed='2019-07-20')
        future=statement('2019-06-30',999.,filed='2019-08-01',accessionNumber='amended')
        self.assertEqual(q.select_latest([old,future],'2019-08-01',False)['epsDiluted'],2.)
        self.assertEqual(q.select_latest([old,future],'2019-08-02',False)['epsDiluted'],999.)

    def test_pair_blocks_wrong_issuer_ytd_duration_or_currency(self):
        current=statement('2019-06-30',4.)
        for prior in [statement('2018-06-30',2.,cik='2'),statement('2018-06-30',2.,startDate='2018-01-01',periodDays=181),statement('2018-06-30',2.,currency='EUR')]:
            self.assertIsNone(q.comparison([prior],current,'2019-09-01','yoy'))

    def test_pure_split_normalization_ignores_future_and_separation(self):
        f=statement('2018-06-30',8.)
        events=[dict(day='2019-01-01',kind='SPLIT',numerator='2',denominator='1'),dict(day='2019-02-01',kind='SEPARATION_RESEARCH',numerator='9',denominator='1'),dict(day='2021-01-01',kind='SPLIT',numerator='4',denominator='1')]
        self.assertEqual(q.eps_asof(f,events,'2019-09-01'),4.)
        f['filedAt']='2019-01-01'
        self.assertIsNone(q.eps_asof(f,events,'2019-09-01'))

    def test_loss_and_zero_baseline_are_not_naive_growth_ratios(self):
        f,_=q.financial_features([statement('2018-06-30',-1.),statement('2019-06-30',1.)],'2019-09-01',[])
        self.assertEqual(f['quarter_yoy_symmetric'],2.)
        self.assertEqual(f['quarter_loss_to_profit'],1)
        self.assertEqual(f['quarter_positive_growth_missing'],1)
        f,_=q.financial_features([statement('2018-06-30',0.),statement('2019-06-30',0.)],'2019-09-01',[])
        self.assertEqual(f['quarter_yoy_symmetric'],0.)
        self.assertEqual(f['quarter_yoy_missing'],0)
        self.assertEqual(f['quarter_positive_growth_missing'],1)

    def test_acceleration_is_difference_of_two_same_quarter_yoy_changes(self):
        rows=[statement('2018-03-31',2.,'Q1'),statement('2019-03-31',3.,'Q1'),statement('2018-06-30',2.),statement('2019-06-30',4.)]
        f,_=q.financial_features(rows,'2019-09-01',[])
        self.assertAlmostEqual(f['quarter_yoy_acceleration'],4/15)
        self.assertAlmostEqual(f['quarter_qoq_symmetric'],2/7)
        self.assertEqual(f['quarter_yoy_acceleration_missing'],0)

    def test_missing_q4_is_not_inferred_by_subtracting_annual_eps(self):
        rows=[statement('2018-12-31',5.,'FY'),statement('2018-03-31',1.,'Q1'),statement('2019-03-31',2.,'Q1')]
        f,_=q.financial_features(rows,'2019-06-01',[])
        self.assertEqual(f['quarter_yoy_missing'],0)
        self.assertEqual(f['quarter_qoq_missing'],1)
        self.assertEqual(f['quarter_yoy_acceleration_missing'],1)

    def test_dis_issuer_boundary_is_not_a_same_symbol_comparison(self):
        self.assertTrue(q.issuer_allowed('DIS','1001039','2019-03-19',q.DIS_RULE))
        self.assertFalse(q.issuer_allowed('DIS','1001039','2019-05-09',q.DIS_RULE))
        self.assertTrue(q.issuer_allowed('DIS','1744489','2019-05-09',q.DIS_RULE))

    def test_future_financial_observations_cannot_change_features(self):
        rows=[statement('2018-06-30',2.),statement('2019-06-30',4.)]
        a=q.financial_features(rows,'2019-09-01',[])[0]
        rows.extend([statement('2020-06-30',999.),statement('2019-06-30',999.,filed='2020-01-01',accessionNumber='later')])
        self.assertEqual(a,q.financial_features(rows,'2019-09-01',[])[0])

    def test_zero_eps_and_missing_eps_have_distinct_flags(self):
        a=q.financial_features([statement('2018-06-30',1.),statement('2019-06-30',0.)],'2019-09-01',[])[0]
        b=q.financial_features([statement('2018-06-30',1.),statement('2019-06-30',None)],'2019-09-01',[])[0]
        self.assertEqual(a['quarter_yoy_missing'],0)
        self.assertEqual(b['quarter_yoy_missing'],1)

    def test_stale_anchor_masks_growth_without_dropping_the_prediction(self):
        f,e=q.financial_features([statement('2017-06-30',1.),statement('2018-06-30',2.)],'2019-09-01',[])
        self.assertEqual(f['quarter_yoy_missing'],1)
        self.assertEqual(e['quarterStatus'],'STALE')

    def test_basic_and_diluted_eps_cannot_be_mixed(self):
        f,_=q.financial_features([statement('2018-06-30',2.,_epsConcept='EarningsPerShareBasic'),statement('2019-06-30',4.)],'2019-09-01',[])
        self.assertEqual(f['quarter_yoy_missing'],1)

    def test_selection_blocks_rmse_regression_even_when_both_maes_improve(self):
        base=[dict(mae=[10.,10.],rmse=[12.,12.])]*3
        bad=[dict(mae=[9.,9.],rmse=[13.,11.])]*3
        good=[dict(mae=[9.5,9.5],rmse=[11.,11.])]*3
        self.assertEqual(q.select_candidate({'v9':base,'bad':bad},['v9','bad']),'v9')
        self.assertEqual(q.select_candidate({'v9':base,'bad':bad,'good':good},['v9','bad','good']),'good')

    def test_actual_nested_fit_and_selection_ignore_future_labels(self):
        rows=[];d=date(2017,1,2)
        while d<date(2019,4,1):
            if d.weekday()<5:
                x=[0.]*len(q.FEATURES);x[0]=.2;x[1]=.1
                rows.append(dict(day=d.isoformat(),exit=(d+timedelta(days=28)).isoformat(),symbol='A',horizon=20,x=x,y=[.5,.3],base=[.2,.1],maxFeatureDay=d.isoformat()))
            d+=timedelta(days=1)
        a=q.run_fold(rows,'2019-01-01',{})
        changed=copy.deepcopy(rows)
        for r in changed:
            if r['exit']>='2019-01-01':r['y']=[-1e8,1e8]
        b=q.run_fold(changed,'2019-01-01',{})
        self.assertEqual(a['selected'],b['selected'])
        for name in q.NAMES:
            np.testing.assert_array_equal(a['outer']['predictions'][name],b['outer']['predictions'][name])
            self.assertLessEqual(float(abs(a['outer']['predictions'][name]-np.array([r['base'] for r in rows if '2019-01-01'<=r['day']<'2019-04-01'])).max()),.5+1e-12)

    def test_old60_or_duplicate_panel_keys_rejected(self):
        row=dict(day='2020-09-30',exit='2020-10-29',symbol='A',horizon=20,x=[0.]*26,y=[1.,1.],base=[0.,0.],maxFeatureDay='2020-09-30')
        with self.assertRaises(ValueError):q.validate_panel([row])
        row.update(day='2020-08-28',exit='2020-09-29',maxFeatureDay='2020-08-28')
        with self.assertRaises(ValueError):q.validate_panel([row,copy.deepcopy(row)])

if __name__=='__main__':unittest.main()
