"""Independent stdlib validation of corrected-input paired replay outputs."""
import argparse
import csv
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path
from statistics import mean

REPORTS = [f'tmp/{name}/result.json' for name in (
    'quant-older-model-recovery-v2-final-20261030',
    'quant-extended-model-recovery-final-20261018',
    'quant-team-model-recovery-final-20261018',
    'quant-v9-historical-market-final-20261031',
    'quant-march-model-v3-final-20261031')]
HOLDINGS = ['tmp/quant-older-staged-20261030/holdings.csv',
            'tmp/quant-complete-windows-recovered-20261018/complete-holdings.csv']
V9 = 'EXPECTED_RETURN_V9_CORRECTED_20261017'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def paired_metrics(rows):
    if not rows or len({r['symbol'] for r in rows}) != len(rows):
        raise ValueError('Unique nonempty symbol cohort required')
    if not all(math.isfinite(float(r[k])) for r in rows for k in
               ('actual', 'benchmark', 'oldAbsolute', 'newAbsolute', 'oldExcess', 'newExcess')):
        raise ValueError('Finite paired values required')
    result = {}
    for target in ('absolute', 'excess'):
        actual = lambda r: r['actual'] - (r['benchmark'] if target == 'excess' else 0)
        old = mean(abs(r['old' + target.title()] - actual(r)) for r in rows)
        new = mean(abs(r['new' + target.title()] - actual(r)) for r in rows)
        result[target] = dict(oldMaePp=old, newMaePp=new, maeDeltaPp=new-old)
    return result


def prepare(path):
    """Freeze existing local archive inputs only. No database or network access."""
    from quant_complete_windows import parse_chart, merge_prices
    hashes = {}
    def read(p):
        hashes[p] = sha(p)
        return Path(p).read_bytes()
    forecasts = {}; dates = {}; old_signals = defaultdict(dict); market = defaultdict(dict)
    target_caps = defaultdict(list)
    recorded_sources = {}
    for p in REPORTS:
        r = json.loads(read(p))
        assert r['databaseWrites'] == 0 and r['promotionAllowed'] is False
        recorded_sources[p] = r['inputHashes']
        good = {(d['decision'], m['model']) for d in r['dates'] for m in d['models']
                if m['fullPopulation'] and m['predictions'] == 101}
        for prediction in r['predictions']:
            day = prediction['signalDate']; model = prediction['modelVersion']
            if prediction['horizonDays'] != 20 or (day, model) not in good:
                continue
            key = day + '/' + prediction['symbol'] + '/' + model
            assert key not in forecasts
            forecasts[key] = prediction
        for d in r['dates']:
            day = d['decision']; pop = sorted(d['fullPopulation'])
            assert len(pop) == len(set(pop)) == 101
            assert day not in dates or dates[day] == pop
            dates[day] = pop
            for recovered in d.get('recoveredInputs', []):
                signal = recovered['signal']; old_signals[day][signal['symbol']] = signal
                if 'market' in recovered:
                    market[day][recovered['symbol']] = recovered['market']
                    target_caps[day].append(recovered['symbol'])
            if 'teamSignal' in d:
                old_signals[day]['TEAM'] = d['teamSignal']
                market[day]['TEAM'] = d['teamMarket']
                target_caps[day].append('TEAM')
        for m in r.get('marketInputsUsed', []):
            market[m['snapshotDate']][m['symbol']] = m
    # March uses 100 original native rows plus TEAM's frozen evidence.
    p = 'tmp/quant-march-inputs-20261031/inputs-v3.json'; march = json.loads(read(p))
    market['2026-03-13']['TEAM'] = dict(march['teamMarket'], symbol='TEAM',
        snapshotDate='2026-03-13', currency='USD')
    target_caps['2026-03-13'].append('TEAM')
    holdings = defaultdict(dict)
    for p in HOLDINGS:
        for h in csv.DictReader(read(p).decode('utf-8-sig').splitlines()):
            if h['index'] != 'NASDAQ100_MACRO_FREE_20261018' or h['decision'] not in dates:
                continue
            assert h['symbol'] not in holdings[h['decision']]
            holdings[h['decision']][h['symbol']] = h
    canonical = defaultdict(dict)
    p = 'tmp/quant-complete-window-canonical-20261018.tsv'
    for r in csv.DictReader(read(p).decode('utf-8-sig').splitlines(), delimiter='\t'):
        canonical[r['symbol']][r['trade_date']] = float(r['close_price'])
    prices = {}
    for symbol in sorted({s for pop in dates.values() for s in pop}):
        if symbol == 'ANSS':
            p = 'tmp/quant-anss-free-evidence-20261018/chartexchange-anss.csv'
            series = {r['date']: float(r['close']) for r in csv.DictReader(read(p).decode().splitlines())}
        else:
            native = canonical[symbol]
            required = [(d, holdings[d][symbol]) for d in dates if symbol in dates[d]]
            adequate = all(d in native and sum(x <= d for x in native) >= 80 and
                           h['entry'] in native and h['exit'] in native for d, h in required)
            if adequate:
                series = native
            else:
                p = f'tmp/pit-acquisition/actions/raw/{symbol}.json'
                incoming = parse_chart(read(p), symbol)
                series = merge_prices(native, incoming) if native else incoming
        assert all(math.isfinite(v) and v > 0 for v in series.values())
        prices[symbol] = [{'day': d, 'close': v} for d, v in sorted(series.items())]
    assert len(dates) == 20 and len(forecasts) == 16160
    for day, pop in dates.items():
        assert set(holdings[day]) == set(pop)
        assert all(day + '/' + s + '/' + V9 in forecasts for s in pop)
    result = dict(dates=dates, holdings=holdings, prices=prices, frozenPredictions=forecasts,
                  oldRecoveredSignals=old_signals, marketOverrides=market,
                  oldRecoveredTargetCapSymbols=target_caps,
                  inputHashes=hashes, priorReportsRecordedSourceHashes=recorded_sources,
                  limitations=['Prior reports are immutable frozen controls; their recorded historical source hashes '
                               'are retained, not asserted to match current edited product sources.'])
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8') as f:
        json.dump(result, f, indent=2, allow_nan=False)
    print(json.dumps(dict(status='PREPARED', dates=len(dates), symbols=len(prices),
                          forecasts=len(forecasts), hashes=len(hashes))))


