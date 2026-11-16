"""Offline, source-proved US-GAAP statement recovery. No network/DB/model access.

The current companyfacts downloads are retrospective source reconstructions,
not historical captures. Filing cutoffs do not remove that limitation.
"""
import argparse
from collections import Counter, defaultdict
from datetime import date, datetime, timezone
import hashlib
import json
import math
from pathlib import Path

CUTOFF = date(2020, 8, 28)
MIN_START = date(2013, 1, 1)
FIELDS = {
    'epsDiluted': ('USD/shares', ('EarningsPerShareDiluted',)),
    'revenue': ('USD', ('RevenueFromContractWithCustomerExcludingAssessedTax',
                       'Revenues', 'SalesRevenueNet', 'RevenuesNetOfInterestExpense',
                       'RevenueFromContractWithCustomerIncludingAssessedTax')),
    'netIncome': ('USD', ('NetIncomeLoss', 'ProfitLoss')),
    'operatingIncome': ('USD', ('OperatingIncomeLoss',)),
}
LEGACY_SHA = 'ec0616296890c8861dfd9d3c3c9f8862c1d77b928c9814bae47ee9549713bc85'
FORMS = {'10-K', '10-K/A', '10-Q', '10-Q/A'}
SCOPE = 'RETROSPECTIVE_CURRENT_COMPANYFACTS_RECONSTRUCTION_NOT_POINT_IN_TIME_ARCHIVE'


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_verified_archive(record):
    """Read only after matching the inventory's original archive fingerprint."""
    path = Path(record['archivePath'])
    payload = path.read_bytes()
    if hashlib.sha256(payload).hexdigest() != record['sha256'].lower():
        raise ValueError(f'SHA mismatch: {path}')
    facts = json.loads(payload)
    if int(facts['cik']) != int(record['cik']):
        raise ValueError(f'CIK mismatch: {path}')
    return facts


def _whole_year_offset(earlier, anchor):
    """Calendar anniversary ±7d permits 52/53-week fiscal calendars, not Q shifts."""
    if earlier == anchor:
        return 0
    years = round((anchor - earlier).days / 365.2425)
    if years <= 0:
        return None
    try:
        anniversary = earlier.replace(year=earlier.year + years)
    except ValueError:  # leap-day comparative
        anniversary = earlier.replace(year=earlier.year + years, day=28)
    return years if abs((anniversary - anchor).days) <= 7 else None


