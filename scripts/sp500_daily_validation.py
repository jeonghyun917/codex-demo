"""Fixed-protocol daily research. No live signals invented, DB writes, orders or tuning."""
import csv
import gzip
import json
import math
from collections import Counter, defaultdict
from datetime import date

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import brier_score_loss, roc_auc_score

from build_sp500_daily_dataset import read_csv, write_csv, digest
from sp500_validation_data import DATA, OUT

FEATURES=['ret20','ret60','vol20','atr20','high63','index_ret20','index_ma50']


def exit_event(bar, entry, age):
    stop,target=entry*.95,entry*1.15
    if bar['open'] <= stop:
        return bar['open'],'SL_GAP'
    if bar['open'] >= target:
        return target,'TP'
    if bar['low'] <= stop:
        return stop,'SL'
    if bar['high'] >= target:
        return target,'TP'
    if age >= 20:
        return bar['close'],'TIME'
    return None


def valid_bar(b):
    if b is not None and b.get('execution_blocked', False):
        return False
    return b is not None and all(math.isfinite(b[k]) for k in ('open','high','low','close')) and 0<b['low']<=min(b['open'],b['close'])<=max(b['open'],b['close'])<=b['high'] and math.isfinite(b.get('volume',1)) and b.get('volume',1)>0


def run_account(days, prices, rankings, fee=.001):
    cash=100000.; positions={}; pending=[]; equity=[]; fills=[]; events=[]
    peak=100000.; mdd=0.; halt=None
    for day in days:
        # Validate today's required observations only after yesterday's orders are fixed.
        required=set(positions)|{o['symbol'] for o in pending}
        bad=[s for s in sorted(required) if not valid_bar(prices.get((s,day)))]
        if bad:
            halt=dict(date=day,symbols=bad,reason='MISSING_OR_INVALID_ORDER_OR_HELD_MARK')
            break
        for order in pending:
            s=order['symbol']; op=prices[s,day]['open']
            shares=math.floor(min(order['budget'],cash)/(op*(1+fee)))
            if shares <= 0:
                continue
            debit=shares*op*(1+fee)
            cash-=debit
            position=dict(symbol=s,decision=order['decision'],entry=day,entry_price=op,
                          shares=shares,budget=debit,age=0)
            positions[s]=position
            events.append(dict(date=day,symbol=s,side='BUY',shares=shares,price=op,fee_amount=shares*op*fee,cash_delta=-debit))
        pending=[]
        for s,p in list(positions.items()):
            p['age']+=1
            b=prices[s,day]
            outcome=exit_event(b,p['entry_price'],p['age'])
            if outcome:
                px,reason=outcome
                credit=p['shares']*px*(1-fee)
                cash+=credit
                fills.append(dict(**p,exit=day,exit_price=px,reason=reason,proceeds=credit,pnl=credit-p['budget'],
                                  ambiguous=int(b['low']<=p['entry_price']*.95 and b['high']>=p['entry_price']*1.15)))
                events.append(dict(date=day,symbol=s,side='SELL',shares=p['shares'],price=px,fee_amount=p['shares']*px*fee,cash_delta=credit))
                del positions[s]
        holdings=sum(p['shares']*prices[s,day]['close'] for s,p in positions.items())
        nav=cash+holdings
        peak=max(peak,nav); mdd=min(mdd,100*(nav/peak-1))
        assert cash>=-1e-6 and len(positions)<=10
        equity.append(dict(date=day,cash=cash,holdings=holdings,equity=nav,positions=len(positions)))
        reserved=0.; names=set(positions)
        for candidate in rankings.get(day,[]):
            if len(pending)>=min(2,10-len(positions)):
                break
            s=candidate['symbol']
            if s in names:
                continue
            budget=min(nav*.1,cash-reserved)
            if budget<=0:
                break
            pending.append(dict(symbol=s,decision=day,budget=budget))
            names.add(s); reserved+=budget
    status='BLOCKED_MISSING_PRICE' if halt else 'OPEN_POSITIONS_AT_END' if positions or pending else 'COMPLETE_RESEARCH_ONLY'
    complete=status=='COMPLETE_RESEARCH_ONLY'
    last=equity[-1]['equity'] if equity else 100000.
    return dict(status=status,halt=halt,start=days[0],requested_end=days[-1],
                evaluated_end=equity[-1]['date'] if equity else None,
                total_return_pct=100*(last/100000-1) if complete else None,
                prefix_return_pct=100*(last/100000-1),max_drawdown_pct=mdd,
                average_cash_pct=float(np.mean([100*r['cash']/r['equity'] for r in equity])) if equity else 100.,
                trades=len(fills),stop_pct=100*sum(f['reason'].startswith('SL') for f in fills)/len(fills) if fills else None,
                tp_pct=100*sum(f['reason']=='TP' for f in fills)/len(fills) if fills else None,
                ambiguous_trades=sum(f['ambiguous'] for f in fills),
                equity=equity,fills=fills,events=events)


