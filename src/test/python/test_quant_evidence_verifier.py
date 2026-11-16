import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class EvidenceVerifierTest(unittest.TestCase):
    def run_fixture(self, ratio):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            scripts = root / 'scripts'
            scripts.mkdir()
            original = Path(__file__).resolve().parents[3] / 'scripts/verify_quant_actions.py'
            shutil.copyfile(original, scripts / original.name)
            data = root / 'tmp/pit-acquisition/actions'
            data.mkdir(parents=True)
            (data / 'raw.json').write_bytes(b'abc')
            source = dict(id='sample', path='raw.json', sha256=hashlib.sha256(b'abc').hexdigest())
            event = dict(sourceId='sample', symbol='APH', action='SPLIT', effectiveDate='2024-06-12',
                         numerator=2, denominator=1, ratioBasis=ratio)
            raw = json.dumps(dict(sources=[source], events=[event]))
            (data / 'bundle.json').write_text(raw)
            official = dict(sourceId='sample', symbol='APH', action='SPLIT', effectiveDate='2024-06-12',
                            numerator=2, denominator=1, ratioBasis='OFFICIAL_SHARE_RATIO')
            (data / 'official-bundle.json').write_text(json.dumps(dict(sources=[source], events=[official])))
            (data / 'manifest.json').write_text(json.dumps(dict(results=[])))
            run = subprocess.run([sys.executable, str(scripts / original.name)], capture_output=True, text=True)
            return run, raw, (data / 'bundle.json').read_text()

    def test_rejects_wrong_ratio_basis_instead_of_relabeling_it(self):
        run, before, after = self.run_fixture('OFFICIAL_SHARE_RATIO')
        self.assertNotEqual(0, run.returncode)
        self.assertEqual(before, after)

    def test_verification_never_rewrites_original_bundle(self):
        run, before, after = self.run_fixture('PROVIDER_ADJUSTMENT_FACTOR_UNVERIFIED')
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertEqual(before, after)


if __name__ == '__main__':
    unittest.main()
