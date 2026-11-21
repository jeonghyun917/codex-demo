"""Append-only verified data extension for the unchanged original paper model."""
from collections import defaultdict
from datetime import datetime
import json
import math
import shutil
import subprocess
import sys
from zoneinfo import ZoneInfo
import pandas as pd
from acquire_sp500_top20_latest import OUT, ANCHOR
from build_sp500_paper_start import approve_extension
from build_sp500_daily_dataset import digest, read_csv, write_csv, price_flags, members_on
from recover_sp500_daily_dataset import validate_chart
from sp500_paper_ledger import ROOT, DEFAULT, protocol

BASE = ROOT / 'output/sp500-paper-start-20261031/dataset'
DATA = OUT / 'dataset'
SOURCE = 'https://www.spglobal.com/spdji/en/documents/indexnews/announcements/20261030-1485650/1485650_kvue-ramp546.pdf'


def extend_membership(original, roster, aliases):
    prior = {aliases.get(s, s) for s in members_on(original, '2026-10-30')}
    if roster - prior != {'OKTA'} or prior - roster != {'KVUE'}:
        raise ValueError('UNREVIEWED_MEMBERSHIP_CHANGE')
    rows = [dict(r) for r in original]
    for r in rows:
        if r['end_date'] == '2026-10-30' and r['is_member'].lower() == 'true':
            r['end_date'] = '2026-11-03' if r['symbol'] == 'KVUE' else ANCHOR
            r['provenance_ids'] += ';top20-20261114-reviewed'
    rows.append(dict(index_code='SP500', symbol='OKTA', start_date='2026-11-04', end_date=ANCHOR,
                     source='OFFICIAL_CHANGE_WITH_CURRENT_ENDPOINT', provenance_ids=SOURCE, is_member='true'))
    if {aliases.get(s, s) for s in members_on(rows, ANCHOR)} != roster:
        raise ValueError('ROSTER_ENDPOINT_MISMATCH')
    return rows


def approve_new_symbol(incoming, sessions):
    required = sessions[-63:]
    if len(required) != 63 or any(d not in incoming or price_flags(incoming[d]) for d in required):
        raise ValueError('INSUFFICIENT_VALID_WARMUP')
    return {d: incoming[d] for d in sessions if d in incoming}


