"""Offline paired evaluation of frozen candidate outputs. No fitting, DB or network.

Bundles preserve exact source bytes so evaluation does not depend on temporary
research classes or scripts remaining at their original paths months later.
"""
import argparse
import base64
from collections import Counter
import hashlib
import json
import math
from pathlib import Path

import quant_forward_validation as forward

FORMAT='FROZEN_RETURN_BLEND_EVALUATION_BUNDLE_V1'
MODELS=('baselineV9','half_upstream','past_mae_blend')
DEFAULT_ROOT=Path('data/quant-candidate-forward-20261101-v1/DOW30')
DEFAULT_BUNDLE=Path('data/quant-candidate-evaluation-20261101-v1/DOW30/bundle.json')


def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def _context(bundle):
    forward.verify_seal(bundle)
    if bundle.get('format')!=FORMAT:raise ValueError('Unknown candidate evaluation bundle')
    decoded={}
    for path,record in bundle['files'].items():
        raw=base64.b64decode(record['base64'],validate=True)
        if forward.digest(raw)!=record['sha256']:raise ValueError('Bundled source hash mismatch')
        decoded[path]=raw
    roles=bundle['roles']
    parse=lambda role:json.loads(decoded[roles[role]].decode('utf-8-sig'))
    protocol,archive,anchor,manifest=(parse(k) for k in ['candidateProtocol','candidateCohort','initialAnchor','baselineManifest'])
    protocol_hash=forward.digest(decoded[roles['candidateProtocol']]);archive_hash=forward.digest(decoded[roles['candidateCohort']])
    if anchor['protocolSha256']!=protocol_hash or archive['protocolSha256']!=protocol_hash or anchor['cohortSha256']!=archive_hash:
        raise ValueError('Candidate protocol/cohort anchor changed')
    for path,expected in protocol['sourceHashes'].items():
        if path not in decoded or forward.digest(decoded[path])!=expected:raise ValueError('Incomplete original source evidence')
    forward.verify_seal(manifest);forward.verify_seal(manifest['protocol'])
    expected=protocol['expectedNames']
    if type(expected) is not int or expected<=0 or protocol['horizonTradingDays']!=20:
        raise ValueError('Fixed positive cohort and20-session horizon required')
    if protocol['candidates']!=list(MODELS[1:]) or protocol.get('promotionAllowed') is not False:
        raise ValueError('Only fixed research candidates without promotion qualify')
    rules=protocol['evaluationRules']
    if any(rules.get(k) is not True for k in ['all21ClosesRequired','allFrozenNamesRetained','missingLabelsNeverDropped']) or rules.get('candidateReselectionAllowed') is not False:
        raise ValueError('Frozen full-cohort comparison rules required')
    matches=[c for c in manifest['cohorts'] if c['indexCode']==protocol['indexCode'] and c['anchorSession']==protocol['anchorSession']]
    if len(matches)!=1 or len(manifest['cohorts'])!=1:raise ValueError('Exactly one original index-bound cohort required')
    original=matches[0]
    if any(protocol[k]!=original[k] for k in ['entrySession','exitSession']) or manifest['protocol']['indexCode']!=protocol['indexCode'] or archive['originalSignalDate']!=original['originalSignalDate']:
        raise ValueError('Original date/index/benchmark binding changed')
    sessions=forward.validated_sessions(manifest['protocol']['calendar']);position={s['session']:i for i,s in enumerate(sessions)}
    entry,exit_day=protocol['entrySession'],protocol['exitSession']
    if position[exit_day]-position[entry]!=20 or forward.timestamp(protocol['entryCloseUtc'])!=forward.timestamp(sessions[position[entry]]['closeUtc']):
        raise ValueError('Original21-close holding window required')
    frozen=forward.timestamp(protocol['frozenAtUtc'])
    if forward.timestamp(archive['frozenAtUtc'])!=frozen or not forward.timestamp(manifest['frozenAtUtc'])<=frozen<forward.timestamp(protocol['entryCloseUtc']):
        raise ValueError('All fixed outputs must precede original entry')
    baseline={}
    for row in original['predictions']:
        if row['symbol'] in baseline:raise ValueError('Duplicate original frozen name')
        baseline[row['symbol']]=[forward.number(row[k]) for k in ['expectedReturnPct','expectedExcessReturnPct']]
    predictions={}
    for row in archive['predictions']:
        name=row['symbol']
        if name in predictions or name not in baseline:raise ValueError('Duplicate/unknown candidate name')
        values={m:[forward.number(x) for x in row[m]] for m in MODELS}
        if any(len(v)!=2 for v in values.values()) or values['baselineV9']!=baseline[name]:raise ValueError('Baseline or candidate heads changed')
        predictions[name]=values
    if len(predictions)!=expected or predictions.keys()!=baseline.keys():raise ValueError('Partial fixed cohort forbidden')
    return protocol,manifest,predictions


