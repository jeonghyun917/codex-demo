"""Offline full-population PRICE-ONLY coverage/benchmark validation, never fake Quant forecasts.

Reads archived provider evidence and DB exports. No DB writes. Every constituent must
have every supplied benchmark session, not just endpoints. Community membership is not PIT.
"""
import argparse
import bisect
import csv
import hashlib
import json
import math
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

REPO = Path(__file__).resolve().parents[1]
OUT = REPO / 'tmp/quant-complete-windows-recovered-20261018'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_chart(body, symbol):
    root = json.loads(body)['chart']
    if root.get('error') or not isinstance(root.get('result'), list) or len(root['result']) != 1:
        raise ValueError('Invalid chart result')
    r = root['result'][0]
    meta = r['meta']
    if meta['symbol'] != symbol.replace('.', '-') or meta['currency'] != 'USD' or meta['instrumentType'] != 'EQUITY':
        raise ValueError('Price identity/currency/instrument mismatch')
    zone = ZoneInfo(meta['exchangeTimezoneName'])
    dates, quotes = r['timestamp'], r['indicators']['quote'][0]
    fields = ('open', 'high', 'low', 'close', 'volume')
    if not dates or any(len(quotes[f]) != len(dates) for f in fields):
        raise ValueError('Unequal price array sizes')
    result, seen = {}, set()
    for i, stamp in enumerate(dates):
        day = datetime.fromtimestamp(stamp, zone).date().isoformat()
        if day in seen:
            raise ValueError('Duplicate exchange date')
        seen.add(day)
        values = [quotes[f][i] for f in fields]
        if any(v is None for v in values[:4]):
            continue  # Unknown remains absent, never forward-filled.
        o, h, l, c, volume = values
        if not all(math.isfinite(v) and v > 0 for v in values[:4]) or h < max(o, l, c) or l > min(o, h, c):
            raise ValueError('Invalid OHLC')
        if volume is not None and (not isinstance(volume, int) or volume < 0):
            raise ValueError('Invalid volume')
        result[day] = c
    return result


def price_window(members, sessions, prices, forecast_symbols):
    if not members or len(members) != len(set(members)) or len(sessions) < 2 or sessions != sorted(set(sessions)):
        raise ValueError('Empty/duplicate membership or invalid calendar')
    missing = {}
    for symbol in members:
        available = prices.get(symbol, {})
        absent = [day for day in sessions if day not in available]
        if any(not math.isfinite(available[d]) or available[d] <= 0 for d in sessions if d in available):
            raise ValueError('Nonpositive or nonfinite price')
        if absent:
            missing[symbol] = absent
    missing_forecasts = sorted(set(members) - forecast_symbols)
    returns = {} if missing else {s: (prices[s][sessions[-1]]/prices[s][sessions[0]]-1)*100 for s in members}
    return dict(targetMembers=len(members), priceComplete=not missing, missingPrices=missing,
                quantForecastComplete=not missing and not missing_forecasts, missingForecasts=missing_forecasts,
                memberReturnsPct=returns, grossReturnPct=sum(returns.values())/len(members) if returns else None)


def compound_periods(periods, per_side_bps):
    if not math.isfinite(per_side_bps) or not 0 <= per_side_bps < 5000:
        raise ValueError('Invalid per-side cost')
    gross = net = benchmark = 1.0
    capital_free = None
    decisions = []
    for p in sorted(periods, key=lambda p: p['decision']):
        if p['exit'] <= p['decision']:
            raise ValueError('Nonpositive holding period')
        if capital_free is not None and p['decision'] < capital_free:
            continue
        g, b = p['grossReturnPct'], p['benchmarkReturnPct']
        if any(not math.isfinite(v) or v < -100 for v in (g, b)):
            raise ValueError('Invalid return')
        gross *= 1+g/100
        net *= 1+max(-100, g - 2*per_side_bps/100)/100
        benchmark *= 1+b/100
        decisions.append(p['decision'])
        capital_free = p['exit']
        if net == 0:
            break
    return dict(periodCount=len(decisions), grossReturnPct=(gross-1)*100 if decisions else None,
                netReturnPct=(net-1)*100 if decisions else None,
                benchmarkReturnPct=(benchmark-1)*100 if decisions else None, decisions=decisions)


