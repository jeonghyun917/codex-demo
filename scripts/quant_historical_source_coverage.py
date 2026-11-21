"""Append-only archival SEC-fact staging. Never imports, overwrites, or invents receipt history."""
import argparse
import hashlib
import json
from collections import Counter, defaultdict
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

KEY = ('symbol','taxonomy','concept','unit','accessionNumber','fiscalYear','fiscalPeriod','startDate','endDate')
DB_NAMES = {'fiscalYear':'fiscal_year','fiscalPeriod':'fiscal_period','startDate':'start_date',
            'endDate':'end_date','accessionNumber':'accession_number','filedAt':'filed_at',
            'rawJson':'raw_json','fetchedAt':'fetched_at'}
PERIODIC = {'10-K','10-K/A','10-Q','10-Q/A','20-F','20-F/A','40-F','40-F/A'}


def natural_key(row):
    return tuple(str(row.get(k)) if row.get(k) is not None else None for k in KEY)


def semantic(row):
    return (Decimal(str(row['value'])), row.get('filedAt'), row.get('form'), int(row['cik']))


def stage_missing(archived, existing, decision, fetched_now):
    date.fromisoformat(decision)
    current = defaultdict(list)
    for r in existing:
        current[natural_key(r)].append(r)
    seen, staged, conflicts = {}, [], []
    for r in archived:
        if not r.get('filedAt') or r['filedAt'] >= decision or not r.get('endDate') or r['endDate'] >= decision:
            continue
        value = Decimal(str(r['value']))
        if not value.is_finite() or (r.get('startDate') and r['startDate'] > r['endDate']):
            raise ValueError('Invalid financial value or duration')
        key = natural_key(r)
        if key in seen:
            if semantic(seen[key]) != semantic(r):
                raise ValueError('conflicting archived same-natural-key facts')
            continue
        seen[key] = r
        if key in current:
            for old in current[key]:
                if semantic(old) != semantic(r):
                    conflicts.append(dict(key=key, archived=r, existing=old))
            continue
        staged.append(r | {'fetchedAt': fetched_now})
    return staged, conflicts


def load(p):
    return json.loads(Path(p).read_text(encoding='utf-8-sig'))


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def write(p, value):
    with Path(p).open('x', encoding='utf-8') as f:
        json.dump(value, f, indent=2, ensure_ascii=False)
        f.write('\n')


def camel_db(rows):
    reverse = {v:k for k,v in DB_NAMES.items()}
    return [{reverse.get(k,k):v for k,v in r.items()} for r in rows]


