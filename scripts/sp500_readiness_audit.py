"""Offline readiness audit. No provider calls, DB writes or ledger mutations."""
import argparse
from datetime import datetime,timezone
import json
from pathlib import Path
from build_sp500_daily_dataset import digest
from quant_forward_validation import timestamp
import sp500_paper_ledger as ledger


def assess(records,calendar,payload,now):
    days=[r['session'] for r in calendar]
    closed=[r for r in calendar if timestamp(r['closeUtc'])<=now]
    if not records or not closed:raise ValueError('No captured or closed sessions')
    known={r['anchor'] for r in records};start=min(known)
    missing=[r['session'] for r in closed if r['session']>=start and r['session'] not in known]
    pending=[];missed=[]
    for day in missing:
        index=days.index(day)
        if index+1<len(calendar) and now<timestamp(calendar[index+1]['openUtc']):pending.append(day)
        else:missed.append(day)
    projected={r['date']:r for r in payload['days']}
    cohorts=[];scores=[]
    for record in records:
        top=sorted(record['predictions'],key=lambda r:(-r['probability'],r['symbol']))[:20]
        tracking=projected.get(record['anchor'],{}).get('tracking',{})
        rows={r['symbol']:r for r in tracking.get('rows',[])}
        entry=days.index(record['entrySession']);maturity=days[entry+19] if entry+19<len(days) else None
        observed=sum(record['entrySession']<=d<=tracking.get('asof','') for d in days)
        mature=bool(maturity and tracking.get('available') and tracking.get('asof','')>=maturity)
        complete=bool(top) and all(rows.get(r['symbol'],{}).get('status') in ('TP','SL','SL_GAP','TIME') for r in top)
        evaluable=mature and complete
        if evaluable:scores.extend((r['probability']-int(rows[r['symbol']]['status']=='TP'))**2 for r in top)
        cohorts.append(dict(date=record['anchor'],predictionCount=len(record['predictions']),topCount=len(top),
            entry=record['entrySession'],maturity=maturity,observedSessions=min(20,observed),
            trackingAsOf=tracking.get('asof'),fullHorizon=mature,evaluable=evaluable,
            closed=sum(rows.get(r['symbol'],{}).get('status') in ('TP','SL','SL_GAP','TIME') for r in top)))
    return dict(status='INSUFFICIENT_PROSPECTIVE_EVIDENCE',latestClosed=closed[-1]['session'],
        latestDecision=max(known),capturedDays=len(known),missedDecisionDates=missed,pendingDecisionDates=pending,
        cohorts=cohorts,matureCohorts=sum(c['fullHorizon'] for c in cohorts),evaluableCohorts=sum(c['evaluable'] for c in cohorts),
        matureTop20Brier=sum(scores)/len(scores) if scores else None,
        matureTop20ObservationCount=len(scores),promotionAllowed=False,
        limitations=['TOP20 selected-population only, not all-universe calibration',
            'Repeated symbols and overlapping dates are correlated, not independent samples',
            'Closed early trades are not a complete fixed-horizon cohort',
            'Watchlist outcomes are not portfolio returns or allocation evidence'])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--publication',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    verification=json.loads((args.publication/'verification.json').read_text(encoding='utf-8'))
    source=args.publication/'after.json'
    if not source.exists():source=args.publication/'paper-web-after.json'
    if verification.get('status')!='PASS' or digest(source)!=verification['afterSha256']:raise ValueError('Unverified publication')
    for name,expected in verification['ledgerHashes'].items():
        if digest(ledger.DEFAULT/name)!=expected:raise ValueError('Ledger changed since publication')
    before={str(p.relative_to(ledger.DEFAULT)):digest(p) for p in ledger.DEFAULT.rglob('*') if p.is_file()}
    protocol=ledger.protocol(ledger.DEFAULT);records=ledger.decisions(ledger.DEFAULT,protocol)
    now=datetime.now(timezone.utc)
    result=assess(records,protocol['calendar'],json.loads(source.read_text(encoding='utf-8')),now)
    result.update(auditedAt=now.isoformat(),publicationSha256=digest(source),ledgerHashes=before)
    assert before=={str(p.relative_to(ledger.DEFAULT)):digest(p) for p in ledger.DEFAULT.rglob('*') if p.is_file()}
    args.out.parent.mkdir(parents=True,exist_ok=True)
    with args.out.open('x',encoding='utf-8') as f:json.dump(result,f,ensure_ascii=False,indent=2,allow_nan=False)
    print(json.dumps({k:v for k,v in result.items() if k not in ('ledgerHashes','limitations')},indent=2))


if __name__=='__main__':main()
