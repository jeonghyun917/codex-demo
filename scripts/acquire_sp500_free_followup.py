"""Bounded public Yahoo/Stooq requests; immutable responses, no DB access or credentials."""
import csv
import hashlib
import json
import time
import urllib.request
import urllib.error
from datetime import datetime,timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/sp500-free-followup-20261027'


def fetch(url,path):
    record=dict(url=url,requested_at=datetime.now(timezone.utc).isoformat(),path=str(path.relative_to(OUT)))
    try:
        req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0 (compatible; local-research/1.0)'})
        with urllib.request.urlopen(req,timeout=12) as response:
            body=response.read(4000001)
            if len(body)>4000000:raise ValueError('Response exceeds 4MB budget')
            record.update(status=response.status,final_url=response.url)
        path.write_bytes(body)
        record.update(bytes=len(body),sha256=hashlib.sha256(body).hexdigest())
        if b'verify your browser' in body or b'/__verify' in body:
            record['blocked']='BROWSER_VERIFICATION_CHALLENGE'
    except (OSError,ValueError) as exc:
        record.update(error=str(exc),status=getattr(exc,'code',None))
    return record


def main():
    raw=OUT/'raw';raw.mkdir(parents=True,exist_ok=True)
    manifest_path=OUT/'acquisition.json'
    if manifest_path.exists():raise ValueError('Refusing to overwrite acquisition')
    with (ROOT/'output/sp500-validation-20261027/remaining_data_queue.csv').open() as f:
        symbols={r['symbol'] for r in csv.DictReader(f)}
    symbols.update(['GEN','GAP','FBIN','BBWI','DOC','SEDG','SMCI','DELL','PSKY'])
    partial=manifest_path.with_suffix('.partial.json')
    records=json.loads(partial.read_text()) if partial.exists() else []
    stopped=set()
    for r in records:
        p=OUT/r['path']
        if p.exists() and (b'verify your browser' in p.read_bytes() or b'/__verify' in p.read_bytes()):
            r['blocked']='BROWSER_VERIFICATION_CHALLENGE'
            stopped.add(r['provider'])
    done={(r['symbol'],r['provider']) for r in records}
    for s in sorted(symbols):
        for provider,url,extension in [
            ('yahoo',f'https://query1.finance.yahoo.com/v8/finance/chart/{s}?period1=1609459200&period2=1792195200&interval=1d&events=div%2Csplits&includeAdjustedClose=true','json'),
            ('stooq',f'https://stooq.com/q/d/l/?s={s.lower()}.us&i=d&d1=20210101&d2=20261016','csv')]:
            if provider in stopped:continue
            if (s,provider) in done:continue
            r=fetch(url,raw/f'{provider}-{s}.{extension}');r.update(provider=provider,symbol=s)
            records.append(r)
            # Stop a provider on rate limit/access denial; do not evade it with alternate hosts.
            if r.get('status') in (429,403) or r.get('blocked'):stopped.add(provider)
            print(provider,s,r.get('status'),r.get('bytes'),r.get('error',''),flush=True)
            time.sleep(.3)
        manifest_path.with_suffix('.partial.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
    manifest_path.write_text(json.dumps(dict(requested_symbols=sorted(symbols),stopped_providers=sorted(stopped),sources=records),indent=2),encoding='utf-8')


if __name__=='__main__':main()
