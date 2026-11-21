"""Plan all periodic cover observations across each missing interval; use issuer keys."""
import json
import pandas as pd
from acquire_sp500_v9_sources import OUT
from sp500_v9_shadow import ROOT,digest


def main():
    targets=pd.read_csv(ROOT/'output/sp500-v9-full-preflight-20261101/remaining_share_targets.csv').set_index('symbol')
    mapping=json.loads((OUT/'issuer-map.json').read_text())
    cached={r['url']:r['path'] for name in ['cover-plan.json','cover-plan-historical-sats.json']
            for r in json.loads((OUT/name).read_text()) if r['status']=='PLANNED'}
    rows=[]
    for m in mapping:
        s=m['symbol'];cik='1415404' if s=='SATS' else m['cik']
        path=OUT/('submissions-'+cik.zfill(10)+'.json');recent=json.loads(path.read_text())['filings']['recent']
        first=targets.loc[s,'firstMissing'];last=targets.loc[s,'lastMissing']
        possible=[i for i,f in enumerate(recent['form']) if f in ['10-K','10-Q','20-F','40-F'] and recent['filingDate'][i]<last]
        before=[i for i in possible if recent['filingDate'][i]<first]
        if not before:raise ValueError('Missing anchor filing '+s)
        anchor=max(before,key=lambda i:recent['filingDate'][i])
        selected=sorted({anchor}|{i for i in possible if recent['filingDate'][i]>=first},key=lambda i:recent['filingDate'][i])
        for i in selected:
            acc=recent['accessionNumber'][i];url=f"https://www.sec.gov/Archives/edgar/data/{int(cik)}/{acc.replace('-','')}/{recent['primaryDocument'][i]}"
            p=cached.get(url,str(OUT/'covers'/f'{cik}-{acc}.html'))
            rows.append(dict(symbol=s,cik=cik,status='PLANNED',cutoff=last,filedAt=recent['filingDate'][i],
                accession=acc,form=recent['form'][i],url=url,path=p,metadataPath=str(path),metadataSha256=digest(path),
                identityPolicy='Historical SATS=EchoStar1415404; all other current issuer mappings retain continuity-review flag'))
    with (OUT/'cover-history-plan.json').open('x') as f:json.dump(rows,f,indent=2)
    print('symbol-filing rows',len(rows),'unique original URLs',len({r['url'] for r in rows}))


if __name__=='__main__':main()
