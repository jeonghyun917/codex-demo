#!/usr/bin/env python
"""
Build a point-in-time index membership CSV from public Wikipedia tables.

This is intentionally a data acquisition helper, not a model rule. It writes a
CSV accepted by StockPointInTimeImportBatchRunner and labels every row with a
Wikipedia-derived source so it remains distinguishable from official index data.
"""

from __future__ import annotations

import argparse
import csv
import io
import re
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
from typing import Iterable

import pandas as pd
import requests


HEADERS = {"User-Agent": "king-yurina-quant-ai/1.0 (local research; contact local)"}

SP500_URL = "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies"
NASDAQ100_URL = "https://en.wikipedia.org/wiki/Nasdaq-100"
DOW_URL = "https://en.wikipedia.org/wiki/Dow_Jones_Industrial_Average"


@dataclass(frozen=True)
class Event:
    event_date: date
    symbol: str
    action: str


@dataclass
class Interval:
    index_code: str
    symbol: str
    start_date: date
    end_date: date
    is_member: bool
    sector: str
    industry: str
    exchange: str
    source: str


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--from-date", default="2021-01-01")
    parser.add_argument("--to-date", default=date.today().isoformat())
    parser.add_argument("--output", default="data/pit/wikipedia_index_membership.csv")
    args = parser.parse_args()

    from_date = date.fromisoformat(args.from_date)
    to_date = date.fromisoformat(args.to_date)
    if to_date < from_date:
        raise SystemExit("--to-date must be on or after --from-date")

    intervals: list[Interval] = []
    intervals.extend(build_sp500(from_date, to_date))
    intervals.extend(build_nasdaq100(from_date, to_date))
    intervals.extend(build_dow30(from_date, to_date))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow([
            "index_code",
            "symbol",
            "start_date",
            "end_date",
            "is_member",
            "sector",
            "industry",
            "exchange",
            "source",
        ])
        for row in sorted(intervals, key=lambda r: (r.index_code, r.symbol, r.start_date, r.end_date)):
            writer.writerow([
                row.index_code,
                row.symbol,
                row.start_date.isoformat(),
                row.end_date.isoformat(),
                "true" if row.is_member else "false",
                row.sector,
                row.industry,
                row.exchange,
                row.source,
            ])

    print(f"wrote {len(intervals)} intervals to {output}")


def build_sp500(from_date: date, to_date: date) -> list[Interval]:
    tables = read_tables(SP500_URL)
    current = tables[0].copy()
    changes = tables[1].copy()
    current_info = current_info_from_table(
        "SP500",
        current,
        symbol_col="Symbol",
        sector_col="GICS Sector",
        industry_col="GICS Sub-Industry",
        exchange_col=None,
        date_added_col="Date added",
    )
    events = events_from_change_table(changes)
    events.extend(current_added_events(current_info))
    return build_intervals("SP500", current_info, events, from_date, to_date,
            "WIKIPEDIA_SP500_CHANGE_TABLE_PIT")


def build_nasdaq100(from_date: date, to_date: date) -> list[Interval]:
    tables = read_tables(NASDAQ100_URL)
    current = find_table(tables, required_columns=("Ticker", "Company"))
    changes = find_change_table(tables)
    current_info = current_info_from_table(
        "NASDAQ100",
        current,
        symbol_col="Ticker",
        sector_col="ICB Industry[15]",
        industry_col="ICB Subsector[15]",
        exchange_col=None,
        date_added_col=None,
    )
    events = events_from_change_table(changes)
    return build_intervals("NASDAQ100", current_info, events, from_date, to_date,
            "WIKIPEDIA_NASDAQ100_CHANGE_TABLE_PIT")


def build_dow30(from_date: date, to_date: date) -> list[Interval]:
    tables = read_tables(DOW_URL)
    current = find_table(tables, required_columns=("Company", "Symbol", "Date added"))
    current_info = current_info_from_table(
        "DOW30",
        current,
        symbol_col="Symbol",
        sector_col="Sector",
        industry_col=None,
        exchange_col="Exchange",
        date_added_col="Date added",
    )
    # The DJIA page exposes current Date added values; post-2021 removals are
    # added here from the same public DJIA history context so current proxy rows
    # do not leak Nvidia/Amazon/Sherwin-Williams into pre-addition periods.
    events = current_added_events(current_info)
    events.extend([
        Event(date(2024, 2, 26), "WBA", "remove"),
        Event(date(2024, 2, 26), "AMZN", "add"),
        Event(date(2024, 11, 8), "INTC", "remove"),
        Event(date(2024, 11, 8), "DOW", "remove"),
        Event(date(2024, 11, 8), "NVDA", "add"),
        Event(date(2024, 11, 8), "SHW", "add"),
    ])
    return build_intervals("DOW30", current_info, events, from_date, to_date,
            "WIKIPEDIA_DJIA_COMPONENT_HISTORY_PIT")


