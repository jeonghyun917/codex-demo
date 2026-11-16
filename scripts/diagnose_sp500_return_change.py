"""Frozen-output counterfactuals; attribution is descriptive, not strategy tuning."""
import json
import math
from pathlib import Path
import numpy as np
import pandas as pd
from sp500_daily_validation import FEATURES,run_account
from build_sp500_daily_dataset import digest

ROOT=Path(__file__).resolve().parents[1]
OLD=ROOT/'output/sp500-validation-20261027'
NEW=ROOT/'output/sp500-free-followup-20261027'
OUT=ROOT/'output/sp500-return-diagnosis-20261027'


def score(frame,models):
    result=frame.copy()
    for month,idx in result.groupby(result.date.str[:7]).groups.items():
        m=models[month]
        z=((result.loc[idx,FEATURES].to_numpy()-m['mean'])/m['scale'])@np.asarray(m['coef'])+m['intercept']
        result.loc[idx,'probability']=1/(1+np.exp(-(z*m['cal_coef']+m['cal_intercept'])))
    return result


def rankings(frame):
    return {d:[dict(symbol=r.symbol,score=r.probability) for r in g.sort_values(['probability','symbol'],ascending=[False,True]).head(max(1,math.ceil(len(g)*.2))).itertuples()]
            for d,g in frame.groupby('date',sort=True)}


def trades(path):
    df=pd.read_csv(path/'research/fills.csv')
    return df[(df.period=='development')&(df.rule=='PRICE_AI_TOP20')&(df.fee==.001)].copy()


