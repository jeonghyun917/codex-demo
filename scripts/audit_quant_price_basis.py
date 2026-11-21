"""Read-only canonical/provider price-basis audit. No scaling or DB writes.

Fresh Yahoo chart close is a provider-reported historical price, not proof of an
official raw/unadjusted or total-return basis. All comparisons are diagnostics.
"""
from __future__ import annotations
import json
import subprocess
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal, getcontext
from pathlib import Path
from zoneinfo import ZoneInfo

getcontext().prec = 28
ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'tmp/pit-acquisition/actions/raw'
OUT = ROOT / 'tmp/pit-acquisition/price-basis-audit.json'
CLI = ROOT / '.tools/mariadb-11.8.2-winx64/bin/mariadb.exe'
D = Decimal
TOL = D('0.00001')
CACHE = {}
FAILURES = []


def sql(query):
    assert query.lstrip().upper().startswith('SELECT ')
    run = subprocess.run([str(CLI), '--host=127.0.0.1', '--user=root',
                          '--database=king_yurina', '--batch', '--skip-column-names',
                          '--execute=' + query], capture_output=True, text=True,
                         encoding='utf-8', check=True)
    return [line.split('\t') for line in run.stdout.splitlines()]


def fresh(symbol):
    key = symbol.replace('.', '-')
    if key not in CACHE:
        path = RAW / (key + '.json')
        if not path.exists():
            CACHE[key] = (None, {'error': 'NO_RAW_FILE'})
        else:
            try:
                result = json.loads(path.read_text(encoding='utf-8'))['chart']['result'][0]
                zone = ZoneInfo(result['meta'].get('exchangeTimezoneName', 'America/New_York'))
                quotes = result['indicators']['quote'][0]['close']
                prices = {}
                missing = 0
                for stamp, close in zip(result.get('timestamp', []), quotes):
                    if close is None or D(str(close)) <= 0:
                        missing += 1
                        continue
                    day = datetime.fromtimestamp(stamp, zone).date().isoformat()
                    prices[day] = D(str(close))
                CACHE[key] = (prices, {'providerSymbol': result['meta'].get('symbol'),
                                      'providerIssuer': result['meta'].get('longName'),
                                      'validCloses': len(prices), 'nullOrNonpositiveCloses': missing,
                                      'splitEvents': result.get('events', {}).get('splits', {})})
            except Exception as exc:
                CACHE[key] = (None, {'error': f'{type(exc).__name__}: {exc}'})
                FAILURES.append({'symbol': symbol, 'error': str(exc)})
    return CACHE[key]


def number(value):
    return float(value) if value is not None else None


