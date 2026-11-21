"""Verify newly acquired SEC originals and recover PDD's same-filing native-currency pair.
No DB import, assumed FX, EPS/ADS conversion, or forecast promotion.
"""
import hashlib, json
from datetime import date
from pathlib import Path
from decimal import Decimal

ROOT=Path('tmp/quant-20d-primary-inputs-20261031')
CUTOFF='2026-03-13'

def main():
    manifest=json.loads((ROOT/'manifest.json').read_text(encoding='utf-8'))
    sources=manifest['sources']
    assert len(sources)==4
    hashes={}
    for s in sources:
        assert s['status']==200 and s['validatedCik']==s['cik']
        p=Path(s['path']);b=p.read_bytes()
        assert len(b)==s['bytes'] and hashlib.sha256(b).hexdigest()==s['sha256']
        hashes[str(p)]=s['sha256']
    facts=json.loads((ROOT/'PDD-facts.json').read_text(encoding='utf-8'))
    sub=json.loads((ROOT/'PDD-submissions.json').read_text(encoding='utf-8'))
    assert int(facts['cik'])==int(sub['cik'])==1737806
    recent=sub['filings']['recent']
    accession='0001410578-25-000951'
    n=recent['accessionNumber'].index(accession)
    assert recent['form'][n]=='20-F' and recent['filingDate'][n]=='2025-04-28'<CUTOFF
    pair=[]
    for year in (2024,2023):
        row=dict(symbol='PDD',currency='CNY',endDate=f'{year}-12-31',accession=accession,
                 filedAt=recent['filingDate'][n],acceptedAtAsReported=recent['acceptanceDateTime'][n])
        for field,tag in [('revenue','RevenueFromContractWithCustomerExcludingAssessedTax'),('netIncome','NetIncomeLoss')]:
            candidates=[r for r in facts['facts']['us-gaap'][tag]['units']['CNY']
                        if r.get('accn')==accession and r.get('start')==f'{year}-01-01' and r.get('end')==row['endDate']
                        and r.get('filed','9999')<CUTOFF and r.get('form')=='20-F']
            assert candidates and len({str(r['val']) for r in candidates})==1
            r=candidates[0]
            assert 330<=(date.fromisoformat(r['end'])-date.fromisoformat(r['start'])).days+1<=380
            row[field]=str(r['val'])
        pair.append(row)
    growth={k+'GrowthPct':str((Decimal(pair[0][k])/Decimal(pair[1][k])-1)*100) for k in ('revenue','netIncome')}
    result=dict(status='PASS',sourcesVerified=4,bytes=sum(s['bytes'] for s in sources),inputHashes=hashes,
                cutoff=CUTOFF,pddComparableAnnualPair=pair,**growth,
                epsPolicy='CNY accounting pair only; no assumed FX or quoted ADS EPS conversion',
                databaseWrites=0,promotionAllowed=False,
                classification='NEWLY_RETRIEVED_PRIMARY_SOURCE_RESEARCH_NOT_HISTORICAL_RECEIPT_PROOF')
    path=ROOT/'verified-native-pair.json'
    if path.exists():
        assert json.loads(path.read_text(encoding='utf-8'))==result,'Existing evidence changed'
    else:path.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))

if __name__=='__main__':main()
