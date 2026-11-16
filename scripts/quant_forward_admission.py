"""Admission of new batch archives; legacy frozen cohorts are never rewritten."""
import json
from pathlib import Path
import re

import quant_forward_validation as forward


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def checked_records(eligibility_path, receipt_path):
    eligibility_path, receipt_path = Path(eligibility_path).resolve(), Path(receipt_path).resolve()
    eligibility, receipt = read(eligibility_path), read(receipt_path)
    run_id = receipt.get("runId")
    if (not isinstance(run_id, str) or not re.fullmatch(r"[0-9a-f-]{36}", run_id)
            or run_id != eligibility.get("runId") or receipt.get("failedSymbols") != []
            or receipt.get("failedModelStages") != []):
        raise ValueError("A complete successful bound run is required")
    eligibility_sha = forward.digest(eligibility_path.read_bytes())
    if receipt.get("eligibilitySha256") != eligibility_sha:
        raise ValueError("Preforecast eligibility changed")
    anchor = eligibility.get("completedSession")
    if anchor != receipt.get("expectedCompletedSession"):
        raise ValueError("Completed-session binding mismatch")
    begin, observed, end = (forward.timestamp(receipt["startedAtUtc"]),
                            forward.timestamp(eligibility["observedAtUtc"]), forward.timestamp(receipt["finishedAtUtc"]))
    if not begin <= observed <= end <= forward.now_utc():
        raise ValueError("Invalid run/eligibility availability order")
    evidence = receipt.get("inputEvidence")
    if receipt.get("admissionVersion") != 2 or not isinstance(evidence, dict):
        raise ValueError("Bound pre-model input evidence is required")
    input_path = Path(evidence["path"]).resolve()
    if forward.digest(input_path.read_bytes()) != evidence.get("sha256"):
        raise ValueError("Pre-model input evidence changed")
    inputs = read(input_path)
    input_at = forward.timestamp(inputs["observedAtUtc"])
    expected_symbols = {symbol for row in eligibility["indices"].values() for symbol in row.get("eligibleNames", [])}
    ranked = inputs.get("signalsRanked", [])
    source_inputs = inputs.get("signalInputFiles", [])
    if (inputs.get("format") != "QUANT_PRE_MODEL_INPUT_EVIDENCE_V1" or inputs.get("runId") != run_id
            or not observed <= input_at <= end or len(ranked) != len(expected_symbols)
            or {r.get("symbol") for r in ranked} != expected_symbols or len(source_inputs) != len(expected_symbols)
            or {r.get("symbol") for r in source_inputs} != expected_symbols):
        raise ValueError("Incomplete pre-model input evidence/feature population")
    factor_names = {"Valuation", "Quality", "Growth", "Stability", "Earnings", "Analyst", "News", "Momentum", "Risk", "Institution"}
    for row in ranked:
        for factor in factor_names:
            try:
                score = forward.number(row.get(factor[0].lower()+factor[1:]+"Score"))
            except (TypeError, ValueError) as error:
                raise ValueError("Missing actual ranked factor value") from error
            if not 0 <= score <= 100:
                raise ValueError("Invalid ranked factor value")
    for source in source_inputs:
        path = Path(source["path"])
        if forward.digest(path.read_bytes()) != source["sha256"]:
            raise ValueError("Signal input evidence changed")
        body = read(path)
        factors = body.get("inputs", {}).get("unrankedFactors", [])
        if (body.get("runId") != run_id or body.get("symbol") != source["symbol"]
                or body.get("schemaVersion") != "QUANT_SIGNAL_INPUT_EVIDENCE_V1"
                or not observed <= forward.timestamp(body["observedAtUtc"]) <= input_at
                or len(factors) != 10 or {r.get("name") for r in factors} != factor_names
                or any(not isinstance(r.get("available"), bool) for r in factors)):
            raise ValueError("Invalid selected input identity/availability evidence")
        for factor in factors:
            try:
                score = forward.number(factor.get('score'))
            except (ValueError, TypeError) as error:
                raise ValueError('Missing actual unranked factor value') from error
            if not 0 <= score <= 100:
                raise ValueError('Invalid unranked factor value')
    archives = receipt.get("capturedArchives", {})
    if not archives or set(archives) != set(eligibility.get("indices", {})):
        raise ValueError("Every planned index needs its own archive")
    records = []
    for index, source in sorted(archives.items()):
        if index not in {"DOW30", "SP500", "NASDAQ100"}:
            raise ValueError("Unexpected index")
        path = Path(source["path"]).resolve()
        sha = forward.digest(path.read_bytes())
        if sha != source.get("sha256"):
            raise ValueError("Archive content does not match the successful run")
        archive = read(path)
        names = eligibility["indices"][index].get("eligibleNames", [])
        if (not names or len(names) != len(set(names)) or
                any(not isinstance(s, str) or not re.fullmatch(r"[A-Z0-9.^_=-]{1,40}", s) for s in names)
                or len(names) != eligibility["indices"][index].get("eligibleCurrentClose")):
            raise ValueError("Exact nonempty preforecast eligible names required")
        if (archive.get("runId") != run_id or archive.get("indexCode") != index
                or archive.get("schemaVersion") != "QUANT_FORWARD_OBSERVATION_V1"
                or not input_at <= forward.timestamp(archive["capturedAtUtc"]) <= end):
            raise ValueError("Archive run/index/capture binding mismatch")
        predictions = archive.get("predictions", [])
        if len(predictions) != len(names) or {p.get("symbol") for p in predictions} != set(names):
            raise ValueError("Archive lost or added eligible securities")
        for prediction in predictions:
            if (prediction.get("indexCode") != index or prediction.get("signalDate") != archive.get("signalDate")
                    or prediction.get("horizonDays") != 20 or prediction.get("modelVersion") != archive.get("modelVersion")
                    or not str(prediction.get("source", "")).startswith("LIVE_V9_")):
                raise ValueError("Prediction identity/provenance mismatch")
            try:
                forward.number(prediction.get("expectedReturnPct"))
                forward.number(prediction.get("expectedExcessReturnPct"))
            except (TypeError, ValueError) as error:
                raise ValueError("Both forecast heads must be finite for every eligible name") from error
        records.append(dict(format="QUANT_NEW_RUN_ADMISSION_V2", runId=run_id, indexCode=index,
                            anchorSession=anchor, eligibleNames=sorted(names), archivePath=str(path), archiveSha256=sha,
                            eligibilityPath=str(eligibility_path), eligibilitySha256=eligibility_sha,
                            receiptPath=str(receipt_path), receiptSha256=forward.digest(receipt_path.read_bytes()),
                            inputEvidencePath=str(input_path), inputEvidenceSha256=evidence["sha256"]))
    return records


