"""Create-only fixed-strategy paper ledger. No broker, network or production writes."""
import argparse
import csv
from datetime import datetime,timezone
import json
from importlib.metadata import version
from pathlib import Path
import uuid
import numpy as np
import pandas as pd
from quant_forward_validation import timestamp,iso,seal,verify_seal,validated_sessions
from build_sp500_daily_dataset import digest,read_csv
import sp500_daily_validation as engine
from sp500_risk_filters import filtered_rankings
from sp500_stable_ranking import EXCLUDED,verify_account
from diagnose_sp500_training_change import fit_score

ROOT=Path(__file__).resolve().parents[1]
DEFAULT=ROOT/'data/sp500-paper-ledger'
POLICY='TREND50_VOL75'
MODULES=('sp500_paper_ledger.py','sp500_daily_validation.py','sp500_risk_filters.py',
         'sp500_stable_ranking.py','diagnose_sp500_training_change.py',
         'quant_forward_validation.py','build_sp500_daily_dataset.py')


def now_utc():return datetime.now(timezone.utc)


def save(path,body):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    encoded=json.dumps(seal(dict(body)),indent=2,allow_nan=False)
    with path.open('x',encoding='utf-8') as f:
        f.write(encoded+'\n');f.flush()


def load(path):
    body=json.loads(Path(path).read_text(encoding='utf-8'))
    verify_seal(body)
    return body


def code_hashes():return {n:digest(ROOT/'scripts'/n) for n in MODULES}


def runtime_versions():return {name:version(name) for name in ('numpy','pandas','scikit-learn')}


def protocol(root):
    p=load(root/'protocol.json')
    if p['codeHashes']!=code_hashes():raise ValueError('CODE_CHANGED: explicit new ledger/version required')
    if p['excluded']!=sorted(EXCLUDED):raise ValueError('EXCLUSIONS_CHANGED')
    if p['runtimeVersions']!=runtime_versions():raise ValueError('MODEL_RUNTIME_CHANGED')
    return p


def initialize(root,calendar):
    rows=validated_sessions(read_csv(calendar))
    frozen=ROOT/'output/sp500-filter-validation-20261027'
    gate=json.loads((frozen/'prospective_gate.json').read_text())
    if digest(frozen/'protocol.json')!=gate['protocol_sha256']:raise ValueError('Historical protocol changed')
    save(root/'protocol.json',dict(version='SP500_PAPER_OPEN_BARRIER_V1',createdAtUtc=iso(now_utc()),
        policy=POLICY,tp=.15,sl=.05,maxSessions=20,fees=[.001,.002],initialCash=100000,
        maxPositions=10,maxNewPerDay=2,positionFraction=.1,excluded=sorted(EXCLUDED),
        calendar=rows,calendarSha256=digest(calendar),codeHashes=code_hashes(),runtimeVersions=runtime_versions(),
        historicalProtocolSha256=gate['protocol_sha256'],
        model='Monthly expanding calibrated price-only logistic; weekly training; maturity purge',
        selection='top20% first, then trend50>0 and vol20<=cross-sectional75%; no backfill',
        classification='RESEARCH_PAPER_ONLY_NOT_REAL_EXECUTION',
        calendarLimitation='Curated official schedule; emergency changes require explicit new version'))


def capture_window(calendar,now,anchor):
    rows=validated_sessions(calendar)
    closed=[i for i,r in enumerate(rows) if timestamp(r['closeUtc'])<=now]
    if not closed:raise ValueError('NO_COMPLETED_SESSION')
    i=closed[-1]
    if rows[i]['session']!=anchor:raise ValueError('STALE_DATA: latest completed session does not match dataset')
    if i+1>=len(rows):raise ValueError('CALENDAR_EXPIRED')
    if now>=timestamp(rows[i+1]['openUtc']):raise ValueError('NEXT_OPEN_CUTOFF_PASSED')
    return rows[i],rows[i+1]


def dataset_hashes(data):
    names=('manifest.json','verification.json','daily_panel.csv.gz','price_history.csv.gz',
           'inputs/calendar.csv.gz','inputs/aliases.csv','inputs/quality_overrides.csv',
           'inputs/sp500_membership_intervals.csv')
    return {name:digest(data/name) for name in names}


def checked_data(data):
    meta=json.loads((data/'manifest.json').read_text())
    if json.loads((data/'verification.json').read_text()).get('status')!='PASS':
        raise ValueError('DATASET_NOT_VERIFIED')
    for name,value in meta['input_sha256'].items():
        if digest(data/'inputs'/name)!=value:raise ValueError('DATASET_INPUT_HASH_CHANGED')
    return meta


