"""Offline prospective-output validation. No network, database or scheduling.

Only CLI init creates a start timestamp (now, never user-backdated). All output
files are create-only. API clock injection exists for synthetic unit tests.
"""
import argparse
from collections import Counter
import csv
from datetime import date, datetime, timezone
import hashlib
import json
import math
from pathlib import Path

PROTOCOL_VERSION = "QUANT_PROSPECTIVE_CLOSE_20D_V2_20261031"


def timestamp(value):
    parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("timestamp requires explicit timezone")
    return parsed.astimezone(timezone.utc)


def iso(value):
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def now_utc():
    return datetime.now(timezone.utc)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def seal(value):
    value["contentSha256"] = digest(canonical(value))
    return value


def verify_seal(value):
    body = {k: v for k, v in value.items() if k != "contentSha256"}
    if digest(canonical(body)) != value.get("contentSha256"):
        raise ValueError("manifest/protocol integrity hash mismatch")


def number(value):
    if isinstance(value, bool):
        raise ValueError("boolean is not a numeric return")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError("numeric values must be finite")
    return result


def validated_sessions(rows):
    previous = None
    for row in rows:
        session = date.fromisoformat(row["session"])
        opening, closing = timestamp(row["openUtc"]), timestamp(row["closeUtc"])
        if opening >= closing or (previous and (session <= previous[0] or opening <= previous[1])):
            raise ValueError("calendar must be unique, chronological, nonoverlapping sessions")
        previous = session, closing
    if not rows:
        raise ValueError("calendar is empty")
    return rows


def create_protocol(calendar, benchmark_symbol, baseline=None, now=None, index_code="SP500"):
    started = now or now_utc()
    calendar = Path(calendar)
    raw = calendar.read_bytes()
    rows = list(csv.DictReader(raw.decode("utf-8-sig").splitlines()))
    validated_sessions(rows)
    if not benchmark_symbol.strip():
        raise ValueError("benchmark symbol required")
    if not isinstance(index_code, str) or not index_code.strip():
        raise ValueError("explicit index code required for benchmark binding")
    if baseline is not None:
        for key in ("absolutePct", "excessPct"):
            number(baseline[key])
        if timestamp(baseline["trainingThroughUtc"]) >= started:
            raise ValueError("historical baseline training must end before protocol start")
    return seal({"protocolVersion": PROTOCOL_VERSION, "startedAtUtc": iso(started),
                 "classification": "PROSPECTIVE_PREPARATION_NOT_VALIDATED", "benchmarkSymbol": benchmark_symbol,
                 "indexCode": index_code,
                 "calendar": rows, "calendarInput": {"path": str(calendar.resolve()), "sha256": digest(raw)},
                 "fixedHistoricalBaseline": baseline,
                 "rules": {"anchor": "LATEST_EXCHANGE_CLOSE_AT_OR_BEFORE_CAPTURE",
                           "entry": "NEXT_CALENDAR_SESSION_CLOSE", "exit": "ENTRY_PLUS_20_SESSIONS_CLOSE",
                           "cohort": "EARLIEST_CAPTURE_PER_INDEX_AND_ANCHOR_ALL_NAMES",
                           "cutoff": "CAPTURE_AND_COHORT_FREEZE_STRICTLY_BEFORE_ENTRY_CLOSE",
                           "priceCoverage": "ALL_21_STOCK_AND_BENCHMARK_CLOSES_REQUIRED",
                           "target": "PRICE_RETURN_PERCENT_AND_ARITHMETIC_BENCHMARK_EXCESS",
                           "weighting": "EQUAL_OBSERVATION_MATURE_ONLY_WITH_PENDING_DENOMINATOR_REPORTED"}})


