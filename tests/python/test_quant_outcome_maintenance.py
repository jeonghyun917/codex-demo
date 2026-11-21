from datetime import datetime, timezone
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
import quant_daily_forward as daily


class OutcomeMaintenanceTest(unittest.TestCase):
    def fixture(self):
        calendar = [dict(session=f"2026-01-{day:02}", openUtc=f"2026-01-{day:02}T14:30:00Z",
                         closeUtc=f"2026-01-{day:02}T21:00:00Z") for day in (2, 5, 6)]
        return [(Path("manifest.json"), dict(protocol=dict(calendar=calendar), cohorts=[
            dict(indexCode="DOW30", entrySession="2026-01-05", exitSession="2026-01-06",
                 predictions=[dict(symbol="HELD"), dict(symbol="REMOVED")])]))]

    def test_requirement_retains_removed_names_and_only_known_completed_holding_dates(self):
        self.assertTrue(hasattr(daily, "outcome_requirements"), "outcome planning is missing")
        result = daily.outcome_requirements(self.fixture(), "2026-01-05")
        self.assertEqual(result["stockKeys"], {("HELD", "2026-01-05"), ("REMOVED", "2026-01-05")})
        self.assertEqual(result["benchmarkKeys"], {("DOW30", "2026-01-05")})
        self.assertEqual(result["knownSessions"], ["2026-01-02", "2026-01-05", "2026-01-06"])
        self.assertEqual(daily.outcome_requirements(self.fixture(), "2026-01-02")["stockKeys"], set())

    def test_complete_stored_labels_need_no_provider_or_runtime_calls(self):
        self.assertTrue(hasattr(daily, "maintain_outcomes"), "outcome maintenance is missing")
        class CompleteDatabase:
            def query(self, sql):
                if "stock_candle_daily" in sql:
                    return [["HELD", "2026-01-05"], ["REMOVED", "2026-01-05"]]
                if "stock_benchmark_return_series" in sql:
                    return [["DOW30", "2026-01-05"]]
                raise AssertionError("Unexpected provider/write query")
        with tempfile.TemporaryDirectory() as tmp:
            result = daily.maintain_outcomes(CompleteDatabase(), Path(tmp), "2026-01-05", self.fixture(),
                                            datetime(2026, 1, 6, 7, tzinfo=timezone.utc))
        self.assertEqual(result["status"], "COMPLETE_STORED_CLOSES")
        self.assertFalse(result["priceCollection"])
        self.assertFalse(result["forecastGeneration"])

    def test_denial_blocks_missing_label_collection_without_attempt_marker(self):
        self.assertTrue(hasattr(daily, "maintain_outcomes"), "outcome maintenance is missing")
        class DeniedDatabase:
            def query(self, sql):
                if "status_code" in sql:
                    return [["1"]]
                if "status='RUNNING'" in sql:
                    return [["0"]]
                return []
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with patch.object(daily, "ROOT", root):
                with self.assertRaisesRegex(RuntimeError, "Provider denied"):
                    daily.maintain_outcomes(DeniedDatabase(), root, "2026-01-05", self.fixture(),
                                            datetime(2026, 1, 6, 7, tzinfo=timezone.utc))
            self.assertFalse(list(root.rglob("*attempt*.json")))

    def test_calendar_blocked_controller_runs_outcome_maintenance_before_evaluation(self):
        self.assertTrue(hasattr(daily, "maintain_outcomes"), "outcome maintenance is missing")
        class StoredDatabase:
            def query(self, sql):
                return []
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            protocols = root / "protocols"
            for index in daily.INDICES:
                folder = protocols / index
                folder.mkdir(parents=True)
                daily.write_record(folder / "protocol.json", daily.forward.seal(dict(indexCode=index,
                    calendar=self.fixture()[0][1]["protocol"]["calendar"])))
            def maintenance(db, output, anchor, manifests, now):
                daily.write_record(output / "maintenance-proof.json", dict(anchor=anchor, forecastGeneration=False))
                return dict(status="SYNTHETIC_MAINTENANCE", forecastGeneration=False)
            def evaluate(db, output, anchor, now):
                self.assertTrue((output / "maintenance-proof.json").exists())
                return [], []
            with patch.object(daily, "ROOT", root), patch.object(daily, "Database", StoredDatabase), \
                    patch.object(daily, "maintain_outcomes", side_effect=maintenance), \
                    patch.object(daily, "evaluate_all", side_effect=evaluate):
                result = daily.execute(protocols, run=True, now=datetime(2026, 1, 7, 7, tzinfo=timezone.utc))
            self.assertEqual(result["outcomeMaintenance"]["status"], "SYNTHETIC_MAINTENANCE")
            self.assertEqual(result["status"], "COLLECTION_BLOCKED_CALENDAR_EVALUATION_COMPLETED")


if __name__ == "__main__":
    unittest.main()
