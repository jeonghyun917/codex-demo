"""Null-only primary revenue overlay and exposed-training error decomposition.

This module never opens the old 60-target files or claims internal data is unseen.
"""
import argparse
import collections
import copy
import json
import math
from pathlib import Path

import quant_full_financial_study as source

ROOT=Path('tmp/quant-v9-financial-cause-20261101-v1')
V2=Path('tmp/quant-full-financial-study-20261101-v2')
BASE=source.BASE


def overlay_revenues(financials, entries):
    result=copy.deepcopy(financials)
    index={source.key(r,source.OVERRIDE_IDENTITY):r for rs in result.values() for r in rs}
    if len(index)!=sum(map(len,result.values())):
        raise ValueError('Duplicate financial source identity')
    seen=set();records=[];filled=present=0
    for entry in entries:
        key=source.key(entry['identity'],source.OVERRIDE_IDENTITY)
        if key in seen or key not in index:
            raise ValueError('Duplicate or unknown primary revenue target')
        seen.add(key);row=index[key];value=source.validate_override(entry,row)
        if row.get('revenue') is not None:
            if float(row['revenue'])!=value:
                raise ValueError('Non-null original revenue conflict')
            present+=1
            continue
        raw=json.loads(row.get('rawJson') or '{}')
        proof={k:copy.deepcopy(v) for k,v in entry['proof'].items() if k!='originalFieldProofs'}
        raw.setdefault('selectedFieldEvidence',{})['revenue']=dict(
            concept='PRIMARY_FILING_CONSOLIDATED_TOTAL_REVENUE',taxonomy='VERIFIED_PRIMARY_FILING',
            unit='USD',startDate=row['startDate'],endDate=row['endDate'],
            periodDays=row['periodDays'],primaryDocumentProof=proof)
        raw['primaryRevenueFollowup']=dict(policy='EXACT_ORIGINAL_NULL_ONLY',identity=entry['identity'])
        row['revenue']=value;row['rawJson']=json.dumps(raw,sort_keys=True);filled+=1
        records.append(dict(identity=entry['identity'],before=None,after=value,
                            sourceDocumentSha256=proof['sourceSha256']))
    return result,dict(filled=filled,alreadyPresent=present,records=records)


