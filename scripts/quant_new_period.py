"""Sealed, file-only temporal comparison. No product/DB writes or promotion."""
import argparse
import hashlib
import json
import math
import re
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

FACTORS = ['valuation', 'quality', 'growth', 'stability', 'earnings',
           'analyst', 'news', 'momentum', 'risk', 'institution']
DATES = ['2026-04-30', '2026-05-29']
CUTOFF = DATES[0]
CONTEXT = Path('tmp/quant-restandardized-all20-20261031/frozen-context.json')
INPUT = Path('tmp/quant-restandardized-prepared-v2-20261031/inputs.json')
ROOT = Path('tmp/quant-new-period-20261031-v1')
CHANGES = [dict(effective='2026-04-20', remove='TEAM', add='SNDK',
                announcement='2026-04-10',
                url='https://ir.nasdaq.com/news-releases/news-release-details/sandisk-corporation-join-nasdaq-100-indexr-beginning-april-20'),
           dict(effective='2026-05-18', remove='CSGP', add='LITE',
                announcement='2026-05-08', url='https://ir.nasdaq.com/node/110416')]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save_new(path, value):
    with Path(path).open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2, allow_nan=False)


def mature_rows(rows, cutoff):
    return [r for r in rows if r.get('horizonDays') == 20
            and r.get('signalDate') and r['signalDate'] < cutoff
            and r.get('exitTradeDate') and r['exitTradeDate'] < cutoff]


def population(base, day):
    names = set(base)
    if len(names) != len(base):
        raise ValueError('Duplicate base population')
    for change in CHANGES:
        if change['effective'] <= day:
            if change['remove'] not in names or change['add'] in names:
                raise ValueError('Roster change incompatible with base')
            names.remove(change['remove'])
            names.add(change['add'])
    return sorted(names)


def validate_pairs(pairs, names):
    symbols = [r['symbol'] for r in pairs]
    if len(set(symbols)) != len(symbols) or set(symbols) != set(names):
        raise ValueError('Incomplete/duplicate target population')
    for r in pairs:
        for k in ['actual', 'benchmark', 'oldAbsolute', 'oldExcess', 'newAbsolute', 'newExcess']:
            if r.get(k) is None or not math.isfinite(float(r[k])):
                raise ValueError('Missing/nonfinite paired outcome or forecast')


def validate_training_pair(old, new):
    def keyed(rows):
        result = {}
        for r in rows:
            key = (r['signalDate'], r['symbol'], r['horizonDays'])
            if key in result:
                raise ValueError('Duplicate training key')
            result[key] = tuple(r.get(k) for k in ['entryTradeDate', 'exitTradeDate',
                                                 'forwardReturnPct', 'benchmarkReturnPct'])
        return result
    if keyed(old) != keyed(new):
        raise ValueError('Training labels/windows/denominators differ')


def error_metrics(predictions, actual):
    p, a = np.asarray(predictions, dtype=float), np.asarray(actual, dtype=float)
    if p.ndim != 1 or p.shape != a.shape or not len(p) or not np.isfinite(p).all() or not np.isfinite(a).all():
        raise ValueError('Nonempty equally sized finite prediction/outcome vectors required')
    e = p-a
    return dict(maePp=float(np.abs(e).mean()), rmsePp=float(np.sqrt(np.mean(e*e))),
                biasPp=float(e.mean()))


def saved_dates(report):
    dates = set()
    nodes = report.get('dates', [])
    if isinstance(nodes, dict):
        dates.update(nodes)
    elif isinstance(nodes, list):
        for node in nodes:
            if isinstance(node, str):
                dates.add(node)
            elif isinstance(node, dict) and node.get('decision'):
                dates.add(node['decision'])
    for name in ['predictions', 'controlPredictions', 'source', 'existing']:
        for row in report.get(name, []):
            if isinstance(row, dict) and row.get('signalDate'):
                dates.add(row['signalDate'])
    return dates


def artifact_dates(path):
    text = Path(path).read_text(encoding='utf-8-sig')
    try:
        report = json.loads(text)
    except json.JSONDecodeError:
        # Interrupted legacy runs can have truncated JSON. Keep their observed
        # decision tokens in the exclusion ledger; never silently discard them.
        dates = re.findall(r'"(?:signalDate|decision)"\s*:\s*"(\d{4}-\d{2}-\d{2})"', text)
        return sorted(set(dates)), 'INCOMPLETE_JSON_DATE_TOKENS_ONLY'
    return sorted(saved_dates(report)) if isinstance(report, dict) else [], 'PARSED_JSON'


