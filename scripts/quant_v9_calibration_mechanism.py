"""Reproduce the actual V6 scaling rule and isolate its mechanical input effect."""
import json
import math
from pathlib import Path

import quant_full_financial_study as io
from quant_v9_financial_followup import BASE,V2,ROOT,error_decomposition

V5='EXPECTED_RETURN_V5_CORRECTED_20261017'
V6='EXPECTED_RETURN_V6_CORRECTED_20261017'


def prediction(row):
    value=[float(row[k]) for k in ['expectedReturnPct','expectedExcessReturnPct']]
    if not all(math.isfinite(v) for v in value):raise ValueError('Finite prediction required')
    return value


def scale(v5,v6):
    error=v6.get('calibrationErrorPct')
    error=abs(float(error)) if error is not None and math.isfinite(float(error)) else 8.
    value=.50+min(100,max(0,v5.get('confidence') or 0))*.0026-min(.18,error/100)
    if (v5.get('sampleCount') or 0)<300:value-=.08
    if (v5.get('sectorSampleCount') or 0)<40:value-=.05
    return min(.78,max(.34,value))


def counterfactual(old5,old6,new5,new6):
    before,after=scale(old5,old6),scale(new5,new6)
    a,b,c,d=map(prediction,[old5,old6,new5,new6])
    for j in range(2):
        if abs(a[j]*before-b[j])>1e-9 or abs(c[j]*after-d[j])>1e-9:
            raise ValueError('Formula does not reproduce actual V6 output')
    propagated=[before*(c[j]-a[j]) for j in range(2)]
    adaptive=[(after-before)*c[j] for j in range(2)]
    for j in range(2):
        if abs((d[j]-b[j])-(propagated[j]+adaptive[j]))>1e-9:
            raise ValueError('Scaling decomposition failed')
    return dict(beforeScale=before,afterScale=after,fixedPreviousScalePrediction=[before*v for v in c],
                propagatedInputDelta=propagated,adaptiveScaleDelta=adaptive)


def selected(path):
    rows=io.load(path);out={}
    for row in rows:
        if row['signalDate']>'2020-08-28':raise ValueError('Old60 or future snapshots forbidden')
        if row['signalDate']<'2018-04-01' or row['modelVersion'] not in [V5,V6]:continue
        key=(row['signalDate'],row['symbol'],row['modelVersion'])
        if key in out:raise ValueError('Duplicate calibration snapshot')
        out[key]=row
    return out


def main():
    oldpath=BASE/'replay/chronological-oof.json';newpath=V2/'replay/chronological-oof.json'
    old,new=selected(oldpath),selected(newpath)
    if old.keys()!=new.keys():raise ValueError('Calibration cohort drift')
    prior=io.load(V2/'comparison.json')
    truth={(r['day'],r['symbol']):r['y'] for r in prior['pairs'] if r['day']>='2018-04-01'}
    observed=[];fixed=[];audit=[]
    for day,symbol in sorted(truth):
        o5,o6,n5,n6=(mapping[(day,symbol,version)] for mapping,version in [(old,V5),(old,V6),(new,V5),(new,V6)])
        effect=counterfactual(o5,o6,n5,n6)
        row=dict(day=day,symbol=symbol,y=truth[(day,symbol)],base=prediction(o6),new=prediction(n6))
        observed.append(row);fixed.append({**row,'new':effect['fixedPreviousScalePrediction']})
        audit.append(dict(day=day,symbol=symbol,**effect))
    actual,error_fixed=error_decomposition(observed),error_decomposition(fixed)
    result=dict(classification='EXPOSED_TRAINING_CODE_MECHANISM_NOT_CAUSAL_MARKET_PROOF',old60TargetsUsed=False,
                matchedRows=len(audit),observed=actual,fixedPreviousScaleCounterfactual=error_fixed,
                adaptiveScaleMaeEffectPp=[actual['deltaMaePp'][j]-error_fixed['deltaMaePp'][j] for j in range(2)],
                increasedScaleRows=sum(r['afterScale']>r['beforeScale']+1e-12 for r in audit),
                decreasedScaleRows=sum(r['afterScale']<r['beforeScale']-1e-12 for r in audit),
                sourceHashes={str(p):io.sha(p) for p in [oldpath,newpath,V2/'comparison.json',Path(__file__),Path('src/main/java/com/kingyurina/demo/stock/StockPortfolioBacktestService.java')]},
                rowAudit=audit)
    io.save(ROOT/'calibration-mechanism.json',result)
    print(json.dumps({k:result[k] for k in ['matchedRows','adaptiveScaleMaeEffectPp','increasedScaleRows','decreasedScaleRows']}))
    print(json.dumps(dict(observedDelta=actual['deltaMaePp'],fixedPreviousScaleDelta=error_fixed['deltaMaePp'])))


if __name__=='__main__':main()
