"""Synthetic-only regressions for time advancing between capture and evaluation."""
import json
import sys
import tempfile
import unittest
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
import quant_daily_forward as daily
import quant_forward_admission as admission
import hashlib
from test_quant_forward_admission import write_input_evidence


class EmptyDatabase:
    def query(self, sql):
        return []


class DailyClockTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.protocol_root = self.root / "data/quant-forward-validation-clock"
        rows = []
        day = date(2026, 1, 2)
        while len(rows) < 27:
            if day.weekday() < 5:
                rows.append(dict(session=str(day), openUtc=f"{day}T14:30:00Z",
                                 closeUtc=f"{day}T21:00:00Z"))
            day += timedelta(days=1)
        self.started = datetime(2026, 1, 3, 7, tzinfo=timezone.utc)
        self.frozen = datetime(2026, 1, 3, 7, 5, tzinfo=timezone.utc)
        self.evaluated = datetime(2026, 1, 3, 7, 6, tzinfo=timezone.utc)
        for index in daily.INDICES:
            folder = self.protocol_root / index
            folder.mkdir(parents=True)
            protocol = daily.forward.seal(dict(indexCode=index, calendar=rows,
                startedAtUtc="2026-01-02T20:00:00Z", benchmarkSymbol="BENCH_" + index,
                fixedHistoricalBaseline=None))
            daily.write_record(folder / "protocol.json", protocol)
            archive = self.root / "data/quant-forward" / index
            archive.mkdir(parents=True)
            daily.write_record(archive / "original.json", dict(
                schemaVersion="QUANT_FORWARD_OBSERVATION_V1", indexCode=index,
                capturedAtUtc="2026-01-03T06:00:00Z", signalDate="2026-01-03", modelVersion="V9",
                predictions=[dict(symbol="AAA", horizonDays=20, modelVersion="V9",
                    source="LIVE_V9_SYNTHETIC", expectedReturnPct=1., expectedExcessReturnPct=2.)]))
        run_id = "11111111-1111-4111-8111-111111111111"
        captures = {}
        for index in daily.INDICES:
            path = self.root / "data/quant-forward" / index / "original.json"
            body = daily.read(path)
            body["runId"] = run_id
            body["predictions"][0].update(indexCode=index, signalDate="2026-01-03")
            path.write_text(json.dumps(body))
            captures[index] = dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest())
        eligibility = self.root / "eligibility.json"
        daily.write_record(eligibility, dict(runId=run_id, observedAtUtc="2026-01-03T05:55:00Z",
            completedSession="2026-01-02", indices={i: dict(eligibleNames=["AAA"], eligibleCurrentClose=1) for i in daily.INDICES}))
        receipt = self.root / "receipt.json"
        daily.write_record(receipt, dict(runId=run_id, startedAtUtc="2026-01-03T05:50:00Z", finishedAtUtc="2026-01-03T06:10:00Z",
            expectedCompletedSession="2026-01-02", failedSymbols=[], failedModelStages=[], capturedArchives=captures,
            admissionVersion=2, inputEvidence=write_input_evidence(self.root, run_id, ["AAA"]),
            eligibilitySha256=hashlib.sha256(eligibility.read_bytes()).hexdigest()))
        admission.register(eligibility, receipt, self.root / "data/quant-forward-admission")

    def run_live(self):
        instants = iter([self.started, self.evaluated, self.evaluated])

        class AdvancingClock(datetime):
            @classmethod
            def now(cls, tz=None):
                return next(instants)

        with patch.object(daily, "ROOT", self.root), patch.object(daily, "Database", EmptyDatabase), \
                patch.object(daily, "datetime", AdvancingClock), \
                patch.object(daily.forward, "now_utc", return_value=self.frozen):
            return daily.execute(self.protocol_root, run=True)

    def test_newly_frozen_archive_is_evaluated_at_actual_later_time_without_recollection(self):
        result = self.run_live()
        self.assertEqual(result["status"], "COMPLETED")
        self.assertEqual(result["asOfUtc"], "2026-01-03T07:00:00Z")
        self.assertEqual(result["evaluationAsOfUtc"], "2026-01-03T07:06:00Z")
        self.assertEqual(len(result["newFrozenManifests"]), 3)
        self.assertTrue(result["collectionSkippedAlreadyFrozen"])
        self.assertFalse(list(self.root.rglob("attempt-*.json")))
        self.assertEqual(len(result["evaluations"]), 3)
        for item in result["evaluations"]:
            report = json.loads(Path(item["report"]).read_text())
            self.assertEqual(report["asOfUtc"], "2026-01-03T07:06:00Z")
            self.assertEqual(report["counts"], {"PENDING_OUTCOME": 1})
            self.assertEqual(report["metrics"]["absolute"]["n"], 0)
            self.assertIsNone(report["metrics"]["absolute"]["modelMaePct"])

    def test_unapproved_existing_archive_is_not_silently_frozen(self):
        directory = self.root / "data/quant-forward-admission"
        if directory.exists():
            for path in directory.iterdir():
                path.unlink()  # Synthetic temporary fixture only.
        with self.assertRaisesRegex(ValueError, "admission"):
            self.run_live()
        self.assertFalse(list(self.protocol_root.rglob("cohort-*.json")))

    def test_calendar_blocked_evaluation_also_uses_current_phase_clock(self):
        for folder in self.protocol_root.iterdir():
            path = folder / "protocol.json"
            protocol = daily.read(path)
            protocol.pop("contentSha256")
            protocol["calendar"] = protocol["calendar"][:1]
            path.write_text(json.dumps(daily.forward.seal(protocol)))
        result = self.run_live()
        self.assertEqual(result["status"], "COLLECTION_BLOCKED_CALENDAR_EVALUATION_COMPLETED")
        self.assertEqual(result["evaluationAsOfUtc"], "2026-01-03T07:06:00Z")
        self.assertFalse(list(self.root.rglob("attempt-*.json")))

    def test_fixed_synthetic_time_still_rejects_future_freeze_and_preserves_error_exports(self):
        with patch.object(daily, "ROOT", self.root), patch.object(daily, "Database", EmptyDatabase), \
                patch.object(daily.forward, "now_utc", return_value=self.frozen):
            with self.assertRaisesRegex(ValueError, "evaluation cannot precede cohort freeze"):
                daily.execute(self.protocol_root, run=True, now=self.started)
        failure_path, = self.root.rglob("failure.json")
        failure = json.loads(failure_path.read_text())
        self.assertEqual(failure["message"], "evaluation cannot precede cohort freeze")
        self.assertEqual(failure["evaluationError"], "evaluation cannot precede cohort freeze")
        self.assertTrue((failure_path.parent / "held-closes.csv").exists())
        self.assertTrue((failure_path.parent / "failure-evaluation/held-closes.csv").exists())


if __name__ == "__main__":
    unittest.main()