def register(eligibility_path, receipt_path, output):
    records = checked_records(eligibility_path, receipt_path)
    output = Path(output)
    if any((output / (r["archiveSha256"]+".json")).exists() for r in records):
        raise FileExistsError("Admission is immutable; inspect existing run instead of replacing it")
    output.mkdir(parents=True, exist_ok=True)
    for record in records:
        forward.write_immutable(output / (record["archiveSha256"]+".json"), forward.seal(record))
    return records


def require(archive_path, anchor, directory):
    sha = forward.digest(Path(archive_path).read_bytes())
    path = Path(directory) / (sha+".json")
    if not path.is_file():
        raise ValueError("New archive lacks validated successful-run admission; inspect, do not auto-admit")
    record = read(path)
    forward.verify_seal(record)
    if record.get("archiveSha256") != sha or record.get("anchorSession") != anchor:
        raise ValueError("Admission archive/anchor mismatch")
    for field in ("eligibility", "receipt"):
        if forward.digest(Path(record[field+"Path"]).read_bytes()) != record[field+"Sha256"]:
            raise ValueError("Admission evidence changed")
    current = checked_records(record["eligibilityPath"], record["receiptPath"])
    match = [r for r in current if r["archiveSha256"] == sha]
    if len(match) != 1 or any(record.get(key) != value for key, value in match[0].items()):
        raise ValueError("Admission no longer matches its exact successful run")
    return record
