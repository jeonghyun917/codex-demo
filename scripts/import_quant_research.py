"""Offline, atomic import into NEW research namespaces; never replaces operating universes.

FRED price-index levels are not total-return series. Community membership remains
research-only, even where an individual transition was corrected from official evidence.
No prices, model timestamps, live signals or official-PIT classifications are rewritten.
"""
import argparse
import csv
import hashlib
import json
import math
import re
import subprocess
from datetime import date
from pathlib import Path


def validate_namespace(value):
    if not re.fullmatch(r'(SP500|NASDAQ100)_FREE_\d{8}', value):
        raise ValueError('Only a separately named FREE research namespace may be imported')


def sql_text(value):
    return 'NULL' if value is None else 'CONVERT(0x' + str(value).encode('utf-8').hex() + ' USING utf8mb4)'


def market_context_sql(namespace, source_index):
    validate_namespace(namespace)
    if source_index not in ('SP500', 'NASDAQ100'):
        raise ValueError('Unsupported source index')
    columns = ('symbol,snapshot_date,sector,industry,exchange,market,currency,close_price,volume,'
               'shares_outstanding,market_cap_usd,price_source,shares_source,market_cap_source,calculated_at')
    return ('INSERT INTO stock_market_snapshot(index_code,' + columns + ') SELECT '
            + sql_text(namespace) + ',' + ','.join('ms.' + col for col in columns.split(','))
            + ' FROM stock_market_snapshot ms JOIN stock_index_membership_snapshot m ON m.index_code='
            + sql_text(namespace) + ' AND m.symbol=ms.symbol AND m.snapshot_date=ms.snapshot_date'
            + ' AND m.is_member=TRUE WHERE ms.index_code=' + sql_text(source_index) + ';')


def expand_memberships(rows, dates):
    result = {}
    intervals = {}
    for row in rows:
        symbol = row['symbol']
        if not re.fullmatch(r'[A-Z0-9.^_-]{1,20}', symbol):
            raise ValueError('Invalid symbol: ' + symbol)
        start, end = date.fromisoformat(row['start_date']), date.fromisoformat(row['end_date'])
        if start > end or row['is_member'].lower() != 'true' or not row['source'].startswith('COMMUNITY_'):
            raise ValueError('Invalid research interval: ' + str(row))
        intervals.setdefault(symbol, []).append((start, end))
        for day in dates:
            if start <= day <= end:
                if (day, symbol) in result:
                    raise ValueError('Overlapping intervals: ' + symbol)
                result[day, symbol] = row['source']
    for symbol, spans in intervals.items():
        spans.sort()
        if any(a[1] >= b[0] for a, b in zip(spans, spans[1:])):
            raise ValueError('Overlapping intervals: ' + symbol)
    return [(day, symbol, source) for (day, symbol), source in sorted(result.items())]


def benchmark_rows(levels, sessions):
    for value in levels.values():
        if not math.isfinite(value) or value <= 0:
            raise ValueError('Index levels must be finite and positive')
    previous = None
    rows = []
    for day in sorted(set(sessions)):
        current = levels.get(day)
        daily = None if previous is None or current is None else 100 * (current / previous - 1)
        rows.append((day, current, daily))
        previous = current  # A missing session also invalidates the next return; no gap bridging.
    return rows


def read_levels(path, column):
    values = {}
    with path.open(encoding='utf-8-sig', newline='') as file:
        for row in csv.DictReader(file):
            day = date.fromisoformat(row['observation_date'])
            if day in values:
                raise ValueError('Duplicate benchmark date: ' + str(day))
            text = row[column].strip()
            values[day] = None if text in ('', '.') else float(text)
    return {day: value for day, value in values.items() if value is not None}


def verified_file(path, expected):
    content = path.read_bytes()
    if hashlib.sha256(content).hexdigest() != expected:
        raise ValueError('Hash mismatch: ' + str(path))
    return content


def query(client, sql):
    command = [str(client), '--host=127.0.0.1', '--user=root', '--database=king_yurina',
               '--batch', '--skip-column-names', '--default-character-set=utf8mb4', '--binary-mode']
    result = subprocess.run(command, input=sql, text=True, encoding='utf-8', capture_output=True, check=True)
    return result.stdout.strip()


