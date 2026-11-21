"""Expanded SEC quarterly features and prespecified high-volatility research; no DB."""
import argparse
import collections
import json
from pathlib import Path
import numpy as np
import quant_v9_nested_training as core
import quant_v9_eps_growth as old

ROOT=Path('tmp/quant-quarter-highvol-study-20261101-v1')
PACK=Path('tmp/quant-quarterly-financials-20261101-v3')
FINANCIAL_SHA='bdc1c1cf79bd3dc929dfb5b364b18b43be53fcfa11e88a26994a86ddeebcdd99'
INPUT_SHA=old.INPUT_SHA
CONTEXT=old.CONTEXT
EXTRA=['operating_margin_yoy_delta','operating_margin_yoy_missing','net_margin_level','net_margin_level_missing','revenue_yoy_acceleration','revenue_yoy_acceleration_missing','bank_net_revenue']
FEATURES=CONTEXT+old.GROWTH+old.ACCEL+old.CONFIRM+EXTRA
NAMES=['v9','expanded_financial','highvol_market','financial_highvol','fast_regime']
MARKET_FEATURES=['market_ret5','market_ret20','market_ret60','market_vol20','market_dd60']
sha,load,save=old.sha,old.load,old.save

def same_concept(rows,field):
    concepts=[f.get('_fieldConcepts',{}).get(field) if f else None for f in rows]
    return all(concepts) and len(set(concepts))==1

def financial_features(rows,day,events,stock_ret20):
    f,e=old.financial_features(rows,day,events,stock_ret20)
    a,b,p,py=(e[n] for n in ['quarter','quarterPriorYear','previousQuarter','previousQuarterPriorYear'])
    if not same_concept([a,b,p,py],'epsDiluted'):
        f['quarter_yoy_acceleration']=0.;f['quarter_yoy_acceleration_missing']=1
    revenue_ok=same_concept([a,b],'revenue')
    if not revenue_ok:
        f['revenue_yoy_symmetric']=0.;f['revenue_yoy_missing']=1
    if not revenue_ok or not same_concept([a,b],'netIncome'):
        f['net_margin_yoy_delta']=0.;f['net_margin_yoy_missing']=1
    f.update({n:0. for n in EXTRA})
    for name in ['operating_margin_yoy_missing','net_margin_level_missing','revenue_yoy_acceleration_missing']:f[name]=1
    fresh=e['quarterStatus']=='AVAILABLE'
    if a:f['bank_net_revenue']=int(a.get('_fieldConcepts',{}).get('revenue')=='RevenuesNetOfInterestExpense')
    def margin(row,field):
        if not row:return None
        revenue,value=old.number(row.get('revenue')),old.number(row.get(field))
        return None if revenue is None or revenue<=0 or value is None else 100*value/revenue
    if fresh:
        level=margin(a,'netIncome')
        if level is not None:f['net_margin_level']=float(np.clip(level,-100,100));f['net_margin_level_missing']=0
        first,second=margin(a,'operatingIncome'),margin(b,'operatingIncome')
        if revenue_ok and same_concept([a,b],'operatingIncome') and first is not None and second is not None:
            f['operating_margin_yoy_delta']=float(np.clip(first-second,-100,100));f['operating_margin_yoy_missing']=0
        if same_concept([a,b,p,py],'revenue'):
            values=[old.number(r.get('revenue')) for r in [a,b,p,py]]
            if all(v is not None and v>0 for v in values):
                f['revenue_yoy_acceleration']=float(np.clip(old.symmetric(*values[:2])-old.symmetric(*values[2:]),-4,4));f['revenue_yoy_acceleration_missing']=0
    return f,e

def daily_records(rows):
    groups=collections.defaultdict(list)
    for r in rows:groups[r['day']].append(r)
    daily=[]
    for day,items in sorted(groups.items()):
        y,base=np.asarray([r['y'] for r in items]),np.asarray([r['base'] for r in items]);benchmark=y[:,0]-y[:,1]
        if np.ptp(benchmark)>1e-8 or len({r['exit'] for r in items})!=1:raise ValueError('Inconsistent same-date benchmark or maturity')
        m=np.asarray([r['market'] for r in items])
        if not np.allclose(m,m[0],rtol=0,atol=1e-12):raise ValueError('Market features differ within a decision date')
        implied=float(np.mean(base[:,0]-base[:,1]))
        daily.append(dict(day=day,exit=items[0]['exit'],vol=float(m[0,3]),residual=np.mean(y-base,axis=0),marketTarget=float(np.mean(benchmark)-implied),marketFeatures=[*m[0],implied]))
    return daily

