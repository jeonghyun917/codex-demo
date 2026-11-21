from unittest import TestCase
from unittest.mock import patch
from refresh_sp500_watchlist_tracking import refresh_row,publish_tracking
from quant_daily_forward import Database

class RefreshTrackingTests(TestCase):
    def test_completed_outcomes_are_not_rewritten(self):
        old=dict(status='TP',symbol='A',net10=14.7)
        self.assertEqual(refresh_row(old,[],{},'2026-11-16'),old)

    def test_waiting_enters_on_next_open_and_can_stop_on_first_day(self):
        old=dict(status='WAITING_ENTRY',symbol='A',probabilityText='70.00%',filterPassed=False)
        prices={('A','2026-11-16'):dict(open=100,high=105,low=94,close=98,volume=100)}
        result=refresh_row(old,['2026-11-16'],prices,'2026-11-16')
        self.assertEqual(result['status'],'SL')
        self.assertEqual(result['entryPrice'],100)
        self.assertFalse(result['filterPassed'])

    def test_large_payload_is_sent_on_stdin_not_windows_command_line(self):
        with patch('refresh_sp500_watchlist_tracking.subprocess.run') as run:
            publish_tracking(Database(),'7b7d',{'large':'x'*40000})
        self.assertLess(len(str(run.call_args.args[0])),1000)
        self.assertGreater(len(run.call_args.kwargs['input']),40000)