def read_tables(url: str) -> list[pd.DataFrame]:
    response = requests.get(url, headers=HEADERS, timeout=45)
    response.raise_for_status()
    return [flatten_columns(table) for table in pd.read_html(io.StringIO(response.text))]


def flatten_columns(table: pd.DataFrame) -> pd.DataFrame:
    table = table.copy()
    flattened: list[str] = []
    for column in table.columns:
        if isinstance(column, tuple):
            parts = [
                str(part).strip()
                for part in column
                if str(part).strip()
                and not str(part).startswith("Unnamed")
                and str(part).lower() != "nan"
            ]
            flattened.append(" ".join(dict.fromkeys(parts)))
        else:
            flattened.append(str(column).strip())
    table.columns = flattened
    return table


def find_table(tables: Iterable[pd.DataFrame], required_columns: tuple[str, ...]) -> pd.DataFrame:
    required = {normalize_header(col) for col in required_columns}
    for table in tables:
        columns = {normalize_header(col) for col in table.columns}
        if required.issubset(columns):
            return table
    raise RuntimeError(f"table not found for columns {required_columns}")


def find_change_table(tables: Iterable[pd.DataFrame]) -> pd.DataFrame:
    for table in tables:
        columns = {normalize_header(col) for col in table.columns}
        if any("addedticker" in col for col in columns) and any("removedticker" in col for col in columns):
            return table
    raise RuntimeError("change table not found")


def current_info_from_table(
    index_code: str,
    table: pd.DataFrame,
    symbol_col: str,
    sector_col: str | None,
    industry_col: str | None,
    exchange_col: str | None,
    date_added_col: str | None,
) -> dict[str, dict[str, object]]:
    info: dict[str, dict[str, object]] = {}
    symbol_key = lookup_column(table, symbol_col)
    sector_key = lookup_column(table, sector_col) if sector_col else None
    industry_key = lookup_column(table, industry_col) if industry_col else None
    exchange_key = lookup_column(table, exchange_col) if exchange_col else None
    date_added_key = lookup_column(table, date_added_col) if date_added_col else None
    for _, row in table.iterrows():
        symbol = normalize_symbol(row.get(symbol_key))
        if not symbol:
            continue
        info[symbol] = {
            "index_code": index_code,
            "sector": clean_text(row.get(sector_key)) if sector_key else "",
            "industry": clean_text(row.get(industry_key)) if industry_key else "",
            "exchange": clean_text(row.get(exchange_key)) if exchange_key else "",
            "date_added": parse_date(row.get(date_added_key)) if date_added_key else None,
        }
    return info


def current_added_events(current_info: dict[str, dict[str, object]]) -> list[Event]:
    events: list[Event] = []
    for symbol, info in current_info.items():
        added = info.get("date_added")
        if isinstance(added, date):
            events.append(Event(added, symbol, "add"))
    return events


def events_from_change_table(table: pd.DataFrame) -> list[Event]:
    date_key = first_matching_column(table, ("effectivedate", "date"))
    added_key = first_matching_column(table, ("addedticker",))
    removed_key = first_matching_column(table, ("removedticker",))
    events: list[Event] = []
    for _, row in table.iterrows():
        event_date = parse_date(row.get(date_key))
        if not event_date:
            continue
        added = normalize_symbol(row.get(added_key))
        removed = normalize_symbol(row.get(removed_key))
        if added:
            events.append(Event(event_date, added, "add"))
        if removed:
            events.append(Event(event_date, removed, "remove"))
    return events


