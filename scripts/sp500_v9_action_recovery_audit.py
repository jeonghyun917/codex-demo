"""Read-only input audit; writes only the assigned recovery evidence directory."""
import csv
import gzip
import hashlib
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/sp500-v9-actions-recovery-20261101'
DATA = ROOT / 'output/sp500-actions-20261028-retry/dataset'
TARGET = ROOT / 'output/sp500-v9-full-preflight-20261101/remaining_share_targets.csv'
BUNDLE = ROOT / 'tmp/pit-acquisition/actions/bundle.json'
SEC = ROOT / 'output/sp500-v9-source-recovery-20261101'
KEYS = ('open_price', 'high_price', 'low_price', 'close_price')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def rows(path):
    opener = gzip.open if path.suffix == '.gz' else open
    with opener(path, 'rt', encoding='utf-8-sig', newline='') as stream:
        yield from csv.DictReader(stream)


def write_csv(name, data, fields):
    with (OUT / name).open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(data)


def dump(name, data):
    (OUT / name).write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def ohlc_equal(row, quote, index):
    for field, vendor in zip(KEYS, ('open', 'high', 'low', 'close')):
        value = quote[vendor][index]
        if value is None or not row.get(field):
            return False
        actual = float(row[field])
        if abs(actual - value) > max(0.00001, abs(value) * 0.00001):
            return False
    return True


