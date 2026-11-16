"""Frozen, file-only exploratory learning of actual ten-score return weights.

No DB/network writes, model promotion, hyperparameter search, or invented factors.
Weights are percentage-point return per one factor-score point, not portfolio weights.
"""
import argparse
import csv
import hashlib
import json
import warnings
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import scipy
import sklearn
from sklearn.linear_model import Ridge, HuberRegressor, QuantileRegressor

FEATURES=['valuation','quality','growth','stability','earnings','analyst','news','momentum','risk','institution']
SETS={'FULL10':FEATURES,'FUNDAMENTALS5':FEATURES[:5],'PRICE2':['momentum','risk'],
      'WITHOUT_FLOWS7':[f for f in FEATURES if f not in ('analyst','news','institution')]}
FAMILIES=['ZERO','PAST_MEDIAN','CURRENT_V9','RIDGE_FULL10','RIDGE_FUNDAMENTALS5',
          'RIDGE_PRICE2','RIDGE_WITHOUT_FLOWS7','HUBER_FULL10','QUANTILE_FULL10']
PROTOCOL=dict(version='TEN_PRODUCTION_FACTOR_SCORES_FIXED_PROTOCOL_V1',families=FAMILIES,
    featureSets=SETS,minimumMatureTrainingDates=8,cohortRows=101,
    trainingRule='Whole decision cohort, decision < test decision AND maximum cohort exit < test decision (strict)',
    targets=['absolute','excess'],units='percentage-point return; weights are pp per one score point, NOT allocations',
    ridge=dict(alpha=100.0,fit_intercept=True),
    huber=dict(alpha=1.0,epsilon=1.35,fit_intercept=True,max_iter=3000,tol=1e-7),
    quantile=dict(quantile=0.5,alpha=0.1,fit_intercept=True,solver='highs'),
    scaling='Training mean/population SD only; zero-variation SD replaced by 1; no imputation or added missing factors',
    nonoverlapRule='Greedy chronological decisions strictly after previous accepted maximum exit; no outcomes inspected',
    primaryMetric='Pooled MAE in percentage points, paired identical full cohorts vs corrected CURRENT_V9',
    fixedBeforeFit=True,hyperparameterSearch=False,featureSearch=False,
    semantics={'RIDGE':'Regularized least-squares mean-location candidate, not a validated expected mean',
               'HUBER':'Robust Huber location, not necessarily conditional mean',
               'QUANTILE':'Conditional median candidate, MUST NOT replace expected-mean return semantics'},
    classification='EXPLORATORY_ON_20_ALREADY_EXPLORED_RECONSTRUCTED_DATES_NOT_PIT',
    databaseWrites=0,promotionAllowed=False)


def training_rows(rows,day):
    maxima={}
    for r in rows:maxima[r['day']]=max(maxima.get(r['day'],''),r['exit'])
    return [r for r in rows if r['day']<day and maxima[r['day']]<day]


def fit_model(x,y,method):
    x=np.asarray(x,dtype=float);y=np.asarray(y,dtype=float)
    if x.ndim!=2 or not len(x) or y.shape!=(len(x),) or not np.isfinite(x).all() or not np.isfinite(y).all():
        raise ValueError('Nonempty finite rectangular paired inputs required; missing values are not fabricated')
    mean=x.mean(axis=0);sd=x.std(axis=0);constant=sd<1e-12;scale=np.where(constant,1,sd)
    if method=='RIDGE':estimator=Ridge(**PROTOCOL['ridge'])
    elif method=='HUBER':estimator=HuberRegressor(**PROTOCOL['huber'])
    elif method=='QUANTILE':estimator=QuantileRegressor(**PROTOCOL['quantile'])
    else:raise ValueError(method)
    with warnings.catch_warnings(record=True) as observed:
        warnings.simplefilter('always');estimator.fit((x-mean)/scale,y)
    weights=np.asarray(estimator.coef_);raw=weights/scale
    return dict(method=method,trainingMeans=mean.tolist(),trainingScales=scale.tolist(),
                constantTrainingFeatures=constant.tolist(),standardizedWeights=weights.tolist(),
                standardizedInterceptPp=float(estimator.intercept_),rawUnitWeights=raw.tolist(),
                rawUnitInterceptPp=float(estimator.intercept_-mean@raw),
                fitWarnings=[str(w.message) for w in observed])


