"""Reuse already reviewed filing shares as input candidates, not live forecasts."""
import json
from pathlib import Path
import pandas as pd
from sp500_v9_full_preflight import ROOT, OUT, prior_observation, digest


def main():
    destination=OUT/'local_share_recovery.json'
    if destination.exists():raise ValueError('Refusing overwrite')
    sources=[ROOT/'tmp/v9-shares-a-20261031-v1/shares-reviewed-v2.json',
             ROOT/'tmp/v9-shares-b-20261031-v1/observations.json']
    candidates=[];hashes={str(p):digest(p) for p in sources};seen=set()
    for path in sources:
        data=json.loads(path.read_text());rows=data['entries'] if isinstance(data,dict) else data
        for r in rows:
            if r['symbol'] not in {'ABNB','APP','DASH','DDOG','PLTR','GOOG','GOOGL','META'}:continue
            p=Path(r['sourcePath']);assert digest(p)==r['sourceSha256'];hashes[str(p)]=r['sourceSha256']
            if r.get('sourceSubmissionsPath'):
                p=Path(r['sourceSubmissionsPath']);assert digest(p)==r['sourceSubmissionsSha256'];hashes[str(p)]=r['sourceSubmissionsSha256']
            total=sum(c['value']*c.get('economicSharesPerShare',1) for c in r['classes'])
            if 'totalReportedAllClasses' in r:
                assert total==r['totalReportedAllClasses']
                total-=r.get('excludedIntragroupShares',0)
                assert total==r['totalEconomicCommonShares']
            else:
                assert total==r['quotedUnitEquivalentShares']
                assert total==r.get('totalCommonShares',r.get('totalEconomicCommonShares'))
            assert total>0 and r['measurementDate']<r['filedDate']
            key=(r['symbol'],r['filedDate'],r['measurementDate'])
            if key in seen:continue
            seen.add(key)
            candidates.append(dict(symbol=r['symbol'],filed_at=r['filedDate'],end_date=r['measurementDate'],
                value=total,sourcePath=r['sourcePath'],sourceSha256=r['sourceSha256']))
    frame=pd.read_csv(OUT/'daily_input_coverage.csv.gz')
    missing=frame[frame.probabilityEligible&~frame.corporateExcluded&~frame.priorShareCandidate].copy()
    available=[]
    for r in missing.itertuples():
        c=prior_observation([x for x in candidates if x['symbol']==r.symbol],r.date)
        available.append(c)
    missing['localPriorShareCandidate']=[x is not None for x in available]
    missing['localShareFiled']=[None if x is None else x['filed_at'] for x in available]
    missing['localShareValue']=[None if x is None else x['value'] for x in available]
    missing.to_csv(OUT/'local_share_recovery_coverage.csv.gz',index=False)
    left=missing[~missing.localPriorShareCandidate]
    left.groupby('symbol').agg(missingDays=('date','size'),firstMissing=('date','min'),lastMissing=('date','max')).reset_index().to_csv(OUT/'remaining_share_targets.csv',index=False)
    report=dict(status='STILL_BLOCKED_FULL_COMPARISON',reviewedLocalObservations=len(candidates),
        priorMissingRows=len(missing),newCandidateRows=int(missing.localPriorShareCandidate.sum()),
        remainingMissingRows=len(left),remainingMissingSymbols=left.symbol.nunique(),
        stillIncompleteDates=left.date.nunique(),sourceHashes=hashes,
        admittedToModel=False,limitation='Verified prior reported amounts only. Price basis, actions and full replay are still pending; no performance calculated.')
    destination.write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='sourceHashes'},indent=2))


if __name__=='__main__':main()
