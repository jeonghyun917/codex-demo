"""Build versioned dashboard status from sealed forward evidence, never run a model on GET."""
import argparse
from collections import Counter
import json
from pathlib import Path

import quant_forward_validation as forward

VERSION = 'QUANT_FORWARD_VALIDATION_STATUS_V1'


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def summarize(index, pairs, diagnostic, as_of):
    now = forward.timestamp(as_of)
    if index not in {'DOW30', 'SP500', 'NASDAQ100'}:
        raise ValueError('Unsupported status index')
    expected, observed, exits, cohort_keys = set(), [], {}, set()
    for manifest, report in pairs:
        if forward.timestamp(report['asOfUtc']) != now:
            raise ValueError('Mixed evaluation clocks')
        closes = {s['session']: forward.timestamp(s['closeUtc']) for s in manifest['protocol']['calendar']}
        for cohort in manifest['cohorts']:
            if cohort['indexCode'] != index:
                continue
            key = (index, cohort['anchorSession'])
            if key in cohort_keys:
                raise ValueError('Duplicate status cohort')
            cohort_keys.add(key)
            exits[key] = closes[cohort['exitSession']]
            expected.update((index, cohort['anchorSession'], p['symbol']) for p in cohort['predictions'])
        observed.extend(r for r in report['observations'] if r['indexCode'] == index)
    keys = [(r['indexCode'], r['anchorSession'], r['symbol']) for r in observed]
    if not expected or len(keys) != len(set(keys)) or set(keys) != expected:
        raise ValueError('Missing or duplicate original names')
    for row in observed:
        if row['status'] not in {'PENDING_OUTCOME', 'INSUFFICIENT_PRICES', 'MATURE'}:
            raise ValueError('Unsupported evaluation state')
        if row['status'] != 'PENDING_OUTCOME' and exits[(index, row['anchorSession'])] > now:
            raise ValueError('A score cannot precede actual maturity')
    mature = [r for r in observed if r['status'] == 'MATURE']
    errors = {}
    for target in ('Absolute', 'Excess'):
        values = [abs(forward.number(r['predicted'+target+'Pct']) - forward.number(r['realized'+target+'Pct']))
                  for r in mature if r.get('predicted'+target+'Pct') is not None]
        errors[target] = sum(values)/len(values) if values else None
    diagnoses = [c for c in diagnostic['cohorts'] if c['indexCode'] == index]
    if ({(index, c['anchorSession']) for c in diagnoses} != cohort_keys or len(diagnoses) != len(cohort_keys)
            or sum(c['expectedNames'] for c in diagnoses) != len(expected)):
        raise ValueError('Diagnostic denominator differs from frozen forecasts')
    counts = Counter()
    for row in diagnoses:
        counts.update(row['counts'])
    matured_cohorts = sum(exit_at <= now for exit_at in exits.values())
    status = ('AWAITING_MATURITY' if not matured_cohorts else
              'MATURED' if len(mature) == len(expected) else 'PARTIAL')
    return dict(format=VERSION, indexCode=index, generatedAtUtc=as_of, status=status, promotionAllowed=False,
        research=dict(classification='EXPOSED_TRAINING_NOT_FORWARD', baselineRawMae=5.749702649,
            baselineExcessMae=4.405489367, candidateRawMae=5.757472208, candidateExcessMae=4.410768581,
            improved=False, basis='609 dates / 17904 rows, previously exposed 2018-04-02..2020-08-28'),
        forward=dict(originalObservations=len(expected), cohorts=len(cohort_keys), maturedCohorts=matured_cohorts,
            scoredObservations=len(mature), firstEarliestEvaluationUtc=forward.iso(min(exits.values())),
            entryNonTradable=counts['NON_TRADABLE_AT_ENTRY'], missingEntryClose=counts['ENTRY_CLOSE_MISSING'],
            rawMae=errors['Absolute'], excessMae=errors['Excess'], counts=dict(Counter(r['status'] for r in observed))),
        benchmark=dict(kind='MARKET_SNAPSHOT_WEIGHTED_PRICE_PROXY', totalReturn=False),
        limitations=['연구 수치는 이미 노출된 훈련 구간이며 실제 전방 성과가 아닙니다.',
            '원래 예측·분모 유지. 기준 평가는 완전 가격 종목만, 별도 후보는 전체 30종목 조건입니다.',
            '중첩 20D 창·지수 내 중복 종목은 독립 표본이 아닙니다.',
            '가격수익률 기준이며 배당·거래비용·상장폐지 청산 대가는 포함하지 않습니다.',
            '예측 MAE는 전략 수익률이 아닙니다. 만기 전 오차는 0이 아니라 미확정입니다.',
            '실시간 캡처는 완전한 과거 입력 취득시각/PIT를 증명하지 않습니다.'])


