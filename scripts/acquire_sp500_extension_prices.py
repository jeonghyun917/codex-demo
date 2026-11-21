"""Bounded sequential downloads for the acquired current and last known rosters."""
import io
import json
import time
import pandas as pd
import acquire_sp500_free_followup as client
from acquire_sp500_extension import OUT,chart_url


def main():
    client.OUT=OUT
    manifest=OUT/'price_acquisition.json'
    if manifest.exists():raise ValueError('Version already acquired')
    old=pd.read_csv(OUT.parent/'sp500-free-followup-20261027/dataset/inputs/sp500_membership_intervals.csv')
    old=old[(old.start_date<='2026-08-18')&(old.end_date>='2026-08-18')&(old.is_member==True)]
    current=pd.read_html(io.StringIO((OUT/'raw/wiki-current.html').read_text(encoding='utf-8')))[0]
    current.to_csv(OUT/'current_roster.csv',index=False)
    symbols={s.replace('.','-') for s in set(old.symbol)|set(current.Symbol)}
    symbols.update(['CAG','BE','P','ILMN','TWLO','VYLR','^GSPC'])
    records=[]
    for i,s in enumerate(sorted(symbols)):
        if s=='CAG':continue
        name='INDEX-SP500' if s=='^GSPC' else s
        r=client.fetch(chart_url(s),OUT/'raw'/f'yahoo-{name}.json')
        r['symbol']=s;records.append(r)
        manifest.with_suffix('.partial.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
        print(i,s,r.get('status'),r.get('bytes'),flush=True)
        if r.get('status') in (403,429) or r.get('blocked'):break
        time.sleep(.15)
    manifest.write_text(json.dumps(records,indent=2),encoding='utf-8')


if __name__=='__main__':main()
