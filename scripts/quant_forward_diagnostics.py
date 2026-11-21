"""Versioned eligibility diagnosis only; no forecast, price, score or denominator changes."""
from collections import Counter
import csv
from datetime import date
import json
from pathlib import Path
import re

import quant_forward_validation as forward


def events(path, as_of):
    if path is None or not Path(path).exists():
        return []
    registry = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    forward.verify_seal(registry)
    if registry.get("format") != "QUANT_TRADING_EVENTS_V1":
        raise ValueError("Unsupported trading-event registry")
    seen, usable = set(), []
    for event in registry["events"]:
        if (event.get("eventId") in seen or not event.get("eventId")
                or not re.fullmatch(r"[A-Z0-9.^_=-]{1,40}", event.get("symbol", ""))
                or event.get("effectiveBoundary") != "MARKET_OPEN"
                or not event.get("sourceUrl", "").startswith("https://")):
            raise ValueError("Invalid or duplicate trading-event identity")
        seen.add(event["eventId"])
        effective = date.fromisoformat(event["effectiveSession"])
        published = date.fromisoformat(event["sourcePublicationDate"])
        observed = forward.timestamp(event["observedAtUtc"])
        if published > observed.date() or forward.digest(Path(event["proofPath"]).read_bytes()) != event["proofSha256"]:
            raise ValueError("Trading-event evidence changed or has impossible availability")
        if observed <= as_of:
            usable.append(event)
    return usable


def diagnose(manifests, prices, registry=None, as_of=None):
    as_of = as_of or forward.now_utc()
    known = events(registry, as_of)
    keys = set()
    for row in csv.DictReader(Path(prices).read_text(encoding="utf-8-sig").splitlines()):
        key = (row["symbol"], date.fromisoformat(row["date"]).isoformat())
        if key in keys or forward.number(row["close"]) <= 0:
            raise ValueError("Invalid or duplicate diagnostic price")
        keys.add(key)
    cohorts = []
    for path, manifest in manifests:
        close_by_day = {row["session"]: forward.timestamp(row["closeUtc"])
                        for row in manifest["protocol"]["calendar"]}
        for cohort in manifest["cohorts"]:
            observations = []
            for prediction in cohort["predictions"]:
                symbol = prediction["symbol"]
                terminal = [event for event in known if event["symbol"] == symbol
                            and event["effectiveSession"] <= cohort["entrySession"]]
                if terminal:
                    status = "NON_TRADABLE_AT_ENTRY"
                elif as_of < close_by_day[cohort["entrySession"]]:
                    status = "ENTRY_NOT_DUE"
                elif (symbol, cohort["entrySession"]) in keys:
                    status = "ENTRY_CLOSE_PRESENT"
                else:
                    status = "ENTRY_CLOSE_MISSING"
                observations.append(dict(symbol=symbol, status=status,
                    eventIds=[event["eventId"] for event in terminal],
                    evidenceObservedAtUtc=[event["observedAtUtc"] for event in terminal]))
            cohorts.append(dict(manifest=str(path), indexCode=cohort["indexCode"], anchorSession=cohort["anchorSession"],
                entrySession=cohort["entrySession"], exitSession=cohort["exitSession"],
                expectedNames=len(cohort["predictions"]), counts=dict(Counter(o["status"] for o in observations)),
                observations=observations, baselineMetricScope="COMPLETE_CASE_MATURE_NAMES_WITH_MISSING_DENOMINATOR_RETAINED"))
    return forward.seal(dict(format="QUANT_FORWARD_ELIGIBILITY_DIAGNOSTICS_V1", asOfUtc=forward.iso(as_of),
        cohorts=cohorts, forecastsChanged=False, pricesChanged=False, scoresProduced=False, promotion=False,
        candidateMetricScope="ALL_FROZEN_NAMES_AND_ALL21_STOCK_BENCHMARK_CLOSES_REQUIRED",
        knowledgePolicy="EVENTS_ONLY_KNOWN_AT_DIAGNOSTIC_TIME_NOT_BACKDATED_TO_FORECAST_FREEZE"))
