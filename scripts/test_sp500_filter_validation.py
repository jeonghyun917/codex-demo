import unittest
import pandas as pd
from sp500_filter_validation import retain_training, eligible_unseen_decisions


class ValidationTests(unittest.TestCase):
    def test_holdout_requires_membership_and_runoff(self):
        days=[f'{i:03}' for i in range(60)]
        self.assertEqual(eligible_unseen_decisions(days,'030','020'),[])
        self.assertEqual(eligible_unseen_decisions(days,'030','059'),days[31:40])

    def test_sampling_is_reproducible_and_prefix_causal(self):
        f=pd.DataFrame([dict(symbol='A',date=str(i)) for i in range(1000)])
        for seed in (0,1):
            a=retain_training(f,seed)
            self.assertTrue(a.equals(retain_training(f,seed)))
            self.assertTrue(a[a.index<500].equals(retain_training(f.iloc[:500],seed)))
            self.assertTrue(970<len(a)<1000)
        self.assertFalse(retain_training(f,0).equals(retain_training(f,1)))


if __name__=='__main__':unittest.main()
