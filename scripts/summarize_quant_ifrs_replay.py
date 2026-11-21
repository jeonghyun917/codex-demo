"""Recompute same-population errors; never grants model promotion from inspected dates."""
import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path


def calculate(rows):
    if not rows:
        return {"n": 0, "absolute": None, "excess": None}
    seen = set()
    for row in rows:
        key = row["decision"], row["symbol"]
        if key in seen:
            raise ValueError("Duplicate cohort member")
        seen.add(key)
        for field in ("actual", "benchmark", "oldAbsolute", "newAbsolute", "oldExcess", "newExcess"):
            value = row[field]
            if value is None or isinstance(value, bool) or not math.isfinite(float(value)):
                raise ValueError("Nonfinite/missing result; do not drop cohort names")
    result = {"n": len(rows)}
    for target in ("Absolute", "Excess"):
        actual = [float(r["actual"]) - (float(r["benchmark"]) if target == "Excess" else 0) for r in rows]
        old = [float(r["old"+target]) for r in rows]
        new = [float(r["new"+target]) for r in rows]
        average = lambda values: math.fsum(values) / len(rows)
        old_mae = average([abs(p-y) for p,y in zip(old, actual)])
        new_mae = average([abs(p-y) for p,y in zip(new, actual)])
        result[target.lower()] = dict(oldMaePp=old_mae, newMaePp=new_mae,
            maeDeltaPp=new_mae-old_mae, zeroMaePp=average([abs(y) for y in actual]),
            oldDirectionPct=average([100*int((p>0)==(y>0)) for p,y in zip(old,actual)]),
            newDirectionPct=average([100*int((p>0)==(y>0)) for p,y in zip(new,actual)]))
    return result


def nonoverlap(rows):
    intervals = {}
    for row in rows:
        interval = row["entry"], row["exit"]
        if row["decision"] in intervals and intervals[row["decision"]] != interval:
            raise ValueError("Inconsistent cohort interval")
        intervals[row["decision"]] = interval
    selected, last_exit = [], ""
    for decision, (entry, exit_day) in sorted(intervals.items()):
        if entry > last_exit:
            selected.append(decision)
            last_exit = exit_day
    return selected


def summarize(report):
    rolling = "trainingAudit" in report
    control_field = "firstDateBaselineMaxAbsDifference" if rolling else "controlMaxAbsDifference"
    if report["databaseWrites"] != 0 or report["promotionAllowed"] is not False or report[control_field] != 0:
        raise ValueError("Replay safety/control invariants failed")
    if rolling and (not report["trainingAudit"] or any(r["maxTrainingExit"] >= r["decision"] for r in report["trainingAudit"])):
        raise ValueError("Rolling training contains same-day or future outcome")
    rows = [dict(p, decision=d["decision"]) for d in report["dates"] for p in d["pairs"]]
    chosen = nonoverlap(rows)
    by_date = defaultdict(list)
    for row in rows:
        by_date[row["decision"]].append(row)
    result = dict(classification=("PAIRED_ROLLING_REPAIRED_PANELS_BASE_HISTORY_FROZEN_NOT_NEW_HOLDOUT" if rolling
                                 else "PAIRED_FINANCIAL_INPUT_SENSITIVITY_ALREADY_INSPECTED_DATES_NOT_NEW_HOLDOUT"),
        promotionAllowed=False, weightsChanged=False, dates=len(by_date), full=calculate(rows),
        foreign=calculate([r for r in rows if r["affected"]]), nonoverlapDates=chosen,
        nonoverlap=calculate([r for r in rows if r["decision"] in chosen]),
        perDate={day: calculate(part) for day,part in sorted(by_date.items())},
        limitations=["Frozen historic training/context; measures input repair, not fully rebuilt training or official PIT validation.",
                     "These dates have already been inspected; neither lower MAE nor nonoverlap is a new holdout/promotion proof.",
                     "Foreign quoted EPS remains blocked; no guessed FX/ADR normalization."])
    if rolling:
        result["limitations"][0] = "Only prior repaired panels/outcomes and prior forecasts are rolled forward; pre-panel history remains frozen, not complete PIT training."
        result["trainingAudit"] = report["trainingAudit"]
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--replay", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    raw = args.replay.read_bytes()
    result = summarize(json.loads(raw))
    result["input"] = dict(path=str(args.replay.resolve()), sha256=hashlib.sha256(raw).hexdigest())
    with args.out.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
    print(json.dumps({k: result[k] for k in ("dates", "full", "foreign", "promotionAllowed")}, indent=2))
