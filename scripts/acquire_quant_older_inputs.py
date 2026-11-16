"""Six bounded free SEC requests, original bytes only, never DB writes."""
import hashlib,json,urllib.request
from datetime import datetime,timezone
from pathlib import Path

ROOT=Path('tmp/quant-older-inputs-20261018')
ISSUERS={'ANSS':1013462,'MDB':1441816,'ILMN':1110803}

def main():
    ROOT.mkdir(parents=True,exist_ok=True)
    if (ROOT/'manifest.json').exists():raise ValueError('Refuse overwrite')
    sources=[]
    for symbol,cik in ISSUERS.items():
        for kind,prefix in [('facts','api/xbrl/companyfacts/'),('submissions','submissions/')]:
            url=f'https://data.sec.gov/{prefix}CIK{cik:010d}.json'
            item=dict(symbol=symbol,cik=cik,kind=kind,url=url,requestedAt=datetime.now(timezone.utc).isoformat())
            try:
                with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'QuantLocalResearch/1.0','Accept-Encoding':'identity'}),timeout=25) as response:
                    body=response.read(12_000_001)
                    if len(body)>12_000_000:raise ValueError('Size budget exceeded')
                    item.update(status=response.status,finalUrl=response.url)
                if int(json.loads(body)['cik'])!=cik:raise ValueError('Wrong company identity')
                path=ROOT/f'{symbol}-{kind}.json'
                with path.open('xb') as stream:stream.write(body)
                item.update(path=str(path),bytes=len(body),sha256=hashlib.sha256(body).hexdigest())
            except (OSError,ValueError,KeyError) as exc:item['error']=str(exc)
            item['retrievedAt']=datetime.now(timezone.utc).isoformat();sources.append(item)
    with (ROOT/'manifest.json').open('x',encoding='utf-8') as stream:json.dump(dict(sources=sources,databaseWrites=0),stream,indent=2)
    print(json.dumps(sources,indent=2))

if __name__=='__main__':main()
