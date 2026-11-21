"""Offline walk-forward barrier probability experiment. Never writes to DB or trades."""
import bisect
import csv
import hashlib
import json
import math
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

import numpy as np
import sklearn
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import brier_score_loss, roc_auc_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from barrier_research import simulate, metrics, select

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'output/barrier-research-20261018'
OUT = ROOT / 'output/barrier-probability-20261018'
FEATURES = ['signal', 'momentum', 'quality', 'risk', 'er', 'prob',
            'return20', 'vol20', 'atr20', 'from_high20', 'max_gap20']


def target_label(trade):
    return int(trade['reason'] == 'TP')


def valid_window(bars):
    return (len({b['source'] for b in bars}) == 1 and
            all(0 < b['low'] <= min(b['open'], b['close']) <=
                max(b['open'], b['close']) <= b['high'] for b in bars) and
            all(0 < (date.fromisoformat(b['date'])-date.fromisoformat(a['date'])).days <= 7
                for a,b in zip(bars, bars[1:])))


def causal_features(bars, decision):
    pos = bisect.bisect_right([b['date'] for b in bars], decision)
    prior = bars[max(0, pos-21):pos]
    if len(prior) != 21 or not valid_window(prior):
        return None
    if (date.fromisoformat(decision)-date.fromisoformat(prior[-1]['date'])).days > 7:
        return None
    returns = [b['close']/a['close']-1 for a,b in zip(prior,prior[1:])]
    tr = [max(b['high']-b['low'], abs(b['high']-a['close']), abs(b['low']-a['close']))
          for a,b in zip(prior,prior[1:])]
    return dict(return20=100*(prior[-1]['close']/prior[0]['close']-1),
                vol20=100*float(np.std(returns, ddof=1)),
                atr20=100*float(np.mean(tr))/prior[-1]['close'],
                from_high20=100*(prior[-1]['close']/max(b['high'] for b in prior[1:])-1),
                max_gap20=100*max(abs(b['open']/a['close']-1) for a,b in zip(prior,prior[1:])))


def split_history(rows, decision, calibration_dates=3):
    mature = [r for r in rows if r['date'] < decision and r['maturity'] < decision]
    dates = sorted({r['date'] for r in mature})
    if len(dates) <= calibration_dates:
        return [], []
    start = dates[-calibration_dates]
    fit = [r for r in mature if r['date'] < start and r['maturity'] < start]
    calibration = [r for r in mature if r['date'] >= start]
    return fit, calibration


def ranked_entries(rows, field, fraction=.2):
    grouped, selected, exits = defaultdict(list), [], {}
    for r in rows:
        grouped[r['date']].append(r)
    for decision, candidates in sorted(grouped.items()):
        quota = math.ceil(fraction*len(candidates))
        picked = 0
        for row in sorted(candidates, key=lambda r: (-r[field],r['symbol'])):
            if row['entry'] <= exits.get(row['symbol'], ''):
                continue
            selected.append(row)
            exits[row['symbol']] = row['exit']
            picked += 1
            if picked == quota:
                break
    return selected


def matrix(rows):
    return np.asarray([[np.nan if r.get(k) is None else r[k] for k in FEATURES] for r in rows])


def fit_predict(fit, calibration, current):
    pipeline = make_pipeline(SimpleImputer(strategy='median', keep_empty_features=True),
                             StandardScaler(), LogisticRegression(C=.1, max_iter=2000))
    pipeline.fit(matrix(fit), [r['label'] for r in fit])
    calibrator = LogisticRegression(C=1., max_iter=2000)
    calibrator.fit(pipeline.decision_function(matrix(calibration)).reshape(-1,1),
                   [r['label'] for r in calibration])
    probabilities = calibrator.predict_proba(pipeline.decision_function(matrix(current)).reshape(-1,1))[:,1]
    imputer, scaler, model = pipeline.steps[0][1], pipeline.steps[1][1], pipeline.steps[2][1]
    parameters = dict(impute=imputer.statistics_.tolist(), mean=scaler.mean_.tolist(),
                      scale=scaler.scale_.tolist(), coefficient=model.coef_[0].tolist(),
                      intercept=float(model.intercept_[0]), calibration_slope=float(calibrator.coef_[0,0]),
                      calibration_intercept=float(calibrator.intercept_[0]))
    return probabilities, parameters


