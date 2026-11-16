"""Bounded public SEC foreign-issuer input acquisition; no DB or access workarounds."""
import hashlib
import json
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path('tmp/quant-extended-inputs-20261018')
ISSUERS={'AZN':901832,'GFS':1709048}

def main():
    ROOT.mkdir(parents=True,exist_ok=True)
    if (ROOT/'manifest.json').exists():raise ValueError('Refuse completed evidence overwrite')
    sources=[]
    for symbol,cik in ISSUERS.items():
        for kind,prefix in [('facts','api/xbrl/companyfacts/'),('submissions','submissions/')]:
            url=f'https://data.sec.gov/{prefix}CIK{cik:010d}.json'
            item=dict(symbol=symbol,cik=cik,kind=kind,url=url,requestedAt=datetime.now(timezone.utc).isoformat())
            try:
                request=urllib.request.Request(url,headers={'User-Agent':'QuantLocalResearch/1.0','Accept-Encoding':'identity'})
                with urllib.request.urlopen(request,timeout=25) as response:
                    body=response.read(12_000_001)
                    if len(body)>12_000_000:raise ValueError('Size budget exceeded')
                    item.update(status=response.status,finalUrl=response.url)
                payload=json.loads(body)
                if int(payload['cik'])!=cik:raise ValueError('Wrong CIK')
                path=ROOT/f'{symbol}-{kind}.json'
                with path.open('xb') as stream:stream.write(body)
                item.update(path=str(path),bytes=len(body),sha256=hashlib.sha256(body).hexdigest())
            except (OSError,ValueError,KeyError) as exc:item['error']=str(exc)
            item['retrievedAt']=datetime.now(timezone.utc).isoformat();sources.append(item)
    with (ROOT/'manifest.json').open('x',encoding='utf-8') as stream:
        json.dump(dict(classification='FILING_BOUNDED_RESEARCH_NOT_PIT',sources=sources,databaseWrites=0),stream,indent=2)
    print(json.dumps(sources,indent=2))

if __name__=='__main__':main()
