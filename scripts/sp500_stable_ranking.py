"""Predeclared causal ranking policies and matched corporate-clean research."""
import gc
import json
import math
from pathlib import Path
import numpy as np
import pandas as pd
import sp500_daily_validation as engine
from diagnose_sp500_training_change import fit_score
from build_sp500_daily_dataset import digest

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'output/sp500-free-followup-20261027'
OUT=ROOT/'output/sp500-stable-ranking-20261027'
EXCLUDED={'FBIN','BBWI','DOC','PEAK','PSKY','AVB','EQR','VMRK'}
POLICIES=('RAW','EMA5','BAND05','EMA5_BAND05')
PERIODS=[('development','2023-01-03','2024-12-31','2025-01-31'),
         ('inspected_evaluation','2025-01-02','2026-08-18','2026-09-16'),
         ('complete_price_window','2026-06-03','2026-07-02','2026-07-31')]


def policy_rankings(frame,policy):
    if policy not in POLICIES:raise ValueError(policy)
    state={};previous={};previous_session=None;output={}
    for day,group in frame[~frame.symbol.isin(EXCLUDED)].groupby('date',sort=True):
        session=int(group.iloc[0].session)
        if previous_session is not None and session!=previous_session+1:previous={}
        scored=[]
        for r in group.itertuples():
            value=float(r.probability)
            old=state.get(r.symbol)
            if 'EMA5' in policy and old and old[0]==session-1:
                value=value/3+old[1]*2/3
            state[r.symbol]=(session,value)
            scored.append(dict(symbol=r.symbol,score=value))
        scored.sort(key=lambda r:(-r['score'],r['symbol']))
        if 'BAND05' in policy:
            ordered=[];i=0
            while i<len(scored):
                j=i+1
                while j<len(scored) and scored[i]['score']-scored[j]['score']<=.005+1e-12:j+=1
                ordered.extend(sorted(scored[i:j],key=lambda r:(previous.get(r['symbol'],math.inf),r['symbol'])))
                i=j
            scored=ordered
        previous={r['symbol']:i for i,r in enumerate(scored)};previous_session=session
        output[day]=scored[:max(1,math.ceil(len(scored)*.2))]
    return output


def verify_account(account,prices,days):
    if account['status']!='COMPLETE_RESEARCH_ONLY':return
    assert abs(sum(t['pnl'] for t in account['fills'])/1000-account['total_return_pct'])<1e-8
    loc={d:i for i,d in enumerate(days)}
    cash=100000.;positions={};peak=cash;mdd=0
    events={}
    for e in account['events']:events.setdefault(e['date'],[]).append(e)
    for mark in account['equity']:
        for e in events.get(mark['date'],[]):
            assert e['symbol'] not in EXCLUDED
            cash+=e['cash_delta']
            if e['side']=='BUY':
                assert e['symbol'] not in positions
                positions[e['symbol']]=e['shares']
            else:assert positions.pop(e['symbol'])==e['shares']
        holdings=sum(n*prices[s,mark['date']]['close'] for s,n in positions.items())
        assert abs(cash-mark['cash'])<1e-7 and abs(cash+holdings-mark['equity'])<1e-7
        assert cash>=-1e-7 and len(positions)<=10
        peak=max(peak,cash+holdings);mdd=min(mdd,100*((cash+holdings)/peak-1))
    assert abs(mdd-account['max_drawdown_pct'])<1e-8
    for t in account['fills']:
        assert loc[t['entry']]==loc[t['decision']]+1
        age=loc[t['exit']]-loc[t['entry']]+1
        assert 1<=age<=20 and age==t['age']
        for offset in range(age):
            b=prices[t['symbol'],days[loc[t['entry']]+offset]]
            stop=t['entry_price']*.95;target=t['entry_price']*1.15
            event=(b['open'],'SL_GAP') if b['open']<=stop else (target,'TP') if b['open']>=target else (stop,'SL') if b['low']<=stop else (target,'TP') if b['high']>=target else (b['close'],'TIME') if offset==19 else None
            if offset<age-1:assert event is None
            else:assert event[1]==t['reason'] and abs(event[0]-t['exit_price'])<1e-8