def paired_difference(a, b):
    def monthly(rows):
        grouped = defaultdict(list)
        for r in rows:
            grouped[r['date'][:7]].append(r['net'])
        return {k:float(np.mean(v)) for k,v in grouped.items()}
    ma, mb = monthly(a), monthly(b)
    months = sorted(ma.keys() & mb.keys())
    differences = np.asarray([ma[m]-mb[m] for m in months])
    rng = np.random.default_rng(731)
    sampled = rng.choice(differences, size=(2000,len(differences)), replace=True).mean(axis=1)
    return dict(months=len(months), equal_month_net_difference=float(differences.mean()),
                ci95=np.quantile(sampled,[.025,.975]).tolist(),
                positive_months=int((differences > 0).sum()))


def probability_metrics(rows):
    y = np.array([r['label'] for r in rows])
    p = np.array([r['barrier_probability'] for r in rows])
    base = np.array([r['historical_rate'] for r in rows])
    bins = []
    for lo, hi in zip([0,.05,.1,.2,.3,.5], [.05,.1,.2,.3,.5,1.000001]):
        mask = (p >= lo) & (p < hi)
        if mask.any():
            bins.append(dict(lower=lo, upper=min(hi,1), n=int(mask.sum()),
                             predicted=float(p[mask].mean()), observed=float(y[mask].mean())))
    return dict(n=len(rows), actual_tp_rate=float(y.mean()), predicted_tp_rate=float(p.mean()),
                brier=float(brier_score_loss(y,p)), historical_rate_brier=float(brier_score_loss(y,base)),
                auc=float(roc_auc_score(y,p)), bins=bins)


def load_data():
    prior = json.loads((SOURCE/'results.json').read_text(encoding='utf-8'))
    for name in ('signals.csv','prices.csv'):
        assert hashlib.sha256((SOURCE/name).read_bytes()).hexdigest() == prior['input_sha256'][name]
    with (SOURCE/'signals.csv').open(encoding='utf-8',newline='') as f:
        signals = list(csv.DictReader(f))
    candles = defaultdict(list)
    with (SOURCE/'prices.csv').open(encoding='utf-8',newline='') as f:
        for r in csv.DictReader(f):
            candles[r['symbol']].append({**r, **{k:float(r[k]) for k in ['open','high','low','close']}})
    dates = {s:[b['date'] for b in bars] for s,bars in candles.items()}
    rows, excluded = [], defaultdict(Counter)
    keys = set()
    for r in signals:
        index = r['index_code']
        key = (index,r['date'],r['symbol'])
        assert key not in keys, 'Duplicate observation'
        keys.add(key)
        if any(r[k]=='NULL' for k in ['signal','er','prob']):
            excluded[index]['missing_signal_or_prediction'] += 1; continue
        bars = candles.get(r['symbol'],[])
        features = causal_features(bars,r['date'])
        if features is None:
            excluded[index]['missing_causal_21bar_history'] += 1; continue
        pos = bisect.bisect_right(dates[r['symbol']],r['date'])
        future = bars[pos:pos+20]
        if len(future)!=20 or not valid_window(future) or (date.fromisoformat(future[0]['date'])-
                date.fromisoformat(r['date'])).days > 7:
            excluded[index]['incomplete_or_invalid_future_window'] += 1; continue
        trade = simulate(future,20)
        assert trade is not None
        rows.append({**r, **{k:None if r[k]=='NULL' else float(r[k]) for k in FEATURES[:6]},
                     **features, **trade, 'maturity':future[-1]['date'], 'label':target_label(trade)})
    return rows, {i:dict(v) for i,v in excluded.items()}, Counter(r['index_code'] for r in signals)