def build(result_path):
    result_path = Path(result_path)
    result = read(result_path)
    if result.get('status', '').startswith('FAILED') or result.get('promotion') is not False:
        raise ValueError('Only completed evaluation runs may publish status')
    as_of = result['evaluationAsOfUtc']
    if forward.timestamp(as_of) > forward.now_utc():
        raise ValueError('Future evaluation clock')
    diagnostic_path = result_path.parent/'eligibility-diagnostics.json'
    diagnostic = read(diagnostic_path)
    forward.verify_seal(diagnostic)
    if diagnostic['asOfUtc'] != as_of:
        raise ValueError('Mixed diagnostic clock')
    pairs, provenance = [], [dict(path=str(result_path.resolve()), sha256=forward.digest(result_path.read_bytes())),
                             dict(path=str(diagnostic_path.resolve()), sha256=forward.digest(diagnostic_path.read_bytes()))]
    for evaluation in result['evaluations']:
        manifest, report = read(evaluation['manifest']), read(evaluation['report'])
        forward.verify_seal(manifest)
        forward.verify_seal(manifest['protocol'])
        forward.verify_seal(report)
        if report['manifestContentSha256'] != manifest['contentSha256']:
            raise ValueError('Evaluation is not bound to original frozen cohort')
        price_path = Path(report['pricesInput']['path'])
        if forward.digest(price_path.read_bytes()) != report['pricesInput']['sha256']:
            raise ValueError('Outcome export changed after evaluation')
        pairs.append((manifest, report))
        for path in (Path(evaluation['manifest']), Path(evaluation['report']), price_path):
            provenance.append(dict(path=str(path.resolve()), sha256=forward.digest(path.read_bytes())))
    payloads = [summarize(index, pairs, diagnostic, as_of) for index in ('DOW30', 'SP500', 'NASDAQ100')]
    return forward.seal(dict(format='QUANT_VALIDATION_STATUS_PUBLICATION_V1', generatedAtUtc=as_of,
                             payloads=payloads, evidence=provenance, promotionAllowed=False))


def verify_publication(path, expected_sha=None):
    path = Path(path)
    raw = path.read_bytes()
    sha = forward.digest(raw)
    if expected_sha is not None and sha != expected_sha:
        raise ValueError('Publication bytes changed before verification')
    body = json.loads(raw.decode('utf-8-sig'))
    forward.verify_seal(body)
    evidence = body.get('evidence')
    if not isinstance(evidence, list) or not evidence:
        raise ValueError('Publication requires original evaluation evidence')
    if build(evidence[0]['path']) != body:
        raise ValueError('Publication differs from exact original-evidence rebuild')
    if forward.digest(path.read_bytes()) != sha:
        raise ValueError('Publication changed during verification')
    return sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--result', type=Path)
    parser.add_argument('--out', type=Path)
    parser.add_argument('--verify-publication', type=Path)
    parser.add_argument('--expected-sha')
    args = parser.parse_args()
    if args.verify_publication:
        if args.result or args.out:
            parser.error('Verification cannot also create an output')
        print('PUBLICATION_VERIFIED '+verify_publication(args.verify_publication, args.expected_sha))
        return
    if not args.result or not args.out or args.expected_sha:
        parser.error('Creation requires --result and --out')
    forward.write_immutable(args.out, build(args.result))
    print('STATUS_EVIDENCE_READY '+str(args.out)+' promotion=false')


if __name__ == '__main__':
    main()