def verify(root):
    p = root / 'result.json'; r = json.loads(p.read_text(encoding='utf-8'))
    assert r['databaseWrites'] == 0 and r['promotionAllowed'] is False
    assert r['sourceDatabaseBefore'] == r['sourceDatabaseAfter']
    for path, digest in r['inputHashes'].items():
        assert sha(path) == digest, path
    all_pairs = []; cohorts = {}
    for date in r['dates']:
        day = date['decision']; pop = set(date['population'])
        assert len(pop) == 101 and date['rebuiltSignals'] == 101
        assert date['frozenTrainingContextHashOld'] == date['frozenTrainingContextHashNew']
        pairs = date['pairs']; assert len(pairs) == 101
        assert {p['symbol'] for p in pairs} == pop
        actual = paired_metrics(pairs)
        for target in actual:
            for field, value in actual[target].items():
                assert math.isclose(value, date['metrics'][target][field], abs_tol=1e-9)
        for row in pairs:
            assert day < row['entry'] < row['exit']
            assert len(row['observedHoldingSessions']) == 21
            assert row['observedHoldingSessions'] == row['expectedHoldingSessions']
            assert row['observedHoldingSessions'][0] == row['entry']
            assert row['observedHoldingSessions'][-1] == row['exit']
            assert row['entryClose'] > 0 and row['exitClose'] > 0
            assert math.isclose((row['exitClose']/row['entryClose']-1)*100, row['actual'], abs_tol=2e-5)
        cohorts[day] = pop; all_pairs.extend(pairs)
    assert len(cohorts) == len(r['dates'])
    result = dict(status='PASS', dates=len(cohorts), pairedRows=len(all_pairs),
                  symbolsPerDate=101, databaseWrites=0, promotionAllowed=False,
                  sourceHashesVerified=len(r['inputHashes']), resultSha256=sha(p))
    with (root/'verification.json').open('x', encoding='utf-8') as f:
        json.dump(result, f, indent=2)
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--prepare', type=Path)
    parser.add_argument('--verify', type=Path)
    args = parser.parse_args()
    if args.prepare:
        prepare(args.prepare)
    elif args.verify:
        verify(args.verify)
    else:
        parser.error('Specify --prepare FILE or --verify DIRECTORY')
