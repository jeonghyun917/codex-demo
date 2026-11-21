"""Offline regression tests for evidence-only foreign history queue."""
import hashlib
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

import quant_free_history_queue as queue


class EvidenceQueueTests(unittest.TestCase):
    def test_half_year_and_nine_month_are_not_standalone_quarters(self):
        self.assertEqual(queue.period_kind('2025-01-01', '2025-06-30'), 'HALF_YEAR')
        self.assertEqual(queue.period_kind('2025-01-01', '2025-09-30'), 'NINE_MONTH')
        self.assertEqual(queue.period_kind('2025-07-01', '2025-09-30'), 'STANDALONE_QUARTER')
        self.assertEqual(queue.period_kind(None, '2025-09-30'), 'INSTANT')
        self.assertEqual(queue.period_kind('2024-01-01', '2024-12-31'), 'ANNUAL')

    def test_wrong_hash_rejected_before_source_is_used(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'facts.json'
            path.write_text('{"cik":123,"facts":{}}')
            with self.assertRaisesRegex(ValueError, 'SHA256'):
                queue.read_verified({'path': str(path), 'sha256': '0'*64}, 123)

    def test_wrong_cik_rejected_despite_valid_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'facts.json'
            body = b'{"cik":999,"facts":{}}'
            path.write_bytes(body)
            with self.assertRaisesRegex(ValueError, 'CIK'):
                queue.read_verified({'path': str(path), 'sha256': hashlib.sha256(body).hexdigest()}, 123)

    def test_facts_preserve_original_units_and_do_not_invent_currency_conversion(self):
        facts = {'facts': {'ifrs-full': {'Revenue': {'units': {'EUR': [
            {'start': '2024-01-01', 'end': '2024-12-31', 'filed': '2025-02-20',
             'accn': 'A', 'form': '20-F', 'val': 100}]}}}}}
        rows = queue.selected_observations(facts, 'A', '2025-02-20')
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['unit'], 'EUR')
        self.assertEqual(rows[0]['original']['val'], 100)
        self.assertEqual(rows[0]['periodKind'], 'ANNUAL')
        self.assertFalse(rows[0]['acceptedForModel'])

    def test_submission_filing_date_mismatch_is_rejected(self):
        facts = {'facts': {'ifrs-full': {'Revenue': {'units': {'USD': [
            {'start': '2024-01-01', 'end': '2024-12-31', 'filed': '2025-03-21',
             'accn': 'A', 'form': '20-F', 'val': 100}]}}}}}
        with self.assertRaisesRegex(ValueError, 'filing date'):
            queue.selected_observations(facts, 'A', '2025-03-20')

    def test_future_period_fact_is_rejected(self):
        facts = {'facts': {'ifrs-full': {'Assets': {'units': {'USD': [
            {'end': '2025-06-30', 'filed': '2025-03-20', 'accn': 'A', 'form': '20-F', 'val': 100}]}}}}}
        with self.assertRaisesRegex(ValueError, 'period end'):
            queue.selected_observations(facts, 'A', '2025-03-20')

    def test_filing_url_uses_known_sec_archive_and_rejects_path_traversal(self):
        self.assertEqual(queue.filing_url(123, '0000000123-25-000001', 'abc.htm'),
                         'https://www.sec.gov/Archives/edgar/data/123/000000012325000001/abc.htm')
        for path in ('../abc.htm', 'https://evil.example/abc.htm', 'a/b.htm'):
            with self.assertRaises(ValueError):
                queue.filing_url(123, '0000000123-25-000001', path)

    def test_access_denial_and_throttling_stop_before_second_document(self):
        entries = [{'symbol': 'GFS', 'accession': accession,
                    'primaryDocumentUrl': f'https://www.sec.gov/Archives/{accession}'}
                   for accession in ['0001709048-25-000024', '0001709048-25-000071']]
        for status in (401, 403, 429):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as directory:
                error = urllib.error.HTTPError(entries[0]['primaryDocumentUrl'], status, 'Denied', {}, None)
                with patch('quant_free_history_queue.urllib.request.urlopen', side_effect=error):
                    receipts = queue.download_gfs_documents(entries, Path(directory))
                self.assertEqual(len(receipts), 1)
                self.assertEqual(receipts[0]['status'], status)
                self.assertEqual(list(Path(directory).iterdir()), [])


if __name__ == '__main__':
    unittest.main()
