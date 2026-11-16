"""Verify archived free foreign financial evidence without database/model writes.

The queue is evidence only: IFRS taxonomy names are never mapped to application
fields. Amounts, units and periods retain their original SEC meaning. Optional
primary-document download is bounded to two GFS documents and stops on 401/403/429.
"""
import argparse
import hashlib
import json
import re
import urllib.error
import urllib.request
from collections import Counter
from datetime import date, datetime, timezone
from pathlib import Path

FOREIGN = {'GFS': 1709048, 'AZN': 901832, 'CCEP': 1650107, 'FER': 1468522, 'TRI': 1075124}
TAGS = {
    'Revenue', 'RevenueFromContractsWithCustomers', 'ProfitLoss',
    'ProfitLossAttributableToOwnersOfParent', 'ProfitLossFromOperatingActivities',
    'OperatingProfitLossOperating', 'DilutedEarningsLossPerShare', 'Assets',
    'Liabilities', 'Equity', 'EquityAttributableToOwnersOfParent',
    'CashFlowsFromUsedInOperatingActivities',
}


def now():
    return datetime.now(timezone.utc).isoformat()


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def period_kind(start, end):
    if not start:
        return 'INSTANT'
    days = (date.fromisoformat(end) - date.fromisoformat(start)).days + 1
    if days <= 0:
        raise ValueError('Invalid duration')
    for low, high, kind in [(70, 110, 'STANDALONE_QUARTER'), (150, 210, 'HALF_YEAR'),
                            (240, 300, 'NINE_MONTH'), (330, 380, 'ANNUAL')]:
        if low <= days <= high:
            return kind
    return 'OTHER_DURATION'


def read_verified(source, cik):
    path = Path(source['path'])
    if sha(path) != source['sha256']:
        raise ValueError(f'SHA256 mismatch: {path}')
    payload = json.loads(path.read_text(encoding='utf-8'))
    if int(payload['cik']) != cik:
        raise ValueError(f'CIK mismatch: {path}')
    return payload


def selected_observations(payload, accession, filed):
    result = []
    for namespace, tags in payload['facts'].items():
        if namespace != 'ifrs-full':
            continue
        for tag, concept in tags.items():
            if tag not in TAGS:
                continue
            for unit, rows in concept['units'].items():
                for original in rows:
                    if original.get('accn') != accession:
                        continue
                    if original['filed'] != filed:
                        raise ValueError(f'Inconsistent filing date for {accession}')
                    if original['end'] > filed:
                        raise ValueError(f'Future period end for {accession}')
                    result.append(dict(taxonomy=namespace, tag=tag, label=concept.get('label'),
                                       description=concept.get('description'), unit=unit,
                                       periodKind=period_kind(original.get('start'), original['end']),
                                       original=original, acceptedForModel=False))
    return result


def filing_url(cik, accession, document):
    if not re.fullmatch(r'\d{10}-\d{2}-\d{6}', accession):
        raise ValueError('Invalid accession')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+\.htm[l]?', document) or '..' in document:
        raise ValueError('Invalid primary document')
    return f'https://www.sec.gov/Archives/edgar/data/{cik}/{accession.replace("-", "")}/{document}'


def write_json(path, payload):
    with Path(path).open('x', encoding='utf-8') as handle:
        json.dump(payload, handle, indent=2, allow_nan=False)


def download_gfs_documents(entries, output):
    # Explicitly known, high-impact annual + recent quarterly filings only.
    targets = ['0001709048-25-000024', '0001709048-25-000071']
    receipts = []
    for accession in targets:
        entry = next(x for x in entries if x['symbol'] == 'GFS' and x['accession'] == accession)
        path = output / f'GFS-{accession}.html'
        receipt = dict(url=entry['primaryDocumentUrl'], requestedAt=now(), accession=accession)
        headers = {'User-Agent': 'QuantLocalResearch/1.0', 'Accept': 'text/html', 'Accept-Encoding': 'identity'}
        receipt['requestHeaders'] = headers
        try:
            # No custom opener, alternate host, authentication, proxy or retry.
            request = urllib.request.Request(receipt['url'], headers=headers)
            with urllib.request.urlopen(request, timeout=25) as response:
                receipt.update(status=response.status, finalUrl=response.url,
                               responseHeaders=dict(response.headers.items()))
                if response.status != 200 or not response.url.startswith('https://www.sec.gov/Archives/'):
                    raise ValueError('Unexpected primary filing response')
                body = response.read(15_000_001)
            if len(body) > 15_000_000 or b'<html' not in body.lower():
                raise ValueError('Filing is oversized or not HTML')
            with path.open('xb') as handle:
                handle.write(body)
            receipt.update(path=str(path), bytes=len(body), sha256=sha(path))
        except urllib.error.HTTPError as exc:
            receipt.update(status=exc.code, error=str(exc), responseHeaders=dict(exc.headers.items()))
        except (OSError, ValueError) as exc:
            receipt['error'] = str(exc)
        receipt['retrievedAt'] = now()
        receipts.append(receipt)
        if receipt.get('status') in (401, 403, 429) or receipt.get('error'):
            break
    return receipts


