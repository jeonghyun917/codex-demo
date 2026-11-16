"""Bounded robust-loss follow-up: reconstructed research only; no DB or promotion."""
import csv
import hashlib
import json
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import scipy
import sklearn
from scipy.stats import spearmanr
from sklearn.linear_model import HuberRegressor, QuantileRegressor

from quant_20d_error_research import V9, load_panel, training_rows
import quant_20d_error_research as original_research

OUT = Path('tmp/quant-20d-robust-research-v6-20261031')
RECOVERED_SNAPSHOT = Path('tmp/quant-20d-robust-research-v2-20261031/historical-v9-source.txt')
V9_SOURCE = 'src/main/java/com/kingyurina/demo/stock/StockExpectedReturnV9Service.java'
HISTORICAL_V9_SHA = 'ca0bfbb93248a4b71c82b4e6df3b8f06b9a67fd5fcdab09812712c32b6b79c63'
PRICE_NAMES = ['return5Pp', 'return20Pp', 'return60Pp', 'vol20AnnualizedPp', 'currentVs60HighPp']
FAMILIES = ['ZERO', 'PAST_MEDIAN', 'V9', 'V9_RESIDUAL_MEDIAN',
            'PRICE_QUANTILE', 'V9_PRICE_QUANTILE', 'PRICE_HUBER', 'V9_PRICE_HUBER']
PROTOCOL = dict(
    families=FAMILIES, minimumMatureTrainingDates=8,
    trainingRule='decision < test decision AND exit < test decision, both strict',
    featureSets=dict(PRICE=PRICE_NAMES, V9_PRICE=['targetSpecificV9Pp'] + PRICE_NAMES),
    scaling='training mean and population standard deviation; constant scale set to 1',
    quantile=dict(quantile=0.5, alpha=0.1, solver='highs', fit_intercept=True),
    huber=dict(epsilon=1.35, alpha=0.1, max_iter=2000, tol=1e-7, fit_intercept=True),
    residualMedian='V9 + median(actual - V9) from all and only mature training rows',
    fixedBeforeFirstFit=True, hyperparameterSearch=False,
    primaryMetric='pooled MAE percentage points, same 11 dates x 101 members per target',
    nonoverlapRule='greedy chronological, accept next decision strictly after previous maximum exit',
    semantics=dict(QUANTILE='regularized conditional median; NOT expected mean return',
                   HUBER='robust Huber location regression; NOT a proven conditional mean',
                   V9_RESIDUAL_MEDIAN='median residual shifted V9; no expected-mean guarantee'),
    classification='EXPLORATORY_FOLLOWUP_ON_ALREADY_EXPLORED_RECONSTRUCTIONS',
    promotionAllowed=False, databaseWrites=0)


def fit_robust(x, y, method):
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    if (x.ndim != 2 or not len(x) or y.shape != (len(x),)
            or not np.isfinite(x).all() or not np.isfinite(y).all()):
        raise ValueError('Nonempty finite rectangular paired data required')
    mu = x.mean(axis=0)
    scale = x.std(axis=0)
    scale[scale < 1e-12] = 1
    if method == 'quantile':
        estimator = QuantileRegressor(**PROTOCOL['quantile'])
    elif method == 'huber':
        estimator = HuberRegressor(**PROTOCOL['huber'])
    else:
        raise ValueError(method)
    estimator.fit((x - mu) / scale, y)
    result = dict(method=method, mean=mu.tolist(), scale=scale.tolist(),
                  weights=estimator.coef_.tolist(), intercept=float(estimator.intercept_))
    if method == 'huber':
        result.update(robustScale=float(estimator.scale_), iterations=int(estimator.n_iter_))
    return result


def predict_robust(model, x):
    return ((np.asarray(x, dtype=float) - model['mean']) / model['scale']
            @ np.asarray(model['weights']) + model['intercept'])


