import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("extra", Path(__file__).resolve().parents[3] / "scripts/acquire_quant_extra_evidence.py")
extra = importlib.util.module_from_spec(spec)
spec.loader.exec_module(extra)


class ExtraEvidenceTests(unittest.TestCase):
    def test_html_entities_and_script_exclusion(self):
        self.assertEqual("A & B $95.00", extra.plain_text(b"<p>A &amp; B</p><script>not proof</script><p>$95.00</p>"))

    def test_missing_assertion_is_rejected(self):
        with self.assertRaises(ValueError):
            extra.validate_assertions(b"<p>Cerner $95.00</p>", ["June 8, 2022"])

    def test_script_only_assertion_is_rejected(self):
        with self.assertRaises(ValueError):
            extra.validate_assertions(b"<script>1.7234</script>", ["1.7234"])

    def test_cvr_never_added_to_cash(self):
        source = next(s for s in extra.SPECS if s["symbol"] == "ABMD")
        event = extra.make_event(source)
        self.assertEqual("380.00", event["cashPerShare"])
        self.assertEqual("35.00", event["contingentAdditionalCashMaximum"])
        self.assertIn("NOT_TERMINAL_CASH", event["cvrStatus"])

    def test_unknown_recovery_stays_missing(self):
        event = extra.make_event(next(s for s in extra.SPECS if s["symbol"] == "FRC"))
        self.assertIsNone(event["recoveryPerShare"])
        self.assertNotIn("cashPerShare", event)

    def test_all_sample_consideration_is_nonnegative_and_not_prices(self):
        self.assertEqual(8, len({s["symbol"] for s in extra.SPECS}))
        for source in extra.SPECS:
            event = extra.make_event(source)
            self.assertIn("NOT_OHLCV", event["priceUse"])
            self.assertEqual("NOT_CERTIFIED_PIT", event["announcementKnowledge"])


if __name__ == "__main__":
    unittest.main()
