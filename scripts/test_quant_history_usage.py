import unittest
import quant_history_usage as q


class HistoryUsageTest(unittest.TestCase):
    def test_strict_gate_rejects_other_horizon_exposure_even_when_20d_is_new(self):
        audit = {'2026-04-30': dict(
            prior20DLabelWindows={'noPriorLabelWindowOverlap':True},
            priorAnyHorizonLabelWindows={'noPriorLabelWindowOverlap':False})}
        with self.assertRaises(ValueError):
            q.require_unused_windows(audit)

    def test_strict_gate_accepts_proven_unexposed_intervals(self):
        q.require_unused_windows({'2022-01-31':dict(
            prior20DLabelWindows={'noPriorLabelWindowOverlap':True},
            priorAnyHorizonLabelWindows={'noPriorLabelWindowOverlap':True})})

    def test_different_signal_dates_still_reuse_overlapping_return_intervals(self):
        previous = [dict(signalDate='2026-06-14', entryTradeDate='2026-06-15',
                         exitTradeDate='2026-07-14', horizonDays=20)]
        report = q.window_usage(previous, '2026-06-01', '2026-06-30')
        self.assertFalse(report['noPriorLabelWindowOverlap'])
        self.assertEqual(report['overlapRows'], 1)
        self.assertEqual(report['priorDecisionDates'], ['2026-06-14'])

    def test_shared_endpoint_is_not_a_shared_daily_return(self):
        previous = [dict(signalDate='2026-04-01', entryTradeDate='2026-04-02',
                         exitTradeDate='2026-06-01', horizonDays=20)]
        self.assertTrue(q.window_usage(previous, '2026-06-01', '2026-06-30')['noPriorLabelWindowOverlap'])

    def test_unknown_old_window_does_not_certify_unused(self):
        previous = [dict(signalDate='2026-01-01', entryTradeDate=None,
                         exitTradeDate='2026-06-30', horizonDays=20)]
        report = q.window_usage(previous, '2026-05-01', '2026-06-01')
        self.assertFalse(report['noPriorLabelWindowOverlap'])
        self.assertEqual(report['unresolvedWindows'], 1)

    def test_reversed_or_empty_target_interval_rejected(self):
        with self.assertRaises(ValueError):
            q.window_usage([], '2026-06-01', '2026-06-01')


if __name__ == '__main__':
    unittest.main()