def predict_fold(rows, day):
    train = training_rows(rows, day)
    training_dates = sorted({r['day'] for r in train})
    if len(training_dates) < PROTOCOL['minimumMatureTrainingDates']:
        return None
    test = [r for r in rows if r['day'] == day]
    if not test:
        raise ValueError('Nonempty test date required')
    saved, fits = [], []
    for target in ('absolute', 'excess'):
        field = 'actual' if target == 'absolute' else 'excess'
        y = [r[field] for r in train]
        for family in FAMILIES:
            fit = None
            if family == 'ZERO':
                predicted = np.zeros(len(test))
            elif family == 'V9':
                predicted = [r['forecasts'][V9][target] for r in test]
            elif family in ('PAST_MEDIAN', 'V9_RESIDUAL_MEDIAN'):
                residual = family == 'V9_RESIDUAL_MEDIAN'
                correction = float(np.median([r[field] - (r['forecasts'][V9][target] if residual else 0)
                                               for r in train]))
                fit = dict(correctionPp=correction)
                predicted = [(r['forecasts'][V9][target] if residual else 0) + correction for r in test]
            else:
                with_v9 = family.startswith('V9_')
                def features(r):
                    return ([r['forecasts'][V9][target]] if with_v9 else []) + r['price']
                method = 'quantile' if family.endswith('QUANTILE') else 'huber'
                fit = fit_robust([features(r) for r in train], y, method)
                fit['featureNames'] = (['targetSpecificV9Pp'] if with_v9 else []) + PRICE_NAMES
                predicted = predict_robust(fit, [features(r) for r in test])
            if fit is not None:
                fits.append(dict(day=day, target=target, family=family, trainingDates=training_dates,
                                 trainingRows=len(train), maximumTrainingExit=max(r['exit'] for r in train), fit=fit))
            for row, value in zip(test, predicted):
                saved.append(dict(day=day, symbol=row['symbol'], entry=row['entry'], exit=row['exit'],
                                  target=target, family=family, predicted=float(value), actual=row[field]))
    return dict(predictions=saved, fits=fits)


def nonoverlap_dates(rows):
    exits = {}
    for row in rows:
        exits[row['day']] = max(exits.get(row['day'], ''), row['exit'])
    selected, last_exit = [], ''
    for day in sorted(exits):
        if day > last_exit:
            selected.append(day)
            last_exit = exits[day]
    return selected


def measures(rows):
    p = np.array([r['predicted'] for r in rows])
    a = np.array([r['actual'] for r in rows])
    e = p - a
    rank = float(spearmanr(p, a).statistic) if np.ptp(p) > 0 and np.ptp(a) > 0 else None
    return dict(maePp=float(np.mean(abs(e))), rmsePp=float(np.sqrt(np.mean(e * e))),
                biasPp=float(np.mean(e)), p90AbsErrorPp=float(np.quantile(abs(e), .9)),
                p95AbsErrorPp=float(np.quantile(abs(e), .95)),
                maxAbsErrorPp=float(np.max(abs(e))), spearman=rank)


def evaluate(rows):
    predictions, fits = [], []
    for day in sorted({r['day'] for r in rows}):
        fold = predict_fold(rows, day)
        if fold is not None:
            predictions.extend(fold['predictions'])
            fits.extend(fold['fits'])
    groups = defaultdict(list)
    for p in predictions:
        groups[p['target'], p['family'], p['day']].append(p)
    daily = [dict(target=t, family=f, day=d, rows=len(rs), **measures(rs))
             for (t, f, d), rs in groups.items()]
    base = {(r['target'], r['day']): r for r in daily if r['family'] == 'V9'}
    for r in daily:
        r['maeDeltaVsV9Pp'] = r['maePp'] - base[r['target'], r['day']]['maePp']
    selected = nonoverlap_dates(predictions)
    summaries = []
    for subset, days in [('allCommonDates', sorted({p['day'] for p in predictions})),
                         ('greedyNonoverlapDescriptive', selected)]:
        for target in ('absolute', 'excess'):
            baseline = [p for p in predictions if p['target'] == target and p['family'] == 'V9' and p['day'] in days]
            baseline_mae = measures(baseline)['maePp']
            for family in FAMILIES:
                ps = [p for p in predictions if p['target'] == target and p['family'] == family and p['day'] in days]
                ds = [r for r in daily if r['target'] == target and r['family'] == family and r['day'] in days]
                values = measures(ps)
                ranks = [r['spearman'] for r in ds if r['spearman'] is not None]
                summaries.append(dict(subset=subset, target=target, family=family, dates=len(days), rows=len(ps),
                                      **values, maeDeltaVsV9Pp=values['maePp'] - baseline_mae,
                                      betterDates=sum(r['maeDeltaVsV9Pp'] < 0 for r in ds),
                                      meanDailySpearman=float(np.mean(ranks)) if ranks else None))
    return dict(predictions=predictions, fits=fits, daily=daily, summary=summaries,
                nonoverlapDates=selected)


def write_json(path, value):
    with path.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2, allow_nan=False)


def recover_historical_v9(current):
    """Undo only concurrent forward archive additions, then require the old exact hash."""
    if hashlib.sha256(current).hexdigest() == HISTORICAL_V9_SHA:
        return current
    source = current.decode('utf-8').replace('\r\n', '\n')
    source = source.replace('import org.springframework.beans.factory.annotation.Autowired;\n', '')
    source = source.replace('    private StockExpectedReturnForwardArchive forwardArchive;\n', '')
    start = source.index('    @Autowired\n')
    end = source.index('    public StockPortfolioBacktestService.ExpectedReturnRefreshResult refreshHistorical', start)
    source = source[:start] + source[end:]
    source = source.replace('        List<String> savedSymbols = new ArrayList<>();\n'
                            '        List<StockMarketSnapshot> usedMarkets = new ArrayList<>();\n', '')
    start = source.index('            savedSymbols.add(adjusted.getSymbol());')
    end = source.index('        return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(savedRows > 0 ? 1 : 0,', start)
    source = source[:start] + '        }\n' + source[end:]
    recovered = source.encode('utf-8')
    if hashlib.sha256(recovered).hexdigest() != HISTORICAL_V9_SHA:
        raise ValueError('Historical V9 source recovery did not match the authoritative saved SHA256')
    return recovered


