"""Descriptive bins of closed historical strategy trades, not universe calibration."""
import argparse
from bisect import bisect_right
import csv
from datetime import datetime, timezone
import gzip
import hashlib
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
HISTORY=ROOT/'output/sp500-actions-20261028-retry'
OUTPUT=ROOT/'output/sp500-probability-bins-20261101'
PERIODS={
    'FROZEN_BASELINE_REPLAY': ('baseline','기존 검증 구간',ROOT/'output/sp500-stable-ranking-20261027/predictions.csv.gz'),
    'NEWLY_INSPECTED_RETROSPECTIVE_WINDOW': ('short','짧은 과거 구간',HISTORY/'unseen_research_predictions.csv.gz'),
}


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def read_rows(path):
    opener=gzip.open if path.suffix=='.gz' else open
    with opener(path,'rt',encoding='utf-8',newline='') as handle:return list(csv.DictReader(handle))


def bucket_index(probability):
    if not math.isfinite(probability) or not 0<=probability<=1:raise ValueError('INVALID_PROBABILITY')
    return bisect_right([i/10 for i in range(1,10)],probability)


def join_trades(fills,predictions):
    result=[];seen=set()
    for raw in fills:
        key=(raw['decision'],raw['symbol'])
        if key in seen:raise ValueError('DUPLICATE_TRADE')
        seen.add(key)
        if key not in predictions:raise ValueError('MISSING_PREDICTION: '+str(key))
        probability=predictions[key];bucket_index(probability)
        values={k:float(raw[k]) for k in ('budget','proceeds','pnl','fee','entry_price','exit_price','shares')}
        if not all(math.isfinite(v) for v in values.values()):raise ValueError('NONFINITE_TRADE')
        if any(values[k]<=0 for k in ('budget','entry_price','exit_price','shares')):raise ValueError('INVALID_TRADE_AMOUNT')
        if values['fee'] not in (.001,.002):raise ValueError('UNEXPECTED_FEE')
        if not raw['decision']<raw['entry']<=raw['exit']:raise ValueError('INVALID_TRADE_ORDER')
        if raw['reason'] not in ('TP','SL','SL_GAP','TIME'):raise ValueError('UNKNOWN_EXIT')
        checks=[(values['budget'],values['entry_price']*values['shares']*(1+values['fee'])),
                (values['proceeds'],values['exit_price']*values['shares']*(1-values['fee'])),
                (values['pnl'],values['proceeds']-values['budget'])]
        if not all(math.isclose(a,b,rel_tol=1e-10,abs_tol=1e-7) for a,b in checks):raise ValueError('TRADE_ACCOUNTING_MISMATCH')
        result.append(dict(symbol=raw['symbol'],decision=raw['decision'],entry=raw['entry'],exit=raw['exit'],
            probability=probability,binIndex=bucket_index(probability),reason=raw['reason'],
            netReturnPct=100*values['pnl']/values['budget'],**values))
    return result


def summarize(rows):
    n=len(rows)
    counts=[sum(r['reason']==reason for r in rows) for reason in ('TP','TIME')]
    stops=sum(r['reason'] in ('SL','SL_GAP') for r in rows)
    if counts[0]+counts[1]+stops!=n:raise ValueError('UNCLASSIFIED_EXIT')
    return dict(count=n,tpCount=counts[0],slCount=stops,timeCount=counts[1],
        tpRatePct=100*counts[0]/n if n else None,slRatePct=100*stops/n if n else None,
        timeRatePct=100*counts[1]/n if n else None,
        meanProbabilityPct=100*math.fsum(r['probability'] for r in rows)/n if n else None,
        meanNetReturnPct=math.fsum(r['netReturnPct'] for r in rows)/n if n else None)


def aggregate(trades):
    bins=[]
    for i in range(10):
        rows=[r for r in trades if bucket_index(r['probability'])==i]
        bins.append(dict(index=i,lower=i/10,upper=(i+1)/10,upperInclusive=i==9,**summarize(rows)))
    return dict(summary=summarize(trades),bins=bins)


