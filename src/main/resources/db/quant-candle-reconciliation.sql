-- Before/after audit for explicit complete-snapshot historical repairs.
CREATE TABLE IF NOT EXISTS stock_candle_reconciliation_run (
    reconciliation_id CHAR(36) NOT NULL PRIMARY KEY,
    source_manifest_sha256 CHAR(64) NOT NULL,
    price_basis VARCHAR(100) NOT NULL,
    report_json LONGTEXT NOT NULL,
    committed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS stock_candle_reconciliation_backup (
    reconciliation_id CHAR(36) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    old_open DECIMAL(24,6) NOT NULL, old_high DECIMAL(24,6) NOT NULL,
    old_low DECIMAL(24,6) NOT NULL, old_close DECIMAL(24,6) NOT NULL,
    old_volume BIGINT NULL, old_source VARCHAR(40) NOT NULL,
    old_created_at TIMESTAMP NOT NULL, old_updated_at TIMESTAMP NOT NULL,
    old_source_row_json LONGTEXT NULL,
    new_open DECIMAL(24,6) NOT NULL, new_high DECIMAL(24,6) NOT NULL,
    new_low DECIMAL(24,6) NOT NULL, new_close DECIMAL(24,6) NOT NULL,
    new_volume BIGINT NULL, source_content_sha256 CHAR(64) NOT NULL,
    PRIMARY KEY (reconciliation_id,symbol,trade_date),
    CONSTRAINT fk_candle_reconciliation_run FOREIGN KEY (reconciliation_id)
        REFERENCES stock_candle_reconciliation_run(reconciliation_id)
);
