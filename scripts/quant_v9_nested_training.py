"""Training-only nested chronological research. Never open old held-out result files."""
import argparse,bisect,collections,hashlib,json,math
from datetime import date
from pathlib import Path
import numpy as np

SOURCE=Path('tmp/quant-long-verified-oof-study-20261031-v1')
ROOT=Path('tmp/quant-v9-nested-training-20261101-v1')
FORECAST_SHA='f3fa85da248107034a2c52a0a22208c20c28788c55780a31613d3877375e0c0e'
INPUT_SHA='aed4e7383ac3c30085e52075f1d78c8301108fd3120ce9fd99b0544185fb2b03'
FINANCIAL_SHA='fc5cfe4e1249040e452c1a9681dd3bc0cb8703ebce4f03c46e1dcedddb55de45'
FACTORS='integratedScore valuationScore qualityScore growthScore stabilityScore earningsScore analystScore newsScore momentumScore riskScore institutionScore dataQualityScore'.split()
GROUPS={'base':['v9_absolute','v9_excess'], 'scores':FACTORS+['log_marketcap'],
 'technical':['stock_ret5','stock_ret20','stock_ret60','stock_vol20','stock_dd60','relative20','relative60'],
 'market':['market_ret5','market_ret20','market_ret60','market_vol20','market_dd60'],
 'availability':['annual_missing','annual_eps_missing','annual_log_age','quarter_missing','quarter_eps_missing','quarter_log_age']}
FEATURES=sum(GROUPS.values(),[])
CONFIGS=[dict(name='v9',kind='v9'),dict(name='zero',kind='zero'),dict(name='past_median',kind='median')]
for kind in ['direct','residual']:
    for penalty in [10,100]:CONFIGS.append(dict(name=f'{kind}_full_{penalty}',kind=kind,penalty=penalty,omit=None))
for omit in ['market','technical','availability']:CONFIGS.append(dict(name='residual_without_'+omit,kind='residual',penalty=100,omit=omit))
NAMES=[c['name'] for c in CONFIGS]

