"""Independent file-only checks of the bounded existing-model recovery experiment.

This verifies reconstructed arithmetic and one leakage regression, NOT official PIT validity.
Only the unperturbed experiment supplies performance; the counterfactual is comparison-only.
"""
import csv
import hashlib
import json
import math
from collections import defaultdict
from datetime import date
from pathlib import Path
from statistics import mean

ROOT = Path('tmp/quant-team-model-recovery-final-20261018')
DATES = ('2026-01-30', '2026-02-27', '2026-03-27')
INDEX = 'NASDAQ100_MACRO_FREE_20261018'


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def table(path, delimiter=','):
    with Path(path).open(encoding='utf-8-sig', newline='') as handle:
        return list(csv.DictReader(handle, delimiter=delimiter))


def close(actual, expected, tolerance=0.00002):
    assert math.isfinite(float(actual)) and abs(float(actual)-expected) < tolerance, (actual, expected)


def ranks(values):
    result = [0.0]*len(values)
    order = sorted(range(len(values)), key=values.__getitem__)
    start = 0
    while start < len(order):
        end = start+1
        while end < len(order) and values[order[start]] == values[order[end]]:
            end += 1
        for position in order[start:end]:
            result[position] = (start+1+end)/2
        start = end
    return result


def correlation(left, right):
    a, b = mean(left), mean(right)
    return sum((x-a)*(y-b) for x,y in zip(left,right))/math.sqrt(
        sum((x-a)**2 for x in left)*sum((y-b)**2 for y in right))


