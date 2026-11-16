"""Read-only, reproducible daily membership/price research panel; no orders or DB writes."""
import argparse
import csv
import gzip
import hashlib
import json
import math
import shutil
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from barrier_research import ROOT, query


def members_on(intervals, day):
    result = {}
    for row in intervals:
        if row['is_member'].lower() != 'true':
            continue
        if row['start_date'] <= day <= row['end_date']:
            if row['symbol'] in result:
                raise ValueError(f"Overlapping membership: {day} {row['symbol']}")
            result[row['symbol']] = row
    return result


def price_flags(row):
    if row is None:
        return ['MISSING_BAR']
    flags = []
    try:
        o, h, l, c = [float(row[k]) for k in ('open_price', 'high_price', 'low_price', 'close_price')]
        if not all(math.isfinite(v) for v in (o, h, l, c)) or not 0 < l <= min(o, c) <= max(o, c) <= h:
            flags.append('INVALID_OHLC')
    except (ValueError, TypeError):
        flags.append('INVALID_OHLC')
    try:
        v = float(row['volume'])
        if not math.isfinite(v) or v < 0 or not v.is_integer():
            flags.append('INVALID_VOLUME')
        elif v == 0:
            flags.append('ZERO_VOLUME')
    except (ValueError, TypeError):
        flags.append('INVALID_VOLUME')
    return flags


