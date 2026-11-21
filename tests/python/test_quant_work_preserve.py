import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
try:
    import quant_work_preserve as q
except ModuleNotFoundError:
    q = None


class PreservationTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(q, "preservation utility is missing")

    def test_snapshot_survives_source_edit_and_reports_exact_original_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "script.py"
            source.write_bytes(b"original\r\n")
            result = q.create_snapshot(root, [source, source], root / "snapshot")
            source.write_bytes(b"new code")
            verified = q.verify_snapshot(root / "snapshot")
            self.assertEqual(verified["files"], 1)
            item = result["files"][0]
            self.assertEqual(item["path"], "script.py")
            self.assertEqual((root / "snapshot" / item["object"]).read_bytes(), b"original\r\n")
            self.assertEqual(source.read_bytes(), b"new code")

    def test_existing_output_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "one.py"
            source.write_bytes(b"one")
            q.create_snapshot(root, [source], root / "snapshot")
            before = (root / "snapshot/manifest.json").read_bytes()
            with self.assertRaises(FileExistsError):
                q.create_snapshot(root, [source], root / "snapshot")
            self.assertEqual((root / "snapshot/manifest.json").read_bytes(), before)

    def test_outside_or_missing_source_cannot_create_a_partial_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            root = base / "repo"
            root.mkdir()
            outside = base / "private.py"
            outside.write_bytes(b"outside")
            for path in [outside, root / "missing.py"]:
                with self.assertRaises(ValueError):
                    q.create_snapshot(root, [path], root / "snapshot")
                self.assertFalse((root / "snapshot").exists())

    def test_changed_object_and_manifest_path_escape_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "one.py"
            source.write_bytes(b"one")
            result = q.create_snapshot(root, [source], root / "snapshot")
            target = root / "snapshot" / result["files"][0]["object"]
            target.write_bytes(b"corrupt")
            with self.assertRaises(ValueError):
                q.verify_snapshot(root / "snapshot")
            target.write_bytes(b"one")
            manifest = root / "snapshot/manifest.json"
            value = json.loads(manifest.read_text())
            value["files"][0]["object"] = "../one.py"
            manifest.write_text(json.dumps(value))
            with self.assertRaises(ValueError):
                q.verify_snapshot(root / "snapshot")


if __name__ == "__main__":
    unittest.main()
