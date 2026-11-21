import copy
import unittest
import numpy as np
import quant_20d_factor_weights as research


class FactorWeightResearchTest(unittest.TestCase):
    def test_training_requires_strictly_mature_whole_date_cohorts(self):
        rows=[dict(day='2024-01-01',exit='2024-02-01'),dict(day='2024-01-01',exit='2024-02-02'),dict(day='2024-01-02',exit='2024-01-31')]
        self.assertEqual(['2024-01-02'],[r['day'] for r in research.training_rows(rows,'2024-02-02')])

    def test_toy_ridge_has_hand_derived_raw_unit_weights(self):
        model=research.fit_model([[-1],[0],[1]],[1,3,5],'RIDGE')
        self.assertAlmostEqual(6/103,model['rawUnitWeights'][0],places=12)
        self.assertAlmostEqual(3,model['rawUnitInterceptPp'],places=12)
        np.testing.assert_allclose(research.predict_model(model,[[-1],[1]]),[3-6/103,3+6/103])

    def test_future_label_mutation_does_not_change_earlier_fit_or_prediction(self):
        rows=[]
        for month in range(1,11):
            for j in range(4):
                rows.append(dict(day=f'2024-{month:02}-01',exit=f'2024-{month:02}-20',symbol=str(j),entry=f'2024-{month:02}-02',scores=[40+month+j*(i+1) for i in range(10)],actual=float(month+j),excess=float(j),v9Absolute=1.0,v9Excess=0.0,availability={}))
        before=research.predict_fold(rows,'2024-09-01')
        mutated=copy.deepcopy(rows)
        for row in mutated:
            if row['exit']>='2024-09-01':row['actual']+=999999;row['excess']-=999999
        after=research.predict_fold(mutated,'2024-09-01')
        self.assertEqual(before['fits'],after['fits'])
        self.assertEqual([r['predicted'] for r in before['predictions']],[r['predicted'] for r in after['predictions']])

    def test_nonoverlap_selection_is_outcome_blind(self):
        rows=[dict(day='2024-01-01',exit='2024-01-31',actual=1),dict(day='2024-01-20',exit='2024-02-20',actual=9),dict(day='2024-02-01',exit='2024-03-01',actual=-4)]
        self.assertEqual(['2024-01-01','2024-02-01'],research.nonoverlap_dates(rows))
        for r in rows:r['actual']*=10000
        self.assertEqual(['2024-01-01','2024-02-01'],research.nonoverlap_dates(rows))

    def test_constant_factor_has_zero_identified_weight_and_unavailable_variation(self):
        model=research.fit_model([[50],[50],[50]],[1,2,3],'RIDGE')
        self.assertEqual([True],model['constantTrainingFeatures'])
        self.assertEqual([0.0],model['rawUnitWeights'])


if __name__=='__main__':unittest.main()
