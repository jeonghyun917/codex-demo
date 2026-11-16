"""Offline expected-net-return ranking. Never publishes or changes the paper ledger."""
import argparse
from datetime import datetime,timezone
import gc
import hashlib
import json
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
import sklearn
from sklearn.ensemble import HistGradientBoostingRegressor,HistGradientBoostingClassifier
from threadpoolctl import threadpool_limits
import sp500_daily_validation as engine
import sp500_paper_ledger as ledger
from sp500_risk_filters import filtered_rankings
from sp500_stable_ranking import EXCLUDED,verify_account
from diagnose_sp500_probability_quality import forward_outcomes,resampled_days,interval

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'output/sp500-actions-20261028-retry'
DATA=BASE/'dataset'
FROZEN=ROOT/'output/sp500-stable-ranking-20261027'
OUT=ROOT/'output/sp500-expected-return-shadow-20261101'
PARAMS=dict(max_iter=100,learning_rate=.05,max_leaf_nodes=7,max_depth=3,min_samples_leaf=100,
            l2_regularization=10,max_bins=63,early_stopping=False,random_state=20261101)
PERIODS=[('baseline','2025-01-02','2026-08-18','2026-09-16'),('short','2026-09-17','2026-09-25','2026-10-23')]


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def mature_training(frame,cutoff):
    return frame[(frame.date<cutoff)&(frame.maturity<cutoff)&(frame.session%5==0)&frame.netReturnPct.notna()]


def fee_adjusted_return(value,fee):
    return 100*((1+value/100)*((1-fee)/(1+fee))/(.999/1.001)-1)


def ranking_variants(frame):
    if not np.isfinite(frame[['probability','expectedReturnPct','matchedProbability']].to_numpy()).all():
        raise ValueError('Invalid score')
    original,_=filtered_rankings(frame,'TREND50_VOL75')
    expected,_=filtered_rankings(frame.assign(probability=frame.expectedReturnPct),'TREND50_VOL75')
    matched,_=filtered_rankings(frame.assign(probability=frame.matchedProbability),'TREND50_VOL75')
    lookup=frame.set_index(['date','symbol']).expectedReturnPct.to_dict()
    fixed={d:sorted([dict(symbol=r['symbol'],score=lookup[d,r['symbol']]) for r in rows],
                     key=lambda r:(-r['score'],r['symbol'])) for d,rows in original.items()}
    return dict(PROBABILITY=original,EXPECTED_RETURN=expected,EXPECTED_RETURN_FIXED_POOL=fixed,MATCHED_HGB_PROBABILITY=matched)


def build_targets(frame,days):
    loc={d:i for i,d in enumerate(days)}
    aliases=pd.read_csv(DATA/'inputs/aliases.csv').set_index('symbol').provider_symbol.to_dict()
    prices=pd.read_csv(DATA/'price_history.csv.gz')
    prices.symbol=prices.symbol.map(lambda s:aliases.get(s,s))
    cols=['open_price','high_price','low_price','close_price','volume','price_usable']
    duplicates=prices[prices.duplicated(['symbol','trade_date'],keep=False)]
    if len(duplicates):assert (duplicates.groupby(['symbol','trade_date'])[cols].nunique(dropna=False)<=1).all().all()
    grouped={s:g for s,g in prices.drop_duplicates(['symbol','trade_date']).groupby('symbol')}
    parts=[]
    for symbol,g in frame.groupby('symbol'):
        bars=grouped[symbol].set_index('trade_date').reindex(days)
        outcome=forward_outcomes(bars[cols[:5]].to_numpy(),bars.price_usable.fillna(0).to_numpy()==1,
                                 np.array([loc[d] for d in g.date]),.001)
        part=g.copy();part['netReturnPct']=outcome['netReturnPct'];part['targetTp']=outcome['actual'];part['exitReason']=outcome['reason']
        assert np.array_equal(part.label.isna(),part.targetTp.isna())
        valid=part.label.notna();assert (part.loc[valid,'label']==part.loc[valid,'targetTp']).all()
        parts.append(part)
    return pd.concat(parts,ignore_index=True).sort_values(['date','symbol']).reset_index(drop=True)