def main():
    report = {'generatedAt': datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
              'mode': 'READ_ONLY_DIAGNOSTIC_NO_PRICES_REPLACED',
              'providerCloseBasis': 'PROVIDER_REPORTED_HISTORICAL_CLOSE_NOT_OFFICIAL_RAW_BASIS',
              'relativePriceTolerance': number(TOL), 'outcomeDifferenceThresholdPercentagePoints': 0.01,
              'warnings': ['Observed price factors are not inferred or applied corporate-action adjustments.',
                           'No point-in-time membership or model-performance certification follows.',
                           'Ticker identity reuse and stale/partial provider history can affect comparisons.']}
    rows = sql('SELECT snapshot_id,symbol,signal_date,horizon_days,entry_trade_date,entry_close,'
               'exit_trade_date,exit_close,forward_return_pct FROM stock_backtest_result_v2')
    matched, absent, material, max_diff = 0, Counter(), 0, D(0)
    details, internal_mismatches = [], 0
    for sid, symbol, signal, horizon, ed, ec, xd, xc, stored in rows:
        prices, metadata = fresh(symbol)
        if prices is None:
            absent['missingOrInvalidRawFile'] += 1
            continue
        if ed not in prices or xd not in prices:
            absent['missingEntryAndExit' if ed not in prices and xd not in prices else
                   'missingEntry' if ed not in prices else 'missingExit'] += 1
            continue
        matched += 1
        new_return = (prices[xd] / prices[ed] - 1) * 100
        old_return = D(stored)
        delta = abs(new_return - old_return)
        internal = (D(xc) / D(ec) - 1) * 100
        if abs(internal - old_return) > D('0.000001'):
            internal_mismatches += 1
        max_diff = max(max_diff, delta)
        if delta > D('0.01'):
            material += 1
            details.append({'snapshotId': sid, 'symbol': symbol, 'signalDate': signal,
                            'horizonDays': int(horizon), 'entryDate': ed, 'exitDate': xd,
                            'storedEntryClose': ec, 'storedExitClose': xc,
                            'freshEntryClose': str(prices[ed]), 'freshExitClose': str(prices[xd]),
                            'storedReturnPct': stored, 'freshCloseReturnPct': number(new_return),
                            'differencePercentagePoints': number(new_return-old_return)})
    details.sort(key=lambda d: abs(d['differencePercentagePoints']), reverse=True)
    report['correctedOutcomeComparison'] = {
        'table': 'stock_backtest_result_v2', 'totalRows': len(rows), 'matchedRows': matched,
        'missingRows': sum(absent.values()), 'missingReasons': dict(absent),
        'differenceAboveThresholdRows': material, 'maximumAbsoluteDifferencePercentagePoints': number(max_diff),
        'storedReturnVersusStoredPricesMismatchRows': internal_mismatches,
        'materialRowsBySymbol': dict(Counter(d['symbol'] for d in details)),
        'largestDifferences': details[:50]}
    print(json.dumps(report['correctedOutcomeComparison']), flush=True)
    # Preserve the high-value outcome comparison before the larger canonical audit.
    OUT.write_text(json.dumps(report, indent=2), encoding='utf-8')
    candles = sql("SELECT c.symbol,c.trade_date,c.close_price,c.source FROM stock_candle_daily c "
                  "JOIN (SELECT DISTINCT symbol FROM index_constituent WHERE current_member=1) u "
                  "ON c.symbol=u.symbol WHERE c.trade_date>='2020-01-01' AND c.trade_date<='2026-10-17' "
                  "ORDER BY c.symbol,c.trade_date")
    by_symbol, aph = {}, []
    for symbol, day, close, source in candles:
        s = by_symbol.setdefault(symbol, {'symbol': symbol, 'canonicalRows': 0, 'matchedRows': 0,
                                         'sameWithinToleranceRows': 0, 'missingFreshRows': 0,
                                         'minRatio': None, 'maxRatio': None, 'largestRelativeDifference': D(0),
                                         'canonicalSources': Counter()})
        s['canonicalRows'] += 1
        s['canonicalSources'][source] += 1
        prices, metadata = fresh(symbol)
        if prices is None or day not in prices:
            s['missingFreshRows'] += 1
            continue
        old, new = D(close), prices[day]
        if old <= 0:
            FAILURES.append({'symbol': symbol, 'date': day, 'error': 'NONPOSITIVE_CANONICAL_CLOSE'})
            continue
        ratio = old/new
        difference = abs(ratio-1)
        s['matchedRows'] += 1
        s['sameWithinToleranceRows'] += int(difference <= TOL)
        s['minRatio'] = min(s['minRatio'], ratio) if s['minRatio'] is not None else ratio
        s['maxRatio'] = max(s['maxRatio'], ratio) if s['maxRatio'] is not None else ratio
        if difference > s['largestRelativeDifference']:
            s['largestRelativeDifference'] = difference
            s['largestDiscrepancyDate'] = day
            s['canonicalCloseAtLargestDifference'] = close
            s['freshCloseAtLargestDifference'] = str(new)
        if symbol == 'APH' and day.startswith('2026-'):
            aph.append({'date': day, 'canonicalClose': close, 'freshClose': str(new),
                        'observedCanonicalToFreshRatio': number(ratio), 'canonicalSource': source})
    for s in by_symbol.values():
        if not s['matchedRows']:
            label = 'NO_OVERLAP'
        elif s['sameWithinToleranceRows'] == s['matchedRows']:
            label = 'SAME_WITHIN_TOLERANCE'
        elif s['maxRatio']/s['minRatio']-1 <= D('0.001'):
            label = 'APPROXIMATELY_CONSTANT_FACTOR_DISCREPANCY'
        else:
            label = 'DATE_VARYING_DISCREPANCY'
        s['classification'] = label
        for field in ['minRatio', 'maxRatio', 'largestRelativeDifference']:
            s[field] = number(s[field])
        s['canonicalSources'] = dict(s['canonicalSources'])
    members = [r[0] for r in sql('SELECT DISTINCT symbol FROM index_constituent WHERE current_member=1')]
    summary = {'universe': 'CURRENT_INDEX_CONSTITUENTS_NOT_HISTORICAL_PIT',
               'currentMemberSymbols': len(members), 'symbolsWithCanonicalCandles': len(by_symbol),
               'currentMembersWithoutCanonicalCandles': sorted(set(members)-set(by_symbol)),
               'canonicalRows': len(candles),
               'matchedRows': sum(s['matchedRows'] for s in by_symbol.values()),
               'missingFreshRows': sum(s['missingFreshRows'] for s in by_symbol.values()),
               'sameWithinToleranceRows': sum(s['sameWithinToleranceRows'] for s in by_symbol.values()),
               'symbolClassifications': dict(Counter(s['classification'] for s in by_symbol.values())),
               'allSymbols': sorted(by_symbol.values(), key=lambda s: s['largestRelativeDifference'], reverse=True)}
    report['currentUniverseCanonicalOverlap'] = summary
    report['APH2026'] = {'providerMetadata': fresh('APH')[1], 'dailyComparisons': aph}
    report['rawFilesAvailable'] = len(list(RAW.glob('*.json')))
    report['rawFilesRead'] = len(CACHE)
    report['failures'] = FAILURES
    OUT.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in summary.items() if k != 'allSymbols'}))
    print('PRICE_BASIS_AUDIT_WRITTEN', OUT)


if __name__ == '__main__':
    main()