def seal():
    if (ROOT/'protocol.json').exists():
        raise ValueError('Protocol already sealed; refuse overwrite/reselection')
    inventory = []
    paths = sorted(set(Path('tmp').glob('quant*/result.json')) |
                   set(Path('tmp').glob('quant*/report.json')) | {CONTEXT})
    # Only date metadata is inspected; never calculate/read out target returns here.
    for path in paths:
        dates, status = artifact_dates(path)
        if set(DATES) & set(dates):
            raise ValueError(f'Candidate already used in saved artifact: {path}')
        inventory.append(dict(path=str(path), sha256=sha(path), dates=dates, parseStatus=status))
    source = json.loads(CONTEXT.read_text(encoding='utf-8'))
    base = json.loads(INPUT.read_text(encoding='utf-8'))['dates']['2026-03-27']
    training = mature_rows(source['source'], CUTOFF)
    ROOT.mkdir(parents=True, exist_ok=True)
    protocol = dict(sealedAtUtc=datetime.now(timezone.utc).isoformat(), decisions=DATES,
                    fixedTrainingCutoff=CUTOFF,
                    trainingPredicate='signalDate < cutoff AND actual exitTradeDate < cutoff; horizon20',
                    trainingRows=len(training), trainingDates=sorted({r['signalDate'] for r in training}),
                    populations={d:population(base, d) for d in DATES}, changes=CHANGES,
                    candidates=['corrected_v9_baseline', 'repaired_prepanel_v9',
                                'training_pooled_median', 'fixed_ten_factor_ridge_alpha10'],
                    references=['zero_return'], ridgeAlpha=10,
                    primaryMetrics=['absolute20D_mae_pp', 'excess20D_mae_pp'],
                    promotionAllowed=False, parameterSearchAllowed=False,
                    foldRetrainingAllowed=False,
                    novelty='No candidate decision occurs in audited saved model/source artifacts; DB counts independently checked zero. Not a globally pristine market-history claim.',
                    limitations=['Two date groups only; overlapping20D holding windows are correlated.',
                                 'Reconstructed base roster plus official changes, not a certified full PIT roster.',
                                 'Preserved weighted price benchmark proxy; not official total return.',
                                 'Earlier frozen training has incomplete former-member and arrival-time evidence.'],
                    hashes={str(CONTEXT):sha(CONTEXT), str(INPUT):sha(INPUT),
                            'scripts/quant_new_period.py':sha('scripts/quant_new_period.py')},
                    usageInventory=inventory)
    save_new(ROOT/'protocol.json', protocol)
    print(json.dumps(dict(protocol=str(ROOT/'protocol.json'), sha256=sha(ROOT/'protocol.json'),
                          inventoryFiles=len(inventory), trainingRows=len(training),
                          populations={d:len(protocol['populations'][d]) for d in DATES})))


