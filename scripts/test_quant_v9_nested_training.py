import copy
import unittest
from datetime import date,timedelta
import numpy as np
import quant_v9_nested_training as q

class NestedTrainingTest(unittest.TestCase):
    def test_equal_day_maturity_is_not_available(self):
        rows=[dict(day='2019-01-01',exit='2019-01-31'),dict(day='2019-01-02',exit='2019-02-01'),dict(day='2019-02-01',exit='2019-02-02')]
        self.assertEqual(q.mature_indices(rows,'2019-02-01'),[0])

    def test_date_weights_do_not_overweight_larger_cohorts(self):
        np.testing.assert_allclose(q.date_weights([{'day':'A'},{'day':'A'},{'day':'B'}]),[.5,.5,1.])

    def test_date_balanced_mae_is_not_row_average(self):
        r=[dict(day='A',y=[0,0]),dict(day='A',y=[0,0]),dict(day='B',y=[0,0])]
        self.assertAlmostEqual(q.summarize(r,np.array([[0,0],[2,2],[4,4]]))['Absolute']['maePp'],2.5)

    def test_future_prices_cannot_change_observable_features(self):
        series=[((date(2019,1,1)+timedelta(days=i)).isoformat(),100+i) for i in range(80)]
        original=q.price_features(series,'2019-03-21')
        changed=q.price_features(series+[('2019-03-22',9999999)],'2019-03-21')
        np.testing.assert_allclose(original,changed)
        self.assertAlmostEqual(original[1],12.57861635220126)

    def test_ridge_recovers_literal_line_and_fits_scaling_on_train_only(self):
        m=q.fit_ridge(np.array([[0.],[1.],[2.]]),np.array([[1.,2.],[3.,4.],[5.,6.]]),np.ones(3),0.)
        np.testing.assert_allclose(q.predict(m,np.array([[3.]])),[[7.,8.]],atol=1e-10)
        np.testing.assert_allclose(m['mean'],[1.])

    def test_constant_training_feature_does_not_invent_signal(self):
        m=q.fit_ridge(np.array([[50.],[50.],[50.]]),np.array([[1.,2.],[3.,4.],[5.,6.]]),np.ones(3),10.)
        np.testing.assert_allclose(q.predict(m,np.array([[90.]])),[[3.,4.]],atol=1e-10)

    def test_single_target_gain_cannot_pass_joint_selection_gate(self):
        scores={'v9':[[10,10]]*3,'bad':[[8,11]]*3,'good':[[9,9]]*3}
        self.assertEqual(q.choose(scores,['v9','bad','good']),'good')
        self.assertEqual(q.choose({k:v for k,v in scores.items() if k!='good'},['v9','bad']),'v9')

    def test_future_label_changes_do_not_change_an_earlier_fit(self):
        rows=[dict(day='2018-01-01',exit='2018-02-01',x=[0.],y=[1.,2.],base=[0.,0.]),dict(day='2018-01-02',exit='2018-02-02',x=[1.],y=[3.,4.],base=[0.,0.]),dict(day='2018-03-01',exit='2018-04-01',x=[2.],y=[100.,200.],base=[0.,0.])]
        before=q.fit_at(rows,'2018-03-01',[0],10.,False)
        rows[-1]['y']=[-999999.,999999.]
        after=q.fit_at(rows,'2018-03-01',[0],10.,False)
        np.testing.assert_allclose(q.predict(before,np.array([[2.]])),q.predict(after,np.array([[2.]])))

    def test_old_test_or_duplicate_keys_are_rejected(self):
        row=dict(day='2020-09-30',exit='2020-10-29',symbol='A',horizon=20,x=[1.],y=[1.,1.],base=[1.,1.],maxFeatureDay='2020-09-30')
        with self.assertRaises(ValueError):q.validate_panel([row])
        row.update(day='2020-08-28',exit='2020-09-29',maxFeatureDay='2020-08-28')
        with self.assertRaises(ValueError):q.validate_panel([row,copy.deepcopy(row)])

    def test_actual_missing_sentinel_and_eps_are_distinct(self):
        records={'A/2020-01-31/findLatestAnnualAsOf':{'missing':True},'A/2020-01-31/findLatestQuarterAsOf':dict(filedAt='2020-01-01',endDate='2019-12-31',epsDiluted=None)}
        x=q.availability_features(records,'A','2020-01-31')
        np.testing.assert_allclose(x,[1,1,0,0,1,np.log1p(31)])
        with self.assertRaises(ValueError):q.availability_features({},'A','2020-01-31')

if __name__=='__main__':unittest.main()
