"""Independent March13 reconstruction/metric/nonoverlap verification, no DB access."""
import hashlib,json
from collections import defaultdict
from datetime import date
from decimal import Decimal
from pathlib import Path
from statistics import mean
from verify_quant_team_models import read_json,table,close,ranks,correlation

ROOT=Path('tmp/quant-march-model-v3-final-20261031');DAY='2026-03-13';INDEX='NASDAQ100_MACRO_FREE_20261018'
def verify():
    normal=read_json(ROOT/'result.json');counter=read_json('tmp/quant-march-model-v3-perturbed-20261031/result.json');inputs=read_json('tmp/quant-march-inputs-20261031/inputs-v3.json')
    for r in (normal,counter,inputs):
        assert r['databaseWrites']==0 and r['promotionAllowed'] is False
        for p,h in r['inputHashes'].items():assert hashlib.sha256(Path(p).read_bytes()).hexdigest()==h,p
    key=lambda p:(p['signalDate'],p['modelVersion'],p['symbol'],p['horizonDays'])
    canonical=lambda p:{k:v for k,v in p.items() if k!='calculatedAt'}
    a={key(p):canonical(p) for p in normal['predictions']};b={key(p):canonical(p) for p in counter['predictions']}
    assert len(a)==len(normal['predictions'])==len(b)==808 and a==b,'Future label changed forecast'
    assert not normal['targetOutcomePerturbation'] and counter['targetOutcomePerturbation']
    assert normal['sourceOutcomeIsolationVerified'] and counter['sourceOutcomeIsolationVerified']
    assert any(x['forecastMetrics']!=y['forecastMetrics'] for x,y in zip(normal['dates'][0]['models'],counter['dates'][0]['models']))
    expected={'ALNY','ARM','ASML','CCEP','FER','INSM','MELI','MRVL','MSTR','PDD','SHOP','TEAM','TRI','ZS'}
    assert set(inputs['financialsBySymbol'])==expected
    for symbol,rows in inputs['financialsBySymbol'].items():
        assert any(r['fiscalPeriod']=='FY' for r in rows)
        for annual in (True,False):assert len({r['currency'] for r in rows if (r['fiscalPeriod']=='FY')==annual})<=1,'Mixed paired-period currency'
        for r in rows:
            assert r['symbol']==symbol and r['filedAt']<DAY and r['endDate']<DAY
            duration=(date.fromisoformat(r['endDate'])-date.fromisoformat(r['startDate'])).days+1
            assert 330<=duration<=380 if r['fiscalPeriod']=='FY' else 70<=duration<=110
            policy=json.loads(r['rawJson'])['valuationUnitPolicy']
            if r['currency']!='USD':assert r['epsDiluted'] is None and not policy['fxAssumed']
            elif policy['reportedEps'] is not None:
                assert Decimal(str(r['epsDiluted']))==Decimal(policy['reportedEps'])*(4 if symbol=='PDD' else 1)
    assert any(r['form']=='40-F' and r['endDate']=='2025-12-31' for r in inputs['financialsBySymbol']['TRI'])
    assert all(r['fiscalPeriod']=='FY' for s in ('CCEP','FER') for r in inputs['financialsBySymbol'][s])
    for r in normal['dates'][0]['recoveredInputs']:
        assert r['symbol'] in expected and r['priceObservationsBeforeDecision']>=80 and r['financialRowCount']>=1
        if r['symbol']=='PDD':assert r['signal']['growthScore']==50,'Incomparable prior currency must use missing-factor policy'
    share=inputs['teamShareEvidence'];assert share['filedDate']<DAY and share['shareMeasurementDate']<DAY and share['classAShares']+share['classBShares']==share['totalCommonShares']
    tm=inputs['teamMarket'];assert Decimal(tm['closePrice'])*share['totalCommonShares']==Decimal(tm['marketCapUsd'])
    members=defaultdict(set)
    for r in table('tmp/quant-complete-window-members-20261018.tsv','\t'):
        if r['index_code']==INDEX:members[r['snapshot_date']].add(r['symbol'])
    holdings={}
    for r in table('tmp/quant-complete-windows-recovered-20261018/complete-holdings.csv')+table('tmp/quant-older-staged-20261030/holdings.csv'):
        if r['index']!=INDEX:continue
        k=(r['decision'],r['symbol']);assert k not in holdings;holdings[k]=r
    levels={r['trade_date']:float(r['benchmark_level']) for r in table('tmp/quant-complete-window-benchmarks-20261018.tsv','\t') if r['index_code']==INDEX and r['benchmark_level'] not in ('NULL','')}
    reports=[read_json(Path(p)/'result.json') for p in ('tmp/quant-older-model-recovery-v2-final-20261030','tmp/quant-extended-model-recovery-final-20261018','tmp/quant-team-model-recovery-final-20261018','tmp/quant-v9-historical-market-final-20261031')]+[normal]
    grouped=defaultdict(list);info={}
    for report in reports:
        for p,h in report['inputHashes'].items():assert hashlib.sha256(Path(p).read_bytes()).hexdigest()==h
        admitted={(d['decision'],m['model']):m for d in report['dates'] for m in d['models'] if m['fullPopulation'] and m['predictions']==101}
        for k,m in admitted.items():assert k not in info;info[k]=m
        for p in report['predictions']:
            if (p['signalDate'],p['modelVersion']) in admitted:grouped[p['signalDate'],p['modelVersion']].append(p)
    assert len(grouped)==160 and sum(map(len,grouped.values()))==16160
    periods=defaultdict(list)
    for (day,model),ps in grouped.items():
        assert len(ps)==101 and {p['symbol'] for p in ps}==members[day]
        actual=[];predicted=[];probs=[];windows=set()
        for p in ps:
            assert p['calculatedAt'][:10]>day and p['source'].endswith('CUTOFF_LT_'+day.replace('-',''))
            h=holdings[day,p['symbol']];windows.add((h['entry'],h['exit']))
            close(float(h['exitClose'])/float(h['entryClose'])*100-100,float(h['actualReturnPct']),1e-8)
            benchmark=(levels[h['exit']]/levels[h['entry']]-1)*100;close(h['benchmarkReturnPct'],benchmark)
            actual.append(float(h['actualReturnPct'])-benchmark);predicted.append(p['expectedExcessReturnPct']);probs.append(p['calibratedUpsideProbabilityPct']/100)
            assert 0<=probs[-1]<=1 and p['excessP10Pct']<=p['excessP50Pct']<=p['excessP90Pct']
        assert len(windows)==1
        m=info[day,model];metrics=m['forecastMetrics']
        close(metrics['maePct'],mean(abs(p-y) for p,y in zip(predicted,actual)))
        close(metrics['brierScore'],mean((p-(y>0))**2 for p,y in zip(probs,actual)),1e-8)
        close(metrics['directionalAccuracyPct'],100*mean((p>0 and y>0) or (p<0 and y<0) or (p==y==0) for p,y in zip(predicted,actual)))
        close(metrics['rankIc'],correlation(ranks(predicted),ranks(actual)),1e-8)
        basket=sorted(ps,key=lambda p:p['expectedExcessReturnPct'],reverse=True)[:20]
        gross=mean(float(holdings[day,p['symbol']]['actualReturnPct']) for p in basket)
        close(m['portfolio']['grossReturnPct'],gross);close(m['portfolio']['netReturnPct'],gross-.2)
        entry,exit_=next(iter(windows));periods[model].append(dict(decision=day,entry=entry,exit=exit_,grossReturnPct=gross,netReturnPct=gross-.2,benchmarkReturnPct=benchmark))
    java=read_json(ROOT/'combined-java-accounting.json');old=read_json('tmp/quant-v9-historical-market-final-20261031/combined-java-accounting.json');assert java['modelDateGroups']==160
    combined={}
    for model,rows in periods.items():
        assert len(rows)==20
        used=[];excluded=[];last=None;wealth=[1.,1.,1.]
        for p in sorted(rows,key=lambda p:p['decision']):
            if last and p['decision']<last:excluded.append(p['decision']);continue
            used.append(p);last=p['exit']
            for i,f in enumerate(('grossReturnPct','netReturnPct','benchmarkReturnPct')):wealth[i]*=1+p[f]/100
        assert len(used)==14==java['combined'][model]['periodCount'] and DAY in excluded
        for i,f in enumerate(('grossReturnPct','netReturnPct','benchmarkReturnPct')):close(java['combined'][model][f],(wealth[i]-1)*100)
        assert java['combined'][model]==old['combined'][model],'Existing cash-to-cash accounting changed'
        combined[model]=dict(decisionDates=20,nonoverlapPeriods=14,overlapExcludedDates=excluded,netReturnPct=(wealth[1]-1)*100,benchmarkReturnPct=(wealth[2]-1)*100)
    result=dict(status='PASS',classification='MARCH_INPUT_RECOVERY_RESEARCH_NOT_OFFICIAL_PIT',newDecision=DAY,newForecasts=808,
        wholePopulationMembers=101,recoveredInputSymbols=sorted(expected),allDecisionDates=20,allNonoverlapPeriods=14,
        combinedAcceptedForecasts=16160,modelDateGroups=160,futureTargetLabelInvariant=True,existingNonoverlapAccountingUnchanged=True,
        overlappingNewDecisionNotExtraPeriod=True,combined=combined,databaseWrites=0,promotionAllowed=False,
        limitations=normal['limitations'],solsExactIndexDatesResolved=False,solsMinimumHistoryWaived=False,
        hashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'result.json',ROOT/'combined-java-accounting.json',Path('tmp/quant-march-inputs-20261031/inputs-v3.json'))})
    (ROOT/'independent-verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('combined','limitations','hashes')},indent=2))

if __name__=='__main__':verify()
