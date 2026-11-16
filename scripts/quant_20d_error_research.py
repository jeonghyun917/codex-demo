"""Frozen, causal 20D error experiments. No DB access or automatic promotion."""
import csv, hashlib, json, math
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
import numpy as np
from quant_complete_windows import parse_chart, merge_prices

REPORTS = [f'tmp/{name}/result.json' for name in (
    'quant-older-model-recovery-v2-final-20261030',
    'quant-extended-model-recovery-final-20261018',
    'quant-team-model-recovery-final-20261018',
    'quant-v9-historical-market-final-20261031',
    'quant-march-model-v3-final-20261031')]
HOLDINGS = ['tmp/quant-older-staged-20261030/holdings.csv',
            'tmp/quant-complete-windows-recovered-20261018/complete-holdings.csv']
V9='EXPECTED_RETURN_V9_CORRECTED_20261017'
V8='EXPECTED_RETURN_V8_CORRECTED_20261017'
OUT=Path('tmp/quant-20d-error-research-v2-20261031')

def training_rows(rows, day):
    return [r for r in rows if r['day'] < day and r['exit'] < day]

def fit_ridge(x, y, alpha=10):
    x=np.asarray(x,dtype=float); y=np.asarray(y,dtype=float)
    if x.ndim!=2 or y.shape!=(len(x),) or not len(x) or not np.isfinite(x).all() or not np.isfinite(y).all():
        raise ValueError('Nonempty finite rectangular training data required')
    mu=x.mean(axis=0); scale=x.std(axis=0); scale[scale<1e-12]=1
    z=(x-mu)/scale; intercept=float(y.mean())
    weights=np.linalg.lstsq(z.T@z+alpha*np.eye(x.shape[1]),z.T@(y-intercept),rcond=None)[0]
    return dict(mean=mu.tolist(),scale=scale.tolist(),weights=weights.tolist(),intercept=intercept)

def predict_ridge(model,x):
    return (np.asarray(x)-model['mean'])/model['scale']@np.asarray(model['weights'])+model['intercept']

def metrics(predicted,actual):
    p=np.asarray(predicted);a=np.asarray(actual)
    if p.ndim!=1 or p.shape!=a.shape:raise ValueError('Equal one-dimensional prediction/actual rows required')
    e=p-a
    if not len(e) or not np.isfinite(e).all():raise ValueError('Finite paired predictions required')
    return dict(maePp=float(np.mean(abs(e))),rmsePp=float(np.sqrt(np.mean(e*e))),
                biasPp=float(e.mean()),p90AbsErrorPp=float(np.quantile(abs(e),.9)))

def price_features(prices,day):
    p=np.asarray([v for d,v in prices if d<=day],dtype=float)
    if len(p)<80 or not np.isfinite(p).all() or (p<=0).any():raise ValueError('80 valid historical closes required')
    ret=[float((p[-1]/p[-1-n]-1)*100) for n in (5,20,60)]
    vol=float(np.std(np.diff(np.log(p[-21:])),ddof=1)*math.sqrt(252)*100)
    draw=float((p[-1]/max(p[-60:])-1)*100)
    return ret+[vol,draw]