def regime(vol):return 0 if vol<15 else 1 if vol<25 else 2

def conditional_interval(rows,prediction,baseline,mask):
    """Resample full decision-date blocks, then condition on high-vol observations."""
    grouped=collections.defaultdict(list)
    for i,r in enumerate(rows):grouped[r['day']].append(i)
    y=np.asarray([r['y'] for r in rows]);delta=abs(prediction-y)-abs(baseline-y);values=[];present=[]
    for day,indices in sorted(grouped.items()):
        ix=[i for i in indices if mask[i]];values.append(delta[ix].mean(axis=0) if ix else np.zeros(2));present.append(int(bool(ix)))
    values=np.asarray(values);present=np.asarray(present);count=len(values)
    if count<21 or not present.any():raise ValueError('Full calendar>=21 and nonempty conditional dates required')
    rng=np.random.default_rng(20261101);means=[]
    while len(means)<1000:
        starts=rng.integers(0,count-20,size=int(np.ceil(count/21)));ix=np.concatenate([np.arange(s,s+21) for s in starts])[:count];den=present[ix].sum()
        if den:means.append(values[ix].sum(axis=0)/den)
    bounds=np.quantile(means,[.025,.975],axis=0);point=values.sum(axis=0)/present.sum()
    return dict(calendarDates=count,selectedDates=int(present.sum()),blockFullCalendarDates=21,**{h:dict(deltaMaePp=float(point[j]),lower95=float(bounds[0,j]),upper95=float(bounds[1,j])) for j,h in enumerate(['Absolute','Excess'])})

def regime_correction(daily,day,vol):
    mature=[r for r in daily if r['day']<day and r['exit']<day]
    same=[r for r in mature if regime(r['vol'])==regime(vol)]
    selected=(same if len(same)>=20 else mature)[-63:]
    mode='SAME_REGIME' if len(same)>=20 else 'GLOBAL_FALLBACK'
    if len(selected)<20:return np.zeros(2),dict(mode='INSUFFICIENT',dates=len(selected),maxExit=max((r['exit'] for r in selected),default=None))
    correction=.25*np.clip(np.median([r['residual'] for r in selected],axis=0),-2,2)
    return correction,dict(mode=mode,dates=len(selected),firstSignal=selected[0]['day'],lastSignal=selected[-1]['day'],maxExit=max(r['exit'] for r in selected))

def market_correction(train,valid):
    d=daily_records(train);out=np.zeros((len(valid),2))
    if not valid or len(d)<20:return out,dict(ready=False,trainDates=len(d))
    first=min(r['day'] for r in valid)
    if any(r['day']>=first or r['exit']>=first for r in d):raise ValueError('Immature market training source')
    model=core.fit_ridge(np.array([r['marketFeatures'] for r in d]),np.array([[r['marketTarget'],r['marketTarget']] for r in d]),np.ones(len(d)),100.)
    groups=collections.defaultdict(list)
    for i,r in enumerate(valid):groups[r['day']].append(i)
    for day,indices in groups.items():
        items=[valid[i] for i in indices];implied=np.mean([r['base'][0]-r['base'][1] for r in items]);features=np.array([[*items[0]['market'],implied]])
        if items[0]['market'][3]>=25:out[indices,0]=.25*np.clip(core.predict(model,features)[0,0],-4,4)
    return out,dict(ready=True,trainDates=len(d),maxTrainExit=max(r['exit'] for r in d),model=model)

