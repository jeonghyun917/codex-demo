"""Read-only causal diagnostics and predeclared calibration shadow replays."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import brier_score_loss, log_loss, roc_auc_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'output/sp500-actions-20261028-retry'
DATA=BASE/'dataset'
FROZEN=ROOT/'output/sp500-stable-ranking-20261027'
OUT=ROOT/'output/sp500-probability-diagnostics-20261101'
SEED=20261101


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def forward_outcomes(bars,clean,indices,fee,require_full_window=True):
    positions=indices[:,None]+np.arange(1,21)
    bounded=positions[:,-1]<len(bars)
    clipped=np.minimum(positions,len(bars)-1)
    future=bars[clipped]
    op,hi,lo,cl,volume=[future[:,:,i] for i in range(5)]
    bar_valid=np.isfinite(future).all(axis=2) & clean[clipped] & (positions<len(bars))
    bar_valid&=(lo>0)&(lo<=np.minimum(op,cl))&(hi>=np.maximum(op,cl))&(volume>0)
    valid=bar_valid.all(axis=1)&bounded if require_full_window else np.ones(len(indices),bool)
    entry=op[:,0];stop=entry*.95;target=entry*1.15
    reason=np.full(len(indices),'',dtype=object);exit_price=np.full(len(indices),np.nan);age=np.zeros(len(indices),int)
    prefix=np.ones(len(indices),bool)
    for k in range(20):
        prefix &= bar_valid[:,k]
        active=valid & prefix & (reason=='')
        gap=active & (op[:,k]<=stop)
        tp_open=active & ~gap & (op[:,k]>=target)
        sl=active & ~gap & ~tp_open & (lo[:,k]<=stop)
        tp=active & ~gap & ~tp_open & ~sl & (hi[:,k]>=target)
        for mask,name,price in [(gap,'SL_GAP',op[:,k]),(tp_open|tp,'TP',target),(sl,'SL',stop)]:
            reason[mask]=name;exit_price[mask]=price[mask];age[mask]=k+1
        if k==19:
            mask=valid & prefix & (reason=='');reason[mask]='TIME';exit_price[mask]=cl[mask,k];age[mask]=20
    return dict(actual=np.where(reason!='',(reason=='TP').astype(float),np.nan),reason=reason,age=age,
                netReturnPct=100*(exit_price*(1-fee)/(entry*(1+fee))-1))


def interval(values):
    values=values[np.isfinite(values)]
    return np.quantile(values,[.025,.975]).tolist() if len(values) else None


def resampled_days(size,block,reps):
    rng=np.random.default_rng(SEED+block)
    starts=rng.integers(0,size,(reps,math.ceil(size/block)))
    return ((starts[:,:,None]+np.arange(block))%size).reshape(reps,-1)[:,:size]


def block_intervals(frame,days,block=20,reps=3000):
    # Resample the full session grid, preserving no-entry days and concurrent stocks.
    sampled=resampled_days(len(days),block,reps)
    results=[];rates=[];returns=[]
    for lower,upper in [(0,.1),(.1,.2),(.2,.3),(.3,1.000001)]:
        subset=frame[(frame.probability>=lower)&(frame.probability<upper)]
        sums=subset.groupby('date').agg(n=('actual','size'),tp=('actual','sum'),ret=('netReturnPct','sum')).reindex(days,fill_value=0).to_numpy()
        totals=sums[sampled].sum(axis=1);valid=totals[:,0]>0
        rate=np.divide(totals[:,1]*100,totals[:,0],out=np.full(reps,np.nan),where=valid)
        ret=np.divide(totals[:,2],totals[:,0],out=np.full(reps,np.nan),where=valid)
        rates.append(rate);returns.append(ret)
        results.append(dict(lower=lower,upper=min(upper,1),count=len(subset),uniqueSymbols=int(subset.symbol.nunique()) if 'symbol' in subset else None,
            decisionDays=int(subset.date.nunique()),tpCI=interval(rate),returnCI=interval(ret),validResamples=int(valid.sum())))
    return dict(blockSessions=block,repetitions=reps,bins=results,
        difference20to30Minus0to10=dict(tpCI=interval(rates[2]-rates[0]),returnCI=interval(returns[2]-returns[0])))


def matched_comparison(frame,stratum):
    f=frame[(frame.probability<.1)|(frame.probability>=.2)].copy()
    f['side']=np.where(f.probability<.1,'low','high')
    groups=f.groupby([stratum,'side']).agg(n=('actual','size'),tp=('actual','mean'),ret=('netReturnPct','mean'))
    shared=[]
    for key in f[stratum].unique():
        if (key,'low') in groups.index and (key,'high') in groups.index:
            low=groups.loc[(key,'low')];high=groups.loc[(key,'high')]
            shared.append((low.n+high.n,100*(high.tp-low.tp),high.ret-low.ret))
    n=sum(r[0] for r in shared)
    return dict(sharedStrata=len(shared),matchedRows=int(n),totalComparisonRows=len(f),
        standardizedTpDifferencePp=sum(r[0]*r[1] for r in shared)/n if n else None,
        standardizedReturnDifferencePp=sum(r[0]*r[2] for r in shared)/n if n else None,
        caveat='Descriptive pooled-common-stratum weighting, not causal adjustment; unmatched strata excluded.')


def metrics(frame,column='probability'):
    f=frame[frame.actual.notna() & frame[column].notna()]
    if not len(f):return dict(n=0)
    p=f[column].to_numpy();y=f.actual.to_numpy()
    return dict(n=len(f),symbols=int(f.symbol.nunique()),days=int(f.date.nunique()),meanProbability=float(p.mean()),
        actualTpRate=float(y.mean()),calibrationGap=float(y.mean()-p.mean()),brier=float(brier_score_loss(y,p)),
        logLoss=float(log_loss(y,p,labels=[0,1])),auc=float(roc_auc_score(y,p)) if len(set(y))==2 else None,
        meanNetReturnPct=float(f.netReturnPct.mean()),stopRate=float(f.reason.isin(['SL','SL_GAP']).mean()))


def bin_table(frame,ident):
    rows=[]
    for lo in range(0,100,10):
        f=frame[(frame.probability>=lo/100)&(frame.probability<((lo+10)/100 if lo<90 else 1.000001))]
        f=f[f.actual.notna()]
        rows.append(dict(**ident,lower=lo,upper=lo+10,**metrics(f),
            meanVol20=float(f.vol20.mean()) if len(f) else None,meanTrend50=float(f.index_ma50.mean()) if len(f) else None))
    return rows


def load_frame():
    frame=pd.concat([pd.read_csv(FROZEN/'predictions.csv.gz'),pd.read_csv(BASE/'unseen_research_predictions.csv.gz')],ignore_index=True)
    assert not frame.duplicated(['date','symbol']).any()
    calendar=pd.read_csv(DATA/'inputs/calendar.csv.gz')
    days=calendar.loc[calendar.trade_date>='2021-06-07','trade_date'].tolist();loc={d:i for i,d in enumerate(days)}
    prices=pd.read_csv(DATA/'price_history.csv.gz')
    aliases=pd.read_csv(DATA/'inputs/aliases.csv').set_index('symbol').provider_symbol.to_dict()
    prices['symbol']=prices.symbol.map(lambda s:aliases.get(s,s))
    columns=['open_price','high_price','low_price','close_price','volume','price_usable']
    duplicates=prices[prices.duplicated(['symbol','trade_date'],keep=False)]
    if len(duplicates):assert (duplicates.groupby(['symbol','trade_date'])[columns].nunique(dropna=False)<=1).all().all()
    prices=prices.drop_duplicates(['symbol','trade_date'])
    bysymbol={s:g for s,g in prices.groupby('symbol')};pieces=[]
    for symbol,g in frame.groupby('symbol'):
        bars=bysymbol[symbol].set_index('trade_date').reindex(days)
        indices=np.array([loc[d] for d in g.date])
        outcomes=forward_outcomes(bars[columns[:5]].to_numpy(),bars.price_usable.fillna(0).to_numpy()==1,indices,.001)
        closed=forward_outcomes(bars[columns[:5]].to_numpy(),bars.price_usable.fillna(0).to_numpy()==1,indices,.001,False)
        part=g.copy()
        for k,v in outcomes.items():part[k]=v
        for k,v in closed.items():part['exit'+k[0].upper()+k[1:]]=v
        pieces.append(part)
    frame=pd.concat(pieces,ignore_index=True).sort_values(['date','probability','symbol'],ascending=[True,False,True])
    frame['top20']=frame.groupby('date').cumcount()<np.ceil(frame.groupby('date').symbol.transform('size')*.2)
    cap=frame.groupby('date').vol20.transform(lambda x:x.quantile(.75))
    frame['trendPass']=frame.index_ma50>0;frame['volPass']=frame.vol20<=cap
    frame['combined']=frame.top20 & frame.trendPass & frame.volPass
    frame['probBand']=pd.cut(frame.probability,[-.000001,.1,.2,.3,1.000001],right=False,labels=['0-10','10-20','20-30','30-100']).astype(str)
    frame['quarter']=pd.to_datetime(frame.date).dt.to_period('Q').astype(str)
    frame['volBand']=pd.cut(frame.vol20,[0,.015,.025,np.inf],labels=['low_le1.5pct','mid_le2.5pct','high_gt2.5pct']).astype(str)
    frame['trendBand']=pd.cut(frame.index_ma50,[-np.inf,0,.02,np.inf],labels=['below50','above0to2pct','above2pct']).astype(str)
    frame['jointStratum']=frame.quarter+'|'+frame.volBand+'|'+frame.trendBand
    return frame,days


def shadow_replay(frame,days):
    f=frame.copy();f['logit']=np.log(f.probability.clip(1e-6,1-1e-6)/(1-f.probability.clip(1e-6,1-1e-6)))
    for col in ['prior','platt','context']:f[col]=np.nan
    audit=[]
    for month in sorted(f.loc[f.date>='2025-01-01','date'].str[:7].unique()):
        cutoff=min(d for d in days if d.startswith(month));start=days[max(0,days.index(cutoff)-252)]
        train=f[(f.date>=start)&(f.date<cutoff)&(f.maturity<cutoff)&f.actual.notna()&(f.session%5==0)]
        target=f.index[f.date.str.startswith(month)]
        assert len(train)>1000 and train.actual.nunique()==2
        f.loc[target,'prior']=train.actual.mean()
        models={}
        for name,features in [('platt',['logit']),('context',['logit','vol20','index_ma50'])]:
            model=make_pipeline(StandardScaler(),LogisticRegression(C=1,max_iter=1000,random_state=SEED))
            model.fit(train[features],train.actual)
            f.loc[target,name]=model.predict_proba(f.loc[target,features])[:,1]
            models[name]=dict(features=features,coef=model[-1].coef_[0].tolist(),intercept=float(model[-1].intercept_[0]))
        audit.append(dict(month=month,cutoff=cutoff,trainingStart=start,rows=len(train),maxMaturity=train.maturity.max(),models=models))
    return f,audit


def loss_difference(frame,days,column,block=20):
    f=frame[frame.actual.notna()&frame[column].notna()].copy()
    f['delta']=(f[column]-f.actual)**2-(f.probability-f.actual)**2
    sums=f.groupby('date').agg(n=('delta','size'),delta=('delta','sum')).reindex(days,fill_value=0).to_numpy()
    totals=sums[resampled_days(len(days),block,3000)].sum(axis=1)
    delta=np.divide(totals[:,1],totals[:,0],out=np.full(3000,np.nan),where=totals[:,0]>0)
    return dict(mean=float(f.delta.mean()),ci95=interval(delta),blockSessions=block)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=OUT);args=parser.parse_args()
    if args.out.exists():raise ValueError('Refusing overwrite')
    sources=[Path(__file__),FROZEN/'predictions.csv.gz',FROZEN/'training_audit.csv',BASE/'unseen_research_predictions.csv.gz',
             BASE/'unseen_training_audit.csv',DATA/'price_history.csv.gz',DATA/'inputs/calendar.csv.gz',DATA/'inputs/aliases.csv',
             BASE/'revalidation_fills.csv',ROOT/'output/sp500-probability-bins-20261101/report.json',
             ROOT/'data/sp500-paper-ledger/protocol.json',ROOT/'data/sp500-paper-ledger/decisions/2026-10-30.json']
    hashes={str(p.relative_to(ROOT)):digest(p) for p in sources}
    args.out.mkdir(parents=True)
    frame,days=load_frame();print('Reconstructed outcomes',len(frame),flush=True)
    comparable=frame.actual.notna()&frame.label.notna()
    label_check=dict(rows=len(frame),known=int(frame.actual.notna().sum()),compared=int(comparable.sum()),
        disagreements=int((frame.loc[comparable,'actual']!=frame.loc[comparable,'label']).sum()),
        newlyKnown=int((frame.actual.notna()&frame.label.isna()).sum()),newlyUnknown=int((frame.actual.isna()&frame.label.notna()).sum()))
    assert label_check['disagreements']==0,label_check
    audits=pd.concat([pd.read_csv(FROZEN/'training_audit.csv'),pd.read_csv(BASE/'unseen_training_audit.csv')])
    assert (audits.fit_max_maturity<audits.cal_start).all() and (audits.cal_max_maturity<audits.cutoff).all()
    raw=pd.read_csv(BASE/'revalidation_fills.csv')
    selected=raw[(raw.policy=='TREND50_VOL75')&(raw.fee==.001)&raw.scenario.isin(['FROZEN_BASELINE_REPLAY','NEWLY_INSPECTED_RETROSPECTIVE_WINDOW'])]
    trades=selected.merge(frame,left_on=['decision','symbol'],right_on=['date','symbol'],validate='one_to_one',suffixes=('_fill',''))
    censored_trades=trades[trades.actual.isna()][['symbol','decision','entry','exit','reason_fill']].to_dict('records')
    for k in ('actual','reason','netReturnPct'):trades[k]=trades['exit'+k[0].upper()+k[1:]]
    assert len(trades)==len(selected) and trades.actual.notna().all()
    assert (trades.reason_fill==trades.reason).all() and trades.combined.all()
    assert np.allclose(trades.netReturnPct,100*trades.pnl/trades.budget,atol=1e-9)
    frame.to_csv(args.out/'outcome_predictions.csv.gz',index=False)
    trades.to_csv(args.out/'selected_trade_diagnostics.csv',index=False)
    cohorts=[];bins=[];strata=[];bootstrap={};matched={};concentration=[]
    periods=[('baseline','2025-01-02','2026-08-18'),('short','2026-09-17','2026-09-25')]
    for period,start,end in periods:
        f=frame[(frame.date>=start)&(frame.date<=end)];t=trades[(trades.date>=start)&(trades.date<=end)]
        masks={'all':np.ones(len(f),bool),'top20':f.top20,'top20_trend':f.top20&f.trendPass,
               'top20_vol':f.top20&f.volPass,'combined_candidates':f.combined}
        for name,mask in masks.items():
            g=f[mask];cohorts.append(dict(period=period,cohort=name,**metrics(g)))
            bins+=bin_table(g,dict(period=period,cohort=name))
        cohorts.append(dict(period=period,cohort='executed',**metrics(t)));bins+=bin_table(t,dict(period=period,cohort='executed'))
        for dim in ['quarter','volBand','trendBand']:
            for value,g in t.groupby(dim):strata+=bin_table(g,dict(period=period,dimension=dim,value=value))
            matched[period+'_'+dim]=matched_comparison(t,dim)
        matched[period+'_joint']=matched_comparison(t,'jointStratum')
        span=[d for d in days if start<=d<=end]
        bootstrap[period]=[block_intervals(t,span,b) for b in (20,40) if len(span)>=b]
        for band,g in t.groupby('probBand'):
            for dimension in ['symbol','quarter']:
                leave=[]
                for value in g[dimension].unique():
                    remaining=g[g[dimension]!=value]
                    if len(remaining):leave.append(dict(excluded=value,n=len(remaining),tpRate=float(remaining.actual.mean()),meanReturn=float(remaining.netReturnPct.mean())))
                concentration.append(dict(period=period,probBand=band,dimension=dimension,n=len(g),
                    distinct=int(g[dimension].nunique()),largestShare=float(g[dimension].value_counts(normalize=True).max()),leaveOneOut=leave))
    pd.DataFrame(cohorts).to_csv(args.out/'cohort_metrics.csv',index=False)
    pd.DataFrame(bins).to_csv(args.out/'cohort_bins.csv',index=False)
    pd.DataFrame(strata).to_csv(args.out/'selected_strata.csv',index=False)
    print('Selection, strata and block bootstrap complete',flush=True)
    shadow,audit=shadow_replay(frame,days);shadow=shadow[shadow.date>='2025-01-01']
    shadow.to_csv(args.out/'shadow_predictions.csv.gz',index=False)
    shadow_metrics=[];shadow_losses={}
    for period,start,end in [('2025H1','2025-01-02','2025-06-30'),('2025H2','2025-07-01','2025-12-31'),('2026','2026-01-02','2026-08-18'),('short','2026-09-17','2026-09-25')]:
        f=shadow[(shadow.date>=start)&(shadow.date<=end)]
        for cohort,g in [('all',f),('combined_candidates',f[f.combined])]:
            for model in ['probability','prior','platt','context']:
                shadow_metrics.append(dict(period=period,cohort=cohort,model=model,**metrics(g,model)))
    for cohort,g in [('all',shadow[shadow.date<='2026-08-18']),('combined_candidates',shadow[(shadow.date<='2026-08-18')&shadow.combined])]:
        for model in ['prior','platt','context']:
            shadow_losses[cohort+'_'+model]=loss_difference(g,[d for d in days if '2025-01-02'<=d<='2026-08-18'],model)
    pd.DataFrame(shadow_metrics).to_csv(args.out/'shadow_metrics.csv',index=False)
    result=dict(status='ANALYSIS_COMPLETE_RESEARCH_ONLY',strategyChanged=False,sourceHashes=hashes,
        labelValidation=label_check,selectedTradeAgreement=len(trades),closedTradesWithoutFullLabel=censored_trades,trainingMonthsChecked=len(audits),
        uncertainty=bootstrap,matchedStrata=matched,concentration=concentration,shadowTraining=audit,shadowBrierDifferences=shadow_losses,
        methodology=dict(target='+15% before -5%, next open, inclusive 20 sessions; gap stops and SL-first ambiguous bars',
            fees=.001,bootstrap='Circular moving blocks of 20 and 40 full sessions, 3000 draws, seed fixed; approximate dependence-aware intervals.',
            shadow='Monthly prior/Platt/context calibration on previous 252 sessions of out-of-sample predictions; weekly samples; full maturity before cutoff; C=1; no tuning.',
            limitations='All historical windows were previously inspected, not untouched holdouts. No live probability or order changes. Strata are observational, not causal. Research universe and prices remain imperfect.'))
    assert all(digest(ROOT/p)==h for p,h in hashes.items()),'Inputs changed during analysis'
    (args.out/'diagnostics.json').write_text(json.dumps(result,indent=2,allow_nan=False),encoding='utf-8')
    print(json.dumps(dict(labelValidation=label_check,tradeAgreement=len(trades),cohorts=cohorts,shadowBrierDifferences=shadow_losses),indent=2))


if __name__=='__main__':main()
