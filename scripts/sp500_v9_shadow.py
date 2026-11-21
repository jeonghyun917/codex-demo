"""Archived V9/probability paired research; never writes DB or live paper state."""
import hashlib
import json
import math
from datetime import datetime, timezone
from pathlib import Path
import numpy as np
import pandas as pd
import sp500_daily_validation as engine
from sp500_stable_ranking import EXCLUDED, verify_account
from verify_sp500_expected_return_shadow import first_exit

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/sp500-v9-shadow-20261101'
DATA=ROOT/'output/sp500-actions-20261028-retry/dataset'
V9=ROOT/'tmp/quant-ifrs-rolling-training-20261031-v2/result.json'
LATEST=ROOT/'tmp/quant-eps-v9-training-20261101-v2/replay/forecast-only.json'
PROB=ROOT/'output/sp500-stable-ranking-20261027/predictions.csv.gz'
MODEL='EXPECTED_RETURN_V9_CORRECTED_20261017'


def digest(p):
    h=hashlib.sha256()
    with Path(p).open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()


def save(p,v):
    with p.open('x',encoding='utf-8') as f:json.dump(v,f,indent=2,allow_nan=False)


def matched_panel(prob,v9):
    for frame in (prob,v9):
        if frame.duplicated(['date','symbol']).any():raise ValueError('Duplicate forecast key')
    eligible=prob[~prob.symbol.isin(EXCLUDED)].copy()
    eligible['vol_cap']=eligible.groupby('date').vol20.transform(lambda v:v.quantile(.75))
    return eligible.merge(v9,on=['date','symbol'],how='inner',validate='one_to_one')


def rankings(frame,column):
    if not np.isfinite(frame[[column,'vol20','vol_cap','index_ma50']].to_numpy()).all():
        raise ValueError('Invalid forecast or filter value')
    result={}
    for day,g in frame.groupby('date',sort=True):
        if g.symbol.duplicated().any() or g.index_ma50.nunique()!=1:raise ValueError('Invalid daily universe')
        top=g.sort_values([column,'symbol'],ascending=[False,True]).head(math.ceil(len(g)*.2))
        keep=top[(top.index_ma50>0)&(top.vol20<=top.vol_cap)]
        result[day]=[dict(symbol=r.symbol,score=float(getattr(r,column))) for r in keep.itertuples()]
    return result


def verify_cash_and_paths(a,bars,days,candidates):
    loc={d:i for i,d in enumerate(days)};marks={r['date']:r for r in a['equity']}
    buys={};sells={};fee=a['fee']
    for t in a['fills']:
        assert t['symbol'] in [r['symbol'] for r in candidates[t['decision']]]
        assert loc[t['entry']]==loc[t['decision']]+1
        assert t['entry_price']==bars[t['symbol'],t['entry']]['open_price']
        future=[bars[t['symbol'],d] for d in days[loc[t['entry']]:loc[t['exit']]+1]]
        d,px,reason,age=first_exit(t['entry_price'],future)
        assert (d,reason,age)==(t['exit'],t['reason'],t['age']) and abs(px-t['exit_price'])<1e-9
        debit=t['entry_price']*t['shares']*(1+fee);credit=px*t['shares']*(1-fee)
        assert abs(debit-t['budget'])<1e-7 and abs(credit-t['proceeds'])<1e-7
        assert abs(credit-debit-t['pnl'])<1e-7 and debit<=marks[t['decision']]['equity']*.1+1e-7
        buys.setdefault(t['entry'],[]).append(t);sells.setdefault(t['exit'],[]).append(t)
    cash=100000.;positions={};peak=cash;mdd=0
    for mark in a['equity']:
        d=mark['date'];assert len(buys.get(d,[]))<=2
        for t in buys.get(d,[]):
            assert t['symbol'] not in positions
            positions[t['symbol']]=t['shares'];cash-=t['budget']
            assert len(positions)<=10 and cash>=-1e-7
        for t in sells.get(d,[]):
            assert positions.pop(t['symbol'])==t['shares'];cash+=t['proceeds']
        nav=cash+sum(n*bars[s,d]['close_price'] for s,n in positions.items())
        assert abs(nav-mark['equity'])<1e-6 and abs(cash-mark['cash'])<1e-6
        assert len(positions)==mark['positions']
        peak=max(peak,nav);mdd=min(mdd,100*(nav/peak-1))
    assert not positions and abs(100*(cash/100000-1)-a['total_return_pct'])<1e-8
    assert abs(mdd-a['max_drawdown_pct'])<1e-8