def evaluate(replay):
    protocol_path = ROOT/'amended-protocol.json'
    protocol = json.loads(protocol_path.read_text())
    for path, expected in protocol['hashes'].items():
        if sha(path) != expected:
            raise ValueError(f'Sealed input/code changed: {path}')
    report = json.loads(Path(replay).read_text())
    if report.get('protocolSha256') != sha(protocol_path):
        raise ValueError('Replay not bound to sealed protocol')
    if [d['decision'] for d in report['dates']] != protocol['decisions']:
        raise ValueError('Date selection differs from protocol')
    old, new = report['trainingOld'], report['trainingNew']
    validate_training_pair(old, new)
    if len(old) != protocol['trainingRows'] or len(mature_rows(old, CUTOFF)) != len(old):
        raise ValueError('Wrong training cohort or cutoff leakage')
    x = np.asarray([[float(r[f+'Score']) for f in FACTORS] for r in new])
    mu, scale = x.mean(axis=0), x.std(axis=0)
    scale[scale < 1e-12] = 1
    z = (x-mu)/scale
    if not np.isfinite(z).all():
        raise ValueError('Nonfinite training features')
    fits = {}
    for target in ['Absolute', 'Excess']:
        # benchmarkReturnPct must come from full actual holding benchmark, never null=>zero.
        y = np.asarray([float(r['forwardReturnPct']) -
                        (float(r['benchmarkReturnPct']) if target == 'Excess' else 0) for r in new])
        if not np.isfinite(y).all():
            raise ValueError('Nonfinite/missing training target')
        intercept = float(y.mean())
        weights = np.linalg.lstsq(z.T@z+10*np.eye(len(FACTORS)), z.T@(y-intercept), rcond=None)[0]
        fits[target] = dict(intercept=intercept, weights=weights.tolist(), median=float(np.median(y)))
    observations = []
    for day in report['dates']:
        validate_pairs(day['pairs'], protocol['populations'][day['decision']])
        signals = {r['symbol']:r for r in day['newSignals']}
        if set(signals) != set(protocol['populations'][day['decision']]):
            raise ValueError('Target feature population differs')
        for row in day['pairs']:
            r = dict(row, decision=day['decision'])
            tx = (np.asarray([float(signals[r['symbol']][f+'Score']) for f in FACTORS])-mu)/scale
            for target in ['Absolute', 'Excess']:
                fit = fits[target]
                r['median'+target] = fit['median']
                r['ridge'+target] = float(tx @ np.asarray(fit['weights'])+fit['intercept'])
            observations.append(r)
    metrics = {}
    for target in ['Absolute', 'Excess']:
        actual = [float(r['actual'])-(float(r['benchmark']) if target=='Excess' else 0) for r in observations]
        metrics[target] = {}
        for name, prefix in [('corrected_v8_baseline','old'),('repaired_prepanel_v8','new'),
                             ('training_pooled_median','median'),('fixed_ten_factor_ridge_alpha10','ridge')]:
            metrics[target][name] = error_metrics([r[prefix+target] for r in observations], actual)
        metrics[target]['zero_return'] = error_metrics([0.0]*len(actual), actual)
    daily = {}
    for day in DATES:
        selected = [r for r in observations if r['decision']==day]
        daily[day] = {}
        for target in ['Absolute','Excess']:
            actual = [float(r['actual'])-(float(r['benchmark']) if target=='Excess' else 0) for r in selected]
            daily[day][target] = {p:error_metrics([r[p+target] for r in selected],actual)
                                  for p in ['old','new','median','ridge']}
    result = dict(protocolSha256=sha(protocol_path), replaySha256=sha(replay),
                  testDateGroups=2, outcomes=len(observations), trainingRows=len(new),
                  metrics=metrics, perDate=daily, fittedOnlyOnPrecutoff=fits,
                  featureMean=mu.tolist(), featureScale=scale.tolist(),
                  promotionAllowed=False, noTuningAfterHoldout=True,
                  limitations=protocol['limitations'], pairs=observations)
    save_new(ROOT/'evaluation.json',result)
    print(json.dumps({k:v for k,v in result.items() if k in ['outcomes','trainingRows','metrics','perDate','promotionAllowed']},indent=2))


def amend():
    original = ROOT/'protocol.json'
    protocol = json.loads(original.read_text())
    old_code = ROOT/'sealed-v1-evaluator.py'
    if sha(old_code) != protocol['hashes']['scripts/quant_new_period.py']:
        raise ValueError('Original sealed code not preserved')
    protocol['originalProtocolSha256'] = sha(original)
    protocol['originalSealedAtUtc'] = protocol['sealedAtUtc']
    protocol['sealedAtUtc'] = datetime.now(timezone.utc).isoformat()
    protocol['comparisonModelVersion'] = 'EXPECTED_RETURN_V8_CORRECTED_20261017'
    protocol['v9ComparisonStatus'] = 'BLOCKED_MISSING_PRIOR_SHARE_EVIDENCE_NO_SUBSET_EVALUATION'
    protocol['missingV9Shares'] = ['ABNB','APP','DASH','DDOG','GOOG','GOOGL','META','MSTR','PDD','PLTR','SHOP']
    protocol['amendmentReason'] = 'FullV9required808forecasts but only797available;11missing capitalization inputs. Input availability only; no test MAE/results inspected. Preserve dates,101populations,cutoff,training,candidateformulas; use fullV8beforeV9provenancegate. Do not interpret as V9validation.'
    protocol['candidates'] = ['corrected_v8_baseline','repaired_prepanel_v8',
                              'training_pooled_median','fixed_ten_factor_ridge_alpha10']
    protocol['hashes']['scripts/quant_new_period.py'] = sha('scripts/quant_new_period.py')
    protocol['hashes'][str(old_code)] = sha(old_code)
    protocol['limitations'].append('V9full-population holdout remains blocked, V8results do not certify V9.')
    path = ROOT/'amended-protocol.json'
    save_new(path, protocol)
    print(json.dumps(dict(protocol=str(path),sha256=sha(path),comparison=protocol['comparisonModelVersion'])))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['seal','amend','evaluate'])
    parser.add_argument('--replay')
    args = parser.parse_args()
    if args.mode == 'seal':
        seal()
    elif args.mode == 'amend':
        amend()
    else:
        evaluate(args.replay)
