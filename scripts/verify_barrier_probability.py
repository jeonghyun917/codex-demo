"""Independently reconcile saved probabilities, selection counts and trade arithmetic."""
import csv
import hashlib
import json
import math
from collections import Counter, defaultdict
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'output/barrier-probability-20261018'


def main():
    report = json.loads((OUT/'results.json').read_text())
    models = json.loads((OUT/'walk-forward-models.json').read_text())
    for name, expected in report['hashes'].items():
        assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest() == expected, name
    lookup = {(m['index'],m['decision']):m for m in models}
    assert len(lookup) == len(models)
    for model in models:
        assert model['fit_max_maturity'] < model['calibration_start']
        assert model['calibration_max_maturity'] < model['decision']
    with (OUT/'predictions.csv').open(newline='') as f:
        predictions = list(csv.DictReader(f))
    for row in predictions:
        model = lookup[row['index_code'],row['date']]['parameters']
        x = np.array([float(row[k]) if row[k] else model['impute'][i]
                      for i,k in enumerate(report['features'])])
        z = (x-np.array(model['mean']))/np.array(model['scale'])
        logit = float(z @ np.array(model['coefficient'])) + model['intercept']
        p = 1/(1+math.exp(-(model['calibration_slope']*logit+model['calibration_intercept'])))
        assert abs(p-float(row['barrier_probability'])) < 1e-12
        assert row['date'] < row['entry'] <= row['exit'] <= row['maturity']
        assert int(row['label']) == int(row['reason']=='TP')
    with (OUT/'strategy-trades.csv').open(newline='') as f:
        trades = list(csv.DictReader(f))
    groups = defaultdict(list)
    for row in trades:
        expected = (float(row['exit_price'])/float(row['entry_price'])-1)*100-.2
        assert abs(expected-float(row['net'])) < 1e-10
        groups[row['index_code'],row['strategy']].append(row)
    for result in report['results']:
        monthly_population = Counter(r['date'] for r in predictions if r['index_code']==result['index'])
        for strategy, summary in result['strategies'].items():
            rows = groups[result['index'],strategy]
            assert len(rows) == summary['n']
            assert abs(float(np.mean([float(r['net']) for r in rows]))-summary['mean_net']) < 1e-10
            if strategy.endswith('TOP20'):
                counts = Counter(r['date'] for r in rows)
                assert counts == {d:math.ceil(n*.2) for d,n in monthly_population.items()}
            last_exit = {}
            for r in sorted(rows,key=lambda r:(r['entry'],r['symbol'])):
                assert r['entry'] > last_exit.get(r['symbol'],'')
                last_exit[r['symbol']] = r['exit']
    result = dict(status='PASS',models=len(models),predictions=len(predictions),
                  trade_observations=len(trades),checks=['input_hashes','temporal_boundaries',
                  'saved_model_probability_reconstruction','labels','price_to_net_return',
                  'matched_monthly_quotas','no_duplicate_positions'])
    (OUT/'verification.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    main()