def frame(rows,start,cache):
    if start in cache:return cache[start]
    train=[rows[i] for i in core.mature_indices(rows,start)];vi=[i for i,r in enumerate(rows) if start<=r['day']<core.qshift(start,1)];valid=[rows[i] for i in vi]
    base=np.asarray([r['base'] for r in valid]);ready=len(train)>=300 and len({r['day'] for r in train})>=20
    fin=np.zeros_like(base);market=np.zeros_like(base);fast=np.zeros_like(base);models={};fast_audit={}
    if ready:
        model=core.fit_ridge(np.asarray([r['x'] for r in train]),np.asarray([r['y'] for r in train])-np.asarray([r['base'] for r in train]),core.date_weights(train),100.)
        fin=.25*np.clip(core.predict(model,np.asarray([r['x'] for r in valid])),-2,2);models['financial']=model
        market,models['market']=market_correction(train,valid)
        if '_daily' not in cache:cache['_daily']=daily_records(rows)
        for i,r in enumerate(valid):
            if r['day'] not in fast_audit:
                value,audit=regime_correction(cache['_daily'],r['day'],r['market'][3]);fast_audit[r['day']]={**audit,'correction':value}
            fast[i]=fast_audit[r['day']]['correction']
    predictions=dict(v9=base,expanded_financial=base+fin,highvol_market=base+market,financial_highvol=base+fin+market,fast_regime=base+fast)
    result=dict(start=start,trainRows=len(train),trainDates=len({r['day'] for r in train}),maxTrainExit=max((r['exit'] for r in train),default=None),ready=ready,indices=vi,predictions=predictions,models=models,fastAudit=fast_audit)
    cache[start]=result;return result

def run_fold(rows,start,cache):
    scores={name:[] for name in NAMES};inner=[]
    for offset in [-3,-2,-1]:
        f=frame(rows,core.qshift(start,offset),cache);ix=[j for j,i in enumerate(f['indices']) if rows[i]['exit']<start and rows[i]['day']<start];valid=[rows[f['indices'][j]] for j in ix]
        if not f['ready'] or not valid:return dict(selected='v9',inner=inner,scores={},outer=frame(rows,start,cache))
        for name in NAMES:
            metrics=core.summarize(valid,f['predictions'][name][ix]);scores[name].append({k:[metrics[h][v] for h in ['Absolute','Excess']] for k,v in [('mae','maePp'),('rmse','rmsePp')]})
        inner.append(dict(start=f['start'],trainRows=f['trainRows'],maxTrainExit=f['maxTrainExit'],selectionRows=len(valid),maxSelectionExit=max(r['exit'] for r in valid)))
    return dict(selected=old.select_candidate(scores,NAMES),inner=inner,scores=scores,outer=frame(rows,start,cache))