def prepare_bundle(candidate_root,output):
    candidate_root,output=Path(candidate_root),Path(output)
    if output.exists():raise FileExistsError(output)
    protocol_path=candidate_root/'protocol.json';protocol=json.loads(protocol_path.read_text(encoding='utf-8-sig'))
    baseline=Path(protocol['evaluationRules']['benchmarkProtocolPath']).parent/'cohort-first-actual.json'
    roles=dict(candidateProtocol=str(protocol_path),candidateCohort=str(candidate_root/'cohort.json'),
               initialAnchor=str(candidate_root/'pending-evaluation.json'),baselineManifest=str(baseline))
    expected=dict(protocol['sourceHashes']);expected.update({p:sha(p) for p in roles.values()})
    files={}
    for path,fingerprint in expected.items():
        raw=Path(path).read_bytes()
        if forward.digest(raw)!=fingerprint:raise ValueError('Original evidence changed before bundling')
        files[path]=dict(sha256=fingerprint,base64=base64.b64encode(raw).decode('ascii'))
    bundle=forward.seal(dict(format=FORMAT,assembledAtUtc=forward.iso(forward.now_utc()),roles=roles,files=files,
                             purpose='Exact existing outputs/evidence snapshot; no new prediction or label',promotion=False))
    _context(bundle)
    output.parent.mkdir(parents=True,exist_ok=True);forward.write_immutable(output,bundle)
    return dict(bundle=str(output),sha256=sha(output),expectedNames=protocol['expectedNames'],sourceFiles=len(files))


def evaluate(bundle_path,prices,as_of=None):
    bundle_path=Path(bundle_path);bundle=json.loads(bundle_path.read_text(encoding='utf-8-sig'))
    protocol,manifest,predictions=_context(bundle)
    baseline=forward.evaluate(manifest,prices,as_of=as_of)
    expected=protocol['expectedNames'];observations=baseline['observations']
    if len(observations)!=expected or {r['symbol'] for r in observations}!=predictions.keys():raise ValueError('Evaluation denominator drift')
    counts=dict(Counter(r['status'] for r in observations));ready=counts.get('MATURE',0)==expected
    status='COMPLETE_COHORT' if ready else 'PENDING_OUTCOME' if counts.get('PENDING_OUTCOME',0) else 'INSUFFICIENT_COMPLETE_COHORT'
    metrics={m:{h:dict(n=0,maePp=None,rmsePp=None,biasPp=None) for h in ['absolute','excess']} for m in MODELS}
    zero={h:dict(n=0,maePp=None,rmsePp=None,biasPp=None) for h in ['absolute','excess']}
    deltas={m:dict(n=0,absoluteMaePp=None,excessMaePp=None,absoluteRmsePp=None,excessRmsePp=None) for m in MODELS[1:]}
    details=[]
    for row in observations:
        detail={k:v for k,v in row.items() if k not in ['realizedAbsolutePct','realizedBenchmarkPct','realizedExcessPct','predictedAbsolutePct','predictedExcessPct']}
        if ready:detail.update(realizedAbsolutePct=row['realizedAbsolutePct'],realizedBenchmarkPct=row['realizedBenchmarkPct'],realizedExcessPct=row['realizedExcessPct'])
        details.append(detail)
    if ready:
        for head in ['absolute','excess']:
            actual=[r['realized'+head.capitalize()+'Pct'] for r in observations]
            zero[head]=dict(n=expected,maePp=sum(abs(y) for y in actual)/expected,
                            rmsePp=math.sqrt(sum(y*y for y in actual)/expected),biasPp=-sum(actual)/expected)
        for m in MODELS:
            for j,head in enumerate(['absolute','excess']):
                errors=[predictions[r['symbol']][m][j]-r['realized'+head.capitalize()+'Pct'] for r in observations]
                metrics[m][head]=dict(n=expected,maePp=sum(abs(e) for e in errors)/expected,
                                     rmsePp=math.sqrt(sum(e*e for e in errors)/expected),biasPp=sum(errors)/expected)
        for m in MODELS[1:]:
            deltas[m]=dict(n=expected,**{head+metric.capitalize()+'Pp':metrics[m][head][metric+'Pp']-metrics['baselineV9'][head][metric+'Pp']
                            for head in ['absolute','excess'] for metric in ['mae','rmse']})
    return forward.seal(dict(reportVersion='PAIRED_FROZEN_RETURN_BLEND_EVALUATION_V1',classification='DESCRIPTIVE_PAIRED_RESEARCH_NOT_PROMOTION',
        status=status,asOfUtc=baseline['asOfUtc'],bundleInput=dict(path=str(bundle_path.resolve()),sha256=sha(bundle_path)),
        pricesInput=baseline['pricesInput'],indexCode=protocol['indexCode'],anchorSession=protocol['anchorSession'],entrySession=protocol['entrySession'],exitSession=protocol['exitSession'],
        expectedNames=expected,completeNames=counts.get('MATURE',0),evaluatedNames=expected if ready else 0,counts=counts,
        metrics=metrics,zeroReference=zero,pairedDeltas=deltas,observations=details,allFrozenNamesRetained=True,promotion=False,candidatesRefit=False,
        evaluationCodeSha256=sha(__file__)))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='mode',required=True)
    prepare=sub.add_parser('prepare');prepare.add_argument('--candidate-root',type=Path,default=DEFAULT_ROOT);prepare.add_argument('--out',type=Path,default=DEFAULT_BUNDLE)
    score=sub.add_parser('evaluate');score.add_argument('--bundle',type=Path,default=DEFAULT_BUNDLE);score.add_argument('--prices',type=Path,required=True);score.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    if args.mode=='prepare':print(json.dumps(prepare_bundle(args.candidate_root,args.out),indent=2))
    else:
        report=evaluate(args.bundle,args.prices);forward.write_immutable(args.out,report)
        print(json.dumps({k:report[k] for k in ['status','expectedNames','evaluatedNames','counts','metrics']},indent=2))
