"""Frozen filter validation: real vintages and explicitly synthetic sensitivity."""
import gc
import hashlib
import json
from pathlib import Path
import numpy as np
import pandas as pd
import sp500_daily_validation as engine
from sp500_risk_filters import filtered_rankings,OUT as FILTER_RESULTS
from sp500_stable_ranking import BASE,OUT as CURRENT,EXCLUDED,PERIODS,verify_account
from diagnose_sp500_training_change import fit_score
from build_sp500_daily_dataset import digest

OLD=BASE.parent/'sp500-validation-20261027'
OUT=BASE.parent/'sp500-filter-validation-20261027'
POLICIES=('VOL75','TREND50_VOL75')


def eligible_unseen_decisions(days,last_used,member_end):
    return [d for i,d in enumerate(days) if last_used<d<=member_end and i+20<len(days)]


def retain_training(frame,seed):
    keep=[int.from_bytes(hashlib.blake2b(f'{seed}|{r.symbol}|{r.date}'.encode(),digest_size=8).digest(),'big')%100!=0
          for r in frame[['symbol','date']].itertuples(index=False)]
    return frame.loc[keep]


def main():
    OUT.mkdir(exist_ok=True)
    protocol=dict(policies=POLICIES,excluded=sorted(EXCLUDED),periods=PERIODS,last_used_outcome='2026-09-16',
                  training_drop_rate=.01,seeds=list(range(5)),probability_noise_sd=.0001,log_volatility_noise_sd=.01,
                  tp=.15,sl=.05,max_sessions=20,vol_quantile=.75,trend='index_ma50>0',
                  no_optimization=True,prospective_test='First verified exchange session after this protocol freeze, with membership evidence and full 20-session runoff; no performance claim before data exist.',
                  input_hashes={str(p):digest(p) for p in [CURRENT/'predictions.csv.gz',FILTER_RESULTS/'comparisons.csv',OLD/'dataset/manifest.json',BASE/'dataset/manifest.json',Path(__file__)]})
    (OUT/'protocol.json').write_text(json.dumps(protocol,indent=2),encoding='utf-8')
    cal=pd.read_csv(BASE/'dataset/inputs/calendar.csv.gz');days=cal.trade_date.tolist()
    meta=json.loads((BASE/'dataset/manifest.json').read_text())
    available=eligible_unseen_decisions(days,'2026-09-16',meta['end'])
    price_only=eligible_unseen_decisions(days,'2026-09-16',days[-1])
    holdout=dict(status='BLOCKED_NO_UNSEEN_MEMBERSHIP_COVERAGE',membership_end=meta['end'],price_calendar_end=days[-1],
                 last_used_outcome='2026-09-16',eligible_unseen_decision_dates=available,
                 price_only_mature_decision_dates_not_usable=price_only,
                 reason='Do not extend last roster or relabel already inspected periods as holdout.')
    assert not available
    (OUT/'holdout_audit.json').write_text(json.dumps(holdout,indent=2),encoding='utf-8')
    old_train=None;old_scoring=None;old_prices=None
    for path in (OLD,BASE):
        engine.DATA=path/'dataset'
        _,prices,members,rows,excluded,levels=engine.load_features()
        f=pd.DataFrame(rows);del rows,members,levels;gc.collect()
        f=f[~f.symbol.isin(EXCLUDED)].reset_index(drop=True)
        train=f[f.label.notna()&(f.session%5==0)].copy()
        scoring=f[f.date>='2023-01-01'].copy().reset_index(drop=True)
        prices={key:value for key,value in prices.items() if key[0] not in EXCLUDED and '2023-01-03'<=key[1]<='2026-09-16'}
        if path==OLD:old_train,old_scoring,old_prices=train,scoring,prices
        else:new_train,new_scoring,new_prices=train,scoring,prices
        print('loaded',path.name,len(train),len(scoring),flush=True)
        del f;gc.collect()
    common_keys=old_prices.keys()&new_prices.keys()
    changed=[k for k in common_keys if old_prices[k]!=new_prices[k]]
    (OUT/'price_revision_audit.json').write_text(json.dumps(dict(common_bars=len(common_keys),changed_common_bars=len(changed),
         added_bars=len(new_prices.keys()-old_prices.keys()),removed_bars=len(old_prices.keys()-new_prices.keys())),indent=2),encoding='utf-8')
    current=pd.read_csv(CURRENT/'predictions.csv.gz')
    assert not set(current.symbol)&EXCLUDED
    specs={m['month']:m for m in json.loads((BASE/'research/models.json').read_text()) if m['status']=='FIT'}
    previous=pd.read_csv(FILTER_RESULTS/'comparisons.csv')
    summaries=[];fills=[];equities=[];audits=[];references={};models=[]
    def run(name,kind,pred,prices,seed=-1,fees=(.001,)):
        for policy in POLICIES:
            ranks,_=filtered_rankings(pred,policy)
            for period,start,last,end in PERIODS:
                sessions=[d for d in days if start<=d<=end]
                for fee in fees:
                    a=engine.run_account(sessions,prices,{d:r for d,r in ranks.items() if start<=d<=last},fee)
                    verify_account(a,prices,days)
                    identity=dict(scenario=name,kind=kind,seed=seed,period=period,policy=policy,fee=fee)
                    keys={(t['symbol'],t['entry']) for t in a['fills']}
                    if name=='CURRENT':
                        b=previous[(previous.period==period)&(previous.policy==policy)&(previous.fee==fee)].iloc[0]
                        assert a['status']==b.status
                        for field in ('total_return_pct','max_drawdown_pct','trades'):
                            assert np.isclose(a[field],b[field],rtol=1e-12,atol=1e-8),field
                        references[period,policy,fee]=keys
                    reference=references[period,policy,fee]
                    summary=dict(**identity,**{k:a[k] for k in ('status','halt','total_return_pct','prefix_return_pct','max_drawdown_pct','average_cash_pct','trades')},
                                 trade_change=1-len(keys&reference)/len(keys|reference) if keys|reference else 0)
                    summaries.append(summary)
                    fills.extend(dict(**identity,**t) for t in a['fills'])
                    equities.extend(dict(**identity,**t) for t in a['equity'])
        print('scenario',name,flush=True)
    run('CURRENT','baseline',current,new_prices,fees=(.001,.002))
    def refit(name,train,scoring,seed=-1):
        pred=scoring.copy()
        for month,idx in pred.groupby(pred.date.str[:7]).groups.items():
            spec=specs[month]
            pred.loc[idx,'probability']=fit_score(train,pred.loc[idx],spec)
            fit=train[train.maturity<spec['cal_start']]
            cal=train[(train.date>=spec['cal_start'])&(train.date<spec['cutoff'])&(train.maturity<spec['cutoff'])]
            assert fit.maturity.max()<spec['cal_start'] and cal.maturity.max()<spec['cutoff']
            audits.append(dict(scenario=name,month=month,fit_rows=len(fit),cal_rows=len(cal),fit_max_maturity=fit.maturity.max(),cal_max_maturity=cal.maturity.max(),cutoff=spec['cutoff'],cal_start=spec['cal_start']))
        return pred
    reproduced=refit('CURRENT_REFIT',new_train,new_scoring)
    compare=current[['symbol','date','probability']].merge(reproduced[['symbol','date','probability']],on=['symbol','date'],suffixes=('_saved','_refit'),validate='one_to_one')
    error=float(np.max(np.abs(compare.probability_saved-compare.probability_refit)))
    assert len(compare)==len(current)==len(reproduced) and error<1e-10,error
    oldpred=refit('OLD_SNAPSHOT',old_train,old_scoring)
    run('OLD_SNAPSHOT','actual_vintage',oldpred,old_prices,fees=(.001,.002))
    old_new=refit('OLD_TRAIN_NEW_FEATURES',old_train,new_scoring)
    run('OLD_TRAIN_NEW_FEATURES','training_vintage',old_new,new_prices)
    sample_audit=[]
    for seed in range(5):
        retained=retain_training(new_train,seed)
        sample_audit.append(dict(seed=seed,total_rows=len(new_train),removed_rows=len(new_train)-len(retained)))
        name=f'DROP1_{seed}'
        pred=refit(name,retained,new_scoring,seed)
        run(name,'training_drop1',pred,new_prices,seed)
        for kind in ('probability_noise','volatility_noise'):
            perturbed=current.copy();rng=np.random.default_rng(seed)
            if kind=='probability_noise':perturbed['probability']=np.clip(perturbed.probability+rng.normal(0,.0001,len(perturbed)),0,1)
            else:perturbed['vol20']=perturbed.vol20*np.exp(rng.normal(0,.01,len(perturbed)))
            run(f'{kind}_{seed}',kind,perturbed,new_prices,seed)
    pd.DataFrame(summaries).to_csv(OUT/'comparisons.csv',index=False)
    pd.DataFrame(fills).to_csv(OUT/'fills.csv.gz',index=False)
    pd.DataFrame(equities).to_csv(OUT/'daily_equity.csv.gz',index=False)
    pd.DataFrame(audits).to_csv(OUT/'training_audit.csv',index=False)
    pd.DataFrame(sample_audit).to_csv(OUT/'sample_audit.csv',index=False)
    (OUT/'verification.json').write_text(json.dumps(dict(status='PASS',accounts=len(summaries),refit_probability_max_error=error,
              complete_accounts=sum(r['status']=='COMPLETE_RESEARCH_ONLY' for r in summaries),holdout_status=holdout['status']),indent=2),encoding='utf-8')
    print('DONE',len(summaries),flush=True)


if __name__=='__main__':main()
