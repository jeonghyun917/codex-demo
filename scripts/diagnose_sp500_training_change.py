"""Rebuild causal training samples and isolate each added symbol's influence."""
import gc
import json
import numpy as np
import pandas as pd
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
import sp500_daily_validation as engine
from diagnose_sp500_return_change import OLD,NEW,OUT,FEATURES,rankings


def fit_score(train,current,spec,seed=731):
    fit=train[train.maturity<spec['cal_start']]
    cal=train[(train.date>=spec['cal_start'])&(train.date<spec['cutoff'])&(train.maturity<spec['cutoff'])]
    model=make_pipeline(StandardScaler(),LogisticRegression(C=.1,max_iter=1000,random_state=seed))
    model.fit(fit[FEATURES].to_numpy(),fit.label.to_numpy())
    calibration=LogisticRegression(C=1,max_iter=1000,random_state=seed)
    calibration.fit(model.decision_function(cal[FEATURES].to_numpy()).reshape(-1,1),cal.label.to_numpy())
    prob=calibration.predict_proba(model.decision_function(current[FEATURES].to_numpy()).reshape(-1,1))[:,1]
    return prob


def main():
    training=[];prices=None;days=None
    for path in (OLD,NEW):
        engine.DATA=path/'dataset'
        days,prices,members,rows,excluded,levels=engine.load_features()
        train=pd.DataFrame([r for r in rows if r['label'] is not None and r['session']%5==0 and r['date']<'2025-01-01'])
        training.append(train)
        del rows,members,levels;gc.collect()
        print('loaded training',path.name,len(train),flush=True)
        if path==OLD:
            del prices;prices=None;gc.collect()
    old,new=training
    key=['symbol','date']
    joined=old.merge(new,on=key,suffixes=('_old','_new'),how='outer',indicator=True,validate='one_to_one')
    additions=joined[joined._merge=='right_only'][key]
    removed=joined[joined._merge=='left_only'][key]
    same=joined[joined._merge=='both']
    changes={k:int((np.abs(same[k+'_old']-same[k+'_new'])>1e-12).sum()) for k in FEATURES+['label']}
    additions.to_csv(OUT/'added_training_keys.csv',index=False)
    removed.to_csv(OUT/'removed_training_keys.csv',index=False)
    extra=new.merge(additions,on=key,validate='one_to_one')
    extra.to_csv(OUT/'added_training_samples.csv',index=False)
    assert len(removed)==0 and not any(changes.values()),(len(removed),changes)
    stored=[pd.read_csv(p/'research/predictions.csv.gz') for p in (OLD,NEW)]
    stored=[f[(f.date>='2023-01-03')&(f.date<='2024-12-31')].reset_index(drop=True) for f in stored]
    specs={m['month']:m for m in json.loads((NEW/'research/models.json').read_text()) if m['status']=='FIT'}
    periods=[d for d in days if '2023-01-03'<=d<='2025-01-31']
    variants={'refit_old':old,'refit_new':new}
    for symbol in sorted(extra.symbol.unique()):
        variants['add_only_'+symbol]=pd.concat([old,extra[extra.symbol==symbol]],ignore_index=True).sort_values(key).reset_index(drop=True)
    summaries=[];maxerr={};seedcheck={}
    for name,train in variants.items():
        frame=stored[0].copy()
        for month,idx in frame.groupby(frame.date.str[:7]).groups.items():
            prob=fit_score(train,frame.loc[idx],specs[month])
            frame.loc[idx,'probability']=prob
            if name.startswith('refit_'):
                original=stored[0 if name=='refit_old' else 1].loc[idx,'probability'].to_numpy()
                maxerr[name]=max(maxerr.get(name,0),float(np.max(np.abs(prob-original))))
                if month=='2023-06':
                    repeated=fit_score(train,frame.loc[idx],specs[month],seed=999)
                    seedcheck[name]=float(np.max(np.abs(prob-repeated)))
        account=engine.run_account(periods,prices,rankings(frame),.001)
        summaries.append(dict(variant=name,training_rows=len(train),status=account['status'],return_pct=account['total_return_pct'],mdd_pct=account['max_drawdown_pct'],trades=account['trades']))
        pd.DataFrame(account['fills']).to_csv(OUT/(name+'_fills.csv'),index=False)
        print(summaries[-1],flush=True)
    assert all(v<1e-10 for v in maxerr.values()),maxerr
    summary=extra.groupby('symbol').agg(rows=('date','size'),first_date=('date','min'),last_date=('date','max'),tp_labels=('label','sum'))
    summary.to_csv(OUT/'added_training_summary.csv')
    pd.DataFrame(summaries).to_csv(OUT/'training_ablations.csv',index=False)
    result=dict(status='PASS',common_training_rows=len(same),removed_training_rows=len(removed),added_training_rows=len(extra),common_changes=changes,
                refit_probability_max_error=maxerr,june_seed_change_probability_max_error=seedcheck)
    (OUT/'training_verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(summary.to_string());print(json.dumps(result,indent=2))


if __name__=='__main__':main()
