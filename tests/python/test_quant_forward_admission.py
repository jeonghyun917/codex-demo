import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
try:
    import quant_forward_admission as admission
except ModuleNotFoundError:
    admission = None


def write_input_evidence(root, run_id, symbols):
    names = ["Valuation", "Quality", "Growth", "Stability", "Earnings", "Analyst", "News", "Momentum", "Risk", "Institution"]
    files, ranked = [], []
    for symbol in symbols:
        path = root / (symbol+"-inputs.json")
        path.write_text(json.dumps(dict(schemaVersion="QUANT_SIGNAL_INPUT_EVIDENCE_V1", runId=run_id, symbol=symbol,
            observedAtUtc="2026-01-03T05:58:00Z", completeHistoricalPIT=False,
            inputs=dict(unrankedFactors=[dict(name=n, score=50, available=False) for n in names]))))
        files.append(dict(symbol=symbol, path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        ranked.append(dict(symbol=symbol, **{n[0].lower()+n[1:]+"Score":50 for n in names}))
    path = root / "inputs.json"
    path.write_text(json.dumps(dict(format="QUANT_PRE_MODEL_INPUT_EVIDENCE_V1", runId=run_id,
        observedAtUtc="2026-01-03T05:59:00Z", signalsRanked=ranked, signalInputFiles=files)))
    return dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest())


class ForwardAdmissionTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(admission, "new-run admission guard is missing")
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.run_id = "11111111-1111-4111-8111-111111111111"
        self.archive = self.root / "archive.json"
        self.archive.write_text(json.dumps(dict(runId=self.run_id, indexCode="DOW30", signalDate="2026-01-03",
            schemaVersion="QUANT_FORWARD_OBSERVATION_V1", capturedAtUtc="2026-01-03T06:00:00Z", modelVersion="V9",
            predictions=[dict(symbol="A", indexCode="DOW30", signalDate="2026-01-03", horizonDays=20,
                              modelVersion="V9", source="LIVE_V9_TEST", expectedReturnPct=1., expectedExcessReturnPct=2.)])))
        self.eligibility = self.root / "eligibility.json"
        self.eligibility.write_text(json.dumps(dict(runId=self.run_id, observedAtUtc="2026-01-03T05:55:00Z",
            completedSession="2026-01-02", indices=dict(DOW30=dict(eligibleNames=["A"], eligibleCurrentClose=1)))))
        self.receipt = self.root / "result.json"
        self.inputs = self.root / "inputs.json"
        input_evidence = write_input_evidence(self.root, self.run_id, ["A"])
        self.value = dict(runId=self.run_id, startedAtUtc="2026-01-03T05:50:00Z", finishedAtUtc="2026-01-03T06:10:00Z",
            expectedCompletedSession="2026-01-02", failedSymbols=[], failedModelStages=[],
            admissionVersion=2, inputEvidence=input_evidence,
            eligibilitySha256=hashlib.sha256(self.eligibility.read_bytes()).hexdigest(),
            capturedArchives=dict(DOW30=dict(path=str(self.archive), sha256=hashlib.sha256(self.archive.read_bytes()).hexdigest())))
        self.receipt.write_text(json.dumps(self.value))

    def test_success_is_bound_to_eligible_names_receipt_archive_and_run(self):
        out = self.root / "admissions"
        records = admission.register(self.eligibility, self.receipt, out)
        self.assertEqual(len(records), 1)
        result = admission.require(self.archive, "2026-01-02", out)
        self.assertEqual(result["eligibleNames"], ["A"])
        self.assertEqual(result["runId"], self.run_id)
        with self.assertRaises(FileExistsError):
            admission.register(self.eligibility, self.receipt, out)

    def test_failed_partial_or_mismatched_runs_cannot_publish_admission(self):
        for field, value in [("failedSymbols", ["A"]), ("failedModelStages", ["SP500"]),
                             ("runId", "22222222-2222-4222-8222-222222222222")]:
            broken = copy.deepcopy(self.value)
            broken[field] = value
            self.receipt.write_text(json.dumps(broken))
            with self.assertRaises(ValueError):
                admission.register(self.eligibility, self.receipt, self.root / "bad")
            self.assertFalse(list((self.root / "bad").glob("*.json")))

    def test_missing_eligible_forecasts_nonfinite_values_or_capture_before_eligibility_rejected(self):
        original = json.loads(self.archive.read_text())
        for mutate in (lambda b: b["predictions"].clear(),
                       lambda b: b["predictions"][0].update(expectedReturnPct=None),
                       lambda b: b.update(capturedAtUtc="2026-01-03T05:54:00Z")):
            body = copy.deepcopy(original)
            mutate(body)
            self.archive.write_text(json.dumps(body))
            self.value["capturedArchives"]["DOW30"]["sha256"] = hashlib.sha256(self.archive.read_bytes()).hexdigest()
            self.receipt.write_text(json.dumps(self.value))
            with self.assertRaises(ValueError):
                admission.register(self.eligibility, self.receipt, self.root / "bad")

    def test_unapproved_archive_and_changed_receipt_rejected_even_if_old_archive_exists(self):
        with self.assertRaises(ValueError):
            admission.require(self.archive, "2026-01-02", self.root / "admissions")
        admission.register(self.eligibility, self.receipt, self.root / "admissions")
        self.receipt.write_text("{}")
        with self.assertRaises(ValueError):
            admission.require(self.archive, "2026-01-02", self.root / "admissions")

    def test_missing_or_changed_input_evidence_cannot_be_admitted(self):
        missing = copy.deepcopy(self.value)
        missing.pop("inputEvidence")
        self.receipt.write_text(json.dumps(missing))
        with self.assertRaisesRegex(ValueError, "input evidence"):
            admission.register(self.eligibility, self.receipt, self.root / "admissions")
        self.receipt.write_text(json.dumps(self.value))
        self.inputs.write_text("{}")
        with self.assertRaisesRegex(ValueError, "input evidence"):
            admission.register(self.eligibility, self.receipt, self.root / "admissions")

    def test_nonfinite_unranked_factor_is_not_accepted_as_observed_input(self):
        source = self.root / 'A-inputs.json'
        body = json.loads(source.read_text())
        body['inputs']['unrankedFactors'][0]['score'] = None
        source.write_text(json.dumps(body))
        inputs = json.loads(self.inputs.read_text())
        inputs['signalInputFiles'][0]['sha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
        self.inputs.write_text(json.dumps(inputs))
        self.value['inputEvidence']['sha256'] = hashlib.sha256(self.inputs.read_bytes()).hexdigest()
        self.receipt.write_text(json.dumps(self.value))
        with self.assertRaisesRegex(ValueError, 'unranked factor'):
            admission.register(self.eligibility, self.receipt, self.root / 'admissions')


if __name__ == "__main__":
    unittest.main()
