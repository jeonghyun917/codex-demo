-- Post-experiment read-only comparison with tmp/quant-macro-before-20261018.tsv.
SELECT index_code, COUNT(*) rows_total, MAX(calculated_at) latest,
       SUM(expected_excess_return_pct) excess_sum,
       SUM(calibrated_upside_probability_pct) probability_sum
FROM stock_expected_return_snapshot
WHERE index_code IN ('NASDAQ100_FREE_20261018','SP500_FREE_20261018')
GROUP BY index_code ORDER BY index_code;
SELECT index_code, COUNT(*) feature_rows, MAX(calculated_at) latest
FROM stock_macro_feature_snapshot
WHERE index_code IN ('NASDAQ100','SP500') GROUP BY index_code ORDER BY index_code;
SELECT COUNT(*) corrected_outcomes, SUM(forward_return_pct) return_sum FROM stock_backtest_result_v2;
