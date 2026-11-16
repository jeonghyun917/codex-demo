"""Independent saved-artifact arithmetic verifier; never imports the fitting code."""
import argparse
import hashlib
import json
import math
import statistics
from collections import Counter
from pathlib import Path
from datetime import datetime,timezone

FEATURES=['valuation','quality','growth','stability','earnings','analyst','news','momentum','risk','institution']


def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda:stream.read(1048576),b''):h.update(chunk)
    return h.hexdigest()


def close(a,b,tolerance=1e-8):
    assert math.isfinite(a) and math.isfinite(b) and abs(a-b)<tolerance,(a,b)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();root=args.out
    manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
    for filename,sha in manifest['outputHashes'].items():assert digest(root/filename)==sha,filename
    for filename,sha in manifest['inputHashes'].items():assert digest(filename)==sha,filename
    result=json.loads((root/'result.json').read_text(encoding='utf-8'));panel=json.loads((root/'panel.json').read_text(encoding='utf-8'));protocol=json.loads((root/'protocol.json').read_text(encoding='utf-8'))
    assert result['promotionAllowed'] is False and result['databaseWrites']==0 and len(panel)==2020
    assert protocol['minimumMatureTrainingDates']==8 and protocol['ridge']['alpha']==100 and protocol['huber']['alpha']==1
    assert protocol['quantile']['alpha']==.1 and protocol['quantile']['quantile']==.5 and len(protocol['families'])==9
    records={(r['day'],r['symbol']):r for r in panel};assert len(records)==2020
    cohort_sizes=Counter(r['day'] for r in panel);assert len(cohort_sizes)==20 and set(cohort_sizes.values())=={101}
    inputs=[Path(p) for p in manifest['inputHashes'] if Path(p).name=='result.json'];assert len(inputs)==1
    source=json.loads(inputs[0].read_text(encoding='utf-8'));source_rows={}
    for d in source['dates']:
        signal={s['symbol']:s for s in d['newSignals']}
        for p in d['pairs']:
            key=(d['decision'],p['symbol']);assert key not in source_rows;source_rows[key]=p
            r=records[key];assert r['scores']==[float(signal[p['symbol']][n+'Score']) for n in FEATURES]
            close(r['actual'],float(p['actual']));close(r['excess'],float(p['actual'])-float(p['benchmark']))
            close(r['v9Absolute'],float(p['newAbsolute']));close(r['v9Excess'],float(p['newExcess']))
    assert len(source_rows)==2020
    exits={day:max(r['exit'] for r in panel if r['day']==day) for day in cohort_sizes}
    eligible={day:[d for d in sorted(cohort_sizes) if d<day and exits[d]<day] for day in cohort_sizes}
    actual_eligible=sorted(d for d,train in eligible.items() if len(train)>=8);assert result['eligibleDates']==actual_eligible
    fits={(f['day'],f['target'],f['family']):f for f in result['fits']};assert len(fits)==len(result['fits'])
    diagnostics=[]
    for f in result['fits']:
        train_days=eligible[f['day']];train=[r for r in panel if r['day'] in train_days]
        assert f['trainingDates']==train_days and f['trainingRows']==len(train)==len(train_days)*101
        assert f['maximumTrainingExit']==max(r['exit'] for r in train)<f['day']
        model=f['fit']
        if 'featureNames' not in model:continue
        for i,name in enumerate(model['featureNames']):
            values=[r['scores'][FEATURES.index(name)] for r in train];mean=statistics.mean(values);sd=statistics.pstdev(values);scale=sd if sd>=1e-12 else 1
            close(mean,model['trainingMeans'][i]);close(scale,model['trainingScales'][i]);close(model['standardizedWeights'][i]/scale,model['rawUnitWeights'][i])
            assert model['constantTrainingFeatures'][i]==(sd<1e-12)
            if f['family']=='RIDGE_FULL10' and name in ('analyst','news','institution'):
                test_values=[r['scores'][FEATURES.index(name)] for r in panel if r['day']==f['day']]
                diagnostics.append(dict(day=f['day'],target=f['target'],factor=name,trainRows=len(values),trainMean=mean,trainSD=sd,trainMin=min(values),trainMax=max(values),
                    trainUnique=len(set(values)),testMin=min(test_values),testMax=max(test_values),testUnique=len(set(test_values)),
                    rawUnitWeight=model['rawUnitWeights'][i],standardizedWeight=model['standardizedWeights'][i],constantUnidentified=sd<1e-12))
        close(model['rawUnitInterceptPp'],model['standardizedInterceptPp']-sum(m*w for m,w in zip(model['trainingMeans'],model['rawUnitWeights'])))
    predictions=result['predictions'];seen=set();groups={};learned=0
    for p in predictions:
        key=(p['day'],p['symbol'],p['target'],p['family']);assert key not in seen;seen.add(key)
        row=records[p['day'],p['symbol']];target='actual' if p['target']=='absolute' else 'excess';close(p['actual'],row[target])
        if p['family']=='ZERO':expected=0
        elif p['family']=='CURRENT_V9':expected=row['v9Absolute' if p['target']=='absolute' else 'v9Excess']
        elif p['family']=='PAST_MEDIAN':expected=statistics.median(r[target] for r in panel if r['day'] in eligible[p['day']])
        else:
            model=fits[p['day'],p['target'],p['family']]['fit'];values=[row['scores'][FEATURES.index(n)] for n in model['featureNames']]
            expected=sum(v*w for v,w in zip(values,model['rawUnitWeights']))+model['rawUnitInterceptPp']
            standardized=sum((v-m)/s*w for v,m,s,w in zip(values,model['trainingMeans'],model['trainingScales'],model['standardizedWeights']))+model['standardizedInterceptPp']
            close(expected,standardized);learned+=1
        close(expected,p['predicted']);groups.setdefault((p['day'],p['target'],p['family']),[]).append(p)
    assert len(groups)==len(actual_eligible)*2*9 and all(len(g)==101 for g in groups.values())
    chosen=[];previous=''
    for day in actual_eligible:
        if day>previous:chosen.append(day);previous=exits[day]
    assert chosen==result['nonoverlapDates']
    for summary in result['summary']:
        days=actual_eligible if summary['subset']=='allCommonDates' else chosen
        rows=[p for p in predictions if p['day'] in days and p['target']==summary['target'] and p['family']==summary['family']]
        errors=[p['predicted']-p['actual'] for p in rows]
        assert len(rows)==summary['rows']==len(days)*101
        close(sum(abs(e) for e in errors)/len(errors),summary['maePp'])
        close(math.sqrt(sum(e*e for e in errors)/len(errors)),summary['rmsePp'])
        close(sum(errors)/len(errors),summary['biasPp'])
        baseline=[p for p in predictions if p['day'] in days and p['target']==summary['target'] and p['family']=='CURRENT_V9']
        close(summary['maePp']-sum(abs(p['predicted']-p['actual']) for p in baseline)/len(baseline),summary['maeDeltaVsV9Pp'])
    proof=dict(verifiedAt=datetime.now(timezone.utc).isoformat(),verifierSha256=digest(__file__),sourceSha256=digest(inputs[0]),
        resultSha256=digest(root/'result.json'),frozenProtocolSha256=digest(root/'protocol.json'),allInputAndOutputHashesMatch=True,
        independentForecastArithmeticRows=len(predictions),rawAndStandardizedForecastArithmeticRows=learned,
        strictMaturityFolds=len(actual_eligible),fullCohortRows=101,independentSummaryCount=len(result['summary']),
        flowFactorDiagnostics=diagnostics,databaseWrites=0,promotionAllowed=False,
        sourceNature='Research-staged matched financial overlays; not a claim that all overlays were imported into production DB')
    with (root/'independent-verification.json').open('x',encoding='utf-8') as f:json.dump(proof,f,indent=2,allow_nan=False)
    print('VERIFIED',len(predictions),'saved forecasts;',learned,'raw+standardized learned forecasts;',len(result['summary']),'summary arithmetic checks; all hashes and strict maturity pass')


if __name__=='__main__':main()
