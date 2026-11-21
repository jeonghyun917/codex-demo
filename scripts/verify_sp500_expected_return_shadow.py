"""Reconcile saved shadow models, targets, orders, cash and frozen baseline trades."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
from threadpoolctl import threadpool_limits

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'output/sp500-actions-20261028-retry'


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def first_exit(entry,rows):
    stop=entry*.95;target=entry*1.15
    for age,r in enumerate(rows,1):
        if r['open_price']<=stop:return r['trade_date'],r['open_price'],'SL_GAP',age
        if r['open_price']>=target:return r['trade_date'],target,'TP',age
        if r['low_price']<=stop:return r['trade_date'],stop,'SL',age
        if r['high_price']>=target:return r['trade_date'],target,'TP',age
        if age==20:return r['trade_date'],r['close_price'],'TIME',age
    raise ValueError('No exit found')


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=ROOT/'output/sp500-expected-return-shadow-20261101');args=parser.parse_args()
    out=args.out;protocol=json.loads((out/'protocol.json').read_text())
    for path,expected in protocol['sourceHashes'].items():assert digest(ROOT/path)==expected,path
    pred=pd.read_csv(out/'predictions.csv.gz');targets=pd.read_csv(out/'training_targets.csv.gz')
    audit=pd.read_csv(out/'training_audit.csv');accounts=json.loads((out/'accounts.json').read_text())
    reference=pd.read_csv(BASE/'revalidation_fills.csv')
    prices=pd.read_csv(BASE/'dataset/price_history.csv.gz')
    aliases=pd.read_csv(BASE/'dataset/inputs/aliases.csv').set_index('symbol').provider_symbol.to_dict()
    prices.symbol=prices.symbol.map(lambda s:aliases.get(s,s))
    prices=prices.drop_duplicates(['symbol','trade_date'])
    bars={(r['symbol'],r['trade_date']):r for r in prices.to_dict('records')}
    days=pd.read_csv(BASE/'dataset/inputs/calendar.csv.gz').trade_date.tolist();loc={d:i for i,d in enumerate(days)}
    for a in audit.itertuples():
        train=targets[(targets.date<a.cutoff)&(targets.maturity<a.cutoff)&(targets.session%5==0)&targets.netReturnPct.notna()]
        assert len(train)==a.trainingRows and train.maturity.max()==a.maxMaturity<a.cutoff
        assert not set(train.symbol)&set(protocol['excluded'])
        assert abs(train.netReturnPct.mean()-a.targetMeanPct)<1e-10
        current=pred[pred.date.str.startswith(a.month)].iloc[::max(1,len(pred[pred.date.str.startswith(a.month)])//20)]
        model=joblib.load(out/'models'/f'{a.month}.joblib')
        with threadpool_limits(limits=2):
            assert np.allclose(100*model['returnModel'].predict(current[protocol['features']]),current.expectedReturnPct,atol=1e-10,rtol=0)
            assert np.allclose(model['probabilityControl'].predict_proba(current[protocol['features']])[:,1],current.matchedProbability,atol=1e-10,rtol=0)
    checked_targets=targets[targets.netReturnPct.notna()].sample(1000,random_state=20261101)
    for r in checked_targets.itertuples():
        future=[bars[r.symbol,d] for d in days[loc[r.date]+1:loc[r.date]+21]]
        assert len(future)==20 and all(b['price_usable']==1 for b in future)
        _,px,reason,_=first_exit(future[0]['open_price'],future)
        value=100*(px*.999/(future[0]['open_price']*1.001)-1)
        assert abs(value-r.netReturnPct)<1e-9 and reason==r.exitReason
    candidates={}
    for day,g in pred.groupby('date'):
        n=math.ceil(len(g)*.2);cap=g.vol20.quantile(.75)
        for model,col in [('PROBABILITY','probability'),('EXPECTED_RETURN','expectedReturnPct'),('MATCHED_HGB_PROBABILITY','matchedProbability')]:
            ranked=g.sort_values([col,'symbol'],ascending=[False,True]).head(n)
            selected=ranked[(ranked.index_ma50>0)&(ranked.vol20<=cap)]
            candidates[day,model]=selected.symbol.tolist()
        fixed=g[g.symbol.isin(candidates[day,'PROBABILITY'])].sort_values(['expectedReturnPct','symbol'],ascending=[False,True])
        candidates[day,'EXPECTED_RETURN_FIXED_POOL']=fixed.symbol.tolist()
    total_fills=0;baseline_fills=0
    for a in accounts:
        if a['status']!='COMPLETE_RESEARCH_ONLY':continue
        fee=a['fee'];fills=a['fills'];equity=a['equity'];equity_by_day={r['date']:r for r in equity}
        buys={};sells={}
        for t in fills:
            total_fills+=1
            assert t['symbol'] not in protocol['excluded'] and t['symbol'] in candidates[t['decision'],a['model']]
            assert loc[t['entry']]==loc[t['decision']]+1
            assert abs(t['entry_price']-bars[t['symbol'],t['entry']]['open_price'])<1e-9
            future=[bars[t['symbol'],d] for d in days[loc[t['entry']]:loc[t['exit']]+1]]
            exit_day,px,reason,age=first_exit(t['entry_price'],future)
            assert exit_day==t['exit'] and reason==t['reason'] and age==t['age'] and abs(px-t['exit_price'])<1e-9
            debit=t['entry_price']*t['shares']*(1+fee);credit=t['exit_price']*t['shares']*(1-fee)
            assert abs(debit-t['budget'])<1e-7 and abs(credit-debit-t['pnl'])<1e-7
            assert debit<=equity_by_day[t['decision']]['equity']*.1+1e-7
            buys.setdefault(t['entry'],[]).append(t);sells.setdefault(t['exit'],[]).append(t)
        cash=100000.;positions={};peak=100000.;mdd=0.
        for mark in equity:
            day=mark['date'];assert len(buys.get(day,[]))<=2
            for t in buys.get(day,[]):
                assert t['symbol'] not in positions;positions[t['symbol']]=t['shares'];cash-=t['budget']
                assert cash>=-1e-7 and len(positions)<=10
            for t in sells.get(day,[]):
                assert positions.pop(t['symbol'])==t['shares'];cash+=t['proceeds']
            holdings=sum(shares*bars[s,day]['close_price'] for s,shares in positions.items());nav=cash+holdings
            assert abs(nav-mark['equity'])<1e-6 and abs(cash-mark['cash'])<1e-6
            assert mark['positions']==len(positions)
            peak=max(peak,nav);mdd=min(mdd,100*(nav/peak-1))
        assert not positions and abs(100*(cash/100000-1)-a['total_return_pct'])<1e-8
        assert abs(mdd-a['max_drawdown_pct'])<1e-8
        if a['model']=='PROBABILITY':
            scenario='FROZEN_BASELINE_REPLAY' if a['period']=='baseline' else 'NEWLY_INSPECTED_RETROSPECTIVE_WINDOW'
            old=reference[(reference.scenario==scenario)&(reference.policy=='TREND50_VOL75')&(reference.fee==fee)]
            joined=old.merge(pd.DataFrame(fills),on=['symbol','decision'],validate='one_to_one',suffixes=('_old',''))
            assert len(joined)==len(old)==len(fills)
            for col in ['entry','exit','reason','shares']:assert (joined[col]==joined[col+'_old']).all()
            assert np.allclose(joined.pnl,joined.pnl_old,rtol=0,atol=1e-7);baseline_fills+=len(fills)
    # Inspect expected versus realized trade returns without using outcomes to alter ranking.
    deciles=[];attribution=[]
    for period,start,last,end in protocol['periods']:
        f=pred[(pred.date>=start)&(pred.date<=last)&pred.netReturnPct.notna()].copy()
        f['decile']=pd.qcut(f.expectedReturnPct.rank(method='first'),10,labels=False)
        for decile,g in f.groupby('decile'):
            deciles.append(dict(period=period,decile=int(decile)+1,n=len(g),predictedMeanPct=float(g.expectedReturnPct.mean()),
                actualMeanPct=float(g.netReturnPct.mean()),tpRate=float(g.targetTp.mean())))
        for a in [a for a in accounts if a['period']==period and a['fee']==.001 and a['status']=='COMPLETE_RESEARCH_ONLY']:
            fills=pd.DataFrame(a['fills'])
            for reason,g in fills.groupby('reason'):
                attribution.append(dict(period=period,model=a['model'],reason=reason,n=len(g),pnl=float(g.pnl.sum()),
                    meanNetReturnPct=float((100*g.pnl/g.budget).mean())))
    pd.DataFrame(deciles).to_csv(out/'score_deciles.csv',index=False)
    pd.DataFrame(attribution).to_csv(out/'payoff_attribution.csv',index=False)
    result=dict(status='PASS',sourceHashesUnchanged=True,monthlyModelsChecked=len(audit),targetPathsChecked=1000,
        completeAccountsChecked=sum(a['status']=='COMPLETE_RESEARCH_ONLY' for a in accounts),
        incompleteAccounts=[dict(period=a['period'],model=a['model'],fee=a['fee'],status=a['status'],halt=a['halt']) for a in accounts if a['status']!='COMPLETE_RESEARCH_ONLY'],
        fillsChecked=total_fills,baselineFillsReproduced=baseline_fills,
        checks=['maturity purge and excluded symbols','saved model prediction parity','net targets incl. gaps and costs',
                'score then frozen filters, no backfill','next-open entry and first barrier exit','daily cash NAV and drawdown',
                'position count and capital limits','exact original baseline trades'])
    for path,expected in protocol['sourceHashes'].items():assert digest(ROOT/path)==expected,path
    result['outputHashes']={p.name:digest(p) for p in out.iterdir() if p.is_file() and p.name!='verification.json'}
    result['modelHashes']={p.name:digest(p) for p in (out/'models').glob('*.joblib')}
    (out/'verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('outputHashes','modelHashes')},indent=2))


if __name__=='__main__':main()
