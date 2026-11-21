"""Bounded public corporate-action acquisition; evidence only, no DB writes.

Usage: python acquire_actions.py --symbols-file symbols.txt
Snapshots and normalized events are retained with SHA-256 provenance.
"""
import argparse
import concurrent.futures
import csv
import hashlib
import json
from pathlib import Path
from datetime import datetime, timezone
from zoneinfo import ZoneInfo
import threading
import time
import subprocess
import urllib.error
import urllib.parse
import urllib.request

REPO = Path(__file__).resolve().parents[1]
ROOT = REPO / 'tmp/pit-acquisition/actions'
LOCK = threading.Lock()
FAILURES = 0
STOP = threading.Event()
PREVIOUS = {}
FIELDS = ['sourceId', 'symbol', 'action', 'effectiveDate', 'dateBasis',
          'numerator', 'denominator', 'amount', 'currency', 'amountBasis', 'ratioBasis']


def now():
    return datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')


def fetch(symbol):
    global FAILURES
    if symbol in PREVIOUS:
        return PREVIOUS[symbol]
    with LOCK:
        if STOP.is_set() or FAILURES >= 12:
            return None, [], {'symbol': symbol, 'status': 'SKIPPED_FAILURE_BUDGET'}
    normal = symbol.replace('.', '-')
    query = urllib.parse.urlencode(dict(period1=1577836800, period2=1792195200,
                                       interval='1d', events='div,splits,capitalGains',
                                       includeAdjustedClose='true'))
    url = 'https://query1.finance.yahoo.com/v8/finance/chart/' + urllib.parse.quote(normal) + '?' + query
    retrieved = now()
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 quant-research/0.1'})
        with urllib.request.urlopen(req, timeout=20) as response:
            body = response.read()
            status = response.status
        result = json.loads(body)['chart']['result'][0]
        if not result:
            raise ValueError('EMPTY_PROVIDER_RESULT')
        filename = 'raw/' + normal + '.json'
        (ROOT / filename).write_bytes(body)
        sid = 'yahoo-chart-' + normal
        source = dict(id=sid, url=url, publisher='Yahoo Finance', publishedDate=None,
                      retrievedAt=retrieved, sha256=hashlib.sha256(body).hexdigest(), path=filename)
        zone = ZoneInfo(result['meta'].get('exchangeTimezoneName', 'America/New_York'))
        events = []
        for group, action in [('splits', 'SPLIT'), ('dividends', 'DIVIDEND')]:
            for item in result.get('events', {}).get(group, {}).values():
                day = datetime.fromtimestamp(item['date'], zone).date().isoformat()
                events.append(dict(sourceId=sid, symbol=symbol, action=action,
                                   effectiveDate=day, dateBasis='EX_DATE',
                                   numerator=item.get('numerator'), denominator=item.get('denominator'),
                                   amount=item.get('amount'), currency=result['meta'].get('currency'),
                                   amountBasis='PROVIDER_REPORTED_UNVERIFIED' if action == 'DIVIDEND' else None,
                                   ratioBasis='PROVIDER_ADJUSTMENT_FACTOR_UNVERIFIED' if action == 'SPLIT' else None))
        dates = [datetime.fromtimestamp(t, zone).date().isoformat() for t in result.get('timestamp', [])]
        info = dict(symbol=symbol, status='OK', httpStatus=status, bars=len(dates),
                    firstBar=min(dates, default=None), lastBar=max(dates, default=None),
                    splits=sum(e['action'] == 'SPLIT' for e in events),
                    dividends=sum(e['action'] == 'DIVIDEND' for e in events),
                    capitalGains=len(result.get('events', {}).get('capitalGains', {})))
        with LOCK:
            FAILURES = 0
        return source, events, info
    except (OSError, ValueError, KeyError, TypeError) as exc:
        with LOCK:
            FAILURES += 1
            if isinstance(exc, urllib.error.HTTPError) and exc.code == 429:
                STOP.set()
        return None, [], dict(symbol=symbol, status='FAILED', error=str(exc), url=url, attemptedAt=retrieved)
    finally:
        time.sleep(0.12)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--symbols-file')
    parser.add_argument('--membership-csv')
    parser.add_argument('--database-symbols', action='store_true')
    parser.add_argument('--resume-skipped', action='store_true')
    args = parser.parse_args()
    if (ROOT / 'bundle.json').exists() and not args.resume_skipped:
        raise ValueError('Existing evidence found; refusing to overwrite snapshots')
    symbols = {'APH', 'AAPL', 'NVDA', 'AVGO', 'GE', 'GEHC', 'GEV', 'JNJ', 'KVUE', 'MMM', 'SOLV', 'ATVI', 'VMW'}
    if args.symbols_file:
        symbols.update(Path(args.symbols_file).read_text(encoding='utf-8-sig').split())
    if args.membership_csv:
        with open(args.membership_csv, encoding='utf-8-sig', newline='') as stream:
            symbols.update(row['symbol'] for row in csv.DictReader(stream))
    if args.database_symbols:
        database = subprocess.run([
            str(REPO / '.tools/mariadb-11.8.2-winx64/bin/mariadb.exe'),
            '--host=127.0.0.1', '--user=root', '--database=king_yurina',
            '--batch', '--skip-column-names',
            '--execute=SELECT DISTINCT symbol FROM stock_index_membership_snapshot UNION SELECT DISTINCT symbol FROM stock_candle_daily ORDER BY symbol'
        ], check=True, capture_output=True, text=True)
        symbols.update(database.stdout.split())
    symbols = sorted(s for s in symbols if s and '/' not in s and '\\' not in s)
    if len(symbols) > 1000:
        raise ValueError('bounded acquisition maximum is 1000 symbols')
    (ROOT / 'raw').mkdir(parents=True, exist_ok=True)
    if args.resume_skipped:
        old_bundle = json.loads((ROOT / 'bundle.json').read_text(encoding='utf-8'))
        old_manifest = json.loads((ROOT / 'manifest.json').read_text(encoding='utf-8'))
        by_symbol = {s['id'].removeprefix('yahoo-chart-'): s for s in old_bundle['sources']}
        for result in old_manifest['results']:
            symbol = result['symbol']
            if result['status'] == 'OK':
                PREVIOUS[symbol] = (by_symbol[symbol.replace('.', '-')],
                                    [e for e in old_bundle['events'] if e['symbol'] == symbol], result)
            elif result['status'] == 'FAILED':
                PREVIOUS[symbol] = (None, [], result)
        for name in ['bundle.json', 'manifest.json', 'events.csv']:
            target = ROOT / (name + '.pass1')
            if target.exists():
                raise ValueError('Resume archive already exists: no overwrite permitted')
            (ROOT / name).rename(target)
    started = now()
    sources, events, results = [], [], []
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for source, rows, result in pool.map(fetch, symbols):
            if source:
                sources.append(source)
            events.extend(rows)
            results.append(result)
            print(json.dumps(result), flush=True)
    bundle = dict(sources=sources, events=sorted(events, key=lambda e: (e['symbol'], e['effectiveDate'], e['action'])),
                  coverage='PROVIDER_REPORTED_EVENTS',
                  warning='Backfilled current vendor history; no announcement timestamps, historical snapshot proof, merger/delisting coverage, or PIT predictor eligibility. EX_DATE is vendor event-date convention, not independently verified per row.')
    (ROOT / 'bundle.json').write_text(json.dumps(bundle, indent=2), encoding='utf-8')
    with (ROOT / 'events.csv').open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, FIELDS)
        writer.writeheader()
        writer.writerows(bundle['events'])
    manifest = dict(startedAt=started, finishedAt=now(), requestedSymbols=len(symbols),
                    succeededSymbols=len(sources), failedSymbols=sum(r['status'] == 'FAILED' for r in results),
                    skippedSymbols=sum(r['status'].startswith('SKIPPED') for r in results),
                    eventCount=len(events), splits=sum(e['action'] == 'SPLIT' for e in events),
                    dividends=sum(e['action'] == 'DIVIDEND' for e in events), results=results,
                    requestedStart='2020-01-01', requestedEndExclusive='2026-10-17')
    (ROOT / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in manifest.items() if k != 'results'}))


if __name__ == '__main__':
    main()
