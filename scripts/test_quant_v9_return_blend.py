import copy
from datetime import date,timedelta
import importlib
import unittest

try: q=importlib.import_module('quant_v9_return_blend')
except ModuleNotFoundError:q=None


class ReturnBlendTest(unittest.TestCase):
    def setUp(self):self.assertIsNotNone(q,'Return-loss candidate implementation missing')

    def rows(self):
        rows=[]
        for n in range(100):
            day=date(2019,1,1)+timedelta(days=n)
            for symbol in range(10):
                rows.append(dict(day=str(day),exit=str(day+timedelta(days=20)),symbol=str(symbol),
                                 y=[2.,1.],v5=[2.,1.],v9=[0.,0.]))
        return rows

    def test_literal_convex_blend_and_invalid_scale(self):
        self.assertEqual(q.blend([4.,4.],[2.,0.],.5),[3.,2.])
        with self.assertRaises(ValueError):q.blend([4.,4.],[2.,0.],1.1)

    def test_fit_uses_only_strictly_mature_labels_and_recovers_literal_weight(self):
        rows=self.rows();future=copy.deepcopy(rows[-1]);future.update(day='2019-05-31',exit='2019-06-01',symbol='future',y=[-999.,999.])
        model=q.fit_blend(rows+[future],'2019-06-01')
        self.assertEqual(model['weight'],1.)
        self.assertEqual(model['trainRows'],1000)
        self.assertEqual(model['maxTrainExit'],'2019-04-30')
        future['y']=[999999.,-999999.]
        self.assertEqual(q.fit_blend(rows+[future],'2019-06-01'),model)

    def test_joint_loss_and_rmse_guard_reject_tradeoff_and_small_history(self):
        rows=self.rows()
        for row in rows:row['v5']=[2.,-20.]
        self.assertEqual(q.fit_blend(rows,'2019-06-01')['weight'],0.)
        self.assertEqual(q.fit_blend(rows[:590],'2019-06-01')['weight'],0.)

    def test_old60_windows_and_duplicate_training_keys_cannot_be_fit(self):
        rows=self.rows()
        with self.assertRaises(ValueError):q.fit_blend(rows+[rows[0]],'2019-06-01')
        row=copy.deepcopy(rows[0]);row.update(day='2020-09-30',exit='2020-10-29')
        with self.assertRaises(ValueError):q.fit_blend([row],'2021-01-01')

    def test_uncertified_historical_window_cannot_be_scored_as_unseen(self):
        with self.assertRaises(ValueError):q.require_unseen_gate({'certified':False,'classification':'INTERNAL'})


if __name__=='__main__':unittest.main()
