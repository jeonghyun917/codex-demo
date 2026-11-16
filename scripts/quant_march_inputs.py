"""File-only March13 input recovery with causal financial and quoted-security unit guards."""
import copy,csv,hashlib,json,sys
from datetime import date
from decimal import Decimal
from pathlib import Path
from quant_team_financials import financial_rows
from quant_complete_windows import parse_chart

DAY='2026-03-13';OUT=Path('tmp/quant-march-inputs-20261031')
FOREIGN=Path('tmp/quant-march-foreign-evidence-20261031/network-approved')
CIKS={'CCEP':1650107,'FER':1468522,'TRI':1075124}
CURRENCIES={'CCEP':'EUR','FER':'EUR','TRI':'USD'}
FIELDS={'revenue':['Revenue'],'operatingIncome':['ProfitLossFromOperatingActivities'],
 'netIncome':['ProfitLossAttributableToOwnersOfParent','ProfitLoss'],'epsDiluted':['DilutedEarningsLossPerShare'],
 'assets':['Assets'],'liabilities':['Liabilities'],'equity':['EquityAttributableToOwnersOfParent','Equity'],
 'operatingCashFlow':['CashFlowsFromUsedInOperatingActivities']}
INSTANT={'assets','liabilities','equity'}
def load(p):return json.loads(Path(p).read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()

def foreign_rows(payload,symbol,decision):
    if symbol not in CIKS or int(payload['cik'])!=CIKS[symbol]:raise ValueError('Wrong foreign issuer')
    currency=CURRENCIES[symbol];facts=payload['facts'].get('ifrs-full',{});anchors={}
    for r in facts.get('Revenue',{}).get('units',{}).get(currency,[]):
        if not r.get('start') or r['start']<'2023-01-01' or r['filed']>=decision or r['end']>=decision:continue
        start,end=date.fromisoformat(r['start']),date.fromisoformat(r['end']);days=(end-start).days+1
        if r['form'] in ('20-F','20-F/A','40-F','40-F/A') and 330<=days<=380:period='FY'
        elif r['form'] in ('6-K','6-K/A') and 70<=days<=110:
            if start.day!=1 or start.month not in (1,4,7,10) or end.month!=start.month+2:continue
            period='Q'+str((start.month-1)//3+1)
        else:continue
        anchors[(r['end'],r['filed'],r['accn'],r['start'],period)]=r
    result=[]
    for (end,filed,accn,start,period),anchor in anchors.items():
        row=dict(symbol=symbol,cik=str(CIKS[symbol]),fiscalYear=anchor.get('fy') or date.fromisoformat(end).year,
            fiscalPeriod=period,form=anchor['form'],filedAt=filed,startDate=start,endDate=end,accessionNumber=accn,
            periodDays=(date.fromisoformat(end)-date.fromisoformat(start)).days+1,currency=currency,epsUnit=currency+'/shares')
        provenance={}
        for field,tags in FIELDS.items():
            row[field]=None;unit=currency+'/shares' if field=='epsDiluted' else currency
            for tag in tags:
                matches=[r for r in facts.get(tag,{}).get('units',{}).get(unit,[]) if r.get('accn')==accn and r.get('filed')==filed and r.get('end')==end
                    and ((field in INSTANT and not r.get('start')) or (field not in INSTANT and r.get('start')==start))]
                vals={Decimal(str(r['val'])) for r in matches}
                if len(vals)>1 or any(not v.is_finite() for v in vals):raise ValueError('Ambiguous/nonfinite same-filing financial fact')
                if vals:row[field]=str(next(iter(vals)));provenance[field]=dict(tag=tag,unit=unit,accession=accn,filed=filed);break
        row['rawJson']=json.dumps(provenance,sort_keys=True);result.append(row)
    return sorted(result,key=lambda r:(r['endDate'],r['filedAt'],r['accessionNumber']),reverse=True)

def valuation_units(rows,symbol,decision,ordinary_per_quote):
    if ordinary_per_quote is None or ordinary_per_quote<=0:raise ValueError('Unproven quoted-security unit ratio')
    result=copy.deepcopy(rows)
    for r in result:
        if r['symbol']!=symbol or not r['filedAt']<decision or not r['endDate']<decision:raise ValueError('Wrong/noncausal financial row')
        provenance=json.loads(r.get('rawJson') or '{}');reported=r.get('epsDiluted')
        policy=dict(reportedEps=str(reported) if reported is not None else None,reportedEpsUnit=r.get('epsUnit'),
            ordinarySharesPerQuotedSecurity=ordinary_per_quote,priceCurrency='USD',fxAssumed=False)
        if reported is not None:
            if r.get('currency')!='USD' or r.get('epsUnit')!='USD/shares':
                r['epsDiluted']=None;policy['action']='OMITTED_UNPROVEN_FX_EXISTING_MISSING_FACTOR_POLICY'
            else:
                r['epsDiluted']=str(Decimal(str(reported))*Decimal(str(ordinary_per_quote)))
                r['epsUnit']='USD/quoted-security';policy['action']='REPORTED_EPS_QUOTED_SECURITY_UNIT_NORMALIZATION_ROUNDING_RETAINED'
        provenance['valuationUnitPolicy']=policy;r['rawJson']=json.dumps(provenance,sort_keys=True)
    return result

def latest_inputs(rows):
    result=[]
    for annual in (True,False):
        seen=set();pair=[]
        for r in sorted(rows,key=lambda r:(r['endDate'],r['filedAt']),reverse=True):
            if (r['fiscalPeriod']=='FY')!=annual or r['endDate'] in seen:continue
            days=(date.fromisoformat(r['endDate'])-date.fromisoformat(r['startDate'])).days+1
            if not ((330<=days<=380) if annual else (70<=days<=110)):continue
            pair.append(r);seen.add(r['endDate'])
            if len(seen)==2:break
        if len(pair)==2 and pair[0].get('currency')!=pair[1].get('currency'):
            current=copy.deepcopy(pair[0]);provenance=json.loads(current.get('rawJson') or '{}')
            provenance['pairedPeriodPolicy']=dict(action='INCOMPARABLE_PRIOR_CURRENCY_EXISTING_MISSING_FACTOR_POLICY',
                currentCurrency=current.get('currency'),excludedPriorCurrency=pair[1].get('currency'),excludedPriorEndDate=pair[1]['endDate'],
                reason='No invented FX and no substitution of an older nonadjacent year/quarter.')
            current['rawJson']=json.dumps(provenance,sort_keys=True);pair=[current]
        result.extend(pair)
    return result

def main():
    if (OUT/'inputs-v3.json').exists():raise ValueError('Refuse completed output overwrite')
    native=load(OUT/'native-inputs-v2.json');paths=[str(OUT/'native-inputs-v2.json'),'scripts/QuantMarchInputInventory.java']
    manifest=load(FOREIGN/'manifest.json');paths.append(str(FOREIGN/'manifest.json'))
    for s in manifest['sources']:
        if s.get('status')!=200 or sha(s['path'])!=s['sha256']:raise ValueError('Changed/incomplete SEC original')
        paths.append(s['path'])
    financials=native['financialsBySymbol']
    for symbol in CIKS:financials[symbol]=latest_inputs(foreign_rows(load(FOREIGN/(symbol+'-facts.json')),symbol,DAY))
    team_path='tmp/quant-team-inputs-20261018/team-facts.json';team_manifest=load('tmp/quant-team-inputs-20261018/manifest.json')
    assert sha(team_path)==next(s['sha256'] for s in team_manifest['sources'] if s['id']=='team-facts')
    financials['TEAM']=latest_inputs(financial_rows(load(team_path),DAY));paths.extend([team_path,'tmp/quant-team-inputs-20261018/manifest.json'])
    for symbol,rows in financials.items():
        rows=latest_inputs(rows)
        if not any(r['fiscalPeriod']=='FY' and r.get('revenue') is not None for r in rows):raise ValueError('Missing actual annual financials '+symbol)
        financials[symbol]=valuation_units(rows,symbol,DAY,4 if symbol=='PDD' else 1)
    prices={};bundle_path='tmp/pit-acquisition/actions/bundle.json';bundle=load(bundle_path);paths.append(bundle_path)
    for symbol in financials:
        s=next(s for s in bundle['sources'] if s['id']=='yahoo-chart-'+symbol);p=Path('tmp/pit-acquisition/actions')/s['path']
        if sha(p)!=s['sha256']:raise ValueError('Changed price original')
        prices[symbol]=parse_chart(p.read_text(encoding='utf-8'),symbol);paths.append(str(p))
        if sum(d<=DAY for d in prices[symbol])<80 or DAY not in prices[symbol]:raise ValueError('Insufficient price history '+symbol)
    hp='tmp/quant-complete-windows-recovered-20261018/complete-holdings.csv';paths.append(hp)
    with Path(hp).open(encoding='utf-8') as stream:
        holdings=[r for r in csv.DictReader(stream) if r['index']=='NASDAQ100_MACRO_FREE_20261018' and r['decision']==DAY]
    if len(holdings)!=101 or {r['symbol'] for r in holdings}!=set(native['population']):raise ValueError('Incomplete holdings')
    for h in holdings:
        if h['symbol'] not in prices:continue
        pr=prices[h['symbol']]
        if abs(pr[h['entry']]/float(h['entryClose'])-1)>1e-5 or abs(pr[h['exit']]/float(h['exitClose'])-1)>1e-5:raise ValueError('Price endpoint basis mismatch')
    shares_path='tmp/quant-team-share-evidence-20261018/share-facts.json';paths.append(shares_path)
    share=max((s for s in load(shares_path)['observations'] if s['filedDate']<DAY and s['shareMeasurementDate']<DAY),key=lambda s:s['shareMeasurementDate'])
    if share['classAShares']+share['classBShares']!=share['totalCommonShares']:raise ValueError('TEAM common-class total mismatch')
    close=Decimal(str(prices['TEAM'][DAY]));market=dict(indexCode='NASDAQ100_MARCH_RESEARCH_20261031',symbol='TEAM',snapshotDate=DAY,currency='USD',
        closePrice=str(close),sharesOutstanding=str(share['totalCommonShares']),marketCapUsd=str(close*share['totalCommonShares']),
        priceSource='ARCHIVED_YAHOO_CLOSE_RESEARCH',sharesSource='SEC_TEXT_CURATED_PRIOR_QUARTER_TOTAL_COMMON',marketCapSource='PRIOR_REPORTED_COMPANY_EQUITY_PROXY')
    paths.extend(['scripts/quant_march_inputs.py','docs/quant-march-foreign-evidence-2026-10-31.md'])
    result=dict(classification='PRIOR_FILED_FINANCIAL_RESEARCH_NOT_OFFICIAL_PIT',databaseWrites=0,promotionAllowed=False,decision=DAY,
        financialsBySymbol=financials,holdings=holdings,teamMarket=market,teamShareEvidence=share,
        priorPriceCounts={s:sum(d<=DAY for d in ps) for s,ps in prices.items()},
        inputHashes={p:sha(p) for p in paths},
        unitNotes=['EUR financial ratios retain original common currency; EUR EPS omitted for USD valuation without proven FX.',
          'PDD reported USD per-ordinary EPS multiplied by primary-proven4ordinary perADS, reported rounding retained; not exact EPS observation.',
          'ARM oneADS=oneordinary; other quoted common units1:1. No synthetic forecasts or FX assumptions.',
          'Foreign annual/half-year data not relabeled as standalone quarters; existing missing-factor policies retained.'])
    with (OUT/'inputs-v3.json').open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
    print(json.dumps(dict(symbols=len(financials),financialRows={s:len(r) for s,r in financials.items()},holdings=len(holdings)),indent=2))

if __name__=='__main__':main()
