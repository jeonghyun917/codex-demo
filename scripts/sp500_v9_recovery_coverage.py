"""Connect original cover evidence to missing dates without admitting model inputs."""
import json
from pathlib import Path
import pandas as pd
from acquire_sp500_v9_sources import OUT
from sp500_v9_shadow import ROOT, digest


def select_cover(rows, day):
    eligible = [r for r in rows if r['status'] == 'PARSED' and r['filedAt'] < day
                and r.get('facts') and all(f['measurementDate'] < day for f in r['facts'])
                and any(f['value'] > 0 for f in r['facts'])]
    return max(eligible, key=lambda r: r['filedAt']) if eligible else None


def main():
    source = OUT / 'cover-history-observations.json'
    covers = json.loads(source.read_text())
    hashes = {str(source): digest(source)}
    grouped = {}
    for row in covers:
        if row['status'] != 'PARSED':
            continue
        for path_key, hash_key in [('path', 'sourceSha256'), ('metadataPath', 'metadataSha256')]:
            p = Path(row[path_key])
            assert digest(p) == row[hash_key], str(p)
            hashes[str(p)] = row[hash_key]
        metadata = json.loads(Path(row['metadataPath']).read_text())['filings']['recent']
        i = metadata['accessionNumber'].index(row['accession'])
        assert metadata['filingDate'][i] == row['filedAt']
        assert metadata['form'][i] == row['form']
        assert all(str(int(f['issuerCik'])) == str(int(row['cik'])) for f in row['facts'])
        if row['symbol'] == 'SATS':
            assert int(row['cik']) == 1415404
        grouped.setdefault(row['symbol'], []).append(row)
    previous = ROOT / 'output/sp500-v9-full-preflight-20261101/local_share_recovery_coverage.csv.gz'
    hashes[str(previous)] = digest(previous)
    frame = pd.read_csv(previous)
    missing = frame[~frame.localPriorShareCandidate].copy()
    joined = []
    for row in missing.itertuples():
        c = select_cover(grouped.get(row.symbol, []), row.date)
        joined.append(dict(date=row.date, symbol=row.symbol, priorCoverFound=c is not None,
            filedAt=c['filedAt'] if c else None, accession=c['accession'] if c else None,
            sourcePath=c['path'] if c else None, sourceSha256=c['sourceSha256'] if c else None,
            classFactCount=len(c['facts']) if c else 0,
            dimensionedFacts=any(f['dimensions'] for f in c['facts']) if c else False,
            maxMeasurementAgeDays=max((pd.Timestamp(row.date)-pd.Timestamp(f['measurementDate'])).days for f in c['facts']) if c else None,
            admittedForModel=False))
    result = pd.DataFrame(joined)
    summary = result.groupby('symbol').agg(targetRows=('date','size'),
        rawCoverRows=('priorCoverFound','sum'), dimensionedRows=('dimensionedFacts','sum'),
        maxMeasurementAgeDays=('maxMeasurementAgeDays','max')).reset_index()
    actions = ROOT / 'output/sp500-v9-actions-recovery-20261101/symbol_audit.csv'
    hashes[str(actions)] = digest(actions)
    summary = summary.merge(pd.read_csv(actions)[['symbol','identity_status','price_basis_certified','blockers']], on='symbol', validate='one_to_one')
    summary['admittedForModel'] = False
    report = dict(status='RAW_SHARE_EVIDENCE_RECOVERED_NOT_MODEL_READY',
        coveredSymbols=int(result.symbol.nunique()), filingSymbolRows=len(covers),
        uniqueFilingDocuments=len({r['path'] for r in covers}),
        parsedFilings=sum(r['status']=='PARSED' for r in covers),
        priorMissingRows=len(result), rawPriorCoverRows=int(result.priorCoverFound.sum()),
        remainingNoCoverRows=int((~result.priorCoverFound).sum()),
        dimensionedRows=int(result.dimensionedFacts.sum()),
        singleUnscopedRows=int((result.priorCoverFound & ~result.dimensionedFacts).sum()),
        maxMeasurementAgeDays=int(result.maxMeasurementAgeDays.max()),
        admittedModelRows=0, generatedV9Predictions=0, comparedAccounts=0,
        historicalSatsCik=1415404, sourceHashes=hashes,
        limitations=['Raw share facts are not economic common-equivalent totals.',
                    'No blind class summation, price multiplication, or historical issuer substitution.',
                    'Existing 524-symbol input population and pre-evaluation mature training still need full replay.',
                    'Retrospective reconstruction, not forecasts stored before the historical order.'])
    for name in ['daily-cover-evidence.csv.gz','symbol-readiness.csv','recovery-summary.json']:
        if (OUT/name).exists():
            raise ValueError('Refusing overwrite: '+name)
    result.to_csv(OUT/'daily-cover-evidence.csv.gz',index=False)
    summary.to_csv(OUT/'symbol-readiness.csv',index=False)
    (OUT/'recovery-summary.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='sourceHashes'},indent=2))


if __name__ == '__main__':
    main()