def transitive_hashes(direct_hashes, snapshot):
    hashes = dict(direct_hashes)
    for path in direct_hashes:
        if str(path).replace('\\', '/').endswith('/result.json'):
            report = json.loads(Path(path).read_text(encoding='utf-8'))
            for name, expected in report.get('inputHashes', {}).items():
                if name in hashes and hashes[name] != expected:
                    raise ValueError('Conflicting historical input hashes: ' + name)
                hashes[name] = expected
    if hashes.pop(V9_SOURCE) != HISTORICAL_V9_SHA:
        raise ValueError('Unexpected historical V9 source hash')
    hashes[str(snapshot)] = HISTORICAL_V9_SHA
    return hashes


def main():
    OUT.mkdir(exist_ok=False)
    declared = dict(PROTOCOL, declaredAtUtc=datetime.now(timezone.utc).isoformat(),
                    versions=dict(numpy=np.__version__, scipy=scipy.__version__, sklearn=sklearn.__version__))
    write_json(OUT / 'protocol.json', declared)  # Written before reading outcomes or fitting.
    current_source = Path(V9_SOURCE).read_bytes()
    recovered = recover_historical_v9(RECOVERED_SNAPSHOT.read_bytes())
    snapshot = OUT / 'historical-v9-source.txt'
    with snapshot.open('xb') as stream:
        stream.write(recovered)
    source_provenance = dict(originalPath=V9_SOURCE, snapshotPath=str(snapshot),
                             expectedHistoricalSha256=HISTORICAL_V9_SHA,
                             recoveredHistoricalSha256=hashlib.sha256(recovered).hexdigest(),
                             currentSourceSha256AtRun=hashlib.sha256(current_source).hexdigest(),
                             reconstruction='Removed only concurrent forwardArchive additions; exact historical hash required',
                             recoveredFromPreviouslyVerifiedSnapshot=str(RECOVERED_SNAPSHOT),
                             currentSourceWasUnchanged=False)
    original_path = original_research.Path
    def historical_path(path):
        return snapshot if str(path).replace('\\', '/') == V9_SOURCE else original_path(path)
    try:
        original_research.Path = historical_path
        rows, _, hashes = load_panel()  # All original hash checks still run.
    finally:
        original_research.Path = original_path
    hashes = transitive_hashes(hashes, snapshot)
    for path in ['scripts/quant_20d_error_research.py', __file__,
                 'src/test/python/test_quant_20d_robust_research.py',
                 'docs/quant-20d-error-results-2026-10-31.md']:
        hashes[str(path)] = hashlib.sha256(Path(path).read_bytes()).hexdigest()
    for path, expected in hashes.items():
        assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected, path
    write_json(OUT / 'input-panel.json', rows)
    hashes[str(OUT / 'input-panel.json')] = hashlib.sha256((OUT / 'input-panel.json').read_bytes()).hexdigest()
    result = evaluate(rows)
    dates = sorted({p['day'] for p in result['predictions']})
    assert len(dates) == 11
    assert all(len({p['symbol'] for p in result['predictions'] if p['day'] == day}) == 101 for day in dates)
    result.update(inputHashes=hashes, historicalSourceProvenance=source_provenance,
                  protocol=declared, databaseWrites=0, promotionAllowed=False,
                  limitations=[
                      'Already-explored reconstructed periods, not an untouched holdout or LIVE predictions.',
                      '101 securities and overlapping 20D returns are dependent; no significance claim.',
                      'Nonoverlap subset is a descriptive greedy subset, not an independent confirmatory test.',
                      'Official PIT membership, receipt timing, corporate actions and price bases remain imperfect.',
                      'Conditional median and Huber location do not establish expected-mean return semantics.',
                      'No predicted probability, interval, trading cost or strategy benefit is validated.'])
    write_json(OUT / 'result.json', result)
    with (OUT / 'summary.csv').open('x', newline='', encoding='utf-8-sig') as stream:
        writer = csv.DictWriter(stream, fieldnames=result['summary'][0])
        writer.writeheader()
        writer.writerows(result['summary'])
    print(json.dumps([r for r in result['summary'] if r['subset'] == 'allCommonDates'], indent=2))


if __name__ == '__main__':
    main()