def main():
    if OUT.exists():raise ValueError('Refusing overwrite')
    raw=json.loads(V9.read_text());assert raw['databaseWrites']==0 and raw['futureTargetLabelInvariant']
    assert all(r['maxTrainingExit']<r['decision'] for r in raw['trainingAudit'])
    frames=[]
    for key,col in [('predictions','v9_absolute'),('controlPredictions','v9_control')]:
        f=pd.DataFrame(raw[key]);f=f[(f.modelVersion==MODEL)&(f.horizonDays==20)]
        cols=['signalDate','symbol','expectedReturnPct']+(['expectedExcessReturnPct'] if key=='predictions' else [])
        f=f[cols].rename(columns=dict(signalDate='date',expectedReturnPct=col,expectedExcessReturnPct='v9_excess'))
        frames.append(f)
    v9=frames[0].merge(frames[1],on=['date','symbol'],validate='one_to_one')
    assert len(v9)==2020
    probability=pd.read_csv(PROB);panel=matched_panel(probability,v9)
    latest=json.loads(LATEST.read_text());latest=pd.DataFrame(latest['oofPredictions'])
    latest=latest[(latest.modelVersion==MODEL)&(latest.horizonDays==20)]
    overlap=latest.merge(probability,left_on=['signalDate','symbol'],right_on=['date','symbol'])
    assert overlap.empty, 'New latest EPS overlap available: redesign before running'
    days=pd.read_csv(DATA/'inputs/calendar.csv.gz').trade_date.tolist()
    start=panel.date.min();last=panel.date.max();end=days[days.index(last)+20]
    span=[d for d in days if start<=d<=end]
    probability=probability[(probability.date>=start)&(probability.date<=last)&~probability.symbol.isin(EXCLUDED)].copy()
    probability['vol_cap']=probability.groupby('date').vol20.transform(lambda x:x.quantile(.75))
    variants={name:rankings(panel,col) for name,col in [('COMMON_PROBABILITY','probability'),
        ('COMMON_V9_ABSOLUTE','v9_absolute'),('COMMON_V9_EXCESS','v9_excess'),('COMMON_V9_CONTROL','v9_control')]}
    full=rankings(probability,'probability')
    variants['CONTEXT_FULL_SP_SPARSE']={d:r for d,r in full.items() if d in set(panel.date)}
    variants['CONTEXT_FULL_SP_DAILY']=full
    sources=[Path(__file__),ROOT/'scripts/test_sp500_v9_shadow.py',V9,LATEST,PROB,
        ROOT/'scripts/sp500_daily_validation.py',ROOT/'scripts/sp500_stable_ranking.py',
        ROOT/'scripts/verify_sp500_expected_return_shadow.py',DATA/'price_history.csv.gz',
        DATA/'inputs/calendar.csv.gz',DATA/'inputs/aliases.csv',
        ROOT/'data/sp500-paper-ledger/protocol.json',ROOT/'data/sp500-paper-ledger/decisions/2026-10-30.json',
        ROOT/'docs/superpowers/plans/2026-11-01-v9-trading-shadow.md']
    hashes={str(p.relative_to(ROOT)):digest(p) for p in sources}
    historical_hashes={p:digest(ROOT/p)==h for p,h in raw['inputHashes'].items()}
    OUT.mkdir()
    save(OUT/'protocol.json',dict(createdAtUtc=datetime.now(timezone.utc).isoformat(),strategyChanged=False,
        classification='ARCHIVED_V9_MATCHED_SPARSE_RESEARCH_NOT_LATEST_EPS_NOT_HOLDOUT',
        primary='COMMON_V9_ABSOLUTE vs COMMON_PROBABILITY',start=start,lastDecision=last,end=end,
        tp=.15,sl=.05,maxSessions=20,fees=[.001,.002],excluded=sorted(EXCLUDED),
        account='100k cash,10 positions,2 new/day,10% priorNAV,whole shares,next open,SL-first,no forward-fill',
        selection='Top ceil20% of common symbols THEN index_ma50>0 and FULL SP daily vol75; no backfill',
        latestEPS=dict(rows=len(latest),start=latest.signalDate.min(),end=latest.signalDate.max(),matchedRows=0),
        archivedTrainingAudit=raw['trainingAudit'],archivedSourceHashesStillMatch=historical_hashes,
        sourceHashes=hashes))
    panel.to_csv(OUT/'matched_predictions.csv',index=False)
    audit=[];rejected=[]
    keys=set(zip(panel.date,panel.symbol))
    for day,g in v9.groupby('date'):
        p=panel[panel.date==day];f=probability[probability.date==day]
        audit.append(dict(date=day,v9Rows=len(g),spEligible=len(f),commonRows=len(p),
            commonCandidates=len(variants['COMMON_PROBABILITY'][day]),v9Candidates=len(variants['COMMON_V9_ABSOLUTE'][day]),
            indexAbove50=bool(p.index_ma50.iloc[0]>0),volCap=float(p.vol_cap.iloc[0])))
        rejected.extend(dict(date=day,symbol=s,reason='No matching eligible frozen SP probability; not backfilled') for s in g.symbol if (day,s) not in keys)
    pd.DataFrame(audit).to_csv(OUT/'coverage.csv',index=False);pd.DataFrame(rejected).to_csv(OUT/'unmatched_v9.csv',index=False)
    save(OUT/'candidate_rankings.json',variants)
    prices=pd.read_csv(DATA/'price_history.csv.gz');aliases=pd.read_csv(DATA/'inputs/aliases.csv').set_index('symbol').provider_symbol.to_dict()
    prices.symbol=prices.symbol.map(lambda s:aliases.get(s,s))
    prices=prices[(prices.trade_date>=start)&(prices.trade_date<=end)].copy()
    cols=['open_price','high_price','low_price','close_price','volume','quality_flags']
    dup=prices[prices.duplicated(['symbol','trade_date'],keep=False)]
    assert (dup.groupby(['symbol','trade_date'])[cols].nunique(dropna=False)<=1).all().all()
    bars={(r['symbol'],r['trade_date']):r for r in prices.drop_duplicates(['symbol','trade_date']).to_dict('records')}
    execution={k:{**{c:r[c+'_price'] for c in ('open','high','low','close')},'volume':r['volume'],
        'execution_blocked':any(f in str(r['quality_flags']) for f in ('PREDECESSOR_IDENTITY_UNRESOLVED','TERMINAL_CORPORATE_ACTION_UNRESOLVED'))} for k,r in bars.items()}
    accounts=[];summaries=[]
    for name,candidates in variants.items():
        for fee in (.001,.002):
            a=dict(model=name,fee=fee,**engine.run_account(span,execution,candidates,fee))
            verify_account(a,execution,days)
            if a['status']=='COMPLETE_RESEARCH_ONLY':verify_cash_and_paths(a,bars,days,candidates)
            accounts.append(a)
            summaries.append({k:a[k] for k in ('model','fee','status','total_return_pct','max_drawdown_pct','average_cash_pct','trades','stop_pct','tp_pct')})
            print(summaries[-1],flush=True)
    save(OUT/'accounts.json',accounts);pd.DataFrame(summaries).to_csv(OUT/'comparison.csv',index=False)
    for p,h in hashes.items():assert digest(ROOT/p)==h,p
    save(OUT/'verification.json',dict(status='PASS',accounts=len(accounts),
        completedAccounts=sum(a['status']=='COMPLETE_RESEARCH_ONLY' for a in accounts),
        fillsChecked=sum(len(a['fills']) for a in accounts if a['status']=='COMPLETE_RESEARCH_ONLY'),
        unchangedSourceHashes=True,latestEPSComparable=False,
        checks=['exact-date key join, no forward fill','mature training audit','same full-SP filter threshold',
                'first barrier path, next-open and 20-session exit','daily cash/equity/MDD reconstruction',
                'position sizing and fees','frozen live ledger files unchanged'],
        outputHashes={p.name:digest(p) for p in OUT.iterdir() if p.is_file()}))


if __name__=='__main__':main()
