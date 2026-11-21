"""Append verified October 26-30 bars to the prior research dataset, never revise it."""
from collections import defaultdict
from datetime import datetime
import io
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
from zoneinfo import ZoneInfo
import pandas as pd
from acquire_sp500_paper_start import OUT
from build_sp500_daily_dataset import read_csv,write_csv,digest,price_flags,members_on
from recover_sp500_daily_dataset import validate_chart

BASE=OUT.parent/'sp500-actions-20261028-retry/dataset'
DATA=OUT/'dataset'
NEW_DAYS=['2026-10-26','2026-10-27','2026-10-28','2026-10-29','2026-10-30']
END=NEW_DAYS[-1]
KEYS=('open_price','high_price','low_price','close_price')


def approve_extension(old,incoming,required):
    overlap=[d for d in old if d in incoming and not price_flags(old[d])]
    if len(overlap)<min(10,len([d for d in old if d>='2026-10-01' and not price_flags(old[d])])) or not overlap:
        raise ValueError('INSUFFICIENT_OVERLAP')
    for d in overlap:
        if price_flags(incoming[d]) or any(abs(float(old[d][k])-float(incoming[d][k]))>.00501 for k in KEYS):
            raise ValueError('OVERLAP_PRICE_REVISION: '+d)
    for d in required:
        if d not in incoming or price_flags(incoming[d]):raise ValueError('MISSING_OR_INVALID_NEW_BAR: '+d)
    return {d:incoming[d] for d in required if d not in old}


