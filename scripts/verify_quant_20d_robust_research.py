"""Independent stdlib-only saved-artifact validation; no model refitting or DB."""
import argparse
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path
from statistics import mean, median, pstdev


def quantile(values, fraction):
    values = sorted(values)
    position = (len(values)-1)*fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    return values[lower] + (values[upper]-values[lower])*(position-lower)


def ranks(values):
    result = [0.] * len(values)
    order = sorted(range(len(values)), key=lambda i: values[i])
    start = 0
    while start < len(order):
        stop = start + 1
        while stop < len(order) and values[order[stop]] == values[order[start]]:
            stop += 1
        for index in order[start:stop]:
            result[index] = (start+stop-1)/2+1
        start = stop
    return result


def independent_metrics(rows):
    p = [r['predicted'] for r in rows]
    a = [r['actual'] for r in rows]
    errors = [x-y for x,y in zip(p,a)]
    absolute = [abs(e) for e in errors]
    rank = None
    if min(p) != max(p) and min(a) != max(a):
        pr, ar = ranks(p), ranks(a)
        pm, am = mean(pr), mean(ar)
        numerator = sum((x-pm)*(y-am) for x,y in zip(pr,ar))
        denominator = math.sqrt(sum((x-pm)**2 for x in pr)*sum((y-am)**2 for y in ar))
        rank = numerator/denominator
    return dict(maePp=mean(absolute), rmsePp=math.sqrt(mean(e*e for e in errors)),
                biasPp=mean(errors), p90AbsErrorPp=quantile(absolute,.9),
                p95AbsErrorPp=quantile(absolute,.95), maxAbsErrorPp=max(absolute), spearman=rank)


def close(actual, expected):
    if expected is None:
        assert actual is None, (actual, expected)
    else:
        assert math.isclose(actual, expected, abs_tol=1e-9, rel_tol=1e-10), (actual, expected)


