"""Synthetic-only causal, maturity and immutable-output contract tests."""
import importlib.util
import json
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
import tempfile
import subprocess
import sys
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / "scripts/quant_forward_validation.py"
spec = importlib.util.spec_from_file_location("forward_validation", SCRIPT)
forward = importlib.util.module_from_spec(spec) if SCRIPT.exists() else None
if forward:
    spec.loader.exec_module(forward)


def utc(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


class ForwardValidationTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(forward, "Forward evaluator must exist")
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        # Explicit holiday omission and DST transition; never infer NY timezone.
        self.days = ["2026-03-05", "2026-03-06", "2026-03-09"]
        day = date(2026, 3, 10)
        while len(self.days) < 27:
            if day.weekday() < 5 and day.isoformat() != "2026-03-13":
                self.days.append(day.isoformat())
            day += timedelta(days=1)
        rows = ["session,openUtc,closeUtc"]
        for d in self.days:
            opening, closing = ("14:30", "21:00") if d < "2026-03-08" else ("13:30", "20:00")
            rows.append(f"{d},{d}T{opening}:00Z,{d}T{closing}:00Z")
        self.calendar = self.root / "calendar.csv"
        self.calendar.write_text("\n".join(rows), encoding="utf-8")
        self.protocol = forward.create_protocol(self.calendar, "BENCH", now=utc("2026-03-06T20:00:00Z"))

    def observation(self, captured="2026-03-07T01:00:00Z", symbols=("AAA", "BBB")):
        return {"schemaVersion": "QUANT_FORWARD_OBSERVATION_V1", "capturedAtUtc": captured,
                "indexCode": "SP500", "signalDate": "2099-01-01", "modelVersion": "V9",
                "predictions": [{"symbol": s, "horizonDays": 20, "modelVersion": "V9",
                                 "source": "LIVE_V9_TEST", "expectedReturnPct": 10,
                                 "expectedExcessReturnPct": 6} for s in symbols]}

    def freeze(self, observations=None):
        archive = self.root / "archive"
        archive.mkdir(exist_ok=True)
        for i, item in enumerate(observations or [self.observation()]):
            (archive / f"{i}.json").write_text(json.dumps(item), encoding="utf-8")
        return forward.freeze_cohort(self.protocol, archive, now=utc("2026-03-09T19:00:00Z"))

    def prices(self, missing=None, duplicate=False):
        rows = ["symbol,date,close"]
        for i, d in enumerate(self.days):
            for symbol in ("AAA", "BBB", "BENCH"):
                if (symbol, d) == missing:
                    continue
                # Entry is index 2 at 100; exit index 22 at 110 or 104.
                close = 100 + (i - 2) * (.2 if symbol == "BENCH" else .5)
                rows.append(f"{symbol},{d},{close}")
        if duplicate:
            rows.append(rows[1])
        target = self.root / "prices.csv"
        target.write_text("\n".join(rows), encoding="utf-8")
        return target

    def test_anchor_uses_closed_exchange_session_not_signal_date_or_utc_day(self):
        manifest = self.freeze()
        row = manifest["cohorts"][0]
        self.assertEqual("2026-03-06", row["anchorSession"])
        self.assertEqual("2026-03-09", row["entrySession"])
        self.assertEqual(self.days[22], row["exitSession"])
        self.assertEqual(["AAA", "BBB"], [p["symbol"] for p in row["predictions"]])

    def test_intraday_before_dst_adjusted_close_qualifies_current_session_entry(self):
        manifest = forward.freeze_cohort(self.protocol, self._archive(self.observation("2026-03-09T19:59:59Z")),
                                          now=utc("2026-03-09T19:59:59Z"))
        self.assertEqual("2026-03-09", manifest["cohorts"][0]["entrySession"])

    def test_capture_at_close_rolls_anchor_and_entry_forward(self):
        manifest = forward.freeze_cohort(self.protocol, self._archive(self.observation("2026-03-09T20:00:00Z")),
                                          now=utc("2026-03-09T20:00:00Z"))
        self.assertEqual("2026-03-10", manifest["cohorts"][0]["entrySession"])

    def _archive(self, observation):
        archive = self.root / "at-close"
        archive.mkdir()
        (archive / "one.json").write_text(json.dumps(observation), encoding="utf-8")
        return archive

    def test_pre_protocol_and_future_capture_not_relabelled_prospective(self):
        manifest = self.freeze([self.observation("2026-03-06T19:59:59Z"),
                                self.observation("2026-03-10T12:00:00Z")])
        self.assertEqual([], manifest["cohorts"])
        self.assertEqual({"BEFORE_PROTOCOL", "AFTER_FREEZE"}, {r["reason"] for r in manifest["excludedArchives"]})

    def test_earliest_capture_freezes_names_without_looking_at_future_prices(self):
        manifest = self.freeze([self.observation(symbols=("AAA", "BBB")),
                                self.observation("2026-03-08T01:00:00Z", ("AAA",))])
        self.assertEqual(1, len(manifest["cohorts"]))
        report = forward.evaluate(manifest, self.prices(missing=("BBB", self.days[10])),
                                  as_of=utc(self.days[22] + "T20:00:00Z"))
        self.assertEqual({"MATURE": 1, "INSUFFICIENT_PRICES": 1}, report["counts"])
        self.assertEqual(2, len(report["observations"]))
        self.assertEqual(1, report["metrics"]["absolute"]["n"])
        self.assertAlmostEqual(0, report["metrics"]["absolute"]["modelMaePct"], places=10)
        self.assertAlmostEqual(10, report["metrics"]["absolute"]["zeroMaePct"], places=10)
        self.assertAlmostEqual(0, report["metrics"]["excess"]["modelMaePct"], places=10)

    def test_twenty_sessions_after_entry_not_twenty_close_observations(self):
        manifest = self.freeze()
        report = forward.evaluate(manifest, self.prices(), as_of=utc(self.days[21] + "T20:00:00Z"))
        self.assertEqual({"PENDING_OUTCOME": 2}, report["counts"])
        self.assertEqual(0, report["metrics"]["absolute"]["n"])
        self.assertIsNone(report["metrics"]["absolute"]["modelMaePct"])

    def test_future_bars_cannot_mature_before_exchange_close(self):
        report = forward.evaluate(self.freeze(), self.prices(), as_of=utc(self.days[22] + "T19:59:59Z"))
        self.assertEqual({"PENDING_OUTCOME": 2}, report["counts"])

    def test_duplicate_symbols_and_price_rows_fail_not_silently_weighted(self):
        with self.assertRaisesRegex(ValueError, "duplicate symbol"):
            self.freeze([self.observation(symbols=("AAA", "AAA"))])
        with self.assertRaisesRegex(ValueError, "duplicate price"):
            forward.evaluate(self.freeze(), self.prices(duplicate=True),
                             as_of=utc(self.days[22] + "T20:00:00Z"))

    def test_calendar_rejects_unsorted_and_naive_time(self):
        self.calendar.write_text("session,openUtc,closeUtc\n2026-03-06,2026-03-06T14:30:00,2026-03-06T21:00:00Z", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "timezone"):
            forward.create_protocol(self.calendar, "BENCH")

    def test_fractional_second_sort_preserves_earliest_observation(self):
        manifest = self.freeze([self.observation("2026-03-07T01:00:00.1Z", ("AAA",)),
                                self.observation("2026-03-07T01:00:00Z", ("AAA", "BBB"))])
        self.assertEqual(2, len(manifest["cohorts"][0]["predictions"]))

    def test_freeze_after_entry_close_cannot_create_retrospective_cohort(self):
        manifest = forward.freeze_cohort(self.protocol, self._archive(self.observation()),
                                          now=utc("2026-03-09T20:00:00Z"))
        self.assertEqual([], manifest["cohorts"])
        self.assertEqual("ENTRY_CUTOFF_PASSED", manifest["excludedArchives"][0]["reason"])

    def test_other_index_is_excluded_without_pooling_its_excess_target(self):
        other = self.observation("2026-03-07T02:00:00Z")
        other["indexCode"] = "NASDAQ100"
        manifest = self.freeze([self.observation(), other])
        self.assertEqual(["SP500"], [c["indexCode"] for c in manifest["cohorts"]])
        self.assertEqual("INDEX_BENCHMARK_MISMATCH", manifest["excludedArchives"][0]["reason"])

    def test_single_wrong_index_cannot_use_default_sp500_benchmark(self):
        other = self.observation()
        other["indexCode"] = "NASDAQ100"
        manifest = self.freeze([other])
        self.assertEqual([], manifest["cohorts"])
        self.assertEqual("INDEX_BENCHMARK_MISMATCH", manifest["excludedArchives"][0]["reason"])

    def test_explicit_index_protocol_accepts_only_its_matching_archive(self):
        self.protocol = forward.create_protocol(self.calendar, "NASDAQ_BENCH", index_code="NASDAQ100",
                                                now=utc("2026-03-06T20:00:00Z"))
        other = self.observation("2026-03-07T02:00:00Z")
        other["indexCode"] = "NASDAQ100"
        manifest = self.freeze([self.observation(), other])
        self.assertEqual("NASDAQ100", manifest["protocol"]["indexCode"])
        self.assertEqual(["NASDAQ100"], [c["indexCode"] for c in manifest["cohorts"]])

    def test_evaluator_refuses_wrong_index_even_in_validly_hashed_manifest(self):
        manifest = self.freeze()
        manifest["cohorts"][0]["indexCode"] = "DOW30"
        del manifest["contentSha256"]
        forward.seal(manifest)
        with self.assertRaisesRegex(ValueError, "index.*benchmark"):
            forward.evaluate(manifest, self.prices(), as_of=utc(self.days[22] + "T20:00:00Z"))

    def test_legacy_protocol_without_index_binding_requires_new_initialization(self):
        del self.protocol["indexCode"]
        del self.protocol["contentSha256"]
        forward.seal(self.protocol)
        with self.assertRaisesRegex(ValueError, "explicit index/benchmark"):
            self.freeze()

    def test_missing_benchmark_held_close_is_not_successful_mature_outcome(self):
        report = forward.evaluate(self.freeze(), self.prices(missing=("BENCH", self.days[10])),
                                  as_of=utc(self.days[22] + "T20:00:00Z"))
        self.assertEqual({"INSUFFICIENT_PRICES": 2}, report["counts"])

    def test_tampered_frozen_cohort_is_rejected(self):
        manifest = self.freeze()
        manifest["cohorts"][0]["predictions"].pop()
        with self.assertRaisesRegex(ValueError, "integrity"):
            forward.evaluate(manifest, self.prices(), as_of=utc(self.days[22] + "T20:00:00Z"))

    def test_simultaneous_conflicting_captures_fail_and_exact_duplicate_is_not_counted_twice(self):
        manifest = self.freeze([self.observation(), self.observation()])
        self.assertEqual(1, len(manifest["cohorts"]))
        with self.assertRaisesRegex(ValueError, "conflicting"):
            self.freeze([self.observation(), self.observation(symbols=("AAA",))])

    def test_baseline_is_fixed_and_outputs_never_overwrite(self):
        baseline = {"absolutePct": 2, "excessPct": 1, "trainingThroughUtc": "2026-03-05T21:00:00Z"}
        self.protocol = forward.create_protocol(self.calendar, "BENCH", baseline=baseline,
                                                now=utc("2026-03-06T20:00:00Z"))
        report = forward.evaluate(self.freeze(), self.prices(), as_of=utc(self.days[22] + "T20:00:00Z"))
        self.assertAlmostEqual(8, report["metrics"]["absolute"]["historicalBaselineMaePct"])
        target = self.root / "result.json"
        forward.write_immutable(target, report)
        with self.assertRaises(FileExistsError):
            forward.write_immutable(target, {"changed": True})
        self.assertEqual(report, json.loads(target.read_text(encoding="utf-8")))

    def test_cli_initializes_now_and_cannot_relabel_historical_archive(self):
        target = self.root / "protocol.json"
        args = [sys.executable, str(SCRIPT), "init", "--calendar", str(self.calendar),
                "--benchmark-symbol", "BENCH", "--out", str(target)]
        before = datetime.now(timezone.utc)
        result = subprocess.run(args, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        protocol = json.loads(target.read_text(encoding="utf-8"))
        self.assertEqual("SP500", protocol["indexCode"])
        self.assertGreaterEqual(utc(protocol["startedAtUtc"]), before)
        self.assertLessEqual(utc(protocol["startedAtUtc"]), datetime.now(timezone.utc))
        other_target = self.root / "nasdaq-protocol.json"
        other_args = args[:-1] + [str(other_target), "--index-code", "NASDAQ100"]
        other = subprocess.run(other_args, capture_output=True, text=True)
        self.assertEqual(0, other.returncode, other.stderr)
        self.assertEqual("NASDAQ100", json.loads(other_target.read_text(encoding="utf-8"))["indexCode"])
        again = subprocess.run(args, capture_output=True, text=True)
        self.assertNotEqual(0, again.returncode)
        archive = self._archive(self.observation())
        manifest_path = self.root / "manifest.json"
        result = subprocess.run([sys.executable, str(SCRIPT), "freeze", "--protocol", str(target),
                                 "--archive", str(archive), "--out", str(manifest_path)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual([], manifest["cohorts"])
        self.assertEqual("BEFORE_PROTOCOL", manifest["excludedArchives"][0]["reason"])


if __name__ == "__main__":
    unittest.main()