def build_intervals(
    index_code: str,
    current_info: dict[str, dict[str, object]],
    events: list[Event],
    from_date: date,
    to_date: date,
    source: str,
) -> list[Interval]:
    current_symbols = set(current_info)
    events = sorted({event for event in events if event.symbol}, key=lambda e: (e.event_date, e.symbol, e.action))
    universe = current_symbols | {event.symbol for event in events}
    active: set[str] = set()
    for symbol in universe:
        info = current_info.get(symbol, {})
        added = info.get("date_added")
        if isinstance(added, date) and added <= from_date:
            active.add(symbol)
        elif added is None and symbol in current_symbols:
            active.add(symbol)
        if any(event.symbol == symbol and event.action == "remove" and event.event_date >= from_date
               for event in events):
            active.add(symbol)
    for event in events:
        if event.event_date < from_date:
            if event.action == "add":
                active.add(event.symbol)
            elif event.action == "remove":
                active.discard(event.symbol)

    boundaries = sorted({event.event_date for event in events if from_date <= event.event_date <= to_date})
    intervals: list[Interval] = []
    start = from_date
    for boundary in boundaries:
        append_state_intervals(index_code, current_info, current_symbols, active, start,
                boundary - timedelta(days=1), source, intervals)
        for event in [e for e in events if e.event_date == boundary]:
            if event.action == "add":
                active.add(event.symbol)
            elif event.action == "remove":
                active.discard(event.symbol)
        start = boundary
    append_state_intervals(index_code, current_info, current_symbols, active, start, to_date, source, intervals)
    return merge_intervals(intervals)


def append_state_intervals(
    index_code: str,
    current_info: dict[str, dict[str, object]],
    current_symbols: set[str],
    active: set[str],
    start: date,
    end: date,
    source: str,
    out: list[Interval],
) -> None:
    if end < start:
        return
    # Non-current active names are required to add former constituents back into
    # the PIT universe. Current inactive names are required to override current
    # membership proxy rows before their actual addition date.
    required_symbols = (active - current_symbols) | (current_symbols - active)
    for symbol in sorted(required_symbols):
        is_member = symbol in active
        info = current_info.get(symbol, {})
        out.append(Interval(
            index_code=index_code,
            symbol=symbol,
            start_date=start,
            end_date=end,
            is_member=is_member,
            sector=str(info.get("sector") or ""),
            industry=str(info.get("industry") or ""),
            exchange=str(info.get("exchange") or ""),
            source=source,
        ))


def merge_intervals(rows: list[Interval]) -> list[Interval]:
    merged: list[Interval] = []
    for row in sorted(rows, key=lambda r: (r.index_code, r.symbol, r.is_member, r.source, r.start_date)):
        if merged:
            prev = merged[-1]
            if (
                prev.index_code == row.index_code
                and prev.symbol == row.symbol
                and prev.is_member == row.is_member
                and prev.source == row.source
                and prev.sector == row.sector
                and prev.industry == row.industry
                and prev.exchange == row.exchange
                and prev.end_date + timedelta(days=1) == row.start_date
            ):
                prev.end_date = row.end_date
                continue
        merged.append(row)
    return merged


def lookup_column(table: pd.DataFrame, wanted: str | None) -> str:
    if not wanted:
        return ""
    normalized = normalize_header(wanted)
    for column in table.columns:
        if normalize_header(column) == normalized:
            return column
    raise RuntimeError(f"column not found: {wanted}")


def first_matching_column(table: pd.DataFrame, needles: tuple[str, ...]) -> str:
    normalized_needles = tuple(normalize_header(needle) for needle in needles)
    for column in table.columns:
        normalized = normalize_header(column)
        if any(needle in normalized for needle in normalized_needles):
            return column
    raise RuntimeError(f"column not found for {needles}")


def normalize_header(value: object) -> str:
    return re.sub(r"[^a-z0-9]", "", str(value).lower())


def normalize_symbol(value: object) -> str:
    if value is None or pd.isna(value):
        return ""
    symbol = re.sub(r"\[[^\]]*]", "", str(value)).strip().upper()
    symbol = symbol.replace(".", "-").replace(" ", "")
    symbol = re.sub(r"[^A-Z0-9-]", "", symbol)
    return symbol if symbol not in {"NAN", "NONE"} else ""


def clean_text(value: object) -> str:
    if value is None or pd.isna(value):
        return ""
    text = re.sub(r"\[[^\]]*]", "", str(value)).strip()
    return "" if text.lower() == "nan" else text


def parse_date(value: object) -> date | None:
    if value is None or pd.isna(value):
        return None
    text = re.sub(r"\[[^\]]*]", "", str(value)).strip()
    if not text or text.lower() in {"nan", "none"}:
        return None
    parsed = pd.to_datetime(text, errors="coerce")
    if pd.isna(parsed):
        return None
    return parsed.date()


if __name__ == "__main__":
    main()