def load_panel():
    hashes={}; forecasts={}; admitted=set()
    def read(path):
        p=Path(path); data=p.read_bytes(); hashes[str(p)]=hashlib.sha256(data).hexdigest();return data
    for path in REPORTS:
        report=json.loads(read(path))
        assert report['databaseWrites']==0 and not report['promotionAllowed']
        for p,h in report['inputHashes'].items():
            assert hashlib.sha256(Path(p).read_bytes()).hexdigest()==h,p
        good={(d['decision'],m['model']) for d in report['dates'] for m in d['models'] if m['fullPopulation'] and m['predictions']==101}
        for p in report['predictions']:
            if (p['signalDate'],p['modelVersion']) not in good:continue
            assert p['horizonDays']==20
            key=(p['signalDate'],p['symbol'],p['modelVersion']);assert key not in forecasts
            forecasts[key]=p;admitted.add(key[:2])
    holdings={}
    for path in HOLDINGS:
        for h in csv.DictReader(read(path).decode('utf-8-sig').splitlines()):
            if h['index']!='NASDAQ100_MACRO_FREE_20261018':continue
            key=(h['decision'],h['symbol']);assert key not in holdings;holdings[key]=h
    canonical=defaultdict(dict)
    path='tmp/quant-complete-window-canonical-20261018.tsv'
    for r in csv.DictReader(read(path).decode('utf-8-sig').splitlines(),delimiter='\t'):
        canonical[r['symbol']][r['trade_date']]=float(r['close_price'])
    models=sorted({k[2] for k in forecasts}); price_cache={}; rows=[]
    for day,symbol in sorted(admitted):
        assert all((day,symbol,m) in forecasts for m in models)
        h=holdings[day,symbol]; actual=float(h['actualReturnPct']); bench=float(h['benchmarkReturnPct'])
        assert day<h['entry']<h['exit']
        assert abs((float(h['exitClose'])/float(h['entryClose'])-1)*100-actual)<1e-7
        if symbol not in price_cache:
            if symbol=='ANSS':
                path='tmp/quant-anss-free-evidence-20261018/chartexchange-anss.csv'
                prices=[(r['date'],float(r['close'])) for r in csv.DictReader(read(path).decode().splitlines())]
            else:
                path=f'tmp/pit-acquisition/actions/raw/{symbol}.json'
                native=canonical.get(symbol,{})
                required=[d for d,s in admitted if s==symbol]
                if all(d in native and sum(x<=d for x in native)>=80 for d in required):
                    merged=native
                else:
                    incoming=parse_chart(read(path),symbol)
                    # Existing canonical history wins; different bases must never be spliced.
                    merged=merge_prices(native,incoming) if native else incoming
                prices=sorted(merged.items())
            assert prices==sorted(prices) and len({d for d,_ in prices})==len(prices)
            price_cache[symbol]=prices
        prices=price_cache[symbol]; assert any(d==day for d,_ in prices),(symbol,day)
        rows.append(dict(day=day,symbol=symbol,exit=h['exit'],entry=h['entry'],actual=actual,excess=actual-bench,
                         price=price_features(prices,day),
                         forecasts={m:dict(absolute=float(forecasts[day,symbol,m]['expectedReturnPct']),
                                           excess=float(forecasts[day,symbol,m]['expectedExcessReturnPct'])) for m in models}))
    assert len(rows)==2020 and len(models)==8
    return rows,models,hashes

def features(row,kind,target,models):
    if kind=='V9_CALIBRATED':return [row['forecasts'][V9][target]]
    if kind=='PRICE_RIDGE':return row['price']
    if kind=='PRICE_RETURN_RIDGE':return row['price'][:3]
    if kind=='PRICE_RISK_RIDGE':return row['price'][3:]
    if kind=='V9_PRICE_RIDGE':return [row['forecasts'][V9][target]]+row['price']
    if kind=='MODEL_STACK':return [row['forecasts'][m][target] for m in models]
    raise ValueError(kind)

