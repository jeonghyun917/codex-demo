import copy
import importlib
import json
import sys
import tempfile
import unittest
from datetime import date,datetime,timedelta,timezone
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
import quant_forward_validation as forward
try:q=importlib.import_module('quant_v9_blend_evaluate')
except ModuleNotFoundError:q=None


class CandidateEvaluationTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(q,'Frozen candidate evaluator is missing')
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        self.baseline=self.root/'baseline';self.baseline.mkdir();self.candidate=self.root/'candidate';self.candidate.mkdir()
        self.sessions=[];day=date(2026,1,2)
        while len(self.sessions)<22:
            if day.weekday()<5:self.sessions.append(dict(session=str(day),openUtc=f'{day}T14:30:00Z',closeUtc=f'{day}T21:00:00Z'))
            day+=timedelta(days=1)
        baseline_protocol=forward.seal(dict(indexCode='DOW30',calendar=self.sessions,benchmarkSymbol='BENCH',fixedHistoricalBaseline=None))
        self.write(self.baseline/'protocol.json',baseline_protocol)
        predictions=[dict(symbol=s,indexCode='DOW30',signalDate='2026-01-03',horizonDays=20,modelVersion='V9',
                         expectedReturnPct=a,expectedExcessReturnPct=e) for s,a,e in [('A',10.,10.),('B',0.,-10.)]]
        cohort=dict(indexCode='DOW30',anchorSession='2026-01-02',entrySession='2026-01-05',exitSession='2026-02-02',originalSignalDate='2026-01-03',predictions=predictions)
        original=forward.seal(dict(protocol=baseline_protocol,frozenAtUtc='2026-01-04T12:00:00Z',cohorts=[cohort]))
        self.write(self.baseline/'cohort-first-actual.json',original)
        proto=dict(indexCode='DOW30',anchorSession='2026-01-02',entrySession='2026-01-05',exitSession='2026-02-02',
                   entryCloseUtc='2026-01-05T21:00:00Z',frozenAtUtc='2026-01-04T13:00:00Z',horizonTradingDays=20,expectedNames=2,
                   candidates=['half_upstream','past_mae_blend'],pastMaeWeight=0.,promotionAllowed=False,
                   evaluationRules=dict(benchmarkProtocolPath=str(self.baseline/'protocol.json'),all21ClosesRequired=True,
                                        allFrozenNamesRetained=True,missingLabelsNeverDropped=True,candidateReselectionAllowed=False),
                   sourceHashes={str(self.baseline/'cohort-first-actual.json'):q.sha(self.baseline/'cohort-first-actual.json'),
                                 str(self.baseline/'protocol.json'):q.sha(self.baseline/'protocol.json')})
        self.write(self.candidate/'protocol.json',proto)
        archive=dict(protocolSha256=q.sha(self.candidate/'protocol.json'),frozenAtUtc=proto['frozenAtUtc'],originalSignalDate='2026-01-03',
                     predictions=[dict(symbol='A',baselineV9=[10.,10.],half_upstream=[15.,15.],past_mae_blend=[10.,10.]),
                                  dict(symbol='B',baselineV9=[0.,-10.],half_upstream=[-5.,-15.],past_mae_blend=[0.,-10.])])
        self.write(self.candidate/'cohort.json',archive)
        self.write(self.candidate/'pending-evaluation.json',dict(protocolSha256=q.sha(self.candidate/'protocol.json'),cohortSha256=q.sha(self.candidate/'cohort.json')))
        self.bundle=self.root/'bundle.json';q.prepare_bundle(self.candidate,self.bundle)

    def write(self,path,value):path.write_text(json.dumps(value),encoding='utf-8')

    def prices(self,missing=None,duplicate=False):
        lines=['symbol,date,close']
        for n,session in enumerate(self.sessions[1:]):
            for symbol,increment in [('A',1.),('B',-.5),('BENCH',.25)]:
                if (symbol,session['session'])==missing:continue
                lines.append(f"{symbol},{session['session']},{100+n*increment}")
        if duplicate:lines.append(lines[-1])
        path=self.root/'prices.csv';path.write_text('\n'.join(lines),encoding='utf-8');return path

    def test_complete_paired_literal_metrics_use_same_arithmetic_excess_and_denominator(self):
        result=q.evaluate(self.bundle,self.prices(),datetime(2026,2,3,tzinfo=timezone.utc))
        self.assertEqual(result['status'],'COMPLETE_COHORT')
        self.assertEqual(result['expectedNames'],2);self.assertEqual(result['evaluatedNames'],2)
        self.assertAlmostEqual(result['metrics']['baselineV9']['absolute']['maePp'],10.)
        self.assertAlmostEqual(result['metrics']['baselineV9']['excess']['maePp'],5.)
        self.assertAlmostEqual(result['metrics']['baselineV9']['absolute']['rmsePp'],10.)
        self.assertAlmostEqual(result['metrics']['baselineV9']['excess']['biasPp'],0.)
        self.assertAlmostEqual(result['metrics']['half_upstream']['absolute']['maePp'],5.)
        self.assertAlmostEqual(result['metrics']['half_upstream']['excess']['maePp'],0.)
        self.assertAlmostEqual(result['pairedDeltas']['half_upstream']['absoluteMaePp'],-5.)
        self.assertAlmostEqual(result['pairedDeltas']['half_upstream']['excessMaePp'],-5.)
        self.assertEqual(result['metrics']['past_mae_blend'],result['metrics']['baselineV9'])

    def test_zero_reference_is_reported_on_the_identical_complete_cohort(self):
        result=q.evaluate(self.bundle,self.prices(),datetime(2026,2,3,tzinfo=timezone.utc))
        self.assertIsNotNone(result.get('zeroReference'),'Zero-return reference is missing')
        self.assertAlmostEqual(result['zeroReference']['absolute']['maePp'],15.)
        self.assertAlmostEqual(result['zeroReference']['excess']['maePp'],15.)
        self.assertAlmostEqual(result['zeroReference']['absolute']['biasPp'],-5.)

    def test_pending_even_with_future_prices_never_emits_realized_values_or_metrics(self):
        result=q.evaluate(self.bundle,self.prices(),datetime(2026,2,2,20,59,tzinfo=timezone.utc))
        self.assertEqual(result['status'],'PENDING_OUTCOME');self.assertEqual(result['evaluatedNames'],0)
        self.assertIsNone(result['metrics']['half_upstream']['absolute']['maePp'])
        self.assertFalse(any('realizedAbsolutePct' in r for r in result['observations']))

    def test_one_missing_intermediate_stock_or_benchmark_blocks_all_cohort_metrics(self):
        for missing in [('A','2026-01-15'),('BENCH','2026-01-15')]:
            result=q.evaluate(self.bundle,self.prices(missing),datetime(2026,2,3,tzinfo=timezone.utc))
            self.assertEqual(result['status'],'INSUFFICIENT_COMPLETE_COHORT')
            self.assertEqual(result['expectedNames'],2);self.assertEqual(result['evaluatedNames'],0)
            for model in result['metrics'].values():
                self.assertIsNone(model['absolute']['maePp']);self.assertIsNone(model['excess']['maePp'])

    def test_bundle_is_self_contained_after_temporary_source_removed(self):
        for p in self.candidate.iterdir():p.unlink()
        for p in self.baseline.iterdir():p.unlink()
        self.assertEqual(q.evaluate(self.bundle,self.prices(),datetime(2026,2,3,tzinfo=timezone.utc))['evaluatedNames'],2)

    def test_tamper_duplicate_prices_or_wrong_index_cannot_be_evaluated(self):
        with self.assertRaises(ValueError):q.evaluate(self.bundle,self.prices(duplicate=True),datetime(2026,2,3,tzinfo=timezone.utc))
        tampered=json.loads(self.bundle.read_text());tampered['assembledAtUtc']='2099-01-01T00:00:00Z';self.write(self.bundle,tampered)
        with self.assertRaises(ValueError):q.evaluate(self.bundle,self.prices(),datetime(2026,2,3,tzinfo=timezone.utc))

    def test_prediction_tamper_or_partial_cohort_rejected_before_bundle_creation(self):
        archive=json.loads((self.candidate/'cohort.json').read_text());archive['predictions'].pop()
        self.write(self.candidate/'cohort.json',archive)
        # Even a matching local file hash does not authorize a partial new denominator.
        anchor=json.loads((self.candidate/'pending-evaluation.json').read_text());anchor['cohortSha256']=q.sha(self.candidate/'cohort.json')
        self.write(self.candidate/'pending-evaluation.json',anchor)
        with self.assertRaises(ValueError):q.prepare_bundle(self.candidate,self.root/'bad-bundle.json')

    def test_wrong_embedded_index_is_rejected_even_when_local_file_anchors_match(self):
        proto=json.loads((self.candidate/'protocol.json').read_text());proto['indexCode']='SP500'
        self.write(self.candidate/'protocol.json',proto)
        archive=json.loads((self.candidate/'cohort.json').read_text());archive['protocolSha256']=q.sha(self.candidate/'protocol.json')
        self.write(self.candidate/'cohort.json',archive)
        self.write(self.candidate/'pending-evaluation.json',dict(protocolSha256=q.sha(self.candidate/'protocol.json'),cohortSha256=q.sha(self.candidate/'cohort.json')))
        with self.assertRaises(ValueError):q.prepare_bundle(self.candidate,self.root/'wrong-index.json')

    def test_output_is_create_only(self):
        before=self.bundle.read_bytes()
        with self.assertRaises(FileExistsError):q.prepare_bundle(self.candidate,self.bundle)
        self.assertEqual(before,self.bundle.read_bytes())


if __name__=='__main__':unittest.main()
