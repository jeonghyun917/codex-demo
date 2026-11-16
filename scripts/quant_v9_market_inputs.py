"""Actual pre-decision common-share observations for file-only V9 research."""
import csv,hashlib,json
from datetime import date
from decimal import Decimal
from pathlib import Path

OUT=Path('tmp/quant-v9-market-inputs-20261030')
CIKS={'ANSS':1013462,'ILMN':1110803,'GFS':1709048,'MDB':1441816,'TEAM':1650372,'AZN':901832}
# Deliberately exclude stock issued, treasury shares and all EPS weighted-average denominators.
TAGS=[('dei','EntityCommonStockSharesOutstanding',3),('us-gaap','CommonStockSharesOutstanding',2),
      ('ifrs-full','NumberOfSharesOutstanding',1)]

def count(value):
    number=Decimal(str(value))
    if not number.is_finite() or number<=0 or number!=number.to_integral_value():
        raise ValueError('Invalid actual common-share count')
    return int(number)

def xbrl_share_rows(payload,symbol,*,filed_from=None,filed_before=None):
    if symbol not in CIKS or int(payload['cik'])!=CIKS[symbol]:raise ValueError('Wrong issuer identity')
    observations=[]
    for namespace,tag,priority in TAGS:
        seen={}
        for raw in payload.get('facts',{}).get(namespace,{}).get(tag,{}).get('units',{}).get('shares',[]):
            if raw.get('start') or raw.get('form') not in ('10-K','10-K/A','10-Q','10-Q/A','20-F','20-F/A','6-K','6-K/A'):continue
            measurement=raw['end'];filed=raw['filed'];date.fromisoformat(measurement);date.fromisoformat(filed)
            if (filed_from and filed<filed_from) or (filed_before and filed>=filed_before):continue
            if measurement>filed:raise ValueError('Shares measured after filing')
            value=count(raw['val']);key=(measurement,filed,raw['accn'])
            if key in seen and seen[key]!=value:raise ValueError('Conflicting same-tag/accession actual count')
            if key in seen:continue
            seen[key]=value
            observations.append(dict(symbol=symbol,cik=str(CIKS[symbol]),measurementDate=measurement,filedDate=filed,
                accessionNumber=raw['accn'],form=raw['form'],totalCommonShares=value,unit='shares',
                sourceClassification='SEC_XBRL_ACTUAL_REPORTED_COMMON',sourcePriority=priority,
                rawOriginalSourceAcquired=True,sourceUrl=f'https://data.sec.gov/api/xbrl/companyfacts/CIK{CIKS[symbol]:010d}.json',
                taxonomy=namespace,tag=tag,basisNotes='Actual common shares as reported; reported precision retained, not free float or EPS denominator.'))
    return observations

def select_share(observations,symbol,decision):
    date.fromisoformat(decision)
    eligible=[r for r in observations if r['symbol']==symbol and r['filedDate']<decision and r['measurementDate']<decision]
    if not eligible:raise ValueError(f'No eligible actual shares: {symbol} {decision}')
    for r in eligible:
        if r.get('unit')!='shares':raise ValueError('Wrong share units')
        count(r['totalCommonShares'])
        if r.get('classAShares') is not None and r.get('classBShares') is not None:
            if int(r['classAShares'])+int(r['classBShares'])!=r['totalCommonShares']:raise ValueError('Share-class total mismatch')
    key=lambda r:(r['measurementDate'],r['filedDate'],r.get('sourcePriority',0))
    chosen=max(eligible,key=key)
    if len({count(r['totalCommonShares']) for r in eligible if key(r)==key(chosen)})!=1:
        raise ValueError('Ambiguous equally ranked actual common shares')
    return chosen

def market_cap(symbol,close,share,price_basis):
    expected='ORDINARY_EQUIVALENT_ARCHIVE' if symbol=='AZN' else 'ORDINARY'
    if price_basis!=expected or share['symbol']!=symbol or share.get('unit')!='shares':
        raise ValueError('Incompatible common-share/price basis')
    price=Decimal(str(close))
    if not price.is_finite() or price<=0:raise ValueError('Invalid decision close')
    return price*count(share['totalCommonShares'])