def recover_company(companyfacts, symbol, expected_cik, archive_sha):
    """Return (statement rows, source-only counters), preserving original raw facts.

    Fields never cross accession/filed/start/end. A priority concept that exists
    but conflicts becomes null; an alternate concept cannot rescue it. All
    observed alternatives remain in proof. Duration-bearing allowed income
    concepts, including YTD, anchor filing report end; shares/instants cannot.
    """
    if int(companyfacts['cik']) != int(expected_cik):
        raise ValueError(f'CIK mismatch: {symbol}')
    cik = str(int(expected_cik)).zfill(10)
    stats = Counter({key: 0 for key in (
        'observations', 'rejectedDateOrForm', 'rejectedDuration',
        'rejectedAmbiguousPhase', 'rejectedInvalidValue', 'conflictingFields',
        'rows', 'annualRows', 'quarterRows', 'directQ4Rows', 'proofObservations')})
    filings = defaultdict(list)
    facts = companyfacts.get('facts', {}).get('us-gaap', {})
    for field, (unit, concepts) in FIELDS.items():
        for concept in concepts:
            if concept == 'RevenueFromContractWithCustomerIncludingAssessedTax' and symbol != 'DOW':
                continue  # Bounded source-metadata ruling; not a global alias.
            for raw in facts.get(concept, {}).get('units', {}).get(unit, []):
                stats['observations'] += 1
                try:
                    start = date.fromisoformat(raw['start'])
                    end = date.fromisoformat(raw['end'])
                    filed = date.fromisoformat(raw['filed'])
                    days = (end - start).days + 1
                    if (raw.get('form') not in FORMS or not raw.get('accn')
                            or start < MIN_START or end < start or end >= filed
                            or filed > CUTOFF):
                        raise ValueError('date/form')
                except (KeyError, ValueError, TypeError):
                    stats['rejectedDateOrForm'] += 1
                    continue
                if not 70 <= days <= 380:
                    stats['rejectedDuration'] += 1
                    continue
                value = raw.get('val')
                if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
                    stats['rejectedInvalidValue'] += 1
                    continue
                filings[(raw['accn'], raw['filed'])].append(
                    (field, concept, unit, raw, start, end, days))

    rows = []
    for (accession, filed), observations in sorted(filings.items()):
        anchor = max(o[5] for o in observations)
        fps = {o[3].get('fp') for o in observations}
        fys = {o[3].get('fy') for o in observations}
        forms = {o[3].get('form') for o in observations}
        if (len(fps) != 1 or len(fys) != 1 or len(forms) != 1
                or next(iter(fps)) not in {'Q1', 'Q2', 'Q3', 'FY'}
                or not isinstance(next(iter(fys)), int)):
            stats['rejectedAmbiguousPhase'] += len(observations)
            continue
        fp, fy, form = next(iter(fps)), next(iter(fys)), next(iter(forms))
        annual = [o for o in observations if 330 <= o[6] <= 380 and fp == 'FY']
        annual_ends = {o[5] for o in annual}
        periods = defaultdict(list)
        for observation in observations:
            periods[(observation[4], observation[5])].append(observation)
        for (start, end), period in sorted(periods.items()):
            days = (end - start).days + 1
            year_offset = _whole_year_offset(end, anchor)
            phase = None
            rule = None
            if 330 <= days <= 380 and fp == 'FY' and year_offset is not None:
                phase, rule = 'FY', 'EXPLICIT_ANNUAL_DURATION'
            elif 70 <= days <= 110:
                if fp in {'Q1', 'Q2', 'Q3'} and year_offset is not None:
                    phase = fp
                    rule = 'CURRENT_REPORT_END' if end == anchor else 'WHOLE_YEAR_SAME_PHASE_COMPARATIVE'
                elif fp == 'FY' and end in annual_ends and year_offset is not None:
                    phase, rule = 'Q4', 'DIRECT_QUARTER_MATCHES_SAME_ACCESSION_ANNUAL_END'
            else:
                stats['rejectedDuration'] += len(period)
                continue
            if phase is None:
                stats['rejectedAmbiguousPhase'] += len(period)
                continue
            row = dict(id=None, symbol=symbol, cik=cik, accessionNumber=accession,
                       filedAt=filed, startDate=start.isoformat(), endDate=end.isoformat(),
                       periodDays=days, currency='USD', fiscalPeriod=phase,
                       fiscalYear=fy - year_offset, form=form, epsUnit='USD/shares',
                       _epsConcept=None, _fieldConcepts={}, _proofs={})
            row['_phaseEvidence'] = {
                'originalFP': [fp], 'originalFY': [fy], 'reportEndAnchor': anchor.isoformat(),
                'rule': rule, 'wholeYearOffset': year_offset,
                'annualEndProofs': [dict(concept=o[1], unit=o[2], archiveSha256=archive_sha,
                                         observation=o[3]) for o in annual if o[5] == end]
                    if phase == 'Q4' else [],
            }
            for field, (unit, concepts) in FIELDS.items():
                candidates = defaultdict(list)
                for o in period:
                    if o[0] == field:
                        candidates[o[1]].append(o[3])
                selected = next((concept for concept in concepts if concept in candidates), None)
                row[field] = None
                if selected is None:
                    stats['missing_' + field] += 1
                    continue
                raw = candidates[selected]
                values = {o['val'] for o in raw}
                conflict = len(values) != 1
                row['_fieldConcepts'][field] = selected
                if field == 'epsDiluted':
                    row['_epsConcept'] = selected
                row['_proofs'][field] = {
                    'concept': selected, 'unit': unit, 'archiveSha256': archive_sha,
                    'observations': raw, 'conflict': conflict,
                    'alternatives': {k: v for k, v in candidates.items() if k != selected},
                }
                stats['proofObservations'] += sum(len(v) for v in candidates.values())
                if conflict:
                    stats['conflictingFields'] += 1
                    stats['conflict_' + field] += 1
                    stats['missing_' + field] += 1
                else:
                    row[field] = raw[0]['val']
            rows.append(row)
            stats['annualRows' if phase == 'FY' else 'quarterRows'] += 1
            stats['directQ4Rows'] += int(phase == 'Q4')
    rows.sort(key=lambda r: (r['filedAt'], r['endDate'], r['startDate'], r['accessionNumber']))
    stats['rows'] = len(rows)
    return rows, dict(stats)