def verify(root):
    result_path = root/'result.json'
    result = json.loads(result_path.read_text(encoding='utf-8'))
    assert result['databaseWrites'] == 0 and result['promotionAllowed'] is False
    for path, expected in result['inputHashes'].items():
        assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected, path
    provenance = result['historicalSourceProvenance']
    historical = Path(provenance['snapshotPath'])
    assert hashlib.sha256(historical.read_bytes()).hexdigest() == provenance['expectedHistoricalSha256']
    assert provenance['expectedHistoricalSha256'] == provenance['recoveredHistoricalSha256']
    panel = json.loads((root/'input-panel.json').read_text(encoding='utf-8'))
    panel_by_key = {(r['day'],r['symbol']): r for r in panel}
    assert len(panel_by_key) == len(panel) == 2020
    grouped = defaultdict(list)
    keys = set()
    for p in result['predictions']:
        key = (p['day'],p['target'],p['family'],p['symbol'])
        assert key not in keys
        keys.add(key)
        assert p['day'] < p['entry'] < p['exit']
        assert math.isfinite(p['predicted']) and math.isfinite(p['actual'])
        field = 'actual' if p['target'] == 'absolute' else 'excess'
        assert p['actual'] == panel_by_key[p['day'],p['symbol']][field]
        grouped[p['target'],p['family'],p['day']].append(p)
    daily = {(d['target'],d['family'],d['day']): d for d in result['daily']}
    for key, rows in grouped.items():
        assert len(rows) == 101
        baseline = grouped[key[0],'V9',key[2]]
        assert {(p['symbol'],p['actual']) for p in rows} == {(p['symbol'],p['actual']) for p in baseline}
        for name,value in independent_metrics(rows).items():
            close(daily[key][name], value)
        close(daily[key]['maeDeltaVsV9Pp'], daily[key]['maePp']-daily[key[0],'V9',key[2]]['maePp'])
    for s in result['summary']:
        days = sorted({p['day'] for p in result['predictions']}) if s['subset'] == 'allCommonDates' else result['nonoverlapDates']
        rows = [p for p in result['predictions'] if p['target'] == s['target'] and p['family'] == s['family'] and p['day'] in days]
        assert s['dates'] == len(days) and s['rows'] == len(rows) == 101*len(days)
        for name,value in independent_metrics(rows).items():
            close(s[name],value)
        ds = [daily[s['target'],s['family'],day] for day in days]
        close(s['maeDeltaVsV9Pp'],mean(d['maeDeltaVsV9Pp'] for d in ds))
        assert s['betterDates'] == sum(d['maeDeltaVsV9Pp'] < 0 for d in ds)
        correlations = [d['spearman'] for d in ds if d['spearman'] is not None]
        close(s['meanDailySpearman'], mean(correlations) if correlations else None)
    models = sorted({m for row in panel for m in row['forecasts']})
    v9 = next(m for m in models if m.startswith('EXPECTED_RETURN_V9'))
    for fold in result['fits']:
        day, target, family = fold['day'], fold['target'], fold['family']
        train = [r for r in panel if r['day'] < day and r['exit'] < day]
        assert fold['trainingDates'] == sorted({r['day'] for r in train})
        assert len(fold['trainingDates']) >= 8 and fold['trainingRows'] == len(train)
        assert fold['maximumTrainingExit'] == max(r['exit'] for r in train) < day
        fit = fold['fit']
        field = 'actual' if target == 'absolute' else 'excess'
        if 'weights' not in fit:
            residual = family == 'V9_RESIDUAL_MEDIAN'
            correction = median(r[field]-(r['forecasts'][v9][target] if residual else 0) for r in train)
            close(fit['correctionPp'],correction)
        else:
            with_v9 = family.startswith('V9_')
            xs = [([r['forecasts'][v9][target]] if with_v9 else [])+r['price'] for r in train]
            for j in range(len(xs[0])):
                close(fit['mean'][j],mean(x[j] for x in xs))
                scale = pstdev(x[j] for x in xs)
                close(fit['scale'][j],scale if scale >= 1e-12 else 1)
        for p in grouped[target,family,day]:
            row = panel_by_key[day,p['symbol']]
            if 'weights' in fit:
                x = ([row['forecasts'][v9][target]] if family.startswith('V9_') else [])+row['price']
                value = fit['intercept']+sum((a-b)/c*d for a,b,c,d in zip(x,fit['mean'],fit['scale'],fit['weights']))
            else:
                value = fit['correctionPp']+(row['forecasts'][v9][target] if family == 'V9_RESIDUAL_MEDIAN' else 0)
            close(p['predicted'],value)
    # Independently apply the chronological nonoverlap rule, without labels.
    exits = {}
    for p in result['predictions']:
        exits[p['day']] = max(exits.get(p['day'],''),p['exit'])
    selected, last = [], ''
    for day in sorted(exits):
        if day > last:
            selected.append(day)
            last = exits[day]
    assert selected == result['nonoverlapDates']
    assert len(keys) == 17776 and len(daily) == 176 and len(result['fits']) == 132
    return dict(status='PASS', predictionRows=len(keys), dailyGroups=len(daily), fits=len(result['fits']),
                commonDates=11, membersPerDate=101, nonoverlapDates=len(selected),
                inputHashesVerified=len(result['inputHashes']), currentJavaNotRequiredForHistoricalValidation=True,
                resultSha256=hashlib.sha256(result_path.read_bytes()).hexdigest(),
                validatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                independentStdlibArithmeticAndSavedCoefficientPredictionsVerified=True,
                databaseWrites=0,promotionAllowed=False)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path('tmp/quant-20d-robust-research-v6-20261031'))
    args = parser.parse_args()
    verification = verify(args.root)
    path = args.root/'verification.json'
    if not path.exists():
        with path.open('x',encoding='utf-8') as stream:
            json.dump(verification,stream,indent=2)
    else:
        assert json.loads(path.read_text(encoding='utf-8')) == verification
    print(json.dumps(verification,indent=2))


if __name__ == '__main__':
    main()
