"""Two bounded public downloads for BK/BNY identity and provider history; no DB writes."""
import hashlib
import json
import argparse
import csv
import re
from html import unescape
from datetime import datetime, timezone
from pathlib import Path
import urllib.error
import urllib.request
from quant_complete_windows import parse_chart

ROOT = Path('tmp/quant-complete-window-new-prices-20261018')
SOURCES = [
    ('bny-common-ticker-notice', 'https://www.bny.com/corporate/global/en/about-us/newsroom/press-release/bny-announces-planned-change-of-stock-ticker-symbol-to-bny-130465.html'),
    ('yahoo-chart-BNY', 'https://query1.finance.yahoo.com/v8/finance/chart/BNY?period1=1577836800&period2=1792195200&interval=1d&events=div%2Csplits&includeAdjustedClose=true'),
]


def acquire():
    ROOT.mkdir(parents=True, exist_ok=True)
    if (ROOT/'manifest.json').exists():
        raise ValueError('Refuse overwrite of acquired sources')
    sources = []
    for key, url in SOURCES:
        record = dict(id=key, url=url, requestedAt=datetime.now(timezone.utc).isoformat())
        try:
            req = urllib.request.Request(url, headers={'User-Agent':'QuantLocalResearch/1.0'})
            with urllib.request.urlopen(req, timeout=20) as response:
                body = response.read(4_000_001)
                if len(body)>4_000_000:
                    raise ValueError('Response above budget')
                record.update(status=response.status, finalUrl=response.url, contentType=response.headers.get('Content-Type'))
            path = ROOT/(key+'.raw')
            with path.open('xb') as stream:
                stream.write(body)
            record.update(path=str(path),sha256=hashlib.sha256(body).hexdigest(),bytes=len(body))
        except (OSError, ValueError) as exc:
            record['error'] = str(exc)
        record['retrievedAt'] = datetime.now(timezone.utc).isoformat()
        sources.append(record)
    manifest=dict(sources=sources,databaseWrites=0,aliasAutomatic=False,
                  historicalPriceBasis='PROVIDER_REPORTED_NOT_PIT_OR_TOTAL_RETURN')
    with (ROOT/'manifest.json').open('x',encoding='utf-8') as stream:
        json.dump(manifest,stream,indent=2)
    print(json.dumps(manifest,indent=2))


def verify():
    manifest=json.loads((ROOT/'manifest.json').read_text(encoding='utf-8'))
    by_id={s['id']:s for s in manifest['sources']}
    for source in by_id.values():
        path=Path(source['path'])
        if not path.resolve().is_relative_to(ROOT.resolve()) or hashlib.sha256(path.read_bytes()).hexdigest()!=source['sha256']:
            raise ValueError('Source path/hash mismatch')
    notice=Path(by_id['bny-common-ticker-notice']['path']).read_text(encoding='utf-8')
    text=re.sub(r'\s+',' ',re.sub(r'<[^>]+>',' ',unescape(notice)))
    assertions=['The Bank of New York Mellon Corporation','"BK"','"BNY"','May 21, 2026',
                'capital structure, CUSIPs','rights of securityholders']
    if any(term not in text for term in assertions):
        raise ValueError('Common-security continuity notice assertions missing')
    chart=Path(by_id['yahoo-chart-BNY']['path']).read_bytes()
    prices=parse_chart(chart,'BNY')
    result=dict(status='VERIFIED_RETROSPECTIVE_ALIAS_CANDIDATE_NOT_CANONICAL_IMPORT',
        providerSymbol='BNY',historicalSymbol='BK',announcedChangeDate='2026-05-21',
        sourcePublicationDate='2026-05-11',observedPrices=len(prices),firstDate=min(prices),lastDate=max(prices),
        priorWindowPrices=sum('2023-04-28'<=d<='2026-04-28' for d in prices),
        databaseWrites=0,promotionAllowed=False,automaticAlias=False,
        caveat='Later official continuity notice, not evidence available to an earlier forecast; provider-reported prices, not official unadjusted closes.')
    output=ROOT/'mapped-BK-closes-candidate.csv'
    if not output.exists():
        with output.open('x',encoding='utf-8',newline='') as stream:
            writer=csv.writer(stream);writer.writerow(['historicalSymbol','providerSymbol','tradeDate','close','priceSourceId','identitySourceId'])
            writer.writerows(['BK','BNY',d,c,'yahoo-chart-BNY','bny-common-ticker-notice']
                for d,c in sorted(prices.items()) if '2023-04-28'<=d<='2026-04-28')
    result['candidateSha256']=hashlib.sha256(output.read_bytes()).hexdigest()
    saved=ROOT/'verification.json'
    if not saved.exists():
        saved.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--verify',action='store_true')
    args=parser.parse_args()
    verify() if args.verify else acquire()