def write_csv(path, rows, fields):
    opener = gzip.open if path.suffix == '.gz' else open
    with opener(path, 'wt', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def read_csv(path):
    opener = gzip.open if path.suffix == '.gz' else open
    with opener(path, 'rt', newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT / 'output/sp500-daily-dataset-20261018')
    parser.add_argument('--offline', action='store_true', help='Replay saved input snapshots without DB access')
    args = parser.parse_args()
    out = args.out
    inputs = out / 'inputs'
    inputs.mkdir(parents=True, exist_ok=True)
    source = ROOT / 'tmp/free-quant-corrected-2026-10-18'
    queries = {
        'prices': 'SELECT symbol,trade_date,open_price,high_price,low_price,close_price,volume,source,updated_at FROM stock_candle_daily ORDER BY symbol,trade_date',
        'calendar': "SELECT trade_date,benchmark_level,source FROM stock_benchmark_return_series WHERE index_code='SP500_FREE_20261018' AND source='FRED_SP500_PRICE_INDEX' AND benchmark_level IS NOT NULL ORDER BY trade_date",
    }
    if not args.offline:
        if (out / 'manifest.json').exists():
            raise RuntimeError('Use a new output folder for a new DB snapshot, or --offline to replay.')
        for name, sql in queries.items():
            rows = query(sql)
            if not rows:
                raise ValueError(f'Empty source: {name}')
            write_csv(inputs / f'{name}.csv.gz', rows, list(rows[0]))
        for filename in ('sp500_membership_intervals.csv', 'manifest.json'):
            shutil.copyfile(source / filename, inputs / filename)
        (inputs / 'extraction.json').write_text(json.dumps(dict(
            extracted_at=datetime.now(timezone.utc).isoformat(), queries=queries,
            consistency='Each query uses a separate read-only consistent snapshot; saved inputs define this build.'
        ), indent=2), encoding='utf-8')
    intervals = read_csv(inputs / 'sp500_membership_intervals.csv')
    membership_meta = json.loads((inputs / 'manifest.json').read_text(encoding='utf-8'))
    prices = read_csv(inputs / 'prices.csv.gz')
    calendar = read_csv(inputs / 'calendar.csv.gz')
    days = [r['trade_date'] for r in calendar]
    if days != sorted(set(days)):
        raise ValueError('Calendar dates are not unique and sorted')
    price_start = min(r['trade_date'] for r in prices)
    start = max(price_start, membership_meta['coverage']['firstDate'])
    end = min(max(r['trade_date'] for r in prices), membership_meta['coverage']['lastDate'], days[-1])
    panel_days = [d for d in days if start <= d <= end]
    day_index = {d: i for i, d in enumerate(days)}
    price_map = {}
    for row in prices:
        key = row['symbol'], row['trade_date']
        if key in price_map:
            raise ValueError(f'Duplicate price key: {key}')
        price_map[key] = row
    relevant_symbols = {r['symbol'] for r in intervals if r['start_date'] <= end and r['end_date'] >= start}
    audit, bars, usable = [], [], {}
    overrides = read_csv(inputs/'quality_overrides.csv') if (inputs/'quality_overrides.csv').exists() else []
    overrides_by_symbol = defaultdict(list)
    for override in overrides:
        overrides_by_symbol[override['symbol']].append(override)
    for row in prices:
        if row['symbol'] in relevant_symbols and row['trade_date'] not in day_index:
            audit.append(dict(symbol=row['symbol'], trade_date=row['trade_date'],
                              issue='OFF_CALENDAR_REVIEW', detail='Preserved in input extract; absent from calendar proxy, not joined.'))
    streaks, previous = defaultdict(int), {}
    # Follow the full calendar, including pre-membership history, without forward filling.
    for day in [d for d in days if price_start <= d <= days[-1]]:
        for symbol in sorted(relevant_symbols):
            row = price_map.get((symbol, day))
            flags = price_flags(row)
            if row:
                flags.extend(o['flag'] for o in overrides_by_symbol[symbol] if o['start_date']<=day<=o['end_date'])
            prev = previous.get(symbol)
            if row and not flags and prev:
                change = float(row['close_price']) / float(prev['close_price']) - 1
                gap = float(row['open_price']) / float(prev['close_price']) - 1
                if abs(change) > .30 or abs(gap) > .30:
                    flags.append('LARGE_MOVE_REVIEW')
            # Large moves are quarantined, not declared erroneous or winsorized.
            streaks[symbol] = 0 if flags else streaks[symbol] + 1
            usable[symbol, day] = (flags, streaks[symbol])
            previous[symbol] = row if row and 'INVALID_OHLC' not in flags else None
            if row:
                bars.append(dict(row, quality_flags='|'.join(flags), price_usable=int(not flags),
                                 consecutive_usable_sessions=streaks[symbol]))
                if flags:
                    audit.append(dict(symbol=symbol, trade_date=day, issue='|'.join(flags),
                                      detail='Raw values preserved; quarantined, no automatic repair.'))
    panel, gaps, coverage = [], defaultdict(list), []
    counts = Counter()
    for day in panel_days:
        roster = members_on(intervals, day)
        day_counts = Counter()
        for symbol, interval in sorted(roster.items()):
            row = price_map.get((symbol, day))
            flags, streak = usable[symbol, day]
            issue = '|'.join(flags)
            counts.update(flags)
            day_counts['members'] += 1
            day_counts['usable'] += int(not flags)
            day_counts['missing'] += int(row is None)
            if row is None:
                gaps[symbol].append(day)
            idx = day_index[day]
            record = dict(trade_date=day, symbol=symbol, membership_source=interval['source'],
                          membership_provenance=interval['provenance_ids'],
                          membership_start=interval['start_date'], membership_end=interval['end_date'],
                          official_pit_verified=0, next_session_date=days[idx+1] if idx+1 < len(days) else '',
                          quality_flags=issue, price_usable=int(not flags),
                          consecutive_usable_sessions=streak, history_200_ready=int(streak >= 200))
            for k in ('open_price', 'high_price', 'low_price', 'close_price', 'volume', 'source'):
                record[k] = row[k] if row else ''
            panel.append(record)
        coverage.append(dict(trade_date=day, **day_counts,
                             usable_pct=round(100*day_counts['usable']/day_counts['members'], 4)))
    missing = []
    for symbol, missing_days in sorted(gaps.items()):
        symbol_dates = sorted(d for s, d in price_map if s == symbol)
        missing.append(dict(symbol=symbol, missing_member_sessions=len(missing_days),
                            first_missing=missing_days[0], last_missing=missing_days[-1],
                            first_available=symbol_dates[0] if symbol_dates else '',
                            last_available=symbol_dates[-1] if symbol_dates else '',
                            action='Verify identifier/rename, then recover local raw or acquire historical/delisted bars.'))
    write_csv(out / 'daily_panel.csv.gz', panel, list(panel[0]))
    write_csv(out / 'price_history.csv.gz', bars, list(bars[0]))
    write_csv(out / 'daily_coverage.csv', coverage, list(coverage[0]))
    write_csv(out / 'missing_symbols.csv', missing, ['symbol', 'missing_member_sessions', 'first_missing', 'last_missing', 'first_available', 'last_available', 'action'])
    write_csv(out / 'price_anomalies.csv', audit, ['symbol', 'trade_date', 'issue', 'detail'])
    additions = [
        ('P0', 'MEMBERSHIP', '2026-08-19 onward and official full historical chain', 'Obtain effective-date evidence; do not carry the final roster forward.'),
        ('P0', 'MISSING_BARS', 'See missing_symbols.csv and MISSING_BAR panel rows', 'Check existing local raw Yahoo responses first; acquire unresolved delisted/renamed histories.'),
        ('P0', 'SECURITY_MASTER', 'Stable identifiers and ticker validity periods', 'Resolve renamed/reused symbols with evidence; no fuzzy automatic matching.'),
        ('P0', 'CORPORATE_ACTIONS', 'Splits, dividends, mergers, spin-offs and terminal payouts', 'Reconcile existing events with vendor-adjusted OHLC before portfolio-return certification.'),
        ('P0', 'CALENDAR', 'Official exchange sessions, timezone and close timestamps', 'FRED non-null index dates are only a calendar proxy in this dataset.'),
        ('P1', 'PRICE_REVIEW', 'See price_anomalies.csv', 'Reconcile zero volume, invalid prices and >30% close/open moves; preserve genuine market moves.'),
        ('P1', 'INTRADAY_EXECUTION', 'Minute bars, spreads, real fees and fills', 'Needed for same-day barrier ordering and realistic execution; daily bars only approximate this.'),
        ('P1', 'ASOF_SIGNALS', 'Immutable daily inputs, model version and generated_at', 'Do not join reconstructed/current signals as historical known-at-close inputs.'),
    ]
    write_csv(out / 'additional_data_required.csv', [dict(zip(['priority','category','scope','action'], r)) for r in additions], ['priority','category','scope','action'])
    summary = dict(start=start, end=end, sessions=len(panel_days), member_rows=len(panel),
                   symbols=len(relevant_symbols), usable_rows=sum(r['price_usable'] for r in panel),
                   history_200_ready_rows=sum(r['history_200_ready'] for r in panel), issues=dict(counts),
                   missing_symbols=len(missing), price_history_rows=len(bars),
                   official_pit_verified=False, production_ready=False,
                   calendar='FRED_SP500_PRICE_INDEX non-null dates; proxy, not certified exchange calendar',
                   price_basis='Existing vendor-adjusted OHLC; not certified executable or total-return prices',
                   classification='RESEARCH_ONLY_INCOMPLETE_UNIVERSE',
                   input_sha256={p.name:digest(p) for p in sorted(inputs.iterdir()) if p.is_file()})
    (out / 'manifest.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    readme = f'''# S&P500 daily research dataset

Period: {start} through {end}; {len(panel_days)} calendar-proxy sessions.
Grain: one historical member symbol per session. Rows: {len(panel):,}.
Status: RESEARCH ONLY; not certified official PIT membership or tradable total-return history.

## Files
- daily_panel.csv.gz: full member denominator, including missing bars; never silently drop missing members.
- price_history.csv.gz: observed bars for historical members, including pre-entry and post-removal dates, through available calendar end. Use for warmup and existing-position exits.
- daily_coverage.csv: denominator and usable-price coverage each day.
- missing_symbols.csv: per-symbol acquisition queue; exact missing dates remain in daily_panel.
- price_anomalies.csv: observed anomalous bars across full exported history (not just membership dates).
- additional_data_required.csv: outstanding evidence and acquisition list.
- inputs/: frozen DB extracts, membership intervals/provenance, extraction SQL and timestamps.
- manifest.json: counts, restrictions and SHA256 input hashes.

## Contract
Membership interval endpoints are inclusive; no extension past acquired evidence.
Exact symbols only. Overlapping memberships or duplicate price/calendar keys abort the build.
No price interpolation, forward fill, deletion, or invented replacements. Source values are preserved.
price_usable=1 means basic checks passed ONLY; it is not a certified investment eligibility flag.
INVALID_OHLC, invalid/zero volume and >30% close-to-previous-close or open-gap moves are quarantined.
Large moves may be genuine; review against corporate actions before releasing them.
Consecutive history resets at every missing or quarantined session; pre-membership bars count for warmup.
history_200_ready describes past data availability only, not a recommended strategy threshold.
next_session_date is a calendar mapping, not proof of next-day price availability or execution.
Do not filter decisions on future bar availability. Missing future bars/terminal events must stop or flag the portfolio evaluation, not delete losing trades.
Keep holdings after index removal unless the strategy explicitly liquidates them; use price_history, not member-only panel, for valuation.
FRED calendar may omit sessions: reconcile an official calendar before final validation.
Historical membership is reconstructed from community data with bounded corrections, NOT a complete official PIT chain.
Vendor-adjusted prices and current retrieval timestamps do not establish historical data availability.
No existing AI signals or future outcome labels are included. This is not an end-to-end backtest.

## Reproduce
python scripts/build_sp500_daily_dataset.py --offline --out {out.as_posix()}
Use a new --out folder without --offline for a new read-only DB extraction.
Tests: python -m unittest discover -s scripts -p test_build_sp500_daily_dataset.py
'''
    (out / 'README.md').write_text(readme, encoding='utf-8')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
