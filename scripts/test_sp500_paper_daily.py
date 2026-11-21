import json
import tempfile
from pathlib import Path
from unittest import TestCase
from sp500_paper_daily import verified_cached,extend_unchanged_membership,publish_prepared
from build_sp500_daily_dataset import digest


class DailyRecoveryTests(TestCase):
    def test_cached_response_is_reused_only_when_receipt_and_hash_match(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'sources').mkdir();(root/'raw').mkdir()
            raw=root/'raw/A.json';raw.write_text('{}')
            receipt=root/'sources/A.json';receipt.write_text(json.dumps(dict(status=200,path='raw/A.json',sha256=digest(raw))))
            self.assertEqual(verified_cached(root,'A')['status'],200)
            raw.write_text('{"changed":true}')
            with self.assertRaises(ValueError):verified_cached(root,'A')
            receipt.write_text(json.dumps(dict(status=429)))
            with self.assertRaises(ValueError):verified_cached(root,'A')

    def test_roster_change_never_silently_extends_membership(self):
        rows=[dict(symbol='A',start_date='2026-01-01',end_date='2026-11-13',is_member='true',provenance_ids='x')]
        with self.assertRaises(ValueError):extend_unchanged_membership(rows,{}, {'B'},'2026-11-13','2026-11-16')
        new=extend_unchanged_membership(rows,{}, {'A'},'2026-11-13','2026-11-16')
        self.assertEqual(new[0]['end_date'],'2026-11-16')
        self.assertEqual(rows[0]['end_date'],'2026-11-13')

    def test_publication_recovers_after_db_write_without_second_write(self):
        from unittest.mock import Mock
        target={'days':[],'accounts':[]}
        db=Mock();db.query.return_value=[[json.dumps(target).encode().hex()]]
        writer=Mock()
        publish_prepared(db,'7b7d',target,writer)
        writer.assert_not_called()

    def test_publication_refuses_concurrent_snapshot_change(self):
        from unittest.mock import Mock
        db=Mock();db.query.return_value=[['7b226f74686572223a317d']]
        writer=Mock()
        with self.assertRaises(ValueError):publish_prepared(db,'7b7d',{'days':[]},writer)
        writer.assert_not_called()

    def test_prepared_publication_requires_readback(self):
        from unittest.mock import Mock
        db=Mock();db.query.return_value=[['7b7d']]
        with self.assertRaises(ValueError):publish_prepared(db,'7b7d',{'days':[]},Mock())
