"""Offline primary inline-XBRL comparison; no mapping, network or DB access."""
import argparse
import json
from collections import Counter
from decimal import Decimal
from pathlib import Path

from bs4 import BeautifulSoup

from quant_free_history_queue import TAGS, now, sha, write_json


def inline_value(text, attrs):
    if attrs.get('xsi:nil') in ('true', '1'):
        raise ValueError('Nil fact')
    transform = attrs.get('format')
    if transform == 'ixt:fixed-zero':
        value = Decimal(0)
    elif transform in (None, 'ixt:num-dot-decimal'):
        value = Decimal(text.replace(',', '').replace('\u00a0', '').replace(' ', '').strip())
    else:
        raise ValueError(f'Unsupported inline transform: {transform}')
    if attrs.get('sign') == '-':
        value = -value
    value *= Decimal(10) ** int(attrs.get('scale', '0'))
    if not value.is_finite():
        raise ValueError('Nonfinite fact')
    return value


def parse_inline(html):
    soup = BeautifulSoup(html, 'html.parser')
    contexts, units, rows, errors = {}, {}, [], []
    for item in soup.find_all('xbrli:context'):
        if item.find('xbrldi:explicitmember') or item.find('xbrldi:typedmember'):
            continue
        def content(tag):
            node = item.find(tag)
            return node.get_text(strip=True) if node else None
        contexts[item['id']] = dict(cik=int(content('xbrli:identifier')),
                                    start=content('xbrli:startdate'),
                                    end=content('xbrli:enddate') or content('xbrli:instant'))
    for item in soup.find_all('xbrli:unit'):
        divide = item.find('xbrli:divide')
        if divide:
            numerator = divide.find('xbrli:unitnumerator').find('xbrli:measure').get_text(strip=True).split(':')[-1]
            denominator = divide.find('xbrli:unitdenominator').find('xbrli:measure').get_text(strip=True).split(':')[-1]
            units[item['id']] = numerator + '/' + denominator
        else:
            measures = item.find_all('xbrli:measure')
            if len(measures) == 1:
                units[item['id']] = measures[0].get_text(strip=True).split(':')[-1]
    for item in soup.find_all('ix:nonfraction'):
        name = item.get('name', '')
        if not name.startswith('ifrs-full:') or name.split(':')[-1] not in TAGS:
            continue
        context = contexts.get(item.get('contextref'))
        if not context:
            continue
        try:
            value = inline_value(item.get_text(), item.attrs)
            rows.append(dict(**context, name=name, unit=units[item['unitref']], value=str(value),
                             contextRef=item['contextref'], inlineId=item.get('id'),
                             originalText=item.get_text(), scale=item.get('scale', '0'),
                             sign=item.get('sign'), decimals=item.get('decimals'), format=item.get('format')))
        except (ValueError, KeyError, ArithmeticError) as exc:
            errors.append(dict(name=name, contextRef=item.get('contextref'), error=str(exc)))
    return rows, errors


def verify_directory(root, output):
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    for filename, expected in manifest['outputs'].items():
        if sha(root / filename) != expected:
            raise ValueError(f'Output SHA mismatch: {filename}')
    observations = json.loads((root / 'source-observations.json').read_text(encoding='utf-8'))
    facts_by_path = {}
    for source in manifest['sourceFiles']:
        if sha(source['path']) != source['sha256']:
            raise ValueError(f'Original archive SHA mismatch: {source["path"]}')
        if '/api/xbrl/companyfacts/' in source['url']:
            facts_by_path[source['path']] = json.loads(Path(source['path']).read_text(encoding='utf-8'))
    for row in observations:
        facts = facts_by_path[row['sourcePath']]
        original_rows = facts['facts'][row['taxonomy']][row['tag']]['units'][row['unit']]
        if int(facts['cik']) != row['cik'] or row['original'] not in original_rows:
            raise ValueError('Observation does not match original source archive')
    queue = json.loads((root / 'accession-queue.json').read_text(encoding='utf-8'))
    receipts = json.loads((root / 'primary-downloads.json').read_text(encoding='utf-8'))
    documents, matches = [], []
    for receipt in receipts:
        if receipt.get('status') != 200 or 'path' not in receipt:
            continue
        path = Path(receipt['path'])
        if sha(path) != receipt['sha256']:
            raise ValueError('Primary document SHA mismatch')
        accession = receipt['accession']
        entry = next(x for x in queue if x['accession'] == accession and x['symbol'] == 'GFS')
        inline, errors = parse_inline(path.read_text(encoding='utf-8'))
        if any(x['cik'] != entry['cik'] for x in inline):
            raise ValueError('Primary CIK mismatch')
        selected = [x for x in observations if x['symbol'] == 'GFS' and x['original']['accn'] == accession]
        unmatched = []
        for row in selected:
            fact = row['original']
            candidates = [x for x in inline if x['name'] == row['taxonomy'] + ':' + row['tag']
                          and x['start'] == fact.get('start') and x['end'] == fact['end']
                          and x['unit'] == row['unit'] and Decimal(x['value']) == Decimal(str(fact['val']))]
            if not candidates:
                unmatched.append(row)
            else:
                matches.append(dict(symbol='GFS', accession=accession, filed=entry['filed'],
                                    documentPath=str(path), documentSha256=receipt['sha256'],
                                    sourceUrl=receipt['url'], sourceObservation=row,
                                    inlineMatches=candidates, acceptedForModel=False))
        text = BeautifulSoup(path.read_text(encoding='utf-8'), 'html.parser').get_text(' ', strip=True)
        phrase = 'Ordinary shares, par value'
        index = text.find(phrase)
        documents.append(dict(accession=accession, filed=entry['filed'], reportDate=entry['reportDate'],
                              path=str(path), sha256=receipt['sha256'], url=receipt['url'],
                              matchedObservations=len(selected)-len(unmatched),
                              unmatchedObservations=unmatched, parseErrors=errors,
                              defaultContextSelectedInlineFacts=len(inline),
                              listingCoverExcerpt=text[max(0,index-50):index+230] if index >= 0 else None))
    result = dict(classification='PRIMARY_DOCUMENT_COMPARISON_NOT_MAPPED_NOT_PIT', verifiedAt=now(),
                  queueManifestSha256=sha(root / 'manifest.json'), verifierSha256=sha(Path(__file__)),
                  primaryDocuments=len(documents),
                  archivedSourceObservationsVerified=len(observations),
                  sourceObservationsMatched=len(matches),
                  unmatchedObservations=sum(len(x['unmatchedObservations']) for x in documents),
                  inlineParseErrors=sum(len(x['parseErrors']) for x in documents),
                  matchedPeriodKinds=dict(Counter(x['sourceObservation']['periodKind'] for x in matches)),
                  documents=documents, matches=matches,
                  databaseWrites=0, newPublicRequests=0, newlyAcceptedModelRows=0,
                  limitations=['Matching is same-source reconciliation, not independent accounting audit.',
                               'No IFRS-to-application mapping, EPS conversion or model usability asserted.',
                               'Filing dates come from original submissions; receipt dates are actual retrieval.'])
    write_json(output, result)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = verify_directory(args.root, args.output)
    print(json.dumps({k: result[k] for k in ['primaryDocuments','sourceObservationsMatched',
                                          'unmatchedObservations','inlineParseErrors','matchedPeriodKinds']}))