def load_features():
    aliases={r['symbol']:r['provider_symbol'] for r in read_csv(DATA/'inputs/aliases.csv')}
    calendar=read_csv(DATA/'inputs/calendar.csv.gz')
    calendar=[r for r in calendar if r['trade_date']>='2021-06-07']
    days=[r['trade_date'] for r in calendar]; loc={d:i for i,d in enumerate(days)}
    levels=pd.Series([float(r['benchmark_level']) for r in calendar])
    index_ret=levels/levels.shift(20)-1
    index_ma=levels/levels.rolling(50).mean()-1
    prices={}; clean={}
    with gzip.open(DATA/'price_history.csv.gz','rt',newline='',encoding='utf-8') as f:
        for r in csv.DictReader(f):
            s=aliases.get(r['symbol'],r['symbol']); key=s,r['trade_date']
            b={k:float(r[k+'_price']) for k in ('open','high','low','close')}
            b['volume']=float(r['volume'])
            b['execution_blocked']=any(flag in r['quality_flags'] for flag in ('PREDECESSOR_IDENTITY_UNRESOLVED','TERMINAL_CORPORATE_ACTION_UNRESOLVED'))
            if key in prices:
                assert prices[key]==b,key
            prices[key]=b
            # Quarantine excludes training/features, not hidden deletion of future trades.
            clean[key]=r['price_usable']=='1'
    members=defaultdict(set)
    with gzip.open(DATA/'daily_panel.csv.gz','rt',newline='',encoding='utf-8') as f:
        for r in csv.DictReader(f):
            s=aliases.get(r['symbol'],r['symbol'])
            assert s not in members[r['trade_date']],(s,r['trade_date'])
            members[r['trade_date']].add(s)
    rows=[]; censored=Counter()
    symbols=sorted({s for group in members.values() for s in group})
    for s in symbols:
        frame=pd.DataFrame([prices.get((s,d),{}) if clean.get((s,d),False) else {} for d in days],columns=['open','high','low','close'])
        close=frame['close']; ret=close/close.shift(1)-1
        tr=pd.concat([frame['high']-frame['low'],(frame['high']-close.shift(1)).abs(),(frame['low']-close.shift(1)).abs()],axis=1).max(axis=1)
        feats=pd.DataFrame(dict(ret20=close/close.shift(20)-1,ret60=close/close.shift(60)-1,
                               vol20=ret.rolling(20).std(),atr20=tr.rolling(20).mean()/close,
                               high63=close/frame['high'].rolling(63).max()-1,
                               index_ret20=index_ret,index_ma50=index_ma))
        complete=close.rolling(63).count()==63
        for i,d in enumerate(days):
            if s not in members.get(d,set()):
                continue
            if not complete.iloc[i] or not np.isfinite(feats.iloc[i].values).all():
                censored['missing_causal_history']+=1;continue
            label=None; maturity=days[i+20] if i+20<len(days) else None
            if maturity:
                future=[prices.get((s,t)) for t in days[i+1:i+21]]
                if all(valid_bar(b) and clean.get((s,t),False) for b,t in zip(future,days[i+1:i+21])):
                    entry=future[0]['open']
                    for age,b in enumerate(future,1):
                        event=exit_event(b,entry,age)
                        if event:
                            label=int(event[1]=='TP');break
            if label is None:
                censored['unknown_future_label']+=1
            rows.append(dict(symbol=s,date=d,session=i,maturity=maturity,label=label,**feats.iloc[i].to_dict()))
    return days,prices,members,rows,dict(censored),levels.tolist()


