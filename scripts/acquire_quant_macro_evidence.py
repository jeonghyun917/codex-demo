"""Acquire bounded, no-key official ALFRED as-of snapshots; never write a DB.

Graph vintages establish day-level as-of snapshots, not release timestamps or
full realtime validity intervals. Outputs intentionally do not use the existing
macro importer's realtime_start/realtime_end/vintage_date header contract.
"""
from __future__ import annotations

import argparse
import calendar
import csv
import hashlib
import io
import json
import math
import subprocess
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlencode

import requests

SERIES = ("DGS3MO", "DGS10", "FEDFUNDS", "CPIAUCSL", "UNRATE", "VIXCLS", "DTWEXBGS")
MAX_DATE = date(2026, 10, 17)
FIELDS = ("series_code", "observation_date", "as_of_date", "value", "source_id")


def now():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def months_before(day, count):
    months = day.year * 12 + day.month - 1 - count
    year, month = divmod(months, 12)
    month += 1
    return date(year, month, min(day.day, calendar.monthrange(year, month)[1]))


def parse_snapshot(raw, series, as_of):
    reader = csv.DictReader(io.StringIO(raw.decode("utf-8-sig")))
    expected = ["observation_date", series + "_" + as_of.strftime("%Y%m%d")]
    if reader.fieldnames != expected:
        raise ValueError(f"Expected vintage-tagged header {expected}, got {reader.fieldnames}")
    rows, seen, missing = [], set(), 0
    for row in reader:
        day = date.fromisoformat(row["observation_date"])
        if day > as_of or day in seen:
            raise ValueError("Future or duplicate observation date")
        seen.add(day)
        value = row[expected[1]].strip()
        if value in ("", "."):
            missing += 1
            continue
        if not math.isfinite(float(value)):
            raise ValueError("Nonfinite value")
        rows.append(dict(zip(FIELDS, (series, str(day), str(as_of), value,
                                     f"alfred-{series}-{as_of}"))))
    if not rows:
        raise ValueError("No usable observations")
    return rows, missing


def fetch(root, job):
    source_id, url, relative, category, extra = job
    path, record_path = root / relative, root / (relative + ".metadata.json")
    if path.exists() and record_path.exists():
        record = json.loads(record_path.read_text(encoding="utf-8"))
        if record["sha256"] != sha(path.read_bytes()) or record["url"] != url:
            raise ValueError("Cached source failed integrity validation: " + str(path))
        return record
    if path.exists() or record_path.exists():
        raise ValueError("Incomplete existing source: " + str(path))
    try:
        response = requests.get(url, timeout=35)
        response.raise_for_status()
        raw = response.content
        if not raw or "access denied" in response.text[:500].lower():
            raise ValueError("Empty or access-denied response")
        if category == "ALFRED_AS_OF_DAY_SNAPSHOT":
            parse_snapshot(raw, extra["series"], date.fromisoformat(extra["asOfDate"]))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
        record = dict(id=source_id, url=url, finalUrl=response.url, path=relative,
                      retrievedAt=now(), bytes=len(raw), sha256=sha(raw),
                      httpStatus=response.status_code, contentType=response.headers.get("Content-Type"),
                      category=category, **extra)
        record_path.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        return record
    except (requests.RequestException, ValueError) as exc:
        return dict(id=source_id, url=url, error=f"{type(exc).__name__}: {exc}", failedAt=now())


def decision_dates(client):
    command = [str(client), "--host=127.0.0.1", "--user=root", "--database=king_yurina",
               "--batch", "--skip-column-names", "--execute="
               "SELECT DISTINCT signal_date FROM stock_expected_return_snapshot "
               "WHERE index_code IN ('SP500_FREE_20261018','NASDAQ100_FREE_20261018') "
               "AND signal_date<='2026-03-31' ORDER BY signal_date"]
    output = subprocess.run(command, check=True, capture_output=True, text=True).stdout
    dates = [date.fromisoformat(line) for line in output.splitlines() if line.strip()]
    if not dates or len(dates) > 50:
        raise ValueError("Unexpected historical decision-date inventory")
    return dates


