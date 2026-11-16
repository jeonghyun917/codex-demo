"""Append cross-checked post-action segments without predecessor stitching."""
import io
import json
import shutil
import subprocess
import sys
from datetime import date,timedelta
from pathlib import Path
import pandas as pd
from bs4 import BeautifulSoup
from build_sp500_daily_dataset import read_csv,write_csv,digest,price_flags
from recover_sp500_daily_dataset import validate_chart

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/sp500-actions-20261028-retry'
BASE=ROOT/'output/sp500-extension-20261027/dataset'
DATA=OUT/'dataset'
START={'SKYD':'2026-10-14','VMRK':'2026-08-18','VYLR':'2026-10-01'}
KEYS=('open_price','high_price','low_price','close_price')


def approved_rows(incoming,secondary,start,calendar):
    selected={};audit=dict(unconfirmed=0,mismatched=0,invalid=0,volume_differences=0)
    for day,bar in sorted(incoming.items()):
        if day<start or day not in calendar:continue
        other=secondary.get(day)
        if other is None:audit['unconfirmed']+=1;continue
        if price_flags(bar) or price_flags(other):audit['invalid']+=1;continue
        # Secondary table rounds OHLC to cents. Never compare adjusted close.
        if any(abs(float(bar[k])-float(other[k]))>.00501 for k in KEYS):
            audit['mismatched']+=1;continue
        audit['volume_differences']+=int(int(bar['volume'])!=int(other['volume']))
        selected[day]=bar
    return selected,audit


def main():
    if DATA.exists():raise ValueError('Refusing dataset overwrite')
    records=json.loads((OUT/'acquisition.json').read_text())
    sources={r['name']:r for r in records}
    for r in records:
        if r.get('sha256'):assert digest(OUT/r['path'])==r['sha256']
    texts={n:BeautifulSoup((OUT/sources[n]['path']).read_text(encoding='utf-8'),'html.parser').get_text(' ',strip=True)
           for n in ('skyd-exdate','vmrk-merger','vylr-completion')}
    assert 'October 14, 2026' in texts['skyd-exdate'] and '$0.19368' in texts['skyd-exdate']
    assert '2.793' in texts['vmrk-merger'] and 'August 18, 2026' in texts['vmrk-merger']
    assert 'October 1, 2026' in texts['vylr-completion']
    calendar={r['trade_date'] for r in read_csv(BASE/'inputs/calendar.csv.gz')}
    prices=read_csv(BASE/'inputs/prices.csv.gz');fields=list(prices[0])
    original={(r['symbol'],r['trade_date']) for r in prices}
    decisions=[];lineage=[]
    for symbol,start in START.items():
        record=sources['yahoo-'+symbol]
        chart=json.loads((OUT/record['path']).read_bytes())['chart']['result'][0]
        incoming=validate_chart(symbol,chart)
        html=(OUT/sources['history-'+symbol]['path']).read_text(encoding='utf-8')
        title=BeautifulSoup(html,'html.parser').title.get_text()
        assert {'VMRK':'Vivmark','VYLR':'Vylor','SKYD':'Skydance'}[symbol].lower() in title.lower()
        table=pd.read_html(io.StringIO(html))[0]
        secondary={}
        for _,r in table.iterrows():
            day=pd.to_datetime(r['Date']).date().isoformat()
            assert day not in secondary
            secondary[day]=dict(zip(KEYS,[r[k] for k in ('Open','High','Low','Close')]),volume=r['Volume'])
        selected,audit=approved_rows(incoming,secondary,start,calendar)
        for day,bar in selected.items():
            assert (symbol,day) not in original
            prices.append(dict(symbol=symbol,trade_date=day,**bar,source='crosschecked-post-action-20261028',updated_at=''))
            lineage.append(dict(symbol=symbol,trade_date=day,yahoo_sha256=record['sha256'],secondary_sha256=sources['history-'+symbol]['sha256']))
        decisions.append(dict(symbol=symbol,event_boundary=start,first_added=min(selected) if selected else '',
            last_added=max(selected) if selected else '',added=len(selected),vendor_name=chart['meta'].get('longName'),
            secondary_title=title,status='POST_ACTION_ONLY_NOT_PREDECESSOR_OR_TOTAL_RETURN_RECONSTRUCTION',**audit))
    inputs=DATA/'inputs';inputs.mkdir(parents=True)
    for p in (BASE/'inputs').iterdir():
        if p.is_file():shutil.copyfile(p,inputs/p.name)
    write_csv(inputs/'prices.csv.gz',sorted(prices,key=lambda r:(r['symbol'],r['trade_date'])),fields)
    overrides=read_csv(inputs/'quality_overrides.csv')
    for r in overrides:
        if r['symbol'] in ('SKYD','VYLR'):
            r['end_date']=(date.fromisoformat(START[r['symbol']])-timedelta(days=1)).isoformat()
    write_csv(inputs/'quality_overrides.csv',overrides,list(overrides[0]))
    write_csv(OUT/'integration_decisions.csv',decisions,list(decisions[0]))
    write_csv(OUT/'added_price_lineage.csv.gz',lineage,list(lineage[0]))
    (OUT/'build_audit.json').write_text(json.dumps(dict(original_rows=len(original),added_rows=len(lineage),
        original_prices_sha256=digest(BASE/'inputs/prices.csv.gz'),price_basis='Unadjusted quote OHLC; no split factor or predecessor mapping applied',
        exclusions_unchanged=True,calendar_end=max(calendar)),indent=2),encoding='utf-8')
    del prices
    subprocess.run([sys.executable,'scripts/build_sp500_daily_dataset.py','--offline','--out',str(DATA)],check=True)
    print(json.dumps(decisions,indent=2),flush=True)


if __name__=='__main__':main()
