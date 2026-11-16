import csv
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
import quant_forward_validation as forward
try:
    import quant_forward_diagnostics as q
except ModuleNotFoundError:
    q = None


class ForwardDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(q, "separate non-tradable-entry diagnosis is missing")
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.manifests = [(Path("original.json"), dict(protocol=dict(calendar=[
            dict(session="2026-01-05", openUtc="2026-01-05T14:30:00Z", closeUtc="2026-01-05T21:00:00Z")]),
            cohorts=[dict(indexCode="SP500", anchorSession="2026-01-02", entrySession="2026-01-05", exitSession="2026-02-02",
                          predictions=[dict(symbol="A"), dict(symbol="GONE")])]))]
        self.prices = self.root / "prices.csv"
        self.prices.write_text("symbol,date,close\nA,2026-01-05,100\n")
        self.proof = self.root / "source.json"
        self.proof.write_text('{"kind":"official source metadata"}')
        self.registry = self.root / "events.json"
        self.event = dict(eventId="GONE_MERGER", symbol="GONE", effectiveSession="2026-01-05",
            effectiveBoundary="MARKET_OPEN", observedAtUtc="2026-01-06T06:00:00Z", sourcePublicationDate="2026-01-05",
            sourceUrl="https://issuer.example/official", proofPath=str(self.proof),
            proofSha256=hashlib.sha256(self.proof.read_bytes()).hexdigest(), rawOriginalAvailable=False)
        self.write_registry([self.event])

    def write_registry(self, events):
        self.registry.write_text(json.dumps(forward.seal(dict(format="QUANT_TRADING_EVENTS_V1", events=events))))

    def test_known_non_tradable_entry_is_separate_and_original_denominator_stays(self):
        result = q.diagnose(self.manifests, self.prices, self.registry,
                            datetime(2026,1,6,7,tzinfo=timezone.utc))
        cohort = result["cohorts"][0]
        self.assertEqual(cohort["expectedNames"], 2)
        self.assertEqual(cohort["counts"], {"ENTRY_CLOSE_PRESENT": 1, "NON_TRADABLE_AT_ENTRY": 1})
        self.assertFalse(result["forecastsChanged"])
        self.assertFalse(result["scoresProduced"])
        self.assertEqual(self.prices.read_text(), "symbol,date,close\nA,2026-01-05,100\n")

    def test_later_acquired_event_is_not_known_before_observation(self):
        result = q.diagnose(self.manifests, self.prices, self.registry,
                            datetime(2026,1,5,22,tzinfo=timezone.utc))
        self.assertEqual(result["cohorts"][0]["counts"], {"ENTRY_CLOSE_PRESENT": 1, "ENTRY_CLOSE_MISSING": 1})

    def test_future_entry_does_not_become_present_from_future_bars(self):
        result = q.diagnose(self.manifests, self.prices, self.registry,
                            datetime(2026,1,5,20,tzinfo=timezone.utc))
        self.assertEqual(result["cohorts"][0]["counts"], {"ENTRY_NOT_DUE": 2})

    def test_duplicate_events_and_tampered_source_evidence_fail_closed(self):
        self.write_registry([self.event, self.event])
        with self.assertRaises(ValueError):
            q.diagnose(self.manifests, self.prices, self.registry, datetime(2026,1,6,7,tzinfo=timezone.utc))
        self.write_registry([self.event])
        self.proof.write_text("changed")
        with self.assertRaises(ValueError):
            q.diagnose(self.manifests, self.prices, self.registry, datetime(2026,1,6,7,tzinfo=timezone.utc))


if __name__ == "__main__":
    unittest.main()