def write_csv(path, fields, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    return dict(path=path.name, rows=len(rows), bytes=path.stat().st_size, sha256=sha(path.read_bytes()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--db-client", type=Path, required=True)
    args = parser.parse_args()
    root = args.output
    root.mkdir(parents=True, exist_ok=True)
    dates = decision_dates(args.db_client)
    as_of_dates = sorted({day - timedelta(days=1) for day in dates} | {MAX_DATE})
    jobs = []
    for series in SERIES:
        for as_of in as_of_dates:
            query = urlencode(dict(id=series, vintage_date=as_of, cosd=months_before(as_of, 18), coed=as_of))
            jobs.append((f"alfred-{series}-{as_of}",
                         "https://alfred.stlouisfed.org/graph/alfredgraph.csv?" + query,
                         f"raw/alfred/{series}/{as_of}.csv", "ALFRED_AS_OF_DAY_SNAPSHOT",
                         dict(series=series, asOfDate=str(as_of), publicationPrecision="DAY_AS_OF_NOT_RELEASE_TIME",
                              completeRealtimeIntervals=False, officialPitEligible=False)))
        jobs.append((f"fred-series-{series}", "https://fred.stlouisfed.org/series/" + series,
                     f"raw/documentation/{series}.html", "SERIES_METADATA_AND_RIGHTS", {}))
    for name in ("", "help"):
        jobs.append(("alfred-documentation-" + (name or "home"), "https://alfred.stlouisfed.org/" + name,
                     "raw/documentation/alfred-" + (name or "home") + ".html", "OFFICIAL_METHODOLOGY", {}))
    jobs.append(("fred-nasdaq100-hole-probe", "https://fred.stlouisfed.org/graph/fredgraph.csv?"
                 + urlencode(dict(id="NASDAQ100", cosd="2026-10-15", coed="2026-10-16")),
                 "raw/fred_NASDAQ100_20261015_20261016.csv", "BENCHMARK_HOLE_PROBE", {}))
    with ThreadPoolExecutor(max_workers=4) as pool:
        records = list(pool.map(lambda job: fetch(root, job), jobs))
    sources, failures = [r for r in records if "sha256" in r], [r for r in records if "error" in r]
    rows, profile = [], []
    for record in sources:
        if record["category"] != "ALFRED_AS_OF_DAY_SNAPSHOT":
            continue
        parsed, missing = parse_snapshot((root / record["path"]).read_bytes(), record["series"],
                                         date.fromisoformat(record["asOfDate"]))
        rows.extend(parsed)
        profile.append(dict(sourceId=record["id"], series=record["series"], asOfDate=record["asOfDate"],
                            usableRows=len(parsed), missingObservations=missing,
                            firstObservation=parsed[0]["observation_date"], lastObservation=parsed[-1]["observation_date"]))
    keys = [(r["series_code"], r["observation_date"], r["as_of_date"]) for r in rows]
    if len(keys) != len(set(keys)):
        raise ValueError("Duplicate as-of observation keys")
    outputs = [write_csv(root / "derived/macro_asof_snapshots_RESEARCH_ONLY.csv", FIELDS, rows)]
    mapping = [dict(decision_date=str(day), as_of_date=str(day - timedelta(days=1)),
                    policy="PREVIOUS_CALENDAR_DAY_NO_INTRADAY_ASSUMPTION") for day in dates]
    outputs.append(write_csv(root / "derived/decision_asof_map.csv", mapping[0].keys(), mapping))
    latest = {(r["series_code"], r["observation_date"]): r["value"] for r in rows if r["as_of_date"] == str(MAX_DATE)}
    differences, overlaps = [], 0
    for row in rows:
        current = latest.get((row["series_code"], row["observation_date"]))
        if row["as_of_date"] != str(MAX_DATE) and current is not None:
            overlaps += 1
            if float(current) != float(row["value"]):
                differences.append(dict(**row, latest_as_of_date=str(MAX_DATE), latest_value=current))
    outputs.append(write_csv(root / "derived/revision_comparison.csv",
                             FIELDS + ("latest_as_of_date", "latest_value"), differences))
    profile_data = dict(snapshotProfiles=profile, usableRows=len(rows), distinctAsOfDates=len(as_of_dates),
                        decisionDates=len(dates), latestComparisonOverlaps=overlaps,
                        changedHistoricalValues=len(differences), duplicateKeys=0, futureObservations=0,
                        missingPolicy="PRESERVE_RAW_MISSING_NO_ZERO_FILL_NO_FORWARD_FILL", outputs=outputs)
    (root / "profile.json").write_text(json.dumps(profile_data, indent=2) + "\n", encoding="utf-8")
    manifest = dict(generatedAt=now(), sources=sources, failures=failures, databaseWrites=0,
                    classification="OFFICIAL_ALFRED_DAY_ASOF_EVIDENCE_RESEARCH_ONLY",
                    officialPitEligible=False, promotionAllowed=False, completeRealtimeIntervals=False,
                    limitations=["No intraday source release or availability timestamps",
                                 "As-of snapshots do not identify the full realtime interval or first publication",
                                 "Do not import over frozen research or production namespaces",
                                 "VIX is copyrighted by CBOE; other rights remain series-specific",
                                 "Does not certify other features, membership, delisted prices or live forecasts"],
                    outputs=outputs, profileSha256=sha((root / "profile.json").read_bytes()))
    (root / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(files=len(sources), bytes=sum(r["bytes"] for r in sources),
                          failures=len(failures), rows=len(rows), dates=len(dates),
                          changedHistoricalValues=len(differences))))


if __name__ == "__main__":
    main()
