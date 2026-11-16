"""Resume frozen interrupted cases, then first-inspection retrospective smoke test."""
import gc
import json
import math
from pathlib import Path
import numpy as np
import pandas as pd
import sp500_daily_validation as engine
from sp500_stable_ranking import EXCLUDED,OUT as FROZEN,verify_account
from sp500_risk_filters import filtered_rankings
from diagnose_sp500_training_change import fit_score
from build_sp500_extension import DATA,OUT,BASE
from build_sp500_daily_dataset import digest


def main():
    engine.DATA=DATA
    days,prices,members,rows,excluded,levels=engine.load_features()
    del members,levels;gc.collect()
    frame=pd.DataFrame(rows);del rows
    frame=frame[~frame.symbol.isin(EXCLUDED)].reset_index(drop=True)
    previous=pd.read_csv(FROZEN/'predictions.csv.gz')
    results=[];fills=[];equities=[];events=[]
    def run(name,pred,start,last,end,policies):
        for policy in policies:
            ranks,_=filtered_rankings(pred,policy)
            sessions=[d for d in days if start<=d<=end]
            for fee in (.001,.002):
                a=engine.run_account(sessions,prices,{d:r for d,r in ranks.items() if start<=d<=last},fee)
                verify_account(a,prices,days)
                ident=dict(scenario=name,policy=policy,fee=fee)
                results.append(dict(**ident,**{k:a[k] for k in ('status','halt','start','requested_end','evaluated_end','total_return_pct','prefix_return_pct','max_drawdown_pct','average_cash_pct','trades')},last_decision=last))
                fills.extend(dict(**ident,**r) for r in a['fills']);equities.extend(dict(**ident,**r) for r in a['equity']);events.extend(dict(**ident,**r) for r in a['events'])
                print(ident,a['status'],a['total_return_pct'],a['max_drawdown_pct'],flush=True)
    # Replay unchanged probabilities and the identical seed-4 feature perturbation.
    run('FROZEN_BASELINE_REPLAY',previous,'2025-01-02','2026-08-18','2026-09-16',['TREND50_VOL75'])
    noisy=previous.copy();noisy['vol20']=noisy.vol20*np.exp(np.random.default_rng(4).normal(0,.01,len(noisy)))
    run('INTERRUPTED_VOLATILITY_NOISE4_REPLAY',noisy,'2025-01-02','2026-08-18','2026-09-16',['VOL75','TREND50_VOL75'])
    del previous,noisy;gc.collect()
    meta=json.loads((DATA/'manifest.json').read_text())
    last=days[len(days)-21]
    decisions=[d for d in days if '2026-09-16'<d<=min(last,meta['end'])]
    audit=[]
    if decisions:
        pred=frame[(frame.date>=decisions[0])&(frame.date<=decisions[-1])].copy()
        training=frame[frame.label.notna()&(frame.session%5==0)]
        for month,idx in pred.groupby(pred.date.str[:7]).groups.items():
            cutoff=min(d for d in days if d.startswith(month));cal_start=days[days.index(cutoff)-126]
            spec=dict(cutoff=cutoff,cal_start=cal_start)
            fit=training[training.maturity<cal_start]
            cal=training[(training.date>=cal_start)&(training.date<cutoff)&(training.maturity<cutoff)]
            assert fit.maturity.max()<cal_start and cal.maturity.max()<cutoff
            pred.loc[idx,'probability']=fit_score(training,pred.loc[idx],spec)
            audit.append(dict(month=month,cutoff=cutoff,cal_start=cal_start,fit_rows=len(fit),cal_rows=len(cal),fit_max_maturity=fit.maturity.max(),cal_max_maturity=cal.maturity.max()))
        pred.to_csv(OUT/'unseen_research_predictions.csv.gz',index=False)
        run('NEWLY_INSPECTED_RETROSPECTIVE_WINDOW',pred,decisions[0],decisions[-1],days[-1],['TREND50_VOL75'])
    pd.DataFrame(results).to_csv(OUT/'revalidation.csv',index=False)
    pd.DataFrame(fills).to_csv(OUT/'revalidation_fills.csv',index=False)
    pd.DataFrame(equities).to_csv(OUT/'revalidation_equity.csv.gz',index=False)
    pd.DataFrame(events).to_csv(OUT/'revalidation_events.csv',index=False)
    pd.DataFrame(audit).to_csv(OUT/'unseen_training_audit.csv',index=False)
    gate=json.loads((OUT.parent/'sp500-filter-validation-20261027/prospective_gate.json').read_text())
    (OUT/'unseen_feasibility.json').write_text(json.dumps(dict(status='RECONSTRUCTED_SHORT_RETROSPECTIVE_TEST_NOT_PROSPECTIVE',
        decision_dates=decisions,runoff_end=days[-1],membership_end=meta['end'],
        frozen_at_utc=gate['frozen_at_utc'],prospective_status='NO_POST_FREEZE_PRICE_DATA',
        warning='Only newly inspected retrospective outcomes; retrospective community reconstruction and insufficient independent decisions for stability.'),indent=2),encoding='utf-8')
    (OUT/'revalidation_verification.json').write_text(json.dumps(dict(status='PASS',accounts=len(results),
         complete_accounts=sum(r['status']=='COMPLETE_RESEARCH_ONLY' for r in results),
         source_hashes={str(p):digest(p) for p in [DATA/'manifest.json',FROZEN/'predictions.csv.gz',Path(__file__)]}),indent=2),encoding='utf-8')


if __name__=='__main__':main()
