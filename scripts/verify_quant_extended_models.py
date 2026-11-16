"""File-only independent checks, preserving old experiment and joining disjoint dates."""
import hashlib
import json
from collections import defaultdict
from datetime import date
from pathlib import Path
from statistics import mean
from verify_quant_team_models import read_json, table, close, ranks, correlation

ROOT=Path('tmp/quant-extended-model-recovery-final-20261018')
OLD=Path('tmp/quant-team-model-recovery-final-20261018/result.json')
INDEX='NASDAQ100_MACRO_FREE_20261018'

def verify():
    normal=read_json(ROOT/'result.json')
    perturbed=read_json('tmp/quant-extended-model-recovery-perturbed-20261018/result.json')
    old=read_json(OLD)
    for report in (normal,perturbed,old):
        assert report['databaseWrites']==0 and report['promotionAllowed'] is False
        assert report['sourceOutcomeIsolationVerified']
        for path,expected in report['inputHashes'].items():assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==expected,path
    assert not normal['targetOutcomePerturbation'] and perturbed['targetOutcomePerturbation']
    key=lambda p:(p['modelVersion'],p['signalDate'],p['symbol'],p['horizonDays'])
    canonical=lambda p:{k:v for k,v in p.items() if k!='calculatedAt'}
    normal_map={key(p):canonical(p) for p in normal['predictions']}
    counter_map={key(p):canonical(p) for p in perturbed['predictions']}
    assert len(normal_map)==len(normal['predictions'])==len(counter_map)==4040
    assert normal_map==counter_map,'Future target labels changed forecasts'
    assert any(a['forecastMetrics']!=b['forecastMetrics'] for x,y in zip(normal['dates'],perturbed['dates']) for a,b in zip(x['models'],y['models'])), 'Ineffective perturbation'
    manifest=read_json('tmp/quant-extended-inputs-20261018/manifest.json')
    for source in manifest['sources']:
        assert source['status']==200 and hashlib.sha256(Path(source['path']).read_bytes()).hexdigest()==source['sha256']
    financials=read_json('tmp/quant-extended-inputs-20261018/financial-inputs.json')
    for symbol,dates in financials['bySymbol'].items():
        for day,rows in dates.items():
            for f in rows:
                assert f['symbol']==symbol and f['filedAt']<day and f['endDate']<day
                duration=(date.fromisoformat(f['endDate'])-date.fromisoformat(f['startDate'])).days+1
                assert (330<=duration<=380) if f['fiscalPeriod']=='FY' else (70<=duration<=110)
    for d in normal['dates']:
        for r in d['recoveredInputs']:
            s=r['share'];m=r['market'];day=d['decision']
            assert s['filedDate']<day and s['shareMeasurementDate']<day
            eligible=[x for x in financials['shareObservations'] if x['symbol']==r['symbol'] and x['filedDate']<day and x['shareMeasurementDate']<day]
            latest=max(eligible,key=lambda x:(x['shareMeasurementDate'],x['filedDate']))
            assert s==latest and m['sharesOutstanding']==s['totalCommonShares']
            close(m['marketCapUsd'],m['closePrice']*s['totalCommonShares'],0.001)
            if r['symbol']=='AZN':
                assert m['priceSource']=='ARCHIVED_YAHOO_ORDINARY_EQUIVALENT_RESEARCH'
                # Hand-checked ordinary PE: 32.48,35.51,34.10,41.21,40.86;
                # never divide the 4.50 ordinary EPS by two against these prices.
                assert r['signal']['valuationScore']=={
                    '2025-07-31':55,'2025-08-29':40,'2025-09-30':55,
                    '2025-11-28':40,'2025-12-31':40}[day]
                assert s['totalCommonShares']==1550546239
    members=defaultdict(set)
    for r in table('tmp/quant-complete-window-members-20261018.tsv','\t'):
        if r['index_code']==INDEX:members[r['snapshot_date']].add(r['symbol'])
    holdings={(r['decision'],r['symbol']):r for r in table('tmp/quant-complete-windows-recovered-20261018/complete-holdings.csv') if r['index']==INDEX}
    levels={r['trade_date']:float(r['benchmark_level']) for r in table('tmp/quant-complete-window-benchmarks-20261018.tsv','\t') if r['index_code']==INDEX and r['benchmark_level'] not in ('NULL','')}
    groups=defaultdict(list)
    for report in (normal,old):
        for p in report['predictions']:groups[p['signalDate'],p['modelVersion']].append(p)
    assert len(groups)==64 and sum(map(len,groups.values()))==6464
    model_periods=defaultdict(list)
    for report in (normal,old):
        for d in report['dates']:
            day=d['decision'];assert len(members[day])==101
            for model in d['models']:
                ps=groups[day,model['model']];assert len(ps)==101 and {p['symbol'] for p in ps}==members[day]
                assert model['fullPopulation'] and model['missingSymbols']==[]
                actual=[];predicted=[];probs=[];windows=set()
                for p in ps:
                    assert p['calculatedAt'][:10]>day and p['source'].endswith('CUTOFF_LT_'+day.replace('-',''))
                    assert 0<=p['calibratedUpsideProbabilityPct']<=100 and p['excessP10Pct']<=p['excessP50Pct']<=p['excessP90Pct']
                    h=holdings[day,p['symbol']];windows.add((h['entry'],h['exit']))
                    b=(levels[h['exit']]/levels[h['entry']]-1)*100
                    close(h['benchmarkReturnPct'],b)
                    actual.append(float(h['actualReturnPct'])-b);predicted.append(p['expectedExcessReturnPct']);probs.append(p['calibratedUpsideProbabilityPct']/100)
                assert len(windows)==1
                metrics=model['forecastMetrics']
                close(metrics['maePct'],mean(abs(p-a) for p,a in zip(predicted,actual)))
                close(metrics['brierScore'],mean((p-(a>0))**2 for p,a in zip(probs,actual)),1e-8)
                close(metrics['directionalAccuracyPct'],100*mean((p>0 and a>0) or (p<0 and a<0) or (p==a==0) for p,a in zip(predicted,actual)))
                close(metrics['rankIc'],correlation(ranks(predicted),ranks(actual)),1e-8)
                basket=sorted(ps,key=lambda p:p['expectedExcessReturnPct'],reverse=True)[:20]
                gross=mean(float(holdings[day,p['symbol']]['actualReturnPct']) for p in basket)
                close(model['portfolio']['grossReturnPct'],gross);close(model['portfolio']['netReturnPct'],gross-0.2)
                entry,exit_=next(iter(windows))
                model_periods[model['model']].append(dict(decision=day,entry=entry,exit=exit_,grossReturnPct=gross,
                    netReturnPct=gross-0.2,benchmarkReturnPct=b,selected=[p['symbol'] for p in basket]))
    java=read_json(ROOT/'combined-java-accounting.json');assert java['modelDateGroups']==64
    combined={}
    for model,periods in model_periods.items():
        used=[];last=None;wealth=[1.,1.,1.]
        for p in sorted(periods,key=lambda p:p['decision']):
            if last and p['decision']<last:continue
            used.append(p);last=p['exit']
            for i,field in enumerate(['grossReturnPct','netReturnPct','benchmarkReturnPct']):wealth[i]*=1+p[field]/100
        assert len(used)==java['combined'][model]['periodCount']==6
        values={field:(wealth[i]-1)*100 for i,field in enumerate(['grossReturnPct','netReturnPct','benchmarkReturnPct'])}
        for field,value in values.items():close(java['combined'][model][field],value)
        close(java['combined'][model]['transactionCostPct'],1.2)
        combined[model]=dict(**values,periodCount=len(used),periods=used)
    result=dict(status='PASS',classification='EXPANDED_EXISTING_FORMULA_RECONSTRUCTED_RESEARCH_NOT_PIT',
        newDecisionDates=[d['decision'] for d in normal['dates']],allDecisionDates=sorted({d for d,m in groups}),
        newPredictions=4040,totalDistinctPredictions=6464,wholePopulationMembersPerDate=101,modelDateGroups=64,
        futureTargetLabelInvariant=True,existingModels='V2-V9',combined=combined,promotionAllowed=False,databaseWrites=0,
        sourceExperimentHashes={str(path):hashlib.sha256(path.read_bytes()).hexdigest() for path in [ROOT/'result.json',OLD]},
        exclusions={'2025-10-31':'102 members incl SOLS; missing predictor and unresolved official interval',
                    '2026-03-13':'14 absent historical predictor rows; no partial-population evaluation'},
        limitations=['No official PIT population certification; issuer tables later-published and same source lineage.',
          'Existing missing-factor policies and frozen incomplete older training context retained; current recovered labels not rolled into later training.',
          'Each forecast reconstructed now; no live performance claim. Price returns, not dividend/spin-off-reinvested total returns.',
          'AZN ordinary-equivalent archive normalization is retrospective, not proof of contemporaneous execution quotes.'])
    (ROOT/'independent-verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('combined','sourceExperimentHashes')},indent=2))

if __name__=='__main__':verify()