def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def load(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def save(p,obj):
    with Path(p).open('x',encoding='utf-8') as f:json.dump(obj,f,indent=2,allow_nan=False,default=lambda x:x.tolist() if isinstance(x,np.ndarray) else x.item())

def mature_indices(rows,cutoff):return [i for i,r in enumerate(rows) if r['day']<cutoff and r['exit']<cutoff]
def date_weights(rows):
    counts=collections.Counter(r['day'] for r in rows)
    return np.array([1/counts[r['day']] for r in rows])

def summarize(rows,pred):
    y=np.asarray([r['y'] for r in rows]);e=np.asarray(pred)-y
    if not len(rows) or e.shape!=(len(rows),2) or not np.isfinite(e).all():raise ValueError('Complete finite paired predictions required')
    w=date_weights(rows);w=w/w.sum()
    return {name:dict(maePp=float(w@abs(e[:,j])),rmsePp=float(np.sqrt(w@(e[:,j]**2))),biasPp=float(w@e[:,j]),rowMaePp=float(abs(e[:,j]).mean())) for j,name in enumerate(['Absolute','Excess'])}

def price_features(series,day):
    days=[r[0] for r in series];end=bisect.bisect_right(days,day)
    if end<80 or days[end-1]!=day:raise ValueError('Actual decision close and80prior prices required')
    p=np.array([v for _,v in series[max(0,end-80):end]],float)
    if not np.isfinite(p).all() or (p<=0).any():raise ValueError('Invalid historical prices')
    return np.array([(p[-1]/p[-1-n]-1)*100 for n in [5,20,60]]+[np.std(np.diff(np.log(p[-21:])),ddof=1)*math.sqrt(252)*100,(p[-1]/max(p[-60:])-1)*100])

def fit_ridge(x,y,w,alpha):
    x=np.asarray(x,float);y=np.asarray(y,float);w=np.asarray(w,float)
    if not len(x) or x.ndim!=2 or y.shape!=(len(x),2) or not np.isfinite(x).all() or not np.isfinite(y).all() or (w<=0).any():raise ValueError('Finite training matrix required')
    norm=w/w.sum();mean=norm@x;scale=np.sqrt(norm@((x-mean)**2));scale[scale<1e-12]=1
    z=np.clip((x-mean)/scale,-5,5);a=np.column_stack([np.ones(len(x)),z]);pen=np.diag([0]+[alpha]*x.shape[1])
    coef=np.linalg.lstsq(a.T@(w[:,None]*a)+pen,a.T@(w[:,None]*y),rcond=None)[0]
    return dict(mean=mean,scale=scale,coef=coef,penalty=alpha)

def predict(model,x):
    z=np.clip((np.asarray(x)-model['mean'])/model['scale'],-5,5)
    return np.column_stack([np.ones(len(z)),z])@model['coef']

def fit_at(rows,cutoff,columns,penalty,residual):
    selected=[rows[i] for i in mature_indices(rows,cutoff)]
    x=np.array([r['x'] for r in selected])[:,columns];y=np.array([r['y'] for r in selected])
    if residual:y=y-np.array([r['base'] for r in selected])
    m=fit_ridge(x,y,date_weights(selected),penalty)
    m.update(trainRows=len(selected),trainDates=len(set(r['day'] for r in selected)),maxTrainExit=max(r['exit'] for r in selected),cutoff=cutoff,columns=columns,residual=residual)
    return m

def choose(scores,names):
    if 'v9' not in scores or len(scores['v9'])!=3:return 'v9'
    b=np.array(scores['v9']);eligible=[]
    for order,name in enumerate(names):
        if name=='v9' or name not in scores or len(scores[name])!=3:continue
        a=np.array(scores[name]);ratio=np.mean(a/np.maximum(b,1e-12),axis=1)
        if np.all(a.mean(axis=0)<b.mean(axis=0)-1e-12) and sum(ratio<1-1e-12)>=2:eligible.append((float(ratio.mean()),order,name))
    return min(eligible)[2] if eligible else 'v9'

def validate_panel(rows):
    seen=set()
    for r in rows:
        k=(r['day'],r['symbol'],r['horizon'])
        if k in seen or r['day']>'2020-08-28' or r['exit']>='2020-09-30' or r['horizon']!=20 or r['maxFeatureDay']>r['day']:raise ValueError('Duplicate/forbidden/future panel record')
        if not np.isfinite(r['x']+r['y']+r['base']).all():raise ValueError('Nonfinite panel')
        seen.add(k)

def qshift(start,n):
    d=date.fromisoformat(start);number=d.year*4+(d.month-1)//3+n
    return date(number//4,number%4*3+1,1).isoformat()

def weighted_median(y,w):
    answer=[]
    for v in np.asarray(y).T:
        order=np.argsort(v);a=v[order];cw=np.cumsum(w[order]);half=cw[-1]/2;k=np.searchsorted(cw,half)
        answer.append((a[k]+a[k+1])/2 if k+1<len(a) and abs(cw[k]-half)<1e-9 else a[k])
    return answer

def availability_features(financial,symbol,day):
    result=[]
    for op in ['findLatestAnnualAsOf','findLatestQuarterAsOf']:
        key=symbol+'/'+day+'/'+op
        if key not in financial:raise ValueError('Missing audit evidence, not observed missingness: '+key)
        f=financial[key];missing=f.get('missing',False)
        if not missing and (f['filedAt']>=day or f['endDate']>=day):raise ValueError('Financial source not strictly prior')
        age=0 if missing else (date.fromisoformat(day)-date.fromisoformat(f['endDate'])).days
        result.extend([int(missing),int(missing or f.get('epsDiluted') is None),math.log1p(age)])
    return result

def prepare():
    ROOT.mkdir(parents=True,exist_ok=True)
    if (ROOT/'protocol.json').exists():raise ValueError('Already sealed; no reselection')
    source=SOURCE/'replay/forecast-only.json'
    if sha(source)!=FORECAST_SHA:raise ValueError('Frozen OOF source changed')
    if sha(SOURCE/'inputs.json')!=INPUT_SHA or sha(SOURCE/'replay/financial-input-audit.json')!=FINANCIAL_SHA:raise ValueError('Frozen asof source changed')
    raw=load(source);training=raw['trainingRows'];oof=raw['oofPredictions'];del raw
    if len(training)!=26401 or len(oof)!=25473:raise ValueError('Wrong source population')
    keys={(r['signalDate'],r['symbol'],r['horizonDays']):r for r in training}
    if len(keys)!=len(training):raise ValueError('Duplicate training labels')
    inputs=load(SOURCE/'inputs.json');financial=load(SOURCE/'replay/financial-input-audit.json')
    prices={s:[(p['day'],float(p['close'])) for p in v if p['day']<='2020-08-28'] for s,v in inputs['pricesBySymbol'].items()}
    benchmark=[(p['day'],float(p['level'])) for p in inputs['benchmark'] if p['day']<='2020-08-28']
    market={};panel=[]
    for p in sorted(oof,key=lambda p:(p['signalDate'],p['symbol'])):
        day=p['signalDate'];symbol=p['symbol'];r=keys[(day,symbol,20)]
        if p['modelVersion']!='EXPECTED_RETURN_V9_CORRECTED_20261017':raise ValueError('Not genuine V9 OOF')
        if day not in market:market[day]=price_features(benchmark,day)
        tech=price_features(prices[symbol],day);m=market[day];availability=availability_features(financial,symbol,day)
        base=[float(p['expectedReturnPct']),float(p['expectedExcessReturnPct'])]
        x=base+[float(r[n]) for n in FACTORS]+[math.log(float(r['marketCap']))]+list(tech)+[tech[1]-m[1],tech[2]-m[2]]+list(m)+availability
        actual=float(r['forwardReturnPct']);bench=float(r['benchmarkReturnPct'])
        panel.append(dict(day=day,symbol=symbol,horizon=20,exit=r['exitTradeDate'],x=x,y=[actual,actual-bench],base=base,maxFeatureDay=day))
    validate_panel(panel)
    if len(panel)!=25473:raise ValueError('Incomplete OOF join')
    save(ROOT/'panel.json',dict(features=FEATURES,groups=GROUPS,rows=panel,classification='EXISTING_TRAINING_ONLY_NOT_NEW_HOLDOUT'))
    paths=[source,SOURCE/'inputs.json',SOURCE/'replay/financial-input-audit.json',Path(__file__),Path('scripts/test_quant_v9_nested_training.py')]
    hashes={str(p):sha(p) for p in paths};hashes[str(ROOT/'panel.json')]=sha(ROOT/'panel.json')
    protocol=dict(classification='INTERNAL_NESTED_TRAINING_RESEARCH_NOT_UNTOUCHED_TEST',inputHashes=hashes,rows=len(panel),features=FEATURES,configs=CONFIGS,
       outerStarts=[qshift('2018-04-01',i) for i in range(10)],innerQuarters=3,minRows=300,minDates=20,
       fitRule='signal<cutoff AND exit<cutoff; inner selection labels alsoexit<outercutoff',
       loss='sum_date(mean_stock(square_error))+lambda*sum(beta^2); unpenalizedintercept; trainingdatebalancedstandardization; zclip5',
       selection='Mean3innerquarter MAEbothheads<V9 ANDrelativejointMAE<1in2of3quarters; smallestmeanratio/libraryorder; elseV9',
       baseline='Online daily-causal archivedV9; correctionweightsquarterfrozen, not end-to-endquarterfrozen',
       forbiddenTargetsUsed=False,forbiddenArtifacts=['previous result.json','previous evaluation.json','target prediction arrays','baseline prediction arrays'],
       diagnosticDisclosure='Descriptive V9 baseline/missingness audit inspected before sealing; no candidate performance. Not blinded outer test.',
       bootstrap=dict(blockDecisionDates=21,draws=1000,seed=20261101),promotionAllowed=False)
    save(ROOT/'protocol.json',protocol)
    print(json.dumps(dict(protocolSha256=sha(ROOT/'protocol.json'),rows=len(panel),dates=len(set(r['day'] for r in panel)),features=len(FEATURES),candidates=NAMES)))

def frame(rows,start,cache):
    if start in cache:return cache[start]
    ti=mature_indices(rows,start);vi=[i for i,r in enumerate(rows) if start<=r['day']<qshift(start,1)]
    train=[rows[i] for i in ti];valid=[rows[i] for i in vi];base=np.array([r['base'] for r in valid]);x=np.array([r['x'] for r in valid])
    ready=len(train)>=300 and len(set(r['day'] for r in train))>=20
    predictions={};models={}
    for c in CONFIGS:
        name=c['name'];kind=c['kind']
        if not ready or kind=='v9':pred=base.copy()
        elif kind=='zero':pred=np.zeros_like(base)
        elif kind=='median':pred=np.tile(weighted_median([r['y'] for r in train],date_weights(train)),(len(valid),1))
        else:
            columns=[i for i,n in enumerate(FEATURES) if not c['omit'] or n not in GROUPS[c['omit']]]
            model=fit_at(rows,start,columns,c['penalty'],kind=='residual');models[name]=model;pred=predict(model,x[:,columns])
            if kind=='residual':pred=pred+base
        predictions[name]=pred
    out=dict(start=start,trainRows=len(train),trainDates=len(set(r['day'] for r in train)),maxTrainExit=max((r['exit'] for r in train),default=None),ready=ready,indices=vi,predictions=predictions,models=models)
    cache[start]=out;return out

def run_fold(rows,start,cache):
    scores={name:[] for name in NAMES};inner=[]
    for offset in [-3,-2,-1]:
        f=frame(rows,qshift(start,offset),cache);indices=[j for j,i in enumerate(f['indices']) if rows[i]['exit']<start and rows[i]['day']<start]
        valid=[rows[f['indices'][j]] for j in indices]
        if not f['ready'] or not valid:return dict(selected='v9',outer=frame(rows,start,cache),inner=inner,scores={})
        for name in NAMES:
            metric=summarize(valid,f['predictions'][name][indices]);scores[name].append([metric[t]['maePp'] for t in ['Absolute','Excess']])
        inner.append(dict(start=f['start'],trainRows=f['trainRows'],trainDates=f['trainDates'],maxTrainExit=f['maxTrainExit'],selectionRows=len(valid),maxSelectionExit=max(r['exit'] for r in valid)))
    return dict(selected=choose(scores,NAMES),outer=frame(rows,start,cache),inner=inner,scores=scores)

def block_interval(rows,selected,baseline):
    grouped=collections.defaultdict(list)
    for i,r in enumerate(rows):grouped[r['day']].append(i)
    y=np.array([r['y'] for r in rows]);d=abs(selected-y)-abs(baseline-y)
    daily=np.array([d[ix].mean(axis=0) for _,ix in sorted(grouped.items())]);rng=np.random.default_rng(20261101);means=[]
    for _ in range(1000):
        starts=rng.integers(0,len(daily)-20,size=math.ceil(len(daily)/21));ix=np.concatenate([np.arange(s,s+21) for s in starts])[:len(daily)];means.append(daily[ix].mean(axis=0))
    ci=np.quantile(means,[.025,.975],axis=0)
    return {t:dict(deltaMaePp=float(daily[:,j].mean()),lower95=float(ci[0,j]),upper95=float(ci[1,j])) for j,t in enumerate(['Absolute','Excess'])}

def run():
    protocol=load(ROOT/'protocol.json')
    for p,h in protocol['inputHashes'].items():
        if sha(p)!=h:raise ValueError('Sealed input drift '+p)
    rows=load(ROOT/'panel.json')['rows'];validate_panel(rows);cache={};folds=[];all_ix=[];selected=[];all_predictions={n:[] for n in NAMES}
    for start in protocol['outerStarts']:
        f=run_fold(rows,start,cache);outer=f.pop('outer');ix=outer['indices'];valid=[rows[i] for i in ix];name=f['selected'];all_ix.extend(ix);selected.extend(outer['predictions'][name])
        for n in NAMES:all_predictions[n].extend(outer['predictions'][n])
        f.update(start=start,rows=len(ix),dates=len(set(r['day'] for r in valid)),maxTrainExit=outer['maxTrainExit'],trainRows=outer['trainRows'],models=outer['models'],metrics={n:summarize(valid,outer['predictions'][n]) for n in NAMES})
        folds.append(f);print('OUTER',start,'selected='+name,'rows='+str(len(ix)),flush=True)
    # Actual-label perturbation tests the entire first nested choice+fit, not only a standalone filter.
    first=protocol['outerStarts'][0];altered=[dict(r,y=[-99999.,99999.]) if r['exit']>=first else r for r in rows];check=run_fold(altered,first,{})
    reference=frame(rows,first,cache);chosen=folds[0]['selected']
    if check['selected']!=chosen or not np.array_equal(reference['predictions'][chosen],check['outer']['predictions'][chosen]):raise ValueError('Future-label leak in nested procedure')
    valid=[rows[i] for i in all_ix];pred=np.array(selected);base=np.array(all_predictions['v9']);metrics={n:summarize(valid,np.array(v)) for n,v in all_predictions.items()};metrics['nested_selected']=summarize(valid,pred)
    for p,h in protocol['inputHashes'].items():
        if sha(p)!=h:raise ValueError('Source mutated during run')
    output=dict(classification=protocol['classification'],protocolSha256=sha(ROOT/'protocol.json'),rows=len(valid),dates=len(set(r['day'] for r in valid)),folds=folds,metrics=metrics,
       bootstrap=block_interval(valid,pred,base),futureLabelPerturbationInvariant=True,
       pairs=[dict(panelIndex=i,day=r['day'],symbol=r['symbol'],y=r['y'],base=r['base'],selected=pred[j],candidates={n:all_predictions[n][j] for n in NAMES}) for j,(i,r) in enumerate(zip(all_ix,valid))],
       fitAudit=[dict(start=k,trainRows=v['trainRows'],trainDates=v['trainDates'],maxTrainExit=v['maxTrainExit'],ready=v['ready']) for k,v in sorted(cache.items())],
       heldout60Used=False,promotionAllowed=False,dbWrites=0)
    save(ROOT/'result.json',output)
    print(json.dumps(dict(rows=output['rows'],dates=output['dates'],metrics=metrics,bootstrap=output['bootstrap']),indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['prepare','run']);a=p.parse_args()
    prepare() if a.mode=='prepare' else run()
