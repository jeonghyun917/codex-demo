-- Read-only verification of the 2026-10-18 frozen research datasets.
-- Run after replay; all dates are US equity session dates, not retrieval dates.
-- Optional: SET @research_namespace='NASDAQ100_FREE_20261018'; in this session
-- to verify one completed namespace. An unset/NULL variable includes both.
SELECT COUNT(*) AS corrected_outcomes,
       SUM(entry_close IS NULL OR exit_close IS NULL OR entry_close<=0 OR exit_close<=0) AS invalid_prices,
       SUM(signal_date IS NULL OR entry_trade_date IS NULL OR exit_trade_date IS NULL
           OR entry_trade_date<=signal_date OR exit_trade_date<=entry_trade_date) AS invalid_date_order,
       SUM(forward_return_pct IS NULL OR ABS(forward_return_pct-(exit_close/entry_close-1)*100)>0.000001) AS arithmetic_mismatches
FROM stock_backtest_result_v2;

SELECT index_code,model_version,COUNT(*) AS predictions,COUNT(DISTINCT signal_date) AS decision_dates,
       MIN(signal_date) AS first_signal,MAX(signal_date) AS last_signal,
       SUM(source LIKE 'LIVE%') AS mislabeled_live,
       SUM(source IS NULL OR source NOT LIKE CONCAT('%CUTOFF_LT_',DATE_FORMAT(signal_date,'%Y%m%d'))) AS cutoff_tag_mismatches,
       SUM(calculated_at IS NULL OR DATE(calculated_at)<signal_date) AS invalid_recorded_date,
       SUM(expected_return_pct IS NULL OR expected_excess_return_pct IS NULL
           OR calibrated_upside_probability_pct IS NULL OR calibrated_upside_probability_pct NOT BETWEEN 0 AND 100
           OR excess_p10_pct IS NULL OR excess_p90_pct IS NULL OR excess_p10_pct>excess_p90_pct) AS invalid_prediction_values
FROM stock_expected_return_snapshot
WHERE index_code IN ('SP500_FREE_20261018','NASDAQ100_FREE_20261018')
  AND (@research_namespace IS NULL OR index_code=@research_namespace)
GROUP BY index_code,model_version ORDER BY index_code,model_version;

-- Independent numerical path: use endpoint FRED index levels rather than compound
-- the stored daily returns used by CompleteBenchmarkReturn. Full session windows,
-- member populations, timing, model versions and finite-field checks match the
-- descriptive Java diagnostics. This does NOT relax the strict PIT evaluator.
WITH sessions AS (
    SELECT index_code,trade_date AS entry_date,benchmark_level AS entry_level,
           LAG(trade_date) OVER(PARTITION BY index_code ORDER BY trade_date) AS previous_session,
           LEAD(trade_date,20) OVER(PARTITION BY index_code ORDER BY trade_date) AS exit_date,
           LEAD(benchmark_level,20) OVER(PARTITION BY index_code ORDER BY trade_date) AS exit_level
    FROM stock_benchmark_return_series
    WHERE index_code IN ('SP500_FREE_20261018','NASDAQ100_FREE_20261018')
      AND (@research_namespace IS NULL OR index_code=@research_namespace)
      AND trade_date<='2026-10-18'
), complete_windows AS (
    SELECT s.index_code,s.entry_date,s.previous_session,s.exit_date,
           100*(s.exit_level/s.entry_level-1) AS benchmark_return
    FROM sessions s JOIN stock_benchmark_return_series b
      ON b.index_code=s.index_code AND b.trade_date>s.entry_date AND b.trade_date<=s.exit_date
    WHERE s.entry_level>0 AND s.exit_level>0
    GROUP BY s.index_code,s.entry_date,s.previous_session,s.exit_date,s.entry_level,s.exit_level
    HAVING COUNT(*)=20 AND COUNT(b.return_pct)=20
       AND MIN(b.return_pct)>=-100 AND COUNT(DISTINCT b.source)=1
       AND MAX(b.trade_date)=s.exit_date
), pairs AS (
    SELECT e.index_code,e.model_version,e.signal_date,e.symbol,
           e.expected_excess_return_pct AS predicted_excess,
           r.forward_return_pct-w.benchmark_return AS actual_excess,
           e.calibrated_upside_probability_pct/100 AS probability
    FROM complete_windows w
    JOIN stock_index_membership_snapshot m
      ON m.index_code=w.index_code AND m.is_member=TRUE
     AND m.snapshot_date>=w.previous_session AND m.snapshot_date<w.entry_date
    JOIN stock_signal_snapshot source_signal
      ON source_signal.symbol=m.symbol AND source_signal.signal_date=m.snapshot_date
    JOIN stock_backtest_result_v2 r
      ON r.snapshot_id=source_signal.id AND r.horizon_days=20
     AND r.symbol=m.symbol AND r.signal_date=m.snapshot_date
     AND r.entry_trade_date=w.entry_date AND r.exit_trade_date=w.exit_date
    JOIN stock_expected_return_snapshot e
      ON e.index_code=m.index_code AND e.symbol=m.symbol AND e.signal_date=m.snapshot_date
     AND e.horizon_days=20
    WHERE r.forward_return_pct>=-100 AND e.calculated_at IS NOT NULL
      AND e.expected_return_pct IS NOT NULL AND e.expected_excess_return_pct IS NOT NULL
      AND e.calibrated_upside_probability_pct BETWEEN 0 AND 100
      AND e.excess_p10_pct IS NOT NULL AND e.excess_p90_pct IS NOT NULL
      AND e.excess_p10_pct<=e.excess_p90_pct
      AND e.model_version LIKE 'EXPECTED_RETURN_V%_CORRECTED_20261017'
)
SELECT index_code,model_version,COUNT(*) AS usable_20d,
       AVG(POW(probability-IF(actual_excess>0,1,0),2)) AS independently_recomputed_brier,
       AVG(ABS(predicted_excess-actual_excess)) AS independently_recomputed_mae_pct,
       100*AVG(IF((predicted_excess>0 AND actual_excess>0)
                   OR (predicted_excess<0 AND actual_excess<0)
                   OR (predicted_excess=0 AND actual_excess=0),1,0)) AS direction_accuracy_pct
FROM pairs GROUP BY index_code,model_version ORDER BY index_code,model_version;
