-- Read-only independent endpoint-level calculation; no model or dataset writes.
WITH variants AS (
 SELECT 'SP500' universe,'baseline' variant,'SP500_MACRO_FREE_20261018' research_index,'SP500_FREE_20261018' forecast_index
 UNION ALL SELECT 'SP500','asofMacro','SP500_MACRO_FREE_20261018','SP500_MACRO_FREE_20261018'
 UNION ALL SELECT 'NASDAQ100','baseline','NASDAQ100_MACRO_FREE_20261018','NASDAQ100_FREE_20261018'
 UNION ALL SELECT 'NASDAQ100','asofMacro','NASDAQ100_MACRO_FREE_20261018','NASDAQ100_MACRO_FREE_20261018'
), sessions AS (
 SELECT index_code,trade_date entry_date,benchmark_level entry_level,
 LAG(trade_date) OVER(PARTITION BY index_code ORDER BY trade_date) previous_session,
 LEAD(trade_date,20) OVER(PARTITION BY index_code ORDER BY trade_date) exit_date,
 LEAD(benchmark_level,20) OVER(PARTITION BY index_code ORDER BY trade_date) exit_level
 FROM stock_benchmark_return_series
 WHERE index_code IN ('SP500_MACRO_FREE_20261018','NASDAQ100_MACRO_FREE_20261018') AND trade_date<='2026-10-18'
), windows AS (
 SELECT s.index_code,s.entry_date,s.previous_session,s.exit_date,100*(s.exit_level/s.entry_level-1) benchmark_return
 FROM sessions s JOIN stock_benchmark_return_series b ON b.index_code=s.index_code
 AND b.trade_date>s.entry_date AND b.trade_date<=s.exit_date
 WHERE s.entry_level>0 AND s.exit_level>0
 GROUP BY s.index_code,s.entry_date,s.previous_session,s.exit_date,s.entry_level,s.exit_level
 HAVING COUNT(*)=20 AND COUNT(b.return_pct)=20 AND MIN(b.return_pct)>=-100
 AND COUNT(DISTINCT b.source)=1 AND MAX(b.trade_date)=s.exit_date
), pairs AS (
 SELECT v.universe,v.variant,e.model_version,e.expected_excess_return_pct predicted_excess,
 r.forward_return_pct-w.benchmark_return actual_excess,e.calibrated_upside_probability_pct/100 probability
 FROM variants v JOIN windows w ON w.index_code=v.research_index
 JOIN stock_index_membership_snapshot m ON m.index_code=w.index_code AND m.is_member=TRUE
 AND m.snapshot_date>=w.previous_session AND m.snapshot_date<w.entry_date
 JOIN stock_signal_snapshot s ON s.symbol=m.symbol AND s.signal_date=m.snapshot_date
 JOIN stock_backtest_result_v2 r ON r.snapshot_id=s.id AND r.horizon_days=20
 AND r.symbol=m.symbol AND r.signal_date=m.snapshot_date AND r.entry_trade_date=w.entry_date AND r.exit_trade_date=w.exit_date
 JOIN stock_expected_return_snapshot e ON e.index_code=v.forecast_index AND e.symbol=m.symbol
 AND e.signal_date=m.snapshot_date AND e.horizon_days=20
 WHERE r.forward_return_pct>=-100 AND e.calculated_at IS NOT NULL
 AND e.expected_return_pct IS NOT NULL AND e.expected_excess_return_pct IS NOT NULL
 AND e.calibrated_upside_probability_pct BETWEEN 0 AND 100 AND e.excess_p10_pct IS NOT NULL
 AND e.excess_p90_pct IS NOT NULL AND e.excess_p10_pct<=e.excess_p90_pct
 AND e.model_version IN ('EXPECTED_RETURN_V7_CORRECTED_20261017','EXPECTED_RETURN_V8_CORRECTED_20261017','EXPECTED_RETURN_V9_CORRECTED_20261017')
)
SELECT universe,variant,model_version,COUNT(*) usable_20d,
 AVG(POW(probability-IF(actual_excess>0,1,0),2)) brier,
 AVG(ABS(predicted_excess-actual_excess)) mae_pct,
 100*AVG(IF((predicted_excess>0 AND actual_excess>0) OR (predicted_excess<0 AND actual_excess<0)
 OR (predicted_excess=0 AND actual_excess=0),1,0)) direction_pct
FROM pairs GROUP BY universe,variant,model_version ORDER BY universe,variant,model_version;