def predict_model(model,x):
    return np.asarray(x,dtype=float)@np.asarray(model['rawUnitWeights'])+model['rawUnitInterceptPp']


def availability_summary(rows):
    keys=sorted({k for r in rows for k in r.get('availability',{})})
    return {k:dict(trueRows=sum(r['availability'].get(k) is True for r in rows),
                   falseRows=sum(r['availability'].get(k) is False for r in rows),
                   unknownRows=sum(r['availability'].get(k) is None for r in rows)) for k in keys}


def predict_fold(rows,day):
    train=training_rows(rows,day);days=sorted({r['day'] for r in train})
    if len(days)<PROTOCOL['minimumMatureTrainingDates']:return None
    test=[r for r in rows if r['day']==day]
    if not test:raise ValueError('No test cohort')
    predictions=[];fits=[]
    for target,field,baseline in [('absolute','actual','v9Absolute'),('excess','excess','v9Excess')]:
        y=[r[field] for r in train]
        for family in FAMILIES:
            model=None
            if family=='ZERO':p=np.zeros(len(test))
            elif family=='CURRENT_V9':p=np.asarray([r[baseline] for r in test])
            elif family=='PAST_MEDIAN':
                median=float(np.median(y));p=np.full(len(test),median);model=dict(trainingMedianPp=median)
            else:
                method,set_name=family.split('_',1);names=SETS[set_name];indices=[FEATURES.index(n) for n in names]
                def x(rs):return [[r['scores'][i] for i in indices] for r in rs]
                model=fit_model(x(train),y,method);p=predict_model(model,x(test))
                model['featureNames']=names
                model['fullTenRawUnitWeights']={n:(model['rawUnitWeights'][names.index(n)] if n in names else 0.0) for n in FEATURES}
                model['factorStatus']={n:('EXCLUDED_BY_FROZEN_SET' if n not in names else 'CONSTANT_NO_IDENTIFIABLE_VARIATION' if model['constantTrainingFeatures'][names.index(n)] else 'FITTED_OBSERVED_SCORE_VARIATION_NOT_CAUSAL_IMPORTANCE') for n in FEATURES}
            if model is not None:fits.append(dict(day=day,target=target,family=family,trainingDates=days,trainingRows=len(train),
                maximumTrainingExit=max(r['exit'] for r in train),trainingInputAvailability=availability_summary(train),fit=model))
            for row,value in zip(test,p):
                if not np.isfinite(value):raise ValueError('Nonfinite forecast')
                predictions.append(dict(day=day,symbol=row['symbol'],entry=row['entry'],exit=row['exit'],target=target,
                                        family=family,predicted=float(value),actual=row[field]))
    return dict(predictions=predictions,fits=fits)


def nonoverlap_dates(rows):
    exits={}
    for r in rows:exits[r['day']]=max(exits.get(r['day'],''),r['exit'])
    selected=[];previous=''
    for day in sorted(exits):
        if day>previous:selected.append(day);previous=exits[day]
    return selected


def measures(rows):
    error=np.array([r['predicted']-r['actual'] for r in rows]);absolute=abs(error)
    if not len(error):raise ValueError('Empty metrics')
    return dict(maePp=float(absolute.mean()),rmsePp=float(np.sqrt((error**2).mean())),biasPp=float(error.mean()),
                p90AbsErrorPp=float(np.quantile(absolute,.9)),p95AbsErrorPp=float(np.quantile(absolute,.95)),
                maxAbsErrorPp=float(absolute.max()))


