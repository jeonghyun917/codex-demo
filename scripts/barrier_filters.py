"""Apply admission filters to a fixed, previously predicted barrier candidate tape."""
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path
import numpy as np
from barrier_research import metrics

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'output/barrier-probability-20261018'
OUT=ROOT/'output/barrier-filters-20261018'
RULES={('NONE' if s is None and e is None else
        f'S{str(s) if s is not None else "ANY"}_ER{str(e) if e is not None else "ANY"}'):(s,e)
       for s in (None,55,60,65,70) for e in (None,0,.5,1)}


def accepts(row, rule):
    return all(minimum is None or (row.get(key) is not None and row[key]>=minimum)
               for key,minimum in zip(('signal','er'),rule))


def mature_history(rows, decision):
    return [r for r in rows if r['date']<decision and r['maturity']<decision]


def summarize(rows, baseline_count):
    result=metrics(rows) if rows else dict(n=0,months=0,mean_net=None,stop_pct=None,
                                         tp_pct=None,profit_factor=None,mean_days=None)
    result['retention_pct']=100*len(rows)/baseline_count if baseline_count else None
    result['slot_mean_net']=sum(r['net'] for r in rows)/baseline_count if baseline_count else None
    return result


def choose_rule(history):
    baseline=summarize(history,len(history))
    eligible=[]
    if baseline['months']<6:
        return 'NONE',eligible
    for name,rule in RULES.items():
        if name=='NONE':
            continue
        stats=summarize([r for r in history if accepts(r,rule)],len(history))
        if (stats['n']>=50 and stats['months']>=6 and stats['retention_pct']>=25
                and stats['stop_pct']<=baseline['stop_pct']-3
                and stats['mean_net']>baseline['mean_net']):
            eligible.append(dict(rule=name,**stats))
    if not eligible:
        return 'NONE',eligible
    best=sorted(eligible,key=lambda x:(-x['slot_mean_net'],-x['mean_net'],x['rule']))[0]
    return best['rule'],eligible


def uncertainty(base, kept):
    months=sorted({r['date'][:7] for r in base})
    def aggregate(rows, month):
        chosen=[r for r in rows if r['date'].startswith(month)]
        return np.array([len(chosen),sum(r['net'] for r in chosen),
                         sum(r['reason'].startswith('SL') for r in chosen)],dtype=float)
    b=np.array([aggregate(base,m) for m in months])
    k=np.array([aggregate(kept,m) for m in months])
    rng=np.random.default_rng(731)
    picks=rng.integers(0,len(months),size=(2000,len(months)))
    bs,ks=b[picks].sum(axis=1),k[picks].sum(axis=1)
    valid=ks[:,0]>0
    slot=(ks[:,1]-bs[:,1])/bs[:,0]
    conditional=ks[valid,1]/ks[valid,0]-bs[valid,1]/bs[valid,0]
    stops=100*(ks[valid,2]/ks[valid,0]-bs[valid,2]/bs[valid,0])
    return dict(months=len(months),slot_difference_ci95=np.quantile(slot,[.025,.975]).tolist(),
                conditional_difference_ci95=np.quantile(conditional,[.025,.975]).tolist() if valid.any() else None,
                stop_difference_pp_ci95=np.quantile(stops,[.025,.975]).tolist() if valid.any() else None)


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    with (SOURCE/'strategy-trades.csv').open(newline='',encoding='utf-8') as f:
        rows=[r for r in csv.DictReader(f) if r['strategy']=='BARRIER_TOP20']
    for r in rows:
        for k in ('signal','er','net','gross','entry_price','exit_price','barrier_probability'):
            r[k]=float(r[k]) if r[k] else None
        r['days']=int(r['days']); r['ambiguous']=r['ambiguous']=='True'
    report=dict(status='EXPLORATORY_NOT_UNTOUCHED_HOLDOUT',rules=RULES,results=[])
    chosen_rows=[]
    for index in sorted({r['index_code'] for r in rows}):
        base=[r for r in rows if r['index_code']==index]
        table=[]
        for name,rule in RULES.items():
            kept=[r for r in base if accepts(r,rule)]
            table.append(dict(rule=name,**summarize(kept,len(base)),
                              years={str(y):summarize([r for r in kept if r['date'].startswith(str(y))],
                                      sum(r['date'].startswith(str(y)) for r in base)) for y in (2025,2026)}))
        test=[r for r in base if r['date']>='2025-10-01']
        selected,decisions=[],[]
        for decision in sorted({r['date'] for r in test}):
            history=mature_history(base,decision)
            name,eligible=choose_rule(history)
            current=[r for r in test if r['date']==decision]
            kept=[r for r in current if accepts(r,RULES[name])]
            selected.extend(kept)
            decisions.append(dict(date=decision,rule=name,history_n=len(history),
                                  history_max_maturity=max(r['maturity'] for r in history),
                                  eligible_rules=[r['rule'] for r in eligible],
                                  baseline=summarize(current,len(current)),
                                  chosen=summarize(kept,len(current))))
            chosen_rows.extend({**r,'filter_rule':name} for r in kept)
        fixed_test={name:summarize([r for r in test if accepts(r,rule)],len(test))
                    for name,rule in RULES.items()}
        result=dict(index=index,retrospective_table=table,forward=dict(
                    baseline=summarize(test,len(test)),chosen=summarize(selected,len(test)),
                    decisions=decisions,uncertainty=uncertainty(test,selected),
                    years={str(y):dict(baseline=summarize([r for r in test if r['date'].startswith(str(y))],
                                             sum(r['date'].startswith(str(y)) for r in test)),
                                      chosen=summarize([r for r in selected if r['date'].startswith(str(y))],
                                             sum(r['date'].startswith(str(y)) for r in test)))
                           for y in (2025,2026)}),fixed_test_descriptive=fixed_test)
        report['results'].append(result)
        print(index,'FORWARD',json.dumps({k:result['forward'][k] for k in ['baseline','chosen','uncertainty']}),flush=True)
        print('SELECTIONS',[(r['date'],r['rule'],r['chosen']['n']) for r in decisions],flush=True)
        for t in table:
            print(t['rule'],t['n'],t['mean_net'],t['stop_pct'],t['slot_mean_net'],flush=True)
    report['input_hashes']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()
                            for p in [SOURCE/'strategy-trades.csv',SOURCE/'results.json',OUT/'PROTOCOL.txt',Path(__file__)]}
    (OUT/'results.json').write_text(json.dumps(report,indent=2,allow_nan=False),encoding='utf-8')
    if chosen_rows:
        with (OUT/'forward-trades.csv').open('w',encoding='utf-8',newline='') as f:
            writer=csv.DictWriter(f,fieldnames=chosen_rows[0].keys()); writer.writeheader(); writer.writerows(chosen_rows)


if __name__=='__main__':
    main()
