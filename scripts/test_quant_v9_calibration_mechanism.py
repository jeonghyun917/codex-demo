import copy
import importlib
import unittest

try: module=importlib.import_module('quant_v9_calibration_mechanism')
except ModuleNotFoundError: module=None


def snapshot(prediction,confidence=50,sector=0,calibration=8.):
    return dict(expectedReturnPct=prediction[0],expectedExcessReturnPct=prediction[1],
                confidence=confidence,sampleCount=300,sectorSampleCount=sector,calibrationErrorPct=calibration)


class CalibrationMechanismTest(unittest.TestCase):
    def setUp(self):self.assertIsNotNone(module,'Calibration mechanism implementation missing')

    def test_literal_scaling_change_can_reverse_an_upstream_improvement(self):
        old5=snapshot([10.,5.]);old6=snapshot([5.,2.5])
        new5=snapshot([8.,4.],confidence=70,sector=40)
        new6=snapshot([5.456,2.728],calibration=0.)
        result=module.counterfactual(old5,old6,new5,new6)
        self.assertAlmostEqual(result['beforeScale'],.5)
        self.assertAlmostEqual(result['afterScale'],.682)
        self.assertEqual(result['fixedPreviousScalePrediction'],[4.,2.])
        for actual,want in zip(result['propagatedInputDelta'],[-1.,-.5]):self.assertAlmostEqual(actual,want)
        for actual,want in zip(result['adaptiveScaleDelta'],[1.456,.728]):self.assertAlmostEqual(actual,want)

    def test_formula_must_reproduce_actual_both_heads_not_only_a_ratio(self):
        old5=snapshot([10.,5.]);old6=snapshot([5.,2.5])
        wrong=copy.deepcopy(old6);wrong['expectedExcessReturnPct']=99.
        with self.assertRaises(ValueError):module.counterfactual(old5,wrong,old5,old6)

    def test_missing_calibration_uses_explicit_fallback_and_low_sample_guard(self):
        v5=snapshot([10.,0.],confidence=0,sector=0);v5['sampleCount']=10
        v6=snapshot([3.4,0.],calibration=None)
        self.assertAlmostEqual(module.scale(v5,v6),.34)


if __name__=='__main__':unittest.main()