def equity_segments(account):
    frame=pd.DataFrame(account['equity']);frame['before']=frame.equity.shift(1).fillna(100000)
    frame['segment']=frame.date.str[:4]+'H'+np.where(frame.date.str[5:7]<='06','1','2')
    result=[]
    for segment,g in frame.groupby('segment',sort=True):
        start=float(g.before.iloc[0]);values=np.r_[start,g.equity.to_numpy()]
        result.append(dict(segment=segment,start=g.date.iloc[0],end=g.date.iloc[-1],
            returnPct=100*(values[-1]/start-1),maxDrawdownPct=100*np.min(values/np.maximum.accumulate(values)-1)))
    return result


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=OUT);args=parser.parse_args()
    if args.out.exists():raise ValueError('Refusing overwrite')
    ledger.checked_data(DATA);protocol=ledger.protocol(ledger.DEFAULT);ledger.decisions(ledger.DEFAULT,protocol)
    sources=[Path(__file__),Path(engine.__file__),ROOT/'scripts/sp500_risk_filters.py',ROOT/'scripts/sp500_stable_ranking.py',
             ROOT/'scripts/diagnose_sp500_probability_quality.py',DATA/'manifest.json',DATA/'verification.json',DATA/'price_history.csv.gz',
             DATA/'daily_panel.csv.gz',DATA/'inputs/calendar.csv.gz',DATA/'inputs/aliases.csv',FROZEN/'predictions.csv.gz',
             BASE/'unseen_research_predictions.csv.gz',BASE/'revalidation.csv',BASE/'revalidation_fills.csv',
             ledger.DEFAULT/'protocol.json',ledger.DEFAULT/'decisions/2026-10-30.json']
    hashes={str(p.relative_to(ROOT)):digest(p) for p in sources}
    args.out.mkdir(parents=True);(args.out/'models').mkdir()
    config=dict(version='EXPECTED_RETURN_SHADOW_V1',createdAtUtc=datetime.now(timezone.utc).isoformat(),
        classification='RETROSPECTIVE_RESEARCH_ONLY_NOT_UNTOUCHED_HOLDOUT',strategyChanged=False,
        features=engine.FEATURES,excluded=sorted(EXCLUDED),parameters=PARAMS,periods=PERIODS,
        target='Expected net simple trade return: exit*(1-fee)/(entry*(1+fee))-1, next open, TP15/SL5/inclusive20, gap stops and SL-first ambiguity',
        targetFee=.001,fees=[.001,.002],training='Expanding from earliest verified feature history; monthly fit, weekly samples, full 20-session maturity strictly before month start',
        variants=['PROBABILITY','EXPECTED_RETURN','EXPECTED_RETURN_FIXED_POOL','MATCHED_HGB_PROBABILITY'],
        ranking='Top ceil(20%) by score, then unchanged index_ma50>0 and cross-sectional vol20<=75th percentile. No backfill or positive-score cutoff.',
        account='100000 USD, <=10 positions, <=2 new/day, <=10% previous NAV per stock, whole shares, no leverage; cash included',
        feeConversion='Uniform two-sided fees are an affine transform of expected return, so 10/20bp rankings are identical; account share sizes and cash are separately simulated.',
        noTuning=True,sourceHashes=hashes,versions=dict(numpy=np.__version__,pandas=pd.__version__,sklearn=sklearn.__version__))
    (args.out/'protocol.json').write_text(json.dumps(config,indent=2),encoding='utf-8')
    engine.DATA=DATA
    days,prices,members,rows,excluded,levels=engine.load_features()
    frame=pd.DataFrame(rows);del rows,members,levels;gc.collect()
    frame=frame[~frame.symbol.isin(EXCLUDED)].reset_index(drop=True)
    frame=build_targets(frame,days);frame.to_csv(args.out/'training_targets.csv.gz',index=False)
    print('Targets built',len(frame),'known',frame.netReturnPct.notna().sum(),flush=True)
    pred=pd.concat([pd.read_csv(FROZEN/'predictions.csv.gz'),pd.read_csv(BASE/'unseen_research_predictions.csv.gz')],ignore_index=True)
    pred=pred[((pred.date>='2025-01-02')&(pred.date<='2026-08-18'))|((pred.date>='2026-09-17')&(pred.date<='2026-09-25'))].reset_index(drop=True)
    check=pred.merge(frame,on=['symbol','date'],suffixes=('_frozen',''),validate='one_to_one')
    assert len(check)==len(pred)
    assert all(np.allclose(check[k+'_frozen'],check[k],rtol=0,atol=1e-10) for k in engine.FEATURES)
    pred=check[['symbol','date','session','maturity','probability','netReturnPct','targetTp','exitReason']+engine.FEATURES].copy()
    pred['expectedReturnPct']=np.nan;pred['matchedProbability']=np.nan
    audit=[]
    with threadpool_limits(limits=2):
        for month,indices in pred.groupby(pred.date.str[:7]).groups.items():
            cutoff=min(d for d in days if d.startswith(month));train=mature_training(frame,cutoff)
            assert len(train)>1000 and train.targetTp.nunique()==2
            reg=HistGradientBoostingRegressor(loss='squared_error',**PARAMS)
            cls=HistGradientBoostingClassifier(loss='log_loss',**PARAMS)
            X=train[engine.FEATURES];current=pred.loc[indices,engine.FEATURES]
            reg.fit(X,train.netReturnPct/100);cls.fit(X,train.targetTp)
            pred.loc[indices,'expectedReturnPct']=100*reg.predict(current)
            pred.loc[indices,'matchedProbability']=cls.predict_proba(current)[:,1]
            joblib.dump(dict(returnModel=reg,probabilityControl=cls,features=engine.FEATURES,cutoff=cutoff),args.out/'models'/f'{month}.joblib')
            audit.append(dict(month=month,cutoff=cutoff,trainingRows=len(train),firstTrainingDate=train.date.min(),
                lastTrainingDate=train.date.max(),maxMaturity=train.maturity.max(),targetMeanPct=float(train.netReturnPct.mean()),
                tp=int((train.exitReason=='TP').sum()),sl=int(train.exitReason.isin(['SL','SL_GAP']).sum()),time=int((train.exitReason=='TIME').sum())))
            print('Fit',month,len(train),flush=True)
    pred['expectedReturn20bpPct']=fee_adjusted_return(pred.expectedReturnPct,.002)
    pred.to_csv(args.out/'predictions.csv.gz',index=False);pd.DataFrame(audit).to_csv(args.out/'training_audit.csv',index=False)
    rankings=ranking_variants(pred);reference=pd.read_csv(BASE/'revalidation.csv');accounts=[];summary=[];segments=[]
    for period,start,last,end in PERIODS:
        span=[d for d in days if start<=d<=end]
        for name,rank in rankings.items():
            selected={d:r for d,r in rank.items() if start<=d<=last}
            for fee in (.001,.002):
                account=engine.run_account(span,prices,selected,fee);verify_account(account,prices,days)
                ident=dict(period=period,model=name,fee=fee)
                accounts.append(dict(**ident,**account))
                result=dict(**ident,**{k:account[k] for k in ('status','halt','start','requested_end','evaluated_end','total_return_pct','prefix_return_pct','max_drawdown_pct','average_cash_pct','trades','stop_pct','tp_pct','ambiguous_trades')})
                result['meanTradeReturnPct']=float(np.mean([100*t['pnl']/t['budget'] for t in account['fills']])) if account['fills'] else None
                result['negativeExpectedEntryCount']=sum(float(pred.loc[(pred.date==t['decision'])&(pred.symbol==t['symbol']),'expectedReturnPct'].iloc[0])<0 for t in account['fills'])
                summary.append(result);segments.extend(dict(**ident,**s) for s in equity_segments(account))
                if name=='PROBABILITY':
                    scenario='FROZEN_BASELINE_REPLAY' if period=='baseline' else 'NEWLY_INSPECTED_RETROSPECTIVE_WINDOW'
                    old=reference[(reference.scenario==scenario)&(reference.policy=='TREND50_VOL75')&(reference.fee==fee)].iloc[0]
                    assert old.status==account['status'] and old.trades==account['trades']
                    for key in ('total_return_pct','max_drawdown_pct','average_cash_pct'):assert abs(old[key]-account[key])<1e-8
                print('Account',ident,account['status'],account['total_return_pct'],flush=True)
    (args.out/'accounts.json').write_text(json.dumps(accounts,indent=2,allow_nan=False),encoding='utf-8')
    pd.DataFrame(summary).to_csv(args.out/'comparison.csv',index=False);pd.DataFrame(segments).to_csv(args.out/'segments.csv',index=False)
    differences=[]
    for fee in (.001,.002):
        base=next(a for a in accounts if a['period']=='baseline' and a['model']=='PROBABILITY' and a['fee']==fee)
        for alternative in [a for a in accounts if a['period']=='baseline' and a['model']!='PROBABILITY' and a['fee']==fee]:
            if alternative['status']!='COMPLETE_RESEARCH_ONLY':continue
            a=pd.DataFrame(base['equity']);b=pd.DataFrame(alternative['equity']);assert a.date.tolist()==b.date.tolist()
            delta=np.diff(np.log(np.r_[100000,b.equity]))-np.diff(np.log(np.r_[100000,a.equity]))
            for block in (20,40):
                samples=delta[resampled_days(len(delta),block,3000)].mean(axis=1)*10000
                differences.append(dict(model=alternative['model'],fee=fee,block=block,meanDailyLogExcessBp=float(delta.mean()*10000),ci95=interval(samples)))
    (args.out/'paired_uncertainty.json').write_text(json.dumps(differences,indent=2),encoding='utf-8')
    for path,expected in hashes.items():assert digest(ROOT/path)==expected,path
    ledger.protocol(ledger.DEFAULT)
    (args.out/'run_checks.json').write_text(json.dumps(dict(status='PASS',sourceHashesUnchanged=True,baselineAccountsReproduced=4,
        trainingMonths=len(audit),accounts=len(accounts),completedAccounts=sum(a['status']=='COMPLETE_RESEARCH_ONLY' for a in accounts)),indent=2),encoding='utf-8')
    print(pd.DataFrame(summary)[['period','model','fee','status','total_return_pct','max_drawdown_pct','trades']].to_string(index=False))


if __name__=='__main__':main()
