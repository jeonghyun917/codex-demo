"""Offline source-faithful IFRS period/basis evidence registry.

Produces evidence candidates, never imports financial facts or converts EPS.
Calendar labels are candidates; only exact CY frame matches approve quarters.
"""
import argparse
import calendar
import json
from collections import Counter
from datetime import date
from pathlib import Path

from bs4 import BeautifulSoup

from quant_free_history_queue import now, period_kind, sha, write_json
from verify_quant_free_primary import verify_directory


def normalized_period(row):
    end = date.fromisoformat(row['end'])
    if row.get('filed') and end > date.fromisoformat(row['filed']):
        raise ValueError('Future period end')
    start = date.fromisoformat(row['start']) if row.get('start') else None
    kind = period_kind(row.get('start'), row['end'])
    days = (end-start).days+1 if start else None
    label = None
    if start and start.year == end.year:
        exact_end = end.day == calendar.monthrange(end.year, end.month)[1]
        if kind == 'ANNUAL' and (start.month, start.day, end.month, end.day) == (1,1,12,31):
            label = 'FY'
        elif kind == 'STANDALONE_QUARTER' and exact_end and end.month in (3,6,9,12) and start.month == end.month-2 and start.day == 1:
            label = f'Q{end.month//3}'
        elif kind == 'HALF_YEAR' and (start.month,start.day,end.month,end.day) == (1,1,6,30):
            label = 'H1'
        elif kind == 'NINE_MONTH' and (start.month,start.day,end.month,end.day) == (1,1,9,30):
            label = '9M'
    fiscal_period = label if label == 'FY' or label and label.startswith('Q') else None
    return dict(kind=kind, calendarYear=end.year, period=label,
                fiscalYear=end.year if fiscal_period else None,
                fiscalPeriod=fiscal_period, days=days)


def strictly_prior(filed, decision):
    return date.fromisoformat(filed) < date.fromisoformat(decision)


def price_eps_compatible(symbol, unit, decision, proofs):
    proof = proofs.get(symbol, {})
    return bool(proof.get('kind') == 'ORDINARY' and proof.get('quotedCurrency')
                and unit == proof['quotedCurrency'] + '/shares'
                and proof.get('availableFrom') and strictly_prior(proof['availableFrom'], decision))


def quarter_frame_proven(original, normalized):
    period = normalized['fiscalPeriod']
    return bool(period and period.startswith('Q') and original.get('frame') ==
                f"CY{normalized['fiscalYear']}{period}")