def select(frame):
    if frame.empty:raise ValueError('NO_ELIGIBLE_FEATURES')
    if not np.isfinite(frame.probability).all() or not frame.probability.between(0,1).all():
        raise ValueError('INVALID_PROBABILITY')
    if frame.date.nunique()!=1:raise ValueError('ONE_ANCHOR_REQUIRED')
    ranks,audit=filtered_rankings(frame,POLICY)
    columns=['symbol','probability']+[k for k in engine.FEATURES if k in frame]
    predictions=frame[columns].sort_values('symbol').to_dict('records')
    return predictions,ranks.get(frame.iloc[0].date,[]),audit


def capture(root,data):
    p=protocol(root);meta=checked_data(data);anchor=meta['end']
    capture_window(p['calendar'],now_utc(),anchor)
    target=root/'decisions'/f'{anchor}.json'
    if target.exists():raise FileExistsError('Decision already frozen: '+anchor)
    hashes=dataset_hashes(data)
    engine.DATA=data
    days,prices,members,rows,excluded,levels=engine.load_features()
    frame=pd.DataFrame(rows)
    frame=frame[~frame.symbol.isin(EXCLUDED)]
    current=frame[frame.date==anchor].copy()
    cutoff=min(d for d in days if d.startswith(anchor[:7]))
    ci=days.index(cutoff)
    if ci<126:raise ValueError('INSUFFICIENT_CALIBRATION_HISTORY')
    cal_start=days[ci-126]
    training=frame[frame.label.notna()&(frame.session%5==0)]
    current['probability']=fit_score(training,current,dict(cutoff=cutoff,cal_start=cal_start))
    predictions,ranks,audit=select(current)
    # Recheck after fitting: a computation that crosses the opening is too late.
    recorded=now_utc();a,entry=capture_window(p['calendar'],recorded,anchor)
    if recorded<timestamp(p['createdAtUtc']):raise ValueError('CLOCK_PRECEDES_PROTOCOL')
    if hashes!=dataset_hashes(data):raise ValueError('INPUT_CHANGED_DURING_CAPTURE')
    protocol(root)
    fit=training[training.maturity<cal_start]
    cal=training[(training.date>=cal_start)&(training.date<cutoff)&(training.maturity<cutoff)]
    save(target,dict(version='SP500_PAPER_DECISION_V1',protocolSha256=p['contentSha256'],
        anchor=anchor,anchorCloseUtc=a['closeUtc'],entrySession=entry['session'],
        nextOpenUtc=entry['openUtc'],recordedAtUtc=iso(recorded),
        datasetPath=str(data.resolve()),inputHashes=hashes,predictions=predictions,
        ranks=ranks,filterAudit=audit,missingFeatureCount=len(members[anchor])-len(current),
        training=dict(cutoff=cutoff,calibrationStart=cal_start,fitRows=len(fit),calibrationRows=len(cal),
                      maxFitMaturity=fit.maturity.max(),maxCalibrationMaturity=cal.maturity.max())))
    committed=now_utc()
    # A separate receipt proves the decision file existed before the opening.
    # An interrupted or late write is retained but cannot become a valid order.
    capture_window(p['calendar'],committed,anchor)
    save(root/'receipts'/f'{anchor}.json',dict(decisionSha256=load(target)['contentSha256'],
                                            committedAtUtc=iso(committed)))
    return dict(status='SAVED_BEFORE_NEXT_OPEN',path=str(target),anchor=anchor,candidates=len(ranks))


def decisions(root,p):
    result=[]
    for file in sorted((root/'decisions').glob('*.json')):
        r=load(file)
        if r['protocolSha256']!=p['contentSha256']:raise ValueError('PROTOCOL_MISMATCH')
        a,e=capture_window(p['calendar'],timestamp(r['recordedAtUtc']),r['anchor'])
        if timestamp(r['recordedAtUtc'])<timestamp(p['createdAtUtc']):raise ValueError('BACKDATED_CAPTURE')
        if e['session']!=r['entrySession'] or e['openUtc']!=r['nextOpenUtc']:raise ValueError('ENTRY_MISMATCH')
        receipt=load(root/'receipts'/f"{r['anchor']}.json")
        if receipt['decisionSha256']!=r['contentSha256']:raise ValueError('RECEIPT_MISMATCH')
        committed=timestamp(receipt['committedAtUtc'])
        if not timestamp(r['recordedAtUtc'])<=committed<timestamp(e['openUtc']):raise ValueError('LATE_COMMIT')
        result.append(r)
    if len({r['anchor'] for r in result})!=len(result):raise ValueError('DUPLICATE_ANCHOR')
    return result


