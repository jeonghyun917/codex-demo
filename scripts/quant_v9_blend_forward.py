"""Freeze separate pre-entry candidate outputs; never alter the live V9 archive."""
from datetime import datetime,timezone
from pathlib import Path
import math

import quant_full_financial_study as io
import quant_v9_return_blend as candidate
from quant_forward_validation import timestamp

ROOT=Path('data/quant-candidate-forward-20261101-v1/DOW30')
COHORT=Path('data/quant-forward-validation-20261031-v2/DOW30/cohort-first-actual.json')
CAPTURE=Path('tmp/quant-v9-return-blend-forward-read-20261101-v1/upstream.json')


def build_predictions(cohort,capture,weight,now):
    if capture['databaseWrites']!=0 or capture['databaseBefore']!=capture['databaseAfter']:
        raise ValueError('Read-only upstream receipt required')
    clock=timestamp(now);read_at=timestamp(capture['capturedAtUtc']);entry=timestamp(capture['entryCloseUtc'])
    if not read_at<=clock<entry or entry.date().isoformat()!=cohort['entrySession']:
        raise ValueError('Every input and freeze must precede original entry')
    day=cohort['originalSignalDate'];index=cohort['indexCode'];compact=day.replace('-','')
    if capture['indexCode']!=index or capture['originalSignalDate']!=day:
        raise ValueError('Upstream cohort/date mismatch')
    upstream={};baseline={}
    for rows,mapping,version in [(cohort['predictions'],baseline,'V9'),(capture['upstreamV5'],upstream,'V5')]:
        for p in rows:
            if p['symbol'] in mapping or p['indexCode']!=index or p['signalDate']!=day or p['horizonDays']!=20 or p['modelVersion']!=f'EXPECTED_RETURN_{version}_CORRECTED_20261017':
                raise ValueError('Duplicate or mismatched forward prediction')
            source=p.get('source') or ''
            if version=='V5' and source!='LIVE_REGIME_NONLINEAR_INTERACTION_EXPECTED_RETURN_V5_CUTOFF_LT_'+compact:
                raise ValueError('Unproved live V5 cutoff')
            if version=='V9' and (not source.startswith('LIVE_V9_') or not source.endswith('CUTOFF_LT_'+compact)):
                raise ValueError('Unproved live V9 cutoff')
            values=[float(p['expectedReturnPct']),float(p['expectedExcessReturnPct'])]
            if not all(math.isfinite(v) for v in values):raise ValueError('Finite forward heads required')
            mapping[p['symbol']]=values
    if not baseline or baseline.keys()!=upstream.keys():raise ValueError('Whole frozen cohort required')
    return [dict(symbol=symbol,baselineV9=baseline[symbol],half_upstream=candidate.blend(upstream[symbol],baseline[symbol],.5),
                 past_mae_blend=candidate.blend(upstream[symbol],baseline[symbol],weight)) for symbol in sorted(baseline)]


def main():
    if ROOT.exists():raise ValueError('Never overwrite prospective candidate freeze')
    fixed=io.load(candidate.ROOT/'protocol.json');io.utility.verify_sources(fixed['sourceHashes'])
    internal=io.load(candidate.ROOT/'internal-result.json');fit=internal['finalForwardFit']
    manifest=io.load(COHORT);capture=io.load(CAPTURE)
    if io.sha(COHORT)!=capture['sourceCohortSha256'] or len(manifest['cohorts'])!=1:
        raise ValueError('Exact immutable source cohort required')
    cohort=manifest['cohorts'][0]
    if cohort['indexCode']!='DOW30' or len(cohort['predictions'])!=30:
        raise ValueError('Predeclared complete DOW30 cohort required')
    for record in manifest['archiveInputs']:
        if io.sha(record['path'])!=record['sha256']:raise ValueError('Original source archive changed')
    now=datetime.now(timezone.utc).isoformat().replace('+00:00','Z')
    rows=build_predictions(cohort,capture,fit['weight'],now)
    candidate.require_unseen_gate(dict(certified=True,classification='PROSPECTIVE_PRE_ENTRY_FROZEN'))
    paths=[COHORT,COHORT.parent/'protocol.json',CAPTURE,candidate.ROOT/'protocol.json',candidate.ROOT/'internal-result.json',
           Path(__file__),Path('scripts/test_quant_v9_blend_forward.py'),Path('scripts/QuantReturnBlendForwardRead.java'),
           Path('scripts/QuantReturnBlendForwardReadTest.java'),Path('tmp/quant-forward-blend-classes-20261101/QuantReturnBlendForwardRead.class')]
    hashes={str(p):io.sha(p) for p in paths}
    hashes.update({record['path']:record['sha256'] for record in manifest['archiveInputs']})
    protocol=dict(classification='PROSPECTIVE_PRE_ENTRY_FROZEN_RESEARCH_OUTPUT_NOT_FULL_INPUT_PIT',
                  certifiedScope='Outputs frozen before planned entry, not historical-global-novelty certification',
                  frozenAtUtc=now,indexCode='DOW30',anchorSession=cohort['anchorSession'],entrySession=cohort['entrySession'],exitSession=cohort['exitSession'],
                  entryCloseUtc=capture['entryCloseUtc'],horizonTradingDays=20,expectedNames=30,originalCohortUnchanged=True,
                  candidates=['half_upstream','past_mae_blend'],pastMaeWeight=fit['weight'],calibrationFit=fit,
                  candidateDefinitionProtocolSha256=io.sha(candidate.ROOT/'protocol.json'),sourceHashes=hashes,
                  promotionAllowed=False,old60TargetsUsed=False,
                  evaluationRules=dict(target='PRICE_RETURN_AND_ARITHMETIC_BENCHMARK_EXCESS',
                    benchmarkProtocolPath=str(COHORT.parent/'protocol.json'),all21ClosesRequired=True,
                    allFrozenNamesRetained=True,missingLabelsNeverDropped=True,metricsBeforeMaturity=None,
                    weighting='EQUAL_NAMES_WITHIN_THIS_SINGLE_COHORT',candidateReselectionAllowed=False),
                  limitations=['Current live outputs are observed, not complete raw-input PIT proof.',
                    'Blend fitting used exposed mature research OOF through2020Sep29, not current-forward outcome labels.',
                    'The guarded candidate weight is zero and is therefore exactly baseline, not a new improved model.',
                    'The fixed-half candidate worsened excess MAE internally; it is an experimental comparison only.',
                    'No historical unused performance claim, no operating deployment or automatic promotion.'])
    archive=dict(protocolSha256=None,frozenAtUtc=now,originalSignalDate=cohort['originalSignalDate'],predictions=rows)
    ROOT.mkdir(parents=True);io.save(ROOT/'protocol.json',protocol)
    archive['protocolSha256']=io.sha(ROOT/'protocol.json');io.save(ROOT/'cohort.json',archive)
    io.save(ROOT/'pending-evaluation.json',dict(status='PENDING_ORIGINAL_20D_EXIT',exitSession=cohort['exitSession'],
        expectedNames=30,matureNames=0,pendingNames=30,baselineMaePp=None,halfUpstreamMaePp=None,pastMaeBlendMaePp=None,
        protocolSha256=io.sha(ROOT/'protocol.json'),cohortSha256=io.sha(ROOT/'cohort.json'),promotionAllowed=False))
    print(dict(frozenNames=len(rows),entry=cohort['entrySession'],exit=cohort['exitSession'],pastMaeWeight=fit['weight'],noOutcomes=True))


if __name__=='__main__':main()
