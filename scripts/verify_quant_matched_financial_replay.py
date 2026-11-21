"""Independent source-coverage, selection-policy and paired-arithmetic checks."""
import argparse
import hashlib
import json
import math
from datetime import date
from pathlib import Path
from statistics import mean
from verify_quant_restandardized_replay import paired_metrics

OVERLAYS = ['tmp/quant-older-staged-20261030/inputs.json',
            'tmp/quant-extended-inputs-20261018/financial-inputs.json',
            'tmp/quant-team-inputs-20261018/financial-inputs.json',
            'tmp/quant-march-inputs-20261031/inputs-v3.json']


def valid(row, annual):
    period = row.get('fiscalPeriod', '')
    if not row.get('endDate') or (period != 'FY' if annual else period not in ('Q1', 'Q2', 'Q3', 'Q4')):
        return False
    actual = ((date.fromisoformat(row['endDate']) - date.fromisoformat(row['startDate'])).days + 1
              if row.get('startDate') else None)
    days = row.get('periodDays') if row.get('periodDays') is not None else actual
    return days is not None and (330 <= days <= 380 if annual else 70 <= days <= 110) and (actual is None or actual == days)


def choose(rows, annual, before, asof):
    def eligible(r):
        if not valid(r, annual) or not r.get('filedAt') or r['filedAt'] >= asof:
            return False
        if before:
            gap = (date.fromisoformat(before)-date.fromisoformat(r['endDate'])).days
            return 330 <= gap <= 380 if annual else 70 <= gap <= 110
        return True
    def key(r):
        priority = (0 if r.get('form') in ('10-K', '10-K/A', '20-F', '20-F/A', '40-F', '40-F/A') else 1) if annual else (0 if r.get('assets') is not None or r.get('equity') is not None else 1)
        return r['endDate'], -priority, r['filedAt'], r.get('id') or -1
    matches = sorted(filter(eligible, rows), key=key, reverse=True)
    return matches[0] if matches else None


def verify(root, prepared):
    report_path = root/'result.json'; r = json.loads(report_path.read_text(encoding='utf-8'))
    inputs = json.loads(prepared.read_text(encoding='utf-8'))
    files = [json.loads(Path(p).read_text(encoding='utf-8')) for p in OVERLAYS]
    assert r['databaseWrites'] == 0 and r['promotionAllowed'] is False
    assert r['sourceDatabaseBefore'] == r['sourceDatabaseAfter']
    for p, digest in r['inputHashes'].items():
        assert hashlib.sha256(Path(p).read_bytes()).hexdigest() == digest, p
    covered = 0; checks = 0; symbol_dates = 0; policy_missing = 0
    manifest = {(x['decision'], x['symbol']): x for x in r['overlayManifest']}
    for d in r['dates']:
        day = d['decision']; assert len(d['pairs']) == len(d['population']) == 101
        assert d['frozenTrainingContextHashOld'] == d['frozenTrainingContextHashNew']
        metric = paired_metrics(d['pairs'])
        for target in metric:
            for field, value in metric[target].items():
                assert math.isclose(value, d['metrics'][target][field], abs_tol=1e-10)
        for symbol, signal in inputs['oldRecoveredSignals'].get(day, {}).items():
            rows = (files[0].get('bySymbol', {}).get(symbol, {}).get(day) or
                    files[1].get('bySymbol', {}).get(symbol, {}).get(day) or
                    (files[2]['byDecision'].get(day) if symbol == 'TEAM' else None) or
                    (files[3]['financialsBySymbol'].get(symbol) if day == '2026-03-13' else None))
            assert rows
            raw = json.loads(signal['rawJson'])
            required = {raw[k] for k in ('annualAccession', 'previousAnnualAccession', 'quarterAccession', 'previousQuarterAccession') if raw[k]}
            assert required <= {f['accessionNumber'] for f in rows}
            assert required == set(manifest[day, symbol]['oldRequiredAccessions'])
            covered += len(required); symbol_dates += 1
            for annual, kind in [(True, 'Annual'), (False, 'Quarter')]:
                latest = choose(rows, annual, None, day)
                previous = choose(rows, annual, latest['endDate'], day) if latest else None
                for prefix, expected in [('Latest', latest), ('Previous', previous)]:
                    method = 'find'+prefix+kind+'AsOf'; key = symbol+'/'+method
                    # Production builder does not query a previous row when latest is missing.
                    if prefix == 'Previous' and latest is None:
                        assert key not in d['financialReads']; continue
                    actual = d['financialReads'][key]; checks += 1
                    if expected is None:
                        assert actual == {'missing': True}; policy_missing += 1
                    else:
                        assert valid(actual, annual) and actual['filedAt'] < day
                        for field in ('accessionNumber', 'startDate', 'endDate', 'filedAt', 'fiscalPeriod', 'currency', 'epsUnit'):
                            assert actual.get(field) == expected.get(field), (day, symbol, method, field)
        assert set(d['overlayRebuiltSymbols']) == set(inputs['oldRecoveredSignals'].get(day, {}))
    assert covered == r['coveredOldRequiredAccessionReferences']
    assert symbol_dates == r['sourceMatchedOverlaySymbolDates']
    assert len(r['predictions']) == len(r['dates'])*808
    metrics = {target: {key: mean(d['metrics'][target][key] for d in r['dates'])
                       for key in ('oldMaePp', 'newMaePp', 'maeDeltaPp')}
               for target in ('absolute', 'excess')}
    result = dict(status='PASS', dates=len(r['dates']), pairedRows=len(r['dates'])*101,
                  sourceMatchedOverlaySymbolDates=symbol_dates,
                  originalRequiredAccessionReferencesCovered=covered,
                  uncoveredOriginalAccessions=[], independentlyCheckedFinancialSelections=checks,
                  policyMissingSelections=policy_missing, metrics=metrics,
                  databaseWrites=0, promotionAllowed=False,
                  resultSha256=hashlib.sha256(report_path.read_bytes()).hexdigest())
    with (root/'matched-verification.json').open('x', encoding='utf-8') as f:
        json.dump(result, f, indent=2, allow_nan=False)
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('replay_directory', type=Path)
    parser.add_argument('prepared_inputs', type=Path)
    args = parser.parse_args()
    verify(args.replay_directory, args.prepared_inputs)
