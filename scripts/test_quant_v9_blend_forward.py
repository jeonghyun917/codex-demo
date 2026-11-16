import copy
import importlib
import unittest

try:q=importlib.import_module('quant_v9_blend_forward')
except ModuleNotFoundError:q=None


def fixture():
    base=dict(symbol='A',indexCode='DOW30',signalDate='2026-10-31',horizonDays=20,
              modelVersion='EXPECTED_RETURN_V9_CORRECTED_20261017',source='LIVE_V9_SEC_SOURCE_CUTOFF_LT_20261031',
              expectedReturnPct=1.,expectedExcessReturnPct=2.)
    upstream={**base,'modelVersion':'EXPECTED_RETURN_V5_CORRECTED_20261017',
              'source':'LIVE_REGIME_NONLINEAR_INTERACTION_EXPECTED_RETURN_V5_CUTOFF_LT_20261031',
              'expectedReturnPct':3.,'expectedExcessReturnPct':4.}
    cohort=dict(indexCode='DOW30',originalSignalDate='2026-10-31',anchorSession='2026-10-30',entrySession='2026-11-02',exitSession='2026-12-01',predictions=[base])
    capture=dict(indexCode='DOW30',originalSignalDate='2026-10-31',entryCloseUtc='2026-11-02T21:00:00Z',
                 capturedAtUtc='2026-11-01T07:00:00Z',upstreamV5=[upstream],databaseBefore={'n':1},databaseAfter={'n':1},databaseWrites=0)
    return cohort,capture


class BlendForwardTest(unittest.TestCase):
    def setUp(self):self.assertIsNotNone(q,'Prospective candidate freeze implementation missing')

    def test_literal_candidate_forecasts_keep_whole_baseline_and_no_labels(self):
        cohort,capture=fixture();out=q.build_predictions(cohort,capture,0.,'2026-11-01T07:01:00Z')
        self.assertEqual(out[0]['baselineV9'],[1.,2.])
        self.assertEqual(out[0]['half_upstream'],[2.,3.])
        self.assertEqual(out[0]['past_mae_blend'],[1.,2.])
        self.assertNotIn('actual',out[0]);self.assertEqual(len(out),1)

    def test_partial_duplicate_research_wrong_cutoff_or_db_mutation_is_rejected(self):
        cohort,capture=fixture()
        for mutate in [lambda x:x.update(upstreamV5=[]),lambda x:x['upstreamV5'].append(copy.deepcopy(x['upstreamV5'][0])),
                       lambda x:x['upstreamV5'][0].update(source='RESEARCH_BACKFILL'),
                       lambda x:x['upstreamV5'][0].update(signalDate='2026-11-01'),
                       lambda x:x.update(databaseWrites=1)]:
            bad=copy.deepcopy(capture);mutate(bad)
            with self.assertRaises(ValueError):q.build_predictions(cohort,bad,0.,'2026-11-01T07:01:00Z')

    def test_freeze_at_or_after_entry_and_before_upstream_capture_is_rejected(self):
        cohort,capture=fixture()
        for now in ['2026-11-02T21:00:00Z','2026-11-03T00:00:00Z','2026-11-01T06:00:00Z']:
            with self.assertRaises(ValueError):q.build_predictions(cohort,capture,0.,now)


if __name__=='__main__':unittest.main()
