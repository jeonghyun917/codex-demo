"""Bounded free Nasdaq evidence acquisition; offline QA; never writes a DB.

Raw evidence is append-only: --acquire refuses to replace its manifest.
--verify checks saved hashes and independently replays curated event boundaries.
No full dated roster is inferred from press releases or counts.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
from datetime import datetime, timedelta, date, timezone
import hashlib
from html import unescape
import json
from pathlib import Path
import re
import urllib.error
import urllib.request

REPO = Path(__file__).resolve().parents[1]
ROOT = REPO / "tmp/quant-complete-window-membership-20261018"
INTERVALS = REPO / "tmp/free-quant-data-2026-10-18/derived/nasdaq100_jmccarrell_intervals_RESEARCH_ONLY.csv"
SOURCES = [
    ("wmt-20260120", "https://ir.nasdaq.com/node/109851", "PARTIAL_OFFICIAL_CHANGE_EVENT", "html"),
    ("ndx-2025-reconstitution", "https://indexes.nasdaq.com/docs/202601%20NDX%20Reconstitution%20and%20Performance%20Highlights.pdf", "PARTIAL_OFFICIAL_RECONSTITUTION_RESEARCH", "pdf"),
    ("ndx-weighting-access", "https://indexes.nasdaq.com/Index/Weighting/NDX", "ACCESS_DOCUMENTATION_NOT_HISTORICAL_ROSTER", "html"),
]
EVENTS = [
    dict(sourceId="annual2025-reused-web-observation", publishedDate="2025-12-12", effectiveDate="2025-12-22",
         add=["ALNY", "FER", "INSM", "MPWR", "STX", "WDC"], remove=["BIIB", "CDW", "GFS", "LULU", "ON", "TTD"],
         url="https://ir.nasdaq.com/node/109786", evidence="PREEXISTING_MANIFEST_AND_NEW_WEB_READER_CORROBORATION"),
    dict(sourceId="wmt-20260120", publishedDate="2026-01-09", effectiveDate="2026-01-20", add=["WMT"], remove=["AZN"],
         url="https://ir.nasdaq.com/node/109851", evidence="WEB_READER_CORROBORATION_RAW_STATUS_RECORDED_SEPARATELY"),
]


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def fetch(spec):
    source_id, url, coverage, extension = spec
    record = dict(id=source_id, url=url, coverage=coverage,
                  requestedAt=datetime.now(timezone.utc).isoformat(), authenticated=False)
    raw = None
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "QuantLocalEvidence/1.0 (public research; no authentication)"})
        with urllib.request.urlopen(req, timeout=20) as response:
            raw = response.read(4_000_001)
            if len(raw) > 4_000_000:
                raise ValueError("Response exceeds bounded 4 MB limit")
            record.update(httpStatus=response.status, finalUrl=response.url,
                          contentType=response.headers.get("Content-Type"), status="SAVED_RAW_RESPONSE")
    except urllib.error.HTTPError as exc:
        raw = exc.read(4_000_000)
        record.update(httpStatus=exc.code, status="HTTP_ERROR_NOT_SOURCE_EVIDENCE", error=str(exc))
        extension = "error.html"
    except Exception as exc:
        record.update(status="NETWORK_FAILURE_NO_RAW", error=f"{type(exc).__name__}: {exc}")
    record["retrievedAt"] = datetime.now(timezone.utc).isoformat()
    if raw is not None:
        relative = f"raw/{source_id}.{extension}"
        (ROOT / relative).write_bytes(raw)
        record.update(path=relative, bytes=len(raw), sha256=digest(raw))
    return record


def memberships(rows, day):
    return {r["symbol"] for r in rows if r["start_date"] <= day <= r["end_date"] and r["is_member"] == "true"}


def compare_intervals():
    raw = INTERVALS.read_bytes()
    with INTERVALS.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    checks = []
    for event in EVENTS:
        day = event["effectiveDate"]
        previous = (date.fromisoformat(day) - timedelta(days=1)).isoformat()
        before, after = memberships(rows, previous), memberships(rows, day)
        added, removed = sorted(after - before), sorted(before - after)
        checks.append(dict(sourceId=event["sourceId"], effectiveDate=day,
                           expectedAdd=event["add"], expectedRemove=event["remove"], actualAdd=added, actualRemove=removed,
                           matches=added == sorted(event["add"]) and removed == sorted(event["remove"])))
    days = ["2025-12-31", "2026-01-30", "2026-02-27", "2026-03-27"]
    sets = {day: sorted(memberships(rows, day)) for day in days}
    return dict(inputPath=str(INTERVALS.relative_to(REPO)), inputSha256=digest(raw), intervalRows=len(rows),
                eventChecks=checks, reconstructedSnapshots={d: dict(count=len(s), symbols=s) for d, s in sets.items()},
                targetRosterEquality=sets[days[1]] == sets[days[2]] == sets[days[3]],
                coverage="RECONSTRUCTED_RESEARCH_ONLY_NOT_OFFICIAL_PIT", completeOfficialHistoricalDates=0)


def verify():
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    supplement_path = ROOT / "manifest-public-supplement.json"
    sources = list(manifest["sources"])
    if supplement_path.exists():
        sources.extend(json.loads(supplement_path.read_text(encoding="utf-8"))["sources"])
    sources.extend(json.loads((ROOT / "manifest-public-mirror.json").read_text(encoding="utf-8"))["sources"])
    checked = 0
    for source in sources:
        if "path" in source:
            raw = (ROOT / source["path"]).read_bytes()
            assert len(raw) == source["bytes"] and digest(raw) == source["sha256"], source["id"]
            checked += 1
    saved = json.loads((ROOT / "interval-comparison.json").read_text(encoding="utf-8"))
    assert saved == compare_intervals(), "Input or derived comparison changed"
    assert all(c["matches"] for c in saved["eventChecks"]), "Official event boundary discrepancy"
    assert manifest["completeOfficialHistoricalDates"] == saved["completeOfficialHistoricalDates"] == 0
    observations = ROOT / "web-observations.json"
    assert observations.exists(), "Missing manually reviewed source observations"
    observation_data = json.loads(observations.read_text(encoding="utf-8"))
    assert observation_data["completeOfficialHistoricalDates"] == 0
    mirror = (ROOT / "raw/wmt-nasdaq-press-release.html").read_text(encoding="utf-8")
    text = re.sub(r"\s+", " ", unescape(re.sub("<[^>]*>", " ", mirror)))
    for expected in ["Jan 9, 2026", "January 20, 2026", "Walmart Inc.", "AstraZeneca PLC", "prior to market open"]:
        assert expected in text, expected
    assert (ROOT / "raw/ndx-2025-reconstitution-public.pdf").read_bytes().startswith(b"%PDF-")
    export_path = REPO / "tmp/quant-complete-window-members-20261018.tsv"
    with export_path.open(encoding="utf-8-sig", newline="") as handle:
        export = list(csv.DictReader(handle, delimiter="\t"))
    comparisons = []
    for day in ["2026-01-30", "2026-02-27", "2026-03-27"]:
        found = [r["symbol"] for r in export if r["index_code"] == "NASDAQ100_MACRO_FREE_20261018" and r["snapshot_date"] == day]
        expected = saved["reconstructedSnapshots"][day]["symbols"]
        assert len(found) == len(set(found)), f"Duplicate export symbol: {day}"
        assert sorted(found) == expected, f"Export roster differs: {day}"
        comparisons.append(dict(snapshotDate=day, count=len(found), matchesReconstruction=True))
    result = dict(rawHashesVerified=checked,
                          successfulSourceResponses=sum(s["status"] in {"SAVED_RAW_RESPONSE", "SUCCESSFUL_PUBLIC_DOWNLOAD_HTTP_STATUS_NOT_CAPTURED"} for s in sources),
                          savedErrorResponses=sum(s["status"] == "HTTP_ERROR_NOT_SOURCE_EVIDENCE" for s in sources),
                          eventChecksPassed=len(saved["eventChecks"]),
                          targetCounts={d: x["count"] for d, x in saved["reconstructedSnapshots"].items()},
                          targetRosterEquality=saved["targetRosterEquality"], completeOfficialHistoricalDates=0,
                          exportSha256=digest(export_path.read_bytes()), exportComparisons=comparisons,
                          observationsSha256=digest(observations.read_bytes()))
    print(json.dumps(result, indent=2))
    return result


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--acquire", action="store_true")
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--acquire-public-supplement", action="store_true")
    args = parser.parse_args()
    if args.acquire_public_supplement:
        target = ROOT / "manifest-public-supplement.json"
        if target.exists():
            raise SystemExit("Public supplement exists; use --verify")
        # Public only, no credentials. Distinct paths preserve failed initial attempts.
        specs = [(i + "-public", u, c, e) for i, u, c, e in SOURCES]
        with ThreadPoolExecutor(max_workers=3) as pool:
            sources = list(pool.map(fetch, specs))
        target.write_text(json.dumps(dict(sources=sources, officialPIT=False, completeOfficialHistoricalDates=0), indent=2) + "\n", encoding="utf-8")
        print(json.dumps(sources, indent=2))
    if args.acquire:
        if (ROOT / "manifest.json").exists():
            raise SystemExit("Manifest exists; use --verify (no automatic redownload)")
        (ROOT / "raw").mkdir(parents=True, exist_ok=True)
        with ThreadPoolExecutor(max_workers=4) as pool:
            sources = list(pool.map(fetch, SOURCES))
        manifest = dict(createdAt=datetime.now(timezone.utc).isoformat(), sources=sources,
                        existingAnnual2025="tmp/free-quant-corrected-2026-10-18/manifest.json officialEventQA; no raw response was previously saved",
                        events=EVENTS, completeOfficialHistoricalDates=0, officialPIT=False,
                        coverage="PARTIAL_OFFICIAL_ASSERTIONS_AND_ACCESS_STATUS_NOT_COMPLETE_CHANGE_CHAIN",
                        prohibitedActionsPerformed=[], databaseWrites=0)
        (ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        (ROOT / "interval-comparison.json").write_text(json.dumps(compare_intervals(), indent=2) + "\n", encoding="utf-8")
        print(json.dumps([{k: s.get(k) for k in ["id", "status", "httpStatus", "bytes", "sha256"]} for s in sources], indent=2))
    if args.verify:
        verify()
    if not args.acquire and not args.verify and not args.acquire_public_supplement:
        parser.error("Choose --acquire, --acquire-public-supplement or --verify")


if __name__ == "__main__":
    main()
