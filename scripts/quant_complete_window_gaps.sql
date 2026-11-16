-- Read-only key-level gap inventory. Forecasts are descriptive reconstructions, not PIT.
WITH sessions AS (
 SELECT index_code,trade_date entry_date,
 LAG(trade_date) OVER(PARTITION BY index_code ORDER BY trade_date) previous_session,
 LEAD(trade_date,20) OVER(PARTITION BY index_code ORDER BY trade_date) exit_date
 FROM stock_benchmark_return_series
 WHERE index_code IN ('SP500_MACRO_FREE_20261018','NASDAQ100_MACRO_FREE_20261018')
 AND trade_date<='2026-10-18'
)
SELECT m.index_code,m.snapshot_date,m.symbol,m.source membership_source,w.entry_date,w.exit_date,
 p.model_version,p.source prediction_source,s.id signal_id,r.snapshot_id outcome_id,
 r.entry_trade_date actual_entry,r.exit_trade_date actual_exit,r.forward_return_pct,
 (SELECT COUNT(*) FROM stock_candle_daily c WHERE c.symbol=m.symbol
 AND c.trade_date BETWEEN w.entry_date AND w.exit_date) candle_count,
 (SELECT c.close_price FROM stock_candle_daily c WHERE c.symbol=m.symbol AND c.trade_date=w.entry_date) entry_close,
 (SELECT c.close_price FROM stock_candle_daily c WHERE c.symbol=m.symbol AND c.trade_date=w.exit_date) exit_close,
 CASE WHEN p.symbol IS NULL THEN 'MISSING_PREDICTION'
 WHEN r.snapshot_id IS NULL THEN 'MISSING_OUTCOME'
 WHEN r.entry_trade_date<>w.entry_date OR r.exit_trade_date<>w.exit_date THEN 'WRONG_OUTCOME_WINDOW'
 ELSE 'CHECK_VALUES' END primary_gap
FROM stock_index_membership_snapshot m
JOIN sessions w ON w.index_code=m.index_code AND m.snapshot_date>=w.previous_session
 AND m.snapshot_date<w.entry_date AND w.exit_date IS NOT NULL
LEFT JOIN stock_expected_return_snapshot p ON p.index_code=m.index_code AND p.symbol=m.symbol
 AND p.signal_date=m.snapshot_date AND p.horizon_days=20 AND p.model_version='EXPECTED_RETURN_V9_CORRECTED_20261017'
LEFT JOIN stock_signal_snapshot s ON s.symbol=m.symbol AND s.signal_date=m.snapshot_date
LEFT JOIN stock_backtest_result_v2 r ON r.snapshot_id=s.id AND r.horizon_days=20
WHERE m.index_code IN ('SP500_MACRO_FREE_20261018','NASDAQ100_MACRO_FREE_20261018') AND m.is_member=TRUE
 AND (p.symbol IS NULL OR r.snapshot_id IS NULL OR r.entry_trade_date<>w.entry_date OR r.exit_trade_date<>w.exit_date
 OR r.forward_return_pct IS NULL OR p.expected_excess_return_pct IS NULL)
ORDER BY m.index_code,m.snapshot_date,m.symbol;
