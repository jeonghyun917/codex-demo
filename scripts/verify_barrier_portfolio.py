"""Reconcile each saved daily cash/NAV path independently from the order ledger."""
import csv
import hashlib
import json
from collections import defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/barrier-portfolio-20261018'


def main():
    report=json.loads((OUT/'results.json').read_text())
    for name,expected in report['hashes'].items():
        assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==expected
    closes={}
    with (ROOT/'output/barrier-research-20261018/prices.csv').open(newline='') as f:
        for r in csv.DictReader(f):
            closes[r['symbol'],r['date']]=float(r['close'])
    paths=defaultdict(list); fills=defaultdict(list)
    for name,groups in [('daily-equity.csv',paths),('funded-trades.csv',fills)]:
        with (OUT/name).open(newline='') as f:
            for r in csv.DictReader(f):
                groups[r['index'],r['period'],r['rule']].append(r)
    count=0
    for result in report['results']:
        for period,strategies in result['periods'].items():
            for rule,summary in strategies.items():
                key=result['index'],period,rule
                ledger=fills[key]; daily=paths[key]
                purchases=defaultdict(float); sales=defaultdict(float)
                for r in ledger:
                    shares=float(r['shares']); budget=float(r['budget']); proceeds=float(r['proceeds'])
                    assert abs(shares*float(r['entry_price'])*1.001-budget)<1e-7
                    assert abs(shares*float(r['exit_price'])*.999-proceeds)<1e-7
                    assert abs(proceeds-budget-float(r['pnl']))<1e-7
                    purchases[r['entry']]+=budget; sales[r['exit']]+=proceeds
                cash=100000.; peak=cash; mdd=0.; cash_ratios=[]
                for r in daily:
                    day=r['date']; cash+=sales[day]-purchases[day]
                    holdings=sum(float(t['shares'])*closes[t['symbol'],day] for t in ledger
                                 if t['entry']<=day<t['exit'])
                    nav=cash+holdings
                    assert cash>=-1e-6
                    assert abs(cash-float(r['cash']))<1e-6
                    assert abs(holdings-float(r['holdings']))<1e-6
                    assert abs(nav-float(r['equity']))<1e-6
                    peak=max(peak,nav); mdd=min(mdd,100*(nav/peak-1))
                    cash_ratios.append(100*cash/nav); count+=1
                assert abs(cash-summary['ending_equity'])<1e-6
                assert abs(100*(cash/100000-1)-summary['total_return_pct'])<1e-8
                assert abs(mdd-summary['max_drawdown_pct'])<1e-8
                assert abs(sum(cash_ratios)/len(cash_ratios)-summary['average_cash_pct'])<1e-8
                assert len(ledger)==summary['funded_trades']
                if ledger:
                    assert abs(100*sum(t['reason'].startswith('SL') for t in ledger)/len(ledger)-summary['stop_pct'])<1e-8
    verification=dict(status='PASS',accounts=len(paths),daily_account_rows=count,
                      funded_trade_rows=sum(map(len,fills.values())),checks=['input_hashes',
                      'entry_exit_costs','independent_cash_ledger','close_marked_holdings',
                      'ending_equity','daily_drawdown','cash_fraction','trade_counts','stop_rates'])
    (OUT/'verification.json').write_text(json.dumps(verification,indent=2))
    print(json.dumps(verification))


if __name__=='__main__':
    main()