def evaluate(rows,models):
    saved=[]; fits=[]; baseline=[]
    families=['ZERO','PAST_MEDIAN','V9','V8','V9_CALIBRATED','PRICE_RIDGE','PRICE_RETURN_RIDGE','PRICE_RISK_RIDGE','V9_PRICE_RIDGE','MODEL_STACK']
    for day in sorted({r['day'] for r in rows}):
        test=[r for r in rows if r['day']==day];train=training_rows(rows,day)
        for target in ('absolute','excess'):
            actual=lambda r:r['actual' if target=='absolute' else 'excess']
            for m in models:
                baseline.append(dict(day=day,target=target,model=m,**metrics([r['forecasts'][m][target] for r in test],[actual(r) for r in test])))
            if len({r['day'] for r in train})<8:continue
            for family in families:
                fit=None
                if family in ('V9_CALIBRATED','PRICE_RIDGE','PRICE_RETURN_RIDGE','PRICE_RISK_RIDGE','V9_PRICE_RIDGE','MODEL_STACK'):
                    fit=fit_ridge([features(r,family,target,models) for r in train],[actual(r) for r in train])
                    pred=predict_ridge(fit,[features(r,family,target,models) for r in test])
                    fits.append(dict(day=day,target=target,family=family,trainingDates=sorted({r['day'] for r in train}),
                                     maximumTrainingExit=max(r['exit'] for r in train),fit=fit))
                elif family=='ZERO':pred=[0]*len(test)
                elif family=='PAST_MEDIAN':pred=[float(np.median([actual(r) for r in train]))]*len(test)
                else:pred=[r['forecasts'][V9 if family=='V9' else V8][target] for r in test]
                for r,p in zip(test,pred):saved.append(dict(day=day,symbol=r['symbol'],entry=r['entry'],exit=r['exit'],target=target,family=family,predicted=float(p),actual=actual(r)))
    grouped=defaultdict(list)
    for r in saved:grouped[r['target'],r['family'],r['day']].append(r)
    daily=[dict(target=t,family=f,day=d,**metrics([r['predicted'] for r in rs],[r['actual'] for r in rs])) for (t,f,d),rs in grouped.items()]
    summary=[]; rng=np.random.default_rng(20261031)
    for target in ('absolute','excess'):
        base={r['day']:r for r in daily if r['target']==target and r['family']=='V9'}
        for family in families:
            ds=[r for r in daily if r['target']==target and r['family']==family]
            delta=np.array([r['maePp']-base[r['day']]['maePp'] for r in ds])
            # Descriptive date bootstrap only; overlapping outcomes limit interpretation.
            boot=rng.choice(delta,(2000,len(delta)),replace=True).mean(axis=1)
            summary.append(dict(target=target,family=family,dates=len(ds),rows=sum(len(rs) for (t,f,d),rs in grouped.items() if t==target and f==family),
                                **{k:float(np.mean([r[k] for r in ds])) for k in ('maePp','rmsePp','biasPp','p90AbsErrorPp')},
                                maeDeltaVsV9Pp=float(delta.mean()),betterDates=int((delta<0).sum()),
                                exploratoryDateBootstrap95=np.quantile(boot,[.025,.975]).tolist()))
    return dict(baselineDaily=baseline,summary=summary,daily=daily,predictions=saved,fits=fits)

def main():
    rows,models,hashes=load_panel();result=evaluate(rows,models)
    OUT.mkdir(exist_ok=False)
    result.update(inputHashes=hashes,classification='EXPLORED_RECONSTRUCTION_NOT_UNTOUCHED_HOLDOUT',
                  databaseWrites=0,promotionAllowed=False,
                  protocol=dict(minimumMatureTrainingDates=8,ridgeAlpha=10,trainingExitStrictlyBeforeDecision=True,
                                targets=['absolute price return','benchmark excess price return'],minimumPriceBars=80),
                  limitations=['20 decision dates; correlated securities and overlapping outcomes, not independent rows.',
                               'All historical periods previously inspected; no untouched holdout.',
                               'Official PIT membership, price corporate actions and receipt timing not fully proven.',
                               'Vendor close history and ANSS secondary provider have adjustment limitations.',
                               'Bootstrap is descriptive date resampling, not valid independent-period significance.',
                               'No newly trained model is deployed; probabilities and intervals are not recalibrated by this regression.'])
    (OUT/'result.json').write_text(json.dumps(result,indent=2,allow_nan=False),encoding='utf-8')
    with (OUT/'summary.csv').open('w',newline='',encoding='utf-8-sig') as f:
        w=csv.DictWriter(f,fieldnames=result['summary'][0]);w.writeheader();w.writerows(result['summary'])
    print(json.dumps(result['summary'],indent=2))

if __name__=='__main__':main()
