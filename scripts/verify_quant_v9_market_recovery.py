"""Independent file-only V9 lineage, whole-population, metric and accounting checks."""
import hashlib,json
from collections import defaultdict,Counter
from datetime import date
from decimal import Decimal
from pathlib import Path
from statistics import mean
from verify_quant_team_models import read_json,table,close,ranks,correlation

ROOT=Path('tmp/quant-v9-historical-market-final-20261031')
OLD=Path('tmp/quant-older-model-recovery-v2-final-20261030')
V9='EXPECTED_RETURN_V9_CORRECTED_20261017'
V8='EXPECTED_RETURN_V8_CORRECTED_20261017'
INDEX='NASDAQ100_MACRO_FREE_20261018'

def verify():
    normal=read_json(ROOT/'result.json')
    counter=read_json('tmp/quant-v9-historical-market-perturbed-20261031/result.json')
    original=read_json(OLD/'result.json')
    inputs=read_json('tmp/quant-v9-market-inputs-20261030/inputs.json')
    prior=[read_json('tmp/quant-extended-model-recovery-final-20261018/result.json'),read_json('tmp/quant-team-model-recovery-final-20261018/result.json')]
    for report in (normal,counter,original,inputs,*prior):
        assert report['databaseWrites']==0 and report['promotionAllowed'] is False
        for path,expected in report['inputHashes'].items():
            assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==expected,path
    assert not normal['targetOutcomePerturbation'] and counter['targetOutcomePerturbation']
    assert normal['savedV8Unchanged'] and counter['savedV8Unchanged']
    key=lambda p:(p['signalDate'],p['modelVersion'],p['symbol'],p['horizonDays'])
    canonical=lambda p:{k:v for k,v in p.items() if k not in ('calculatedAt','indexCode')}
    newmap={key(p):p for p in normal['predictions']}
    countermap={key(p):p for p in counter['predictions']}
    assert len(newmap)==len(normal['predictions'])==len(countermap)==1111
    assert {k:canonical(p) for k,p in newmap.items()}=={k:canonical(p) for k,p in countermap.items()}
    assert any(a['models'][0]['forecastMetrics']!=b['models'][0]['forecastMetrics'] for a,b in zip(normal['dates'],counter['dates']))
    oldknown=[p for p in original['predictions'] if p['modelVersion']==V9]
    assert len(oldknown)==1052
    for p in oldknown:assert canonical(p)==canonical(newmap[key(p)]),key(p)
    # Check every new unit/cutoff/capitalization independently, without using the normalizer.
    recovered=[];sources=Counter();max_age=0
    prices={}
    bundle=read_json('tmp/pit-acquisition/actions/bundle.json')
    from datetime import datetime
    from zoneinfo import ZoneInfo
    for symbol in ('ILMN','GFS','MDB','TEAM','AZN'):
        source=next(s for s in bundle['sources'] if s['id']=='yahoo-chart-'+symbol)
        chart=read_json(Path('tmp/pit-acquisition/actions')/source['path'])['chart']['result'][0]
        prices[symbol]={datetime.fromtimestamp(t,ZoneInfo(chart['meta']['exchangeTimezoneName'])).date().isoformat():str(chart['indicators']['quote'][0]['close'][i])
                        for i,t in enumerate(chart['timestamp'])}
        if symbol=='AZN':assert any(s['numerator']==1 and s['denominator']==2 for s in chart['events']['splits'].values())
    anss=Path('tmp/quant-anss-free-evidence-20261018/chartexchange-anss.csv')
    assert hashlib.sha256(anss.read_bytes()).hexdigest()=='d3bd7e80e31fa9a082f82ac30caa52de017aa6a06b36e8c127068d8bf52247c1'
    prices['ANSS']={r['date']:r['close'] for r in table(anss)}
    for day,rows in inputs['marketRowsByDecision'].items():
        gaps=next(m['missingSymbols'] for d in original['dates'] if d['decision']==day for m in d['models'] if m['model']==V9)
        assert {r['symbol'] for r in rows}==set(gaps) and len(rows)==len(gaps)
        for r in rows:
            s=r['shareEvidence'];symbol=r['symbol']
            assert s['symbol']==symbol and s['measurementDate']<day and s['filedDate']<day and s['unit']=='shares'
            count=Decimal(str(s['totalCommonShares']));assert count>0 and count==count.to_integral_value()
            assert Decimal(r['sharesOutstanding'])==count
            assert Decimal(r['closePrice'])==Decimal(prices[symbol][day])
            assert Decimal(r['closePrice'])*count==Decimal(r['marketCapUsd'])
            assert r['currency']=='USD' and r['snapshotDate']==day
            assert r['priceBasis']==('ORDINARY_EQUIVALENT_ARCHIVE' if symbol=='AZN' else 'ORDINARY')
            if symbol=='TEAM':assert s['classAShares']+s['classBShares']==s['totalCommonShares']
            if symbol in ('TEAM','AZN'):assert not s['rawOriginalSourceAcquired'] and not r['sharesSource'].startswith('SEC_XBRL')
            eligible=[o for o in inputs['observations'] if o['symbol']==symbol and o['filedDate']<day and o['measurementDate']<day]
            ordering=lambda o:(o['measurementDate'],o['filedDate'],o.get('sourcePriority',0))
            assert ordering(s)==max(map(ordering,eligible))
            max_age=max(max_age,(date.fromisoformat(day)-date.fromisoformat(s['measurementDate'])).days)
            recovered.append((day,symbol));sources[r['sharesSource']]+=1
    assert len(recovered)==len(set(recovered))==59
    # The application-produced new rows must obey V9's *existing* source trust adjustment.
    v8map={(p['signalDate'],p['symbol']):p for p in original['predictions'] if p['modelVersion']==V8}
    markets={(m['snapshotDate'],m['symbol']):m for m in normal['marketInputsUsed']}
    assert len(markets)==1111
    for day,symbol in recovered:
        market=markets[day,symbol];p=newmap[day,V9,symbol,20];v8=v8map[day,symbol]
        sec=market['sharesSource'].startswith('SEC_XBRL');scale=1 if sec else .78
        close(p['expectedExcessReturnPct'],v8['expectedExcessReturnPct']*scale,1e-10)
        close(p['calibratedUpsideProbabilityPct'],50+(v8['calibratedUpsideProbabilityPct']-50)*(1 if sec else .74),1e-10)
        assert p['source'].startswith('RESEARCH_V9_'+('SEC_SOURCE' if sec else 'WEAK_PROVENANCE'))
    members=defaultdict(set)
    for r in table('tmp/quant-complete-window-members-20261018.tsv','\t'):
        if r['index_code']==INDEX:members[r['snapshot_date']].add(r['symbol'])
    hs=table('tmp/quant-complete-windows-recovered-20261018/complete-holdings.csv')+table('tmp/quant-older-staged-20261030/holdings.csv')
    holdings={}
    for h in hs:
        if h['index']!=INDEX:continue
        k=(h['decision'],h['symbol']);assert k not in holdings;holdings[k]=h
    levels={r['trade_date']:float(r['benchmark_level']) for r in table('tmp/quant-complete-window-benchmarks-20261018.tsv','\t') if r['index_code']==INDEX and r['benchmark_level'] not in ('NULL','')}
    groups=defaultdict(list);model_info={}
    for report in (original,*prior,normal):
        admitted={(d['decision'],m['model']):m for d in report['dates'] for m in d['models'] if m['fullPopulation'] and m['predictions']==101}
        for k,m in admitted.items():assert k not in model_info;model_info[k]=m
        for p in report['predictions']:
            k=(p['signalDate'],p['modelVersion'])
            if k in admitted:groups[k].append(p)
    assert len(groups)==152 and sum(map(len,groups.values()))==15352
    periods=defaultdict(list)
    for (day,model),ps in groups.items():
        assert len(ps)==101 and {p['symbol'] for p in ps}==members[day]
        actual=[];predicted=[];probs=[];windows=set()
        for p in ps:
            assert p['calculatedAt'][:10]>day and p['source'].endswith('CUTOFF_LT_'+day.replace('-',''))
            h=holdings[day,p['symbol']];windows.add((h['entry'],h['exit']))
            close(float(h['exitClose'])/float(h['entryClose'])*100-100,float(h['actualReturnPct']),1e-8)
            b=(levels[h['exit']]/levels[h['entry']]-1)*100;close(h['benchmarkReturnPct'],b)
            actual.append(float(h['actualReturnPct'])-b);predicted.append(p['expectedExcessReturnPct']);probs.append(p['calibratedUpsideProbabilityPct']/100)
            assert 0<=p['calibratedUpsideProbabilityPct']<=100 and p['excessP10Pct']<=p['excessP50Pct']<=p['excessP90Pct']
        assert len(windows)==1
        m=model_info[day,model];metrics=m['forecastMetrics']
        close(metrics['maePct'],mean(abs(p-a) for p,a in zip(predicted,actual)))
        close(metrics['brierScore'],mean((p-(a>0))**2 for p,a in zip(probs,actual)),1e-8)
        close(metrics['directionalAccuracyPct'],100*mean((p>0 and a>0) or (p<0 and a<0) or (p==a==0) for p,a in zip(predicted,actual)))
        close(metrics['rankIc'],correlation(ranks(predicted),ranks(actual)),1e-8)
        basket=sorted(ps,key=lambda p:p['expectedExcessReturnPct'],reverse=True)[:20]
        gross=mean(float(holdings[day,p['symbol']]['actualReturnPct']) for p in basket)
        close(m['portfolio']['grossReturnPct'],gross);close(m['portfolio']['netReturnPct'],gross-.2)
        entry,exit_=next(iter(windows))
        periods[model].append(dict(decision=day,entry=entry,exit=exit_,grossReturnPct=gross,netReturnPct=gross-.2,benchmarkReturnPct=b,selected=[p['symbol'] for p in basket]))
    java=read_json(ROOT/'combined-java-accounting.json');assert java['modelDateGroups']==152
    oldjava=read_json(OLD/'combined-java-accounting.json');combined={}
    for model,rows in periods.items():
        assert len(rows)==19
        used=[];last=None;wealth=[1.,1.,1.]
        for p in sorted(rows,key=lambda p:p['decision']):
            if last and p['decision']<last:continue
            used.append(p);last=p['exit']
            for i,f in enumerate(('grossReturnPct','netReturnPct','benchmarkReturnPct')):wealth[i]*=1+p[f]/100
        assert len(used)==14==java['combined'][model]['periodCount']
        values={f:(wealth[i]-1)*100 for i,f in enumerate(('grossReturnPct','netReturnPct','benchmarkReturnPct'))}
        for f,v in values.items():close(java['combined'][model][f],v)
        close(java['combined'][model]['transactionCostPct'],2.8)
        if model!=V9:assert java['combined'][model]==oldjava['combined'][model],'Frozen V2-V8 accounting changed'
        combined[model]=dict(**values,periodCount=14,decisionDates=19,periods=used)
    result=dict(status='PASS',classification='V9_PRIOR_REPORTED_SHARES_RESEARCH_NOT_OFFICIAL_PIT',databaseWrites=0,promotionAllowed=False,
        recoveredMarketKeys=59,newWholePopulationDates=11,newV9Predictions=1111,unchangedPriorKnownV9Predictions=1052,
        allModelsWholePopulationDates=19,allModelsNonoverlapPeriods=14,totalDistinctAcceptedPredictions=15352,
        futureTargetLabelInvariant=True,allNewDates=[d['decision'] for d in normal['dates']],shareSourceCounts=dict(sources),oldestShareMeasurementAgeDays=max_age,
        combined=combined,limitations=normal['limitations'],exclusions={'2025-06-30':'ANSS acquisition-crossing holding remains excluded',
            '2025-10-31':'SOLS date conflict and <80 prior candles; no member dropped','2026-03-13':'14 missing predictor rows; no partial population'},
        sourceExperimentHashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'result.json',ROOT/'combined-java-accounting.json',OLD/'result.json')})
    (ROOT/'independent-verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('combined','limitations','sourceExperimentHashes')},indent=2))
    print('V9 combined',json.dumps({k:v for k,v in combined[V9].items() if k!='periods'}))

if __name__=='__main__':verify()
