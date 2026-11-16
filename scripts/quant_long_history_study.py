"""Free-source long-history preparation and sealed chronological calibration comparison."""
import argparse
import csv
import hashlib
import json
import math
from datetime import datetime,timezone
from pathlib import Path

import numpy as np
from quant_strict_unused_study import query
from quant_history_usage import window_usage,require_unused_windows
from quant_20d_error_research import metrics

ROOT=Path('tmp/quant-long-oof-study-20261031-v1')
DATA=Path('tmp/quant-long-history-free-20261031-v1')
INPUT=Path('tmp/quant-long-history-inputs-20261031-v1')
V9='EXPECTED_RETURN_V9_CORRECTED_20261017'
ROSTER=sorted('MMM AXP AMGN AAPL BA CAT CVX CSCO KO DIS DOW GS HD HON IBM INTC JNJ JPM MCD MRK MSFT NKE PG CRM TRV UNH VZ V WBA WMT'.split())
TEST=['2020-09-30','2020-10-30'];CUTOFF=TEST[0]


def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def load(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def save(path,value):
    with Path(path).open('x',encoding='utf-8') as f:json.dump(value,f,indent=2,allow_nan=False)


def mature(row,day):
    return bool(row.get('signalDate') and row.get('exitTradeDate') and row['signalDate']<day and row['exitTradeDate']<day)


def assert_new_windows(previous,targets):
    audit={t['decision']:dict(entry=t['entry'],exit=t['exit'],
           priorAnyHorizonLabelWindows=window_usage(previous,t['entry'],t['exit'],None)) for t in targets}
    require_unused_windows(audit)
    return audit


def residual_shift(training,predictions,cutoff,minimum=200):
    labels={};preds={}
    for row in training:
        if row.get('horizonDays')!=20 or not mature(row,cutoff):continue
        key=(row['signalDate'],row['symbol'],20)
        if key in labels:raise ValueError('Duplicate training label')
        labels[key]=row
    for p in predictions:
        if p.get('modelVersion')!=V9 or p.get('horizonDays')!=20:continue
        key=(p['signalDate'],p['symbol'],20)
        if key in preds:raise ValueError('Duplicate OOF forecast')
        if key in labels:preds[key]=p
    if len(preds)<minimum:raise ValueError('Insufficient matured genuine OOF forecasts')
    residual={t:[] for t in ['Absolute','Excess']}
    for k,p in preds.items():
        r=labels[k]
        for target in residual:
            actual=float(r['forwardReturnPct'])-(float(r['benchmarkReturnPct']) if target=='Excess' else 0)
            expected=float(p['expectedReturnPct' if target=='Absolute' else 'expectedExcessReturnPct'])
            if not math.isfinite(actual) or not math.isfinite(expected):raise ValueError('Nonfinite OOF pair')
            residual[target].append(actual-expected)
    return {t:float(np.median(values)) for t,values in residual.items()}


def prepare():
    ROOT.mkdir(parents=True,exist_ok=True)
    if (ROOT/'protocol.json').exists():raise ValueError('Already sealed; refuse reselection')
    audit=load(DATA/'cohort-source-audit.json')
    if len(audit['datasets'])!=30:raise ValueError('Missing source identity audit')
    hashes={str(DATA/'cohort-source-audit.json'):sha(DATA/'cohort-source-audit.json')}
    benchmark=[]
    for row in csv.DictReader((INPUT/'DJIA-2019-2020.csv').read_text(encoding='utf-8').splitlines()):
        if row['DJIA'] not in ('','.'):
            benchmark.append(dict(day=row['observation_date'],level=row['DJIA'],source='FRED_DJIA_SP_DOW_JONES_PRICE_INDEX'))
    calendar=[b['day'] for b in benchmark]
    if len(calendar)!=len(set(calendar)):raise ValueError('Duplicate benchmark date')
    train=[d for d in calendar if '2019-07-31'<=d<='2020-08-28']
    prices={};events={}
    for symbol in ROSTER:
        path=DATA/(symbol+'-zenodo-2019-2020.csv');hashes[str(path)]=sha(path)
        rows=list(csv.DictReader(path.read_text(encoding='utf-8-sig').splitlines()))
        if any(r['symbol']!=symbol or r['currency']!='USD' for r in rows):raise ValueError('Price issuer/unit mismatch')
        series=[dict(day=r['date'],close=r['close'],source=r['provider']+'_COHERENT_CLOSE_NOT_ADJCLOSE')
                for r in rows if symbol!='DOW' or r['date']>='2019-04-02']
        days=[p['day'] for p in series]
        if len(days)!=len(set(days)) or any(not math.isfinite(float(p['close'])) or float(p['close'])<=0 for p in series):raise ValueError('Duplicate/invalid source price')
        expected=[d for d in calendar if d>=days[0]]
        if days!=expected:raise ValueError('Incomplete original calendar '+symbol)
        for day in train+TEST:
            if sum(d<=day for d in days)<80:raise ValueError('Insufficient actual history '+symbol+day)
            held=[d for d in calendar if d>day][:21]
            if len(held)!=21 or not set(held)<=set(days):raise ValueError('Incomplete holding calendar '+symbol+day)
            if day in train and not held[-1]<CUTOFF:raise ValueError('Training not mature at fixed cutoff')
        prices[symbol]=series
        archive=Path('tmp/pit-acquisition/actions/raw')/(symbol+'.json')
        if archive.exists():
            hashes[str(archive)]=sha(archive);original=load(archive)['chart']['result'][0]
            if original['meta']['symbol']!=symbol:raise ValueError('Adjustment issuer mismatch')
            supplied=[]
            for event in original.get('events',{}).get('splits',{}).values():
                d=datetime.fromtimestamp(event['date'],timezone.utc).strftime('%Y-%m-%d')
                if d<='2024-06-26':
                    supplied.append(dict(day=d,numerator=str(event['numerator']),denominator=str(event['denominator']),
                       kind='SPLIT' if symbol in ('AAPL','WMT') else 'SEPARATION_RESEARCH',
                       sourcePath=str(archive),sourceSha256=sha(archive),sourceRatio=event['splitRatio']))
            events[symbol]=supplied
        else:events[symbol]=[]
    old=query('SELECT signal_date AS signalDate,entry_trade_date AS entryTradeDate,exit_trade_date AS exitTradeDate,horizon_days AS horizonDays FROM stock_backtest_result_v2')
    old=[dict(r,horizonDays=int(r['horizonDays'])) for r in old]
    exposures=[]
    for path in sorted(Path('output').glob('sp500*/**/manifest.json')):
        meta=load(path)
        if isinstance(meta,dict) and meta.get('start') and meta.get('end'):
            hashes[str(path)]=sha(path)
            old.append(dict(signalDate=meta['start'],entryTradeDate=meta['start'],exitTradeDate=meta['end'],horizonDays=0))
            exposures.append(str(path))
    if not exposures:raise ValueError('Missing file-only research inventory')
    for path in [Path('tmp/quant-new-period-20261031-v1/replay-v8-complete/result.json'),
                 Path('tmp/quant-strict-unused-2021-20261031-v1/replay/result.json')]:
        r=load(path);hashes[str(path)]=sha(path)
        rows=r.get('trainingRows',[])
        if isinstance(rows,list):old.extend({k:row[k] for k in ['signalDate','entryTradeDate','exitTradeDate','horizonDays']} for row in rows)
        old.extend(dict(signalDate=d['decision'],entryTradeDate=p['entry'],exitTradeDate=p['exit'],horizonDays=20) for d in r['dates'] for p in d['pairs'])
    targets=[]
    for day in TEST:
        held=[d for d in calendar if d>day][:21]
        targets.append(dict(decision=day,entry=held[0],exit=held[-1]))
    exposure=assert_new_windows(old,targets)
    save(ROOT/'prior-label-exposure.json',dict(rows=len(old),fileOnlyConservativeCalendars=exposures,
         earliestPriorEntry=min(r['entryTradeDate'] for r in old),testIntervals=exposure,
         priorWindowFingerprint=hashlib.sha256(json.dumps(old,sort_keys=True).encode()).hexdigest(),strictUnusedPassed=True))
    for p in [INPUT/'DJIA-2019-2020.csv',INPUT/'AAPL-split-primary-20200730.html',
              Path('tmp/quant-strict-dow-inputs-20261031-v1/WBA-companyfacts.json'),
              Path('tmp/quant-strict-dow-inputs-20261031-v1/WBA-financials.json'),
              Path('tmp/quant-strict-dow-inputs-20261031-v1/WBA-shares.json'),
              Path('scripts/quant_long_history_study.py'),Path('scripts/QuantLongHistoryReplay.java'),
              Path('scripts/quant_history_usage.py'),Path('scripts/quant_strict_unused_study.py')]:hashes[str(p)]=sha(p)
    value=dict(population=ROSTER,trainingDecisions=train,testDecisions=TEST,fixedCutoff=CUTOFF,
        namespace='DOW30_LONG_OOF_RESEARCH_20261031',
        pricePolicy='COHERENT_PROVIDER_SPLIT_ADJUSTED_CLOSE_WITH_ASOF_NOMINAL_FEATURES',
        providerSnapshotDate='2024-06-26',pricesBySymbol=prices,providerEventsBySymbol=events,
        financialsBySymbol={'WBA':load('tmp/quant-strict-dow-inputs-20261031-v1/WBA-financials.json')},
        sharesBySymbol={'WBA':load('tmp/quant-strict-dow-inputs-20261031-v1/WBA-shares.json')},
        benchmark=benchmark,inputHashes=hashes,classification='FIXED_TARGET_COHORT_FREE_AUTHOR_ARCHIVES_RESEARCH_NOT_PIT')
    save(ROOT/'inputs.json',value)
    protocol=dict(sealedAtUtc=datetime.now(timezone.utc).isoformat(),inputsPath=str(ROOT/'inputs.json'),
        inputsSha256=sha(ROOT/'inputs.json'),trainingDecisions=train,trainingRows=len(train)*30,
        testDecisions=TEST,testIntervals=targets,fixedCutoff=CUTOFF,population=ROSTER,
        candidateNames=['long_train_v9_no_oof','long_train_v9_chronological_oof','oof_median_residual_point_correction'],
        references=['zero_return','train_pooled_median'],minimumTrainingRows=300,functionalCalibrationMinimumPerBucket=200,
        readinessRule='Both dates full30V2-V9, ALL targetV6/V7errors nonnull, actual matching maturedV5/V6OOFbucket>=200. Else no target outcomes/MAE.',
        residualRule='Fixed maturedV9OOFmedian(actual-prediction), absolute/excess separately, min200; applied to chronologicalOOF V9point only; no fit on target labels.',
        promotionAllowed=False,noHyperparameterSearch=True,noHoldoutRollIn=True,inputHashes=hashes,
        exposureAuditSha256=sha(ROOT/'prior-label-exposure.json'),
        limitations=['Fixed postAug2020cohort used retrospectively; some stocks not members in training. Not certified PIT/receipt/license or prospective study.',
          'Author-deposited Close has undocumented upstream vendor; original hash/calendar/overlap verified but not redistribution rights or executable-price guarantee.',
          'Provider separation ratios may be rounded; source-consistent labels and asofEPS/price/share transforms are research reconstruction.',
          'Daily overlapping windows increase bucket coverage, not independent sample count; only2testdates.',
          'Unknown sectors, analyst/news/institution/macro follow actual missing-policy; point residual arm is not a complete probability/interval model contract.'])
    save(ROOT/'protocol.json',protocol)
    print(json.dumps(dict(protocol=str(ROOT/'protocol.json'),sha256=sha(ROOT/'protocol.json'),trainingDates=len(train),trainingRows=30*len(train),
       oldExposureRows=len(old),overlapRows={d:exposure[d]['priorAnyHorizonLabelWindows']['overlapRows'] for d in TEST})))


def evaluate(replay):
    protocol=load(ROOT/'protocol.json');report=load(replay)
    if report['protocolSha256']!=sha(ROOT/'protocol.json'):raise ValueError('Unsealed replay')
    for p,h in protocol['inputHashes'].items():
        if sha(p)!=h:raise ValueError('Sealed input/code drift '+p)
    readiness=load(Path(replay).parent/'readiness.json')
    if not readiness.get('ready',readiness.get('allReady',False)):raise ValueError('Functional calibration not ready: no scoring')
    train=report['trainingRows'];shift=residual_shift(train,report['oofPredictions'],CUTOFF)
    if len(train)!=protocol['trainingRows'] or any(not mature(r,CUTOFF) for r in train):raise ValueError('Training denominator/purge mismatch')
    actual_pairs=[];medians={}
    for t in ['Absolute','Excess']:
        medians[t]=float(np.median([float(r['forwardReturnPct'])-(float(r['benchmarkReturnPct']) if t=='Excess' else 0) for r in train]))
    for day in report['dates']:
        if day['decision'] not in TEST or sorted(p['symbol'] for p in day['pairs'])!=ROSTER:raise ValueError('Target cohort mismatch')
        for pair in day['pairs']:
            p=dict(pair,decision=day['decision'])
            for t in shift:
                p['residual'+t]=float(p['calibrated'+t])+shift[t]
                p['median'+t]=medians[t]
            actual_pairs.append(p)
    if len(actual_pairs)!=60 or len({(p['decision'],p['symbol']) for p in actual_pairs})!=60:raise ValueError('Wrong test denominator')
    summaries={}
    for label,rows in [('all',actual_pairs)]+[(d,[p for p in actual_pairs if p['decision']==d]) for d in TEST]:
        summaries[label]={}
        for t in ['Absolute','Excess']:
            actual=[float(p['actual'])-(float(p['benchmark']) if t=='Excess' else 0) for p in rows]
            summaries[label][t]={prefix:metrics([float(p[prefix+t]) for p in rows],actual) for prefix in ['baseline','calibrated','residual','median']}
            summaries[label][t]['zero']=metrics([0]*len(rows),actual)
    value=dict(protocolSha256=sha(ROOT/'protocol.json'),replaySha256=sha(replay),outcomes=60,
       trainingRows=len(train),residualShiftLearnedOnlyFromOOF=shift,metrics=summaries,pairs=actual_pairs,
       promotionAllowed=False,readiness=readiness,limitations=protocol['limitations'])
    save(ROOT/'evaluation.json',value)
    print(json.dumps({k:v for k,v in value.items() if k in ['outcomes','trainingRows','residualShiftLearnedOnlyFromOOF','metrics']},indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['prepare','evaluate']);p.add_argument('--replay');a=p.parse_args()
    prepare() if a.mode=='prepare' else evaluate(a.replay)
