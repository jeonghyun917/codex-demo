"""Source-aware bridge plans must not turn associations into destructive repairs."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
import quant_source_bridge as bridge


class SourceBridgeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.source = Path(self.tmp.name) / "release.html"
        self.source.write_bytes(b"issuer quarterly consolidated total revenue 50")
        digest = hashlib.sha256(self.source.read_bytes()).hexdigest()
        identity = {
            "symbol": "AMGN", "cik": "0000318154",
            "sourceKind": "OFFICIAL_ISSUER_EARNINGS_RELEASE",
            "sourceDocumentId": "https://www.amgen.com/newsroom/quarter",
            "sourceSha256": digest, "publicationDate": "2020-01-30",
            "secAccessionNumber": None, "secFiledDate": None,
            "startDate": "2019-10-01", "endDate": "2019-12-31",
            "fiscalPeriod": "Q4", "currency": "USD",
        }
        self.candidate = {
            "candidateId": "AMGN_Q4", "canonicalIdentity": identity,
            "sourceLocalPath": str(self.source),
            "availability": {"publicDate": "2020-01-30", "precision": "DATE_ONLY",
                             "publicAt": None, "publicationTimeZone": None,
                             "systemAcquiredAt": None},
            "values": {"revenue": "50", "netIncome": None,
                       "epsDiluted": None, "operatingIncome": None},
            "units": {"revenue": "USD", "netIncome": None,
                      "epsDiluted": None, "operatingIncome": None},
            "fieldProofs": {"revenue": {"sourceURL": identity["sourceDocumentId"],
                "sourceSha256": digest, "sourcePublicationDate": "2020-01-30",
                "currency": "USD", "printedValue": "50", "unitsScale": 1,
                "totalScopeConfirmed": True}},
            "periodProof": {"printedPeriod": "Three months ended December 31",
                "printedYear": 2019, "startDateBasis": "explicit calendar Q4",
                "endDateLiteralPrinted": True},
            "operationallyAdmitted": False,
        }
        self.pack = {"version": "fixture-v1", "candidates": [self.candidate],
                     "legacyTargetAssociations": [], "legacyRecoveryAccounting": {
                         "originalMissingDenominator": 203, "exactRecoveredBefore": 189,
                         "exactRecoveredAfter": 189, "unresolvedAfter": 14}}

    def test_plan_preserves_nulls_source_kind_and_disallows_live_apply(self):
        result = bridge.build_plan(self.pack, decision_date="2020-01-31")
        row = result["candidates"][0]
        self.assertEqual("RESEARCH_DATE_ELIGIBLE_ONLY", row["eligibility"])
        self.assertEqual("OFFICIAL_ISSUER_EARNINGS_RELEASE", row["identity"]["sourceKind"])
        self.assertIsNone(row["identity"]["secAccessionNumber"])
        self.assertIsNone(row["values"]["operatingIncome"])
        self.assertEqual([], result["databaseOperations"])
        self.assertFalse(result["operationallyAdmitted"])
        self.assertEqual(189, result["legacyRecoveryAccounting"]["exactRecoveredAfter"])

    def test_publication_date_is_not_same_day_or_intraday_permission(self):
        self.assertEqual("NOT_DATE_ELIGIBLE", bridge.build_plan(
            self.pack, decision_date="2020-01-30")["candidates"][0]["eligibility"])
        self.assertEqual("STRICT_TIMESTAMP_UNPROVED", bridge.build_plan(
            self.pack, decision_date="2020-01-31", require_timestamp=True
        )["candidates"][0]["eligibility"])

    def test_duplicate_id_alias_and_ambiguous_sources_are_rejected(self):
        for mutation, expected in (("id", "duplicate candidate"),
                                   ("alias", "duplicate canonical"),
                                   ("differentSource", "ambiguous logical")):
            pack = copy.deepcopy(self.pack)
            other = copy.deepcopy(self.candidate)
            if mutation != "id":
                other["candidateId"] = "ALIAS"
            if mutation == "differentSource":
                other["canonicalIdentity"]["sourceDocumentId"] += "-other"
                other["fieldProofs"]["revenue"]["sourceURL"] += "-other"
            pack["candidates"].append(other)
            with self.subTest(mutation=mutation), self.assertRaisesRegex(ValueError, expected):
                bridge.build_plan(pack)

    def test_source_drift_borrowed_accession_or_unproved_units_fail_closed(self):
        for mutation in ("bytes", "accession", "unit", "scope", "value"):
            pack = copy.deepcopy(self.pack)
            if mutation == "bytes":
                pack["candidates"][0]["canonicalIdentity"]["sourceSha256"] = "0" * 64
            elif mutation == "accession":
                pack["candidates"][0]["canonicalIdentity"]["secAccessionNumber"] = "borrowed"
            elif mutation == "unit":
                pack["candidates"][0]["units"]["revenue"] = "USD/shares"
            elif mutation == "scope":
                pack["candidates"][0]["fieldProofs"]["revenue"]["totalScopeConfirmed"] = False
            else:
                pack["candidates"][0]["values"]["revenue"] = "NaN"
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                bridge.build_plan(pack)

    def test_q4_derivation_cannot_be_relabelled_with_an_arbitrary_start(self):
        self.candidate["canonicalIdentity"]["startDate"] = "2019-09-30"
        with self.assertRaisesRegex(ValueError, "Q4"):
            bridge.build_plan(self.pack)

    def test_zero_is_a_present_value_not_a_missing_field(self):
        self.candidate["values"]["revenue"] = "0"
        self.candidate["fieldProofs"]["revenue"]["printedValue"] = "0"
        existing = [{"identity": self.candidate["canonicalIdentity"],
                     "values": {"revenue": None}, "units": {"revenue": None}}]
        row = bridge.build_plan(self.pack, existing_rows=existing)["candidates"][0]
        self.assertEqual("0", row["values"]["revenue"])
        self.assertEqual("NULL_FILL_REQUIRES_SEPARATE_ADMISSION", row["fieldComparison"]["revenue"])

    def test_nonnull_conflicts_and_source_nulls_are_never_overwritten(self):
        existing = [{"identity": self.candidate["canonicalIdentity"],
                     "values": {"revenue": "49", "netIncome": "7", "epsDiluted": None,
                                "operatingIncome": None},
                     "units": {"revenue": "USD", "netIncome": "USD",
                               "epsDiluted": None, "operatingIncome": None}}]
        result = bridge.build_plan(self.pack, existing_rows=existing)
        comparison = result["candidates"][0]["fieldComparison"]
        self.assertEqual("NONNULL_CONFLICT_REJECTED", comparison["revenue"])
        self.assertEqual("SOURCE_NULL_PRESERVE_EXISTING", comparison["netIncome"])
        self.assertEqual("BOTH_NULL", comparison["operatingIncome"])
        self.assertEqual([], result["databaseOperations"])
        self.assertEqual("49", existing[0]["values"]["revenue"])

    def test_legacy_period_is_not_rewritten_and_output_is_create_only(self):
        self.pack["legacyTargetAssociations"] = [{"candidateId": "AMGN_Q4",
            "targetIdentity": {"symbol": "AMGN", "startDate": "2019-09-30"},
            "candidateOverridesLegacy": False, "legacyTargetUnchanged": True}]
        before = copy.deepcopy(self.pack)
        output = Path(self.tmp.name) / "plan.json"
        bridge.write_plan(self.pack, output)
        result = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual("2019-09-30", result["legacyTargetAssociations"][0]["targetIdentity"]["startDate"])
        self.assertEqual("2019-10-01", result["candidates"][0]["identity"]["startDate"])
        self.assertEqual(before, self.pack)
        with self.assertRaises(FileExistsError):
            bridge.write_plan(self.pack, output)


if __name__ == "__main__":
    unittest.main()
