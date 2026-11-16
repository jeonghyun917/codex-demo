"""Prespecified, training-only EPS feature research. No DB or old60 outcome reader."""
import argparse
import collections
import hashlib
import json
import math
from datetime import date
from pathlib import Path
import numpy as np
import quant_v9_nested_training as core

ROOT=Path('tmp/quant-v9-eps-growth-study-20261101-v1')
SOURCE=Path('tmp/quant-eps-v9-training-20261101-v2')
PANEL=Path('tmp/quant-eps-nested-training-20261101-v1/panel.json')
INPUT_SHA='5eb790770bfa3b1c9188de10c484c044cfa3418583c1f5958c81317eedfd3af9'
FINANCIAL_SHA='c6e067e21a20eaace7b92ff6ac400204a136b6df968abc7858ce2c9b9ecd33dd'
CONTEXT=['v9_absolute','v9_excess','market_ret20','market_vol20']
GROWTH=[f'{kind}_{suffix}' for kind in ['annual','quarter'] for suffix in ['yoy_symmetric','yoy_positive','yoy_missing','positive_growth_missing','loss_to_profit','profit_to_loss']]
ACCEL=['quarter_yoy_acceleration','quarter_yoy_acceleration_missing','quarter_qoq_symmetric','quarter_qoq_missing']
CONFIRM=['revenue_yoy_symmetric','revenue_yoy_missing','net_margin_yoy_delta','net_margin_yoy_missing','eps_momentum_confirmation','eps_momentum_confirmation_missing']
FEATURES=CONTEXT+GROWTH+ACCEL+CONFIRM
GROUPS={'context':CONTEXT,'growth':GROWTH,'acceleration':ACCEL,'confirmation':CONFIRM}
CONFIGS=[dict(name='v9',columns=[]),dict(name='context',columns=CONTEXT),dict(name='eps_growth',columns=CONTEXT+GROWTH),
         dict(name='eps_acceleration',columns=CONTEXT+GROWTH+ACCEL),dict(name='eps_confirmation',columns=FEATURES)]
NAMES=[c['name'] for c in CONFIGS]
DIS_RULE={'legacyCik':'1001039','validUntilExclusive':'2019-03-20'}
validate_panel=core.validate_panel

def sha(p):
    h=hashlib.sha256()
    with Path(p).open('rb') as f:
        for chunk in iter(lambda:f.read(1048576),b''):h.update(chunk)
    return h.hexdigest()

