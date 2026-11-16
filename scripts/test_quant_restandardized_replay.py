import copy
import unittest

import verify_quant_restandardized_replay as replay


class ReplayVerificationTests(unittest.TestCase):
    def test_paired_mae_uses_identical_outcomes(self):
        rows = [dict(symbol='A', actual=4, benchmark=1, oldAbsolute=1,
                     newAbsolute=3, oldExcess=0, newExcess=2)]
        result = replay.paired_metrics(rows)
        self.assertEqual(result['absolute']['oldMaePp'], 3)
        self.assertEqual(result['absolute']['newMaePp'], 1)
        self.assertEqual(result['absolute']['maeDeltaPp'], -2)
        self.assertEqual(result['excess']['newMaePp'], 1)

    def test_duplicate_symbols_are_rejected(self):
        row = dict(symbol='A', actual=4, benchmark=1, oldAbsolute=1,
                   newAbsolute=3, oldExcess=0, newExcess=2)
        with self.assertRaises(ValueError):
            replay.paired_metrics([row, copy.copy(row)])

    def test_nonfinite_and_empty_are_rejected(self):
        with self.assertRaises(ValueError):
            replay.paired_metrics([])
        with self.assertRaises(ValueError):
            replay.paired_metrics([dict(symbol='A', actual=float('nan'), benchmark=1,
                                       oldAbsolute=1, newAbsolute=2, oldExcess=0, newExcess=1)])


if __name__ == '__main__':
    unittest.main()
