"""Independent input/rank reconciliation and paired uncertainty for V9 shadow."""
import json
import math
from pathlib import Path
import numpy as np
import pandas as pd
from diagnose_sp500_probability_quality import resampled_days, interval
from sp500_v9_shadow import ROOT, OUT, V9, PROB, digest


def main():
    protocol=json.loads((OUT/'protocol.json').read_text())
    for p,h in protocol['sourceHashes'].items():assert digest(ROOT/p)==h,p
    check=json.loads((OUT/'verification.json').read_text())
    for p,h in check['outputHashes'].items():assert digest(OUT/p)==h,p
    raw=json.loads(V9.read_text());prob=pd.read_csv(PROB)
    source={(r['signalDate'],r['symbol']):r for r in raw['predictions']
            if r['modelVersion']=='EXPECTED_RETURN_V9_CORRECTED_20261017' and r['horizonDays']==20}
    original={(r['signalDate'],r['symbol']):r for r in raw['controlPredictions']
              if r['modelVersion']=='EXPECTED_RETURN_V9_CORRECTED_20261017' and r['horizonDays']==20}
    excluded=set(protocol['excluded']);pkeys=set(zip(prob[~prob.symbol.isin(excluded)].date,prob[~prob.symbol.isin(excluded)].symbol))
    panel=pd.read_csv(OUT/'matched_predictions.csv')
    assert set(zip(panel.date,panel.symbol))==set(source)&pkeys
    for r in panel.itertuples():
        s=source[r.date,r.symbol];c=original[r.date,r.symbol]
        assert abs(s['expectedReturnPct']-r.v9_absolute)<1e-12
        assert abs(s['expectedExcessReturnPct']-r.v9_excess)<1e-12
        assert abs(c['expectedReturnPct']-r.v9_control)<1e-12
    selected=json.loads((OUT/'candidate_rankings.json').read_text());rank_checks=0
    columns=dict(COMMON_PROBABILITY='probability',COMMON_V9_ABSOLUTE='v9_absolute',
                 COMMON_V9_EXCESS='v9_excess',COMMON_V9_CONTROL='v9_control')
    for day,g in panel.groupby('date'):
        full=prob[(prob.date==day)&~prob.symbol.isin(excluded)]
        cap=float(np.quantile(full.vol20,.75));assert np.allclose(g.vol_cap,cap,rtol=0,atol=1e-12)
        assert g.index_ma50.nunique()==1
        for model,col in columns.items():
            records=sorted(g.to_dict('records'),key=lambda r:(-r[col],r['symbol']))[:math.ceil(len(g)*.2)]
            expected=[r['symbol'] for r in records if r['index_ma50']>0 and r['vol20']<=cap]
            assert expected==[r['symbol'] for r in selected[model][day]];rank_checks+=1
    accounts=json.loads((OUT/'accounts.json').read_text());uncertainty=[];contributions=[]
    for fee in (.001,.002):
        base=next(a for a in accounts if a['model']=='COMMON_PROBABILITY' and a['fee']==fee)
        for other in [a for a in accounts if a['model'].startswith('COMMON_V9') and a['fee']==fee]:
            x=pd.DataFrame(base['equity']);y=pd.DataFrame(other['equity'])
            assert x.date.tolist()==y.date.tolist()
            delta=np.diff(np.log(np.r_[100000,y.equity]))-np.diff(np.log(np.r_[100000,x.equity]))
            for block in (20,40):
                samples=delta[resampled_days(len(delta),block,3000)].mean(axis=1)*10000
                uncertainty.append(dict(model=other['model'],fee=fee,blockSessions=block,
                    meanDailyLogExcessBp=float(delta.mean()*10000),ci95=interval(samples)))
    for a in accounts:
        if not a['model'].startswith('COMMON_'):continue
        for d in sorted(panel.date.unique()):
            trades=[t for t in a['fills'] if t['decision']==d]
            contributions.append(dict(model=a['model'],fee=a['fee'],decision=d,trades=len(trades),
                pnl=sum(t['pnl'] for t in trades),symbols=';'.join(t['symbol'] for t in trades)))
    pd.DataFrame(contributions).to_csv(OUT/'decision_contributions.csv',index=False)
    report=dict(status='PASS',matchedRows=len(panel),commonSymbols=panel.symbol.nunique(),
        decisionDates=panel.date.nunique(),rankChecks=rank_checks,allAccountsComplete=check['completedAccounts']==12,
        primaryIndependentSourceJoin=True,pairedUncertainty=uncertainty,
        limitations='Block CI is exploratory, already inspected period and sparse decisions; no universal superiority inference')
    (OUT/'independent_verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))


if __name__=='__main__':main()
