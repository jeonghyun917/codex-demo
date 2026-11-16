"""Bounded free SEC TEAM company facts + filing metadata, immutable evidence, no DB writes."""
import json
import hashlib
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path('tmp/quant-team-inputs-20261018')

def main():
    ROOT.mkdir(parents=True,exist_ok=True)
    if (ROOT/'manifest.json').exists():
        raise ValueError('Refusing to overwrite existing evidence')
    sources=[]
    for key,url in [('team-facts','https://data.sec.gov/api/xbrl/companyfacts/CIK0001650372.json'),
                    ('team-submissions','https://data.sec.gov/submissions/CIK0001650372.json')]:
        source=dict(id=key,url=url,requestedAt=datetime.now(timezone.utc).isoformat())
        try:
            request=urllib.request.Request(url,headers={'User-Agent':'QuantLocalResearch/1.0','Accept-Encoding':'identity'})
            with urllib.request.urlopen(request,timeout=25) as response:
                body=response.read(12_000_001)
                if len(body)>12_000_000:raise ValueError('Source larger than budget')
                source.update(status=response.status,finalUrl=response.url)
            payload=json.loads(body)
            if int(payload['cik'])!=1650372:raise ValueError('Wrong CIK')
            path=ROOT/(key+'.json')
            with path.open('xb') as stream:stream.write(body)
            source.update(path=str(path),bytes=len(body),sha256=hashlib.sha256(body).hexdigest())
        except (OSError,ValueError,KeyError) as exc:
            source['error']=str(exc)
        source['retrievedAt']=datetime.now(timezone.utc).isoformat()
        sources.append(source)
    result=dict(sources=sources,databaseWrites=0,classification='FILING_DATE_BOUNDED_RESEARCH_INPUT_NOT_HISTORICAL_PREDICTION')
    with (ROOT/'manifest.json').open('x',encoding='utf-8') as stream:json.dump(result,stream,indent=2)
    print(json.dumps(result,indent=2))

if __name__=='__main__':main()
