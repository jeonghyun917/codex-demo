"""Fixed entry filters on frozen, corporate-excluded probability rankings."""
import json
import math
from pathlib import Path
import numpy as np
import pandas as pd
from build_sp500_daily_dataset import digest
from sp500_daily_validation import run_account
from sp500_stable_ranking import BASE,OUT as PREDICTIONS,EXCLUDED,PERIODS,verify_account

OUT=BASE.parent/'sp500-risk-filters-20261027'
POLICIES=('RAW','TREND50','VOL75','TREND50_VOL75')


def filtered_rankings(frame,policy):
    if policy not in POLICIES:raise ValueError(policy)
    ranks={};audit=[]
    for day,group in frame[~frame.symbol.isin(EXCLUDED)].groupby('date',sort=True):
        if not np.isfinite(group[['probability','vol20','index_ma50']].to_numpy()).all():
            raise ValueError('Invalid causal features: '+day)
        if group.symbol.duplicated().any() or group.index_ma50.nunique()!=1:
            raise ValueError('Duplicate security or inconsistent benchmark: '+day)
        cap=float(group.vol20.quantile(.75))
        top=group.sort_values(['probability','symbol'],ascending=[False,True]).head(max(1,math.ceil(len(group)*.2)))
        candidates=top
        if 'TREND50' in policy and float(group.iloc[0].index_ma50)<=0:
            candidates=candidates.iloc[:0]
        if 'VOL75' in policy:candidates=candidates[candidates.vol20<=cap]
        ranks[day]=[dict(symbol=r.symbol,score=r.probability) for r in candidates.itertuples()]
        audit.append(dict(date=day,policy=policy,universe=len(group),top_twenty=len(top),eligible=len(candidates),
                          vol75=cap,index_ma50=float(group.iloc[0].index_ma50)))
    return ranks,audit


def main():
    OUT.mkdir(exist_ok=True)
    sources=[PREDICTIONS/'predictions.csv.gz',PREDICTIONS/'comparisons.csv',PREDICTIONS/'protocol.json',
             BASE/'dataset/price_history.csv.gz',BASE/'dataset/inputs/calendar.csv.gz',BASE/'dataset/inputs/aliases.csv',Path(__file__)]
    protocol=dict(excluded=sorted(EXCLUDED),policies=POLICIES,periods=PERIODS,vol_quantile=.75,
                  volatility='20-session sample standard deviation of daily close returns, not annualized',
                  selection='top20 first, then entry filters, no lower-rank backfill',
                  trend='same-day benchmark close / trailing 50 session mean > 1',
                  fees=[.001,.002],tp=.15,sl=.05,max_sessions=20,
                  model='Frozen probabilities; no refit, parameter sweep or production change',
                  input_hashes={str(p):digest(p) for p in sources})
    (OUT/'protocol.json').write_text(json.dumps(protocol,indent=2),encoding='utf-8')
    frame=pd.read_csv(PREDICTIONS/'predictions.csv.gz')
    assert not set(frame.symbol)&EXCLUDED
    history=pd.read_csv(BASE/'dataset/price_history.csv.gz')
    aliases=pd.read_csv(BASE/'dataset/inputs/aliases.csv').set_index('symbol').provider_symbol.to_dict()
    history['symbol']=history.symbol.map(lambda s:aliases.get(s,s))
    history=history[(history.trade_date>='2023-01-03')&(history.trade_date<='2026-09-16')]
    history=history.drop_duplicates(['symbol','trade_date'])
    prices={(r.symbol,r.trade_date):dict(**{k:getattr(r,k+'_price') for k in ('open','high','low','close')},volume=r.volume,
              execution_blocked=any(flag in str(r.quality_flags) for flag in ('PREDECESSOR_IDENTITY_UNRESOLVED','TERMINAL_CORPORATE_ACTION_UNRESOLVED')))
            for r in history.itertuples()}
    del history
    days=pd.read_csv(BASE/'dataset/inputs/calendar.csv.gz').trade_date.tolist()
    summaries=[];equities=[];fills=[];events=[];audits=[]
    previous=pd.read_csv(PREDICTIONS/'comparisons.csv')
    for policy in POLICIES:
        rankings,audit=filtered_rankings(frame,policy);audits.extend(audit)
        for period,start,last,end in PERIODS:
            sessions=[d for d in days if start<=d<=end]
            chosen={d:r for d,r in rankings.items() if start<=d<=last}
            for fee in (.001,.002):
                a=run_account(sessions,prices,chosen,fee)
                verify_account(a,prices,days)
                ident=dict(period=period,policy=policy,fee=fee)
                if policy=='RAW':
                    baseline=previous[(previous.period==period)&(previous.policy=='RAW')&(previous.fee==fee)].iloc[0]
                    assert a['status']==baseline.status
                    for field in ('total_return_pct','max_drawdown_pct','average_cash_pct','trades'):
                        assert np.isclose(a[field],baseline[field],rtol=1e-12,atol=1e-8),field
                stop_trades=[t for t in a['fills'] if t['reason'].startswith('SL')]
                summaries.append(dict(**ident,**{k:a[k] for k in ('status','halt','total_return_pct','prefix_return_pct','max_drawdown_pct','average_cash_pct','trades','stop_pct','tp_pct')},
                                      stop_trades=len(stop_trades),stop_pnl=sum(t['pnl'] for t in stop_trades),
                                      exposure_days=sum(t['positions']>0 for t in a['equity']),evaluated_days=len(a['equity'])))
                equities.extend(dict(**ident,**t) for t in a['equity'])
                fills.extend(dict(**ident,**t) for t in a['fills'])
                events.extend(dict(**ident,**t) for t in a['events'])
                print(ident,a['status'],a['total_return_pct'],a['max_drawdown_pct'],flush=True)
    pd.DataFrame(summaries).to_csv(OUT/'comparisons.csv',index=False)
    pd.DataFrame(equities).to_csv(OUT/'daily_equity.csv.gz',index=False)
    pd.DataFrame(fills).to_csv(OUT/'fills.csv.gz',index=False)
    pd.DataFrame(events).to_csv(OUT/'events.csv.gz',index=False)
    pd.DataFrame(audits).to_csv(OUT/'daily_filter_audit.csv.gz',index=False)
    (OUT/'verification.json').write_text(json.dumps(dict(status='PASS',accounts=len(summaries),baseline_accounts_reproduced=6,
                    all_complete=all(r['status']=='COMPLETE_RESEARCH_ONLY' for r in summaries),filled_trades=len(fills)),indent=2),encoding='utf-8')


if __name__=='__main__':main()
