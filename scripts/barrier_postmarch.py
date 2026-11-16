"""Fixed-rule partial post-March validation with explicit missing-month accounting."""
import bisect
import csv
import hashlib
import json
import sys
from collections import Counter,defaultdict
from datetime import date
from pathlib import Path
import numpy as np
from barrier_research import query,simulate
from barrier_probability import load_data,causal_features,valid_window,split_history,fit_predict,ranked_entries
from barrier_portfolio import portfolio,dump_csv

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/barrier-postmarch-20261018'
PRICE=ROOT/'output/barrier-research-20261018/prices.csv'
BENCH=ROOT/'output/barrier-portfolio-20261018/benchmark.csv'
INDEX='SP500_FREE_20261018'
MONTHS=[f'2026-{m:02}' for m in range(4,11)]
DATES_SQL="""SELECT DATE_FORMAT(signal_date,'%Y-%m') month,MAX(signal_date) date
FROM stock_signal_snapshot WHERE signal_date BETWEEN '2026-04-01' AND '2026-10-18'
AND snapshot_mode='LIVE' GROUP BY YEAR(signal_date),MONTH(signal_date)"""
SIGNALS_SQL="""SELECT m.snapshot_date date,m.symbol,m.source membership_source,
s.integrated_score `signal`,s.momentum_score momentum,s.quality_score quality,s.risk_score risk,
s.signal_version,s.calculated_at signal_calculated_at,p.expected_return_pct er,
p.calibrated_upside_probability_pct prob,p.calculated_at prediction_calculated_at,p.model_version
FROM stock_index_membership_snapshot m
LEFT JOIN stock_signal_snapshot s ON s.symbol=m.symbol AND s.signal_date=m.snapshot_date AND s.snapshot_mode='LIVE'
LEFT JOIN stock_expected_return_snapshot p ON p.index_code=m.index_code AND p.symbol=m.symbol
AND p.signal_date=m.snapshot_date AND p.horizon_days=20
AND p.model_version='EXPECTED_RETURN_V3_CORRECTED_20261017'
WHERE m.index_code='SP500_FREE_20261018' AND m.is_member=1
AND m.snapshot_date IN (SELECT MAX(signal_date) FROM stock_signal_snapshot
WHERE signal_date BETWEEN '2026-04-01' AND '2026-10-18' AND snapshot_mode='LIVE'
GROUP BY YEAR(signal_date),MONTH(signal_date)) ORDER BY m.snapshot_date,m.symbol"""