def load(path):return json.loads(Path(path).read_text(encoding='utf-8'))
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    if (OUT/'inputs.json').exists():raise ValueError('Refuse completed output overwrite')
    source_roots={'ANSS':'tmp/quant-older-inputs-20261018','ILMN':'tmp/quant-older-inputs-20261018',
        'GFS':'tmp/quant-extended-inputs-20261018','MDB':'tmp/quant-older-inputs-20261018'}
    observations=[];paths=[]
    for symbol,root in source_roots.items():
        fact_path=Path(root)/f'{symbol}-facts.json';meta_path=Path(root)/f'{symbol}-submissions.json'
        manifest=load(Path(root)/'manifest.json')
        for p in (fact_path,meta_path):
            source=next(s for s in manifest['sources'] if Path(s['path'])==p)
            if sha(p)!=source['sha256']:raise ValueError('Changed original SEC source')
        # Bound this experiment, not the validity rule: malformed in-scope counts still fail closed.
        extracted=xbrl_share_rows(load(fact_path),symbol,filed_from='2023-01-01',filed_before='2025-05-30')
        recent=load(meta_path)['filings']['recent']
        for r in extracted:
            if r['accessionNumber'] in recent['accessionNumber']:
                i=recent['accessionNumber'].index(r['accessionNumber'])
                if r['filedDate']!=recent['filingDate'][i] or r['form']!=recent['form'][i]:raise ValueError('Filing metadata mismatch')
                r['filingMetadataVerifiedAgainstRecentSubmissions']=True
            else:r['filingMetadataVerifiedAgainstRecentSubmissions']=False
        observations.extend(extracted);paths.extend([str(fact_path),str(meta_path),str(Path(root)/'manifest.json')])
    for p in ('tmp/quant-v9-dual-shares-20261030/observations.json','tmp/quant-v9-azn-shares-20261030/observations.json'):
        content=load(p);curated=content['observations'] if isinstance(content,dict) else content
        observations.extend(r for r in curated if r['symbol'] in ('TEAM','AZN'))
        paths.append(p)
    old=load('tmp/quant-older-model-recovery-v2-final-20261030/result.json')
    staged=load('tmp/quant-older-staged-20261030/inputs.json')
    byday={};expected_keys=set()
    # Full archived OHLCV supplies decision closes, not future output holding closes.
    price_rows={}
    with Path('tmp/quant-anss-free-evidence-20261018/chartexchange-anss.csv').open(encoding='utf-8') as stream:
        price_rows['ANSS']={r['date']:r['close'] for r in csv.DictReader(stream)}
    bundle=load('tmp/pit-acquisition/actions/bundle.json')
    for symbol in ('ILMN','GFS','MDB','TEAM','AZN'):
        source=next(s for s in bundle['sources'] if s['id']=='yahoo-chart-'+symbol)
        p=Path('tmp/pit-acquisition/actions')/source['path']
        if sha(p)!=source['sha256']:raise ValueError('Changed archived decision prices')
        chart=load(p)['chart']['result'][0];quote=chart['indicators']['quote'][0]
        from datetime import datetime
        from zoneinfo import ZoneInfo
        price_rows[symbol]={datetime.fromtimestamp(t,ZoneInfo(chart['meta']['exchangeTimezoneName'])).date().isoformat():str(quote['close'][i])
            for i,t in enumerate(chart['timestamp']) if quote['close'][i] is not None}
        paths.append(str(p))
    for d in old['dates']:
        day=d['decision'];gaps=next(m['missingSymbols'] for m in d['models'] if m['model'].startswith('EXPECTED_RETURN_V9_'))
        rows=[]
        for symbol in gaps:
            expected_keys.add((day,symbol));share=select_share(observations,symbol,day)
            basis='ORDINARY_EQUIVALENT_ARCHIVE' if symbol=='AZN' else 'ORDINARY'
            close=price_rows[symbol][day];cap=market_cap(symbol,close,share,basis)
            rows.append(dict(symbol=symbol,snapshotDate=day,currency='USD',closePrice=close,
                sharesOutstanding=str(share['totalCommonShares']),marketCapUsd=str(cap),priceBasis=basis,
                sharesSource=share['sourceClassification'],marketCapSource='HISTORICAL_PRIOR_REPORTED_COMMON_COMPANY_EQUITY_PROXY',
                shareEvidence=share,priceSource='ARCHIVED_PROVIDER_RESEARCH_NOT_EXECUTABLE_PRICE_PROOF'))
        byday[day]=rows
    assert len(expected_keys)==59 and sum(map(len,byday.values()))==59
    paths+=['tmp/quant-older-model-recovery-v2-final-20261030/result.json','tmp/quant-older-staged-20261030/inputs.json',
        'tmp/pit-acquisition/actions/bundle.json','tmp/quant-anss-free-evidence-20261018/chartexchange-anss.csv','scripts/quant_v9_market_inputs.py']
    output=dict(classification='PRIOR_PUBLIC_REPORTED_SHARES_RESEARCH_NOT_OFFICIAL_PIT',databaseWrites=0,promotionAllowed=False,
        xbrlFilingScope=dict(fromInclusive='2023-01-01',beforeExclusive='2025-05-30',reason='Research decision range; unrelated 2014 ANSS zero-count facts not imported.'),
        observations=observations,marketRowsByDecision=byday,missingKeysRecovered=len(expected_keys),inputHashes={p:sha(p) for p in sorted(set(paths))},
        limitations=['Latest reported actual shares, not exact daily corporate-action-adjusted outstanding shares or index float.',
          'Manual primary text remains distinct from actual SEC XBRL; reported rounding and older observation ages retained.',
          'AZN archived close ordinary-equivalent after ex-post ADS conversion; ordinary counts used without doubling.',
          'Historical full official roster, contemporaneous receipt times and product/data-use rights unproven.'])
    with (OUT/'inputs.json').open('x',encoding='utf-8') as stream:json.dump(output,stream,indent=2)
    print(json.dumps(dict(recoveredKeys=len(expected_keys),dates=len(byday),symbols=sorted({s for d,s in expected_keys})),indent=2))

if __name__=='__main__':main()