def prepare():
    if ROOT.exists():raise ValueError('Never overwrite a sealed study')
    if sha(PACK/'financials.json')!=FINANCIAL_SHA or sha(old.SOURCE/'inputs.json')!=INPUT_SHA:raise ValueError('Source fingerprint mismatch')
    source_manifest=load(PACK/'source-manifest.json');financials=load(PACK/'financials.json');receipt=load(PACK/'receipt.json')
    if receipt['moduleSha256']!=sha('scripts/quant_quarterly_recovery.py'):raise ValueError('Source extractor changed after recovery')
    if any(f.get('epsDiluted') is not None and f.get('_epsConcept')!='EarningsPerShareDiluted' for values in financials.values() for f in values):raise ValueError('Unexpected EPS identity')
    keys=[old.natural(f) for values in financials.values() for f in values]
    if len(keys)!=len(set(keys)):raise ValueError('Duplicate expanded source key')
    baseline_protocol=load(old.PANEL.parent/'protocol.json')
    if sha(old.PANEL)!=baseline_protocol['inputHashes'][str(old.PANEL)]:raise ValueError('Genuine archived V9 panel changed')
    baseline=load(old.PANEL);inputs=load(old.SOURCE/'inputs.json');index={n:i for i,n in enumerate(baseline['features'])};rows=[];evidence={};coverage=collections.Counter()
    old_panel=load(old.ROOT/'panel.json');old_coverage=collections.Counter()
    for r in old_panel['rows']:
        for name in ['annual_yoy_missing','quarter_yoy_missing','quarter_qoq_missing','quarter_yoy_acceleration_missing','net_margin_yoy_missing']:
            if not r['x'][old_panel['features'].index(name)]:old_coverage[name.replace('_missing','_available')]+=1
    for r in baseline['rows']:
        day,symbol=r['day'],r['symbol'];events=inputs['providerEventsBySymbol'].get(symbol,[])
        admitted=[f for f in financials[symbol] if old.issuer_allowed(symbol,f['cik'],day,old.DIS_RULE)]
        f,e=financial_features(admitted,day,events,r['x'][index['stock_ret20']]);features={**{n:r['x'][index[n]] for n in CONTEXT},**f}
        new=dict(r,x=[features[n] for n in FEATURES],market=[r['x'][index[n]] for n in MARKET_FEATURES]);rows.append(new)
        evidence[symbol+'/'+day]={k:old.brief(v,events,day) if isinstance(v,dict) else v for k,v in e.items()}
        for name in ['annual_yoy_missing','quarter_yoy_missing','quarter_qoq_missing','quarter_yoy_acceleration_missing','net_margin_yoy_missing','operating_margin_yoy_missing','net_margin_level_missing','revenue_yoy_acceleration_missing']:
            if not features[name]:coverage[name.replace('_missing','_available')]+=1
    core.validate_panel(rows)
    if len(rows)!=25473 or len({r['day'] for r in rows})!=870 or inputs['testDecisions']!=[]:raise ValueError('Training-only cohort violated')
    ROOT.mkdir();save(ROOT/'panel.json',dict(features=FEATURES,rows=rows));save(ROOT/'feature-evidence.json',evidence)
    save(ROOT/'coverage.json',dict(rows=25473,oldAvailable=old_coverage,newAvailable=coverage,sourceRows=len(keys),directQ4=receipt['totals']['directQ4Rows']))
    plan=Path('docs/superpowers/plans/2026-11-01-quarter-financial-highvol-validation.md');save(ROOT/'design-prespec.json',dict(source=str(plan),sha256=sha(plan),text=plan.read_text(encoding='utf-8')))
    paths=[PACK/n for n in ['financials.json','source-manifest.json','receipt.json']]+[old.SOURCE/'inputs.json',old.PANEL,old.PANEL.parent/'protocol.json',old.ROOT/'panel.json',ROOT/'panel.json',ROOT/'feature-evidence.json',ROOT/'design-prespec.json']
    paths += [Path(p) for p in ['scripts/quant_quarter_highvol.py','scripts/test_quant_quarter_highvol.py','scripts/quant_quarterly_recovery.py','scripts/test_quant_quarterly_recovery.py','scripts/quant_v9_eps_growth.py','scripts/quant_v9_nested_training.py']]
    hashes={str(p):sha(p) for p in paths}
    for record in source_manifest['records']:hashes[record['archivePath']]=record['sha256']
    old.verify_sources(hashes)
    protocol=dict(classification='PREVIOUSLY_EXPOSED_INTERNAL_QUARTERLY_DATA_AND_HIGHVOL_RESEARCH',inputHashes=hashes,candidates=NAMES,features=FEATURES,marketFeatures=MARKET_FEATURES+['meanV9ImpliedMarket'],outerStarts=[core.qshift('2018-04-01',i) for i in range(10)],
                  financialRidge=100,marketRidge=100,financialBlend=.25,marketBlend=.25,marketVolThreshold=25,fastWindowDates=63,minFastDates=20,innerQuarters=3,
                  fitRule='quarterlyfinancial/market:signalANDexit<quarterstart; fast:signalANDexit<eachdecision',selection='bothmeaninnerMAEbetter/noRMSEworse/joint2of3,elseV9; innerselectionlabelsstrictlymaturebeforeouter',
                  primaryRows=17904,primaryDates=609,old60TargetsUsed=False,dbWrites=0,promotionAllowed=False,baseline='Fixed recovered actual dailycausal V9 OOF forecasts; not full model rebuild',
                  bootstrap=dict(blockDates=21,draws=1000,seed=20261101),sourcePolicy='Currentverifiedoriginalsreconstruction,notPITarchive; enlargedfinancialinputs,newcausalcorrections; no60outcomes')
    save(ROOT/'protocol.json',protocol);print(json.dumps(dict(protocolSha256=sha(ROOT/'protocol.json'),oldCoverage=old_coverage,newCoverage=coverage,rows=len(rows),sourceRows=len(keys))))

