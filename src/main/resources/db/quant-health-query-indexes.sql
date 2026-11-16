-- Additive MariaDB migration for existing databases. No values or model semantics change.
-- Counts/distinct keys/freshness are covered without fetching every wide historical row.
-- The expected-return key also supports the latest model prediction per stock and horizon.
-- Kept separate because production startup may intentionally disable SQL initialization.
CREATE INDEX IF NOT EXISTS idx_stock_er_health_cover
    ON stock_expected_return_snapshot (index_code, model_version, symbol, horizon_days, signal_date, calculated_at);

CREATE INDEX IF NOT EXISTS idx_stock_er_factor_health_cover
    ON stock_expected_return_factor_contribution (index_code, model_version, calculated_at);

CREATE INDEX IF NOT EXISTS idx_stock_market_health_cover
    ON stock_market_snapshot (index_code, snapshot_date, calculated_at);

CREATE INDEX IF NOT EXISTS idx_stock_membership_health_cover
    ON stock_index_membership_snapshot (index_code, is_member, source, snapshot_date, calculated_at);

CREATE INDEX IF NOT EXISTS idx_stock_shares_health_cover
    ON stock_shares_outstanding_snapshot (index_code, source, snapshot_date, calculated_at);

CREATE INDEX IF NOT EXISTS idx_stock_factor_health_cover
    ON stock_factor_exposure_snapshot (index_code, signal_date, calculated_at);

CREATE INDEX IF NOT EXISTS idx_stock_covariance_health_cover
    ON stock_covariance_snapshot (index_code, snapshot_date, calculated_at);

-- Supersede only the two narrow indexes created by the initial diagnostic migration in this task.
-- Neither existed before this task; no original application index is removed.
DROP INDEX IF EXISTS idx_stock_er_health_model ON stock_expected_return_snapshot;
DROP INDEX IF EXISTS idx_stock_er_factor_health_model ON stock_expected_return_factor_contribution;