def main():
    targets = list(rows(TARGET))
    symbols = {r['symbol'] for r in targets}
    assert len(targets) == len(symbols) == 55
    inputs = [TARGET, BUNDLE, DATA / 'manifest.json', DATA / 'price_history.csv.gz',
              DATA / 'daily_panel.csv.gz', DATA / 'price_anomalies.csv',
              DATA / 'inputs/aliases.csv', DATA / 'inputs/quality_overrides.csv',
              OUT / 'primary_evidence.json']
    before = {str(p.relative_to(ROOT)): digest(p) for p in inputs}
    issuer_map = {r['symbol']: r for r in json.loads((SEC / 'issuer-map.json').read_text())}
    for filename in ('issuer-map.json', 'company-tickers.json'):
        before[str((SEC / filename).relative_to(ROOT))] = digest(SEC / filename)
    identities = []
    for target in targets:
        symbol = target['symbol']
        current = issuer_map[symbol]
        cik = '1415404' if symbol == 'SATS' else str(current['cik'])
        path = SEC / f'submissions-{int(cik):010d}.json'
        sub = json.loads(path.read_text())
        assert int(sub['cik']) == int(cik)
        before[str(path.relative_to(ROOT))] = digest(path)
        identities.append(dict(symbol=symbol, current_ticker_cik=current['cik'],
            current_ticker_issuer=current['name'], target_interval_cik=cik,
            interval_start=target['firstMissing'], interval_end=target['lastMissing'],
            submissions_name=sub['name'], submissions_current_tickers=';'.join(sub['tickers']),
            status='HISTORICAL_ECHOSTAR_CONFIRMED_CURRENT_STRIVE_REJECTED' if symbol == 'SATS'
                else 'PSTG_TO_P_SAME_ISSUER_CONFIRMED' if symbol == 'P'
                else 'CURRENT_IDENTITY_ONLY_NOT_HISTORICAL_CERTIFICATION',
            evidence_ids='echostar-ticker;strive-ticker-reuse' if symbol == 'SATS'
                else 'everpure-ticker;everpure-completion-confirmation' if symbol == 'P' else '',
            submissions_path=str(path.relative_to(ROOT)), submissions_sha256=digest(path),
            source_url=f'https://data.sec.gov/submissions/CIK{int(cik):010d}.json',
            price_multiplier='', vendor_price_basis_certified=False))
    identity_by_symbol = {r['symbol']: r for r in identities}
    bundle = json.loads(BUNDLE.read_text(encoding='utf-8'))
    sources = {s['id']: s for s in bundle['sources']}
    legal = json.loads((OUT / 'primary_evidence.json').read_text(encoding='utf-8'))
    evidence = []
    for event in legal:
        item = dict(event, observed_on='2026-11-01',
                    evidence_type='WEB_READER_PRIMARY_SOURCE_PARAPHRASE',
                    vendor_ohlc_certified=False, price_multiplier=None)
        item['summary_sha256'] = hashlib.sha256(event['summary'].encode()).hexdigest()
        if event.get('local_source'):
            p = ROOT / event['local_source']
            item['local_source_sha256'] = digest(p)
            before[event['local_source']] = digest(p)
            item['evidence_type'] = 'EXISTING_LOCAL_PRIMARY_SOURCE_AND_AUDIT'
        evidence.append(item)
    histories = defaultdict(dict)
    for row in rows(DATA / 'price_history.csv.gz'):
        if row['symbol'] in symbols:
            histories[row['symbol']][row['trade_date']] = row
    panel = defaultdict(Counter)
    for row in rows(DATA / 'daily_panel.csv.gz'):
        if row['symbol'] in symbols:
            panel[row['symbol']]['member_rows'] += 1
    anomalies = [r for r in rows(DATA / 'price_anomalies.csv') if r['symbol'] in symbols]
    events = []
    for event in bundle['events']:
        if event['symbol'] in symbols and event['action'] == 'SPLIT':
            source = sources[event['sourceId']]
            relevant = [e for e in legal if event['symbol'] in e['symbols']]
            events.append(dict(symbol=event['symbol'], vendor_date=event['effectiveDate'],
                vendor_numerator=event['numerator'], vendor_denominator=event['denominator'],
                vendor_ratio_basis=event['ratioBasis'],
                legal_evidence_ids=';'.join(e['id'] for e in relevant),
                legal_kinds=';'.join(e['kind'] for e in relevant),
                source_url=source['url'], source_sha256=source['sha256'],
                price_multiplier='', decision='NO_ADJUSTMENT_AUTHORIZED'))
    audits, lineage, boundaries = [], [], []
    for target in targets:
        symbol = target['symbol']
        history = histories[symbol]
        source = sources.get('yahoo-chart-' + symbol)
        overlap = matches = 0
        name = vendor_start = vendor_end = ''
        source_status = 'NO_SAVED_VENDOR_SOURCE'
        if source:
            path = BUNDLE.parent / source['path']
            assert digest(path) == source['sha256'], symbol
            before[str(path.relative_to(ROOT))] = digest(path)
            chart = json.loads(path.read_bytes())['chart']['result'][0]
            name = chart['meta'].get('longName', chart['meta'].get('shortName', ''))
            times = chart.get('timestamp', [])
            dates = [datetime.fromtimestamp(t, timezone.utc).date().isoformat() for t in times]
            vendor_start, vendor_end = (min(dates), max(dates)) if dates else ('', '')
            quote = chart['indicators']['quote'][0]
            for index, day in enumerate(dates):
                if day in history:
                    overlap += 1
                    matches += ohlc_equal(history[day], quote, index)
            source_status = 'HASH_VERIFIED_SAVED_VENDOR'
            lineage.append(dict(symbol=symbol, path=str(path.relative_to(ROOT)),
                sha256=source['sha256'], url=source['url'], retrieved_at=source.get('retrievedAt')))
        relevant = [e for e in legal if symbol in e['symbols']]
        blockers = ['VENDOR_OHLC_BASIS_NOT_CERTIFIED', 'ACTION_MASTER_NOT_EXHAUSTIVE']
        if not source:
            blockers.append('NO_SAVED_VENDOR_ACTION_SOURCE')
        if overlap != matches:
            blockers.append('SAVED_VENDOR_OHLC_OVERLAP_MISMATCH')
        if not history:
            blockers.append('NO_DATASET_PRICE_HISTORY')
        if symbol in ('FDXF', 'HONA'):
            blockers.append('CHILD_STANDALONE_HISTORY_ONLY_NO_PARENT_BACKFILL')
            blockers.append('WHEN_ISSUED_BARS_REQUIRE_SEPARATE_SESSION_BASIS')
        if symbol in ('ECHO', 'SATS', 'P'):
            blockers.append('EFFECTIVE_DATED_SECURITY_ID_MAPPING_REQUIRED')
        if symbol == 'FERG':
            blockers.append('REINCORPORATION_EXCHANGE_CURRENCY_LINEAGE_REQUIRED')
        if symbol == 'SATS' and any(d >= '2026-06-24' for d in history):
            blockers.append('OLD_TICKER_ROWS_AFTER_RENAME_REQUIRE_DEDUPLICATION_REVIEW')
        if symbol == 'SATS':
            blockers.append('CURRENT_SATS_STRIVE_CIK1920406_MUST_NOT_BE_USED')
        if any(e['kind'] in ('SPINOFF_PARENT', 'COMPLEX_SEPARATION') for e in relevant):
            blockers.append('DISTRIBUTION_PRICE_BASIS_NOT_RECONCILED')
        for event in relevant:
            day = event['effective_date']
            earlier = sorted(d for d in history if d < day)
            later = sorted(d for d in history if d >= day)
            prior, following = earlier[-1] if earlier else '', later[0] if later else ''
            boundaries.append(dict(symbol=symbol, evidence_id=event['id'], effective_date=day,
                date_basis=event['date_basis'], prior_date=prior, next_date=following,
                prior_close=history[prior].get('close_price', '') if prior else '',
                next_close=history[following].get('close_price', '') if following else '',
                earlier_history_rows=len(earlier),
                interpretation='OBSERVATION_ONLY_NOT_RETURN_OR_ADJUSTMENT_FACTOR'))
        audits.append(dict(**target, dataset_price_rows=len(history),
            target_interval_cik=identity_by_symbol[symbol]['target_interval_cik'],
            identity_status=identity_by_symbol[symbol]['status'],
            dataset_first=min(history) if history else '', dataset_last=max(history) if history else '',
            dataset_member_rows=panel[symbol]['member_rows'], vendor_status=source_status,
            vendor_name=name, vendor_first=vendor_start, vendor_last=vendor_end,
            ohlc_overlap_rows=overlap, ohlc_matching_rows=matches, ohlc_mismatch_rows=overlap-matches,
            vendor_split_entries=sum(e['symbol'] == symbol for e in events),
            primary_evidence_ids=';'.join(e['id'] for e in relevant),
            pre_regular_way_rows=sum(d < {'FDXF': '2026-06-01', 'HONA': '2026-06-29'}.get(symbol, '') for d in history),
            dataset_anomaly_rows=sum(a['symbol'] == symbol for a in anomalies),
            action_facts_status='PRIMARY_EVIDENCE_REVIEWED' if relevant else 'NO_PRIMARY_ACTION_FACT_REVIEWED',
            price_basis_certified=False, readiness='BLOCKED_FOR_CERTIFIED_PRICE_SHARE_BASIS',
            blockers=';'.join(blockers)))
    write_csv('symbol_audit.csv', audits, list(audits[0]))
    write_csv('vendor_split_entries.csv', events, list(events[0]))
    write_csv('event_boundaries.csv', boundaries, list(boundaries[0]))
    write_csv('dataset_anomalies.csv', anomalies, ['symbol', 'trade_date', 'issue', 'detail'])
    dump('source_evidence.json', evidence)
    dump('vendor_source_lineage.json', lineage)
    write_csv('identity_audit.csv', identities, list(identities[0]))
    assert all(digest(ROOT / p) == sha for p, sha in before.items())
    summary = dict(target_symbols=55, audited_symbols=len(audits),
        primary_evidence_records=len(evidence),
        sec_identity_rows=len(identities),
        symbols_with_primary_evidence=sum(bool(r['primary_evidence_ids']) for r in audits),
        vendor_sources_hash_verified=len(lineage), vendor_split_entries=len(events),
        literal_split_symbol_entries=sum(e['legal_kinds'] == 'LITERAL_SHARE_SPLIT' for e in events),
        ohlc_overlap_rows=sum(r['ohlc_overlap_rows'] for r in audits),
        ohlc_mismatch_rows=sum(r['ohlc_mismatch_rows'] for r in audits),
        certified_symbols=0, input_files_unchanged=True, input_sha256=before,
        no_returns_calculated=True, no_price_multipliers_applied=True,
        no_parent_child_history_stitching=True, no_database_or_git_writes=True,
        coverage_limit='Saved vendor acquisition requests 2020-01-01 through 2026-10-17 exclusive; not a complete action master. Primary searches focused on identified events and six named symbols.',
        overlap_limit='Same-vendor snapshot agreement is not independent numeric certification, execution-price validation, or historical availability proof.',
        decision_effective_date='2026-11-01',
        historical_asof_policy='Retrospective audit only; publication and economic effective dates are retained separately. Do not backdate audit availability.')
    dump('verification.json', summary)
    print(json.dumps({k: v for k, v in summary.items() if k != 'input_sha256'}, indent=2))


if __name__ == '__main__':
    main()