def write_csv(path, rows):
    with path.open('w',encoding='utf-8',newline='') as f:
        writer = csv.DictWriter(f,fieldnames=rows[0].keys())
        writer.writeheader(); writer.writerows(rows)


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    rows, excluded, population = load_data()
    report = dict(status='RESEARCH_ONLY_PREVIOUSLY_INSPECTED_PERIOD', sklearn=sklearn.__version__,
                  features=FEATURES, excluded=excluded, results=[])
    all_predictions, all_trades, models = [], [], []
    for index in sorted(population):
        universe = [r for r in rows if r['index_code']==index]
        predictions, skipped = [], []
        for decision in sorted({r['date'] for r in universe if r['date'] >= '2025-01-01'}):
            fit, calibration = split_history(universe,decision)
            counts = Counter(r['label'] for r in calibration)
            if len(fit)<400 or len({r['date'] for r in fit})<8 or min(counts.get(0,0),counts.get(1,0))<15:
                skipped.append(dict(date=decision,reason='insufficient_fit_or_calibration',
                                    fit_rows=len(fit),calibration_counts=dict(counts))); continue
            assert max(r['maturity'] for r in fit) < min(r['date'] for r in calibration)
            assert max(r['maturity'] for r in calibration) < decision
            current = [r for r in universe if r['date']==decision]
            probabilities, parameters = fit_predict(fit,calibration,current)
            historical_rate = float(np.mean([r['label'] for r in fit+calibration]))
            for row,p in zip(current,probabilities):
                predictions.append({**row,'barrier_probability':float(p),'historical_rate':historical_rate})
            models.append(dict(index=index,decision=decision,fit_rows=len(fit),
                               fit_dates=len({r['date'] for r in fit}),
                               fit_max_maturity=max(r['maturity'] for r in fit),
                               calibration_start=min(r['date'] for r in calibration),
                               calibration_rows=len(calibration),
                               calibration_max_maturity=max(r['maturity'] for r in calibration),
                               parameters=parameters))
        if not predictions:
            report['results'].append(dict(index=index,status='INSUFFICIENT',skipped=skipped)); continue
        strategies = {name:ranked_entries(predictions,field) for name,field in
                      [('BARRIER_TOP20','barrier_probability'),('ER20_TOP20','er'),
                       ('SIGNAL_TOP20','signal'),('UP20_TOP20','prob')]}
        strategies['ALL_REFERENCE'] = select(predictions,{},'test')
        strategies['SIGNAL60_REFERENCE'] = select(predictions,{'signal':60},'test')
        result = dict(index=index,population=population[index],eligible=len(universe),
                      prediction_rows=len(predictions),decision_dates=len({r['date'] for r in predictions}),
                      skipped=skipped,probability_quality=probability_metrics(predictions),strategies={},
                      comparison={name:paired_difference(strategies['BARRIER_TOP20'],strategies[name])
                                  for name in ('ER20_TOP20','SIGNAL_TOP20','UP20_TOP20')})
        for name,trades in strategies.items():
            m = metrics(trades)
            m['mean_predicted_tp_pct'] = 100*float(np.mean([r['barrier_probability'] for r in trades]))
            m['by_year'] = {str(y):metrics([r for r in trades if r['date'].startswith(str(y))])
                            for y in (2025,2026)}
            result['strategies'][name] = m
            all_trades.extend({**r,'strategy':name} for r in trades)
            print(index,name,'n',m['n'],'net',round(m['mean_net'],4),
                  'TP',round(m['tp_pct'],2),'PF',round(m['profit_factor'],3),flush=True)
        report['results'].append(result)
        all_predictions.extend(predictions)
    report['hashes'] = {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()
                       for p in [SOURCE/'signals.csv',SOURCE/'prices.csv',OUT/'PROTOCOL.txt',Path(__file__)]}
    (OUT/'results.json').write_text(json.dumps(report,indent=2,allow_nan=False),encoding='utf-8')
    (OUT/'walk-forward-models.json').write_text(json.dumps(models,indent=2,allow_nan=False),encoding='utf-8')
    write_csv(OUT/'predictions.csv',all_predictions)
    write_csv(OUT/'strategy-trades.csv',all_trades)


if __name__ == '__main__':
    main()
