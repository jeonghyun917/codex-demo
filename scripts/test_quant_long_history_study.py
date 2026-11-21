import unittest
import quant_long_history_study as q


class LongHistoryStudyTest(unittest.TestCase):
    def test_august31_training_would_mature_on_frozen_cutoff_and_is_rejected(self):
        self.assertFalse(q.mature({'signalDate':'2020-08-31','exitTradeDate':'2020-09-30'},'2020-09-30'))
        self.assertTrue(q.mature({'signalDate':'2020-08-28','exitTradeDate':'2020-09-29'},'2020-09-30'))

    def test_residual_correction_uses_only_mature_training_not_current_test(self):
        training=[dict(signalDate='2019-09-02',symbol='A',horizonDays=20,exitTradeDate='2019-10-01',forwardReturnPct=4,benchmarkReturnPct=1)]
        preds=[dict(signalDate='2019-09-02',symbol='A',horizonDays=20,modelVersion=q.V9,expectedReturnPct=1,expectedExcessReturnPct=1)]
        fit=q.residual_shift(training,preds,'2020-09-30',minimum=1)
        self.assertEqual(fit['Absolute'],3)
        self.assertEqual(fit['Excess'],2)
        altered=training+[dict(training[0],signalDate='2020-10-30',exitTradeDate='2020-12-01',forwardReturnPct=999)]
        self.assertEqual(fit,q.residual_shift(altered,preds,'2020-09-30',minimum=1))

    def test_inadequate_or_duplicate_oof_cannot_be_called_calibration(self):
        with self.assertRaises(ValueError):
            q.residual_shift([],[],'2020-09-30')
        row=dict(signalDate='2019-01-01',symbol='A',horizonDays=20,exitTradeDate='2019-02-01',forwardReturnPct=1,benchmarkReturnPct=0)
        p=dict(signalDate='2019-01-01',symbol='A',horizonDays=20,modelVersion=q.V9,expectedReturnPct=0,expectedExcessReturnPct=0)
        with self.assertRaises(ValueError):
            q.residual_shift([row],[p,p],'2020-09-30',minimum=1)

    def test_zero_overlap_gate_counts_prior_training_as_exposure(self):
        old=[dict(signalDate='2020-12-24',entryTradeDate='2020-12-28',exitTradeDate='2021-01-27',horizonDays=20)]
        with self.assertRaises(ValueError):
            q.assert_new_windows(old,[dict(decision='2020-12-30',entry='2020-12-31',exit='2021-01-29')])


if __name__=='__main__':unittest.main()
