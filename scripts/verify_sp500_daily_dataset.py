"""Independent checks against frozen inputs; no DB or network access."""
import bisect
import argparse
import csv
import gzip
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/sp500-daily-dataset-20261018'


def rows(path):
    with (gzip.open(path, 'rt', encoding='utf-8', newline='') if path.suffix == '.gz'
          else path.open(encoding='utf-8', newline='')) as f:
        yield from csv.DictReader(f)


def expected_next_session(days, day):
    i=bisect.bisect_right(days,day)
    return days[i] if i<len(days) else ''


def main():
    global OUT
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=OUT)
    OUT = parser.parse_args().out
    meta = json.loads((OUT / 'manifest.json').read_text())
    for name, expected in meta['input_sha256'].items():
        assert hashlib.sha256((OUT / 'inputs' / name).read_bytes()).hexdigest() == expected, name
    days = [r['trade_date'] for r in rows(OUT / 'inputs/calendar.csv.gz')]
    selected = [d for d in days if meta['start'] <= d <= meta['end']]
    expected = 0
    for r in rows(OUT / 'inputs/sp500_membership_intervals.csv'):
        if r['is_member'] == 'true':
            expected += max(0, bisect.bisect_right(selected, r['end_date']) - bisect.bisect_left(selected, r['start_date']))
    raw_values = {(r['symbol'],r['trade_date']):tuple(r[k] for k in
                  ('open_price','high_price','low_price','close_price','volume','source'))
                  for r in rows(OUT / 'inputs/prices.csv.gz')}
    seen, missing_keys, counts, daily = set(), set(), Counter(), Counter()
    usable = 0
    for r in rows(OUT / 'daily_panel.csv.gz'):
        key = r['trade_date'], r['symbol']
        assert key not in seen, key
        seen.add(key)
        assert r['membership_start'] <= r['trade_date'] <= r['membership_end']
        assert r['next_session_date'] == expected_next_session(days, r['trade_date'])
        flags = r['quality_flags'].split('|') if r['quality_flags'] else []
        counts.update(flags)
        assert int(r['price_usable']) == int(not flags)
        assert int(r['history_200_ready']) == int(int(r['consecutive_usable_sessions']) >= 200)
        if 'MISSING_BAR' in flags:
            assert not r['open_price'] and not r['close_price'] and not r['volume']
            missing_keys.add((r['symbol'],r['trade_date']))
            assert (r['symbol'],r['trade_date']) not in raw_values
        else:
            assert tuple(r[k] for k in ('open_price','high_price','low_price','close_price','volume','source')) == raw_values[r['symbol'],r['trade_date']]
        if r['price_usable'] == '1':
            o, h, l, c = [float(r[k]) for k in ('open_price','high_price','low_price','close_price')]
            assert 0 < l <= min(o,c) <= max(o,c) <= h
            assert float(r['volume']) > 0
            usable += 1
        daily[r['trade_date']] += 1
    assert len(seen) == expected == meta['member_rows']
    assert usable == meta['usable_rows']
    assert dict(counts) == meta['issues']
    assert sum(int(r['missing_member_sessions']) for r in rows(OUT / 'missing_symbols.csv')) == counts['MISSING_BAR']
    assert {r['trade_date']:int(r['members']) for r in rows(OUT / 'daily_coverage.csv')} == dict(daily)
    if (OUT / 'local_recovery_candidates.csv.gz').exists():
        recovered = set()
        for r in rows(OUT / 'local_recovery_candidates.csv.gz'):
            key = r['symbol'], r['trade_date']
            assert key in missing_keys and key not in recovered
            recovered.add(key)
        local_meta = json.loads((OUT / 'local_recovery_summary.json').read_text())
        assert len(recovered) == local_meta['local_raw_candidate_sessions']
        assert len(missing_keys - recovered) == local_meta['unresolved_sessions']
    result = dict(status='PASS', verified_member_rows=expected, verified_sessions=len(daily),
                  checks=['input hashes','independent interval row count','unique grain','membership endpoints',
                          'next session','missing-value preservation','quality flags','OHLC bounds',
                          'coverage reconciliation','missing-symbol reconciliation',
                          'exact raw-price preservation'])
    (OUT / 'verification.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
