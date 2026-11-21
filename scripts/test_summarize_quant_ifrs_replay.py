import importlib
import unittest

try:
    module = importlib.import_module("summarize_quant_ifrs_replay")
except ModuleNotFoundError:
    module = None


class IfrsSummaryTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(module, "same-population IFRS error calculation missing")

    def pair(self, **updates):
        return dict(decision="2025-01-31", symbol="TEST", entry="2025-02-03", exit="2025-03-03",
                    actual=0, benchmark=1, oldAbsolute=2, newAbsolute=1,
                    oldExcess=0, newExcess=-1, affected=True) | updates

    def test_error_delta_is_recomputed_from_realized_target_not_claimed_metrics(self):
        result = module.calculate([self.pair()])
        self.assertEqual(result["n"], 1)
        self.assertEqual(result["absolute"]["oldMaePp"], 2)
        self.assertEqual(result["absolute"]["newMaePp"], 1)
        self.assertEqual(result["absolute"]["maeDeltaPp"], -1)
        self.assertEqual(result["excess"]["oldMaePp"], 1)
        self.assertEqual(result["excess"]["newMaePp"], 0)

    def test_duplicate_or_missing_prediction_is_not_silently_dropped(self):
        for rows in ([self.pair(), self.pair()], [self.pair(newAbsolute=None)], [self.pair(actual=float("nan"))]):
            with self.assertRaises(ValueError):
                module.calculate(rows)

    def test_nonoverlap_uses_exit_and_entry_not_fixed_calendar_gap(self):
        rows = [self.pair(), self.pair(decision="2025-02-28", entry="2025-03-03", exit="2025-04-01"),
                self.pair(decision="2025-03-31", entry="2025-04-02", exit="2025-05-01")]
        self.assertEqual(module.nonoverlap(rows), ["2025-01-31", "2025-03-31"])

    def test_rolling_summary_requires_prior_mature_training_and_distinct_control_meaning(self):
        report = dict(databaseWrites=0, promotionAllowed=False, firstDateBaselineMaxAbsDifference=0,
                      dates=[dict(decision="2025-01-31", pairs=[self.pair()])],
                      trainingAudit=[dict(decision="2025-01-31", maxTrainingExit="2025-01-30")])
        result = module.summarize(report)
        self.assertIn("ROLLING", result["classification"])
        report["trainingAudit"][0]["maxTrainingExit"] = "2025-01-31"
        with self.assertRaises(ValueError):
            module.summarize(report)


if __name__ == "__main__":
    unittest.main()
