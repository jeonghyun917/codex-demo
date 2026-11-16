"""Recover local vendor-quote data into a new research dataset, never the production DB."""
import argparse
import json
import math
import shutil
import subprocess
import sys
from collections import defaultdict
from datetime import datetime, timezone, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

from build_sp500_daily_dataset import ROOT, read_csv, write_csv, price_flags, digest

BASE = ROOT / 'output/sp500-daily-dataset-20261018'
DEFAULT_OUT = ROOT / 'output/sp500-daily-dataset-20261027'
PRICE_KEYS = ('open_price', 'high_price', 'low_price', 'close_price')


def validate_chart(symbol, chart):
    meta = chart['meta']
    expected = dict(symbol=symbol, currency='USD', instrumentType='EQUITY',
                    exchangeTimezoneName='America/New_York', dataGranularity='1d')
    if any(meta.get(k) != v for k, v in expected.items()):
        raise ValueError('IDENTITY_OR_PRICE_CONTRACT_MISMATCH')
    stamps = chart['timestamp']
    quote = chart['indicators']['quote'][0]
    if any(len(quote[k]) != len(stamps) for k in ('open','high','low','close','volume')):
        raise ValueError('ARRAY_LENGTH_MISMATCH')
    # Windows fromtimestamp rejects some valid pre-1970 listing timestamps.
    first = (datetime(1970, 1, 1, tzinfo=timezone.utc) + timedelta(seconds=meta['firstTradeDate'])).astimezone(ZoneInfo('America/New_York')).date().isoformat()
    result = {}
    for i, stamp in enumerate(stamps):
        day = datetime.fromtimestamp(stamp, ZoneInfo('America/New_York')).date().isoformat()
        if day in result:
            raise ValueError('DUPLICATE_SESSION')
        if day < first:
            raise ValueError('BAR_PRECEDES_VENDOR_FIRST_TRADE')
        result[day] = dict(zip(PRICE_KEYS, (quote[k][i] for k in ('open','high','low','close'))), volume=quote['volume'][i])
    return result


