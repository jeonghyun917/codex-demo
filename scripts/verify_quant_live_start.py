"""Independent, read-only checks of the actual one-shot forward collection artifacts."""
import argparse
import hashlib
import json
import subprocess
from decimal import Decimal
from datetime import datetime
from pathlib import Path


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def verify(run, archive, protocols, db_client=None):
    result = load(run / "result.json")
    start = datetime.fromisoformat(result["startedAtUtc"].replace("Z", "+00:00"))
    finish = datetime.fromisoformat(result["finishedAtUtc"].replace("Z", "+00:00"))
    assert not result["failedSymbols"] and not result["failedModelStages"]
    assert result["modelWeightsChanged"] is False and result["promotionAllowed"] is False
    reports = {}
    database = {}
    if db_client:
        query = """SELECT index_code,symbol,expected_return_pct,expected_excess_return_pct,
                   upside_probability_pct,source FROM stock_expected_return_snapshot
                   WHERE signal_date='2026-10-31' AND horizon_days=20
                   AND model_version='EXPECTED_RETURN_V9_CORRECTED_20261017'
                   AND index_code IN ('DOW30','SP500','NASDAQ100')"""
        output = subprocess.run([str(db_client), "-h", "127.0.0.1", "-u", "root", "-N", "-B",
                                 "king_yurina", "-e", query], check=True, capture_output=True,
                                text=True, encoding="utf-8").stdout
        for line in output.splitlines():
            index, symbol, absolute, excess, probability, source = line.split("\t")
            key = index, symbol
            assert key not in database
            database[key] = absolute, excess, probability, source
    for index in ("DOW30", "SP500", "NASDAQ100"):
        manifest = load(protocols / index / "cohort-first-actual.json")
        assert len(manifest["cohorts"]) == 1
        cohort = manifest["cohorts"][0]
        path = Path(cohort["archiveInput"]["path"])
        assert path.is_relative_to(archive.resolve())
        raw = path.read_bytes()
        assert hashlib.sha256(raw).hexdigest() == cohort["archiveInput"]["sha256"]
        capture = json.loads(raw)
        observed = datetime.fromisoformat(capture["capturedAtUtc"].replace("Z", "+00:00"))
        assert start <= observed <= finish
        assert capture["indexCode"] == index
        predictions = capture["predictions"]
        symbols = {row["symbol"] for row in predictions}
        assert len(symbols) == len(predictions) == result["live20DCounts"][index]["savedRows"]
        assert all(row["expectedExcessReturnPct"] is not None for row in predictions)
        if db_client:
            for row in predictions:
                absolute, excess, probability, source = database.pop((index, row["symbol"]))
                assert Decimal(absolute) == Decimal(str(row["expectedReturnPct"]))
                assert Decimal(excess) == Decimal(str(row["expectedExcessReturnPct"]))
                assert Decimal(probability) == Decimal(str(row["upsideProbabilityPct"]))
                assert source == row["source"]
        assert cohort["predictions"] == predictions
        assert cohort["anchorSession"] == "2026-10-30"
        assert cohort["entrySession"] == "2026-11-02"
        assert cohort["exitSession"] == "2026-12-01"
        markets = {row["symbol"]: row for row in capture["marketInputs"]}
        assert symbols == markets.keys()
        assert all(row["snapshotDate"] == "2026-10-30" for row in markets.values())
        upstream = {row["symbol"]: row for row in capture["upstreamV8"] if row["horizonDays"] == 20}
        assert symbols <= upstream.keys()
        assert all(upstream[s]["source"].startswith("LIVE_FRED_MACRO_FEATURE_ADJUSTED_EXPECTED_RETURN_V8_CUTOFF_LT_") for s in symbols)
        eligibility = result["eligibility"][index]
        assert not symbols.intersection(eligibility["excludedKnownBeforeForecast"])
        assert len(symbols) == eligibility["eligibleCurrentClose"]
        reports[index] = dict(predictions=len(symbols), capturedAtUtc=capture["capturedAtUtc"],
                              archiveSha256=cohort["archiveInput"]["sha256"],
                              excludedKnownBeforeForecast=eligibility["excludedKnownBeforeForecast"],
                              entrySession=cohort["entrySession"], exitSession=cohort["exitSession"],
                              outcomeStatus="PENDING_FUTURE_CLOSES")
    assert not database
    return dict(status="VERIFIED_ACTUAL_NEW_OBSERVATIONS", indices=reports,
                databaseValuesMatched=bool(db_client), promotion=False)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--protocols", type=Path, required=True)
    parser.add_argument("--db-client", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.run, args.archive, args.protocols, args.db_client), indent=2))
