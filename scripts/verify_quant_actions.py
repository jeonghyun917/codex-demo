"""Local integrity verification; writes only a report, never relabels or rewrites evidence."""
import hashlib
import json
from collections import Counter
from datetime import date
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ROOT = REPO / 'tmp/pit-acquisition/actions'


def require(condition, message):
    if not condition:
        raise ValueError(message)


summary = {}
for name, ratio in [('bundle.json', 'PROVIDER_ADJUSTMENT_FACTOR_UNVERIFIED'),
                    ('official-bundle.json', 'OFFICIAL_SHARE_RATIO')]:
    path = ROOT / name
    bundle = json.loads(path.read_text(encoding='utf-8'))
    sources = {s['id']: s for s in bundle['sources']}
    require(len(sources) == len(bundle['sources']), 'Duplicate source IDs')
    for source in sources.values():
        raw_path = (ROOT / source['path']).resolve()
        require(raw_path.is_relative_to(ROOT), 'Source path escapes evidence directory')
        require(hashlib.sha256(raw_path.read_bytes()).hexdigest() == source['sha256'], source['id'])
    keys = []
    for event in bundle['events']:
        require(event['sourceId'] in sources, 'Orphan event source')
        require(date.fromisoformat(event['effectiveDate']) < date(2026, 10, 17), 'Future event')
        require(event['action'] in ('SPLIT', 'DIVIDEND'), 'Unsupported event')
        if event['action'] == 'SPLIT':
            require(event.get('ratioBasis') == ratio, 'Wrong ratio basis: evidence must not be relabeled')
            require(event['numerator'] > 0 and event['denominator'] > 0, 'Invalid split ratio')
        else:
            require(event['amount'] > 0, 'Invalid dividend amount')
        keys.append((event['symbol'], event['action'], event['effectiveDate']))
    require(len(keys) == len(set(keys)), 'Duplicate event grain')
    summary[name] = dict(sources=len(sources), events=len(keys),
                         types=dict(Counter(e['action'] for e in bundle['events'])),
                         minEvent=min((e['effectiveDate'] for e in bundle['events']), default=None),
                         maxEvent=max((e['effectiveDate'] for e in bundle['events']), default=None),
                         hashFailures=0, duplicateEvents=0, orphanEvents=0)
manifest = json.loads((ROOT / 'manifest.json').read_text(encoding='utf-8'))
summary['acquisition'] = {k: v for k, v in manifest.items() if k != 'results'}
summary['failures'] = [r for r in manifest['results'] if r['status'] != 'OK']
summary['shortHistories'] = [r for r in manifest['results'] if r['status'] == 'OK' and r['bars'] < 100]
summary['partialRecent'] = [r for r in manifest['results'] if r['status'] == 'OK' and r['lastBar'] != '2026-10-16']
summary['certificationStatus'] = 'NOT_CERTIFIED_PARTIAL_PROVIDER_EVIDENCE_ONLY'
summary['identityMappingStatus'] = 'NO_EFFECTIVE_DATED_SECURITY_ID_MAPPING_VERIFIED'
summary['currentTickerIdentityAmbiguities'] = [
    dict(symbol='ECHO', currentProviderIssuer='EchoStar Corporation',
         risk='Ticker reuse or renamed security: historical membership identity not verified',
         certificationEligible=False)
]
summary['certificationExclusions'] = [
    dict(symbol=r['symbol'], reason='ONE_BAR_STALE_PROVIDER_HISTORY', certificationEligible=False)
    for r in manifest['results'] if r['status'] == 'OK' and r['bars'] == 1
]
(ROOT / 'verification.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps({k: v for k, v in summary.items() if k not in ('failures', 'partialRecent')}))