def month_status(month,decision,members,predictions):
    if decision is None:
        return 'NO_SAVED_SIGNAL'
    if month>='2026-10':
        return 'INCOMPLETE_MONTH_OR_IMMATURE'
    if not members or not predictions:
        return 'MISSING_RESEARCH_CONTEXT'
    return 'AVAILABLE_FOR_PRICE_CHECK'


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    if '--fetch' in sys.argv:
        dump_csv(OUT/'monthly-inventory.csv',query(DATES_SQL))
        dump_csv(OUT/'signals.csv',query(SIGNALS_SQL))
        (OUT/'queries.sql').write_text(DATES_SQL+';\n'+SIGNALS_SQL+';\n')
        print('Saved read-only monthly inventory and signal/prediction join'); return
    with (OUT/'monthly-inventory.csv').open(newline='') as f:
        inventory={r['month']:r['date'] for r in csv.DictReader(f)}
    with (OUT/'signals.csv').open(newline='') as f:
        signals=list(csv.DictReader(f))
    historical,_,_=load_data()
    history=[r for r in historical if r['index_code']==INDEX]
    candles=defaultdict(list); prices={}
    with PRICE.open(newline='') as f:
        for r in csv.DictReader(f):
            for k in ('open','high','low','close'):
                r[k]=float(r[k])
            candles[r['symbol']].append(r); prices[r['symbol'],r['date']]=r
    dates={s:[b['date'] for b in bars] for s,bars in candles.items()}
    with BENCH.open(newline='') as f:
        benchmarks=[r for r in csv.DictReader(f) if r['index_code']==INDEX]
    coverage=[]; predictions=[]; models=[]; keys=set()
    for month in MONTHS:
        decision=inventory.get(month)
        population=[r for r in signals if r['date']==decision]
        existing=sum(r['signal']!='NULL' and r['er']!='NULL' and r['prob']!='NULL' for r in population)
        status=month_status(month,decision,len(population),existing)
        cover=dict(month=month,decision=decision,membership_rows=len(population),
                   signal_prediction_rows=existing,status=status,exclusions={})
        coverage.append(cover)
        if status!='AVAILABLE_FOR_PRICE_CHECK':
            continue
        current=[]; excluded=Counter()
        for raw in population:
            key=(raw['date'],raw['symbol']); assert key not in keys; keys.add(key)
            if any(raw[k]=='NULL' for k in ('signal','er','prob')):
                excluded['missing_signal_prediction']+=1; continue
            bars=candles.get(raw['symbol'],[])
            features=causal_features(bars,decision)
            if features is None:
                excluded['missing_causal_prices']+=1; continue
            pos=bisect.bisect_right(dates[raw['symbol']],decision); future=bars[pos:pos+20]
            if (len(future)!=20 or not valid_window(future) or
                    (date.fromisoformat(future[0]['date'])-date.fromisoformat(decision)).days>7):
                excluded['missing_or_invalid_20session_window']+=1; continue
            trade=simulate(future,20)
            assert trade is not None
            current.append({**raw,**{k:None if raw[k]=='NULL' else float(raw[k])
                                   for k in ('signal','er','prob','momentum','quality','risk')},
                            **features,**trade,'maturity':future[-1]['date'],
                            'label':int(trade['reason']=='TP'),'index_code':INDEX})
        cover['exclusions']=dict(excluded); cover['usable_rows']=len(current)
        fit,calibration=split_history(history,decision)
        counts=Counter(r['label'] for r in calibration)
        if (len(fit)<400 or len({r['date'] for r in fit})<8 or
                min(counts.get(0,0),counts.get(1,0))<15 or not current):
            cover['status']='INSUFFICIENT_MODEL_OR_PRICE_DATA'; continue
        assert max(r['maturity'] for r in fit)<min(r['date'] for r in calibration)
        assert max(r['maturity'] for r in calibration)<decision
        past=[b for b in benchmarks if b['date']<=decision][-50:]
        assert len(past)==50 and all(b['level']!='NULL' for b in past)
        assert (date.fromisoformat(decision)-date.fromisoformat(past[-1]['date'])).days<=7
        level=float(past[-1]['level']); ma=float(np.mean([float(b['level']) for b in past]))
        trend=level>ma
        probabilities,parameters=fit_predict(fit,calibration,current)
        predictions.extend(dict(**r,barrier_probability=float(p),trend50=trend)
                           for r,p in zip(current,probabilities))
        history.extend(current)
        cover.update(status='PARTIAL_PERIOD_EVALUATED',index_level=level,sma50=ma,trend50=trend)
        models.append(dict(index=INDEX,decision=decision,fit_rows=len(fit),
                           fit_max_maturity=max(r['maturity'] for r in fit),
                           calibration_start=min(r['date'] for r in calibration),
                           calibration_max_maturity=max(r['maturity'] for r in calibration),parameters=parameters))
        print('SCORED',decision,len(current),'index',level,'MA50',ma,'trend',trend,flush=True)
    assert predictions, 'No eligible post-March monthly cohort'
    base=ranked_entries(predictions,'barrier_probability')
    quotas=Counter(r['date'] for r in base)
    start=min(r['entry'] for r in base); end=max(r['maturity'] for r in base)
    sessions=[b['date'] for b in benchmarks if start<=b['date']<=end]
    summaries={}; daily=[]; fills=[]
    for name,selected in [('BASE',base),('TREND50',[r for r in base if r['trend50']])]:
        account=portfolio(selected,prices,sessions,quotas)
        daily.extend(dict(index=INDEX,period='partial_postmarch',rule=name,**r) for r in account.pop('equity'))
        fills.extend(dict(index=INDEX,period='partial_postmarch',rule=name,**r) for r in account.pop('fills'))
        summaries[name]=account
        print(name,json.dumps(account),flush=True)
    report=dict(status='PARTIAL_PERIOD_NOT_FULL_APRIL_ONWARD_VALIDATION',coverage=coverage,
                results=[dict(index=INDEX,periods={'partial_postmarch':summaries})],
                hashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in
                        [PRICE,BENCH,OUT/'signals.csv',OUT/'monthly-inventory.csv',OUT/'PROTOCOL.txt',Path(__file__)]})
    (OUT/'results.json').write_text(json.dumps(report,indent=2,allow_nan=False),encoding='utf-8')
    (OUT/'models.json').write_text(json.dumps(models,indent=2,allow_nan=False),encoding='utf-8')
    dump_csv(OUT/'predictions.csv',predictions); dump_csv(OUT/'candidate-trades.csv',base)
    dump_csv(OUT/'daily-equity.csv',daily); dump_csv(OUT/'funded-trades.csv',fills)


if __name__=='__main__':
    main()