def compare_bar(a, b):
    try:
        return all(math.isclose(float(a[k]), float(b[k]), rel_tol=1e-5, abs_tol=1e-6)
                   for k in PRICE_KEYS) and int(a['volume']) == int(b['volume'])
    except (ValueError, TypeError):
        return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    out = args.out
    if out.exists():
        raise RuntimeError('Refusing to overwrite an existing version; choose a new --out.')
    base_manifest = json.loads((BASE / 'manifest.json').read_text())
    for name, sha in base_manifest['input_sha256'].items():
        assert digest(BASE / 'inputs' / name) == sha, name
    prices = read_csv(BASE / 'inputs/prices.csv.gz')
    fields = list(prices[0])
    by_symbol = defaultdict(dict)
    for r in prices:
        by_symbol[r['symbol']][r['trade_date']] = r
    candidate_rows = read_csv(BASE / 'local_recovery_candidates.csv.gz')
    candidates = defaultdict(list)
    for r in candidate_rows:
        candidates[r['symbol']].append(r)
    calendar = {r['trade_date'] for r in read_csv(BASE / 'inputs/calendar.csv.gz')}
    first, last = min(r['trade_date'] for r in prices), max(r['trade_date'] for r in prices)
    inputs = out / 'inputs'
    inputs.mkdir(parents=True)
    for p in (BASE / 'inputs').iterdir():
        if p.is_file() and p.name != 'prices.csv.gz':
            shutil.copyfile(p, inputs / p.name)
    shutil.copyfile(BASE / 'inputs/prices.csv.gz', inputs / 'original_prices.csv.gz')
    raw_dir = out / 'raw_recovery'
    raw_dir.mkdir()
    decisions, added, conflicts = [], [], []
    for symbol, rows in sorted(candidates.items()):
        source = Path(rows[0]['source_path'])
        sha = digest(source)
        assert all(r['source_sha256'] == sha for r in rows), symbol
        shutil.copyfile(source, raw_dir / source.name)
        chart = json.loads(source.read_bytes())['chart']['result'][0]
        meta = chart['meta']
        overlap, mismatches, recovered_member, recovered_total = 0, 0, 0, 0
        status = 'REJECTED'
        try:
            raw = validate_chart(symbol, chart)
            canonical = by_symbol[symbol]
            for day, existing in canonical.items():
                if day not in raw or price_flags(existing) or price_flags(raw[day]):
                    continue
                overlap += 1
                if not compare_bar(existing, raw[day]):
                    mismatches += 1
                    conflicts.append(dict(symbol=symbol, trade_date=day,
                                          canonical=json.dumps(existing), raw=json.dumps(raw[day])))
            if mismatches:
                raise ValueError('OVERLAP_OHLCV_MISMATCH')
            if canonical and overlap < 20:
                raise ValueError('INSUFFICIENT_OVERLAP_FOR_MIXING')
            # Empty canonical histories are not spliced: one intact vendor price basis is used.
            status = 'APPROVED_OVERLAP' if canonical else 'APPROVED_SINGLE_VENDOR_HISTORY'
            candidate_dates = {r['trade_date'] for r in rows}
            for day, bar in sorted(raw.items()):
                if not first <= day <= last or day not in calendar or day in canonical or price_flags(bar):
                    continue
                new = dict(symbol=symbol, trade_date=day, **bar, source='yahoo-chart-local-recovery', updated_at='')
                prices.append(new)
                added.append(dict(new, raw_sha256=sha, identity_check='EXACT_VENDOR_SYMBOL_USD_EQUITY',
                                  basis_check=status, stable_security_id_verified=0,
                                  fills_member_gap=int(day in candidate_dates)))
                recovered_total += 1
                recovered_member += int(day in candidate_dates)
        except (ValueError, KeyError, TypeError) as exc:
            status = 'REJECTED:' + str(exc)
        decisions.append(dict(symbol=symbol, vendor_symbol=meta.get('symbol',''),
                              vendor_name=meta.get('longName',''), currency=meta.get('currency',''),
                              instrument_type=meta.get('instrumentType',''),
                              overlap_sessions=overlap, mismatched_sessions=mismatches,
                              candidate_member_rows=len(rows), recovered_member_rows=recovered_member,
                              added_history_rows=recovered_total, decision=status, raw_sha256=sha,
                              price_basis='Yahoo indicators.quote OHLCV unchanged; adjclose not used',
                              stable_security_id_verified=0))
    write_csv(inputs / 'prices.csv.gz', sorted(prices, key=lambda r:(r['symbol'],r['trade_date'])), fields)
    write_csv(out / 'recovery_decisions.csv', decisions, list(decisions[0]))
    write_csv(out / 'recovery_lineage.csv.gz', added, fields + ['raw_sha256','identity_check','basis_check','stable_security_id_verified','fills_member_gap'])
    write_csv(out / 'overlap_conflicts.csv', conflicts, ['symbol','trade_date','canonical','raw'])
    provenance = dict(created_at=datetime.now(timezone.utc).isoformat(), base=str(BASE),
                      base_manifest_sha256=digest(BASE/'manifest.json'),
                      price_contract_source='src/main/java/com/kingyurina/demo/stock/YahooChartCandleService.java',
                      price_contract_sha256=digest(ROOT/'src/main/java/com/kingyurina/demo/stock/YahooChartCandleService.java'),
                      candidate_sha256=digest(BASE/'local_recovery_candidates.csv.gz'),
                      identity_limit='Exact vendor symbol, USD/EQUITY, first-trade date and NY session checked. Stable ID/ticker reuse history not independently certified.',
                      raw_sha256={p.name:digest(p) for p in sorted(raw_dir.iterdir())})
    (inputs / 'recovery_provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')
    # Run in a separate process after releasing large collections.
    del prices, by_symbol, added
    subprocess.run([sys.executable, str(ROOT/'scripts/build_sp500_daily_dataset.py'), '--offline','--out',str(out)], check=True)
    manifest = json.loads((out/'manifest.json').read_text())
    summary = dict(base_missing_rows=base_manifest['issues']['MISSING_BAR'],
                   recovered_member_rows=sum(r['recovered_member_rows'] for r in decisions),
                   added_history_rows=sum(r['added_history_rows'] for r in decisions),
                   approved_symbols=sum(r['decision'].startswith('APPROVED') for r in decisions),
                   remaining_missing_rows=manifest['issues'].get('MISSING_BAR',0),
                   usable_rows=manifest['usable_rows'], issues=manifest['issues'],
                   limitations=provenance['identity_limit'])
    assert summary['base_missing_rows']-summary['recovered_member_rows'] == summary['remaining_missing_rows']
    (out/'recovery_summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    missing = read_csv(out/'missing_symbols.csv')
    decision_map = {r['symbol']:r['decision'] for r in decisions}
    raw_inventory = {r['symbol']:r['status'] for r in read_csv(BASE/'local_recovery_inventory.csv')}
    requests = [dict(r, recovery_status=decision_map.get(r['symbol'],raw_inventory.get(r['symbol'],'UNKNOWN')),
                     priority='P0', required='Historical identifier evidence and missing OHLCV; terminal payout if delisted.') for r in missing]
    write_csv(out/'additional_prices_required.csv', requests, list(requests[0]))
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
