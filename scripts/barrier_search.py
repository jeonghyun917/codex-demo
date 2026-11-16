"""Finite, risk-constrained filter search with chronological monthly rule selection."""
import csv
import hashlib
import itertools
import json
from collections import Counter,defaultdict
from pathlib import Path
from barrier_portfolio import portfolio,dump_csv

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'output/barrier-portfolio-20261018'
OUT=ROOT/'output/barrier-search-20261018'
PRICE=ROOT/'output/barrier-research-20261018/prices.csv'
BANDS={'ANY':(0,100),'0_10':(0,10),'5_20':(5,20),'10_30':(10,30),
       '20_40':(20,40),'20P':(20,100),'30P':(30,100),'40P':(40,100)}


def grid():
    rules={}
    for band,vol,trend,signal in itertools.product(BANDS,(None,2,3,4,5),
                                                  ('ANY','UP50','UP200','DOWN50'),(None,55,60)):
        name='BASE' if (band,vol,trend,signal)==('ANY',None,'ANY',None) else (
            f'D{band}_V{vol or "ANY"}_T{trend}_S{signal or "ANY"}')
        rules[name]=dict(band=band,vol=vol,trend=trend,signal=signal)
    return rules


def permits(row,rule):
    drawdown=100-row['high_ratio']; low,high=BANDS[rule['band']]
    return (low<=drawdown<=high and (rule['vol'] is None or row['vol20']<=rule['vol'])
            and (rule['signal'] is None or row['signal']>=rule['signal'])
            and {'ANY':True,'UP50':row['trend50'],'UP200':row['trend200'],
                 'DOWN50':not row['trend50']}[rule['trend']])


def mature_before(rows,decision):
    return [r for r in rows if r['date']<decision and r['maturity']<decision]


def eligible(stat,base):
    return (stat['funded_trades']>=max(20,.25*base['funded_trades']) and stat['decision_months']>=4
            and stat['stop_pct']<=base['stop_pct']
            and stat['max_drawdown_pct']>=base['max_drawdown_pct']
            and stat['total_return_pct']>base['total_return_pct']+1e-10)


def choose(stats,complexity):
    good=[name for name,stat in stats.items() if name!='BASE' and eligible(stat,stats['BASE'])]
    return min(good,key=lambda name:(-stats[name]['total_return_pct'],complexity[name],name)) if good else 'BASE'


