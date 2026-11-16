"""Cash-account study of causal volatility, market trend and three-month-high filters."""
import bisect
import calendar
import csv
import hashlib
import json
import sys
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path
import numpy as np
from barrier_research import query

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/barrier-portfolio-20261018'
SOURCE=ROOT/'output/barrier-probability-20261018'
PRICE=ROOT/'output/barrier-research-20261018/prices.csv'
RULES=['BASE','SIGNAL55','VOL2','VOL3','TREND50','TREND200','HIGH90','HIGH80_95',
       'V3_T50','V3_T50_H90','V2_T50_H90','V3_T50_H80_95']


def portfolio(rows, prices, sessions, quotas, initial=100000., cost=.001):
    cash=initial; positions={}; equity=[]; fills=[]; orders=defaultdict(list)
    for row in rows:
        orders[row['entry']].append(row)
    def mark(symbol,day,field):
        bar=prices.get((symbol,day))
        if bar is None or bar[field]<=0:
            raise ValueError(f'Missing or invalid mark: {symbol} {day} {field}')
        return bar[field]
    def sell(symbol):
        nonlocal cash
        p=positions.pop(symbol)
        proceeds=p['shares']*p['row']['exit_price']*(1-cost)
        cash+=proceeds
        p['fill']['proceeds']=proceeds
        p['fill']['pnl']=proceeds-p['fill']['budget']
    peak=initial; drawdown=0
    for day in sessions:
        for symbol,p in list(positions.items()):
            r=p['row']; op=mark(symbol,day,'open')
            if r['exit']==day and (r['reason']=='SL_GAP' or (r['reason']=='TP' and op>=r['exit_price'])):
                sell(symbol)
        nav=cash+sum(p['shares']*mark(s,day,'open') for s,p in positions.items())
        today=orders[day]
        budgets=[nav/quotas[r['date']] for r in today]
        scale=min(1.,cash/sum(budgets)) if budgets and sum(budgets)>0 else 0
        for r,budget in zip(today,budgets):
            budget*=scale
            if budget<1e-8:
                continue
            symbol=r['symbol']; op=mark(symbol,day,'open')
            assert abs(op-r['entry_price'])<1e-5 and symbol not in positions
            shares=budget/(op*(1+cost))
            cash-=budget
            fill=dict(symbol=symbol,decision=r['date'],entry=day,exit=r['exit'],reason=r['reason'],
                      entry_price=op,exit_price=r['exit_price'],budget=budget,shares=shares,proceeds=None,pnl=None)
            fills.append(fill); positions[symbol]=dict(row=r,shares=shares,fill=fill)
        assert cash>=-1e-6, 'Borrowing is forbidden'
        cash=max(0.,cash)
        for symbol,p in list(positions.items()):
            if p['row']['exit']==day:
                sell(symbol)
        holdings=sum(p['shares']*mark(s,day,'close') for s,p in positions.items())
        nav=cash+holdings; peak=max(peak,nav); drawdown=min(drawdown,100*(nav/peak-1))
        equity.append(dict(date=day,cash=cash,holdings=holdings,equity=nav,positions=len(positions)))
    assert not positions, 'Evaluation must include all exits'
    assert abs(cash-initial-sum(f['pnl'] for f in fills))<1e-6
    return dict(start=sessions[0],end=sessions[-1],initial_equity=initial,ending_equity=cash,
                total_return_pct=100*(cash/initial-1),max_drawdown_pct=drawdown,
                average_cash_pct=float(np.mean([100*r['cash']/r['equity'] for r in equity])),
                funded_trades=len(fills),stop_pct=100*sum(f['reason'].startswith('SL') for f in fills)/len(fills) if fills else None,
                tp_pct=100*sum(f['reason']=='TP' for f in fills)/len(fills) if fills else None,
                decision_months=len({f['decision'][:7] for f in fills}),equity=equity,fills=fills)


def context_features(bars, benchmark, decision):
    cutoff=date.fromisoformat(decision)
    serial=cutoff.year*12+cutoff.month-1-3
    year,month=divmod(serial,12); month+=1
    lower=date(year,month,min(cutoff.day,calendar.monthrange(year,month)[1])).isoformat()
    prior=[b for b in bars if lower<=b['date']<=decision]
    bench=[b for b in benchmark if b['date']<=decision][-200:]
    if (len(prior)<40 or len(bench)!=200 or any(b['level'] is None for b in bench)
            or len({b['source'] for b in prior})!=1
            or (date.fromisoformat(prior[0]['date'])-date.fromisoformat(lower)).days>7
            or (cutoff-date.fromisoformat(prior[-1]['date'])).days>7
            or (cutoff-date.fromisoformat(bench[-1]['date'])).days>7):
        return None
    level=bench[-1]['level']
    return dict(high_ratio=100*prior[-1]['close']/max(b['high'] for b in prior),
                trend50=level>np.mean([b['level'] for b in bench[-50:]]),
                trend200=level>np.mean([b['level'] for b in bench]),
                feature_price_date=prior[-1]['date'],benchmark_date=bench[-1]['date'])


