"""Behavioral boundaries for sealed temporal evaluation, not source-text checks."""
import unittest
import tempfile
import contextlib
import io
import json
from pathlib import Path
from unittest.mock import patch
import quant_new_period as q


class NewPeriodTest(unittest.TestCase):
    def test_partial_old_artifact_dates_are_not_silently_ignored(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'partial.json'
            path.write_text('{"predictions":[{"signalDate":"2026-04-30"},', encoding='utf-8')
            dates, status = q.artifact_dates(path)
            self.assertEqual(dates, ['2026-04-30'])
            self.assertEqual(status, 'INCOMPLETE_JSON_DATE_TOKENS_ONLY')

    def test_strict_fixed_training_cutoff_excludes_equal_exit_and_future_signal(self):
        rows = [dict(signalDate='2026-03-01', exitTradeDate='2026-04-29', horizonDays=20),
                dict(signalDate='2026-03-01', exitTradeDate='2026-04-30', horizonDays=20),
                dict(signalDate='2026-05-01', exitTradeDate='2026-03-01', horizonDays=20),
                dict(signalDate='2026-03-01', exitTradeDate=None, horizonDays=20)]
        self.assertEqual(q.mature_rows(rows, '2026-04-30'), rows[:1])

    def test_roster_uses_effective_dates_not_announcement_or_future_members(self):
        base = ['TEAM', 'CSGP', 'AAPL']
        self.assertEqual(q.population(base, '2026-04-19'), ['AAPL', 'CSGP', 'TEAM'])
        self.assertEqual(q.population(base, '2026-04-30'), ['AAPL', 'CSGP', 'SNDK'])
        self.assertEqual(q.population(base, '2026-05-29'), ['AAPL', 'LITE', 'SNDK'])

    def test_complete_pairs_rejects_duplicate_missing_and_nonfinite(self):
        pair = dict(symbol='AAPL', actual=2, benchmark=1, oldAbsolute=0,
                    oldExcess=0, newAbsolute=1, newExcess=1)
        q.validate_pairs([pair], ['AAPL'])
        for rows, names in [([pair, pair], ['AAPL']), ([pair], ['AAPL', 'TEAM']),
                            ([dict(pair, actual=float('nan'))], ['AAPL'])]:
            with self.assertRaises(ValueError):
                q.validate_pairs(rows, names)

    def test_mae_uses_all_actual_pairs_not_only_successes(self):
        self.assertAlmostEqual(q.error_metrics([0, 2], [2, 6])['maePp'], 3)
        with self.assertRaises(ValueError):
            q.error_metrics([], [])

    def test_repaired_training_preserves_labels_and_denominator(self):
        row = dict(symbol='AAPL', signalDate='2023-03-31', horizonDays=20,
                   entryTradeDate='2023-04-03', exitTradeDate='2023-05-01',
                   forwardReturnPct=3, benchmarkReturnPct=1)
        q.validate_training_pair([row], [dict(row, qualityScore=70)])
        for altered in ([dict(row, forwardReturnPct=4)], [], [row, row]):
            with self.assertRaises(ValueError):
                q.validate_training_pair([row], altered)

    def test_full_evaluator_fits_only_training_when_holdout_returns_change(self):
        # Mutating test outcomes must change measured error, never fitted weights.
        def run(actual):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                protocol = dict(hashes={}, decisions=q.DATES, trainingRows=2,
                                populations={day:['AAPL'] for day in q.DATES}, limitations=[])
                q.save_new(root/'amended-protocol.json', protocol)
                training = []
                for day, score, ret in [('2023-01-31',40,2),('2023-03-31',60,4)]:
                    row = dict(symbol='AAPL', signalDate=day, horizonDays=20,
                               entryTradeDate=day, exitTradeDate='2023-05-01',
                               forwardReturnPct=ret, benchmarkReturnPct=1)
                    row.update({f+'Score':score for f in q.FACTORS})
                    training.append(row)
                signal = dict(symbol='AAPL', **{f+'Score':60 for f in q.FACTORS})
                pair = dict(symbol='AAPL', actual=actual, benchmark=1,
                            oldAbsolute=0, oldExcess=0, newAbsolute=0, newExcess=0)
                report = dict(protocolSha256=q.sha(root/'amended-protocol.json'),
                              trainingOld=training, trainingNew=training,
                              dates=[dict(decision=day, pairs=[pair], newSignals=[signal]) for day in q.DATES])
                q.save_new(root/'replay.json', report)
                with patch.object(q,'ROOT',root), contextlib.redirect_stdout(io.StringIO()):
                    q.evaluate(root/'replay.json')
                return json.loads((root/'evaluation.json').read_text())
        first, changed = run(5), run(-50)
        self.assertEqual(first['fittedOnlyOnPrecutoff'], changed['fittedOnlyOnPrecutoff'])
        self.assertAlmostEqual(first['fittedOnlyOnPrecutoff']['Absolute']['median'], 3)
        self.assertAlmostEqual(first['fittedOnlyOnPrecutoff']['Excess']['median'], 2)
        self.assertNotEqual(first['metrics'], changed['metrics'])


if __name__ == '__main__':
    unittest.main()