def load_panel(path):
    source=json.loads(Path(path).read_text(encoding='utf-8'))
    if source.get('databaseWrites')!=0 or source.get('promotionAllowed') is not False:raise ValueError('Research-only read-only replay required')
    v9={}
    for p in source['predictions']:
        if 'V9_' in p['modelVersion']:
            key=(p['signalDate'],p['symbol'])
            if key in v9:raise ValueError('Duplicate V9 forecast')
            v9[key]=p
    panel=[];seen=set();date_seen=set()
    for date in source['dates']:
        day=date['decision']
        if day in date_seen:raise ValueError('Duplicate decision cohort')
        date_seen.add(day)
        signals={r['symbol']:r for r in date['newSignals']};pairs={r['symbol']:r for r in date['pairs']}
        population=set(date['population'])
        if len(date['population'])!=101 or len(signals)!=101 or len(pairs)!=101 or len(date['newSignals'])!=101 or len(date['pairs'])!=101 or set(signals)!=population or set(pairs)!=population:raise ValueError('Incomplete/duplicate 101-member cohort')
        for symbol in sorted(population):
            key=(day,symbol)
            if key in seen:raise ValueError('Duplicate panel key')
            seen.add(key);signal=signals[symbol];pair=pairs[symbol];prediction=v9[key]
            if signal['signalDate']!=day or not (day<pair['entry']<=pair['exit']):raise ValueError('Invalid signal/outcome timing')
            scores=[signal.get(n+'Score') for n in FEATURES]
            if any(v is None or not np.isfinite(float(v)) or not 0<=float(v)<=100 for v in scores):raise ValueError('Missing/invalid score; no invented input permitted')
            if pair.get('expectedHoldingSessions')!=pair.get('observedHoldingSessions') or len(pair.get('expectedHoldingSessions',[]))!=21:raise ValueError('Incomplete 20-session outcome')
            actual=float(pair['actual']);benchmark=float(pair['benchmark'])
            for field,expected in [('expectedReturnPct','newAbsolute'),('expectedExcessReturnPct','newExcess')]:
                if abs(float(prediction[field])-float(pair[expected]))>1e-8:raise ValueError('Corrected V9 baseline mismatch')
            raw=json.loads(signal['rawJson']);fresh=json.loads(signal['sourceFreshnessJson'])
            availability=dict(annualAccessionAvailable=bool(raw.get('annualAccession')),previousAnnualAccessionAvailable=bool(raw.get('previousAnnualAccession')),
                quarterAccessionAvailable=bool(raw.get('quarterAccession')),previousQuarterAccessionAvailable=bool(raw.get('previousQuarterAccession')),
                analystRecommendationPeriodAvailable=raw.get('recommendationPeriodDate') is not None,
                institutionQuarterAvailable=raw.get('institution13fQuarter') is not None,
                institutionSharesFlowAvailable=raw.get('institutionSharesChangePct') is not None,
                institutionValueFlowAvailable=raw.get('institutionValueChangePct') is not None,
                newsEvidenceCountPositive=(int(raw['newsCount'])>0 if raw.get('newsCount') is not None else None),
                candleSnapshotDateMatches=(fresh.get('latestCandleDate')==day))
            panel.append(dict(day=day,symbol=symbol,entry=pair['entry'],exit=pair['exit'],actual=actual,excess=actual-benchmark,
                benchmark=benchmark,v9Absolute=float(pair['newAbsolute']),v9Excess=float(pair['newExcess']),scores=[float(v) for v in scores],
                availability=availability,dataQualityScore=signal.get('dataQualityScore'),dataQualityIssueCount=signal.get('dataQualityIssueCount'),
                dataQualityExcludedMetricCount=signal.get('dataQualityExcludedMetricCount')))
    if len(date_seen)!=20 or len(panel)!=2020 or len(v9)!=2020:raise ValueError('Expected all20 dates, 2020 score/outcome/V9 rows')
    for r in panel:
        if not np.isfinite([r['actual'],r['excess'],r['v9Absolute'],r['v9Excess']]).all():raise ValueError('Nonfinite outcome/baseline')
    return sorted(panel,key=lambda r:(r['day'],r['symbol'])),source


def evaluate(panel):
    forecasts=[];fits=[];skipped=[]
    for day in sorted({r['day'] for r in panel}):
        fold=predict_fold(panel,day)
        if fold is None:skipped.append(dict(day=day,reason='Fewer than8 fully mature prior date cohorts'));continue
        forecasts.extend(fold['predictions']);fits.extend(fold['fits'])
    if not forecasts:raise ValueError('No eligible folds')
    grouped=defaultdict(list)
    for p in forecasts:grouped[p['target'],p['family'],p['day']].append(p)
    daily=[dict(target=t,family=f,day=d,rows=len(rs),**measures(rs)) for (t,f,d),rs in sorted(grouped.items())]
    baseline={(r['target'],r['day']):r for r in daily if r['family']=='CURRENT_V9'}
    for r in daily:r['maeDeltaVsV9Pp']=r['maePp']-baseline[r['target'],r['day']]['maePp']
    all_days=sorted({p['day'] for p in forecasts});nonoverlap=nonoverlap_dates([r for r in panel if r['day'] in all_days]);summary=[]
    for subset,days in [('allCommonDates',all_days),('outcomeBlindNonoverlapDescriptive',nonoverlap)]:
        for target in ('absolute','excess'):
            bm=measures([p for p in forecasts if p['target']==target and p['family']=='CURRENT_V9' and p['day'] in days])
            for family in FAMILIES:
                ps=[p for p in forecasts if p['target']==target and p['family']==family and p['day'] in days]
                dm=[r for r in daily if r['target']==target and r['family']==family and r['day'] in days];m=measures(ps)
                summary.append(dict(subset=subset,target=target,family=family,dates=len(days),rows=len(ps),**m,
                    maeDeltaVsV9Pp=m['maePp']-bm['maePp'],rmseDeltaVsV9Pp=m['rmsePp']-bm['rmsePp'],
                    betterDates=sum(r['maeDeltaVsV9Pp']<0 for r in dm)))
    return dict(predictions=forecasts,fits=fits,daily=daily,summary=summary,eligibleDates=all_days,nonoverlapDates=nonoverlap,skippedDates=skipped)