def load(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def save(p,v):
    with Path(p).open('x',encoding='utf-8') as f:json.dump(v,f,indent=2,allow_nan=False,default=lambda x:x.tolist() if isinstance(x,np.ndarray) else x.item())
def number(v):return None if v is None else float(v) if math.isfinite(float(v)) else None
def days(a,b):return (date.fromisoformat(a)-date.fromisoformat(b)).days
def cik(v):return str(v or '').lstrip('0')
def natural(f):return tuple(f.get(n) for n in ['symbol','cik','accessionNumber','fiscalYear','fiscalPeriod','startDate','endDate'])

def issuer_allowed(symbol,issuer,day,rule):
    return symbol!='DIS' or (cik(issuer)==cik(rule['legacyCik']) if day<rule['validUntilExclusive'] else cik(issuer)=='1744489')

def valid_period(f,annual):
    if not f or not f.get('startDate') or not f.get('endDate'):return False
    kind=f.get('fiscalPeriod','');actual=days(f['endDate'],f['startDate'])+1
    return (kind=='FY' if annual else kind in ['Q1','Q2','Q3','Q4']) and (330<=actual<=380 if annual else 70<=actual<=110) and (f.get('periodDays') is None or int(f['periodDays'])==actual)

def select_latest(rows,day,annual):
    selected=[f for f in rows if valid_period(f,annual) and f.get('filedAt') and f['filedAt']<day and f['endDate']<day]
    # Match the established snapshot ordering; do not prefer rows merely because EPS is populated.
    def order(f):
        preference=int(f.get('form') in ['10-K','10-K/A']) if annual else int(f.get('assets') is not None or f.get('equity') is not None)
        return f['endDate'],preference,f['filedAt'],int(f.get('id') or -1)
    return max(selected,key=order,default=None)

def comparison(rows,current,day,mode):
    if current is None:return None
    annual=current['fiscalPeriod']=='FY';available=[]
    for f in rows:
        if not valid_period(f,annual) or not f.get('filedAt') or f['filedAt']>=day or f['endDate']>=current['endDate']:continue
        if cik(f.get('cik'))!=cik(current.get('cik')) or not cik(f.get('cik')) or f.get('currency')!=current.get('currency'):continue
        gap=days(current['endDate'],f['endDate'])
        if mode=='yoy':
            if f['fiscalPeriod']!=current['fiscalPeriod'] or not 330<=gap<=380 or not 330<=days(current['startDate'],f['startDate'])<=380 or abs(days(current['endDate'],current['startDate'])-days(f['endDate'],f['startDate']))>14:continue
        elif mode=='adjacent':
            if annual or not 70<=gap<=110 or int(current['fiscalPeriod'][1])!=int(f['fiscalPeriod'][1])%4+1 or days(current['startDate'],f['endDate'])!=1:continue
        else:raise ValueError('Unknown comparison mode')
        available.append(f)
    return select_latest(available,day,annual)

def eps_asof(f,events,day):
    if f is None or f.get('currency')!='USD' or f.get('epsUnit')!='USD/shares' or f.get('form') not in ['10-K','10-K/A','10-Q','10-Q/A'] or not f.get('filedAt') or f['filedAt']>=day:return None
    value=number(f.get('epsDiluted'))
    if value is None:return None
    for event in events:
        if event['kind']!='SPLIT' or event['day']>day:continue
        if event['day']==f['filedAt']:return None
        if f['filedAt']<event['day']:
            ratio=float(event['numerator'])/float(event['denominator'])
            if not math.isfinite(ratio) or ratio<=0:raise ValueError('Invalid split ratio')
            value/=ratio
    return value

def paired_eps(a,b,events,day):
    if not a or not b or not a.get('_epsConcept') or a['_epsConcept']!=b.get('_epsConcept') or a.get('epsUnit')!=b.get('epsUnit'):return None
    x,y=eps_asof(a,events,day),eps_asof(b,events,day)
    return None if x is None or y is None else (x,y)

def symmetric(a,b):return 0. if abs(a)+abs(b)==0 else 2*(a-b)/(abs(a)+abs(b))

def financial_features(rows,day,events,stock_ret20=0.):
    features={n:0. for n in GROWTH+ACCEL+CONFIRM};evidence={};pairs={}
    for kind,annual,expiry in [('annual',True,550),('quarter',False,220)]:
        current=select_latest(rows,day,annual);prior=comparison(rows,current,day,'yoy')
        fresh=current is not None and days(day,current['endDate'])<=expiry
        pair=paired_eps(current,prior,events,day) if fresh else None
        evidence[kind]=current;evidence[kind+'PriorYear']=prior;evidence[kind+'Status']='MISSING' if current is None else 'AVAILABLE' if fresh else 'STALE'
        features[kind+'_yoy_missing']=int(pair is None);features[kind+'_positive_growth_missing']=1
        if pair:
            x,y=pair;features[kind+'_yoy_symmetric']=symmetric(x,y)
            if y>0 and x>=0:features[kind+'_yoy_positive']=float(np.clip(x/y-1,-2,2));features[kind+'_positive_growth_missing']=0
            features[kind+'_loss_to_profit']=int(y<0<x);features[kind+'_profit_to_loss']=int(x<0<y)
        pairs[kind]=pair
    current=evidence['quarter'];previous=comparison(rows,current,day,'adjacent');previous_year=comparison(rows,previous,day,'yoy')
    evidence['previousQuarter']=previous;evidence['previousQuarterPriorYear']=previous_year
    fresh=evidence['quarterStatus']=='AVAILABLE'
    adjacent=paired_eps(current,previous,events,day) if fresh else None
    previous_yoy=paired_eps(previous,previous_year,events,day) if fresh else None
    features['quarter_qoq_missing']=int(adjacent is None)
    if adjacent:features['quarter_qoq_symmetric']=symmetric(*adjacent)
    features['quarter_yoy_acceleration_missing']=int(pairs['quarter'] is None or previous_yoy is None)
    if pairs['quarter'] is not None and previous_yoy is not None:features['quarter_yoy_acceleration']=float(np.clip(symmetric(*pairs['quarter'])-symmetric(*previous_yoy),-4,4))
    prior=evidence['quarterPriorYear']
    revenue_pair=None if not fresh or current is None or prior is None else (number(current.get('revenue')),number(prior.get('revenue')))
    revenue_ok=revenue_pair is not None and all(v is not None and v>0 for v in revenue_pair)
    features['revenue_yoy_missing']=int(not revenue_ok)
    if revenue_ok:features['revenue_yoy_symmetric']=symmetric(*revenue_pair)
    income_pair=None if not revenue_ok else (number(current.get('netIncome')),number(prior.get('netIncome')))
    margin_ok=income_pair is not None and all(v is not None for v in income_pair)
    features['net_margin_yoy_missing']=int(not margin_ok)
    if margin_ok:features['net_margin_yoy_delta']=float(np.clip(100*(income_pair[0]/revenue_pair[0]-income_pair[1]/revenue_pair[1]),-100,100))
    features['eps_momentum_confirmation_missing']=int(pairs['quarter'] is None)
    if pairs['quarter']:features['eps_momentum_confirmation']=float(np.clip(symmetric(*pairs['quarter'])*stock_ret20/100,-1,1))
    return features,evidence

def brief(f,events,day):
    if f is None:return None
    return {**{n:f.get(n) for n in ['symbol','cik','accessionNumber','startDate','endDate','filedAt','fiscalPeriod','epsDiluted','_epsConcept']},'decisionAsOfEps':eps_asof(f,events,day)}

def select_candidate(scores,names):
    if 'v9' not in scores or len(scores['v9'])!=3:return 'v9'
    bm=np.array([s['mae'] for s in scores['v9']]);br=np.array([s['rmse'] for s in scores['v9']]);eligible=[]
    for order,name in enumerate(names):
        if name=='v9' or len(scores.get(name,[]))!=3:continue
        m=np.array([s['mae'] for s in scores[name]]);r=np.array([s['rmse'] for s in scores[name]]);ratio=(m/np.maximum(bm,1e-12)).mean(axis=1)
        if np.all(m.mean(axis=0)<bm.mean(axis=0)-1e-12) and np.all(r.mean(axis=0)<=br.mean(axis=0)+1e-12) and sum(ratio<1-1e-12)>=2:eligible.append((float(ratio.mean()),order,name))
    return min(eligible)[2] if eligible else 'v9'

def frame(rows,start,cache):
    if start in cache:return cache[start]
    ti=core.mature_indices(rows,start);vi=[i for i,r in enumerate(rows) if start<=r['day']<core.qshift(start,1)]
    train=[rows[i] for i in ti];valid=[rows[i] for i in vi];base=np.asarray([r['base'] for r in valid],float);x=np.asarray([r['x'] for r in valid],float)
    ready=len(train)>=300 and len(set(r['day'] for r in train))>=20;predictions={};models={}
    for c in CONFIGS:
        name=c['name']
        if name=='v9' or not ready:pred=base.copy()
        else:
            cols=[FEATURES.index(n) for n in c['columns']];tx=np.asarray([r['x'] for r in train])[:,cols];residual=np.asarray([r['y'] for r in train])-np.asarray([r['base'] for r in train])
            model=core.fit_ridge(tx,residual,core.date_weights(train),100.)
            pred=base+.25*np.clip(core.predict(model,x[:,cols]),-2,2);models[name]={**model,'columns':c['columns']}
        predictions[name]=pred
    result=dict(start=start,trainRows=len(train),trainDates=len(set(r['day'] for r in train)),maxTrainExit=max((r['exit'] for r in train),default=None),ready=ready,indices=vi,predictions=predictions,models=models)
    cache[start]=result;return result

def run_fold(rows,start,cache):
    scores={name:[] for name in NAMES};inner=[]
    for offset in [-3,-2,-1]:
        f=frame(rows,core.qshift(start,offset),cache);ix=[j for j,i in enumerate(f['indices']) if rows[i]['exit']<start and rows[i]['day']<start];valid=[rows[f['indices'][j]] for j in ix]
        if not f['ready'] or not valid:return dict(selected='v9',inner=inner,scores={},outer=frame(rows,start,cache))
        for name in NAMES:
            metrics=core.summarize(valid,f['predictions'][name][ix]);scores[name].append({k:[metrics[h][v] for h in ['Absolute','Excess']] for k,v in [('mae','maePp'),('rmse','rmsePp')]})
        inner.append(dict(start=f['start'],trainRows=f['trainRows'],maxTrainExit=f['maxTrainExit'],selectionRows=len(valid),maxSelectionExit=max(r['exit'] for r in valid)))
    return dict(selected=select_candidate(scores,NAMES),inner=inner,scores=scores,outer=frame(rows,start,cache))

def source_history(inputs):
    result={};seen=set()
    for symbol,rows in inputs['financialsBySymbol'].items():
        result[symbol]=[]
        for original in rows:
            f=dict(original);key=natural(f)
            if key in seen:raise ValueError('Duplicate source standard key')
            seen.add(key);proof=json.loads(f['rawJson']).get('selectedFieldEvidence',{}).get('epsDiluted',{})
            if f.get('epsDiluted') is not None:
                if not proof.get('concept') or proof.get('startDate')!=f['startDate'] or proof.get('endDate')!=f['endDate'] or proof.get('unit')!=f['epsUnit']:raise ValueError('EPS selected-field proof mismatch')
            f['_epsConcept']=proof.get('concept');f.pop('rawJson');result[symbol].append(f)
    if len(seen)!=499:raise ValueError('Expected frozen499source rows')
    return result

def verify_sources(hashes):
    for p,h in hashes.items():
        if sha(p)!=h:raise ValueError('Sealed source drift '+p)

def prepare():
    if ROOT.exists():raise ValueError('Never overwrite a study directory')
    if sha(SOURCE/'inputs.json')!=INPUT_SHA or sha(SOURCE/'replay/financial-input-audit.json')!=FINANCIAL_SHA:raise ValueError('Recovered source checksum mismatch')
    baseline_protocol=load(PANEL.parent/'protocol.json');expected_panel=baseline_protocol['inputHashes'][str(PANEL)]
    if sha(PANEL)!=expected_panel:raise ValueError('Recovered V9 OOF panel checksum mismatch')
    original=load(PANEL);core.validate_panel(original['rows']);inputs=load(SOURCE/'inputs.json')
    if inputs['testDecisions']!=[] or max(inputs['trainingDecisions'])!='2020-08-28':raise ValueError('Training-only source required')
    history=source_history(inputs);index={n:i for i,n in enumerate(original['features'])};panel=[];audit={};coverage=collections.Counter();keysets=collections.defaultdict(set)
    for row in original['rows']:
        day,symbol=row['day'],row['symbol'];events=inputs['providerEventsBySymbol'].get(symbol,[])
        allowed=[f for f in history[symbol] if issuer_allowed(symbol,f.get('cik'),day,DIS_RULE)]
        facts,evidence=financial_features(allowed,day,events,row['x'][index['stock_ret20']])
        context={n:row['x'][index[n]] for n in CONTEXT};features={**context,**facts}
        x=[features[n] for n in FEATURES];metadata={n:row['x'][index[n]] for n in ['stock_vol20','stock_ret20','market_ret20','market_vol20']}
        metadata.update({kind+'Status':evidence[kind+'Status'] for kind in ['annual','quarter']})
        metadata.update({kind+'Age':None if evidence[kind] is None else days(day,evidence[kind]['endDate']) for kind in ['annual','quarter']})
        metadata['quarterFiledAge']=None if evidence['quarter'] is None else days(day,evidence['quarter']['filedAt'])
        panel.append({**row,'x':x,'diagnostic':metadata})
        audit[symbol+'/'+day]={k:brief(v,events,day) if isinstance(v,dict) else v for k,v in evidence.items()}
        for name in ['annual_yoy_missing','quarter_yoy_missing','quarter_yoy_acceleration_missing','quarter_qoq_missing','net_margin_yoy_missing']:
            if not features[name]:coverage[name.replace('_missing','_available')]+=1
        for name,f in evidence.items():
            if isinstance(f,dict):
                if f['filedAt']>=day or f['endDate']>=day:raise ValueError('Future source selected')
                keysets[name].add(natural(f))
    core.validate_panel(panel)
    if len(panel)!=25473 or len({r['day'] for r in panel})!=870:raise ValueError('Frozen OOF cohort changed')
    ROOT.mkdir();save(ROOT/'panel.json',dict(features=FEATURES,groups=GROUPS,rows=panel));save(ROOT/'feature-evidence.json',audit)
    save(ROOT/'coverage.json',dict(rows=len(panel),dates=870,available=coverage,uniqueSelectedSourceKeys={n:len(v) for n,v in keysets.items()},reconstructionNotHistoricalApiArchive=True))
    design=Path('docs/superpowers/plans/2026-11-01-v9-eps-growth-validation.md')
    save(ROOT/'design-prespec.json',dict(source=str(design),sha256=sha(design),text=design.read_text(encoding='utf-8')))
    paths=[SOURCE/'inputs.json',SOURCE/'replay/financial-input-audit.json',PANEL,PANEL.parent/'protocol.json',ROOT/'panel.json',ROOT/'feature-evidence.json',ROOT/'design-prespec.json',Path(__file__),Path('scripts/test_quant_v9_eps_growth.py'),Path('scripts/quant_v9_nested_training.py')]
    hashes={str(p):sha(p) for p in paths};verify_sources(hashes)
    protocol=dict(classification='INTERNAL_PREVIOUSLY_EXPOSED_TRAINING_DEVELOPMENT_NOT_NEW_HOLDOUT',inputHashes=hashes,features=FEATURES,configs=CONFIGS,outerStarts=[core.qshift('2018-04-01',i) for i in range(10)],
        minRows=300,minDates=20,innerQuarters=3,ridgePenalty=100,correctionBlend=.25,maxRawCorrectionPp=2.,freshness=dict(annualDays=550,quarterDays=220),
        fitRule='signal ANDexit strictlybeforefitcutoff; innerselectionlabelsexit strictlybeforeoutercutoff',selection='BothmeaninnerMAEs<V9; neithermeaninnerRMSE>V9; jointMAEbetter2of3quarters; bestjointratio/libraryorder elseV9',
        sameCohortPrimary=dict(rows=17904,dates=609,first='2018-04-02',last='2020-08-28'),old60TargetsUsed=False,promotionAllowed=False,dbWrites=0,
        bootstrap=dict(blockDates=21,draws=1000,seed=20261101),exposure='Previous internalcandidate performance known; no results from this new EPS library inspected before sealing.')
    save(ROOT/'protocol.json',protocol);print(json.dumps(dict(protocolSha256=sha(ROOT/'protocol.json'),coverage=coverage,rows=len(panel),candidates=NAMES)))

def diagnose():
    protocol=load(ROOT/'protocol.json');verify_sources(protocol['inputHashes']);rows=load(ROOT/'panel.json')['rows'];validate_panel(rows)
    primary=[r for r in rows if r['day']>='2018-04-01'];base=np.asarray([r['base'] for r in primary]);overall=core.summarize(primary,base);groups=collections.defaultdict(list)
    for r in primary:
        d=r['diagnostic'];f=dict(zip(FEATURES,r['x']))
        cuts={'year':r['day'][:4],'quarter':r['day'][:4]+'Q'+str((int(r['day'][5:7])-1)//3+1),'symbol':r['symbol'],
              'prior_market_direction':'nonnegative' if d['market_ret20']>=0 else 'negative','prior_market_volatility':'below15' if d['market_vol20']<15 else '15to25' if d['market_vol20']<25 else '25plus',
              'quarter_eps_yoy':'unavailable' if f['quarter_yoy_missing'] else 'available','annual_eps_yoy':'unavailable' if f['annual_yoy_missing'] else 'available',
              'quarter_period_age':'missing' if d['quarterAge'] is None else '0to120' if d['quarterAge']<=120 else '121to220' if d['quarterAge']<=220 else 'stale220plus',
              'quarter_filing_age':'missing' if d['quarterFiledAge'] is None else '0to45' if d['quarterFiledAge']<=45 else '46to90' if d['quarterFiledAge']<=90 else '90plus'}
        for cut,value in cuts.items():groups[(cut,value)].append(r)
    w=core.date_weights(primary);w/=w.sum();total_abs=np.sum(w[:,None]*abs(base-np.array([r['y'] for r in primary])),axis=0)
    segments=[]
    for (cut,value),items in sorted(groups.items()):
        # Global weighted contributions reconcile within each disjoint cut; subgroup MAEs use subgroup date weights.
        keys={(r['day'],r['symbol']) for r in items};mask=np.array([(r['day'],r['symbol']) in keys for r in primary])
        contribution=(w[mask,None]*abs(base[mask]-np.array([r['y'] for r in primary])[mask])).sum(axis=0)
        metrics=core.summarize(items,np.array([r['base'] for r in items]))
        segments.append(dict(cut=cut,value=value,rows=len(items),dates=len(set(r['day'] for r in items)),globalDateWeightedMass=float(w[mask].sum()),metrics=metrics,absoluteErrorContributionPp=contribution,absoluteErrorShare=contribution/total_abs))
    squared={};byday=collections.defaultdict(list)
    for r in primary:byday[r['day']].append(r)
    for j,head in enumerate(['Absolute','Excess']):
        common=[];within=[];total=[]
        for day,items in sorted(byday.items()):
            err=np.array([r['base'][j]-r['y'][j] for r in items]);common.append(float(err.mean()**2));within.append(float(((err-err.mean())**2).mean()));total.append(float((err**2).mean()))
        squared[head]=dict(totalMeanSquaredError=float(np.mean(total)),dateCommonMeanSquared=float(np.mean(common)),withinDateMeanSquared=float(np.mean(within)),dateCommonShare=float(np.mean(common)/np.mean(total)))
    tail={h:dict(errorP90=float(np.quantile(abs(base[:,j]-np.array([r['y'][j] for r in primary])),.9)),errorP99=float(np.quantile(abs(base[:,j]-np.array([r['y'][j] for r in primary])),.99))) for j,h in enumerate(['Absolute','Excess'])}
    save(ROOT/'diagnostics.json',dict(rows=len(primary),dates=609,baseline=overall,segments=segments,squaredErrorDecomposition=squared,tail=tail,causalClaims=False))
    print(json.dumps(dict(baseline=overall,squared=squared,tail=tail),indent=2))

def run(expected):
    if sha(ROOT/'protocol.json')!=expected:raise ValueError('External protocolSHA mismatch')
    protocol=load(ROOT/'protocol.json');verify_sources(protocol['inputHashes']);rows=load(ROOT/'panel.json')['rows'];validate_panel(rows)
    cache={};folds=[];all_ix=[];selected=[];predictions={n:[] for n in NAMES}
    for start in protocol['outerStarts']:
        f=run_fold(rows,start,cache);outer=f.pop('outer');ix=outer['indices'];valid=[rows[i] for i in ix];all_ix.extend(ix);selected.extend(outer['predictions'][f['selected']])
        for name in NAMES:predictions[name].extend(outer['predictions'][name])
        folds.append({**f,'start':start,'rows':len(ix),'dates':len(set(r['day'] for r in valid)),'maxTrainExit':outer['maxTrainExit'],'trainRows':outer['trainRows'],'models':outer['models'],'metrics':{n:core.summarize(valid,outer['predictions'][n]) for n in NAMES}})
        print('EPS_GROWTH_OUTER',start,f['selected'],len(ix),flush=True)
    valid=[rows[i] for i in all_ix];base=np.asarray(predictions['v9']);chosen=np.asarray(selected)
    if len(valid)!=17904 or len({r['day'] for r in valid})!=609:raise ValueError('Primary cohort changed')
    first=protocol['outerStarts'][0];altered=[dict(r,y=[-1e8,1e8]) if r['exit']>=first else r for r in rows];check=run_fold(altered,first,{})
    if check['selected']!=folds[0]['selected']:raise ValueError('Future label changes selection')
    for name in NAMES:
        if not np.array_equal(check['outer']['predictions'][name],cache[first]['predictions'][name]):raise ValueError('Future label changes earlier fit/prediction')
    metrics={n:core.summarize(valid,np.array(v)) for n,v in predictions.items()};metrics['nested_selected']=core.summarize(valid,chosen)
    verify_sources(protocol['inputHashes'])
    output=dict(protocolSha256=expected,classification=protocol['classification'],rows=len(valid),dates=609,metrics=metrics,bootstrap=core.block_interval(valid,chosen,base),
        candidateBootstrap={n:core.block_interval(valid,np.asarray(predictions[n]),base) for n in NAMES if n!='v9'},folds=folds,futureLabelPerturbationInvariant=True,
        pairs=[dict(panelIndex=i,day=r['day'],symbol=r['symbol'],y=r['y'],base=r['base'],selected=chosen[j],candidates={n:predictions[n][j] for n in NAMES}) for j,(i,r) in enumerate(zip(all_ix,valid))],
        fitAudit=[{k:v[k] for k in ['start','trainRows','trainDates','maxTrainExit','ready']} for _,v in sorted(cache.items())],old60TargetsUsed=False,dbWrites=0,promotionAllowed=False)
    save(ROOT/'result.json',output);print(json.dumps(dict(metrics=metrics,bootstrap=output['bootstrap']),indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['prepare','diagnose','run']);p.add_argument('--protocol-sha');a=p.parse_args()
    if a.mode=='prepare':prepare()
    elif a.mode=='diagnose':diagnose()
    else:run(a.protocol_sha)