def read_rows(path, delimiter='\t'):
    with path.open(encoding='utf-8-sig') as stream:
        return list(csv.DictReader(stream, delimiter=delimiter))


def benchmark_return(levels, sessions):
    values = [levels.get(d) for d in sessions]
    if any(v is None or not math.isfinite(v) or v <= 0 for v in values):
        return None
    return (values[-1]/values[0]-1)*100


def merge_prices(canonical, incoming):
    overlap = set(canonical) & set(incoming)
    if canonical and incoming and not overlap:
        raise ValueError('No overlapping price-basis evidence')
    if any(abs(canonical[d]/incoming[d]-1) > .00001 for d in overlap):
        raise ValueError('Incompatible historical close basis')
    return {**incoming, **canonical}  # Existing observed values always win.


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    if (OUT / 'report.json').exists():
        raise ValueError('Existing completed output: refusing overwrite')
    exports = {key: REPO / f'tmp/quant-complete-window-{key}-20261018.tsv'
               for key in ('members', 'benchmarks', 'forecasts', 'canonical')}
    members, benchmarks, forecasts = defaultdict(list), defaultdict(dict), defaultdict(set)
    for r in read_rows(exports['members']):
        members[r['index_code'], r['snapshot_date']].append(r['symbol'])
    for r in read_rows(exports['benchmarks']):
        series = benchmarks[r['index_code']]
        if r['trade_date'] in series:
            raise ValueError('Duplicate benchmark date')
        series[r['trade_date']] = None if r['benchmark_level'] == 'NULL' else float(r['benchmark_level'])
    for r in read_rows(exports['forecasts']):
        forecasts[r['index_code'], r['signal_date']].add(r['symbol'])
    evidence_root = REPO / 'tmp/pit-acquisition/actions'
    bundle_path = evidence_root / 'bundle.json'
    bundle = json.loads(bundle_path.read_text(encoding='utf-8'))
    prices, sources, rejected = {}, {}, {}
    requested = {s for group in members.values() for s in group}
    for source in bundle['sources']:
        symbol = source['id'].removeprefix('yahoo-chart-')
        # Dot/dash punctuation only; never map one company/ticker onto another.
        symbol = next((s for s in requested if s.replace('.', '-') == symbol), symbol)
        if symbol not in requested:
            continue
        path = (evidence_root / source['path']).resolve()
        if not path.is_relative_to(evidence_root.resolve()) or digest(path) != source['sha256']:
            raise ValueError('Archived source path/hash mismatch')
        try:
            prices[symbol] = parse_chart(path.read_bytes(), symbol)
            sources[symbol] = dict(source, resolvedPath=str(path))
        except (KeyError, ValueError, TypeError) as exc:
            rejected[symbol] = str(exc)
    canonical, canonical_sources = defaultdict(dict), defaultdict(set)
    for r in read_rows(exports['canonical']):
        symbol, day = r['symbol'], r['trade_date']
        o,h,l,c = [float(r[f]) for f in ('open_price','high_price','low_price','close_price')]
        if not all(math.isfinite(v) and v > 0 for v in (o,h,l,c)) or h < max(o,l,c) or l > min(o,h,c):
            raise ValueError('Invalid canonical OHLC')
        if day in canonical[symbol]:
            raise ValueError('Duplicate canonical date')
        canonical[symbol][day] = c
        canonical_sources[symbol].add(r['source'])
    recovered = []
    for symbol in requested:
        current, incoming = canonical.get(symbol, {}), prices.get(symbol, {})
        try:
            prices[symbol] = merge_prices(current, incoming)
            recovered.extend(dict(symbol=symbol,tradeDate=d,close=incoming[d],sourceId=sources[symbol]['id'])
                             for d in sorted(set(incoming)-set(current)) if '2023-04-28' <= d <= '2026-04-28')
        except ValueError as exc:
            rejected[symbol] = str(exc)
            prices[symbol] = dict(current)  # Rejected provider revision cannot replace old history.
    periods, holdings = [], []
    for (index, decision), population in sorted(members.items()):
        calendar = sorted(benchmarks[index])
        entry = bisect.bisect_right(calendar, decision)
        if entry + 20 >= len(calendar):
            continue
        sessions = calendar[entry:entry+21]
        row = price_window(population, sessions, prices, forecasts[index, decision])
        benchmark_pct = benchmark_return(benchmarks[index], sessions)
        # A null observation remains a calendar slot; do not shorten a holding window.
        calendar_control = [d for d in sorted(prices.get('AAPL', {})) if sessions[0] <= d <= sessions[-1]]
        benchmark_complete = benchmark_pct is not None and calendar_control == sessions
        row['priceComplete'] = row['priceComplete'] and benchmark_complete
        row['quantForecastComplete'] = row['quantForecastComplete'] and benchmark_complete
        row.update(index=index, decision=decision, entry=sessions[0], exit=sessions[-1],
                   sessionCount=len(sessions), benchmarkReturnPct=benchmark_pct,
                   benchmarkComplete=benchmark_complete, independentProviderCalendarMatches=calendar_control == sessions)
        if row['priceComplete']:
            for symbol, actual in row['memberReturnsPct'].items():
                holdings.append(dict(index=index,decision=decision,entry=row['entry'],exit=row['exit'],symbol=symbol,
                    entryClose=prices[symbol][row['entry']],exitClose=prices[symbol][row['exit']],
                    actualReturnPct=actual,benchmarkReturnPct=row['benchmarkReturnPct'],
                    sourceId=('canonical-export:'+','.join(sorted(canonical_sources[symbol])))
                    if row['entry'] in canonical.get(symbol,{}) and row['exit'] in canonical.get(symbol,{})
                    else sources[symbol]['id']))
        periods.append(row)
    summaries = {}
    for index in sorted(benchmarks):
        group = [p for p in periods if p['index'] == index]
        complete = [p for p in group if p['priceComplete']]
        summaries[index] = dict(decisionDates=len(group), priceCompleteDates=len(complete),
            quantForecastCompleteDates=sum(p['quantForecastComplete'] for p in group),
            priceOnlyEqualWeightBaseline=compound_periods(complete,10),
            completeDates=[p['decision'] for p in complete])
    result = dict(asOf='2026-10-18', classification='COMMUNITY_UNIVERSE_FULL_POPULATION_PRICE_ONLY_RESEARCH',
        promotionAllowed=False, databaseWrites=0, forecastRowsSynthesized=0,
        sourceBundleSha256=digest(bundle_path), inputSha256={k:digest(v) for k,v in exports.items()},
        sources=sources,rejectedSources=rejected,summaries=summaries,periods=periods,
        stagedAdditionalCloseRows=len(recovered),stagedAdditionalSymbols=sorted({r['symbol'] for r in recovered}),
        canonicalSourceInventory={s:sorted(v) for s,v in canonical_sources.items()},
        limitations=['Price-only provider-reported close, not total return or unadjusted executable price proof.',
            'All 21 supplied FRED sessions required. FRED session coverage is not an independently certified exchange calendar.',
            'Community membership remains reconstructed, not official historical PIT.',
            'Equal-weight all-members baseline validates data/accounting only, never the missing Quant model forecasts.',
            'Complete dates selected only by data availability, but missingness still creates selection bias.',
            'No current company/ticker substitution, dividend/cash/recovery imputation, or fake historical forecast.'])
    with (OUT/'complete-holdings.csv').open('x',encoding='utf-8',newline='') as stream:
        fields=['index','decision','entry','exit','symbol','entryClose','exitClose','actualReturnPct','benchmarkReturnPct','sourceId']
        writer=csv.DictWriter(stream,fieldnames=fields);writer.writeheader();writer.writerows(holdings)
    with (OUT/'additional-closes.csv').open('x',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=['symbol','tradeDate','close','sourceId'])
        writer.writeheader();writer.writerows(recovered)
    (OUT/'report.json').write_text(json.dumps(result,indent=2,allow_nan=False),encoding='utf-8')
    print(json.dumps(dict(sourceFiles=len(sources), rejectedSources=rejected, summaries=summaries),indent=2))


if __name__ == '__main__':
    main()
