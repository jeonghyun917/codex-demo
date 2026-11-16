import sys, unittest
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))
from quant_20d_error_research import training_rows, fit_ridge, predict_ridge, metrics, price_features, evaluate, V9, V8

class ErrorResearchTest(unittest.TestCase):
    def test_training_requires_mature_outcomes_and_excludes_same_date(self):
        rows=[dict(day='2024-01-01',exit='2024-01-30'),dict(day='2024-01-02',exit='2024-02-01'),dict(day='2024-02-01',exit='2024-01-01')]
        self.assertEqual(training_rows(rows,'2024-02-01'),rows[:1])

    def test_ridge_recovers_linear_relation_without_using_test_values(self):
        model=fit_ridge([[0],[1],[2]],[1,3,5],alpha=0)
        self.assertAlmostEqual(float(predict_ridge(model,[[3]])[0]),7)
        self.assertEqual(model['mean'],[1.0])

    def test_constant_feature_is_finite(self):
        model=fit_ridge([[2],[2]],[1,3])
        self.assertAlmostEqual(float(predict_ridge(model,[[99]])[0]),2)

    def test_metrics_have_hand_calculated_percentage_point_errors(self):
        m=metrics([1,4],[3,2])
        self.assertEqual(m['maePp'],2)
        self.assertEqual(m['rmsePp'],2)
        self.assertEqual(m['biasPp'],0)

    def test_price_features_ignore_future_prices(self):
        p=[(f'2024-{i//28+1:02}-{i%28+1:02}',100+i) for i in range(80)]
        a=price_features(p,p[-1][0])
        self.assertEqual(a,price_features(p+[('2025-01-01',999999)],p[-1][0]))
        self.assertAlmostEqual(a[0],(179/174-1)*100)

    def test_invalid_training_values_fail_closed(self):
        with self.assertRaises(ValueError):fit_ridge([[float('nan')]],[1])

    def test_future_labels_cannot_change_fit(self):
        rows=[dict(day='2024-01-01',exit='2024-01-20',x=1,y=2),dict(day='2024-01-02',exit='2024-01-21',x=2,y=4),dict(day='2024-02-01',exit='2024-02-28',x=3,y=999)]
        def fitted():
            tr=training_rows(rows,'2024-02-01')
            return fit_ridge([[r['x']] for r in tr],[r['y'] for r in tr])
        a=fitted();rows[-1]['y']=-999
        self.assertEqual(a,fitted())

    def test_metrics_reject_broadcasting_mismatched_rows(self):
        with self.assertRaises(ValueError):metrics([1],[1,2,3])

    def test_full_evaluation_never_uses_current_test_labels(self):
        rows=[]
        for month in range(1,11):
            for symbol in ('A','B'):
                rows.append(dict(day=f'2024-{month:02}-01',entry=f'2024-{month:02}-02',exit=f'2024-{month:02}-28',symbol=symbol,
                                 actual=month,excess=month/2,price=[month,1,2,3,4],
                                 forecasts={m:dict(absolute=month/3,excess=month/4) for m in (V8,V9)}))
        a=evaluate(rows,[V8,V9])
        for r in rows[-2:]:r['actual']=-999;r['excess']=999
        b=evaluate(rows,[V8,V9])
        self.assertEqual([r['predicted'] for r in a['predictions']],[r['predicted'] for r in b['predictions']])
        self.assertEqual(a['fits'],b['fits'])

if __name__=='__main__':unittest.main()
