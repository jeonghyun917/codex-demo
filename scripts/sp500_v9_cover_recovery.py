"""Original filing cover parser: structural facts only, no implicit economic-class sum."""
import argparse
from datetime import datetime,timezone
from decimal import Decimal
import json
from pathlib import Path
import time
import urllib.request
import urllib.error
from lxml import html
import pandas as pd
from acquire_sp500_v9_sources import OUT
from sp500_v9_shadow import ROOT,digest


def tag(e):return str(e.tag).lower().split('}')[-1].split(':')[-1]


def cover_facts(raw,cik):
    if isinstance(raw,str):raw=raw.encode('utf-8')
    tree=html.fromstring(raw);contexts={};units={};facts={}
    for e in tree.iter():
        if tag(e)=='context':contexts[e.get('id')]=e
        if tag(e)=='unit':units[e.get('id')]=[x.text.strip() for x in e.iter() if tag(x)=='measure' and x.text]
    for e in tree.iter():
        if tag(e)!='nonfraction' or e.get('name','').lower()!='dei:entitycommonstocksharesoutstanding':continue
        ctx=contexts.get(e.get('contextref'))
        if ctx is None:raise ValueError('Missing context')
        ident=[x.text.strip() for x in ctx.iter() if tag(x)=='identifier' and x.text]
        when=[x.text.strip() for x in ctx.iter() if tag(x)=='instant' and x.text]
        if len(ident)!=1 or ident[0].lstrip('0')!=str(cik).lstrip('0'):raise ValueError('Wrong issuer')
        if len(when)!=1:raise ValueError('Instant measurement required')
        unit=units.get(e.get('unitref'),[])
        if len(unit)!=1 or unit[0].split(':')[-1]!='shares':raise ValueError('Actual shares unit required')
        dims=sorted((x.get('dimension'),''.join(x.itertext()).strip()) for x in ctx.iter() if tag(x)=='explicitmember')
        if any(tag(x)=='typedmember' for x in ctx.iter()):raise ValueError('Typed dimension needs manual review')
        text=''.join(e.itertext()).strip().replace(',','').replace(' ','')
        value=Decimal(0) if 'fixed-zero' in e.get('format','') else Decimal(text)
        value*=Decimal(10)**int(e.get('scale','0'))
        if e.get('sign')=='-':value=-value
        if not value.is_finite() or value<0 or value!=int(value):raise ValueError('Invalid actual shares')
        key=(when[0],tuple(dims));row=dict(measurementDate=when[0],dimensions=dims,value=int(value),
            contextId=e.get('contextref'),decimals=e.get('decimals'),issuerCik=ident[0],unit='shares')
        if key in facts and facts[key]['value']!=row['value']:raise ValueError('Conflicting fact for same scope')
        facts[key]=row
    return list(facts.values())


def plan():
    targets=pd.read_csv(ROOT/'output/sp500-v9-full-preflight-20261101/remaining_share_targets.csv').set_index('symbol')
    mapping=json.loads((OUT/'issuer-map.json').read_text());result=[]
    for m in mapping:
        s=m['symbol'];p=OUT/('submissions-'+m['cik'].zfill(10)+'.json');data=json.loads(p.read_text())
        recent=data['filings']['recent'];first=targets.loc[s,'firstMissing']
        choices=[i for i,f in enumerate(recent['form']) if f in ['10-K','10-Q','20-F','40-F'] and recent['filingDate'][i]<first]
        if not choices:
            result.append(dict(symbol=s,cik=m['cik'],status='NO_PRIOR_COVER_IN_RECENT_METADATA',cutoff=first));continue
        i=max(choices,key=lambda i:recent['filingDate'][i]);acc=recent['accessionNumber'][i]
        result.append(dict(symbol=s,cik=m['cik'],status='PLANNED',cutoff=first,
            filedAt=recent['filingDate'][i],accession=acc,form=recent['form'][i],
            url=f"https://www.sec.gov/Archives/edgar/data/{int(m['cik'])}/{acc.replace('-','')}/{recent['primaryDocument'][i]}",
            path=str(OUT/'covers'/f"{s}-{acc}.html"),metadataPath=str(p),metadataSha256=digest(p)))
    (OUT/'covers').mkdir(exist_ok=True)
    with (OUT/'cover-plan.json').open('x') as f:json.dump(result,f,indent=2)
    print('Planned',sum(r['status']=='PLANNED' for r in result),'of',len(result))


def acquire(plan_path=None):
    receipts=[]
    for r in json.loads((plan_path or OUT/'cover-plan.json').read_text()):
        if r['status']!='PLANNED':continue
        p=Path(r['path'])
        if p.exists():continue
        receipt=dict(**r,attemptedAtUtc=datetime.now(timezone.utc).isoformat())
        try:
            request=urllib.request.Request(r['url'],headers={'User-Agent':'king-yurina-stock-research/0.1 contact@example.com','Accept-Encoding':'identity'})
            with urllib.request.urlopen(request,timeout=40) as response:data=response.read()
            p.write_bytes(data);receipt.update(status='ACQUIRED',sha256=digest(p),bytes=len(data))
        except urllib.error.HTTPError as e:receipt.update(status='HTTP_ERROR',code=e.code)
        except (urllib.error.URLError,TimeoutError) as e:receipt.update(status='FAILED',reason=str(e))
        receipts.append(receipt)
        with (OUT/'cover-receipts.jsonl').open('a') as f:f.write(json.dumps(receipt)+'\n')
        print(r['symbol'],receipt['status'],receipt.get('code',''),flush=True)
        if receipt.get('code') in (401,403,429):break
        time.sleep(.4)


def extract(plan_path=None,output_path=None):
    output=[]
    for r in json.loads((plan_path or OUT/'cover-plan.json').read_text()):
        if r['status']!='PLANNED' or not Path(r['path']).exists():output.append(dict(**r,admittedForModel=False));continue
        try:
            facts=cover_facts(Path(r['path']).read_text(encoding='utf-8'),r['cik'])
            assert all(f['measurementDate']<r['cutoff'] for f in facts)
            output.append(dict(**{**r,'status':'PARSED' if facts else 'NO_COVER_FACT'},facts=facts,
                sourceSha256=digest(Path(r['path'])),admittedForModel=False,
                remaining='Historical issuer continuity, complete economic class coverage and split/price basis'))
        except (ValueError,AssertionError) as e:output.append(dict(**{**r,'status':'REVIEW_REQUIRED'},reason=str(e),admittedForModel=False))
    with (output_path or OUT/'cover-observations-v2.json').open('x') as f:json.dump(output,f,indent=2)
    print(pd.Series([r['status'] for r in output]).value_counts().to_dict())


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['plan','acquire','extract'])
    parser.add_argument('--plan',type=Path);parser.add_argument('--output',type=Path);a=parser.parse_args()
    if a.mode=='plan':plan()
    elif a.mode=='acquire':acquire(a.plan)
    else:extract(a.plan,a.output)
