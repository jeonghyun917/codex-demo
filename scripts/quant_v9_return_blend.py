"""Two prespecified return-loss research candidates, never probability-loss tuning."""
import argparse
import collections
import json
import math
from pathlib import Path

import numpy as np
import quant_full_financial_study as io
import quant_v9_nested_training as metrics
import quant_v9_followup_replay as replay

ROOT=Path('tmp/quant-v9-return-blend-20261101-v1')
GRID=[0.,.25,.5,.75,1.]
DESIGN=Path('docs/superpowers/specs/2026-11-01-v9-calibration-candidates-design.md')


def blend(v5,v9,weight):
    if not 0<=weight<=1 or len(v5)!=2 or len(v9)!=2 or not all(math.isfinite(x) for x in v5+v9):
        raise ValueError('Finite paired heads and convex weight required')
    return [(1-weight)*v9[j]+weight*v5[j] for j in range(2)]


def fit_blend(rows,cutoff):
    seen=set()
    for row in rows:
        key=(row['day'],row['symbol'])
        if key in seen or row['day']<= '2020-12-01' and row['exit']>='2020-10-01':
            raise ValueError('Duplicate or old60 overlapping training window')
        seen.add(key)
    admitted=[r for r in rows if r['day']<cutoff and r['exit']<cutoff]
    dates=sorted({r['day'] for r in admitted})[-252:];allowed=set(dates)
    admitted=[r for r in admitted if r['day'] in allowed]
    result=dict(weight=0.,trainRows=len(admitted),trainDates=len(dates),cutoff=cutoff,
                maxTrainExit=max((r['exit'] for r in admitted),default=None),reason='INSUFFICIENT_HISTORY')
    if len(admitted)<1000 or len(dates)<60:return result
    baseline=metrics.summarize(admitted,np.array([r['v9'] for r in admitted]))
    candidates=[]
    for weight in GRID[1:]:
        score=metrics.summarize(admitted,np.array([blend(r['v5'],r['v9'],weight) for r in admitted]))
        if all(score[h]['maePp']<baseline[h]['maePp']-1e-12 and score[h]['rmsePp']<=baseline[h]['rmsePp']+1e-12 for h in ['Absolute','Excess']):
            ratio=sum(score[h]['maePp']/max(baseline[h]['maePp'],1e-12) for h in ['Absolute','Excess'])/2
            candidates.append((ratio,weight))
    result.update(weight=min(candidates)[1] if candidates else 0.,reason='JOINT_MAE_RMSE_GUARD' if candidates else 'NO_JOINT_ELIGIBLE_WEIGHT')
    return result


def require_unseen_gate(audit):
    if audit.get('certified') is not True or audit.get('classification') not in ['AUDITED_UNEXPOSED_HISTORICAL','PROSPECTIVE_PRE_ENTRY_FROZEN']:
        raise ValueError('Do not score uncertified/internal observations as unseen')


def prepare():
    if ROOT.exists():raise ValueError('Never overwrite candidate protocol')
    if (replay.ROOT/'comparison.json').exists():raise ValueError('Candidate choice must precede follow-up metric inspection')
    ROOT.mkdir()
    paths=[Path(__file__),Path('scripts/test_quant_v9_return_blend.py'),DESIGN,replay.ROOT/'protocol.json',
           Path('tmp/quant-v9-financial-cause-20261101-v1/diagnostic.json'),Path('tmp/quant-v9-financial-cause-20261101-v1/calibration-mechanism.json'),
           Path('tmp/quant-v9-unseen-audit-20261101-v1/audit.json')]
    protocol=dict(classification='FROZEN_RETURN_CALIBRATION_CANDIDATES_NOT_VALIDATED',
                  design=DESIGN.read_text(encoding='utf-8'),sourceHashes={str(p):io.sha(p) for p in paths},
                  candidates=['half_upstream','past_mae_blend'],weights=GRID,historyDecisionDates=252,minDates=60,minRows=1000,
                  bothMaeImprovementAndBothRmseNonworseningRequired=True,refit='QUARTER_START_STRICTLY_MATURE_LABELS',
                  old60TargetsUsed=False,productionChanges=False,promotionAllowed=False,
                  historicalUnseenReady=False,followupMetricsViewed=False)
    io.save(ROOT/'protocol.json',protocol)
    print(json.dumps(dict(protocolSha256=io.sha(ROOT/'protocol.json'),candidateCount=2)))


def training_panel():
    archive=io.load(replay.ROOT/'replay/forecast-only.json')
    truth={(r['signalDate'],r['symbol']):r for r in archive['trainingRows']}
    raw=io.load(replay.ROOT/'replay/chronological-oof.json');stages={}
    for p in raw:
        if p['signalDate']>'2020-08-28':raise ValueError('Forbidden old60/future source')
        if p['modelVersion'] not in ['EXPECTED_RETURN_V5_CORRECTED_20261017','EXPECTED_RETURN_V9_CORRECTED_20261017']:continue
        key=(p['signalDate'],p['symbol']);stage='v5' if '_V5_' in p['modelVersion'] else 'v9'
        item=stages.setdefault(key,{})
        if stage in item:raise ValueError('Duplicate stage prediction')
        item[stage]=[float(p['expectedReturnPct']),float(p['expectedExcessReturnPct'])]
    rows=[]
    for key,item in sorted(stages.items()):
        if item.keys()!={'v5','v9'}:raise ValueError('Incomplete stage pair')
        r=truth[key];absolute=float(r['forwardReturnPct']);excess=absolute-float(r['benchmarkReturnPct'])
        rows.append(dict(day=key[0],symbol=key[1],exit=r['exitTradeDate'],y=[absolute,excess],**item))
    if len(rows)!=25473:raise ValueError('Incomplete actual rebuilt OOF')
    return rows


def internal():
    protocol=io.load(ROOT/'protocol.json');io.utility.verify_sources(protocol['sourceHashes'])
    rows=training_panel();primary=[r for r in rows if r['day']>='2018-04-01'];base=np.array([r['v9'] for r in primary])
    models={};predictions={'half_upstream':[],'past_mae_blend':[]}
    for row in primary:
        quarter=row['day'][:4]+'-'+str(1+(int(row['day'][5:7])-1)//3*3).zfill(2)+'-01'
        if quarter not in models:models[quarter]=fit_blend(rows,quarter)
        predictions['half_upstream'].append(blend(row['v5'],row['v9'],.5))
        predictions['past_mae_blend'].append(blend(row['v5'],row['v9'],models[quarter]['weight']))
    if len(primary)!=17904:raise ValueError('Primary population changed')
    scores={name:metrics.summarize(primary,np.array(pred)) for name,pred in predictions.items()}
    intervals={name:metrics.block_interval(primary,np.array(pred),base) for name,pred in predictions.items()}
    output=dict(classification='EXPOSED_TRAINING_DIAGNOSTIC_NOT_UNUSED_EVALUATION',rows=len(primary),dates=len({r['day'] for r in primary}),
                baseline=metrics.summarize(primary,base),scores=scores,intervals=intervals,quarterFits=models,
                finalForwardFit=fit_blend(rows,'2020-09-30'),protocolSha256=io.sha(ROOT/'protocol.json'),
                sourceForecastSha256=io.sha(replay.ROOT/'replay/forecast-only.json'),old60TargetsUsed=False,promotionAllowed=False)
    io.save(ROOT/'internal-result.json',output)
    print(json.dumps(output,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['prepare','internal']);args=parser.parse_args()
    prepare() if args.mode=='prepare' else internal()