def main():
    OUT.mkdir(exist_ok=True)
    protocol=dict(excluded=sorted(EXCLUDED),policies=POLICIES,ema_alpha=1/3,band_width=.005,noise_sd=[.0001,.001],seeds=list(range(5)),periods=PERIODS,
                  limitation='Retrospectively fixed research universe; not point-in-time corporate-action availability or untouched holdout.',
                  input_hashes={str(p):digest(p) for p in [BASE/'dataset/manifest.json',BASE/'research/models.json',Path(__file__)]})
    (OUT/'protocol.json').write_text(json.dumps(protocol,indent=2),encoding='utf-8')
    engine.DATA=BASE/'dataset'
    days,prices,members,rows,excluded,levels=engine.load_features()
    frame=pd.DataFrame(rows);del rows,members,levels;gc.collect()
    rejected=frame[frame.symbol.isin(EXCLUDED)]
    rejected.groupby('symbol').agg(rows=('date','size'),labels=('label','count')).to_csv(OUT/'excluded_samples.csv')
    frame=frame[~frame.symbol.isin(EXCLUDED)].reset_index(drop=True)
    train=frame[frame.label.notna()&(frame.session%5==0)]
    predictions=frame[frame.date>='2023-01-01'].copy().reset_index(drop=True)
    specs={m['month']:m for m in json.loads((BASE/'research/models.json').read_text()) if m['status']=='FIT'}
    audit=[]
    for month,idx in predictions.groupby(predictions.date.str[:7]).groups.items():
        spec=specs[month]
        predictions.loc[idx,'probability']=fit_score(train,predictions.loc[idx],spec)
        fit=train[train.maturity<spec['cal_start']]
        cal=train[(train.date>=spec['cal_start'])&(train.date<spec['cutoff'])&(train.maturity<spec['cutoff'])]
        assert fit.maturity.max()<spec['cal_start'] and cal.maturity.max()<spec['cutoff']
        audit.append(dict(month=month,fit_rows=len(fit),cal_rows=len(cal),fit_max_maturity=fit.maturity.max(),cal_max_maturity=cal.maturity.max(),cutoff=spec['cutoff'],cal_start=spec['cal_start']))
        print('fit',month,len(fit),len(cal),flush=True)
    predictions.to_csv(OUT/'predictions.csv.gz',index=False)
    pd.DataFrame(audit).to_csv(OUT/'training_audit.csv',index=False)
    del train,frame;gc.collect()
    summaries=[];fills=[];equity=[];noise=[];reference={};verified=0
    def account_for(ranks,period,fee):
        name,start,last,end=period
        sessions=[d for d in days if start<=d<=end]
        account=engine.run_account(sessions,prices,{d:r for d,r in ranks.items() if start<=d<=last},fee)
        verify_account(account,prices,days)
        return account
    for policy in POLICIES:
        ranks=policy_rankings(predictions,policy)
        for period in PERIODS:
            for fee in (.001,.002):
                a=account_for(ranks,period,fee);verified+=1
                ident=dict(period=period[0],policy=policy,fee=fee)
                fills.extend(dict(**ident,**t) for t in a['fills']);equity.extend(dict(**ident,**t) for t in a['equity'])
                summaries.append(dict(**ident,**{k:a[k] for k in ('status','halt','total_return_pct','prefix_return_pct','max_drawdown_pct','average_cash_pct','trades','stop_pct')}))
                if fee==.001:reference[policy,period[0]]={(t['symbol'],t['entry']) for t in a['fills']}
                print(ident,a['status'],a['total_return_pct'],flush=True)
    pd.DataFrame(summaries).to_csv(OUT/'comparisons.csv',index=False)
    pd.DataFrame(fills).to_csv(OUT/'fills.csv.gz',index=False)
    pd.DataFrame(equity).to_csv(OUT/'daily_equity.csv.gz',index=False)
    for amplitude in (.0001,.001):
        for seed in range(5):
            perturbed=predictions.copy()
            perturbed['probability']=np.clip(perturbed.probability+np.random.default_rng(seed).normal(0,amplitude,len(perturbed)),0,1)
            for policy in POLICIES:
                ranks=policy_rankings(perturbed,policy)
                for period in PERIODS:
                    a=account_for(ranks,period,.001);verified+=1
                    actual={(t['symbol'],t['entry']) for t in a['fills']};base=reference[policy,period[0]]
                    turnover=1-len(actual&base)/len(actual|base) if actual|base else 0
                    noise.append(dict(period=period[0],policy=policy,noise_sd=amplitude,seed=seed,status=a['status'],return_pct=a['total_return_pct'],mdd_pct=a['max_drawdown_pct'],trade_set_change=turnover))
            print('noise',amplitude,seed,flush=True)
    pd.DataFrame(noise).to_csv(OUT/'noise_runs.csv',index=False)
    stability=pd.DataFrame(noise).groupby(['period','policy','noise_sd']).agg(runs=('seed','size'),return_min=('return_pct','min'),return_max=('return_pct','max'),return_sd=('return_pct','std'),worst_mdd=('mdd_pct','min'),mean_trade_change=('trade_set_change','mean'))
    stability.to_csv(OUT/'stability.csv')
    (OUT/'verification.json').write_text(json.dumps(dict(status='PASS',accounts_checked=verified,excluded_symbols=sorted(EXCLUDED),training_months=len(audit),base_accounts=len(summaries),noise_accounts=len(noise),
        caveat='PASS is accounting/causality, not model efficacy or all corporate actions certified.'),indent=2),encoding='utf-8')
    print('DONE',verified,flush=True)


if __name__=='__main__':main()
