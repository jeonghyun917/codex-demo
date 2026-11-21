"""Strict filing-before-decision SEC financial rows for existing historical model code."""
import json
import hashlib
import math
from datetime import date
from decimal import Decimal
from pathlib import Path

FIELDS={
 'revenue':('USD',['Revenues','RevenueFromContractWithCustomerExcludingAssessedTax','SalesRevenueNet']),
 'operatingIncome':('USD',['OperatingIncomeLoss']),
 'netIncome':('USD',['NetIncomeLoss','NetIncomeLossAvailableToCommonStockholdersBasic']),
 'epsDiluted':('USD/shares',['EarningsPerShareDiluted']),
 'assets':('USD',['Assets']), 'liabilities':('USD',['Liabilities']),
 'equity':('USD',['StockholdersEquity','StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest']),
 'operatingCashFlow':('USD',['NetCashProvidedByUsedInOperatingActivities','NetCashProvidedByUsedInOperatingActivitiesContinuingOperations'])}
INSTANT={'assets','liabilities','equity'}

def financial_rows(payload,decision):
    if int(payload['cik'])!=1650372:raise ValueError('Wrong company CIK')
    date.fromisoformat(decision)
    facts=payload['facts'].get('us-gaap',{})
    anchors={}
    for tag in FIELDS['revenue'][1]:
        for r in facts.get(tag,{}).get('units',{}).get('USD',[]):
            if not r.get('start') or r.get('filed','9999')>=decision or r['end']>=decision:continue
            period=r.get('fp');form=r.get('form','')
            days=(date.fromisoformat(r['end'])-date.fromisoformat(r['start'])).days+1
            valid=(period=='FY' and form in ('10-K','10-K/A') and 330<=days<=380) or (
                period in ('Q1','Q2','Q3','Q4') and form in ('10-Q','10-Q/A') and 70<=days<=110)
            if not valid:continue
            key=(r['end'],period,r['filed'],r['accn'],r['start'])
            anchors[key]=r
    output=[]
    for (end,period,filed,accession,start),anchor in anchors.items():
        row=dict(symbol='TEAM',cik='1650372',fiscalYear=anchor.get('fy'),fiscalPeriod=period,
            form=anchor['form'],filedAt=filed,startDate=start,endDate=end,accessionNumber=accession,
            periodDays=(date.fromisoformat(end)-date.fromisoformat(start)).days+1,currency='USD',epsUnit='USD/shares')
        provenance={}
        for field,(unit,tags) in FIELDS.items():
            row[field]=None
            for tag in tags:
                matches=[r for r in facts.get(tag,{}).get('units',{}).get(unit,[]) if
                    r.get('accn')==accession and r.get('filed')==filed and r.get('end')==end
                    and (field in INSTANT and not r.get('start') or field not in INSTANT and r.get('start')==start)]
                values={Decimal(str(r['val'])) for r in matches}
                if any(not v.is_finite() for v in values):raise ValueError('Nonfinite financial fact')
                if len(values)>1:raise ValueError('Conflicting same-accession financial values')
                if values:
                    row[field]=str(next(iter(values)));provenance[field]=dict(tag=tag,unit=unit,accession=accession,filed=filed)
                    break
        row['rawJson']=json.dumps(provenance,sort_keys=True)
        output.append(row)
    return sorted(output,key=lambda r:(r['endDate'],r['filedAt'],r['accessionNumber']),reverse=True)


def main():
    root=Path('tmp/quant-team-inputs-20261018')
    manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
    for s in manifest['sources']:
        if hashlib.sha256(Path(s['path']).read_bytes()).hexdigest()!=s['sha256']:raise ValueError('Changed source')
    payload=json.loads((root/'team-facts.json').read_text(encoding='utf-8'))
    dates=['2026-01-30','2026-02-27','2026-03-27']
    inputs={d:financial_rows(payload,d) for d in dates}
    result=dict(classification='RECONSTRUCTED_FILING_DATE_BOUNDED_NOT_PIT',companyFactsSha256=manifest['sources'][0]['sha256'],byDecision=inputs)
    path=root/'financial-inputs.json'
    if path.exists():raise ValueError('Refuse overwrite normalized inputs')
    path.write_text(json.dumps(result,indent=2),encoding='utf-8')
    for d,rows in inputs.items():
        latest_fy=next(r for r in rows if r['fiscalPeriod']=='FY')
        latest_q=next(r for r in rows if r['fiscalPeriod']!='FY')
        print(d,'FY',latest_fy['endDate'],latest_fy['filedAt'],latest_fy['revenue'],'Q',latest_q['endDate'],latest_q['filedAt'],latest_q['revenue'])

if __name__=='__main__':main()