def calculate():
    verification=HISTORY/'revalidation_verification.json'
    verified=json.loads(verification.read_text(encoding='utf-8'))
    if verified['status']!='PASS':raise ValueError('UNVERIFIED_HISTORY')
    for path,expected in verified['source_hashes'].items():
        if digest(Path(path))!=expected:raise ValueError('HISTORICAL_SOURCE_CHANGED: '+path)
    sources=[verification,HISTORY/'revalidation.csv',HISTORY/'revalidation_fills.csv',
             *[v[2] for v in PERIODS.values()]]
    before={str(p.relative_to(ROOT)):digest(p) for p in sources}
    accounts=read_rows(HISTORY/'revalidation.csv')
    fills=read_rows(HISTORY/'revalidation_fills.csv')
    groups=[];joined=[]
    for scenario,(period,label,path) in PERIODS.items():
        relevant=[r for r in fills if r['scenario']==scenario and r['policy']=='TREND50_VOL75']
        keys={(r['decision'],r['symbol']) for r in relevant}
        predictions={}
        for r in read_rows(path):
            key=(r['date'],r['symbol'])
            if key not in keys:continue
            if key in predictions:raise ValueError('DUPLICATE_PREDICTION')
            predictions[key]=float(r['probability'])
        for fee in (.001,.002):
            matches=[a for a in accounts if a['scenario']==scenario and a['policy']=='TREND50_VOL75' and float(a['fee'])==fee]
            if len(matches)!=1 or matches[0]['status']!='COMPLETE_RESEARCH_ONLY':raise ValueError('INCOMPLETE_ACCOUNT')
            account=matches[0]
            trades=join_trades([r for r in relevant if float(r['fee'])==fee],predictions)
            if len(trades)!=int(account['trades']):raise ValueError('TRADE_COUNT_MISMATCH')
            if not math.isclose(math.fsum(t['pnl'] for t in trades)/1000,float(account['total_return_pct']),abs_tol=1e-8):
                raise ValueError('ACCOUNT_RETURN_MISMATCH')
            if any(not account['start']<=t['decision']<=account['last_decision'] or t['exit']>account['evaluated_end'] for t in trades):
                raise ValueError('TRADE_OUTSIDE_VALIDATION_WINDOW')
            groups.append(dict(period=period,label=label,scenario=scenario,fee=fee,start=account['start'],
                lastDecision=account['last_decision'],end=account['evaluated_end'],**aggregate(trades)))
            joined.extend(dict(period=period,scenario=scenario,**t) for t in trades)
    if any(digest(ROOT/p)!=h for p,h in before.items()):raise ValueError('SOURCE_CHANGED_DURING_CALCULATION')
    return dict(version='SP500_CLOSED_TRADE_PROBABILITY_BINS_V1',createdAtUtc=datetime.now(timezone.utc).isoformat(),
        classification='RETROSPECTIVE_SELECTED_CLOSED_TRADES_NOT_UNIVERSE_CALIBRATION',
        policy='TREND50_VOL75',strategyChanged=False,sourceHashes=before,
        methodology=dict(binWidth=.1,bounds='[lower,upper), last bin includes 1.0',
            denominator='Closed simulated trades only, separately by period and fee. No pooled duplicated fee scenarios.',
            stopReasons=['SL','SL_GAP'],targetReason='TP',timeoutReason='TIME',
            returnFormula='100 * (net_sale_proceeds - entry_cost_including_fee) / entry_cost_including_fee',
            average='Arithmetic mean of per-trade net return; not capital-weighted, annualized or cash-inclusive account return.',
            limitations='Already inspected research windows. Selection and repeated-security dependence remain. Empty bins are null, not zero. Fewer than 30 trades are flagged descriptively, not a statistical significance threshold.'),
        groups=groups),joined


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=OUTPUT);args=parser.parse_args()
    if args.out.exists():raise ValueError('Refusing to overwrite existing audit output')
    report,trades=calculate()
    args.out.mkdir(parents=True)
    (args.out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2,allow_nan=False),encoding='utf-8')
    with (args.out/'joined_trades.csv').open('w',encoding='utf-8',newline='') as handle:
        writer=csv.DictWriter(handle,fieldnames=list(trades[0]));writer.writeheader();writer.writerows(trades)
    print(json.dumps(dict(groups=[dict(period=g['period'],fee=g['fee'],**g['summary']) for g in report['groups']]),indent=2))


if __name__=='__main__':main()
