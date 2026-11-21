"""Independent event checks, cohort reconciliation and diagnostic follow-ups."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import numpy as np
import pandas as pd
from sp500_daily_validation import exit_event,valid_bar

ROOT=Path(__file__).resolve().parents[1]


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def calibration_interval(frame,days,block=40):
    f=frame[frame.actual.notna()].copy();f['error']=100*(f.actual-f.probability)
    byday=f.groupby('date').agg(n=('actual','size'),error=('error','sum')).reindex(days,fill_value=0)
    rng=np.random.default_rng(9011+block);samples=[]
    for _ in range(2000):
        ix=np.concatenate([(np.arange(block)+rng.integers(len(days)))%len(days) for _ in range(math.ceil(len(days)/block))])[:len(days)]
        totals=byday.iloc[ix].sum()
        if totals['n']>0:samples.append(totals['error']/totals['n'])
    return dict(n=len(f),symbols=int(f.symbol.nunique()),days=int(f.date.nunique()),
        meanProbability=float(f.probability.mean()),actualRate=float(f.actual.mean()),
        actualMinusPredictedPp=float(f.error.mean()),ci95=np.quantile(samples,[.025,.975]).tolist(),block=block)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=ROOT/'output/sp500-probability-diagnostics-20261101-final');args=parser.parse_args()
    folder=args.out;report=json.loads((folder/'diagnostics.json').read_text())
    for path,expected in report['sourceHashes'].items():assert digest(ROOT/path)==expected,path
    f=pd.read_csv(folder/'outcome_predictions.csv.gz');t=pd.read_csv(folder/'selected_trade_diagnostics.csv')
    base=ROOT/'output/sp500-actions-20261028-retry/dataset'
    calendar=pd.read_csv(base/'inputs/calendar.csv.gz');days=calendar.loc[calendar.trade_date>='2021-06-07','trade_date'].tolist();loc={d:i for i,d in enumerate(days)}
    aliases=pd.read_csv(base/'inputs/aliases.csv').set_index('symbol').provider_symbol.to_dict()
    prices=pd.read_csv(base/'price_history.csv.gz');prices.symbol=prices.symbol.map(lambda s:aliases.get(s,s))
    price_map={(r.symbol,r.trade_date):dict(open=r.open_price,high=r.high_price,low=r.low_price,close=r.close_price,volume=r.volume,usable=r.price_usable) for r in prices.itertuples()}
    sample=pd.concat([t[['date','symbol']],f[f.actual.notna()][['date','symbol']].sample(600,random_state=20261101)]).drop_duplicates()
    checked=sample.merge(f,on=['date','symbol'],validate='one_to_one')
    for row in checked.itertuples():
        i=loc[row.date];entry=price_map[row.symbol,days[i+1]]['open'];outcome=None
        for age,day in enumerate(days[i+1:i+21],1):
            bar=price_map.get((row.symbol,day));assert bar and bar['usable']==1 and valid_bar(bar)
            outcome=exit_event(bar,entry,age)
            if outcome:break
        assert outcome and outcome[1]==row.exitReason
        assert abs(100*(outcome[0]*.999/(entry*1.001)-1)-row.exitNetReturnPct)<1e-9
    known=f.actual.notna()&f.label.notna();assert (f.loc[known,'actual']==f.loc[known,'label']).all()
    metrics=pd.read_csv(folder/'cohort_metrics.csv')
    for row in metrics.itertuples():
        g=f[(f.date>='2025-01-02')&(f.date<='2026-08-18')] if row.period=='baseline' else f[f.date>='2026-09-17']
        if row.cohort=='executed':g=t[t.date<='2026-08-18'] if row.period=='baseline' else t[t.date>='2026-09-17']
        elif row.cohort=='top20':g=g[g.top20]
        elif row.cohort=='top20_trend':g=g[g.top20&g.trendPass]
        elif row.cohort=='top20_vol':g=g[g.top20&g.volPass]
        elif row.cohort=='combined_candidates':g=g[g.combined]
        g=g[g.actual.notna()]
        assert len(g)==row.n and abs(g.actual.mean()-row.actualTpRate)<1e-12
        assert abs(((g.probability-g.actual)**2).mean()-row.brier)<1e-12
        assert abs(g.netReturnPct.mean()-row.meanNetReturnPct)<1e-10
    shadow=pd.read_csv(folder/'shadow_predictions.csv.gz')
    for audit in report['shadowTraining']:
        train=f[(f.date>=audit['trainingStart'])&(f.date<audit['cutoff'])&(f.maturity<audit['cutoff'])&f.actual.notna()&(f.session%5==0)]
        assert len(train)==audit['rows'] and train.maturity.max()==audit['maxMaturity']<audit['cutoff']
        current=shadow[shadow.date.str.startswith(audit['month'])]
        assert np.allclose(current.prior,train.actual.mean(),rtol=0,atol=1e-12)
    # Follow-ups: tail calibration, selection attrition, timing concentration and payoff decomposition.
    b=f[(f.date>='2025-01-02')&(f.date<='2026-08-18')&f.actual.notna()]
    span=[d for d in days if '2025-01-02'<=d<='2026-08-18']
    tail={name:calibration_interval(g,span) for name,g in [('p30plus',b[b.probability>=.3]),('p40plus',b[b.probability>=.4]),('p50plus',b[b.probability>=.5])]}
    attrition=[]
    for lo,hi in [(0,.1),(.1,.2),(.2,.3),(.3,.4),(.4,1.000001)]:
        g=b[(b.probability>=lo)&(b.probability<hi)]
        attrition.append(dict(lower=lo,upper=min(hi,1),all=len(g),top20=int(g.top20.sum()),
            afterTrend=int((g.top20&g.trendPass).sum()),afterVol=int((g.top20&g.volPass).sum()),combined=int(g.combined.sum())))
    bt=t[t.date<='2026-08-18'];decomp=[]
    for band,g in bt.groupby('probBand'):
        for reason,h in g.groupby('reason'):
            decomp.append(dict(band=band,reason=reason,n=len(h),fraction=len(h)/len(g),
                meanReturn=float(h.netReturnPct.mean()),contributionPp=float(h.netReturnPct.sum()/len(g))))
    pd.DataFrame(decomp).to_csv(folder/'payoff_decomposition.csv',index=False)
    quarters=bt.groupby(['probBand','quarter']).agg(n=('actual','size'),tp=('actual','mean'),meanReturn=('netReturnPct','mean'))
    quarters.to_csv(folder/'trade_quarter_mix.csv')
    yearly=[]
    for year,g in b.groupby(b.date.str[:4]):
        high=g[g.probability>=.4]
        yearly.append(dict(year=year,highCount=len(high),meanProbability=float(high.probability.mean()),actualRate=float(high.actual.mean()),
            meanVol20=float(high.vol20.mean()),meanTrend=float(high.index_ma50.mean())))
    supplementary=dict(highTail=tail,attrition=attrition,highTailByYear=yearly,
        probabilityVolatilitySpearman=float(b.probability.corr(b.vol20,method='spearman')),
        probabilityTrendSpearman=float(b.probability.corr(b.index_ma50,method='spearman')),
        lowBandQuarterMix=quarters.loc['0-10'].reset_index().to_dict('records'),
        caveat='Correlated security-day samples; intervals are approximate calendar-block sensitivity checks, not causal proof or untouched validation.')
    (folder/'followup_diagnostics.json').write_text(json.dumps(supplementary,indent=2,allow_nan=False),encoding='utf-8')
    verification=dict(status='PASS',sourceHashesUnchanged=True,eventPathsIndependentlyChecked=len(checked),
        closedTrades=338,cohortsReconciled=len(metrics),shadowMonthlyMaturityChecks=len(report['shadowTraining']),
        outputs={p.name:digest(p) for p in folder.iterdir() if p.is_file() and p.name!='verification.json'})
    (folder/'verification.json').write_text(json.dumps(verification,indent=2),encoding='utf-8')
    print(json.dumps(dict(verification=verification,followup=supplementary),indent=2))


if __name__=='__main__':main()
