"""Independent reconstruction of daily cash/NAV, trade timing and stored probabilities."""
import csv
import gzip
import json
import math
from collections import defaultdict
from sp500_validation_data import DATA, OUT, BASE
from build_sp500_daily_dataset import read_csv, digest


def close(a,b):
    assert math.isclose(a,b,rel_tol=1e-9,abs_tol=1e-5),(a,b)


def main():
    result=OUT/'research'
    aliases={r['symbol']:r['provider_symbol'] for r in read_csv(DATA/'inputs/aliases.csv')}
    prices={}; originals={}
    with gzip.open(DATA/'inputs/prices.csv.gz','rt',encoding='utf-8',newline='') as f:
        for r in csv.DictReader(f):
            originals[r['symbol'],r['trade_date']]=r
            s=aliases.get(r['symbol'],r['symbol'])
            prices[s,r['trade_date']]={k:float(r[k+'_price']) for k in ('open','high','low','close')}
    count=0
    with gzip.open(BASE/'inputs/prices.csv.gz','rt',encoding='utf-8',newline='') as f:
        for r in csv.DictReader(f):
            assert originals[r['symbol'],r['trade_date']]==r
            count+=1
    alias_count=0
    lineage_path=OUT/'alias_lineage.csv.gz'
    if not lineage_path.exists():
        lineage_path=OUT/'added_price_lineage.csv.gz'
    for r in read_csv(lineage_path):
        original=originals[r['symbol'],r['trade_date']]
        provider=originals[r['provider_symbol'],r['trade_date']]
        for k in ('open_price','high_price','low_price','close_price','volume'):
            close(float(r.get(k,provider[k])),float(original[k]))
        alias_count+=1
    del originals
    sessions=[r['trade_date'] for r in read_csv(DATA/'inputs/calendar.csv.gz')]
    idx={d:i for i,d in enumerate(sessions)}
    group=lambda r:(r['period'],r['rule'],r['fee'])
    equities=defaultdict(list); events=defaultdict(lambda:defaultdict(list)); fills=defaultdict(list)
    for r in read_csv(result/'daily_equity.csv.gz'):
        equities[group(r)].append(r)
    for r in read_csv(result/'events.csv'):
        events[group(r)][r['date']].append(r)
    for r in read_csv(result/'fills.csv'):
        fills[group(r)].append(r)
    accounts=[]
    for key,marks in equities.items():
        cash=100000.; positions={}; peak=100000.; mdd=0.; previous_nav=100000.
        for mark in marks:
            d=mark['date']; buys=0
            for e in events[key][d]:
                s=e['symbol']; quantity=int(e['shares']); px=float(e['price']); fee=float(e['fee_amount'])
                close(fee,quantity*px*float(key[2]))
                if e['side']=='BUY':
                    assert s not in positions and quantity>0
                    close(px,prices[s,d]['open'])
                    assert quantity*px+fee <= previous_nav*.1+1e-5
                    delta=-(quantity*px+fee); positions[s]=quantity; buys+=1
                else:
                    assert positions.pop(s)==quantity
                    delta=quantity*px-fee
                close(delta,float(e['cash_delta']))
                cash+=delta
                assert cash>=-1e-5 and len(positions)<=10
            assert buys<=2
            holdings=sum(n*prices[s,d]['close'] for s,n in positions.items())
            nav=cash+holdings
            close(cash,float(mark['cash']));close(holdings,float(mark['holdings']));close(nav,float(mark['equity']))
            assert len(positions)==int(mark['positions'])
            peak=max(peak,nav);mdd=min(mdd,100*(nav/peak-1));previous_nav=nav
        for t in fills[key]:
            assert idx[t['entry']]==idx[t['decision']]+1
            age=idx[t['exit']]-idx[t['entry']]+1
            assert age==int(t['age']) and 1<=age<=20
            entry=float(t['entry_price']); target=entry*1.15; stop=entry*.95
            for offset in range(age):
                day=sessions[idx[t['entry']]+offset]; b=prices[t['symbol'],day]
                expected=None
                if b['open']<=stop:expected=(b['open'],'SL_GAP')
                elif b['open']>=target:expected=(target,'TP')
                elif b['low']<=stop:expected=(stop,'SL')
                elif b['high']>=target:expected=(target,'TP')
                elif offset==19:expected=(b['close'],'TIME')
                if offset<age-1:assert expected is None
                else:
                    assert expected and expected[1]==t['reason']
                    close(expected[0],float(t['exit_price']))
            close(float(t['pnl']),float(t['proceeds'])-float(t['budget']))
        accounts.append(dict(period=key[0],rule=key[1],fee=float(key[2]),nav=previous_nav,mdd=mdd,days=len(marks)))
    report=json.loads((result/'results.json').read_text())
    for a in accounts:
        match=next(r for r in report['comparisons'] if all(r[k]==a[k] for k in ('period','rule','fee')))
        close(a['mdd'],match['max_drawdown_pct'])
        close(100*(a['nav']/100000-1),match['prefix_return_pct'])
    models={r['month']:r for r in json.loads((result/'models.json').read_text()) if r['status']=='FIT'}
    probabilities=0
    for m in models.values():
        assert m['fit_max_maturity']<m['cal_start']<m['cutoff']
        assert m['cal_max_maturity']<m['cutoff']
    with gzip.open(result/'predictions.csv.gz','rt',encoding='utf-8',newline='') as f:
        for r in csv.DictReader(f):
            m=models[r['date'][:7]]
            logit=m['intercept']+sum((float(r[k])-mean)/scale*coef for k,mean,scale,coef in zip(report['features'],m['mean'],m['scale'],m['coef']))
            z=logit*m['cal_coef']+m['cal_intercept']
            close(1/(1+math.exp(-z)),float(r['probability']))
            probabilities+=1
    verified=dict(status='PASS',accounts=len(accounts),original_price_rows_unchanged=count,
                  alias_history_rows_verified=alias_count,probabilities_recalculated=probabilities,
                  filled_trades_verified=sum(map(len,fills.values())),
                  limitations='Accounting and causal split checks, not official membership or total-return certification.')
    (result/'verification.json').write_text(json.dumps(verified,indent=2),encoding='utf-8')
    flags={(aliases.get(r['symbol'],r['symbol']),r['trade_date']):r['quality_flags']
           for r in read_csv(DATA/'price_history.csv.gz') if r['quality_flags']}
    flagged=[dict(r,quality_flags=flags[r['symbol'],r['date']]) for r in read_csv(result/'events.csv')
             if (r['symbol'],r['date']) in flags]
    from build_sp500_daily_dataset import write_csv
    write_csv(result/'flagged_execution_events.csv',flagged,
              list(flagged[0]) if flagged else ['symbol','date','quality_flags'])
    print('Flagged execution events:',len(flagged))
    print(json.dumps(verified,indent=2))


if __name__=='__main__':
    main()