def freeze_cohort(protocol, archive, now=None):
    verify_seal(protocol)
    if not protocol.get("indexCode"):
        raise ValueError("protocol requires explicit index/benchmark binding; initialize a new protocol")
    frozen = now or now_utc()
    start = timestamp(protocol["startedAtUtc"])
    if frozen < start:
        raise ValueError("freeze cannot precede protocol start")
    sessions = validated_sessions(protocol["calendar"])
    inputs, excluded, candidates = [], [], []
    paths = sorted(Path(archive).rglob("*.json"))
    for path in paths:
        raw = path.read_bytes()
        record = json.loads(raw)
        item = {"path": str(path.resolve()), "sha256": digest(raw)}
        inputs.append(item)
        captured = timestamp(record["capturedAtUtc"])
        reason = ("INDEX_BENCHMARK_MISMATCH" if record.get("indexCode") != protocol["indexCode"] else
                  "BEFORE_PROTOCOL" if captured < start else "AFTER_FREEZE" if captured > frozen else None)
        if reason:
            excluded.append(dict(item, reason=reason))
            continue
        if record.get("schemaVersion") != "QUANT_FORWARD_OBSERVATION_V1":
            raise ValueError("unsupported archive schema")
        indices = [i for i, s in enumerate(sessions) if timestamp(s["closeUtc"]) <= captured]
        if not indices:
            raise ValueError("calendar must include a closed anchor session before capture")
        anchor = indices[-1]
        if anchor + 21 >= len(sessions):
            raise ValueError("calendar needs entry plus 20 future sessions; extend before init")
        entry, exit_row = sessions[anchor + 1], sessions[anchor + 21]
        if frozen >= timestamp(entry["closeUtc"]):
            excluded.append(dict(item, reason="ENTRY_CUTOFF_PASSED"))
            continue
        predictions = record.get("predictions", record.get("predictionsV9"))
        if not isinstance(predictions, list) or not predictions:
            raise ValueError("nonempty predictions required")
        seen = set()
        for prediction in predictions:
            symbol = prediction.get("symbol")
            if not isinstance(symbol, str) or not symbol.strip():
                raise ValueError("nonempty symbol required")
            if symbol in seen:
                raise ValueError("duplicate symbol in archived cohort")
            seen.add(symbol)
            if prediction.get("horizonDays") != 20 or not str(prediction.get("source", "")).startswith("LIVE_V9_"):
                raise ValueError("only saved LIVE_V9 20-session predictions qualify")
            if prediction.get("modelVersion") != record.get("modelVersion"):
                raise ValueError("archive model version mismatch")
            for field in ("expectedReturnPct", "expectedExcessReturnPct"):
                if prediction.get(field) is not None:
                    number(prediction[field])
        candidates.append({"archiveInput": item, "capturedAtUtc": iso(captured), "indexCode": record["indexCode"],
                           "originalSignalDate": record.get("signalDate"), "modelVersion": record.get("modelVersion"),
                           "anchorSession": sessions[anchor]["session"], "entrySession": entry["session"],
                           "exitSession": exit_row["session"], "predictions": predictions})
    chosen = {}
    for cohort in sorted(candidates, key=lambda c: (timestamp(c["capturedAtUtc"]), c["archiveInput"]["sha256"])):
        key = cohort["indexCode"], cohort["anchorSession"]
        if key not in chosen:
            chosen[key] = cohort
        else:
            first = chosen[key]
            if first["capturedAtUtc"] == cohort["capturedAtUtc"] and first["predictions"] != cohort["predictions"]:
                raise ValueError("ambiguous simultaneous captures with conflicting predictions")
            excluded.append(dict(cohort["archiveInput"], reason="LATER_OR_DUPLICATE_CAPTURE"))
    return seal({"manifestVersion": "QUANT_FORWARD_COHORT_V1", "frozenAtUtc": iso(frozen),
                 "classification": "FROZEN_OBSERVED_OUTPUT_NOT_FULL_INPUT_PIT_PROOF",
                 "protocol": protocol, "archiveInputs": inputs, "excludedArchives": excluded,
                 "cohorts": list(chosen.values()), "promotion": False})