def build_queue(coverage_path, output, fetch_primary=False):
    coverage = json.loads(coverage_path.read_text(encoding='utf-8'))
    output.mkdir(parents=True, exist_ok=False)
    sources, entries, observations = [], [], []
    # GFS first because its 39 decision/accession references are largest.
    for symbol, cik in FOREIGN.items():
        selected = [x for x in coverage['sourceFiles'] if x['symbol'] == symbol]
        facts_source = next(x for x in selected if x['url'].startswith('https://data.sec.gov/api/'))
        submissions_source = next(x for x in selected if x['url'].startswith('https://data.sec.gov/submissions/'))
        facts, submissions = read_verified(facts_source, cik), read_verified(submissions_source, cik)
        sources.extend(selected)
        recent = submissions['filings']['recent']
        by_accession = {a: {k: v[i] for k, v in recent.items()} for i, a in enumerate(recent['accessionNumber'])}
        required = [x for x in coverage['importQueue'] if x['symbol'] == symbol]
        for item in required:
            accession = item['accessionNumber']
            filing = by_accession.get(accession)
            if filing is None:
                raise ValueError(f'Missing submissions filing: {symbol} {accession}')
            if filing['filingDate'] != item['filedAt'] or filing['form'] not in item['forms']:
                raise ValueError(f'Submissions mismatch: {symbol} {accession}')
            decisions = [x['decision'] for x in coverage['coverage']
                         if x['symbol'] == symbol and accession in x['requiredAccessions']]
            if any(filing['filingDate'] >= d for d in decisions):
                raise ValueError(f'Not prior-filed: {symbol} {accession}')
            rows = selected_observations(facts, accession, filing['filingDate'])
            for row in rows:
                row.update(symbol=symbol, cik=cik, sourcePath=facts_source['path'],
                           sourceSha256=facts_source['sha256'], sourceUrl=facts_source['url'])
            observations.extend(rows)
            entries.append(dict(symbol=symbol, cik=cik, accession=accession,
                                filed=filing['filingDate'], reportDate=filing['reportDate'],
                                form=filing['form'], primaryDocument=filing['primaryDocument'],
                                primaryDocumentUrl=filing_url(cik, accession, filing['primaryDocument']),
                                affectedDecisions=decisions, referenceCount=len(decisions),
                                originalObservationCount=len(rows),
                                periods=dict(Counter(x['periodKind'] for x in rows)),
                                units=sorted({x['unit'] for x in rows}),
                                sourceAlreadyArchived=True, acceptedForModel=False,
                                nextAction='VERIFY_IFRS_SEMANTICS_PERIOD_CURRENCY_AND_SHARE_BASIS',
                                blockers=['NO_APPROVED_IFRS_MAPPING'] +
                                (['NATIVE_6K_UNSUPPORTED'] if filing['form'].startswith('6-K') else []) +
                                ['EPS_PRICE_SHARE_BASIS_UNPROVEN_NO_CONVERSION']))
    receipts = download_gfs_documents(entries, output) if fetch_primary else []
    write_json(output / 'accession-queue.json', entries)
    write_json(output / 'source-observations.json', observations)
    write_json(output / 'primary-downloads.json', receipts)
    summary = {symbol: dict(uniqueAccessions=len([x for x in entries if x['symbol'] == symbol]),
                            historicalReferences=sum(x['referenceCount'] for x in entries if x['symbol'] == symbol),
                            sourceObservations=len([x for x in observations if x['symbol'] == symbol]))
               for symbol in FOREIGN}
    manifest = dict(classification='VERIFIED_FOREIGN_SOURCE_QUEUE_NOT_MAPPED_NOT_IMPORTED_NOT_PIT',
                    generatedAt=now(), inputCoveragePath=str(coverage_path), inputCoverageSha256=sha(coverage_path),
                    sourceFiles=sources, issuers=summary, databaseWrites=0,
                    uniqueAccessions=len(entries), historicalReferences=sum(x['referenceCount'] for x in entries),
                    archivedSourceObservations=len(observations),
                    newSourceDownloads=sum(x.get('status') == 200 and 'sha256' in x for x in receipts),
                    newPublicRequests=len(receipts), newlyAcceptedModelRows=0,
                    stoppedForAccessOrThrottle=any(x.get('status') in (401,403,429) for x in receipts),
                    outputs={p.name: sha(p) for p in output.iterdir() if p.is_file()},
                    codeSha256=sha(Path(__file__)),
                    limitations=['Original filings acquired retrospectively; not historical receipt evidence.',
                                 'Observations already existed in source archive; not new model-ready rows.',
                                 'Tag names are preserved, not treated as verified financial-field equivalence.',
                                 'No FX, ADS, half-year subtraction, fabricated analyst/news or zero fills.',
                                 'No lower-MAE, new independent date or model-validation claim.'])
    write_json(output / 'manifest.json', manifest)
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--coverage', type=Path, default=Path('tmp/quant-historical-source-staged-v4-20261031/coverage.json'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--fetch-primary-gfs', action='store_true')
    args = parser.parse_args()
    result = build_queue(args.coverage, args.output, args.fetch_primary_gfs)
    print(json.dumps({k: result[k] for k in ['uniqueAccessions', 'historicalReferences',
                                          'archivedSourceObservations','newSourceDownloads','newPublicRequests',
                                          'newlyAcceptedModelRows','stoppedForAccessOrThrottle']}))