def import_dataset(client, namespace, membership, benchmarks, manifest, apply):
    validate_namespace(namespace)
    encoded = json.dumps(manifest, sort_keys=True, separators=(',', ':'))
    digest = hashlib.sha256(encoded.encode()).hexdigest()
    previous = query(client, 'SELECT manifest_sha256 FROM stock_research_dataset WHERE index_code=' + sql_text(namespace))
    counts_sql = ('SELECT (SELECT COUNT(*) FROM stock_index_membership_snapshot WHERE index_code={0}),'
                  '(SELECT COUNT(*) FROM stock_benchmark_return_series WHERE index_code={0})').format(sql_text(namespace))
    counts = list(map(int, query(client, counts_sql).split('\t')))
    expected = [len(membership), len(benchmarks)]
    def context_count():
        actual = int(query(client, 'SELECT COUNT(*) FROM stock_market_snapshot WHERE index_code=' + sql_text(namespace)))
        eligible = int(query(client, 'SELECT COUNT(*) FROM stock_market_snapshot ms JOIN stock_index_membership_snapshot m '
                             'ON m.symbol=ms.symbol AND m.snapshot_date=ms.snapshot_date AND m.is_member=TRUE '
                             'WHERE m.index_code=' + sql_text(namespace) + ' AND ms.index_code='
                             + sql_text(manifest['sourceIndex'])))
        if actual != eligible:
            raise ValueError('Research market context missing or changed: ' + namespace)
        return actual
    if previous:
        if previous != digest or counts != expected:
            raise ValueError('Existing dataset differs; refuse overwrite: ' + namespace)
        return dict(index=namespace, status='ALREADY_IMPORTED', membershipRows=counts[0], benchmarkRows=counts[1],
                    marketContextRows=context_count(), sha256=digest)
    if counts != [0, 0]:
        raise ValueError('Unregistered namespace is not empty: ' + namespace)
    if apply:
        commands = ['START TRANSACTION;']
        # Registration collision aborts the transaction rather than silently replacing another import.
        commands.append('INSERT INTO stock_research_dataset(index_code,manifest_sha256,manifest_json) VALUES ('
                        + ','.join(map(sql_text, [namespace, digest, encoded])) + ');')
        for start in range(0, len(membership), 500):
            values = ['(' + ','.join(map(sql_text, [namespace, symbol, str(day), source])) + ')'
                      for day, symbol, source in membership[start:start + 500]]
            commands.append('INSERT INTO stock_index_membership_snapshot(index_code,symbol,snapshot_date,source) VALUES '
                            + ','.join(values) + ';')
        source = manifest['benchmarkSource']
        for start in range(0, len(benchmarks), 500):
            values = ['(' + ','.join([sql_text(namespace), sql_text(day),
                       'NULL' if level is None else format(level, '.12g'),
                       'NULL' if daily is None else format(daily, '.12g'), sql_text(source)]) + ')'
                      for day, level, daily in benchmarks[start:start + 500]]
            commands.append('INSERT INTO stock_benchmark_return_series(index_code,trade_date,benchmark_level,return_pct,source) VALUES '
                            + ','.join(values) + ';')
        commands.append(market_context_sql(namespace, manifest['sourceIndex']))
        commands.append('COMMIT;')
        query(client, '\n'.join(commands))
        counts = list(map(int, query(client, counts_sql).split('\t')))
        if counts != expected:
            raise RuntimeError('Post-import row count mismatch')
    return dict(index=namespace, status='IMPORTED' if apply else 'DRY_RUN', membershipRows=expected[0],
                benchmarkRows=expected[1], marketContextRows=context_count() if apply else None, sha256=digest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--corrected-dir', type=Path, required=True)
    parser.add_argument('--free-dir', type=Path, required=True)
    parser.add_argument('--db-client', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    corrections = json.loads((args.corrected_dir / 'manifest.json').read_text(encoding='utf-8'))
    originals = json.loads((args.free_dir / 'manifest.json').read_text(encoding='utf-8'))
    output_hashes = {item['path']: item['sha256'] for item in corrections['outputs']}
    original_hashes = {item['path']: item['sha256'] for item in originals['sources'] if item.get('sha256')}
    # A source's own missing rows cannot certify its calendar. Independent canonical
    # equity-session evidence retains an actual session even when FRED has a hole.
    sp_path = args.free_dir / 'raw/fred_SP500.csv'
    verified_file(sp_path, original_hashes['raw/fred_SP500.csv'])
    canonical_sessions = [date.fromisoformat(day) for day in query(args.db_client,
                          'SELECT trade_date FROM stock_candle_daily GROUP BY trade_date '
                          'HAVING COUNT(DISTINCT symbol)>=100 ORDER BY trade_date').splitlines()]
    calendar = sorted(set(read_levels(sp_path, 'SP500')) | set(canonical_sessions))
    dates = [date.fromisoformat(day) for day in query(args.db_client,
             'SELECT DISTINCT signal_date FROM stock_signal_snapshot ORDER BY signal_date').splitlines()]
    results = []
    for index, stem, column in [('SP500', 'sp500', 'SP500'), ('NASDAQ100', 'nasdaq100', 'NASDAQ100')]:
        filename = stem + '_membership_intervals.csv'
        raw = verified_file(args.corrected_dir / filename, output_hashes[filename])
        rows = list(csv.DictReader(raw.decode('utf-8-sig').splitlines()))
        if any(row['index_code'] != index for row in rows):
            raise ValueError('Mixed membership universe')
        membership = expand_memberships(rows, dates)
        benchmark_path = 'raw/fred_' + column + '.csv'
        verified_file(args.free_dir / benchmark_path, original_hashes[benchmark_path])
        levels = read_levels(args.free_dir / benchmark_path, column)
        benchmarks = benchmark_rows(levels, calendar)
        manifest = dict(classification='RECONSTRUCTED_RESEARCH_NOT_LIVE_OR_OFFICIAL_PIT', officialPitEligible=False,
                        membershipFile=filename, membershipSha256=output_hashes[filename],
                        correctionManifestSha256=hashlib.sha256((args.corrected_dir / 'manifest.json').read_bytes()).hexdigest(),
                        correctionManifest=corrections, benchmarkFile=benchmark_path,
                        benchmarkSha256=original_hashes[benchmark_path], benchmarkSource='FRED_' + column + '_PRICE_INDEX',
                        benchmarkDefinition='PRICE_INDEX_NOT_TOTAL_RETURN',
                        calendar='UNION_FRED_SP500_AND_CANONICAL_US_EQUITY_QUORUM_100',
                        canonicalSessionDates=[str(day) for day in canonical_sessions],
                        marketContextPolicy='COPY_EXISTING_SAME_DATE_SYMBOL_PRESERVE_SOURCES_AND_CALCULATED_AT_NOT_PIT',
                        membershipDates=[str(day) for day in dates], missingPolicy='NO_ZERO_FILL_NO_GAP_BRIDGING',
                        sourceIndex=index, firstDate=min(row['start_date'] for row in rows),
                        lastDate=max(row['end_date'] for row in rows),
                        featureAvailability='UNPROVEN_RECONSTRUCTION', promotionAllowed=False)
        results.append(import_dataset(args.db_client, index + '_FREE_20261018', membership, benchmarks, manifest, args.apply))
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