def archive_rows(payload, symbol, allowed):
    for taxonomy, concepts in payload['facts'].items():
        for concept, entry in concepts.items():
            for unit, items in entry.get('units',{}).items():
                for raw in items:
                    if raw.get('accn') not in allowed or raw.get('val') is None:
                        continue
                    # Preserve raw FY/FP, units and form. Do not derive foreign quarter labels.
                    yield dict(symbol=symbol,cik=str(payload['cik']),taxonomy=taxonomy,concept=concept,unit=unit,
                        fiscalYear=raw.get('fy'),fiscalPeriod=raw.get('fp'),form=raw.get('form'),
                        filedAt=raw.get('filed'),startDate=raw.get('start'),endDate=raw.get('end'),
                        accessionNumber=raw['accn'],frame=raw.get('frame'),value=str(raw['val']),
                        rawJson=json.dumps(raw,sort_keys=True,separators=(',',':')))


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--snapshot',required=True);ap.add_argument('--out',required=True)
    args=ap.parse_args();snapshot=Path(args.snapshot);out=Path(args.out)
    out.mkdir(parents=True,exist_ok=False)
    now=datetime.now(timezone.utc).isoformat()
    metadata=load(snapshot/'snapshot.json');manifest=load('tmp/quant-matched-financial-all20-20261031/overlay-manifest.json')
    panels=manifest['rows'];required=defaultdict(set);latest={}
    for p in panels:
        required[p['symbol']].update(p['oldRequiredAccessions'])
        latest[p['symbol']]=max(latest.get(p['symbol'],''),p['decision'])
    provenance=[];by_symbol={};all_staged=[];all_conflicts=[];all_keys=defaultdict(set);db_keys=defaultdict(set)
    for symbol, issuer in metadata['issuers'].items():
        if issuer['source']=='NATIVE_DB_ONLY':continue
        source=Path(issuer['source']);source_manifest=load(source.parent/'manifest.json')
        records=[r for r in source_manifest['sources'] if Path(r.get('path','')).resolve()==source.resolve()]
        if len(records)!=1 or records[0].get('status')!=200 or records[0]['sha256']!=sha(source):
            raise ValueError('Unverified original source '+str(source))
        record=records[0];payload=load(source)
        if str(payload['cik'])!=issuer['cik']:raise ValueError('CIK mismatch')
        rows=list(archive_rows(payload,symbol,required[symbol]));existing=camel_db(load(snapshot/f'{symbol}-db-facts.json'))
        staged,conflicts=stage_missing(rows,existing,latest[symbol],now)
        for r in rows:all_keys[symbol].add(natural_key(r))
        for r in existing:db_keys[symbol].add(natural_key(r))
        by_symbol[symbol]=dict(requiredUniqueAccessions=len(required[symbol]),requiredHistoricalReferences=sum(len(p['oldRequiredAccessions']) for p in panels if p['symbol']==symbol),
            archivedRequiredRows=len({natural_key(r) for r in rows}),newRawRows=len(staged),conflicts=len(conflicts),
            newAccessions=len({r['accessionNumber'] for r in staged}),
            nativeParserCompatibleRows=sum(r['form'] in PERIODIC for r in staged),
            excludedFromCurrentParserRows=sum(r['form'] not in PERIODIC for r in staged),
            allRequiredAccessionsHaveOriginalFacts=required[symbol]<={r['accessionNumber'] for r in rows},
            currentStandardizerCandidateRows=len(load(snapshot/f'{symbol}-current-standardizer-candidates.json')))
        write(out/f'{symbol}-missing-raw-facts.json',staged)
        all_staged.extend(staged);all_conflicts.extend(conflicts)
        provenance.append(dict(symbol=symbol,path=str(source),sha256=sha(source),url=record['url'],
                               retrievedAt=record.get('retrievedAt'),classification='ORIGINAL_ARCHIVED_PUBLIC_SEC_RESPONSE'))
        sub=source.with_name(source.name.replace('facts','submissions'))
        sub_records=[r for r in source_manifest['sources'] if Path(r.get('path','')).resolve()==sub.resolve()]
        if len(sub_records)==1 and sub_records[0].get('status')==200 and sub_records[0]['sha256']==sha(sub):
            provenance.append(dict(symbol=symbol,path=str(sub),sha256=sha(sub),url=sub_records[0]['url'],
                                   retrievedAt=sub_records[0].get('retrievedAt'),classification='ORIGINAL_ARCHIVED_PUBLIC_SEC_SUBMISSIONS'))
    coverage=[]
    for p in panels:
        symbol=p['symbol'];existing=camel_db(load(snapshot/f'{symbol}-db-facts.json'))
        native=metadata['issuers'][symbol]['source']=='NATIVE_DB_ONLY'
        original=existing if native else list(archive_rows(load(metadata['issuers'][symbol]['source']),symbol,p['oldRequiredAccessions']))
        prior=[r for r in original if r.get('filedAt') and r['filedAt']<p['decision'] and r.get('endDate') and r['endDate']<p['decision']]
        staged=[r for r in all_staged if r['symbol']==symbol and r['filedAt']<p['decision'] and r['endDate']<p['decision']]
        before={r['accessionNumber'] for r in existing if r.get('filedAt') and r['filedAt']<p['decision']}
        after=before|{r['accessionNumber'] for r in staged}
        wanted=set(p['oldRequiredAccessions']);available={r['accessionNumber'] for r in prior}
        standards=camel_db(load(snapshot/f'{symbol}-db-standards.json'))
        native_before={r['accessionNumber'] for r in standards if r.get('filedAt') and r['filedAt']<p['decision'] and (r.get('revenue') or r.get('eps_diluted') or r.get('operating_income'))}
        candidates=load(snapshot/f'{symbol}-current-standardizer-candidates.json')
        native_after=native_before|{r['accessionNumber'] for r in candidates if r.get('filedAt') and r['filedAt']<p['decision']}
        coverage.append(dict(symbol=symbol,decision=p['decision'],requiredAccessions=sorted(wanted),
            evidenceClass='NATIVE_DB_PRIOR_FILED' if native else 'ARCHIVED_ORIGINAL_PUBLIC_SEC',
            priorFiledOriginalAccessions=sorted(wanted&available),dbRawCoveredBefore=sorted(wanted&before),
            missingRawBefore=sorted(wanted-before),stagedRawCoveredAfter=sorted(wanted&after),
            missingRawAfterStaging=sorted(wanted-after),currentStandardizerRecoveredReferences=sorted(wanted&native_after),
            dbNonQuarantinedStandardReferences=sorted(wanted&native_before)))
    group=defaultdict(list)
    for r in all_staged:group[(r['symbol'],r['accessionNumber'],r['filedAt'])].append(r)
    queue=[]
    for (symbol,acc,filed),rs in sorted(group.items()):
        compatible=all(r['form'] in PERIODIC for r in rs)
        foreign=any(r['taxonomy']=='ifrs-full' for r in rs)
        status='RAW_INSERT_ONLY_REQUIRES_SEPARATE_GUARDED_OPERATOR_REVIEW'
        if not compatible:status='HOLD_UNSUPPORTED_FILING_FORM_NO_AUTOMATIC_MAPPING'
        elif foreign:status='RAW_INSERT_ONLY_FOREIGN_STANDARDIZER_UNSUPPORTED'
        queue.append(dict(symbol=symbol,accessionNumber=acc,filedAt=filed,rows=len(rs),
                          units=sorted({r['unit'] for r in rs}),forms=sorted({r['form'] for r in rs}),
                          minStart=min((r['startDate'] for r in rs if r['startDate']),default=None),maxEnd=max(r['endDate'] for r in rs),status=status))
    result=dict(classification='HISTORICAL_SOURCE_GAP_STAGING_NOT_IMPORTED_NOT_PIT',createdAt=now,databaseWrites=0,
        newPublicRequests=0,promotionAllowed=False,sourceFiles=provenance,issuers=by_symbol,
        requiredPanels=len(coverage),requiredReferences=sum(len(c['requiredAccessions']) for c in coverage),
        rawReferencesCoveredBefore=sum(len(c['dbRawCoveredBefore']) for c in coverage),
        rawReferencesCoveredAfterStaging=sum(len(c['stagedRawCoveredAfter']) for c in coverage),
        originalPriorFiledCoveredReferences=sum(len(c['priorFiledOriginalAccessions']) for c in coverage),
        currentStandardizerCoveredReferences=sum(len(c['currentStandardizerRecoveredReferences']) for c in coverage),
        newRawRows=len(all_staged),conflicts=len(all_conflicts),coverage=coverage,importQueue=queue,
        limitations=['Staging is not a DB import and accession coverage is not financial-input usability.',
        'Native parser omits 6-K; native standardizer does not map IFRS concept names. No foreign labels, currency or share basis manufactured.',
        'All staged fetchedAt values are now; raw SEC filed dates support prior-filed reconstruction, not actual historical receipt.',
        'Existing analyst/news gaps remain: no current-news backfill, neutral inputs or model changes.',
        'No new validation dates, independent holdout, retraining or performance benefit established.'])
    write(out/'candidate-raw-facts.json',all_staged);write(out/'conflicts.json',all_conflicts);write(out/'coverage.json',result)
    hashes={str(p):sha(p) for p in sorted(snapshot.glob('*.json'))}
    hashes.update({r['path']:r['sha256'] for r in provenance})
    hashes.update({str(p):sha(p) for p in sorted(out.glob('*.json'))})
    for p in ['scripts/QuantHistoricalSourceCoverage.java','scripts/quant_historical_source_coverage.py','scripts/test_quant_historical_source_coverage.py','tmp/quant-matched-financial-all20-20261031/overlay-manifest.json']:
        hashes[p]=sha(p)
    write(out/'hashes.json',hashes)
    print(json.dumps({k:result[k] for k in ['requiredPanels','requiredReferences','rawReferencesCoveredBefore','rawReferencesCoveredAfterStaging','originalPriorFiledCoveredReferences','currentStandardizerCoveredReferences','newRawRows','conflicts']},indent=2))


if __name__=='__main__':main()
