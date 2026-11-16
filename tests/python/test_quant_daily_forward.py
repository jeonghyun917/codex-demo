import importlib
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
try:
    daily = importlib.import_module("quant_daily_forward")
except ModuleNotFoundError:
    daily = None


class DailyForwardTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(daily, "daily collection controller is not implemented")

    def test_previous_new_york_day_guard_does_not_mislabel_same_day_data(self):
        rows = [dict(session="2026-10-30", openUtc="2026-10-30T13:30:00Z", closeUtc="2026-10-30T20:00:00Z"),
                dict(session="2026-11-02", openUtc="2026-11-02T14:30:00Z", closeUtc="2026-11-02T21:00:00Z")]
        now = datetime(2026, 11, 3, 0, tzinfo=timezone.utc)  # still Nov2 in NY
        self.assertEqual(daily.completed_session(rows, now)["session"], "2026-10-30")
        self.assertEqual(daily.completed_session(rows, datetime(2026,11,3,6,tzinfo=timezone.utc))["session"], "2026-11-02")

    def test_weekend_and_holiday_select_last_actual_scheduled_close(self):
        rows = [dict(session="2026-11-25", openUtc="2026-11-25T14:30:00Z", closeUtc="2026-11-25T21:00:00Z"),
                dict(session="2026-11-27", openUtc="2026-11-27T14:30:00Z", closeUtc="2026-11-27T18:00:00Z")]
        self.assertEqual(daily.completed_session(rows, datetime(2026,11,27,6,tzinfo=timezone.utc))["session"], "2026-11-25")
        self.assertEqual(daily.completed_session(rows, datetime(2026,11,29,6,tzinfo=timezone.utc))["session"], "2026-11-27")

    def test_expired_or_pre_calendar_dates_fail_closed(self):
        rows = [dict(session="2026-10-30", openUtc="2026-10-30T13:30:00Z", closeUtc="2026-10-30T20:00:00Z")]
        for instant in (datetime(2026,10,29,tzinfo=timezone.utc), datetime(2026,11,5,tzinfo=timezone.utc)):
            with self.assertRaises(ValueError):
                daily.completed_session(rows, instant)

    def test_lock_excludes_second_writer_and_releases_on_exception(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/"collection.lock"
            with self.assertRaisesRegex(RuntimeError, "original failure"):
                with daily.collection_lock(path):
                    with self.assertRaises(BlockingIOError):
                        with daily.collection_lock(path):
                            self.fail("second writer entered")
                    raise RuntimeError("original failure")
            with daily.collection_lock(path):
                pass

    def test_capture_detection_is_anchor_bound_and_requires_all_three_indices(self):
        self.assertEqual(daily.missing_indices("2026-10-30", {("DOW30","2026-10-30")} ), ["SP500","NASDAQ100"])
        self.assertEqual(daily.missing_indices("2026-11-02", {("DOW30","2026-10-30")} ), ["DOW30","SP500","NASDAQ100"])

    def test_create_only_outputs_preserve_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp)/"record.json"
            daily.write_record(file, {"n": 1})
            with self.assertRaises(FileExistsError):
                daily.write_record(file, {"n": 2})
            self.assertIn('"n": 1', file.read_text())

    def test_provider_denial_or_running_batch_blocks_new_collection(self):
        for denied, running in ((1,0), (0,1), (2,2)):
            with self.assertRaises(RuntimeError):
                daily.require_provider_safe(denied, running)
        daily.require_provider_safe(0,0)

    def test_reuse_requires_correct_provider_cutoff_and_all_held_symbol_attempts(self):
        self.assertTrue(daily.price_reuse_allowed("COMPLETED_WITH_ERRORS", 100, 90, {"A", "REMOVED"}, {"A"}, {"REMOVED"}))
        self.assertFalse(daily.price_reuse_allowed("COMPLETED", 89, 90, {"A"}, {"A"}, set()))
        self.assertFalse(daily.price_reuse_allowed("FAILED", 100, 90, {"A"}, {"A"}, {"A"}))
        self.assertFalse(daily.price_reuse_allowed("COMPLETED", 100, 90, {"A", "REMOVED"}, {"A"}, {"A"}))

    def test_new_denial_after_partial_provider_success_blocks_forecast_stage(self):
        class DeniedDatabase:
            def query(self, sql):
                return [["1"]] if "status_code" in sql else [["0"]]
        with self.assertRaises(RuntimeError):
            daily.check_provider(DeniedDatabase())

    def test_short_calendar_blocks_capture_but_not_existing_evaluation(self):
        class EmptyDatabase:
            def query(self, sql):
                return []
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            protocol_root = root/"data/quant-forward-validation-test"
            rows = [dict(session="2026-10-30", openUtc="2026-10-30T13:30:00Z", closeUtc="2026-10-30T20:00:00Z")]
            for index in daily.INDICES:
                directory = protocol_root/index
                directory.mkdir(parents=True)
                protocol = daily.forward.seal(dict(indexCode=index, calendar=rows))
                daily.write_record(directory/"protocol.json", protocol)
            for now in (datetime(2026,10,31,7,tzinfo=timezone.utc), datetime(2026,11,5,7,tzinfo=timezone.utc)):
                with patch.object(daily, "ROOT", root), patch.object(daily, "Database", EmptyDatabase):
                    result = daily.execute(protocol_root, run=True, now=now)
                self.assertEqual(result["status"], "COLLECTION_BLOCKED_CALENDAR_EVALUATION_COMPLETED")
                self.assertEqual(result["evaluations"], [])
            self.assertFalse(list(root.rglob("attempt-*.json")))


if __name__ == "__main__":
    unittest.main()
