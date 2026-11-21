"""Read-only, reconstructed-signal barrier research; never places orders or promotes models."""
import bisect
import csv
import hashlib
import io
import json
import random
import statistics
import subprocess
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output' / 'barrier-research-20261018'
CLIENT = ROOT / '.tools/mariadb-11.8.2-winx64/bin/mariadb.exe'
CUTOFF = '2026-10-16'


def query(sql):
    result = subprocess.run([str(CLIENT), '--host=127.0.0.1', '--user=root',
        '--database=king_yurina', '--batch', '--default-character-set=utf8mb4',
        '-e', 'SET SESSION TRANSACTION READ ONLY; START TRANSACTION WITH CONSISTENT SNAPSHOT; '
        + sql + '; COMMIT;'], capture_output=True, text=True, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(result.stderr)
    return list(csv.DictReader(io.StringIO(result.stdout), delimiter='\t'))


def simulate(bars, horizon, cost=.2):
    if len(bars) < horizon:
        return None
    bars = bars[:horizon]
    if len({b['source'] for b in bars}) != 1:
        return None
    if any(not (0 < b['low'] <= min(b['open'], b['close']) <=
                max(b['open'], b['close']) <= b['high']) for b in bars):
        return None
    entry = bars[0]['open']
    stop, target = entry * .95, entry * 1.15
    for i, b in enumerate(bars):
        ambiguous = b['low'] <= stop and b['high'] >= target
        if b['open'] <= stop:
            price, reason = b['open'], 'SL_GAP'
        elif b['open'] >= target:
            price, reason = target, 'TP'  # No favorable gap-price improvement assumed.
        elif b['low'] <= stop:
            price, reason = stop, 'SL'
        elif b['high'] >= target:
            price, reason = target, 'TP'
        elif i == horizon - 1:
            price, reason = b['close'], 'TIME'
        else:
            continue
        gross = (price / entry - 1) * 100
        return dict(entry=bars[0]['date'], exit=b['date'], entry_price=entry,
                    exit_price=price, gross=gross, net=gross-cost, days=i+1,
                    reason=reason, ambiguous=ambiguous)


RULES = [('ALL', {})]
RULES += [(f'SIGNAL_{s}', {'signal': s}) for s in (60, 70, 80)]
RULES += [(f'ER20_{e}', {'er': e}) for e in (0, 2, 4)]
RULES += [(f'PROB_{p}', {'prob': p}) for p in (55, 60, 70)]
RULES += [(f'S60_ER{e}_P{p}', {'signal': 60, 'er': e, 'prob': p})
          for e in (0, 2) for p in (55, 60)]
RULES += [('S70_ER2', {'signal': 70, 'er': 2}),
          ('S60_ER2_M60', {'signal': 60, 'er': 2, 'momentum': 60}),
          ('S60_ER2_Q70', {'signal': 60, 'er': 2, 'quality': 70}),
          ('ER2_R60', {'er': 2, 'risk': 60})]


def select(rows, thresholds, period):
    last_exit, selected = {}, []
    for row in sorted(rows, key=lambda r: (r['entry'], r['symbol'])):
        if period == 'train' and row['exit'] >= '2025-01-01':
            continue
        if period == 'test' and row['date'] < '2025-01-01':
            continue
        if any(row[k] is None or row[k] < v for k, v in thresholds.items()):
            continue
        if row['entry'] <= last_exit.get(row['symbol'], ''):
            continue
        selected.append(row)
        last_exit[row['symbol']] = row['exit']
    return selected


def metrics(rows):
    if not rows:
        return {'n': 0}
    net = [r['net'] for r in rows]
    months = defaultdict(list)
    for r in rows:
        months[r['date'][:7]].append(r['net'])
    gain, loss = sum(max(0, n) for n in net), -sum(min(0, n) for n in net)
    return dict(n=len(rows), symbols=len({r['symbol'] for r in rows}), months=len(months),
                mean_net=statistics.mean(net), median_net=statistics.median(net),
                win_pct=100*sum(n > 0 for n in net)/len(net),
                tp_pct=100*sum(r['reason'] == 'TP' for r in rows)/len(rows),
                stop_pct=100*sum(r['reason'].startswith('SL') for r in rows)/len(rows),
                timeout_pct=100*sum(r['reason'] == 'TIME' for r in rows)/len(rows),
                mean_days=statistics.mean(r['days'] for r in rows),
                net_per_held_day=sum(net)/sum(r['days'] for r in rows),
                profit_factor=gain/loss if loss else None,
                worst_trade=min(net), ambiguous=sum(r['ambiguous'] for r in rows),
                equal_month_mean=statistics.mean(statistics.mean(v) for v in months.values()),
                monthly={k: dict(n=len(v), mean_net=statistics.mean(v)) for k,v in sorted(months.items())})


def cluster_interval(rows):
    groups = defaultdict(list)
    for r in rows:
        groups[r['date'][:7]].append(r['net'])
    values = list(groups.values())
    if len(values) < 6:
        return None
    rng = random.Random(731)
    draws = []
    for _ in range(1000):
        sample = [rng.choice(values) for _ in values]
        draws.append(sum(map(sum, sample))/sum(map(len, sample)))
    draws.sort()
    return [draws[24], draws[974]]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sql = """SELECT m.index_code,m.snapshot_date date,m.symbol,s.integrated_score `signal`,
      s.momentum_score momentum,s.quality_score quality,s.risk_score risk,
      s.calculated_at signal_calculated_at,p.expected_return_pct er,
      p.calibrated_upside_probability_pct prob,p.calculated_at prediction_calculated_at,
      p.model_version FROM stock_index_membership_snapshot m
      LEFT JOIN stock_signal_snapshot s ON s.symbol=m.symbol AND s.signal_date=m.snapshot_date
        AND s.snapshot_mode='HISTORICAL_RECONSTRUCTED'
      LEFT JOIN stock_expected_return_snapshot p ON p.index_code=m.index_code
        AND p.signal_date=m.snapshot_date AND p.symbol=m.symbol AND p.horizon_days=20
        AND p.model_version=CASE WHEN m.index_code='SP500_FREE_20261018'
          THEN 'EXPECTED_RETURN_V3_CORRECTED_20261017' ELSE 'EXPECTED_RETURN_V9_CORRECTED_20261017' END
      WHERE m.index_code IN ('SP500_FREE_20261018','NASDAQ100_FREE_20261018') AND m.is_member=1
        AND m.snapshot_date BETWEEN '2023-08-31' AND '2026-03-27'
        AND m.snapshot_date IN (SELECT MAX(signal_date) FROM stock_signal_snapshot
          WHERE snapshot_mode='HISTORICAL_RECONSTRUCTED' GROUP BY YEAR(signal_date),MONTH(signal_date))
      ORDER BY m.index_code,m.snapshot_date,m.symbol"""
    signals = query(sql)
    prices = query("SELECT symbol,trade_date date,open_price open,high_price high,low_price low,"
                   "close_price close,source FROM stock_candle_daily WHERE trade_date BETWEEN "
                   "'2023-08-31' AND '2026-10-16' ORDER BY symbol,trade_date")
    for name, rows in [('signals', signals), ('prices', prices)]:
        with (OUT / (name+'.csv')).open('w', newline='', encoding='utf-8') as f:
            w = csv.DictWriter(f, fieldnames=rows[0].keys()); w.writeheader(); w.writerows(rows)
    (OUT/'query.sql').write_text(sql, encoding='utf-8')
    candles = defaultdict(list)
    for r in prices:
        candles[r['symbol']].append({**r, **{k: float(r[k]) for k in ('open','high','low','close')}})
    dates = {k: [b['date'] for b in v] for k,v in candles.items()}
    report = {'as_of': CUTOFF, 'status': 'RESEARCH_ONLY_NOT_LIVE_VALIDATION',
              'cost_pct_roundtrip': .2, 'rules': dict(RULES), 'results': []}
    all_trades = []
    for index in sorted({r['index_code'] for r in signals}):
        population = [r for r in signals if r['index_code'] == index]
        seen = {(r['date'],r['symbol']) for r in population}
        assert len(seen) == len(population), 'Duplicate membership/signal/prediction join'
        fixed_winner = None
        for horizon in (20, 60, 120):
            excluded, trades = Counter(), []
            for r in population:
                if r['signal'] == 'NULL' or r['er'] == 'NULL':
                    excluded['missing_signal_or_prediction'] += 1; continue
                bars = candles.get(r['symbol'], [])
                pos = bisect.bisect_right(dates.get(r['symbol'], []), r['date'])
                future = bars[pos:pos+horizon]
                if future and (date.fromisoformat(future[0]['date'])-
                               date.fromisoformat(r['date'])).days > 7:
                    excluded['entry_gap'] += 1; continue
                if any((date.fromisoformat(b['date'])-
                        date.fromisoformat(a['date'])).days > 7
                       for a,b in zip(future, future[1:])):
                    excluded['history_gap'] += 1; continue
                result = simulate(future, horizon)
                if result is None:
                    excluded['incomplete_or_invalid_price_window'] += 1; continue
                trades.append({**r, **{k: None if r[k]=='NULL' else float(r[k])
                    for k in ('signal','er','prob','momentum','quality','risk')}, **result})
            table = []
            for name, thresholds in RULES:
                train, test = select(trades, thresholds, 'train'), select(trades, thresholds, 'test')
                table.append(dict(rule=name, train=metrics(train), test=metrics(test)))
            if horizon == 20:
                eligible = [r for r in table if r['train']['n'] >= 80 and r['train']['months'] >= 8]
                fixed_winner = max(eligible, key=lambda r:r['train']['net_per_held_day'])['rule'] if eligible else 'ALL'
            winner_rows = select(trades, dict(RULES)[fixed_winner], 'test')
            report['results'].append(dict(index=index, horizon=horizon, population=len(population),
                eligible=len(trades), excluded=dict(excluded), selected_rule=fixed_winner,
                selection='20D train net return per held day; >=80 trades and >=8 months',
                selected_test_ci=cluster_interval(winner_rows),
                selected_by_year={str(y): metrics([r for r in winner_rows if r['date'].startswith(str(y))])
                                  for y in (2025,2026)}, table=table))
            for r in winner_rows:
                all_trades.append({**r,'horizon':horizon,'rule':fixed_winner})
            print(index, horizon, 'population', len(population), 'eligible',len(trades),
                  'selected',fixed_winner, 'test',json.dumps(metrics(winner_rows)), flush=True)
    report['input_sha256'] = {p.name:hashlib.sha256(p.read_bytes()).hexdigest()
                              for p in OUT.glob('*.csv')}
    (OUT/'results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    with (OUT/'selected-test-trades.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=all_trades[0].keys()); w.writeheader(); w.writerows(all_trades)


if __name__ == '__main__':
    main()