def prepare(root, output):
    """Verify 30 manifest originals plus pinned legacy DIS and emit a fresh pack."""
    root, output = Path(root).resolve(), Path(output).resolve()
    if output.exists():
        raise FileExistsError(f'Recovery output already exists; choose a new pack directory: {output}')
    inventory = root / 'tmp/quant-eps-primary-20261101-v1'
    records, manifests = [], []
    for name in ('manifest-first3.json', 'manifest-rest.json'):
        path = inventory / name
        manifests.append({'path': str(path), 'sha256': sha256(path)})
        records.extend(json.loads(path.read_text(encoding='utf-8'))['records'])
    records.append({
        'symbol': 'DIS', 'cik': '0001001039',
        'archivePath': str(root / 'tmp/quant-long-history-inputs-20261031-v1/DIS-legacy-companyfacts-original.json'),
        'sha256': LEGACY_SHA, 'sourceKind': 'EXISTING_LEGACY_PRIMARY_ARCHIVE',
        'url': 'https://data.sec.gov/api/xbrl/companyfacts/CIK0001001039.json',
        'hashOrigin': 'Pinned local original fingerprint, same inventory convention as QuantEpsRecoveryPrepare.java',
    })
    financials, source_receipts, totals = defaultdict(list), [], Counter()
    for record in records:
        facts = load_verified_archive(record)
        rows, stats = recover_company(facts, record['symbol'], record['cik'], record['sha256'])
        financials[record['symbol']].extend(rows)
        totals.update(stats)
        source_receipts.append(dict(record, verifiedSha256=record['sha256'],
                                    verifiedCik=str(facts['cik']).zfill(10), stats=stats))
    for rows in financials.values():
        rows.sort(key=lambda r: (r['filedAt'], r['endDate'], r['startDate'], r['cik'], r['accessionNumber']))
    output.mkdir(parents=True, exist_ok=False)
    manifest = {'scope': SCOPE, 'noDatabaseWrites': True, 'noNetworkRequests': True,
                'inventoryManifests': manifests, 'records': source_receipts}
    def write(name, data):
        (output / name).write_text(json.dumps(data, indent=2, sort_keys=True, allow_nan=False) + '\n', encoding='utf-8')
    write('financials.json', dict(financials))
    write('source-manifest.json', manifest)
    receipt = {
        'scope': SCOPE, 'createdUtc': datetime.now(timezone.utc).isoformat(),
        'noDatabaseWrites': True, 'noNetworkRequests': True, 'noModelOrOutcomeAccess': True,
        'cutoffFiledInclusive': CUTOFF.isoformat(), 'minimumPeriodStartInclusive': MIN_START.isoformat(),
        'sourceCount': len(records), 'symbolCount': len(financials), 'totals': dict(totals),
        'bySymbol': {symbol: dict(rows=len(rows),
                                 annual=sum(r['fiscalPeriod'] == 'FY' for r in rows),
                                 quarter=sum(r['fiscalPeriod'] != 'FY' for r in rows),
                                 completeDollarAndEps=sum(all(r[f] is not None for f in FIELDS) for r in rows))
                     for symbol, rows in sorted(financials.items())},
        'policy': {
            'fieldPriority': {field: list(concepts) for field, (_, concepts) in FIELDS.items()},
            'comparativePhase': 'Whole calendar year anniversary +/-7 days for 52/53-week calendars; no calendar-frame mapping',
            'conflicts': 'Exact concept/natural-key conflicting values => missing, no alternate rescue',
            'incomeIdentity': 'Only NetIncomeLoss then ProfitLoss; common-stockholder income is not silently equivalent',
            'taxInclusiveRevenue': 'DOW only; lowest priority; distinct concept, never assumed equivalent to tax-exclusive revenue',
            'quarter4': 'Direct 70-110d FY observation ending on same-accession 330-380d FY observation only; never subtraction',
            'proofs': 'Every selected and alternate field observation retained; raw fp/fy untouched',
            'reportEnd': 'Latest allowed duration-bearing financial observation; no shares/instant anchor',
        },
        'revenueGapInventory': {
            'scope': 'Read-only source-metadata inventory of 8 SHA/CIK-verified original US-GAAP archives before model outcomes',
            'join': 'Exact accession/filed/start/end in USD; no cross-period, component sum, or pro-forma substitution',
            'baselineMissing': 206, 'sourceProvedAdditionalTotalRows': 3,
            'remainingMissing': totals['missing_revenue'],
            'byIssuer': {
                'AMGN': {'baselineMissing': 13, 'componentMatches': {'SalesRevenueGoodsNet': 9}, 'decision': 'Keep missing: goods-only scope does not prove company total'},
                'AXP': {'baselineMissing': 42, 'componentMatches': {'NoninterestIncome': 35, 'NoninterestIncomeOtherOperatingIncome': 35}, 'decision': 'Keep missing: excludes interest; no exact total-revenue observation'},
                'CRM': {'baselineMissing': 25, 'componentMatches': {'SalesRevenueServicesNet': 25, 'TechnologyServicesRevenue': 23}, 'decision': 'Keep missing: services/component scope does not prove company total'},
                'DOW': {'baselineMissing': 3, 'totalConcept': 'RevenueFromContractWithCustomerIncludingAssessedTax', 'exactMatches': 3, 'decision': 'Accept lowest priority with tax-inclusive concept identity preserved'},
                'GS': {'baselineMissing': 1, 'decision': 'Keep missing: no exact total-revenue observation'},
                'JNJ': {'baselineMissing': 38, 'componentMatches': {'SalesRevenueGoodsNet': 38}, 'decision': 'Keep missing: goods-only scope does not prove company total'},
                'KO': {'baselineMissing': 43, 'componentMatches': {'SalesRevenueGoodsNet': 43, 'SegmentReportingInformationRevenue': 2}, 'decision': 'Keep missing: goods/segment scope does not prove consolidated total'},
                'MRK': {'baselineMissing': 41, 'componentMatches': {'SalesRevenueGoodsNet': 41, 'OtherSalesRevenueNet': 3, 'BusinessAcquisitionsProFormaRevenue': 14}, 'decision': 'Keep missing: goods/other/pro-forma measures cannot replace company total'},
            },
            'metadataDefinitions': {
                'SalesRevenueGoodsNet': 'Revenue from goods sold net of returns, allowances and discounts; not proof of consolidated total',
                'SalesRevenueServicesNet': 'Revenue from services rendered net of allowances and discounts; not proof of consolidated total',
                'NoninterestIncome': 'Noninterest fees, commissions, premiums, asset disposals and other sources; excludes interest',
                'RevenueFromContractWithCustomerIncludingAssessedTax': 'Customer-performance-obligation revenue including transaction-related assessed taxes',
            },
        },
        'limitations': [
            'Current companyfacts archive is not a historical API capture; values can reflect later companyfacts processing.',
            'Only original observations filed by cutoff are selected; no live DB standardization or production-model promotion.',
            'Missing/conflicting fields remain null; unsupported extension concepts and banks without operating-income tag stay missing.',
            'Legacy DIS local original hash is pinned here, not asserted to come from the newer acquisition manifests.',
        ],
        'financialsSha256': sha256(output / 'financials.json'),
        'sourceManifestSha256': sha256(output / 'source-manifest.json'),
        'moduleSha256': sha256(Path(__file__)),
    }
    # Verify originals again before completing the receipt.
    for record in records:
        if sha256(record['archivePath']) != record['sha256'].lower():
            raise ValueError('Source SHA drift during recovery')
    write('receipt.json', receipt)
    return receipt


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = prepare(args.root, args.output or args.root / 'tmp/quant-quarterly-financials-20261101-v1')
    print(json.dumps({k: result[k] for k in ('sourceCount', 'symbolCount', 'totals', 'financialsSha256')}, indent=2))
