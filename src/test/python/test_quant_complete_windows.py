import copy
import importlib.util
import json
from pathlib import Path
import unittest

PATH = Path(__file__).resolve().parents[3] / 'scripts/quant_complete_windows.py'


class CompleteWindowsTest(unittest.TestCase):
    def module(self):
        self.assertTrue(PATH.exists(), 'Missing fail-closed complete-window implementation')
        spec = importlib.util.spec_from_file_location('complete_windows', PATH)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_full_population_return_is_equal_weight_not_available_member_subset(self):
        m = self.module()
        prices = {'A': {'2026-01-02': 100, '2026-01-05': 110, '2026-01-06': 120},
                  'B': {'2026-01-02': 100, '2026-01-05': 90, '2026-01-06': 80}}
        result = m.price_window(['A', 'B'], list(prices['A']), prices, {'A'})
        self.assertTrue(result['priceComplete'])
        self.assertAlmostEqual(0, result['grossReturnPct'])
        self.assertFalse(result['quantForecastComplete'])
        self.assertEqual(['B'], result['missingForecasts'])

    def test_missing_interior_session_blocks_even_when_both_endpoints_exist(self):
        m = self.module()
        result = m.price_window(['A'], ['2026-01-02', '2026-01-05', '2026-01-06'],
                                {'A': {'2026-01-02': 100, '2026-01-06': 120}}, {'A'})
        self.assertFalse(result['priceComplete'])
        self.assertIsNone(result['grossReturnPct'])
        self.assertEqual({'A': ['2026-01-05']}, result['missingPrices'])

    def test_no_price_does_not_become_zero_or_remove_member(self):
        m = self.module()
        result = m.price_window(['A', 'B'], ['2026-01-02', '2026-01-05'],
                                {'A': {'2026-01-02': 100, '2026-01-05': 110}}, {'A', 'B'})
        self.assertEqual(2, result['targetMembers'])
        self.assertIsNone(result['grossReturnPct'])
        self.assertFalse(result['quantForecastComplete'])

    def test_duplicate_members_and_invalid_price_or_calendar_rejected(self):
        m = self.module()
        for members, sessions, prices in [(['A', 'A'], ['2026-01-02', '2026-01-05'], {}),
                (['A'], ['2026-01-05', '2026-01-02'], {}),
                (['A'], ['2026-01-02', '2026-01-05'], {'A': {'2026-01-02': 0, '2026-01-05': 1}})]:
            with self.subTest(members=members, sessions=sessions):
                with self.assertRaises(ValueError):
                    m.price_window(members, sessions, prices, set())

    def chart(self):
        return {'chart': {'error': None, 'result': [{'meta': {'symbol': 'TEAM', 'currency': 'USD',
            'instrumentType': 'EQUITY', 'exchangeTimezoneName': 'America/New_York'},
            'timestamp': [1767373200, 1767632400], 'indicators': {'quote': [{'open': [99, 109],
            'high': [101, 111], 'low': [98, 108], 'close': [100, 110], 'volume': [1000, 2000]}]}}]}}

    def test_chart_uses_local_exchange_date_and_quote_not_dividend_adjusted_close(self):
        m = self.module()
        payload = self.chart()
        payload['chart']['result'][0]['indicators']['adjclose'] = [{'adjclose': [1, 999]}]
        result = m.parse_chart(json.dumps(payload).encode(), 'TEAM')
        self.assertEqual(100, result['2026-01-02'])
        self.assertEqual(110, result['2026-01-05'])

    def test_chart_refuses_wrong_symbol_currency_duplicate_dates_and_bad_ohlc(self):
        m = self.module()
        for mutation in ['symbol', 'currency', 'duplicate', 'ohlc']:
            payload = self.chart()
            r = payload['chart']['result'][0]
            if mutation == 'symbol': r['meta']['symbol'] = 'OTHER'
            if mutation == 'currency': r['meta']['currency'] = 'GBP'
            if mutation == 'duplicate': r['timestamp'][1] = r['timestamp'][0]
            if mutation == 'ohlc': r['indicators']['quote'][0]['high'][0] = 50
            with self.subTest(mutation=mutation):
                with self.assertRaises(ValueError):
                    m.parse_chart(json.dumps(payload).encode(), 'TEAM')

    def test_nonoverlapping_cash_to_cash_compounding_and_roundtrip_costs(self):
        m = self.module()
        periods = [dict(decision='2026-01-01', exit='2026-01-30', grossReturnPct=10, benchmarkReturnPct=5),
                   dict(decision='2026-01-29', exit='2026-02-26', grossReturnPct=999, benchmarkReturnPct=999),
                   dict(decision='2026-01-30', exit='2026-03-02', grossReturnPct=-10, benchmarkReturnPct=0)]
        result = m.compound_periods(periods, 10)
        self.assertEqual(2, result['periodCount'])
        self.assertAlmostEqual(-1, result['grossReturnPct'])
        self.assertAlmostEqual(-1.3996, result['netReturnPct'])
        self.assertAlmostEqual(5, result['benchmarkReturnPct'])
        self.assertEqual(['2026-01-01', '2026-01-30'], result['decisions'])

    def test_missing_benchmark_level_blocks_only_its_own_window_without_deleting_session(self):
        m = self.module()
        levels = {'2026-01-02': 100, '2026-01-05': 105, '2026-01-06': 110, '2026-01-07': None}
        self.assertAlmostEqual(10, m.benchmark_return(levels, ['2026-01-02', '2026-01-05', '2026-01-06']))
        self.assertIsNone(m.benchmark_return(levels, ['2026-01-05', '2026-01-06', '2026-01-07']))
        self.assertIsNone(m.benchmark_return(levels, ['2026-01-02', '2026-01-07', '2026-01-06']))

    def test_new_prices_are_added_only_with_compatible_overlap_and_never_overwrite_canonical(self):
        m = self.module()
        current = {'2026-01-02': 100, '2026-01-05': 110}
        added = {'2026-01-02': 100.0000001, '2026-01-05': 110, '2026-01-06': 120}
        self.assertEqual({'2026-01-02': 100, '2026-01-05': 110, '2026-01-06': 120}, m.merge_prices(current, added))
        with self.assertRaises(ValueError):
            m.merge_prices(current, {'2026-01-05': 55, '2026-01-06': 60})
        with self.assertRaises(ValueError):
            m.merge_prices(current, {'2026-01-06': 120})
        self.assertEqual(added, m.merge_prices({}, added))


if __name__ == '__main__':
    unittest.main()
