"""Independent read-only raw ALFRED -> feature and SQL -> Java result verification."""
import calendar
import csv
import json
import math
import struct
from datetime import date, timedelta
from pathlib import Path

ROOT = Path('tmp/free-quant-followup-20261018/derived')


def rows(path, delimiter=','):
    with path.open(encoding='utf-8-sig') as stream:
        return list(csv.DictReader(stream, delimiter=delimiter))


def months(day, amount):
    n = day.year * 12 + day.month - 1 - amount
    year, month = n // 12, n % 12 + 1
    return date(year, month, min(day.day, calendar.monthrange(year, month)[1]))


def rounded(value):
    value = struct.unpack('f', struct.pack('f', value))[0]
    return max(0, min(100, math.floor(value + .5)))


def check_close(actual, expected, tolerance, label):
    if expected is None:
        assert actual == 'NULL', (label, actual, expected)
    else:
        assert math.isfinite(float(actual)) and abs(float(actual) - expected) <= tolerance, (label, actual, expected)


def verify():
    mapping = {r['decision_date']: r['as_of_date'] for r in rows(ROOT / 'decision_asof_map.csv')}
    observations = {}
    for r in rows(ROOT / 'macro_asof_snapshots_RESEARCH_ONLY.csv'):
        assert r['source_id'] == f"alfred-{r['series_code']}-{r['as_of_date']}"
        assert r['observation_date'] <= r['as_of_date']
        series = observations.setdefault(r['as_of_date'], {}).setdefault(r['series_code'], {})
        assert r['observation_date'] not in series
        series[r['observation_date']] = float(r['value'])
    features = rows(Path('tmp/quant-macro-features-20261018.tsv'), '\t')
    keys = set()
    for r in features:
        key = (r['index_code'], r['snapshot_date'])
        assert key not in keys
        keys.add(key)
        day = date.fromisoformat(r['snapshot_date'])
        assert mapping[str(day)] == str(day - timedelta(days=1))
        s = observations[mapping[str(day)]]
        def prior(code, cutoff):
            dates = [d for d in s[code] if d <= str(cutoff)]
            return s[code][max(dates)] if dates else None
        def latest(code):
            return s[code][max(s[code])]
        def growth(code, cutoff):
            old = prior(code, cutoff)
            return (latest(code) / old - 1) * 100 if old else None
        short, long, fed = latest('DGS3MO'), latest('DGS10'), latest('FEDFUNDS')
        spread = long - short
        cpi_date = date.fromisoformat(max(s['CPIAUCSL']))
        cpi_y, cpi_m = growth('CPIAUCSL', months(cpi_date, 12)), growth('CPIAUCSL', months(cpi_date, 1))
        unrate = latest('UNRATE')
        unrate_old = prior('UNRATE', months(date.fromisoformat(max(s['UNRATE'])), 3))
        unrate_change = unrate - unrate_old if unrate_old is not None else None
        vix = latest('VIXCLS')
        vix_change = growth('VIXCLS', day - timedelta(days=30))
        dollar_change = growth('DTWEXBGS', day - timedelta(days=30))
        expected = dict(short_rate_pct=short, long_rate_pct=long, yield_spread_pct=spread, fed_funds_pct=fed,
                        cpi_yoy_pct=cpi_y, cpi_mom_pct=cpi_m, unemployment_rate_pct=unrate,
                        unemployment_change_3m_pct=unrate_change, vix_level=vix, vix_change_20d_pct=vix_change,
                        dollar_index=latest('DTWEXBGS'), dollar_change_20d_pct=dollar_change)
        for field, value in expected.items():
            check_close(r[field], value, .000001, (key, field))
        tight = rounded(56 - max(0, short-2.5)*5.5 - max(0, fed-2.5)*3.5 + min(2, max(-2, spread))*7 - (8 if long < short else 0))
        stress = 58
        if cpi_y is not None:
            stress -= abs(cpi_y-2.2)*2.4 + max(0, cpi_y-4)*3
        if cpi_m is not None:
            stress -= max(0, cpi_m-.35)*12
        stress -= max(0, unrate-5)*4
        if unrate_change is not None:
            stress += -max(0, unrate_change)*18 + max(0, -unrate_change)*4
        stress = rounded(stress)
        risk = 58 - max(0, vix-18)*1.15 + max(0, 16-vix)*.45 - (4 if spread < -.4 else 0)
        if vix_change is not None:
            risk -= max(0, vix_change)*.12
        if dollar_change is not None:
            risk -= max(0, dollar_change)*1.1
        risk = rounded(risk)
        for field, value in dict(macro_tightness_score=tight, macro_growth_stress_score=stress,
                                 macro_risk_score=risk, macro_feature_score=rounded(tight*.35+stress*.30+risk*.35)).items():
            check_close(r[field], value, 0, (key, field))
        assert r['source'] == 'ALFRED_ASOF_RESEARCH_PREVDAY' and r['calculated_at'][:10] == '2026-10-18'
    assert keys == {(u+'_MACRO_FREE_20261018', d) for u in ('SP500', 'NASDAQ100') for d in mapping}
    report = json.loads(Path('tmp/quant-macro-asof-validation-20261018.json').read_text(encoding='utf-8-sig'))
    independent = rows(Path('tmp/quant-macro-independent-metrics-20261018.tsv'), '\t')
    assert len(independent) == 12
    checked = set()
    for u in report['results']:
        for c in u['comparisons']:
            assert c['baseline']['targetCount'] == c['asofMacro']['targetCount']
            assert c['baseline']['usableRows'] == c['asofMacro']['usableRows']
            for variant in ('baseline', 'asofMacro'):
                key = (u['universe'], variant, c['model'])
                assert key not in checked
                checked.add(key)
                match = [r for r in independent if (r['universe'], r['variant'], r['model_version']) == key]
                assert len(match) == 1
                r, actual = match[0], c[variant]
                assert int(r['usable_20d']) == actual['usableRows']
                for sql, java, tolerance in [('brier', 'brierScore', 1e-10), ('mae_pct', 'maePct', 1e-5), ('direction_pct', 'directionalAccuracyPct', 1e-4)]:
                    check_close(r[sql], actual['metrics'][java], tolerance, (key, sql))
                check_close(actual['metrics']['coveragePct'], 100*actual['usableRows']/actual['targetCount'], 1e-10, key)
            if '_V7_' in c['model']:
                assert c['baseline']['metrics'] == c['asofMacro']['metrics']
            for p in ('baselinePortfolio', 'asofPortfolio'):
                portfolio = c[p]
                assert portfolio['completeDecisionDates'] == 0
                assert portfolio['metrics']['periodCount'] == 0 and portfolio['metrics']['netReturnPct'] == 'NaN'
    assert report['promotionAllowed'] is False
    assert report['strictProductionEvaluation']['decision'] == 'INSUFFICIENT_DATA'
    return dict(status='VERIFIED_WITH_RESEARCH_CAVEATS', featureRows=len(keys), independentMetricGroups=len(checked),
                fullPopulationPortfolioPeriods=0, promotionAllowed=False, rankIcIndependentlyChecked=False)


if __name__ == '__main__':
    result = verify()
    Path('tmp/quant-macro-independent-verification-20261018.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))