def error_decomposition(rows):
    if not rows: raise ValueError('Nonempty diagnostic rows required')
    seen=set();counts=collections.Counter(r['day'] for r in rows);dates=len(counts)
    groups={name:collections.defaultdict(lambda:dict(rows=0,deltaMaeContributionPp=[0.,0.]))
            for name in ['byDate','bySymbol','byQuarter']}
    mae={'before':[0.,0.],'after':[0.,0.]};cross=[0.,0.];square=[0.,0.]
    date_errors={side:collections.defaultdict(list) for side in ['base','new']}
    mse={side:[0.,0.] for side in ['base','new']}
    for row in rows:
        key=(row['day'],row['symbol'])
        values=row['y']+row['base']+row['new']
        if key in seen or row['day']>'2020-08-28' or any(len(row[k])!=2 for k in ['y','base','new']) or not all(math.isfinite(v) for v in values):
            raise ValueError('Duplicate, nonfinite or forbidden diagnostic row')
        seen.add(key);weight=1/(counts[row['day']]*dates)
        old=[row['base'][j]-row['y'][j] for j in range(2)]
        new=[row['new'][j]-row['y'][j] for j in range(2)]
        delta=[row['new'][j]-row['base'][j] for j in range(2)]
        contribution=[weight*(abs(new[j])-abs(old[j])) for j in range(2)]
        for j in range(2):
            mae['before'][j]+=weight*abs(old[j]);mae['after'][j]+=weight*abs(new[j])
            cross[j]+=weight*2*old[j]*delta[j];square[j]+=weight*delta[j]**2
            mse['base'][j]+=weight*old[j]**2;mse['new'][j]+=weight*new[j]**2
        date_errors['base'][row['day']].append(old);date_errors['new'][row['day']].append(new)
        quarter=row['day'][:4]+'Q'+str((int(row['day'][5:7])-1)//3+1)
        for name,label in [('byDate',row['day']),('bySymbol',row['symbol']),('byQuarter',quarter)]:
            group=groups[name][label];group['rows']+=1
            for j in range(2): group['deltaMaeContributionPp'][j]+=contribution[j]
    delta=[mae['after'][j]-mae['before'][j] for j in range(2)]
    for name in groups:
        for j in range(2):
            if not math.isclose(sum(g['deltaMaeContributionPp'][j] for g in groups[name].values()),delta[j],abs_tol=1e-10):
                raise ValueError('Error contribution reconciliation failed')
    common={side:[sum((sum(e[j] for e in g)/len(g))**2 for g in dates_.values())/dates for j in range(2)]
            for side,dates_ in date_errors.items()}
    for j in range(2):
        if not math.isclose(mse['new'][j]-mse['base'][j],cross[j]+square[j],abs_tol=1e-9):
            raise ValueError('Squared-error identity failed')
    return dict(rows=len(rows),dates=dates,beforeMaePp=mae['before'],afterMaePp=mae['after'],deltaMaePp=delta,
                squaredErrorIdentity=dict(baseErrorCrossTerm=cross,predictionDeltaSquared=square),
                mse=mse,dateCommonErrorMse=common,
                **{name:dict(value) for name,value in groups.items()})


def snapshots(path):
    records=source.load(path);result={}
    for p in records:
        if p['horizonDays']!=20 or p['signalDate']>'2020-08-28':
            raise ValueError('Forbidden diagnostic snapshot')
        if p['signalDate']<'2018-04-01':continue
        key=(p['modelVersion'],p['signalDate'],p['symbol'])
        if key in result:raise ValueError('Duplicate model snapshot')
        result[key]=[float(p['expectedReturnPct']),float(p['expectedExcessReturnPct'])]
    return result


def diagnose():
    if ROOT.exists():raise ValueError('Never overwrite diagnostic evidence')
    path=V2/'comparison.json'
    if source.sha(path)!='7f763a8dfd416d716d4b516bda38fcdfd64bd4b2177b4eff29b92cc62c6fcc61':
        raise ValueError('Prior comparison drift')
    comparison=source.load(path);pairs=comparison['pairs'];primary=[r for r in pairs if r['day']>='2018-04-01']
    result=error_decomposition(primary)
    for j,head in enumerate(['Absolute','Excess']):
        if abs(result['deltaMaePp'][j]-comparison['bootstrap'][head]['deltaMaePp'])>1e-10:
            raise ValueError('Prior headline mismatch')
    truth={(r['day'],r['symbol']):r['y'] for r in primary}
    before_path=BASE/'replay/chronological-oof.json';after_path=V2/'replay/chronological-oof.json'
    before=snapshots(before_path);after=snapshots(after_path)
    if before.keys()!=after.keys():raise ValueError('Stage cohort changed')
    stages={}
    for version in sorted({k[0] for k in before}):
        rows=[dict(day=d,symbol=s,y=truth[(d,s)],base=before[(v,d,s)],new=after[(v,d,s)])
              for v,d,s in before if v==version]
        if len(rows)!=17904:raise ValueError('Incomplete stage')
        full=error_decomposition(rows)
        stages[version]={k:v for k,v in full.items() if not k.startswith('by')}
    receipt=dict(classification='EXPOSED_TRAINING_DIAGNOSTIC_NOT_CAUSAL_OR_NEW_HOLDOUT',old60TargetsUsed=False,
                 sourceHashes={str(p):source.sha(p) for p in [path,before_path,after_path,Path(__file__)]},
                 primary=result,modelStages=stages)
    ROOT.mkdir();source.save(ROOT/'diagnostic.json',receipt)
    print(json.dumps(dict(delta=result['deltaMaePp'],stages={k:v['deltaMaePp'] for k,v in stages.items()},
                         largestAbsoluteDeterioration=sorted(result['bySymbol'].items(),key=lambda item:item[1]['deltaMaeContributionPp'][0],reverse=True)[:8],
                         mse=result['mse'],dateCommonErrorMse=result['dateCommonErrorMse']),indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['diagnose']);parser.parse_args();diagnose()
