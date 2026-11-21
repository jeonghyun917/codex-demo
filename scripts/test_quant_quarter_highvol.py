import copy
import importlib
import unittest
from datetime import date,timedelta
import numpy as np
from test_quant_v9_eps_growth import statement

try:q=importlib.import_module('quant_quarter_highvol')
except ModuleNotFoundError:q=None

def financial(end,eps,fp='Q2',**kw):
    f=statement(end,eps,fp,**kw)
    f['_fieldConcepts']={'revenue':'Revenues','netIncome':'NetIncomeLoss','operatingIncome':'OperatingIncomeLoss','epsDiluted':f['_epsConcept']}
    f.setdefault('operatingIncome',20.)
    return f

def row(day,exit=None,vol=30.,base=(1.,.2),y=(3.,1.)):
    return dict(day=day,exit=exit or (date.fromisoformat(day)+timedelta(days=28)).isoformat(),symbol='A',horizon=20,
                x=[0.]*len(q.FEATURES),base=list(base),y=list(y),market=[0.,0.,0.,vol,0.],maxFeatureDay=day)

class QuarterHighVolTest(unittest.TestCase):
    def setUp(self):self.assertIsNotNone(q,'Quarterly/high-volatility research missing')

    def test_operating_and_net_margin_changes_use_actual_comparable_dollars(self):
        a=financial('2018-06-30',2.,revenue=100.,netIncome=10.,operatingIncome=20.)
        b=financial('2019-06-30',4.,revenue=100.,netIncome=20.,operatingIncome=30.)
        f,_=q.financial_features([a,b],'2019-09-01',[],0.)
        self.assertAlmostEqual(f['operating_margin_yoy_delta'],10.)
        self.assertAlmostEqual(f['net_margin_level'],20.)
        self.assertAlmostEqual(f['net_margin_yoy_delta'],10.)

    def test_cross_pair_basic_and_diluted_acceleration_is_blocked(self):
        a=financial('2018-03-31',2.,'Q1',_epsConcept='EarningsPerShareBasic')
        b=financial('2019-03-31',3.,'Q1',_epsConcept='EarningsPerShareBasic')
        c=financial('2018-06-30',2.);d=financial('2019-06-30',4.)
        f,_=q.financial_features([a,b,c,d],'2019-09-01',[],0.)
        self.assertEqual(f['quarter_yoy_acceleration_missing'],1)
        self.assertEqual(f['quarter_yoy_acceleration'],0.)

    def test_changed_revenue_concept_cannot_fabricate_margin_growth(self):
        a=financial('2018-06-30',2.);b=financial('2019-06-30',4.)
        a['_fieldConcepts']['revenue']='RevenuesNetOfInterestExpense'
        f,_=q.financial_features([a,b],'2019-09-01',[],0.)
        self.assertEqual(f['revenue_yoy_missing'],1)
        self.assertEqual(f['net_margin_yoy_missing'],1)
        self.assertEqual(f['operating_margin_yoy_missing'],1)

    def test_revenue_acceleration_needs_four_same_phase_observations(self):
        rows=[financial('2018-03-31',2.,'Q1',revenue=100.),financial('2019-03-31',3.,'Q1',revenue=110.),financial('2018-06-30',2.,revenue=100.),financial('2019-06-30',4.,revenue=140.)]
        f,_=q.financial_features(rows,'2019-09-01',[],0.)
        self.assertAlmostEqual(f['revenue_yoy_acceleration'],.23809523809523808)
        self.assertEqual(f['revenue_yoy_acceleration_missing'],0)

    def test_market_only_correction_changes_absolute_not_excess(self):
        train=[row((date(2018,1,1)+timedelta(days=i)).isoformat(),exit='2018-03-01') for i in range(30)]
        valid=[row('2019-01-02',vol=30.),row('2019-01-03',vol=10.)]
        correction,_=q.market_correction(train,valid)
        np.testing.assert_allclose(correction,[[.3,0.],[0.,0.]],atol=1e-10)

    def test_market_training_is_one_observation_per_date_not_stock_count(self):
        a=row('2018-01-01');b=dict(a,symbol='B');b['base']=[3.,.2]
        records=q.daily_records([a,b]);self.assertEqual(len(records),1)
        self.assertAlmostEqual(records[0]['marketTarget'],.2)

    def test_market_target_mismatch_same_date_fails_closed(self):
        a=row('2018-01-01');b=copy.deepcopy(a);b['symbol']='B';b['y']=[9.,1.]
        with self.assertRaises(ValueError):q.daily_records([a,b])

    def test_regime_uses_latest_63_mature_same_regime_dates(self):
        daily=[dict(day=(date(2018,1,1)+timedelta(days=i)).isoformat(),exit='2018-05-01',vol=30.,residual=[-2.,2.]) for i in range(90)]
        value,audit=q.regime_correction(daily,'2018-06-01',30.)
        np.testing.assert_allclose(value,[-.5,.5]);self.assertEqual(audit['dates'],63)
        self.assertEqual(audit['mode'],'SAME_REGIME')

    def test_regime_falls_back_to_global_then_zero_without_twenty_dates(self):
        daily=[dict(day=(date(2018,1,1)+timedelta(days=i)).isoformat(),exit='2018-05-01',vol=10.,residual=[1.,-1.]) for i in range(25)]
        value,audit=q.regime_correction(daily,'2018-06-01',30.)
        np.testing.assert_allclose(value,[.25,-.25]);self.assertEqual(audit['mode'],'GLOBAL_FALLBACK')
        value,audit=q.regime_correction(daily[:19],'2018-06-01',30.)
        np.testing.assert_array_equal(value,[0.,0.]);self.assertEqual(audit['mode'],'INSUFFICIENT')

    def test_equal_day_or_future_exit_cannot_enter_daily_calibration(self):
        daily=[dict(day=(date(2018,1,1)+timedelta(days=i)).isoformat(),exit='2018-05-01',vol=30.,residual=[1.,1.]) for i in range(25)]
        before=q.regime_correction(daily,'2018-06-01',30.)[0]
        daily.extend([dict(day='2018-05-01',exit='2018-06-01',vol=30.,residual=[-1e6,1e6]),dict(day='2018-05-02',exit='2018-06-02',vol=30.,residual=[-1e6,1e6])])
        np.testing.assert_array_equal(before,q.regime_correction(daily,'2018-06-01',30.)[0])

    def test_future_labels_do_not_change_current_daily_forecasts(self):
        rows=[];d=date(2017,1,2)
        while d<date(2019,4,1):
            if d.weekday()<5:rows.append(row(d.isoformat()))
            d+=timedelta(days=1)
        a=q.run_fold(rows,'2019-01-01',{})
        changed=copy.deepcopy(rows)
        for r in changed:
            if r['exit']>='2019-01-01':r['y']=[-1e6,1e6]
        b=q.run_fold(changed,'2019-01-01',{})
        self.assertEqual(a['selected'],b['selected'])
        for name in q.NAMES:
            np.testing.assert_array_equal(a['outer']['predictions'][name][0],b['outer']['predictions'][name][0])
        # Later daily updates may legitimately change once a perturbed old label matures.

    def test_future_raw_filings_cannot_change_historical_financial_features(self):
        rows=[financial('2018-06-30',2.),financial('2019-06-30',4.)]
        before=q.financial_features(rows,'2019-09-01',[],0.)[0]
        rows.append(financial('2020-06-30',999.))
        self.assertEqual(before,q.financial_features(rows,'2019-09-01',[],0.)[0])

    def test_highvol_interval_resamples_full_calendar_not_only_selected_days(self):
        rows=[row((date(2018,1,1)+timedelta(days=i)).isoformat(),base=(0.,0.),y=(0.,0.)) for i in range(35)]
        baseline=np.zeros((35,2));prediction=np.ones((35,2));mask=np.array([i%2==0 for i in range(35)])
        result=q.conditional_interval(rows,prediction,baseline,mask)
        self.assertEqual(result['calendarDates'],35)
        self.assertEqual(result['selectedDates'],18)
        for h in ['Absolute','Excess']:
            self.assertEqual(result[h]['deltaMaePp'],1.)
            self.assertEqual(result[h]['lower95'],1.)
            self.assertEqual(result[h]['upper95'],1.)

if __name__=='__main__':unittest.main()
