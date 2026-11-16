"""Foreign-issuer IFRS input recovery, explicitly ordinary-share price basis."""
import hashlib
import json
from datetime import date
from decimal import Decimal
from pathlib import Path
from quant_team_financials import financial_rows

ROOT=Path('tmp/quant-extended-inputs-20261018')
DATES=['2025-07-31','2025-08-29','2025-09-30','2025-11-28','2025-12-31']
CIKS={'AZN':901832,'GFS':1709048}
FIELDS={
 'revenue':('USD',['Revenue']),
 'operatingIncome':('USD',['ProfitLossFromOperatingActivities']),
 'netIncome':('USD',['ProfitLossAttributableToOwnersOfParent','ProfitLoss']),
 'epsDiluted':('USD/shares',['DilutedEarningsLossPerShare']),
 'assets':('USD',['Assets']),'liabilities':('USD',['Liabilities']),
 'equity':('USD',['EquityAttributableToOwnersOfParent','Equity']),
 'operatingCashFlow':('USD',['CashFlowsFromUsedInOperatingActivities'])}
INSTANT={'assets','liabilities','equity'}

def foreign_rows(payload,symbol,decision,price_basis):
    if symbol not in CIKS or int(payload['cik'])!=CIKS[symbol]:raise ValueError('Wrong issuer')
    required='ORDINARY_EQUIVALENT_ARCHIVE' if symbol=='AZN' else 'ORDINARY'
    if price_basis!=required:raise ValueError('Unverified ADR/ordinary EPS and price basis')
    facts=payload['facts'].get('ifrs-full',{})
    anchors={}
    for r in facts.get('Revenue',{}).get('units',{}).get('USD',[]):
        if not r.get('start') or r.get('filed','9999')>=decision or r['end']>=decision:continue
        start,end=date.fromisoformat(r['start']),date.fromisoformat(r['end'])
        days=(end-start).days+1
        if r['form'] in ('20-F','20-F/A') and 330<=days<=380:period='FY'
        elif r['form'] in ('6-K','6-K/A') and 70<=days<=110:
            if start.day!=1 or start.month not in (1,4,7,10) or end.month!=start.month+2:continue
            period='Q'+str((start.month-1)//3+1)
            if r.get('fp') not in (None,period):continue
        else:continue
        anchors[(r['end'],period,r['filed'],r['accn'],r['start'])]=r
    rows=[]
    for (end,period,filed,accession,start),anchor in anchors.items():
        row=dict(symbol=symbol,cik=str(CIKS[symbol]),fiscalYear=anchor.get('fy') or date.fromisoformat(end).year,
            fiscalPeriod=period,form=anchor['form'],filedAt=filed,startDate=start,endDate=end,
            accessionNumber=accession,periodDays=(date.fromisoformat(end)-date.fromisoformat(start)).days+1,
            currency='USD',epsUnit='USD/shares')
        provenance=dict(taxonomy='ifrs-full',priceBasis=price_basis,sourceFiscalPeriod=anchor.get('fp'),
            quarterLabelDerivedFromCalendarDates=anchor.get('fp') is None)
        for field,(unit,tags) in FIELDS.items():
            row[field]=None
            for tag in tags:
                matching=[r for r in facts.get(tag,{}).get('units',{}).get(unit,[]) if r.get('accn')==accession
                    and r.get('filed')==filed and r.get('end')==end
                    and ((field in INSTANT and not r.get('start')) or (field not in INSTANT and r.get('start')==start))]
                values={Decimal(str(r['val'])) for r in matching}
                if any(not v.is_finite() for v in values) or len(values)>1:raise ValueError('Conflicting/nonfinite same-filing values')
                if values:
                    row[field]=str(next(iter(values)));provenance[field]=dict(tag=tag,unit=unit,accession=accession,filed=filed);break
        row['rawJson']=json.dumps(provenance,sort_keys=True);rows.append(row)
    return sorted(rows,key=lambda r:(r['endDate'],r['filedAt'],r['accessionNumber']),reverse=True)

def main():
    manifest=json.loads((ROOT/'manifest.json').read_text(encoding='utf-8'))
    for item in manifest['sources']:
        if 'sha256' not in item or hashlib.sha256(Path(item['path']).read_bytes()).hexdigest()!=item['sha256']:
            raise ValueError('Incomplete or changed evidence')
    team=json.loads(Path('tmp/quant-team-inputs-20261018/team-facts.json').read_text(encoding='utf-8'))
    facts={s:json.loads((ROOT/f'{s}-facts.json').read_text(encoding='utf-8')) for s in CIKS}
    result=dict(classification='FILING_DATE_BOUNDED_RESEARCH_NOT_PIT',dates=DATES,bySymbol={})
    result['bySymbol']['TEAM']={d:financial_rows(team,d) for d in DATES}
    for s in CIKS:
        basis='ORDINARY_EQUIVALENT_ARCHIVE' if s=='AZN' else 'ORDINARY'
        result['bySymbol'][s]={d:foreign_rows(facts[s],s,d,basis) for d in DATES}
    # Manually reviewed primary web-reader evidence, never represented as raw/XBRL.
    curated=[dict(symbol='TEAM',accessionNumber='0001650372-25-000022',filedDate='2025-05-02',shareMeasurementDate='2025-03-31',
        classAShares=165150665,classBShares=98008049,totalCommonShares=263158714,
        sourceUrl='https://www.sec.gov/Archives/edgar/data/1650372/000165037225000022/team-20250331.htm',
        evidenceLocation='Balance sheet common-stock rows; actual shares, not thousands'),
      dict(symbol='TEAM',accessionNumber='0001650372-25-000036',filedDate='2025-08-15',shareMeasurementDate='2025-06-30',
        classAShares=165949196,classBShares=97030987,totalCommonShares=262980183,
        sourceUrl='https://www.sec.gov/Archives/edgar/data/1650372/000165037225000036/team-20250630.htm',
        evidenceLocation='Balance sheet common-stock rows; equal economic dividend/liquidation rights Note14'),
      dict(symbol='AZN',accessionNumber='0001104659-25-014750',filedDate='2025-02-18',shareMeasurementDate='2024-12-31',
        totalCommonShares=1550546239,
        sourceUrl='https://www.astrazeneca.ch/content/dam/az/Investor_Relations/annual-report-2024/pdf/AstraZeneca_AR_2024_Financial_Statements.pdf',
        evidenceLocation='Annual report2024 Note24 page192, PDFpage45 actual issued ordinary shares; not weighted-average EPS denominator')]
    previous=json.loads(Path('tmp/quant-team-share-evidence-20261018/share-facts.json').read_text(encoding='utf-8'))
    curated.extend(dict(r,symbol='TEAM') for r in previous['observations'])
    for r in curated:
        r.update(unit='shares',sourceClassification='PRIMARY_TEXT_CURATED_RESEARCH_PROXY_NOT_XBRL',
            rawOriginalSourceAcquired=False,reviewDate='2026-10-18')
    gfs_recent=json.loads((ROOT/'GFS-submissions.json').read_text(encoding='utf-8'))['filings']['recent']
    for r in facts['GFS']['facts']['ifrs-full']['NumberOfSharesOutstanding']['units']['shares']:
        if (r.get('filed','9999')<'2025-12-01' and '2024-12-31'<=r['end']<'2025-12-01'
            and not r.get('start') and r['accn'] in gfs_recent['accessionNumber']
            and gfs_recent['reportDate'][gfs_recent['accessionNumber'].index(r['accn'])]==r['end']):
            curated.append(dict(symbol='GFS',accessionNumber=r['accn'],filedDate=r['filed'],shareMeasurementDate=r['end'],
                totalCommonShares=r['val'],unit='shares',sourceClassification='SEC_XBRL_IFRS_ACTUAL_PRIOR_COMMON_RESEARCH',
                rawOriginalSourceAcquired=True,sourceUrl=f'https://data.sec.gov/api/xbrl/companyfacts/CIK{CIKS["GFS"]:010d}.json',
                evidenceLocation='ifrs-full:NumberOfSharesOutstanding, shares, actual instant; not EPS weighted average'))
    # Validate filing metadata separately from manually transcribed numeric counts.
    submissions={'TEAM':json.loads(Path('tmp/quant-team-inputs-20261018/team-submissions.json').read_text(encoding='utf-8'))}
    submissions.update({s:json.loads((ROOT/f'{s}-submissions.json').read_text(encoding='utf-8')) for s in CIKS})
    for r in curated:
        recent=submissions[r['symbol']]['filings']['recent'];i=recent['accessionNumber'].index(r['accessionNumber'])
        assert recent['filingDate'][i]==r['filedDate']
        assert recent['reportDate'][i]==r['shareMeasurementDate']
        assert r['totalCommonShares']>0
        if r['symbol']=='TEAM':assert r['classAShares']+r['classBShares']==r['totalCommonShares']
    result['shareObservations']=curated
    result['aznPriceBasis']=dict(basis='ORDINARY_EQUIVALENT_ARCHIVE',
        description='Archived Yahoo pre-Feb2026 close series already doubled for 1:2 ADS-to-ordinary transition; pair with unscaled ordinary EPS and ordinary shares, not ADS EPS/count.',
        eventDate='2026-02-02',numerator=1,denominator=2,
        ratioPrimarySource='https://www.sec.gov/Archives/edgar/data/901832/000110465925014732/tm251208d3_ex99-1.pdf',
        transitionPrimarySource='https://www.astrazeneca.com/media-centre/press-releases/2026/astrazeneca-to-complete-direct-listing-on-NYSE.html/',
        classification='EX_POST_PRICE_UNIT_NORMALIZATION_NOT_CONTEMPORANEOUS_EXECUTION_PRICE')
    path=ROOT/'financial-inputs.json'
    with path.open('x',encoding='utf-8') as stream:json.dump(result,stream,indent=2)
    print(json.dumps({s:{d:len(rows) for d,rows in dates.items()} for s,dates in result['bySymbol'].items()}))

if __name__=='__main__':main()
