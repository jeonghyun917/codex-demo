"""Independently reconcile the published bins with pandas joins and cash flows."""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
import pandas as pd

ROOT=Path(__file__).resolve().parents[1]


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=ROOT/'output/sp500-probability-bins-20261101');args=parser.parse_args()
    report_path=args.out/'report.json';report=json.loads(report_path.read_text(encoding='utf-8'))
    for source,expected in report['sourceHashes'].items():assert digest(ROOT/source)==expected,source
    fills=pd.read_csv(ROOT/'output/sp500-actions-20261028-retry/revalidation_fills.csv')
    audit=pd.read_csv(args.out/'joined_trades.csv')
    sources={'baseline':ROOT/'output/sp500-stable-ranking-20261027/predictions.csv.gz',
             'short':ROOT/'output/sp500-actions-20261028-retry/unseen_research_predictions.csv.gz'}
    count=0
    for group in report['groups']:
        trades=fills[(fills.scenario==group['scenario'])&(fills.policy=='TREND50_VOL75')&(fills.fee==group['fee'])].copy()
        pred=pd.read_csv(sources[group['period']],usecols=['date','symbol','probability'])
        trades=trades.merge(pred,left_on=['decision','symbol'],right_on=['date','symbol'],validate='one_to_one',how='left')
        assert trades.probability.notna().all() and trades.probability.between(0,1).all()
        trades['net']=100*(trades.exit_price*(1-trades.fee)/(trades.entry_price*(1+trades.fee))-1)
        edges=list(np.arange(11)/10);edges[-1]=np.nextafter(1.,2.)
        trades['bin']=pd.cut(trades.probability,edges,right=False,labels=False)
        saved=audit[(audit.period==group['period'])&(audit.fee==group['fee'])]
        match=trades.merge(saved,on=['decision','symbol'],validate='one_to_one',suffixes=('','_audit'))
        assert len(match)==len(trades)==len(saved)
        assert np.allclose(match.probability,match.probability_audit,rtol=0,atol=1e-14)
        assert np.allclose(match.net,match.netReturnPct,rtol=0,atol=1e-10)
        assert (match.reason==match.reason_audit).all()
        for expected in [group['summary'],*group['bins']]:
            subset=trades if 'index' not in expected else trades[trades['bin']==expected['index']]
            n=len(subset);assert expected['count']==n
            tp=int((subset.reason=='TP').sum());sl=int(subset.reason.isin(['SL','SL_GAP']).sum());time=int((subset.reason=='TIME').sum())
            assert tp+sl+time==n
            assert [expected['tpCount'],expected['slCount'],expected['timeCount']]==[tp,sl,time]
            metrics={'tpRatePct':100*tp/n if n else None,'slRatePct':100*sl/n if n else None,
                     'timeRatePct':100*time/n if n else None,
                     'meanProbabilityPct':subset.probability.mean()*100 if n else None,
                     'meanNetReturnPct':subset.net.mean() if n else None}
            for key,value in metrics.items():
                if n:assert abs(expected[key]-value)<1e-10,(key,expected[key],value)
                else:assert expected[key] is None
        count+=len(trades)
    result=dict(status='PASS',groups=len(report['groups']),bins=sum(len(g['bins']) for g in report['groups']),
        matchedTradesAcrossSeparateFees=count,reportSha256=digest(report_path),joinedTradesSha256=digest(args.out/'joined_trades.csv'),
        checks=['source hashes','independent one-to-one joins','independent pd.cut boundaries','gross prices and two-sided fees',
                'all bins and totals reconciled','gap stops included','empty bins remain null'])
    (args.out/'verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))


if __name__=='__main__':main()