def run(expected):
    if sha(ROOT/'protocol.json')!=expected:raise ValueError('External protocol SHA mismatch')
    p=load(ROOT/'protocol.json');old.verify_sources(p['inputHashes']);rows=load(ROOT/'panel.json')['rows'];core.validate_panel(rows)
    cache={};folds=[];indices=[];selected=[];predictions={n:[] for n in NAMES};daily_audit={}
    for start in p['outerStarts']:
        f=run_fold(rows,start,cache);outer=f.pop('outer');ix=outer['indices'];valid=[rows[i] for i in ix];indices.extend(ix);selected.extend(outer['predictions'][f['selected']])
        for name in NAMES:predictions[name].extend(outer['predictions'][name])
        daily_audit.update(outer['fastAudit']);folds.append({**f,'start':start,'rows':len(ix),'maxTrainExit':outer['maxTrainExit'],'trainRows':outer['trainRows'],'models':outer['models'],'metrics':{n:core.summarize(valid,outer['predictions'][n]) for n in NAMES}})
        print('QUARTER_HIGHVOL_OUTER',start,f['selected'],len(ix),flush=True)
    valid=[rows[i] for i in indices];base=np.asarray(predictions['v9']);chosen=np.asarray(selected)
    if len(valid)!=17904 or len({r['day'] for r in valid})!=609:raise ValueError('Primary cohort changed')
    first=p['outerStarts'][0];changed=[dict(r,y=[-1e8,1e8]) if r['exit']>=first else r for r in rows];check=run_fold(changed,first,{})
    if check['selected']!=folds[0]['selected']:raise ValueError('Future labels changed selection')
    for name in NAMES:
        actual,reference=check['outer']['predictions'][name],cache[first]['predictions'][name]
        if not np.array_equal(actual[0] if name=='fast_regime' else actual,reference[0] if name=='fast_regime' else reference):raise ValueError('Future labels changed earlier prediction')
    daily=daily_records(rows);fast_checks=[]
    for day in [valid[0]['day'],'2019-01-02','2020-03-16','2020-08-28']:
        current=next(r for r in valid if r['day']==day);altered=[dict(r,y=[-1e8,1e8]) if r['exit']>=day else r for r in rows]
        if not np.array_equal(regime_correction(daily,day,current['market'][3])[0],regime_correction(daily_records(altered),day,current['market'][3])[0]):raise ValueError('Daily calibration future leak')
        fast_checks.append(day)
    metrics={n:core.summarize(valid,np.asarray(v)) for n,v in predictions.items()};metrics['nested_selected']=core.summarize(valid,chosen)
    high=[i for i,r in enumerate(valid) if r['market'][3]>=25];high_rows=[valid[i] for i in high]
    high_metrics={n:core.summarize(high_rows,np.asarray(v)[high]) for n,v in predictions.items()};high_metrics['nested_selected']=core.summarize(high_rows,chosen[high])
    old.verify_sources(p['inputHashes'])
    result=dict(protocolSha256=expected,rows=17904,dates=609,metrics=metrics,highVolatility=dict(rows=len(high),dates=len({r['day'] for r in high_rows}),metrics=high_metrics),
                bootstrap=core.block_interval(valid,chosen,base),candidateBootstrap={n:core.block_interval(valid,np.asarray(v),base) for n,v in predictions.items() if n!='v9'},
                highVolBootstrap=conditional_interval(valid,chosen,base,np.array([r['market'][3]>=25 for r in valid])),folds=folds,fastAudit=daily_audit,fastFutureLabelInvariantDates=fast_checks,futureLabelPerturbationInvariant=True,
                pairs=[dict(day=r['day'],symbol=r['symbol'],exit=r['exit'],y=r['y'],base=r['base'],selected=chosen[j],vol=r['market'][3],candidates={n:predictions[n][j] for n in NAMES}) for j,r in enumerate(valid)],old60TargetsUsed=False,dbWrites=0,promotionAllowed=False)
    save(ROOT/'result.json',result);print(json.dumps(dict(metrics=metrics,highVolatility=result['highVolatility'],bootstrap=result['bootstrap']),indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['prepare','run']);parser.add_argument('--protocol-sha');args=parser.parse_args()
    prepare() if args.mode=='prepare' else run(args.protocol_sha)