def allowed(row, rule):
    v2=row['vol20']<=2; v3=row['vol20']<=3; t=row['trend50']
    high=row['high_ratio']>=90; pull=80<=row['high_ratio']<=95
    return {'BASE':True,'SIGNAL55':row['signal']>=55,'VOL2':v2,'VOL3':v3,
            'TREND50':t,'TREND200':row['trend200'],'HIGH90':high,'HIGH80_95':pull,
            'V3_T50':v3 and t,'V3_T50_H90':v3 and t and high,
            'V2_T50_H90':v2 and t and high,'V3_T50_H80_95':v3 and t and pull}[rule]


def dump_csv(path,rows):
    with path.open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=rows[0].keys()); writer.writeheader(); writer.writerows(rows)


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    if '--fetch' in sys.argv:
        data=query("SELECT index_code,trade_date date,benchmark_level level,source FROM "
                   "stock_benchmark_return_series WHERE index_code IN "
                   "('SP500_FREE_20261018','NASDAQ100_FREE_20261018') AND trade_date "
                   "BETWEEN '2023-01-01' AND '2026-10-16' ORDER BY index_code,trade_date")
        dump_csv(OUT/'benchmark.csv',data); print('Benchmark rows',len(data)); return
    with (SOURCE/'strategy-trades.csv').open(newline='') as f:
        original=[r for r in csv.DictReader(f) if r['strategy']=='BARRIER_TOP20']
    candles=defaultdict(list); prices={}; benchmarks=defaultdict(list)
    with PRICE.open(newline='') as f:
        for r in csv.DictReader(f):
            for k in ('open','high','low','close'):
                r[k]=float(r[k])
            candles[r['symbol']].append(r); prices[r['symbol'],r['date']]=r
    with (OUT/'benchmark.csv').open(newline='') as f:
        for r in csv.DictReader(f):
            r['level']=None if r['level']=='NULL' else float(r['level'])
            benchmarks[r['index_code']].append(r)
    report=dict(status='RESEARCH_ONLY',rules=RULES,results=[])
    all_equity=[]; all_fills=[]; enriched=[]
    for index in sorted(benchmarks):
        base=[]; exclusions=Counter()
        raw=[r for r in original if r['index_code']==index]
        quotas=Counter(r['date'] for r in raw)
        for r in raw:
            for k in ('signal','vol20','entry_price','exit_price'):
                r[k]=float(r[k])
            features=context_features(candles[r['symbol']],benchmarks[index],r['date'])
            if features is None:
                exclusions['missing_causal_context']+=1; continue
            base.append({**r,**features})
        enriched.extend(base)
        train=[r for r in base if r['maturity']<'2025-10-01']
        test=[r for r in base if r['date']>='2025-10-01']
        result=dict(index=index,raw_candidates=len(raw),eligible=len(base),exclusions=dict(exclusions),periods={})
        for period,rows in [('full',base),('train',train),('test',test)]:
            start=min(r['entry'] for r in rows); end=max(r['maturity'] for r in rows)
            sessions=[r['date'] for r in benchmarks[index] if start<=r['date']<=end]
            stats={}
            for rule in RULES:
                selected=[r for r in rows if allowed(r,rule)]
                account=portfolio(selected,prices,sessions,quotas)
                all_equity.extend(dict(index=index,period=period,rule=rule,**e) for e in account.pop('equity'))
                all_fills.extend(dict(index=index,period=period,rule=rule,**f) for f in account.pop('fills'))
                stats[rule]=account
                print(index,period,rule,'return',round(account['total_return_pct'],3),
                      'MDD',round(account['max_drawdown_pct'],3),'stops',account['stop_pct'],
                      'trades',account['funded_trades'],flush=True)
            result['periods'][period]=stats
        baseline=result['periods']['train']['BASE']; eligible=[]
        for rule,stat in result['periods']['train'].items():
            if (rule!='BASE' and stat['funded_trades']>=max(20,.25*baseline['funded_trades'])
                    and stat['decision_months']>=4 and stat['stop_pct']<=baseline['stop_pct']-3
                    and stat['total_return_pct']>baseline['total_return_pct']
                    and stat['max_drawdown_pct']>=baseline['max_drawdown_pct']):
                eligible.append(rule)
        winner=max(eligible,key=lambda r:result['periods']['train'][r]['total_return_pct']) if eligible else 'BASE'
        result['selected_rule']=winner; result['eligible_rules']=eligible
        result['selection_cutoff']='2025-10-01'
        report['results'].append(result)
        print('SELECTED',index,winner,flush=True)
    # Convert NumPy booleans in derived features before writing human-readable records.
    for r in enriched:
        r['trend50']=bool(r['trend50']); r['trend200']=bool(r['trend200'])
    dump_csv(OUT/'candidate-context.csv',enriched)
    dump_csv(OUT/'daily-equity.csv',all_equity)
    dump_csv(OUT/'funded-trades.csv',all_fills)
    report['hashes']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()
                      for p in [SOURCE/'strategy-trades.csv',PRICE,OUT/'benchmark.csv',OUT/'PROTOCOL.txt',Path(__file__)]}
    (OUT/'results.json').write_text(json.dumps(report,indent=2,allow_nan=False),encoding='utf-8')


if __name__=='__main__':
    main()
