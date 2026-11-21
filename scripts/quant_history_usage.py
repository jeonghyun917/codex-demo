"""Audit interval exposure, not just decision-date novelty. Never refit models."""
import argparse
import hashlib
import json
from datetime import date
from pathlib import Path


def window_usage(rows, entry, exit_day, horizon=20):
    if date.fromisoformat(entry) >= date.fromisoformat(exit_day):
        raise ValueError('Positive target return interval required')
    overlaps, unknown = [], 0
    for row in rows:
        if horizon is not None and row.get('horizonDays') != horizon:
            continue
        first, last = row.get('entryTradeDate'), row.get('exitTradeDate')
        if not first or not last or first >= last:
            unknown += 1
            continue
        # The return's increments are (entry,exit]; an endpoint shared with
        # another window is not itself a shared daily return increment.
        if first < exit_day and last > entry:
            overlaps.append(row)
    return dict(overlapRows=len(overlaps), unresolvedWindows=unknown,
                priorDecisionDates=sorted({r['signalDate'] for r in overlaps}),
                noPriorLabelWindowOverlap=not overlaps and not unknown)


def require_unused_windows(audit):
    if not audit or any(not value['priorAnyHorizonLabelWindows']['noPriorLabelWindowOverlap']
                        for value in audit.values()):
        raise ValueError('Prior label-window exposure or unresolved usage; refuse untouched-holdout claim')


def main(context_path, replay_path, output, require_unused=False):
    source = json.loads(Path(context_path).read_text(encoding='utf-8'))['source']
    replay = json.loads(Path(replay_path).read_text(encoding='utf-8'))
    audit = {}
    for node in replay['dates']:
        entry, last = node['pairs'][0]['entry'], node['pairs'][0]['exit']
        if any(p['entry'] != entry or p['exit'] != last for p in node['pairs']):
            raise ValueError('Target members do not share a holding interval')
        audit[node['decision']] = dict(entry=entry, exit=last,
            prior20DLabelWindows=window_usage(source, entry, last),
            priorAnyHorizonLabelWindows=window_usage(source, entry, last, None))
    value = dict(classification='POST_REPLAY_EXPOSURE_AUDIT_NO_SELECTION_OR_REFIT',
                 sourceHashes={str(p):hashlib.sha256(Path(p).read_bytes()).hexdigest()
                               for p in [context_path,replay_path,__file__]},
                 dates=audit,
                 limitation='Decision novelty does not establish globally untouched history; preserve all prespecified metrics, never select a winner using this audit.')
    with Path(output).open('x',encoding='utf-8') as stream:
        json.dump(value,stream,indent=2,allow_nan=False)
    print(json.dumps(audit,indent=2))
    if require_unused:
        require_unused_windows(audit)


if __name__ == '__main__':
    p=argparse.ArgumentParser()
    p.add_argument('context');p.add_argument('replay');p.add_argument('output')
    p.add_argument('--require-unused',action='store_true',
                   help='Fail closed if ANY prior horizon overlaps a proposed test return interval')
    a=p.parse_args()
    main(a.context,a.replay,a.output,a.require_unused)