def main():
    if DATA.exists():raise ValueError('Refusing overwrite')
    records=json.loads((OUT/'acquisition.json').read_text())
    retry=OUT/'retry.json'
    if retry.exists():records+=json.loads(retry.read_text())
    for r in records:
        if r.get('sha256'):assert digest(OUT/r['path'])==r['sha256']
    sources={r['name']:r for r in records}
    roster=set(pd.read_csv(OUT/'current_roster.csv').Symbol.str.replace('.','-',regex=False))
    previous=set(pd.read_csv(OUT.parent/'sp500-extension-20261027/current_roster.csv').Symbol.str.replace('.','-',regex=False))
    assert roster==previous,'ROSTER_CHANGE_REQUIRES_EXPLICIT_EVENT_RECONSTRUCTION'
    history=pd.read_html(io.StringIO((OUT/'raw/membership-history.html').read_text(encoding='utf-8')))[0]
    event_dates=pd.to_datetime(history[('Effective Date','Effective Date')])
    assert not ((event_dates>pd.Timestamp('2026-10-23'))&(event_dates<=pd.Timestamp(END))).any(),'MEMBERSHIP_EVENT_REQUIRES_REVIEW'
    intervals=read_csv(BASE/'inputs/sp500_membership_intervals.csv')
    aliases={r['symbol']:r['provider_symbol'] for r in read_csv(BASE/'inputs/aliases.csv')}
    assert {aliases.get(s,s) for s in members_on(intervals,'2026-10-26')}==roster
    calendar=read_csv(BASE/'inputs/calendar.csv.gz');calfields=list(calendar[0]);byday={r['trade_date']:r for r in calendar}
    fred=pd.read_csv(OUT/'raw/fred-index.csv');fred=fred[pd.to_numeric(fred.SP500,errors='coerce').notna()]
    fred_values=dict(zip(fred.observation_date,fred.SP500.astype(float)))
    index=json.loads((OUT/'raw/yahoo-INDEX.json').read_bytes())['chart']['result'][0]
    assert all(index['meta'].get(k)==v for k,v in dict(symbol='^GSPC',instrumentType='INDEX',currency='USD',exchangeTimezoneName='America/New_York',dataGranularity='1d').items())
    levels={datetime.fromtimestamp(t,ZoneInfo('America/New_York')).date().isoformat():round(float(c),2)
            for t,c in zip(index['timestamp'],index['indicators']['quote'][0]['close'])}
    assert len(levels)==len(index['timestamp'])
    shared=set(fred_values)&set(levels)
    assert len(shared)>=15 and all(abs(fred_values[d]-levels[d])<.011 for d in shared),'INDEX_SOURCE_CONFLICT'
    for d in shared:
        if d in byday:assert abs(float(byday[d]['benchmark_level'])-levels[d])<.011
    for d in NEW_DAYS:
        assert d in levels and math.isfinite(levels[d]) and levels[d]>0
        if d not in byday:byday[d]=dict(trade_date=d,benchmark_level=fred_values.get(d,levels[d]),
            source='FRED_SP500_PRICE_INDEX' if d in fred_values else 'YAHOO_GSPC_CLOSE_FRED_OVERLAP_CHECKED')
    prices=read_csv(BASE/'inputs/prices.csv.gz');fields=list(prices[0]);by=defaultdict(dict)
    for r in prices:by[r['symbol']][r['trade_date']]=r
    lineage=[];audit=[];events=[]
    for s in sorted(roster):
        record=sources['yahoo-'+s]
        assert record.get('sha256') and not record.get('blocked'),s
        chart=json.loads((OUT/record['path']).read_bytes())['chart']['result'][0]
        incoming=validate_chart(s,chart)
        for kind,items in chart.get('events',{}).items():
            for e in items.values():
                day=datetime.fromtimestamp(e['date'],ZoneInfo('America/New_York')).date().isoformat()
                if NEW_DAYS[0]<=day<=END:
                    events.append(dict(symbol=s,date=day,kind=kind,details=json.dumps(e)))
                    if kind!='dividends':raise ValueError('NEW_NON_DIVIDEND_ACTION_REQUIRES_REVIEW: '+s)
        added=approve_extension(by[s],incoming,NEW_DAYS)
        for d,b in added.items():
            prices.append(dict(symbol=s,trade_date=d,**b,source='yahoo-paper-start-20261031',updated_at=''))
            lineage.append(dict(symbol=s,trade_date=d,sha256=record['sha256'],source_path=record['path']))
        audit.append(dict(symbol=s,added=len(added),overlap=sum(d in incoming for d in by[s]),
            volume_revisions=sum(int(by[s][d]['volume'])!=int(incoming[d]['volume']) for d in by[s] if d in incoming)))
    # Build only after every security and membership/benchmark boundary passes.
    inputs=DATA/'inputs';inputs.mkdir(parents=True)
    for p in (BASE/'inputs').iterdir():
        if p.is_file():shutil.copyfile(p,inputs/p.name)
    write_csv(inputs/'prices.csv.gz',sorted(prices,key=lambda r:(r['symbol'],r['trade_date'])),fields)
    write_csv(inputs/'calendar.csv.gz',[byday[d] for d in sorted(byday)],calfields)
    for r in intervals:
        if r['end_date']=='2026-10-26' and r['is_member'].lower()=='true':
            r['end_date']=END;r['provenance_ids']+=';paper-start-20261031-current-and-history-match'
    write_csv(inputs/'sp500_membership_intervals.csv',intervals,list(intervals[0]))
    overrides=read_csv(inputs/'quality_overrides.csv')
    for r in overrides:
        if r['end_date']=='2026-10-26':r['end_date']=END
    write_csv(inputs/'quality_overrides.csv',overrides,list(overrides[0]))
    meta=json.loads((inputs/'manifest.json').read_text());meta['coverage']['lastDate']=END
    meta['paper_extension']=dict(official_pit_verified=False,roster_endpoint_match=True,no_community_events_in_gap=True,
        index_fallback_dates=[d for d in NEW_DAYS if d not in fred_values],
        index_overlap_dates=len(shared),limitation='Community historical membership; scheduled weekdays Oct26-30 checked against NYSE official holidays. No exhaustive corporate-action certification.')
    (inputs/'manifest.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
    write_csv(OUT/'added_price_lineage.csv',lineage,list(lineage[0]))
    write_csv(OUT/'integration_audit.csv',audit,list(audit[0]))
    write_csv(OUT/'new_corporate_events.csv',events,['symbol','date','kind','details'])
    (OUT/'integration_summary.json').write_text(json.dumps(dict(original_rows=len(prices)-len(lineage),added=len(lineage),
        symbols=len(roster),index_fallback_dates=meta['paper_extension']['index_fallback_dates'],
        parent_prices_sha256=digest(BASE/'inputs/prices.csv.gz'),strategy_changed=False),indent=2),encoding='utf-8')
    del prices,by
    subprocess.run([sys.executable,'scripts/build_sp500_daily_dataset.py','--offline','--out',str(DATA)],check=True)


if __name__=='__main__':main()
