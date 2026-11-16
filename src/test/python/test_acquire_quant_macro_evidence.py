import importlib.util
from datetime import date
from pathlib import Path
import unittest

MODULE = Path(__file__).resolve().parents[3] / "scripts/acquire_quant_macro_evidence.py"
SPEC = importlib.util.spec_from_file_location("macro_evidence", MODULE)
macro = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(macro)


class MacroEvidenceTest(unittest.TestCase):
    def parse(self, text):
        return macro.parse_snapshot(text.encode(), "CPIAUCSL", date(2023, 3, 30))

    def test_preserves_string_precision_and_does_not_fill_missing(self):
        rows, missing = self.parse("observation_date,CPIAUCSL_20230330\n2023-01-01,300.536\n2023-02-01,.\n")
        self.assertEqual(rows[0]["value"], "300.536")
        self.assertEqual((len(rows), missing), (1, 1))
        self.assertNotIn("realtime_end", rows[0])

    def test_rejects_latest_or_wrong_vintage_header(self):
        for name in ("CPIAUCSL", "CPIAUCSL_20230331"):
            with self.assertRaises(ValueError):
                self.parse(f"observation_date,{name}\n2023-01-01,300.536\n")

    def test_rejects_future_and_duplicate_observations(self):
        for body in ("2023-04-01,301\n", "2023-01-01,300\n2023-01-01,301\n"):
            with self.assertRaises(ValueError):
                self.parse("observation_date,CPIAUCSL_20230330\n" + body)

    def test_rejects_nonfinite_and_empty_snapshots(self):
        for body in ("2023-01-01,nan\n", "2023-01-01,inf\n", "2023-01-01,.\n"):
            with self.assertRaises(ValueError):
                self.parse("observation_date,CPIAUCSL_20230330\n" + body)

    def test_month_lookback_handles_leap_dates(self):
        self.assertEqual(macro.months_before(date(2024, 8, 31), 18), date(2023, 2, 28))


if __name__ == "__main__":
    unittest.main()
