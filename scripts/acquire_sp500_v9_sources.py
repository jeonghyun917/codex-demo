"""Create-only free source acquisition; stop at access restrictions, never import DB."""
import argparse
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import time
import urllib.request
import urllib.error

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/sp500-v9-source-recovery-20261101'


def fetch(name,url):
    OUT.mkdir(exist_ok=True)
    path=OUT/name
    if path.exists():return json.loads(path.read_text())
    receipt=dict(url=url,path=str(path),attemptedAtUtc=datetime.now(timezone.utc).isoformat())
    request=urllib.request.Request(url,headers={'User-Agent':'king-yurina-stock-research/0.1 contact@example.com','Accept-Encoding':'identity'})
    try:
        with urllib.request.urlopen(request,timeout=40) as response:
            data=response.read();receipt['httpStatus']=response.status
        value=json.loads(data);path.write_bytes(data)
        receipt.update(sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),status='ACQUIRED')
    except urllib.error.HTTPError as e:
        receipt.update(httpStatus=e.code,status='ACCESS_RESTRICTED' if e.code in (401,403,429) else 'HTTP_ERROR')
        value=None
    except (urllib.error.URLError,TimeoutError,ValueError) as e:
        receipt.update(status='FAILED',reason=str(e));value=None
    with (OUT/'acquisition-receipts.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(receipt)+'\n')
    print(json.dumps(receipt),flush=True)
    if receipt.get('httpStatus') in (401,403,429):raise SystemExit('Access restricted; no retry or workaround')
    return value


def main():
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['tickers','facts','submissions']);args=parser.parse_args()
    if args.mode=='tickers':fetch('company-tickers.json','https://www.sec.gov/files/company_tickers.json');return
    mapping=json.loads((OUT/'issuer-map.json').read_text())
    for cik in sorted({r['cik'].zfill(10) for r in mapping if r.get('cik')}):
        if args.mode=='facts':value=fetch('facts-'+cik+'.json','https://data.sec.gov/api/xbrl/companyfacts/CIK'+cik+'.json')
        else:value=fetch('submissions-'+cik+'.json','https://data.sec.gov/submissions/CIK'+cik+'.json')
        if value is not None and str(value['cik']).lstrip('0')!=cik.lstrip('0'):raise ValueError('CIK source mismatch')
        time.sleep(.4)


if __name__=='__main__':main()