def build_registry(root, output):
    if output.exists():
        raise FileExistsError(f'Refusing to overwrite {output}')
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    queue = json.loads((root / 'accession-queue.json').read_text(encoding='utf-8'))
    observations = json.loads((root / 'source-observations.json').read_text(encoding='utf-8'))
    output.mkdir(parents=True)
    # Reconciles all 507 original JSON rows and both exact inline-XBRL documents.
    verification = verify_directory(root, output / 'source-reverification.json')
    document = next(x for x in verification['documents'] if x['accession'] == '0001709048-25-000024')
    html = Path(document['path']).read_text(encoding='utf-8')
    text = BeautifulSoup(html, 'html.parser').get_text(' ', strip=True)
    listing = 'Ordinary shares, par value US$0.02 per share GFS The NASDAQ Global Select Market'
    if listing not in text:
        raise ValueError('GFS ordinary-share cover proof changed')
    listing_index = text.index(listing)
    proofs = {
        'GFS': dict(kind='ORDINARY', quotedCurrency=None, availableFrom='2025-03-20',
                    filing=document['accession'], path=document['path'], sha256=document['sha256'],
                    url=document['url'], excerpt=text[listing_index:listing_index+len(listing)],
                    limitation='Par value USD is not itself trading-currency proof; no price-EPS approval.'),
        'AZN': dict(kind='UNPROVEN_FOR_QUOTED_SECURITY', quotedCurrency=None,
                    blocker='USD per-share IFRS unit alone does not establish ADS ratio or quoted-security compatibility.'),
        'CCEP': dict(kind='UNPROVEN_FOR_QUOTED_SECURITY', quotedCurrency=None,
                     blocker='EUR EPS must not be combined with USD quote without dated currency/share proof.'),
        'FER': dict(kind='UNPROVEN_FOR_QUOTED_SECURITY', quotedCurrency=None,
                    blocker='EUR EPS must not be combined with USD quote without dated currency/share proof.'),
        'TRI': dict(kind='UNPROVEN_FOR_QUOTED_SECURITY', quotedCurrency=None,
                    blocker='USD per-share IFRS unit alone does not establish quoted-security share compatibility.'),
    }
    by_key = {(x['symbol'], x['accession']): x for x in queue}
    inline_by_key = {}
    for match in verification['matches']:
        o = match['sourceObservation']
        key = (o['symbol'], o['original']['accn'], o['tag'], o['unit'],
               o['original'].get('start'), o['original']['end'])
        inline_by_key[key] = dict(path=match['documentPath'], sha256=match['documentSha256'],
                                  contexts=[x['contextRef'] for x in match['inlineMatches']])
    candidates = []
    for row in observations:
        original = row['original']
        entry = by_key[(row['symbol'], original['accn'])]
        if original['filed'] != entry['filed']:
            raise ValueError('Filing date mismatch')
        normalized = normalized_period(original)
        decisions = entry['affectedDecisions']
        if not all(strictly_prior(original['filed'], d) for d in decisions):
            raise ValueError('Non-prior accession decision')
        frame_proven = quarter_frame_proven(original, normalized)
        key = (row['symbol'], original['accn'], row['tag'], row['unit'],
               original.get('start'), original['end'])
        candidates.append(dict(
            **row, normalizedCalendarRange=normalized,
            periodEvidence=dict(source='ORIGINAL_OBSERVATION_START_END_NOT_SEC_FY_FP',
                                originalFiscalYear=original.get('fy'), originalFiscalPeriod=original.get('fp'),
                                frame=original.get('frame'), exactQuarterFrameProven=frame_proven,
                                annualRangeProven=normalized['period'] == 'FY',
                                instantaneousFiscalPeriod=None,
                                primaryInlineProof=inline_by_key.get(key)),
            affectedDecisions=decisions,
            sameUnitGrowthCandidate=normalized['kind'] in ('ANNUAL', 'STANDALONE_QUARTER'),
            priceEpsApproved=False,
            priceEpsBlockers=['QUOTE_CURRENCY_AND_SHARE_BASIS_NOT_FULLY_PROVEN'],
            acceptedForImport=False))
    accession_reports = []
    for entry in queue:
        rows = [x for x in candidates if x['symbol'] == entry['symbol'] and
                x['original']['accn'] == entry['accession']]
        quarter_rows = [x for x in rows if x['normalizedCalendarRange']['kind'] == 'STANDALONE_QUARTER']
        accession_reports.append(dict(
            symbol=entry['symbol'], accession=entry['accession'], filed=entry['filed'],
            reportDate=entry['reportDate'], form=entry['form'],
            primaryDocumentUrl=entry['primaryDocumentUrl'],
            affectedDecisions=entry['affectedDecisions'], observations=len(rows),
            periodKinds=dict(Counter(x['normalizedCalendarRange']['kind'] for x in rows)),
            quarterObservations=len(quarter_rows),
            exactQuarterFrameProven=sum(x['periodEvidence']['exactQuarterFrameProven'] for x in quarter_rows),
            missingQuarterFrame=sum(not x['periodEvidence']['exactQuarterFrameProven'] for x in quarter_rows),
            units=sorted({x['unit'] for x in rows}),
            support='SOURCE_PERIOD_CANDIDATES_ONLY_NATIVE_SELECTION_AND_REGRESSION_REQUIRED',
            priceEpsApproved=False))
    write_json(output/'candidates.json', candidates)
    write_json(output/'accession-support.json', accession_reports)
    write_json(output/'issuer-basis.json', proofs)
    result = dict(classification='OFFLINE_IFRS_PERIOD_BASIS_CANDIDATES_NO_IMPORT_NO_MODEL_CLAIM',
                  generatedAt=now(), inputManifestPath=str(root/'manifest.json'),
                  inputManifestSha256=sha(root/'manifest.json'),
                  sourceFiles=manifest['sourceFiles'], scriptSha256=sha(Path(__file__)),
                  observations=len(candidates), uniqueAccessions=len(queue),
                  historicalReferences=sum(x['referenceCount'] for x in queue),
                  issuers={s: dict(observations=sum(x['symbol']==s for x in candidates),
                                  quarterObservations=sum(x['symbol']==s and x['normalizedCalendarRange']['kind']=='STANDALONE_QUARTER' for x in candidates),
                                  exactQuarterFrameProven=sum(x['symbol']==s and x['periodEvidence']['exactQuarterFrameProven'] for x in candidates))
                           for s in proofs},
                  primaryInlineMatched=verification['sourceObservationsMatched'],
                  databaseWrites=0, networkRequests=0, newSourceDownloads=0,
                  acceptedModelRows=0, priceEpsApprovedRows=0,
                  outputs={p.name:sha(p) for p in sorted(output.iterdir())})
    write_json(output/'manifest.json', result)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=Path('tmp/quant-free-history-queue-primary-approved-20261031'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build_registry(args.input, args.output), indent=2))