def compact(account):
    return {k:v for k,v in account.items() if k not in ('equity','fills')}


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    prior=json.loads((SOURCE/'results.json').read_text())
    for file in (PRICE,SOURCE/'benchmark.csv'):
        assert hashlib.sha256(file.read_bytes()).hexdigest()==prior['hashes'][str(file.relative_to(ROOT))]
    with (SOURCE/'candidate-context.csv').open(newline='') as f:
        rows=list(csv.DictReader(f))
    for r in rows:
        for key in ('high_ratio','vol20','signal','entry_price','exit_price'):
            r[key]=float(r[key])
        for key in ('trend50','trend200'):
            r[key]=r[key]=='True'
    prices={}; calendars=defaultdict(list)
    with PRICE.open(newline='') as f:
        for r in csv.DictReader(f):
            prices[r['symbol'],r['date']]={'open':float(r['open']),'close':float(r['close'])}
    with (SOURCE/'benchmark.csv').open(newline='') as f:
        for r in csv.DictReader(f):
            calendars[r['index_code']].append(r['date'])
    rules=grid()
    complexity={name:sum([r['band']!='ANY',r['vol'] is not None,r['trend']!='ANY',r['signal'] is not None])
                for name,r in rules.items()}
    report=dict(status='RESEARCH_SEARCH_NOT_UNTOUCHED_VALIDATION',rules=rules,results=[])
    search_records=[]; daily=[]; fills=[]; admissions=[]
    for index in sorted(calendars):
        base=[r for r in rows if r['index_code']==index]
        quotas=Counter(r['date'] for r in base)
        def sessions_for(population):
            start=min(r['entry'] for r in population); end=max(r['maturity'] for r in population)
            return [d for d in calendars[index] if start<=d<=end]
        def evaluate(population,tag):
            sessions=sessions_for(population); memo={}; stats={}
            for name,rule in rules.items():
                mask=tuple(i for i,r in enumerate(population) if permits(r,rule))
                if mask not in memo:
                    memo[mask]=compact(portfolio([population[i] for i in mask],prices,sessions,quotas))
                stats[name]=memo[mask]
                search_records.append(dict(index=index,search_asof=tag,rule=name,**memo[mask]))
            return stats,len(memo)
        full_stats,unique=evaluate(base,'RETROSPECTIVE_FULL')
        winner=choose(full_stats,complexity)
        supported=[name for name,s in full_stats.items()
                   if s['funded_trades']>=max(20,.25*full_stats['BASE']['funded_trades']) and s['decision_months']>=4]
        return_leader=min(supported,key=lambda n:(-full_stats[n]['total_return_pct'],complexity[n],n))
        top=sorted([n for n,s in full_stats.items() if n!='BASE' and eligible(s,full_stats['BASE'])],
                   key=lambda n:(-full_stats[n]['total_return_pct'],complexity[n],n))[:10]
        print(index,'FULL BEST',winner,round(full_stats[winner]['total_return_pct'],3),
              'distinct candidate sets',unique,flush=True)
        result=dict(index=index,full_best=winner,full_return_leader=return_leader,
                    full_top10=[dict(rule=n,**full_stats[n]) for n in top],
                    full_distinct_sets=unique,monthly_selection=[],periods={})
        test=[r for r in base if r['date']>='2025-10-01']
        selected=[]
        for decision in sorted({r['date'] for r in test}):
            history=mature_before(base,decision)
            stats,unique=evaluate(history,decision)
            chosen=choose(stats,complexity)
            current=[r for r in test if r['date']==decision]
            accepted=[r for r in current if permits(r,rules[chosen])]
            selected.extend(accepted)
            admissions.extend(dict(**r,selected_rule=chosen) for r in accepted)
            result['monthly_selection'].append(dict(date=decision,chosen=chosen,history_rows=len(history),
                history_max_maturity=max(r['maturity'] for r in history),
                history_baseline=stats['BASE'],history_selected=stats[chosen],
                accepted=len(accepted),opportunities=len(current),distinct_sets=unique))
            print(index,decision,chosen,'accepted',len(accepted),flush=True)
        pure_deep={name:rule for name,rule in rules.items() if rule['band'] in ('20P','30P','40P')
                   and rule['vol'] is None and rule['trend']=='ANY' and rule['signal'] is None}
        for period,population,variants in [
            ('full',base,{'BASE':base,'RETROSPECTIVE_WINNER':[r for r in base if permits(r,rules[winner])],
                          **{n:[r for r in base if permits(r,rule)] for n,rule in pure_deep.items()}}),
            ('walk_forward',test,{'BASE':test,'MONTHLY_SELECTED':selected,
                                  **{n:[r for r in test if permits(r,rule)] for n,rule in pure_deep.items()}})]:
            summaries={}; sessions=sessions_for(population)
            for name,accepted in variants.items():
                account=portfolio(accepted,prices,sessions,quotas)
                summaries[name]=compact(account)
                daily.extend(dict(index=index,period=period,rule=name,**e) for e in account['equity'])
                fills.extend(dict(index=index,period=period,rule=name,**f) for f in account['fills'])
            result['periods'][period]=summaries
        print(index,'WALK FORWARD',json.dumps(result['periods']['walk_forward']['MONTHLY_SELECTED']),flush=True)
        report['results'].append(result)
    dump_csv(OUT/'search-grid.csv',search_records)
    dump_csv(OUT/'daily-equity.csv',daily)
    dump_csv(OUT/'funded-trades.csv',fills)
    dump_csv(OUT/'sequential-admissions.csv',admissions)
    report['hashes']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in
                      [SOURCE/'candidate-context.csv',SOURCE/'benchmark.csv',PRICE,OUT/'PROTOCOL.txt',Path(__file__)]}
    (OUT/'results.json').write_text(json.dumps(report,indent=2,allow_nan=False),encoding='utf-8')


if __name__=='__main__':
    main()
