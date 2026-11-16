"""Create-only source-aware research plan; deliberately has no DB apply mode.

The operational table's SEC-only non-null accession and natural key cannot encode
issuer releases or a second source period for the same filing. A plan is evidence,
not permission to invent an accession, mutate a legacy key, or use unknown times.
"""
import argparse
from copy import deepcopy
from datetime import date, datetime, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlparse

IDENTITY = ("symbol", "cik", "sourceKind", "sourceDocumentId", "sourceSha256",
            "publicationDate", "secAccessionNumber", "secFiledDate", "startDate",
            "endDate", "fiscalPeriod", "currency")
LOGICAL = ("symbol", "cik", "startDate", "endDate", "fiscalPeriod", "currency")
FIELDS = ("revenue", "operatingIncome", "netIncome", "epsDiluted")
KINDS = {"OFFICIAL_ISSUER_EARNINGS_RELEASE": "www.amgen.com",
         "SEC_ORIGINAL_SAME_FILING_XBRL": "www.sec.gov"}


def canonical_digest(identity):
    return hashlib.sha256(json.dumps({k: identity[k] for k in IDENTITY},
        sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def _number(value):
    if value is None:
        return None
    if isinstance(value, bool):
        raise ValueError("boolean is not a financial value")
    try:
        result = Decimal(str(value).replace(",", ""))
    except InvalidOperation as error:
        raise ValueError("invalid financial value") from error
    if not result.is_finite():
        raise ValueError("non-finite financial value")
    return result


def _validate(candidate):
    identity = candidate["canonicalIdentity"]
    if set(identity) != set(IDENTITY):
        raise ValueError("incomplete or unexpected canonical identity")
    if not re.fullmatch(r"[A-Z][A-Z0-9.-]{0,19}", identity["symbol"]):
        raise ValueError("invalid symbol")
    if not re.fullmatch(r"[0-9]{10}", identity["cik"]):
        raise ValueError("invalid CIK")
    url = urlparse(identity["sourceDocumentId"])
    if identity["sourceKind"] not in KINDS or url.scheme != "https" or (
            url.hostname != KINDS[identity["sourceKind"]]):
        raise ValueError("unknown or mismatched original source")
    public = date.fromisoformat(identity["publicationDate"])
    start, end = (date.fromisoformat(identity[key]) for key in ("startDate", "endDate"))
    if start > end or end >= public or identity["currency"] != "USD":
        raise ValueError("unproved period/currency")
    availability = candidate["availability"]
    if availability["publicDate"] != identity["publicationDate"] or (
            availability["precision"] != "DATE_ONLY") or any(
                availability.get(key) is not None for key in
                ("publicAt", "publicationTimeZone", "systemAcquiredAt")):
        raise ValueError("date-only source cannot acquire synthetic timestamps")
    if identity["sourceKind"] == "OFFICIAL_ISSUER_EARNINGS_RELEASE":
        if identity["secAccessionNumber"] is not None or identity["secFiledDate"] is not None:
            raise ValueError("issuer release cannot borrow SEC identity")
        proof = candidate.get("periodProof", {})
        if not proof.get("endDateLiteralPrinted") or not proof.get("startDateBasis"):
            raise ValueError("issuer period derivation not disclosed")
        year = proof.get("printedYear")
        if identity["fiscalPeriod"] != "Q4" or not isinstance(year, int) or (
                start != date(year, 10, 1) or end != date(year, 12, 31)):
            raise ValueError("issuer Q4 derivation differs from proved calendar quarter")
    else:
        if not re.fullmatch(r"\d{10}-\d{2}-\d{6}", identity["secAccessionNumber"] or ""):
            raise ValueError("SEC accession unproved")
        if identity["secFiledDate"] != identity["publicationDate"]:
            raise ValueError("SEC filed date mismatch")
        if candidate.get("periodProof", {}).get("basis") != "ORIGINAL_EX_101_INS_UNDIMENSIONED_CONTEXT":
            raise ValueError("SEC original context unproved")
    raw = Path(candidate["sourceLocalPath"]).read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != identity["sourceSha256"]:
        raise ValueError("source hash drift")
    if candidate.get("canonicalIdentitySha256", canonical_digest(identity)) != canonical_digest(identity):
        raise ValueError("canonical identity hash drift")
    if set(candidate["values"]) != set(FIELDS) or set(candidate["units"]) != set(FIELDS):
        raise ValueError("financial field contract mismatch")
    for field in FIELDS:
        value, unit = candidate["values"][field], candidate["units"][field]
        if value is None:
            if unit is not None:
                raise ValueError("unselected value must retain null unit")
            continue
        number = _number(value)
        expected_unit = "USD/shares" if field == "epsDiluted" else "USD"
        proof = candidate["fieldProofs"].get(field, {})
        if unit != expected_unit or proof.get("currency") != identity["currency"]:
            raise ValueError("unproved or conflicting unit")
        if any(proof.get(proof_key) != identity[identity_key] for proof_key, identity_key in (
                ("sourceURL", "sourceDocumentId"), ("sourceSha256", "sourceSha256"),
                ("sourcePublicationDate", "publicationDate"))):
            raise ValueError("field proof source mismatch")
        if proof.get("totalScopeConfirmed") is not True:
            raise ValueError("consolidated total scope unproved")
        if _number(proof.get("printedValue")) * _number(proof.get("unitsScale")) != number:
            raise ValueError("printed value/scale differs from selected value")
        if identity["sourceKind"] == "SEC_ORIGINAL_SAME_FILING_XBRL" and (
                proof.get("periodStart") != identity["startDate"] or
                proof.get("periodEnd") != identity["endDate"]):
            raise ValueError("cross-period SEC field merge")
    return identity


def _comparison(candidate, existing_rows):
    """No field is overwritten; even a null fill remains an admission proposal."""
    exact = [row for row in existing_rows if row["identity"] == candidate["canonicalIdentity"]]
    if len(exact) > 1:
        raise ValueError("duplicate existing canonical identity")
    result = {}
    for field in FIELDS:
        source = candidate["values"][field]
        if not exact:
            result[field] = "NO_EXACT_SOURCE_ROW" if source is not None else "SOURCE_NULL"
            continue
        current = exact[0]["values"].get(field)
        if source is None:
            result[field] = "BOTH_NULL" if current is None else "SOURCE_NULL_PRESERVE_EXISTING"
        elif current is None:
            result[field] = "NULL_FILL_REQUIRES_SEPARATE_ADMISSION"
        elif _number(source) != _number(current) or candidate["units"][field] != exact[0]["units"].get(field):
            result[field] = "NONNULL_CONFLICT_REJECTED"
        else:
            result[field] = "MATCH"
    return result


def build_plan(pack, *, decision_date=None, require_timestamp=False, existing_rows=()):
    ids, canonical, logical = set(), set(), set()
    rows = []
    decision = date.fromisoformat(decision_date) if decision_date else None
    for candidate in pack["candidates"]:
        candidate_id = candidate["candidateId"]
        if candidate_id in ids:
            raise ValueError("duplicate candidate id")
        ids.add(candidate_id)
        identity = _validate(candidate)
        key = canonical_digest(identity)
        if key in canonical:
            raise ValueError("duplicate canonical identity (alias)")
        canonical.add(key)
        logical_key = tuple(identity[field] for field in LOGICAL)
        if logical_key in logical:
            raise ValueError("ambiguous logical source requires explicit selection")
        logical.add(logical_key)
        eligibility = "DECISION_DATE_NOT_SUPPLIED"
        if require_timestamp:
            eligibility = "STRICT_TIMESTAMP_UNPROVED"
        elif decision:
            eligibility = ("RESEARCH_DATE_ELIGIBLE_ONLY" if decision > date.fromisoformat(
                identity["publicationDate"]) else "NOT_DATE_ELIGIBLE")
        rows.append({"candidateId": candidate_id, "identity": deepcopy(identity),
            "canonicalIdentitySha256": key, "values": deepcopy(candidate["values"]),
            "units": deepcopy(candidate["units"]), "availability": deepcopy(candidate["availability"]),
            "fieldComparison": _comparison(candidate, existing_rows), "eligibility": eligibility,
            "operationallyAdmitted": False, "historicalSystemCaptureProved": False})
    associations = deepcopy(pack.get("legacyTargetAssociations", []))
    targets = set()
    for association in associations:
        if association["candidateId"] not in ids or association.get("candidateOverridesLegacy") is not False or (
                association.get("legacyTargetUnchanged") is not True):
            raise ValueError("legacy override or unknown association")
        target = json.dumps(association["targetIdentity"], sort_keys=True, separators=(",", ":"))
        if target in targets:
            raise ValueError("ambiguous legacy target association")
        targets.add(target)
    accounting = deepcopy(pack.get("legacyRecoveryAccounting", {}))
    if accounting.get("exactRecoveredBefore") != accounting.get("exactRecoveredAfter"):
        raise ValueError("association must not increment exact recovery")
    return {"format": "QUANT_SOURCE_BRIDGE_PLAN_V1", "sourcePackVersion": pack["version"],
        "status": "VALIDATED_RESEARCH_PLAN_NOT_OPERATIONALLY_ADMITTED",
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "decisionDate": decision_date, "strictTimestampConsumer": require_timestamp,
        "candidateCount": len(rows), "associationCount": len(associations),
        "candidates": rows, "legacyTargetAssociations": associations,
        "legacyRecoveryAccounting": accounting, "databaseOperations": [],
        "operationallyAdmitted": False, "sourceOriginalsMutated": False,
        "modelOrOutcomeAccess": False,
        "missingOperationalPrerequisites": [
            "Source-aware schema/version: issuer release accession is legitimately null; stock_sec_financial_standard.accession_number is NOT NULL.",
            "GS original Apr1 and legacy Mar31 share the current natural key; append-only period/source identities must coexist before product admission.",
            "Exact publication time/timezone and historical system acquisition are unproved; no full PIT or intraday eligibility.",
            "Approved source selection, DB preimage/backup/drift/rollback/postimage and future forecast data-version binding remain required.",
            "An eligible research date is not an approved exchange trading session or verified performance sample."]}


def write_plan(pack, output, **options):
    result = build_plan(pack, **options)
    with Path(output).open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--decision-date")
    parser.add_argument("--require-timestamp", action="store_true")
    parser.add_argument("--existing-rows", type=Path,
                        help="Optional read-only exact source-aware rows, never a DB connection")
    args = parser.parse_args()
    raw = args.pack.read_bytes()
    pack = json.loads(raw)
    existing = json.loads(args.existing_rows.read_text(encoding="utf-8")) if args.existing_rows else []
    result = write_plan(pack, args.output, decision_date=args.decision_date,
                        require_timestamp=args.require_timestamp, existing_rows=existing)
    print(json.dumps({"output": str(args.output), "sourcePackSha256": hashlib.sha256(raw).hexdigest(),
                      "candidateCount": result["candidateCount"], "associationCount": result["associationCount"],
                      "status": result["status"], "databaseOperations": 0}))


if __name__ == "__main__":
    main()
