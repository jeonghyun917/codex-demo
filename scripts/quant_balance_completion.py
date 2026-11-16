"""Prespecified exact-accession balance/CFO completion; no I/O or model access."""
import collections
import copy
import json
import math
from decimal import Decimal

PRIORITIES = {
    'assets': ['Assets'],
    'liabilities': ['Liabilities'],
    'equity': ['StockholdersEquity', 'StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest'],
    'operatingCashFlow': ['NetCashProvidedByUsedInOperatingActivities', 'NetCashProvidedByUsedInOperatingActivitiesContinuingOperations'],
}


def _cik(value):
    return str(int(str(value)))


def complete_financials(rowsBySymbol, archivesBySymbolCik):
    """Return a deep-copied DTO map and audit; conflicting source bases fail closed."""
    archives = {}
    for (symbol, cik), archive in archivesBySymbolCik.items():
        key = (symbol, _cik(cik))
        if key in archives:
            raise ValueError('Duplicate normalized issuer archive')
        if _cik(archive.get('cik')) != key[1]:
            raise ValueError('Issuer/archive mismatch')
        archives[key] = archive
    result = copy.deepcopy(rowsBySymbol)
    counts = collections.Counter(priorityUpgrades=0, completedFields=0, rows=0)
    changes = []
    for symbol, rows in result.items():
        for row in rows:
            if row['symbol'] != symbol:
                raise ValueError('Symbol/map mismatch')
            counts['rows'] += 1
            archive = archives.get((symbol, _cik(row['cik'])))
            raw = json.loads(row.get('rawJson') or '{}')
            evidence = raw.setdefault('selectedFieldEvidence', {})
            receipt = []
            for field, order in PRIORITIES.items():
                candidates_by_concept = {}
                if archive is not None and row.get('currency') == 'USD':
                    for concept in order:
                        observations = archive.get('facts', {}).get('us-gaap', {}).get(concept, {}).get('units', {}).get('USD', [])
                        exact = []
                        for obs in observations:
                            if not all(obs.get(a) == row.get(b) for a,b in [('accn','accessionNumber'),('filed','filedAt'),('form','form'),('end','endDate')]):
                                continue
                            if field == 'operatingCashFlow':
                                if not row.get('startDate') or obs.get('start') != row['startDate']:
                                    continue
                            elif 'start' in obs:
                                continue
                            exact.append(obs)
                        if not exact:
                            continue
                        values = {Decimal(str(obs['val'])) for obs in exact}
                        if len(values) != 1 or not all(v.is_finite() for v in values):
                            raise ValueError('Conflicting exact source concept values: '+str((symbol,field,concept)))
                        candidates_by_concept[concept] = (next(iter(values)), exact[0])
                old = row.get(field)
                old_concept = evidence.get(field, {}).get('concept')
                if old is not None and old_concept in candidates_by_concept:
                    if Decimal(str(old)) != candidates_by_concept[old_concept][0]:
                        raise ValueError('Existing non-null same-concept conflict: '+str((symbol,field,old_concept)))
                if not candidates_by_concept:
                    counts['preservedWithoutNewProof'] += int(old is not None)
                    continue
                concept = next(c for c in order if c in candidates_by_concept)
                value, obs = candidates_by_concept[concept]
                different = old is not None and Decimal(str(old)) != value
                if different:
                    if old_concept not in order or order.index(concept) >= order.index(old_concept):
                        raise ValueError('Non-null lower/unknown source-basis conflict: '+str((symbol,field,old_concept,concept)))
                    counts['priorityUpgrades'] += 1
                elif old is not None and old_concept in order and order.index(concept) > order.index(old_concept):
                    continue
                numeric = float(value)
                if not math.isfinite(numeric):
                    raise ValueError('Source value cannot be represented finitely')
                row[field] = numeric
                proof = dict(concept=concept,taxonomy='us-gaap',unit='USD',startDate=row['startDate'] if field=='operatingCashFlow' else None,endDate=row['endDate'],periodDays=row.get('periodDays') if field=='operatingCashFlow' else None,sourceFiscalYear=obs.get('fy'),sourceFiscalPeriod=obs.get('fp'),sourceObservationJson=json.dumps(obs,sort_keys=True))
                evidence[field] = proof
                counts['completedFields'] += 1
                counts['filled_'+field] += int(old is None)
                change = dict(symbol=symbol,cik=row['cik'],accessionNumber=row['accessionNumber'],filedAt=row['filedAt'],field=field,before=old,after=numeric,oldConcept=old_concept,newConcept=concept,reason='HIGHER_PRIORITY_SOURCE_BASIS' if different else 'EXACT_SAME_FILING_SOURCE')
                changes.append(change);receipt.append(change)
            raw['balanceCompletion'] = dict(policy='SAME_CIK_ACCESSION_FILED_FORM_USD_END_INSTANT_BALANCE_EXACT_DURATION_CFO_NO_DERIVATION',changes=receipt)
            row['rawJson'] = json.dumps(raw,sort_keys=True)
    return result, dict(counts,changes=changes)
