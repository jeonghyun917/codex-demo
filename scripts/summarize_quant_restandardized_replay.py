"""File-only paired replay summary and explicit financial-availability attribution audit."""
import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from statistics import mean


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def summarize(root, prepared, output):
    result = json.loads((root/'result.json').read_text(encoding='utf-8'))
    inputs = json.loads(prepared.read_text(encoding='utf-8'))
    assert result['databaseWrites'] == 0 and not result['promotionAllowed']
    assert result['sourceDatabaseBefore'] == result['sourceDatabaseAfter']
    assert len(result['dates']) == 20
    for path, digest in result['inputHashes'].items():
        assert sha(path) == digest, path
    lost = []; observed = 0; availability_daily = []; all_pairs = []
    for date in result['dates']:
        day = date['decision']; all_pairs.extend(date['pairs'])
        new = {s['symbol']: json.loads(s['rawJson']) for s in date['newSignals']}
        day_lost = []
        for symbol, signal in inputs['oldRecoveredSignals'].get(day, {}).items():
            old = json.loads(signal['rawJson']); observed += 1
            for kind in ('annual', 'quarter'):
                if old[kind+'Accession'] and not new[symbol][kind+'Accession']:
                    row = dict(decision=day, symbol=symbol, kind=kind,
                               oldAccession=old[kind+'Accession'], newAccession=None)
                    lost.append(row); day_lost.append(row)
        availability_daily.append(dict(decision=day,
            inspectedOldOverlaySignals=len(inputs['oldRecoveredSignals'].get(day, {})),
            oldAvailableNewMissingRows=len(day_lost),
            affectedSymbolDates=sorted({r['symbol'] for r in day_lost})))
    metrics = {target: {key: mean(d['metrics'][target][key] for d in result['dates'])
                       for key in ('oldMaePp', 'newMaePp', 'maeDeltaPp', 'controlMaePp', 'newVsControlMaeDeltaPp')}
               for target in ('absolute', 'excess')}
    better = {target: sum(d['metrics'][target]['maeDeltaPp'] < 0 for d in result['dates'])
              for target in ('absolute', 'excess')}
    by_pair = {(d['decision'], p['symbol']): p for d in result['dates'] for p in d['pairs']}
    model_metrics = []
    for model in sorted({p['modelVersion'] for p in result['predictions']}):
        predictions = [p for p in result['predictions'] if p['modelVersion'] == model]
        controls = {(p['signalDate'], p['symbol']): p for p in result['controlPredictions'] if p['modelVersion'] == model}
        assert len(predictions) == len(controls) == 2020
        max_delta = 0
        for target, field in [('absolute', 'expectedReturnPct'), ('excess', 'expectedExcessReturnPct')]:
            old_errors = []; new_errors = []; control_errors = []
            for p in predictions:
                day = p['signalDate']; symbol = p['symbol']; pair = by_pair[day, symbol]
                actual = pair['actual'] - (pair['benchmark'] if target == 'excess' else 0)
                old = inputs['frozenPredictions'][day+'/'+symbol+'/'+model]
                control = controls[day, symbol]
                old_errors.append(abs(old[field]-actual)); new_errors.append(abs(p[field]-actual))
                control_errors.append(abs(control[field]-actual))
                max_delta = max(max_delta, abs(old[field]-control[field]))
            model_metrics.append(dict(model=model, target=target, rows=2020,
                oldMaePp=mean(old_errors), newMaePp=mean(new_errors), controlMaePp=mean(control_errors),
                maeDeltaPp=mean(new_errors)-mean(old_errors),
                maxControlVsFrozenPredictionDeltaPp=max_delta))
    summary = dict(classification='ACTUAL_CORRECTED_PRODUCTION_INPUT_PATH_RESEARCH_WITH_AVAILABILITY_CONFOUND',
        databaseWrites=0, promotionAllowed=False, dates=20, symbolsPerDate=101, pairedRows=2020,
        modelPredictions=16160, metrics=metrics, betterDates=better, modelMetrics=model_metrics,
        allV9ControlsExactlyMatchFrozen=all(d['maxControlVsFrozenPredictionDeltaPp'] == 0 for d in result['dates']),
        changedV9SymbolDates=sum(p['oldAbsolute'] != p['newAbsolute'] or p['oldExcess'] != p['newExcess'] for p in all_pairs),
        financialAvailability=dict(inspectedPreservedOldOverlaySignals=observed,
            oldAvailableNewMissingFinancialRows=len(lost),
            affectedSymbolDates=len({(r['decision'], r['symbol']) for r in lost}),
            missingRowsBySymbol=dict(Counter(r['symbol'] for r in lost)),
            missingRowsByKind=dict(Counter(r['kind'] for r in lost)), daily=availability_daily, rows=lost),
        limitations=[
            'The historical frozen models included verified recovered financial overlays absent from the production DB.',
            'This compares the actual corrected production input path against frozen research inputs; it is NOT an isolated causal estimate of DB repair or financial-policy effects.',
            'Availability counts inspect the90 old recovered signals preserved in reports, not every original native financial lookup; no unsupported full-population source-availability claim.',
            'Cross-sectional ranking propagates changed or missing factors across other names; dropping affected securities does not isolate the repair effect.',
            'Existing builder missing-factor policies remain explicit; no forecasts were filled with fabricated neutral predictions.',
            'Both absolute and excess aggregate MAE worsened slightly; no predictive-improvement, significance, promotion or production model-change claim.',
            '20 previously inspected discontinuous dates; reconstructed membership and receipt timing are not official PIT proof; price outcomes are not dividend-reinvested total returns.'],
        inputHashes={str(root/'result.json'): sha(root/'result.json'), str(prepared): sha(prepared),
                     'scripts/summarize_quant_restandardized_replay.py': sha('scripts/summarize_quant_restandardized_replay.py')})
    with output.open('x', encoding='utf-8') as f:
        json.dump(summary, f, indent=2, allow_nan=False)
    print(json.dumps(dict(metrics=metrics, betterDates=better, oldOverlaySignals=observed,
                          availabilityAffectedSymbolDates=summary['financialAvailability']['affectedSymbolDates'],
                          oldAvailableNewMissingFinancialRows=len(lost),
                          missingRowsBySymbol=summary['financialAvailability']['missingRowsBySymbol']), indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('replay_directory', type=Path)
    parser.add_argument('prepared_inputs', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    summarize(args.replay_directory, args.prepared_inputs, args.output)