def main():
    result_dir=OUT/'research'
    result_dir.mkdir(exist_ok=True)
    days,prices,members,rows,excluded,levels=load_features()
    print('features',len(rows),'excluded',excluded,flush=True)
    # Fixed weekly training samples reduce redundant overlapping labels; scoring remains daily.
    train=[r for r in rows if r['label'] is not None and r['session']%5==0]
    predictions=[]; models=[]
    months=sorted({r['date'][:7] for r in rows if r['date']>='2023-01-01'})
    for month in months:
        current=[r for r in rows if r['date'].startswith(month)]
        cutoff=min(r['date'] for r in current); i=days.index(cutoff)
        cal_start=days[max(0,i-126)]
        fit=[r for r in train if r['maturity']<cal_start]
        cal=[r for r in train if cal_start<=r['date']<cutoff and r['maturity']<cutoff]
        if len(fit)<1000 or len(cal)<200 or len({r['label'] for r in fit})<2 or len({r['label'] for r in cal})<2:
            models.append(dict(month=month,status='INSUFFICIENT_TRAINING',fit_rows=len(fit),cal_rows=len(cal)))
            continue
        X=lambda rs:np.asarray([[r[k] for k in FEATURES] for r in rs])
        model=make_pipeline(StandardScaler(),LogisticRegression(C=.1,max_iter=1000,random_state=731))
        model.fit(X(fit),[r['label'] for r in fit])
        calibration=LogisticRegression(C=1,max_iter=1000,random_state=731)
        calibration.fit(model.decision_function(X(cal)).reshape(-1,1),[r['label'] for r in cal])
        p=calibration.predict_proba(model.decision_function(X(current)).reshape(-1,1))[:,1]
        base=float(np.mean([r['label'] for r in fit+cal]))
        predictions.extend(dict(r,probability=float(prob),historical_rate=base) for r,prob in zip(current,p))
        scaler,lr=model.steps[0][1],model.steps[1][1]
        models.append(dict(month=month,status='FIT',cutoff=cutoff,fit_rows=len(fit),cal_rows=len(cal),
                           fit_max_maturity=max(r['maturity'] for r in fit),cal_start=cal_start,
                           cal_max_maturity=max(r['maturity'] for r in cal),
                           mean=scaler.mean_.tolist(),scale=scaler.scale_.tolist(),coef=lr.coef_[0].tolist(),
                           intercept=float(lr.intercept_[0]),cal_coef=float(calibration.coef_[0,0]),cal_intercept=float(calibration.intercept_[0])))
        print('model',month,len(fit),len(cal),flush=True)
    write_csv(result_dir/'predictions.csv.gz',predictions,list(predictions[0]))
    (result_dir/'models.json').write_text(json.dumps(models,indent=2),encoding='utf-8')
    groups=defaultdict(list)
    for r in predictions:
        groups[r['date']].append(r)
    rules={name:{} for name in ('CASH','MOM20_TOP20','PRICE_AI_TOP20','PRICE_AI_TOP20_TREND50')}
    for day,group in groups.items():
        for name,field in [('MOM20_TOP20','ret20'),('PRICE_AI_TOP20','probability'),('PRICE_AI_TOP20_TREND50','probability')]:
            ordered=sorted(group,key=lambda r:(-r[field],r['symbol']))[:max(1,math.ceil(len(group)*.2))]
            if name.endswith('TREND50') and group[0]['index_ma50']<=0:
                ordered=[]
            rules[name][day]=[dict(symbol=r['symbol'],score=r[field]) for r in ordered]
    reports=[]; equities=[]; fills=[]; events=[]
    periods=[('development','2023-01-03','2024-12-31'),
             ('inspected_evaluation','2025-01-02','2026-08-18'),
             ('complete_price_window','2026-06-03','2026-07-31')]
    for period,start,end in periods:
        if period=='complete_price_window':
            # Keep the entire 20-session runoff inside the complete-price window.
            end=days[days.index(end)-20]
        decision_days=[d for d in days if start<=d<=end]
        end_i=days.index(decision_days[-1])+20
        sessions=[d for d in days if start<=d<=days[end_i]]
        for fee in (.001,.002):
            for rule,ranking in rules.items():
                chosen={d:r for d,r in ranking.items() if start<=d<=end}
                account=run_account(sessions,prices,chosen,fee)
                account['last_decision_date']=end
                identity=dict(period=period,rule=rule,fee=fee)
                equities.extend(dict(**identity,**r) for r in account.pop('equity'))
                fills.extend(dict(**identity,**r) for r in account.pop('fills'))
                events.extend(dict(**identity,**r) for r in account.pop('events'))
                start_i=days.index(sessions[0]); last_i=days.index(sessions[-1])
                account['sp500_price_reference_pct']=100*(levels[last_i]/levels[start_i]-1)
                reports.append(dict(**identity,**account))
                print(period,rule,fee,account['status'],account['total_return_pct'],flush=True)
    write_csv(result_dir/'daily_equity.csv.gz',equities,list(equities[0]))
    write_csv(result_dir/'fills.csv',fills,list(fills[0]))
    write_csv(result_dir/'events.csv',events,list(events[0]))
    labels=[r for r in predictions if r['date']>='2025-01-01' and r['label'] is not None]
    quality=dict(n=len(labels),brier=float(brier_score_loss([r['label'] for r in labels],[r['probability'] for r in labels])),
                 base_brier=float(brier_score_loss([r['label'] for r in labels],[r['historical_rate'] for r in labels])),
                 auc=float(roc_auc_score([r['label'] for r in labels],[r['probability'] for r in labels])),
                 observed_tp_rate=float(np.mean([r['label'] for r in labels])),
                 mean_probability=float(np.mean([r['probability'] for r in labels])))
    report=dict(status='DIAGNOSTIC_ONLY_NO_STRATEGY_PROMOTION',features=FEATURES,excluded=excluded,
                model='NEW_PRICE_ONLY_LOGISTIC_NOT_EXISTING_SIGNAL_OR_20D_MODEL',probability_quality=quality,
                comparisons=reports,blocked_comparisons=['Existing20D daily history','Existing signal daily history','Previous mixed-input probability daily history'],
                restrictions=['Incomplete historical member prices, reconstructed membership, provider-adjusted prices and no total-return accounting.',
                              'All periods previously inspected; no untouched final test. Unknown future labels excluded from training, counts reported.',
                              'Feature eligibility uses only present/past prices; portfolios halt on missing ordered/held prices.',
                              'Valid but large moves are allowed in execution and remain corporate-action review risk; excluded from features/labels.',
                              'S&P500 price reference is close-to-close, before costs/dividends, not an executable matched portfolio.'],
                hashes={str(p):digest(p) for p in [DATA/'manifest.json',__import__('pathlib').Path(__file__)]})
    (result_dir/'results.json').write_text(json.dumps(report,indent=2,allow_nan=False),encoding='utf-8')
    print(json.dumps(quality,indent=2))


if __name__=='__main__':
    main()
