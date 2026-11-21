"""Append-only public acquisition for CAG and post-August membership evidence."""
import json
import time
import urllib.parse
from pathlib import Path
from datetime import datetime,timezone
import acquire_sp500_free_followup as client

OUT=Path(__file__).resolve().parents[1]/'output/sp500-extension-20261027'
SOURCES=[
 ('wiki-current','https://en.wikipedia.org/wiki/List_of_S%26P_500_companies','html'),
 ('wiki-history','https://en.wikipedia.org/wiki/Historical_components_of_the_S%26P_500','html'),
 ('github-contents','https://api.github.com/repos/fja05680/sp500/contents','json'),
 ('sp-september','https://press.spglobal.com/2026-09-04-Bloom-Energy%2C-Illumina%2C-and-Everpure-Set-to-Join-S-P-500-Others-to-Join-S-P-100%2C-S-P-MidCap-400%2C-and-S-P-SmallCap-600','html'),
 ('sp-october','https://press.spglobal.com/2026-10-01-Vylor-Added-to-the-S-P-500-Twilio-Set-to-Join-S-P-500-Others-to-Join-S-P-MidCap-400-and-S-P-SmallCap-600','html'),
 ('fred-sp500','https://fred.stlouisfed.org/graph/?g=1&cosd=2026-05-01&coed=2026-10-26&id=SP500','html'),
]


def chart_url(symbol,start='2026-05-01'):
    stamp=lambda day:int(datetime.fromisoformat(day).replace(tzinfo=timezone.utc).timestamp())
    return f'https://query1.finance.yahoo.com/v8/finance/chart/{urllib.parse.quote(symbol)}?period1={stamp(start)}&period2={stamp("2026-10-27")}&interval=1d&events=div%2Csplits&includeAdjustedClose=true'


def main():
    client.OUT=OUT
    raw=OUT/'raw';raw.mkdir(parents=True,exist_ok=True)
    manifest=OUT/'acquisition.json'
    if manifest.exists():raise ValueError('Acquisition already exists')
    records=[]
    requests=[('yahoo-CAG',chart_url('CAG','2021-06-07'),'json')]+SOURCES
    for name,url,ext in requests:
        r=client.fetch(url,raw/f'{name}.{ext}');r['name']=name;records.append(r)
        print(name,r.get('status'),r.get('bytes'),r.get('error',''),flush=True)
        manifest.with_suffix('.partial.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
        time.sleep(.3)
    manifest.write_text(json.dumps(records,indent=2),encoding='utf-8')


if __name__=='__main__':main()