def verify(normal, counterfactual):
    assert not normal['targetOutcomePerturbation'] and counterfactual['targetOutcomePerturbation']
    for report in (normal, counterfactual):
        assert report['databaseWrites'] == 0 and report['promotionAllowed'] is False
        assert report['sourceOutcomeIsolationVerified'] is True
        for path, expected in report['inputHashes'].items():
            assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected, path
    assert normal['inputHashes'] == counterfactual['inputHashes']
    key = lambda p: (p['modelVersion'], p['signalDate'], p['symbol'], p['horizonDays'])
    canonical = lambda p: {k:v for k,v in p.items() if k != 'calculatedAt'}
    a = {key(p):canonical(p) for p in normal['predictions']}
    b = {key(p):canonical(p) for p in counterfactual['predictions']}
    assert len(a) == len(normal['predictions']) == len(b) == 2424
    assert a == b, 'Current target future labels changed predictions'

    members = table('tmp/quant-complete-window-members-20261018.tsv', '\t')
    holdings = {(h['decision'],h['symbol']):h for h in table(
        'tmp/quant-complete-windows-recovered-20261018/complete-holdings.csv') if h['index']==INDEX}
    benchmark = {r['trade_date']:float(r['benchmark_level']) for r in table(
        'tmp/quant-complete-window-benchmarks-20261018.tsv','\t')
        if r['index_code']==INDEX and r['benchmark_level'] not in ('NULL','')}
    financials = read_json('tmp/quant-team-inputs-20261018/financial-inputs.json')
    shares = read_json('tmp/quant-team-share-evidence-20261018/share-facts.json')
    share_by_accession = {r['accessionNumber']:r for r in shares['observations']}
    groups = defaultdict(list)
    for p in normal['predictions']:
        groups[(p['signalDate'],p['modelVersion'])].append(p)
        assert p['horizonDays']==20 and p['indexCode']=='NASDAQ100_TEAM_INPUT_RESEARCH_20261018'
        # Existing V3-V8 formulas retain their normal method-name source tags;
        # isolation/classification comes from the research index and outer artifact.
        assert p['source'].endswith('CUTOFF_LT_'+p['signalDate'].replace('-',''))
        assert 'LIVE' not in p['source'] and 'OFFICIAL_PIT' not in p['source']
        assert p['calculatedAt'][:10] > p['signalDate']
        for field in ('expectedExcessReturnPct','expectedReturnPct','excessP10Pct','excessP90Pct'):
            assert math.isfinite(p[field])
        assert 0 <= p['calibratedUpsideProbabilityPct'] <= 100
        assert p['excessP10Pct'] <= p['excessP50Pct'] <= p['excessP90Pct']

    verified_dates = []
    periods = defaultdict(list)
    for day_report in normal['dates']:
        day = day_report['decision']
        expected = {m['symbol'] for m in members if m['index_code']==INDEX and m['snapshot_date']==day}
        assert day in DATES and len(expected)==101 and expected==set(day_report['fullPopulation'])
        assert {s for d,s in holdings if d==day}==expected
        for f in financials['byDecision'][day]:
            assert f['filedAt'] < day and f['endDate'] < day
            days = (date.fromisoformat(f['endDate'])-date.fromisoformat(f['startDate'])).days+1
            assert 330<=days<=380 if f['fiscalPeriod']=='FY' else 70<=days<=110
        share = share_by_accession[day_report['teamShareAccession']]
        assert share['filedDate'] < day and share['shareMeasurementDate'] < day
        assert share['classAShares']+share['classBShares']==share['totalCommonShares']
        cap = day_report['teamMarket']
        assert cap['sharesOutstanding']==share['totalCommonShares']
        assert cap['sharesSource']=='SEC_TEXT_CURATED_PRIOR_QUARTER_TOTAL_COMMON'
        close(cap['marketCapUsd'],cap['closePrice']*cap['sharesOutstanding'],0.001)
        for model_report in day_report['models']:
            model = model_report['model']
            ps = groups[(day,model)]
            assert len(ps)==101 and {p['symbol'] for p in ps}==expected
            assert model_report['fullPopulation'] and model_report['missingSymbols']==[]
            actual, predicted, probabilities, windows = [], [], [], set()
            for p in ps:
                h = holdings[(day,p['symbol'])]
                windows.add((h['entry'],h['exit']))
                benchmark_return = (benchmark[h['exit']]/benchmark[h['entry']]-1)*100
                close(h['benchmarkReturnPct'],benchmark_return)
                actual.append(float(h['actualReturnPct'])-benchmark_return)
                predicted.append(p['expectedExcessReturnPct'])
                probabilities.append(p['calibratedUpsideProbabilityPct']/100)
            assert len(windows)==1
            metrics = model_report['forecastMetrics']
            close(metrics['maePct'],mean(abs(p-r) for p,r in zip(predicted,actual)))
            close(metrics['brierScore'],mean((p-(r>0))**2 for p,r in zip(probabilities,actual)),1e-9)
            close(metrics['directionalAccuracyPct'],100*mean((p>0 and r>0) or (p<0 and r<0) or (p==r==0) for p,r in zip(predicted,actual)))
            close(metrics['rankIc'],correlation(ranks(predicted),ranks(actual)),1e-8)
            selected = sorted(ps,key=lambda p:p['expectedExcessReturnPct'],reverse=True)[:20]
            entry, exit_ = next(iter(windows))
            gross = mean(float(holdings[(day,p['symbol'])]['actualReturnPct']) for p in selected)
            net = gross-0.2  # 10bps each side, full cash -> basket -> cash.
            close(model_report['portfolio']['grossReturnPct'],gross)
            close(model_report['portfolio']['netReturnPct'],net)
            periods[model].append(dict(decision=day,entry=entry,exit=exit_,gross=gross,net=net,
                benchmark=benchmark_return,selected=[p['symbol'] for p in selected]))
        verified_dates.append(day)
    assert sorted(verified_dates)==list(DATES)

    combined = {}
    for model, candidates in periods.items():
        active_until = None
        used = []
        equities = dict(gross=1.,net=1.,benchmark=1.)
        for period in sorted(candidates,key=lambda p:p['decision']):
            if active_until and period['decision'] < active_until:
                continue
            active_until = period['exit']
            used.append(period)
            for kind in equities:
                equities[kind] *= 1+period[kind]/100
        original = normal['combinedNonoverlapPortfolio'][model]
        assert original['periodCount']==len(used)==2
        for kind, field in [('gross','grossReturnPct'),('net','netReturnPct'),('benchmark','benchmarkReturnPct')]:
            close(original[field],(equities[kind]-1)*100)
        close(original['transactionCostPct'],0.4)
        combined[model]=dict(periods=used,netReturnPct=(equities['net']-1)*100,
            benchmarkReturnPct=(equities['benchmark']-1)*100)
    return dict(status='PASS',classification='RECONSTRUCTED_RESEARCH_NOT_OFFICIAL_PIT',
        dates=verified_dates,uniquePredictions=2424,modelDateGroups=24,
        targetFutureLabelPerturbationInvariant=True,sourceOutcomeIsolationVerified=True,
        forecastMetricsCrosscheck='MAE_BRIER_DIRECTION_RANK_IC_MATCHED',
        accountingCrosscheck='FULL_POPULATION_TOP20_NONOVERLAP_10BPS_PER_SIDE_MATCHED',
        combined=combined,promotionAllowed=False)


if __name__=='__main__':
    result = verify(read_json(ROOT/'result.json'),read_json(
        'tmp/quant-team-model-recovery-perturbed-20261018/result.json'))
    path = ROOT/'independent-verification.json'
    path.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k!='combined'},indent=2))
