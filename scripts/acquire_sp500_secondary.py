"""Public rendered history pages, no login, API reverse engineering or access bypass."""
import json
import time
from acquire_sp500_free_followup import OUT,fetch,ROOT
from build_sp500_daily_dataset import read_csv


def main():
    folder=OUT/'secondary';folder.mkdir(exist_ok=True)
    manifest=OUT/'secondary_acquisition.json'
    if manifest.exists():raise ValueError('Existing acquisition')
    records=[]
    for row in read_csv(ROOT/'output/sp500-validation-20261027/remaining_data_queue.csv'):
        s=row['symbol']
        r=fetch(f'https://stockanalysis.com/stocks/{s.lower()}/history/',folder/f'{s}.html')
        r['symbol']=s;records.append(r)
        print(s,r.get('status'),r.get('bytes'),flush=True)
        if r.get('status') in (403,429) or r.get('blocked'):break
        time.sleep(.4)
    manifest.write_text(json.dumps(records,indent=2),encoding='utf-8')


if __name__=='__main__':main()