def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    return h.hexdigest()


def write_new(path,value):
    with Path(path).open('x',encoding='utf-8') as f:json.dump(value,f,indent=2,allow_nan=False)


def main(argv=None):
    parser=argparse.ArgumentParser();parser.add_argument('--input',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args(argv)
    args.out.mkdir(parents=True,exist_ok=False)
    write_new(args.out/'protocol.json',PROTOCOL)  # Immutable protocol precedes loading labels/fitting.
    inputs={str(p):digest(p) for p in [args.input,Path(__file__),Path(__file__).with_name('test_quant_20d_factor_weights.py')]}
    panel,source=load_panel(args.input);result=evaluate(panel)
    quality=[]
    for i,name in enumerate(FEATURES):
        values=np.array([r['scores'][i] for r in panel]);quality.append(dict(feature=name,rows=len(values),missingScores=0,minimum=float(values.min()),maximum=float(values.max()),
            standardDeviation=float(values.std()),uniqueScores=len(set(values)),neutral50Rows=int(sum(values==50)),constantAcrossAllRows=bool(np.ptp(values)<1e-12)))
    write_new(args.out/'panel.json',panel)
    result.update(classification=PROTOCOL['classification'],databaseWrites=0,promotionAllowed=False,createdAt=datetime.now(timezone.utc).isoformat(),
        inputHashes=inputs,sourceReplayClassification=source['classification'],inheritedSourceHashes=source.get('inputHashes',{}),
        versions=dict(numpy=np.__version__,scipy=scipy.__version__,sklearn=sklearn.__version__),
        featureQuality=quality,availability=availability_summary(panel),
        limitations=source.get('limitations',[])+['All20 dates already explored; this is not an untouched holdout or a production accuracy guarantee.',
            'Neutral/constant analyst, news, or institution scores are not observed historical flows; zero coefficients can mean no identifiable variation.',
            'Availability flags are source-evidence diagnostics only, not extra modeled features or proof of complete historical financial inputs.',
            'Weights are regularized predictive associations; correlated score contributions are unstable and not causal importance or portfolio allocations.',
            'Quantile predicts a median and Huber a robust location; neither may silently replace expected-mean return semantics.',
            'Overlapping outcomes violate independent-row assumptions; nonoverlap is descriptive and still drawn from already explored dates.',
            'Production-source candle-history compatibility remains inherited from replay; rerun unchanged protocol on matched-source sensitivity.'])
    write_new(args.out/'result.json',result)
    with (args.out/'summary.csv').open('x',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(result['summary'][0]));writer.writeheader();writer.writerows(result['summary'])
    outputs={p.name:digest(p) for p in sorted(args.out.iterdir()) if p.is_file()}
    write_new(args.out/'manifest.json',dict(inputHashes=inputs,outputHashes=outputs,protocolSha256=digest(args.out/'protocol.json'),databaseWrites=0,promotionAllowed=False))
    for r in result['summary']:
        if r['subset']=='allCommonDates':print(f"{r['target']:8} {r['family']:23} dates={r['dates']:2} rows={r['rows']:4} MAE={r['maePp']:.4f} deltaV9={r['maeDeltaVsV9Pp']:+.4f} RMSE={r['rmsePp']:.4f} bias={r['biasPp']:+.4f} betterDates={r['betterDates']}")
    print('IMMUTABLE_FILE_ONLY_RESULT',args.out,'promotionAllowed=false')


if __name__=='__main__':main()
