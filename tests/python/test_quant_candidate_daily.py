import importlib
import io
import json
import sys
import tempfile
import unittest
from datetime import datetime,timezone
from pathlib import Path
from unittest.mock import patch
from contextlib import redirect_stdout

sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
import quant_daily_forward as daily
import test_quant_v9_blend_evaluate as fixture_module


class CandidateDailyTest(unittest.TestCase):
    def setUp(self):
        # Reuse the complete synthetic source format, not a mock evaluator/report.
        self.fixture=fixture_module.CandidateEvaluationTest('test_output_is_create_only');self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)

    def run_controller(self,broken=False,cli=False):
        root=self.fixture.root;protocol_root=root/'data/quant-forward-validation-test'
        rows=self.fixture.sessions
        for index in daily.INDICES:
            folder=protocol_root/index;folder.mkdir(parents=True)
            protocol=daily.forward.seal(dict(indexCode=index,calendar=rows))
            daily.write_record(folder/'protocol.json',protocol)
        bundle=root/'data/quant-candidate-evaluation-test/DOW30/bundle.json'
        bundle.parent.mkdir(parents=True);bundle.write_bytes(self.fixture.bundle.read_bytes())
        if broken:
            value=json.loads(bundle.read_text());value['assembledAtUtc']='2099-01-01T00:00:00Z';bundle.write_text(json.dumps(value))
        class EmptyDatabase:
            def query(self,sql):return []
        with patch.object(daily,'ROOT',root),patch.object(daily,'Database',EmptyDatabase):
            # At the last calendar session there is no future-capture calendar;
            # pending original candidates must still be evaluated, without a batch.
            if cli:
                stdout=io.StringIO()
                with redirect_stdout(stdout):code=daily.main(['--run','--protocol-root',str(protocol_root)])
                return code,json.loads(stdout.getvalue())
            result=daily.execute(protocol_root,run=True,now=datetime(2026,2,3,7,tzinfo=timezone.utc))
        return result

    def test_daily_evaluation_discovers_candidates_without_new_forecasts_or_partial_scores(self):
        result=self.run_controller()
        self.assertEqual(result['status'],'COLLECTION_BLOCKED_CALENDAR_EVALUATION_COMPLETED')
        self.assertEqual(len(result.get('candidateEvaluations',[])),1,'Daily candidate stage is missing')
        candidate=result['candidateEvaluations'][0]
        self.assertEqual(candidate['status'],'INSUFFICIENT_COMPLETE_COHORT')
        self.assertEqual(candidate['expectedNames'],2);self.assertEqual(candidate['evaluatedNames'],0)
        self.assertIsNone(candidate['metrics']['half_upstream']['excess']['maePp'])
        self.assertFalse(list(self.fixture.root.rglob('attempt-*.json')))

    def test_candidate_tamper_is_explicit_failure_without_masking_baseline_results(self):
        result=self.run_controller(broken=True)
        self.assertEqual(result['status'],'FAILED_CANDIDATE_EVALUATION')
        self.assertEqual(result['evaluations'],[])
        self.assertEqual(result['candidateEvaluations'][0]['status'],'FAILED')

    def test_cli_returns_nonzero_for_rejected_candidate_bundle(self):
        self.assertIsNotNone(getattr(daily,'main',None),'CLI failure status handling missing')
        code,result=self.run_controller(broken=True,cli=True)
        self.assertEqual(code,1)
        self.assertEqual(result['status'],'FAILED_CANDIDATE_EVALUATION')


if __name__=='__main__':unittest.main()
