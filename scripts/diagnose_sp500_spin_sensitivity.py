"""Conservative exclusion of added labels spanning an unresolved distribution."""
import json
import pandas as pd
import sp500_daily_validation as engine
from diagnose_sp500_return_change import NEW,OUT,rankings
from diagnose_sp500_training_change import fit_score


def main():
    engine.DATA=NEW/'dataset'
    days,prices,members,rows,excluded,levels=engine.load_features()
    train=pd.DataFrame([r for r in rows if r['label'] is not None and r['session']%5==0 and r['date']<'2025-01-01'])
    del rows,members,levels
    mask=(train.symbol=='FBIN')&(train.date<'2022-12-15')&(train.maturity>='2022-12-15')
    suspect=train[mask]
    suspect.to_csv(OUT/'spin_spanning_training_samples.csv',index=False)
    actual=[]
    for r in suspect.itertuples():
        start=days.index(r.date)+1;entry=prices[r.symbol,days[start]]['open']
        for age,d in enumerate(days[start:start+20],1):
            event=engine.exit_event(prices[r.symbol,d],entry,age)
            if event:
                actual.append(dict(symbol=r.symbol,decision=r.date,maturity=r.maturity,exit=d,reason=event[1],
                                   label=int(r.label),actual_exit_on_or_after_spin=d>='2022-12-15'))
                break
    pd.DataFrame(actual).to_csv(OUT/'spin_spanning_label_exits.csv',index=False)
    train=train[~mask]
    frame=pd.read_csv(NEW/'research/predictions.csv.gz')
    frame=frame[(frame.date>='2023-01-03')&(frame.date<='2024-12-31')].reset_index(drop=True)
    specs={m['month']:m for m in json.loads((NEW/'research/models.json').read_text()) if m['status']=='FIT'}
    for month,idx in frame.groupby(frame.date.str[:7]).groups.items():
        frame.loc[idx,'probability']=fit_score(train,frame.loc[idx],specs[month])
    account=engine.run_account([d for d in days if '2023-01-03'<=d<='2025-01-31'],prices,rankings(frame),.001)
    result=dict(removed_samples=len(suspect),actual_labels_crossing_spin=sum(r['actual_exit_on_or_after_spin'] for r in actual),
                **{k:account[k] for k in ('status','total_return_pct','max_drawdown_pct','trades')},
                limitation='Conservative exclusion sensitivity, not certified distribution-adjusted replacement labels.')
    (OUT/'spin_exclusion.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    pd.DataFrame(account['fills']).to_csv(OUT/'spin_exclusion_fills.csv',index=False)
    print(json.dumps(result,indent=2))


if __name__=='__main__':main()