def evaluate(manifest, prices, as_of=None):
    verify_seal(manifest)
    protocol = manifest["protocol"]
    verify_seal(protocol)
    if not protocol.get("indexCode") or any(c.get("indexCode") != protocol["indexCode"] for c in manifest["cohorts"]):
        raise ValueError("cohort index does not match explicit protocol index/benchmark binding")
    as_of = as_of or now_utc()
    if as_of < timestamp(manifest["frozenAtUtc"]):
        raise ValueError("evaluation cannot precede cohort freeze")
    prices = Path(prices)
    raw = prices.read_bytes()
    lookup = {}
    for row in csv.DictReader(raw.decode("utf-8-sig").splitlines()):
        day = date.fromisoformat(row["date"]).isoformat()
        key = row["symbol"], day
        if key in lookup:
            raise ValueError("duplicate price row")
        price = number(row["close"])
        if price <= 0:
            raise ValueError("close must be positive")
        lookup[key] = price
    sessions = validated_sessions(protocol["calendar"])
    position = {s["session"]: i for i, s in enumerate(sessions)}
    observations = []
    for cohort in manifest["cohorts"]:
        entry, exit_day = cohort["entrySession"], cohort["exitSession"]
        holding = sessions[position[entry]:position[exit_day] + 1]
        for prediction in cohort["predictions"]:
            symbol = prediction["symbol"]
            result = {"indexCode": cohort["indexCode"], "anchorSession": cohort["anchorSession"],
                      "symbol": symbol, "entrySession": entry, "exitSession": exit_day,
                      "predictedAbsolutePct": prediction.get("expectedReturnPct"),
                      "predictedExcessPct": prediction.get("expectedExcessReturnPct")}
            if timestamp(holding[-1]["closeUtc"]) > as_of:
                result["status"] = "PENDING_OUTCOME"
            else:
                missing = [{"symbol": s, "date": d["session"]} for s in (symbol, protocol["benchmarkSymbol"])
                           for d in holding if (s, d["session"]) not in lookup]
                if missing:
                    result.update(status="INSUFFICIENT_PRICES", missingPrices=missing)
                else:
                    absolute = (lookup[symbol, exit_day] / lookup[symbol, entry] - 1) * 100
                    benchmark = (lookup[protocol["benchmarkSymbol"], exit_day] /
                                 lookup[protocol["benchmarkSymbol"], entry] - 1) * 100
                    result.update(status="MATURE", realizedAbsolutePct=absolute,
                                  realizedBenchmarkPct=benchmark, realizedExcessPct=absolute - benchmark)
            observations.append(result)
    metrics = {}
    for target in ("absolute", "excess"):
        field = target.capitalize()
        pairs = [(number(r[f"predicted{field}Pct"]), r[f"realized{field}Pct"]) for r in observations
                 if r["status"] == "MATURE" and r[f"predicted{field}Pct"] is not None]
        average = lambda values: sum(values) / len(values) if values else None
        baseline = protocol["fixedHistoricalBaseline"]
        metrics[target] = {"n": len(pairs), "modelMaePct": average([abs(p - y) for p, y in pairs]),
                           "zeroMaePct": average([abs(y) for _, y in pairs]),
                           "historicalBaselineMaePct": average([abs(number(baseline[target + "Pct"]) - y)
                                                                  for _, y in pairs]) if baseline else None}
    return seal({"reportVersion": "QUANT_FORWARD_EVALUATION_V1", "asOfUtc": iso(as_of),
                 "manifestContentSha256": manifest["contentSha256"],
                 "pricesInput": {"path": str(prices.resolve()), "sha256": digest(raw)},
                 "classification": "DESCRIPTIVE_SAVED_OUTPUT_ERROR_NOT_PROMOTION_OR_COMPLETE_PIT_VALIDATION",
                 "promotion": False, "totalFrozenObservations": len(observations),
                 "counts": dict(Counter(r["status"] for r in observations)),
                 "metrics": metrics, "observations": observations})


def write_immutable(path, value):
    encoded = json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n"
    with Path(path).open("x", encoding="utf-8") as stream:
        stream.write(encoded)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    init = sub.add_parser("init")
    init.add_argument("--calendar", type=Path, required=True)
    init.add_argument("--benchmark-symbol", required=True)
    init.add_argument("--index-code", default="SP500", help="index bound to this benchmark (default: SP500)")
    init.add_argument("--historical-baseline", type=Path)
    freeze = sub.add_parser("freeze")
    freeze.add_argument("--protocol", type=Path, required=True)
    freeze.add_argument("--archive", type=Path, required=True)
    evaluate_parser = sub.add_parser("evaluate")
    evaluate_parser.add_argument("--manifest", type=Path, required=True)
    evaluate_parser.add_argument("--prices", type=Path, required=True)
    evaluate_parser.add_argument("--as-of", help="UTC/offset timestamp, never later than execution time")
    for command in (init, freeze, evaluate_parser):
        command.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    read = lambda p: json.loads(p.read_text(encoding="utf-8-sig"))
    try:
        if args.command == "init":
            result = create_protocol(args.calendar, args.benchmark_symbol,
                                     read(args.historical_baseline) if args.historical_baseline else None,
                                     index_code=args.index_code)
        elif args.command == "freeze":
            result = freeze_cohort(read(args.protocol), args.archive)
        else:
            current = now_utc()
            as_of = timestamp(args.as_of) if args.as_of else current
            if as_of > current:
                raise ValueError("as-of cannot be in the future")
            result = evaluate(read(args.manifest), args.prices, as_of=as_of)
        write_immutable(args.out, result)
    except (ValueError, KeyError, OSError, TypeError) as error:
        parser.exit(2, f"error: {error}\n")
    print(f"{args.command}: {args.out} sha256={result['contentSha256']} promotion=false")


if __name__ == "__main__":
    main()