def main():
    if DATA.exists():
        raise ValueError('Refusing dataset overwrite')
    p = protocol(DEFAULT)
    sources = json.loads((OUT / 'acquisition.json').read_text())
    supplement = OUT / 'supplement.json'
    if supplement.exists():
        sources += json.loads(supplement.read_text())
    for r in sources:
        if r.get('sha256') and digest(OUT / r['path']) != r['sha256']:
            raise ValueError('SOURCE_HASH_CHANGED: ' + r['name'])
    sources = {r['name']: r for r in sources}
    review = json.loads((OUT / 'membership-review.json').read_text())
    if (review['added'], review['removed'], review['effectiveSession']) != ('OKTA', 'KVUE', '2026-11-04'):
        raise ValueError('MEMBERSHIP_REVIEW_CHANGED')
    roster = set(pd.read_csv(OUT / 'current_roster.csv').Symbol.str.replace('.', '-', regex=False))
    aliases = {r['symbol']: r['provider_symbol'] for r in read_csv(BASE / 'inputs/aliases.csv')}
    intervals = extend_membership(read_csv(BASE / 'inputs/sp500_membership_intervals.csv'), roster, aliases)
    calendar = read_csv(BASE / 'inputs/calendar.csv.gz')
    old_days = {r['trade_date']: r for r in calendar}
    new_days = [r['session'] for r in p['calendar'] if '2026-10-30' < r['session'] <= ANCHOR]
    sessions = sorted(set(old_days) | set(new_days))
    chart = json.loads((OUT / 'raw/yahoo-INDEX.json').read_bytes())['chart']['result'][0]
    expected = dict(symbol='^GSPC', instrumentType='INDEX', currency='USD',
                    exchangeTimezoneName='America/New_York', dataGranularity='1d')
    if any(chart['meta'].get(k) != v for k, v in expected.items()):
        raise ValueError('WRONG_INDEX_IDENTITY')
    levels = {datetime.fromtimestamp(t, ZoneInfo('America/New_York')).date().isoformat(): round(float(c), 2)
              for t, c in zip(chart['timestamp'], chart['indicators']['quote'][0]['close'])}
    shared = set(levels) & set(old_days)
    if len(shared) < 15 or any(abs(float(old_days[d]['benchmark_level']) - levels[d]) >= .011 for d in shared):
        raise ValueError('INDEX_OVERLAP_CONFLICT')
    if any(d not in levels or not math.isfinite(levels[d]) or levels[d] <= 0 for d in new_days):
        raise ValueError('MISSING_INDEX_SESSION')
    calendar += [dict(trade_date=d, benchmark_level=levels[d], source='YAHOO_GSPC_PRIOR_FRED_OVERLAP_VERIFIED') for d in new_days]
    prices = read_csv(BASE / 'inputs/prices.csv.gz')
    price_fields = list(prices[0])
    by_symbol = defaultdict(dict)
    for r in prices:
        by_symbol[r['symbol']][r['trade_date']] = r
    audit, lineage, events = [], [], []
    for symbol in sorted(roster):
        source = sources.get('yahoo-' + symbol + '-warmup', sources['yahoo-' + symbol])
        try:
            chart = json.loads((OUT / source['path']).read_bytes())['chart']['result'][0]
            incoming = validate_chart(symbol, chart)
            for kind, items in chart.get('events', {}).items():
                for event in items.values():
                    day = datetime.fromtimestamp(event['date'], ZoneInfo('America/New_York')).date().isoformat()
                    if new_days[0] <= day <= ANCHOR:
                        events.append(dict(symbol=symbol, date=day, kind=kind, details=json.dumps(event)))
                        if kind != 'dividends':
                            raise ValueError('NON_DIVIDEND_ACTION_REQUIRES_REVIEW')
            if symbol == 'OKTA':
                recent = json.loads((OUT / sources['yahoo-OKTA']['path']).read_bytes())['chart']['result'][0]
                approve_extension(validate_chart(symbol, recent), incoming, [])
                added = approve_new_symbol(incoming, sessions)
            else:
                added = approve_extension(by_symbol[symbol], incoming, new_days)
            for day, bar in added.items():
                prices.append(dict(symbol=symbol, trade_date=day, **bar, source='yahoo-top20-20261114', updated_at=''))
                lineage.append(dict(symbol=symbol, trade_date=day, sha256=source['sha256'], source_path=source['path']))
            audit.append(dict(symbol=symbol, status='APPENDED', added=len(added), reason=''))
        except (ValueError, KeyError, TypeError) as error:
            audit.append(dict(symbol=symbol, status='WITHHELD', added=0, reason=str(error)))
    write_csv(OUT / 'integration_audit.csv', audit, ['symbol', 'status', 'added', 'reason'])
    write_csv(OUT / 'new_corporate_events.csv', events, ['symbol', 'date', 'kind', 'details'])
    if sum(r['status'] == 'APPENDED' for r in audit) < len(roster) * .98:
        raise ValueError('TOO_MANY_UNRESOLVED_PRICE_SERIES')
    inputs = DATA / 'inputs'
    inputs.mkdir(parents=True)
    for path in (BASE / 'inputs').iterdir():
        if path.is_file():
            shutil.copyfile(path, inputs / path.name)
    write_csv(inputs / 'prices.csv.gz', sorted(prices, key=lambda r: (r['symbol'], r['trade_date'])), price_fields)
    write_csv(inputs / 'calendar.csv.gz', calendar, list(calendar[0]))
    write_csv(inputs / 'sp500_membership_intervals.csv', intervals, list(intervals[0]))
    overrides = read_csv(inputs / 'quality_overrides.csv')
    for row in overrides:
        if row['end_date'] == '2026-10-30':
            row['end_date'] = ANCHOR
    write_csv(inputs / 'quality_overrides.csv', overrides, list(overrides[0]))
    meta = json.loads((inputs / 'manifest.json').read_text())
    meta['coverage']['lastDate'] = ANCHOR
    meta['latest_extension'] = dict(parent=str(BASE), parentPriceSha256=digest(BASE / 'inputs/prices.csv.gz'),
                                   membershipReviewSha256=digest(OUT / 'membership-review.json'),
                                   indexOverlapSessions=len(shared), indexIndependentLatestSource=False,
                                   strategyChanged=False,
                                   limitations=['FRED latest download timed out; prior FRED/Yahoo overlap checked',
                                                'KVUE removed Nov4; missing Nov2-3 bars not filled',
                                                'Corporate action review limited to provider events and price overlap'])
    (inputs / 'manifest.json').write_text(json.dumps(meta, indent=2), encoding='utf-8')
    write_csv(OUT / 'added_price_lineage.csv', lineage, ['symbol', 'trade_date', 'sha256', 'source_path'])
    del prices, by_symbol
    subprocess.run([sys.executable, 'scripts/build_sp500_daily_dataset.py', '--offline', '--out', str(DATA)], check=True)
    subprocess.run([sys.executable, 'scripts/verify_sp500_daily_dataset.py', '--out', str(DATA)], check=True)


if __name__ == '__main__':
    main()
