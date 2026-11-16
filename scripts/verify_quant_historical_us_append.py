"""Independent file-only append-plan verifier. Does not import the preparing implementation."""
import argparse
import hashlib
import json
from collections import Counter, defaultdict
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path


def read(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def camel(s):
    parts=s.split('_');return parts[0]+''.join(p.title() for p in parts[1:])


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--plan',required=True);ap.add_argument('--out',required=True)
    args=ap.parse_args();root=Path(args.plan);m=read(root/'manifest.json')
    assert sha(root/'manifest.json')==read(root/'manifest.sha256.json')['sha256']
    assert m['policy']=='INSERT_ONLY_EMPTY_US_ISSUERS_V1'
    for p,h in m['inputHashes'].items():assert sha(p)==h,p
    for p,h in m['codeHashes'].items():assert sha(p)==h,p
    facts=read(root/m['factsFile']);standards=read(root/m['standardsFile']);backup=read(root/m['backupFile'])
    for key in ['facts','standards','backup']:assert sha(root/m[key+'File'])==m[key+'Hash']
    assert len(facts)==m['factsCount'] and len(standards)==m['standardsCount']
    allowed={'ANSS':'1013462','ILMN':'1110803','MDB':'1441816','TEAM':'1650372'}
    assert not any(r['symbol'] in allowed for r in backup)
    keys=set();raw_index=defaultdict(list);source_index=defaultdict(set)
    paths={r['symbol']:r['path'] for p in m['inputHashes'] if p.endswith('coverage.json') for r in read(p)['sourceFiles'] if r['path'].endswith('facts.json')}
    for symbol in allowed:
        source=read(paths[symbol]);assert str(source['cik'])==allowed[symbol]
        for tax,concepts in source['facts'].items():
            for concept,entry in concepts.items():
                for unit,rows in entry.get('units',{}).items():
                    for r in rows:
                        source_index[symbol].add((tax,concept,unit,json.dumps(r,sort_keys=True,separators=(',',':'))))
    null_starts=0
    for r in facts:
        assert r['symbol'] in allowed and str(int(r['cik']))==allowed[r['symbol']]
        key=tuple(r[k] for k in ['symbol','taxonomy','concept','unit','accession_number','fiscal_year','fiscal_period','start_date','end_date'])
        assert key not in keys;keys.add(key)
        raw=read_string=json.loads(r['raw_json'])
        assert (r['taxonomy'],r['concept'],r['unit'],json.dumps(raw,sort_keys=True,separators=(',',':'))) in source_index[r['symbol']]
        assert raw.get('start')==r['start_date'] and raw['end']==r['end_date']
        assert raw['filed']==r['filed_at'] and raw['accn']==r['accession_number']
        assert Decimal(str(raw['val']))==Decimal(r['value'])
        assert raw['form']==r['form'] and str(raw['fy'])==r['fiscal_year'] and raw['fp']==r['fiscal_period']
        assert r['taxonomy']=='us-gaap' and r['unit'] in ['USD','USD/shares']
        fetched=datetime.fromisoformat(r['fetched_at']).replace(tzinfo=timezone.utc)
        assert abs((datetime.now(timezone.utc)-fetched).total_seconds())<48*3600
        null_starts+=r['start_date'] is None
        raw_index[(r['symbol'],r['accession_number'],r['fiscal_year'],r['fiscal_period'],r['form'],r['filed_at'],r['end_date'])].append(r)
    fields=['revenue','operating_income','net_income','eps_diluted','assets','liabilities','equity','operating_cash_flow']
    standard_keys=set();verified_fields=0;field_counts=Counter()
    for r in standards:
        key=tuple(r[k] for k in ['symbol','fiscal_year','fiscal_period','form','accession_number','end_date'])
        assert key not in standard_keys;standard_keys.add(key)
        assert r['id'] is None and r['currency']=='USD'
        days=(date.fromisoformat(r['end_date'])-date.fromisoformat(r['start_date'])).days+1
        assert days==int(r['period_days'])
        assert (330<=days<=380 if r['fiscal_period']=='FY' else r['fiscal_period'] in ['Q1','Q2','Q3','Q4'] and 70<=days<=110)
        evidence=json.loads(r['raw_json']);assert evidence['coherencePolicy']=='EXPLICIT_CURRENCY_EXACT_DURATION_V1'
        matching=raw_index[(r['symbol'],r['accession_number'],r['fiscal_year'],r['fiscal_period'],r['form'],r['filed_at'],r['end_date'])]
        for f in fields:
            if r[f] is None:continue
            field_counts[f]+=1;e=evidence['selectedFieldEvidence'][camel(f)]
            assert any(x['concept']==e['concept'] and x['unit']==e['unit'] and x['start_date']==e['startDate'] and Decimal(x['value'])==Decimal(r[f]) for x in matching)
            assert e['endDate']==r['end_date']
            if f in ['assets','liabilities','equity']:assert e['startDate'] is None
            else:assert e['startDate']==r['start_date'] and e['periodDays']==days
            assert e['unit']==('USD/shares' if f=='eps_diluted' else 'USD')
            verified_fields+=1
    coverage_paths=[p for p in m['inputHashes'] if p.endswith('coverage.json')];assert len(coverage_paths)==1
    panels=[p for p in read(coverage_paths[0])['coverage'] if p['symbol'] in allowed]
    covered=0;reference_rows=[]
    for p in panels:
        for acc in p['requiredAccessions']:
            rows=[r for r in standards if r['symbol']==p['symbol'] and r['accession_number']==acc and r['filed_at']<p['decision'] and r['end_date']<p['decision']]
            assert rows,(p['symbol'],p['decision'],acc)
            covered+=1
            reference_rows.append(dict(symbol=p['symbol'],decision=p['decision'],accession=acc,eligibleStandardRows=len(rows)))
    report=dict(classification='INDEPENDENT_FILE_ONLY_CANDIDATE_VERIFICATION_NOT_IMPORTED',databaseWrites=0,
                facts=len(facts),standards=len(standards),backupRows=len(backup),nullInstantStartFacts=null_starts,
                verifiedFinancialFields=verified_fields,availableFields=dict(field_counts),
                issuerCounts={s:dict(raw=sum(r['symbol']==s for r in facts),standard=sum(r['symbol']==s for r in standards)) for s in allowed},
                historicalPanels=len(panels),coveredPriorFiledAccessionReferences=covered,
                minDecision=min(p['decision'] for p in panels),maxDecision=max(p['decision'] for p in panels),
                referenceRows=reference_rows,manifestSha256=sha(root/'manifest.json'),
                verificationScriptSha256=sha(__file__),rollbackProbesExecutedByVerifier=False,promotionAllowed=False)
    with Path(args.out).open('x',encoding='utf-8') as f:json.dump(report,f,indent=2);f.write('\n')
    print(json.dumps({k:v for k,v in report.items() if k not in ['referenceRows']},indent=2))


if __name__=='__main__':main()