def main():
    OUT.mkdir(exist_ok=True)
    models=[{m['month']:m for m in json.loads((p/'research/models.json').read_text()) if m['status']=='FIT'} for p in (OLD,NEW)]
    frames=[]
    for path in (OLD,NEW):
        f=pd.read_csv(path/'research/predictions.csv.gz')
        frames.append(f[(f.date>='2023-01-03')&(f.date<='2024-12-31')].reset_index(drop=True))
    old,new=frames
    common=old.merge(new,on=['symbol','date'],suffixes=('_old','_new'),validate='one_to_one')
    feature_changes={k:int((np.abs(common[k+'_old']-common[k+'_new'])>1e-12).sum()) for k in FEATURES}
    delta=np.abs(common.probability_new-common.probability_old)
    rowdiff=old[['symbol','date']].merge(new[['symbol','date']],on=['symbol','date'],how='outer',indicator=True)
    rowdiff[rowdiff._merge!='both'].to_csv(OUT/'prediction_universe_changes.csv',index=False)
    modeldiff=[]
    for month in sorted(set(old.date.str[:7])):
        a,b=models[0][month],models[1][month]
        ca=np.asarray(a['coef'])/a['scale']*a['cal_coef']
        cb=np.asarray(b['coef'])/b['scale']*b['cal_coef']
        modeldiff.append(dict(month=month,old_fit=a['fit_rows'],new_fit=b['fit_rows'],old_cal=a['cal_rows'],new_cal=b['cal_rows'],
                              effective_coef_cosine=float(ca@cb/(np.linalg.norm(ca)*np.linalg.norm(cb))),
                              effective_coef_relative_change=float(np.linalg.norm(cb-ca)/np.linalg.norm(ca))))
    pd.DataFrame(modeldiff).to_csv(OUT/'monthly_model_changes.csv',index=False)
    history=pd.read_csv(NEW/'dataset/price_history.csv.gz')
    alias=pd.read_csv(NEW/'dataset/inputs/aliases.csv').set_index('symbol').provider_symbol.to_dict()
    history['symbol']=history.symbol.map(lambda s:alias.get(s,s))
    history=history[(history.trade_date>='2023-01-03')&(history.trade_date<='2025-01-31')].drop_duplicates(['symbol','trade_date'])
    prices={}
    for r in history.itertuples():
        prices[r.symbol,r.trade_date]={**{k:getattr(r,k+'_price') for k in ('open','high','low','close')},'volume':r.volume,
                                      'execution_blocked':any(flag in str(r.quality_flags) for flag in ('PREDECESSOR_IDENTITY_UNRESOLVED','TERMINAL_CORPORATE_ACTION_UNRESOLVED'))}
    calendar=pd.read_csv(NEW/'dataset/inputs/calendar.csv.gz').trade_date
    days=calendar[(calendar>='2023-01-03')&(calendar<='2025-01-31')].tolist()
    experiments=[];accounts={}
    def run(name,frame):
        a=run_account(days,prices,rankings(frame),.001)
        accounts[name]=a
        experiments.append(dict(experiment=name,**{k:a[k] for k in ('status','total_return_pct','max_drawdown_pct','trades','halt')}))
        pd.DataFrame(a['fills']).to_csv(OUT/(name+'_fills.csv'),index=False)
        return a
    run('old_scores_new_execution',old)
    run('old_model_new_universe',score(new,models[0]))
    run('new_model_old_universe',score(old,models[1]))
    run('new_scores_new_execution',new)
    for name,base,replacement in [('old_except_first_decision',old,new),('new_except_first_decision',new,old)]:
        # The date is observed from the original executions, not selected to improve return.
        changed=base.copy()
        mask=changed.date=='2023-06-21'
        values=replacement[replacement.date=='2023-06-21'].set_index('symbol').probability
        changed.loc[mask,'probability']=changed.loc[mask,'symbol'].map(values)
        run(name,changed)
    for label,frame,model in [('old',old,models[0]),('new',new,models[1])]:
        reconstructed=score(frame,model)
        assert np.max(np.abs(reconstructed.probability-frame.probability))<1e-12
        for amplitude in (1e-6,1e-4):
            for seed in range(5):
                perturb=frame.copy()
                perturb['probability']=np.clip(perturb.probability+np.random.default_rng(seed).normal(0,amplitude,len(frame)),0,1)
                run(f'{label}_noise_{amplitude}_{seed}',perturb)
    pd.DataFrame(experiments).to_csv(OUT/'counterfactuals.csv',index=False)
    a,b=trades(OLD),trades(NEW)
    key=['symbol','decision','entry']
    t=a.merge(b,on=key,how='outer',suffixes=('_old','_new'),indicator=True,validate='one_to_one')
    t['pnl_delta']=t.pnl_new.fillna(0)-t.pnl_old.fillna(0)
    t['return_contribution_pp']=t.pnl_delta/1000
    t.sort_values('pnl_delta').to_csv(OUT/'trade_attribution.csv',index=False)
    sym=t.groupby('symbol').agg(old_pnl=('pnl_old','sum'),new_pnl=('pnl_new','sum'),pnl_delta=('pnl_delta','sum'),return_contribution_pp=('return_contribution_pp','sum'))
    sym.sort_values('pnl_delta').to_csv(OUT/'symbol_attribution.csv')
    categories=t.groupby('_merge',observed=True).agg(trades=('symbol','size'),pnl_delta=('pnl_delta','sum'))
    categories.to_csv(OUT/'trade_category_attribution.csv')
    old_result=next(r for r in json.loads((OLD/'research/results.json').read_text())['comparisons'] if r['period']=='development' and r['rule']=='PRICE_AI_TOP20' and r['fee']==.001)
    new_result=next(r for r in json.loads((NEW/'research/results.json').read_text())['comparisons'] if r['period']=='development' and r['rule']=='PRICE_AI_TOP20' and r['fee']==.001)
    for expected,name in [(old_result,'old_scores_new_execution'),(new_result,'new_scores_new_execution')]:
        assert abs(expected['total_return_pct']-accounts[name]['total_return_pct'])<1e-9
    assert abs(t.pnl_delta.sum()/1000-(new_result['total_return_pct']-old_result['total_return_pct']))<1e-9
    ea=pd.DataFrame(accounts['old_scores_new_execution']['events'])
    eb=pd.DataFrame(accounts['new_scores_new_execution']['events'])
    divergence=next(d for d in days if not ea[ea.date==d].reset_index(drop=True).equals(eb[eb.date==d].reset_index(drop=True)))
    previous=days[days.index(divergence)-1]
    first=common[common.date==previous].copy()
    first['rank_old']=first.probability_old.rank(ascending=False,method='min')
    first['rank_new']=first.probability_new.rank(ascending=False,method='min')
    first.sort_values('rank_old').head(25).to_csv(OUT/'first_divergence_ranks.csv',index=False)
    report=dict(status='PASS_RECONCILIATION',old_return=old_result['total_return_pct'],new_return=new_result['total_return_pct'],
                delta_pp=float(t.pnl_delta.sum()/1000),common_prediction_rows=len(common),feature_changes=feature_changes,
                probability_abs_change_mean=float(delta.mean()),probability_abs_change_p99=float(delta.quantile(.99)),
                probability_correlation=float(common.probability_old.corr(common.probability_new)),
                universe_changes=rowdiff._merge.value_counts().to_dict(),first_different_execution=divergence,preceding_decision=previous,
                input_hashes={str(p):digest(p) for base in (OLD,NEW) for p in [base/'research/predictions.csv.gz',base/'research/models.json',base/'research/fills.csv']})
    (OUT/'diagnosis.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2));print(sym.sort_values('pnl_delta').head(10).to_string());print(pd.DataFrame(experiments).head(4).to_string())


if __name__=='__main__':main()