def compare_fills(account,records):
    probabilities={(r['anchor'],x['symbol']):x['probability'] for r in records for x in r['predictions']}
    return [dict(fill,predictedProbability=probabilities[fill['decision'],fill['symbol']],
                 actualTargetFirst=int(fill['reason']=='TP'),
                 brierSquaredError=(probabilities[fill['decision'],fill['symbol']]-int(fill['reason']=='TP'))**2)
            for fill in account['fills']]


def evaluate(root,data):
    p=protocol(root);records=decisions(root,p)
    if not records:return dict(status='NO_CAPTURED_DECISIONS',trades=0)
    meta=checked_data(data);hashes=dataset_hashes(data);now=now_utc()
    completed=[r['session'] for r in p['calendar'] if timestamp(r['closeUtc'])<=now and r['session']<=meta['end']]
    days=[d for d in completed if d>=records[0]['anchor']]
    if not days:raise ValueError('NO_COMPLETED_PRICE_SESSIONS')
    if meta['end']>p['calendar'][-1]['session']:raise ValueError('CALENDAR_EXPIRED')
    available={r['trade_date'] for r in read_csv(data/'inputs/calendar.csv.gz')}
    if not set(days)<=available:raise ValueError('MISSING_ACCOUNT_SESSION')
    aliases={r['symbol']:r['provider_symbol'] for r in read_csv(data/'inputs/aliases.csv')}
    prices={}
    for r in read_csv(data/'price_history.csv.gz'):
        if r['trade_date'] not in days:continue
        key=aliases.get(r['symbol'],r['symbol']),r['trade_date']
        b={k:float(r[k+'_price']) for k in ('open','high','low','close')}
        b.update(volume=float(r['volume']),execution_blocked=r['price_usable']!='1')
        if key in prices and prices[key]!=b:raise ValueError('CONFLICTING_ALIAS_BAR')
        prices[key]=b
    ranks={r['anchor']:r['ranks'] for r in records}
    accounts=[]
    for fee in p['fees']:
        account=engine.run_account(days,prices,ranks,fee)
        verify_account(account,prices,days)
        account['fills']=compare_fills(account,records)
        accounts.append(dict(fee=fee,**account))
    if hashes!=dataset_hashes(data):raise ValueError('INPUT_CHANGED_DURING_EVALUATION')
    body=dict(version='SP500_PAPER_EVALUATION_V1',evaluatedAtUtc=iso(now),
        classification='SIMULATED_FILLS_FROM_DAILY_OHLC_NOT_BROKER_EXECUTIONS',
        protocolSha256=p['contentSha256'],decisionHashes=[r['contentSha256'] for r in records],
        priceInputHashes=hashes,accounts=accounts,
        missedCaptureSessions=[d for d in days[:-1] if d not in ranks],
        interpretation='Uncaptured sessions create no new orders; existing positions continue. Open positions are marked, not falsely finalized. Brier is selected closed trades only, not population calibration.')
    target=root/'evaluations'/(now.strftime('%Y%m%dT%H%M%S')+'-'+uuid.uuid4().hex[:8]+'.json')
    save(target,body)
    return dict(status='EVALUATED',path=str(target),accountStatuses=[a['status'] for a in accounts])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['init','capture','evaluate','status'])
    parser.add_argument('--ledger',type=Path,default=DEFAULT)
    parser.add_argument('--calendar',type=Path,default=ROOT/'scripts/data/quant-forward-calendar-20261030-20261231.csv')
    parser.add_argument('--dataset',type=Path,default=ROOT/'output/sp500-actions-20261028-retry/dataset')
    args=parser.parse_args()
    try:
        if args.command=='init':initialize(args.ledger,args.calendar);result=dict(status='INITIALIZED',path=str(args.ledger))
        elif args.command=='capture':result=capture(args.ledger,args.dataset)
        elif args.command=='evaluate':result=evaluate(args.ledger,args.dataset)
        else:
            p=protocol(args.ledger);rs=decisions(args.ledger,p)
            result=dict(status='READY_NO_ORDERS',createdAtUtc=p['createdAtUtc'],capturedDays=len(rs),
                        latestAnchor=rs[-1]['anchor'] if rs else None,calendarEnd=p['calendar'][-1]['session'])
        print(json.dumps(result,indent=2))
    except (ValueError,FileExistsError,FileNotFoundError) as exc:
        print(json.dumps(dict(status='BLOCKED',reason=str(exc))))
        raise SystemExit(2)


if __name__=='__main__':main()
