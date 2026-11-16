"""EPS-only repair experiment; frozen weights, no previously scored60test outcomes."""
import argparse,collections,hashlib,json,math
from pathlib import Path
import numpy as np
from quant_v9_nested_training import summarize,block_interval

ROOT=Path('tmp/quant-eps-v9-training-20261101-v2')
OLD=Path('tmp/quant-long-verified-oof-study-20261031-v1')
PACK=Path('tmp/quant-eps-recovered-inputs-20261101-v2')
BASE_INPUT_SHA='aed4e7383ac3c30085e52075f1d78c8301108fd3120ce9fd99b0544185fb2b03'
BASE_FORECAST_SHA='f3fa85da248107034a2c52a0a22208c20c28788c55780a31613d3877375e0c0e'
FACTORS='integratedScore valuationScore qualityScore growthScore stabilityScore earningsScore analystScore newsScore momentumScore riskScore institutionScore dataQualityScore'.split()
def load(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(p,obj):
    with Path(p).open('x',encoding='utf-8') as f:json.dump(obj,f,indent=2,allow_nan=False)

def validate_repairs(old,new):
    if old.keys()!=new.keys():raise ValueError('Repair symbols changed')
    count=0
    for symbol in old:
        if len(old[symbol])!=len(new[symbol]):raise ValueError('Financial keys changed')
        for before,after in zip(old[symbol],new[symbol]):
            a={k:v for k,v in before.items() if k not in ['epsDiluted','epsUnit','rawJson']};b={k:v for k,v in after.items() if k not in ['epsDiluted','epsUnit','rawJson']}
            if a!=b:raise ValueError('Non-EPS field modified')
            if before.get('epsDiluted')==after.get('epsDiluted'):
                if before!=after:raise ValueError('Unchanged EPS record was mutated')
            else:
                if before.get('epsDiluted') is not None or after.get('epsUnit')!='USD/shares' or not math.isfinite(float(after['epsDiluted'])):raise ValueError('Nonnull/invalid EPS overwrite')
                count+=1
    return count

def join_pairs(old_train,new_train,old_oof,new_oof):
    key=lambda r:(r['signalDate'],r['symbol'],r['horizonDays'])
    tables=[]
    for rows in [old_train,new_train,old_oof,new_oof]:
        table={key(r):r for r in rows}
        if len(table)!=len(rows) or any(k[0]>'2020-08-28' or k[2]!=20 for k in table):raise ValueError('Duplicate/forbidden test key')
        tables.append(table)
    a,b,p,q=tables
    if a.keys()!=b.keys() or p.keys()!=q.keys() or not p.keys()<=a.keys():raise ValueError('Comparison cohorts differ')
    for k,r in a.items():
        for name in ['forwardReturnPct','benchmarkReturnPct']:
            if abs(float(r[name])-float(b[k][name]))>1e-12:raise ValueError('Return labels changed')
    result=[]
    for k in sorted(p):
        actual=float(a[k]['forwardReturnPct']);benchmark=float(a[k]['benchmarkReturnPct'])
        result.append(dict(day=k[0],symbol=k[1],y=[actual,actual-benchmark],base=[float(p[k]['expectedReturnPct']),float(p[k]['expectedExcessReturnPct'])],new=[float(q[k]['expectedReturnPct']),float(q[k]['expectedExcessReturnPct'])]))
    return result

def prepare():
    ROOT.mkdir(parents=True,exist_ok=True)
    if (ROOT/'protocol.json').exists():raise ValueError('Protocol already sealed')
    if sha(OLD/'inputs.json')!=BASE_INPUT_SHA or sha(OLD/'replay/forecast-only.json')!=BASE_FORECAST_SHA:raise ValueError('Baseline drift')
    old=load(PACK/'original-selected-standards.json');new=load(PACK/'eps-recovered-selected-standards.json')
    repairs=validate_repairs(old,new)
    if repairs!=355:raise ValueError('Expected355 independently proved repairs')
    value=load(OLD/'inputs.json');value['testDecisions']=[];value['namespace']='DOW30_EPS_RECOVERED_TRAINING_ONLY_RESEARCH_20261101'
    value['financialsBySymbol']={s:new.get(s,[]) for s in value['population']}
    value['pricesBySymbol']={s:[p for p in rows if p['day']<='2020-09-29'] for s,rows in value['pricesBySymbol'].items()}
    value['benchmark']=[p for p in value['benchmark'] if p['day']<='2020-09-29']
    paths=[OLD/'inputs.json',OLD/'replay/forecast-only.json',PACK/'original-selected-standards.json',PACK/'eps-recovered-selected-standards.json',PACK/'recovery-audit.json',Path(__file__),Path('scripts/test_quant_eps_training_recovery.py'),Path('scripts/QuantEpsRecoveredTrainingReplayV2.java'),Path('scripts/quant_v9_nested_training.py'),Path('src/main/java/com/kingyurina/demo/stock/SecEdgarService.java'),Path('src/main/java/com/kingyurina/demo/stock/SecFinancialStandardService.java')]
    hashes={str(p):sha(p) for p in paths}
    for rows in new.values():
        for row in rows:
            recovery=json.loads(row['rawJson']).get('epsRecovery')
            if recovery:
                p=Path(recovery['sourcePath']);h=sha(p)
                if h!=recovery['sourceSha256']:raise ValueError('EPS original archive changed')
                hashes[str(p)]=h
    value['inputHashes']=hashes
    save(ROOT/'inputs.json',value)
    save(ROOT/'protocol.json',dict(inputsPath=str(ROOT/'inputs.json'),inputsSha256=sha(ROOT/'inputs.json'),inputHashes=hashes,
      policy='EPS_ONLY_EXACT_PRIOR_SOURCE_RECOVERY_V1',recoveredSelectedKeys=repairs,cohort=26401,trainingDates=902,
      weightsChanged=False,old60TargetsUsed=False,evaluation='Same2018Q2..2020Q3partial609dates17904rows as previousinternal study; all870OOFdates ancillary',
      primaryMetrics=['dateBalancedAbsoluteMAE','dateBalancedExcessMAE'],bootstrap=dict(blockDates=21,draws=1000,seed=20261101),
      classification='INTERNAL_DATA_REPAIR_EXPERIMENT_NOT_NEW_HOLDOUT',promotionAllowed=False))
    print(json.dumps(dict(protocolSha256=sha(ROOT/'protocol.json'),repairs=repairs,namespace=value['namespace'])))

def compare(replay):
    protocol=load(ROOT/'protocol.json')
    for p,h in protocol['inputHashes'].items():
        if sha(p)!=h:raise ValueError('Sealed source drift '+p)
    baseline=load(OLD/'replay/forecast-only.json');report=load(replay)
    if report['protocolSha256']!=sha(ROOT/'protocol.json') or report['heldoutOutcomesComputed'] or report['databaseWrites']!=0:raise ValueError('Unexpected replay contract')
    pairs=join_pairs(baseline['trainingRows'],report['trainingRows'],baseline['oofPredictions'],report['oofPredictions'])
    primary=[r for r in pairs if '2018-04-01'<=r['day']<='2020-08-28']
    if len(pairs)!=25473 or len(primary)!=17904 or len(set(r['day'] for r in primary))!=609:raise ValueError('Fixed primary denominator changed')
    scopes={}
    for name,rows in [('primary',primary),('all_oof_ancillary',pairs)]:
        scopes[name]=dict(rows=len(rows),dates=len(set(r['day'] for r in rows)),before=summarize(rows,np.array([r['base'] for r in rows])),after=summarize(rows,np.array([r['new'] for r in rows])))
    quarters={}
    for r in primary:quarters.setdefault(r['day'][:4]+'Q'+str((int(r['day'][5:7])-1)//3+1),[]).append(r)
    for name,rows in quarters.items():scopes[name]=dict(rows=len(rows),before=summarize(rows,np.array([r['base'] for r in rows])),after=summarize(rows,np.array([r['new'] for r in rows])))
    before={(r['signalDate'],r['symbol']):r for r in baseline['trainingRows']}
    changed={f:sum(before[(r['signalDate'],r['symbol'])].get(f)!=r.get(f) for r in report['trainingRows']) for f in FACTORS}
    old_fin=load(OLD/'replay/financial-input-audit.json');coverage={}
    for op in ['findLatestAnnualAsOf','findLatestQuarterAsOf','findPreviousAnnualAsOf','findPreviousQuarterAsOf']:
        keys=[k for k in report['financialReads'] if k.endswith('/'+op)];get=lambda d,k:d.get(k,{})
        coverage[op]=dict(selections=len(keys),beforeEps=sum(get(old_fin,k).get('epsDiluted') is not None for k in keys),afterEps=sum(get(report['financialReads'],k).get('epsDiluted') is not None for k in keys))
    value=dict(protocolSha256=sha(ROOT/'protocol.json'),replaySha256=sha(replay),comparison=scopes,bootstrap=block_interval(primary,np.array([r['new'] for r in primary]),np.array([r['base'] for r in primary])),changedScores=changed,actualSelectedEpsCoverage=coverage,pairs=pairs,
       labelsAndCohortsIdentical=True,weightsChanged=False,old60TargetsUsed=False,promotionAllowed=False,
       limits=['Internal repeatedly used training research, not untouched/prospective or guaranteed generalization.','Fixed-cohort survivorship, missingnonEPSfinancial/news/sector data and overlapping20Dlabels remain.','Data-onlyEPSrepair uses sourceoriginalfiling/asof/split units; accuracyimprovement is separatefromdatacorrectness.'])
    save(ROOT/'comparison.json',value)
    print(json.dumps({k:value[k] for k in ['comparison','bootstrap','changedScores','actualSelectedEpsCoverage']},indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['prepare','compare']);p.add_argument('--replay');a=p.parse_args()
    prepare() if a.mode=='prepare' else compare(a.replay)
