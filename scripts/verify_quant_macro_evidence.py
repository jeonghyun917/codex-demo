"""Offline, independent integrity/temporal checks for ALFRED evidence output."""
import argparse
import csv
import hashlib
import json
import math
from collections import Counter
from datetime import date, timedelta
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(root):
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    profile = json.loads((root / "profile.json").read_text(encoding="utf-8"))
    assert manifest["databaseWrites"] == 0
    assert manifest["officialPitEligible"] is False and manifest["promotionAllowed"] is False
    assert digest(root / "profile.json") == manifest["profileSha256"]
    source_ids, expected_rows, raw_counts = set(), {}, Counter()
    for item in manifest["sources"]:
        path = (root / item["path"]).resolve()
        assert path.is_relative_to(root.resolve())
        assert path.stat().st_size == item["bytes"] and digest(path) == item["sha256"]
        assert item["id"] not in source_ids
        source_ids.add(item["id"])
        if item["category"] != "ALFRED_AS_OF_DAY_SNAPSHOT":
            continue
        as_of, series = item["asOfDate"], item["series"]
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.reader(file)
            assert next(reader) == ["observation_date", series + "_" + as_of.replace("-", "")]
            seen = set()
            for day, value in reader:
                assert day not in seen and date.fromisoformat(day) <= date.fromisoformat(as_of)
                seen.add(day)
                if value in ("", "."):
                    raw_counts["missing"] += 1
                    continue
                assert math.isfinite(float(value))
                key = (series, day, as_of)
                assert key not in expected_rows
                expected_rows[key] = (value, item["id"])
                raw_counts[series] += 1
    path = root / "derived/macro_asof_snapshots_RESEARCH_ONLY.csv"
    with path.open(encoding="utf-8", newline="") as file:
        reader = csv.DictReader(file)
        assert "vintage_date" not in reader.fieldnames and "realtime_start" not in reader.fieldnames
        actual_rows = {}
        for row in reader:
            key = (row["series_code"], row["observation_date"], row["as_of_date"])
            assert key not in actual_rows
            actual_rows[key] = (row["value"], row["source_id"])
    assert actual_rows == expected_rows
    with (root / "derived/decision_asof_map.csv").open(encoding="utf-8", newline="") as file:
        mappings = list(csv.DictReader(file))
    for row in mappings:
        assert date.fromisoformat(row["as_of_date"]) == date.fromisoformat(row["decision_date"]) - timedelta(days=1)
    for item in manifest["outputs"]:
        path = root / "derived" / item["path"]
        assert digest(path) == item["sha256"] and path.stat().st_size == item["bytes"]
        with path.open(encoding="utf-8", newline="") as file:
            assert sum(1 for _ in csv.DictReader(file)) == item["rows"]
    assert len(actual_rows) == profile["usableRows"]
    return dict(status="VERIFIED", verifiedSources=len(source_ids), verifiedObservationRows=len(actual_rows),
                historicalDecisionDates=len(mappings), bySeries=dict(raw_counts),
                duplicateKeys=0, futureObservationDates=0, rawToDerivedMismatches=0,
                missingFilled=0, manifestSha256=digest(root / "manifest.json"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = verify(args.output)
    (args.output / "verification.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
