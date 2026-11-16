"""Bounded create-only acquisition for the first 2026-10-30 paper decision."""
import io
import json
import time
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import quote
import pandas as pd
import acquire_sp500_free_followup as client

OUT=Path(__file__).resolve().parents[1]/'output/sp500-paper-start-20261031'


def chart_url(symbol):
    stamp=lambda d:int(datetime.fromisoformat(d).replace(tzinfo=timezone.utc).timestamp())
    return f'https://query1.finance.yahoo.com/v8/finance/chart/{quote(symbol)}?period1={stamp("2026-10-01")}&period2={stamp("2026-10-31")}&interval=1d&events=div%2Csplits&includeAdjustedClose=true'


def main():
    client.OUT=OUT;(OUT/'raw').mkdir(parents=True,exist_ok=True)
    manifest=OUT/'acquisition.json';partial=OUT/'acquisition.partial.json'
    if manifest.exists():raise ValueError('Refusing overwrite')
    records=json.loads(partial.read_text()) if partial.exists() else []
    done={r['name'] for r in records}
    def fetch(name,url,ext):
        if name in done:return
        path=OUT/'raw'/f'{name}.{ext}'
        if path.exists():raise ValueError('Unmanifested existing raw response')
        r=client.fetch(url,path);r['name']=name;records.append(r)
        partial.write_text(json.dumps(records,indent=2),encoding='utf-8')
        print(name,r.get('status'),r.get('bytes'),flush=True)
    for name,url,ext in [
        ('roster','https://en.wikipedia.org/wiki/List_of_S%26P_500_companies','html'),
        ('membership-history','https://en.wikipedia.org/wiki/Historical_components_of_the_S%26P_500','html'),
        ('nyse-calendar','https://www.nyse.com/trade/hours-calendars','html'),
        ('fred-index','https://fred.stlouisfed.org/graph/fredgraph.csv?id=SP500&cosd=2026-10-01&coed=2026-10-30','csv')]:fetch(name,url,ext)
    roster=pd.read_html(io.StringIO((OUT/'raw/roster.html').read_text(encoding='utf-8')))[0]
    symbols=sorted(set(roster.Symbol.str.replace('.','-',regex=False)))
    assert len(symbols)==len(roster) and 490<=len(symbols)<=510
    roster.to_csv(OUT/'current_roster.csv',index=False)
    for symbol in ['^GSPC']+symbols:
        if any(r.get('status') in (403,429) or r.get('blocked') for r in records if r['name'].startswith('yahoo-')):break
        fetch('yahoo-'+('INDEX' if symbol=='^GSPC' else symbol),chart_url(symbol),'json')
        time.sleep(.12)
    manifest.write_text(json.dumps(records,indent=2),encoding='utf-8')


if __name__=='__main__':main()
