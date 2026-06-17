CREATE TABLE IF NOT EXISTS main_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    label VARCHAR(100) NOT NULL,
    href VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_main_menu_code (code)
);

CREATE TABLE IF NOT EXISTS side_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    label VARCHAR(100) NOT NULL,
    href VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_side_menu_code (code)
);

CREATE TABLE IF NOT EXISTS stock_symbol (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    exchange VARCHAR(40) NULL,
    name VARCHAR(255) NULL,
    currency VARCHAR(10) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    collect_tier VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_collected_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_symbol_symbol (symbol),
    KEY idx_stock_symbol_active_collected (active, last_collected_at)
);

ALTER TABLE stock_symbol
    ADD COLUMN IF NOT EXISTS collect_tier VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_collected_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_stock_symbol_active_collected
    ON stock_symbol (active, last_collected_at);

CREATE TABLE IF NOT EXISTS index_constituent (
    index_code VARCHAR(40) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    name VARCHAR(255) NULL,
    exchange VARCHAR(80) NULL,
    sector VARCHAR(160) NULL,
    current_member BOOLEAN NOT NULL DEFAULT TRUE,
    first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at TIMESTAMP NULL,
    source VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, symbol),
    KEY idx_index_constituent_current (index_code, current_member)
);

CREATE TABLE IF NOT EXISTS company_profile (
    symbol VARCHAR(20) NOT NULL,
    name VARCHAR(255) NULL,
    country VARCHAR(80) NULL,
    currency VARCHAR(10) NULL,
    exchange VARCHAR(120) NULL,
    finnhub_industry VARCHAR(160) NULL,
    market_cap DECIMAL(24, 4) NULL,
    share_outstanding DECIMAL(24, 4) NULL,
    logo VARCHAR(1000) NULL,
    weburl VARCHAR(1000) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol)
);

CREATE TABLE IF NOT EXISTS stock_quote_cache (
    symbol VARCHAR(20) NOT NULL,
    current_price DECIMAL(24, 6) NULL,
    open_price DECIMAL(24, 6) NULL,
    high_price DECIMAL(24, 6) NULL,
    low_price DECIMAL(24, 6) NULL,
    previous_close DECIMAL(24, 6) NULL,
    quote_time TIMESTAMP NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol)
);

CREATE TABLE IF NOT EXISTS stock_candle_daily (
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    open_price DECIMAL(24, 6) NOT NULL,
    high_price DECIMAL(24, 6) NOT NULL,
    low_price DECIMAL(24, 6) NOT NULL,
    close_price DECIMAL(24, 6) NOT NULL,
    volume BIGINT NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'FINNHUB',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, trade_date),
    KEY idx_stock_candle_daily_date (trade_date)
);

CREATE TABLE IF NOT EXISTS stock_metric_snapshot (
    symbol VARCHAR(20) NOT NULL,
    metric_date DATE NOT NULL,
    pe_normalized_annual DECIMAL(24, 6) NULL,
    pb_annual DECIMAL(24, 6) NULL,
    roe_ttm DECIMAL(24, 6) NULL,
    eps_ttm DECIMAL(24, 6) NULL,
    week_52_high DECIMAL(24, 6) NULL,
    week_52_low DECIMAL(24, 6) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, metric_date)
);

CREATE TABLE IF NOT EXISTS company_news (
    symbol VARCHAR(20) NOT NULL,
    news_id BIGINT NOT NULL,
    headline VARCHAR(1000) NULL,
    summary TEXT NULL,
    url VARCHAR(1000) NULL,
    source VARCHAR(120) NULL,
    published_at TIMESTAMP NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, news_id),
    KEY idx_company_news_published_at (published_at)
);

CREATE TABLE IF NOT EXISTS stock_recommendation_trend (
    symbol VARCHAR(20) NOT NULL,
    period_date DATE NOT NULL,
    strong_buy INT NULL,
    buy INT NULL,
    hold INT NULL,
    sell INT NULL,
    strong_sell INT NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, period_date),
    KEY idx_stock_recommendation_period (period_date)
);

CREATE TABLE IF NOT EXISTS stock_eps_surprise (
    symbol VARCHAR(20) NOT NULL,
    period_date DATE NOT NULL,
    fiscal_year INT NULL,
    fiscal_quarter INT NULL,
    actual DECIMAL(24, 6) NULL,
    estimate DECIMAL(24, 6) NULL,
    surprise DECIMAL(24, 6) NULL,
    surprise_percent DECIMAL(24, 6) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, period_date),
    KEY idx_stock_eps_surprise_period (period_date)
);

CREATE TABLE IF NOT EXISTS stock_analysis_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    analysis_date DATE NOT NULL,
    summary VARCHAR(1000) NULL,
    trend_signal VARCHAR(40) NULL,
    valuation_signal VARCHAR(40) NULL,
    risk_signal VARCHAR(40) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_stock_analysis_symbol_date (symbol, analysis_date)
);

CREATE TABLE IF NOT EXISTS stock_signal_latest (
    symbol VARCHAR(20) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signal_version VARCHAR(20) NOT NULL DEFAULT 'v1',
    integrated_score INT NULL,
    integrated_label VARCHAR(80) NULL,
    tone VARCHAR(30) NULL,
    confidence VARCHAR(40) NULL,
    summary VARCHAR(1000) NULL,
    valuation_score INT NULL,
    valuation_label VARCHAR(120) NULL,
    quality_score INT NULL,
    quality_label VARCHAR(120) NULL,
    growth_score INT NULL,
    growth_label VARCHAR(120) NULL,
    stability_score INT NULL,
    stability_label VARCHAR(120) NULL,
    earnings_score INT NULL,
    earnings_label VARCHAR(120) NULL,
    analyst_score INT NULL,
    analyst_label VARCHAR(120) NULL,
    news_score INT NULL,
    news_label VARCHAR(120) NULL,
    momentum_score INT NULL,
    momentum_label VARCHAR(120) NULL,
    risk_score INT NULL,
    risk_label VARCHAR(120) NULL,
    institution_score INT NULL,
    institution_label VARCHAR(120) NULL,
    reasons_json LONGTEXT NULL CHECK (reasons_json IS NULL OR JSON_VALID(reasons_json)),
    cards_json LONGTEXT NULL CHECK (cards_json IS NULL OR JSON_VALID(cards_json)),
    source_freshness_json LONGTEXT NULL CHECK (source_freshness_json IS NULL OR JSON_VALID(source_freshness_json)),
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    PRIMARY KEY (symbol),
    KEY idx_stock_signal_latest_calculated (calculated_at),
    KEY idx_stock_signal_latest_score (integrated_score)
);

ALTER TABLE stock_signal_latest
    ADD COLUMN IF NOT EXISTS institution_score INT NULL,
    ADD COLUMN IF NOT EXISTS institution_label VARCHAR(120) NULL;

CREATE TABLE IF NOT EXISTS stock_signal_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    signal_date DATE NOT NULL,
    snapshot_mode VARCHAR(40) NOT NULL DEFAULT 'LIVE',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signal_version VARCHAR(20) NOT NULL DEFAULT 'v1',
    integrated_score INT NULL,
    integrated_label VARCHAR(80) NULL,
    tone VARCHAR(30) NULL,
    confidence VARCHAR(40) NULL,
    summary VARCHAR(1000) NULL,
    valuation_score INT NULL,
    valuation_label VARCHAR(120) NULL,
    quality_score INT NULL,
    quality_label VARCHAR(120) NULL,
    growth_score INT NULL,
    growth_label VARCHAR(120) NULL,
    stability_score INT NULL,
    stability_label VARCHAR(120) NULL,
    earnings_score INT NULL,
    earnings_label VARCHAR(120) NULL,
    analyst_score INT NULL,
    analyst_label VARCHAR(120) NULL,
    news_score INT NULL,
    news_label VARCHAR(120) NULL,
    momentum_score INT NULL,
    momentum_label VARCHAR(120) NULL,
    risk_score INT NULL,
    risk_label VARCHAR(120) NULL,
    institution_score INT NULL,
    institution_label VARCHAR(120) NULL,
    data_quality_score INT NULL,
    data_quality_excluded_metric_count INT NULL,
    data_quality_issue_count INT NULL,
    reasons_json LONGTEXT NULL CHECK (reasons_json IS NULL OR JSON_VALID(reasons_json)),
    cards_json LONGTEXT NULL CHECK (cards_json IS NULL OR JSON_VALID(cards_json)),
    source_freshness_json LONGTEXT NULL CHECK (source_freshness_json IS NULL OR JSON_VALID(source_freshness_json)),
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_signal_snapshot_day (symbol, signal_date, signal_version),
    KEY idx_stock_signal_snapshot_date (signal_date),
    KEY idx_stock_signal_snapshot_mode (snapshot_mode, signal_date),
    KEY idx_stock_signal_snapshot_score (integrated_score)
);

ALTER TABLE stock_signal_snapshot
    ADD COLUMN IF NOT EXISTS snapshot_mode VARCHAR(40) NOT NULL DEFAULT 'LIVE';

ALTER TABLE stock_signal_snapshot
    ADD COLUMN IF NOT EXISTS institution_score INT NULL,
    ADD COLUMN IF NOT EXISTS institution_label VARCHAR(120) NULL;

DROP INDEX IF EXISTS uk_stock_signal_snapshot_day ON stock_signal_snapshot;

CREATE UNIQUE INDEX IF NOT EXISTS uk_stock_signal_snapshot_mode
    ON stock_signal_snapshot (symbol, signal_date, signal_version, snapshot_mode);

CREATE INDEX IF NOT EXISTS idx_stock_signal_snapshot_mode
    ON stock_signal_snapshot (snapshot_mode, signal_date);

CREATE TABLE IF NOT EXISTS stock_signal_backtest_result (
    snapshot_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    signal_date DATE NOT NULL,
    horizon_days INT NOT NULL,
    entry_trade_date DATE NOT NULL,
    entry_close DECIMAL(24, 6) NOT NULL,
    exit_trade_date DATE NOT NULL,
    exit_close DECIMAL(24, 6) NOT NULL,
    forward_return_pct DECIMAL(18, 6) NOT NULL,
    integrated_score INT NULL,
    valuation_score INT NULL,
    quality_score INT NULL,
    growth_score INT NULL,
    stability_score INT NULL,
    earnings_score INT NULL,
    analyst_score INT NULL,
    news_score INT NULL,
    momentum_score INT NULL,
    risk_score INT NULL,
    institution_score INT NULL,
    data_quality_score INT NULL,
    sector VARCHAR(160) NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (snapshot_id, horizon_days),
    KEY idx_stock_signal_backtest_horizon (horizon_days),
    KEY idx_stock_signal_backtest_date (signal_date),
    KEY idx_stock_signal_backtest_return (forward_return_pct),
    CONSTRAINT fk_stock_signal_backtest_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES stock_signal_snapshot (id)
        ON DELETE CASCADE
);

ALTER TABLE stock_signal_backtest_result
    ADD COLUMN IF NOT EXISTS institution_score INT NULL;

CREATE TABLE IF NOT EXISTS stock_signal_weight_profile (
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(80) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (code),
    KEY idx_stock_signal_weight_profile_active (active)
);

CREATE TABLE IF NOT EXISTS stock_signal_weight_profile_item (
    profile_code VARCHAR(40) NOT NULL,
    factor VARCHAR(40) NOT NULL,
    weight DECIMAL(9, 4) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reason VARCHAR(1000) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_code, factor),
    KEY idx_stock_signal_weight_profile_item_sort (profile_code, sort_order),
    CONSTRAINT fk_stock_signal_weight_profile_item_profile
        FOREIGN KEY (profile_code) REFERENCES stock_signal_weight_profile (code)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS stock_data_quality_latest (
    symbol VARCHAR(20) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    quality_score INT NULL,
    quality_label VARCHAR(80) NULL,
    tone VARCHAR(30) NULL,
    coverage_score INT NULL,
    freshness_score INT NULL,
    outlier_score INT NULL,
    consistency_score INT NULL,
    issue_count INT NULL,
    excluded_metric_count INT NULL,
    stale_sources_json LONGTEXT NULL CHECK (stale_sources_json IS NULL OR JSON_VALID(stale_sources_json)),
    excluded_fields_json LONGTEXT NULL CHECK (excluded_fields_json IS NULL OR JSON_VALID(excluded_fields_json)),
    issues_json LONGTEXT NULL CHECK (issues_json IS NULL OR JSON_VALID(issues_json)),
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    PRIMARY KEY (symbol),
    KEY idx_stock_data_quality_score (quality_score),
    KEY idx_stock_data_quality_calculated (calculated_at)
);

CREATE TABLE IF NOT EXISTS stock_sec_company (
    symbol VARCHAR(20) NOT NULL,
    cik VARCHAR(10) NOT NULL,
    ticker VARCHAR(20) NOT NULL,
    company_name VARCHAR(255) NULL,
    source VARCHAR(80) NOT NULL DEFAULT 'SEC_COMPANY_TICKERS',
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol),
    UNIQUE KEY uk_stock_sec_company_cik_symbol (cik, symbol),
    KEY idx_stock_sec_company_cik (cik)
);

CREATE TABLE IF NOT EXISTS stock_sec_fact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    cik VARCHAR(10) NOT NULL,
    taxonomy VARCHAR(32) NOT NULL,
    concept VARCHAR(160) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    fiscal_year INT NULL,
    fiscal_period VARCHAR(20) NULL,
    form VARCHAR(20) NULL,
    filed_at DATE NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    accession_number VARCHAR(32) NOT NULL,
    frame VARCHAR(40) NULL,
    value DECIMAL(32, 6) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_sec_fact_natural (
        symbol, taxonomy, concept, unit, accession_number, fiscal_year, fiscal_period, start_date, end_date
    ),
    KEY idx_stock_sec_fact_symbol_period (symbol, fiscal_year, fiscal_period),
    KEY idx_stock_sec_fact_concept (concept, fiscal_year, fiscal_period),
    KEY idx_stock_sec_fact_filed (filed_at)
);

DROP INDEX IF EXISTS uk_stock_sec_fact_natural ON stock_sec_fact;

CREATE UNIQUE INDEX IF NOT EXISTS uk_stock_sec_fact_natural
    ON stock_sec_fact (
        symbol, taxonomy, concept, unit, accession_number, fiscal_year, fiscal_period, start_date, end_date
    );

CREATE TABLE IF NOT EXISTS stock_sec_financial_standard (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    cik VARCHAR(10) NOT NULL,
    fiscal_year INT NOT NULL,
    fiscal_period VARCHAR(20) NOT NULL,
    form VARCHAR(20) NOT NULL,
    filed_at DATE NULL,
    start_date DATE NULL,
    end_date DATE NOT NULL,
    accession_number VARCHAR(32) NOT NULL,
    period_days INT NULL,
    currency VARCHAR(10) NULL,
    revenue DECIMAL(32, 6) NULL,
    operating_income DECIMAL(32, 6) NULL,
    net_income DECIMAL(32, 6) NULL,
    eps_diluted DECIMAL(24, 6) NULL,
    assets DECIMAL(32, 6) NULL,
    liabilities DECIMAL(32, 6) NULL,
    equity DECIMAL(32, 6) NULL,
    operating_cash_flow DECIMAL(32, 6) NULL,
    eps_unit VARCHAR(40) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    mapped_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_sec_financial_period (
        symbol, fiscal_year, fiscal_period, form, accession_number, end_date
    ),
    KEY idx_stock_sec_financial_symbol_filed (symbol, filed_at),
    KEY idx_stock_sec_financial_symbol_period (symbol, fiscal_year, fiscal_period)
);

CREATE TABLE IF NOT EXISTS institution_13f_manager (
    cik VARCHAR(10) NOT NULL PRIMARY KEY,
    manager_name VARCHAR(255) NOT NULL,
    short_name VARCHAR(80) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 100,
    source VARCHAR(80) NOT NULL DEFAULT 'SEED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_institution_13f_manager_active (active, priority)
);

CREATE TABLE IF NOT EXISTS institution_13f_batch_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_name VARCHAR(80) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    status VARCHAR(30) NOT NULL,
    requested_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    holding_count INT NOT NULL DEFAULT 0,
    aggregate_rows INT NOT NULL DEFAULT 0,
    message TEXT NULL,
    KEY idx_institution_13f_batch_run_started (started_at),
    KEY idx_institution_13f_batch_run_status (status)
);

CREATE TABLE IF NOT EXISTS institution_13f_filing (
    accession_number VARCHAR(32) NOT NULL PRIMARY KEY,
    manager_cik VARCHAR(10) NOT NULL,
    manager_name VARCHAR(255) NOT NULL,
    form_type VARCHAR(20) NOT NULL,
    report_quarter DATE NOT NULL,
    report_date DATE NULL,
    filing_date DATE NULL,
    information_table_url VARCHAR(600) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DISCOVERED',
    raw_json LONGTEXT NULL,
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_institution_13f_filing_manager (manager_cik, report_quarter),
    KEY idx_institution_13f_filing_quarter (report_quarter, filing_date)
);

CREATE TABLE IF NOT EXISTS institution_13f_holding (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    accession_number VARCHAR(32) NOT NULL,
    manager_cik VARCHAR(10) NOT NULL,
    manager_name VARCHAR(255) NOT NULL,
    report_quarter DATE NOT NULL,
    issuer_name VARCHAR(255) NULL,
    title_of_class VARCHAR(120) NULL,
    cusip VARCHAR(20) NULL,
    value_usd_thousands DECIMAL(24, 4) NULL,
    shares DECIMAL(24, 4) NULL,
    share_type VARCHAR(40) NULL,
    put_call VARCHAR(20) NULL,
    investment_discretion VARCHAR(80) NULL,
    other_manager VARCHAR(80) NULL,
    voting_sole DECIMAL(24, 4) NULL,
    voting_shared DECIMAL(24, 4) NULL,
    voting_none DECIMAL(24, 4) NULL,
    symbol VARCHAR(20) NULL,
    mapped_by VARCHAR(40) NULL,
    raw_xml LONGTEXT NULL,
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_institution_13f_holding_natural (accession_number, cusip, issuer_name, title_of_class, put_call),
    KEY idx_institution_13f_holding_symbol_quarter (symbol, report_quarter),
    KEY idx_institution_13f_holding_cusip (cusip),
    KEY idx_institution_13f_holding_manager (manager_cik, report_quarter)
);

CREATE TABLE IF NOT EXISTS stock_institution_flow_quarterly (
    symbol VARCHAR(20) NOT NULL,
    report_quarter DATE NOT NULL,
    holder_count INT NOT NULL DEFAULT 0,
    total_value_usd_thousands DECIMAL(24, 4) NULL,
    total_shares DECIMAL(24, 4) NULL,
    previous_total_value_usd_thousands DECIMAL(24, 4) NULL,
    previous_total_shares DECIMAL(24, 4) NULL,
    value_change_pct DECIMAL(18, 6) NULL,
    shares_change_pct DECIMAL(18, 6) NULL,
    net_shares_change DECIMAL(24, 4) NULL,
    top_manager_name VARCHAR(255) NULL,
    top_manager_value_usd_thousands DECIMAL(24, 4) NULL,
    source_filing_count INT NOT NULL DEFAULT 0,
    mapped_holding_count INT NOT NULL DEFAULT 0,
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, report_quarter),
    KEY idx_stock_institution_flow_quarter (report_quarter),
    KEY idx_stock_institution_flow_change (shares_change_pct)
);

INSERT INTO institution_13f_manager (cik, manager_name, short_name, priority, source)
VALUES
    ('0000102909', 'VANGUARD GROUP INC', 'Vanguard', 10, 'SEED'),
    ('0001364742', 'BLACKROCK INC.', 'BlackRock', 11, 'SEED'),
    ('0000093751', 'STATE STREET CORP', 'State Street', 12, 'SEED'),
    ('0000315066', 'FMR LLC', 'Fidelity', 13, 'SEED'),
    ('0001067983', 'BERKSHIRE HATHAWAY INC', 'Berkshire Hathaway', 14, 'SEED'),
    ('0000019617', 'JPMORGAN CHASE & CO', 'JPMorgan', 15, 'SEED'),
    ('0000070858', 'BANK OF AMERICA CORP /DE/', 'Bank of America', 16, 'SEED'),
    ('0000895421', 'MORGAN STANLEY', 'Morgan Stanley', 17, 'SEED'),
    ('0000886982', 'GOLDMAN SACHS GROUP INC', 'Goldman Sachs', 18, 'SEED'),
    ('0000914208', 'INVESCO LTD.', 'Invesco', 19, 'SEED'),
    ('0000902219', 'WELLINGTON MANAGEMENT GROUP LLP', 'Wellington', 20, 'SEED'),
    ('0001113169', 'T. ROWE PRICE ASSOCIATES, INC.', 'T. Rowe Price', 21, 'SEED'),
    ('0001422848', 'CAPITAL WORLD INVESTORS', 'Capital World', 22, 'SEED'),
    ('0001350694', 'BRIDGEWATER ASSOCIATES, LP', 'Bridgewater', 23, 'SEED'),
    ('0001037389', 'RENAISSANCE TECHNOLOGIES LLC', 'Renaissance', 24, 'SEED'),
    ('0001423053', 'CITADEL ADVISORS LLC', 'Citadel', 25, 'SEED'),
    ('0001273087', 'MILLENNIUM MANAGEMENT LLC', 'Millennium', 26, 'SEED'),
    ('0001446417', 'TWO SIGMA INVESTMENTS, LP', 'Two Sigma', 27, 'SEED'),
    ('0001595888', 'JANE STREET GROUP, LLC', 'Jane Street', 28, 'SEED'),
    ('0001446194', 'SUSQUEHANNA INTERNATIONAL GROUP, LLP', 'Susquehanna', 29, 'SEED')
ON DUPLICATE KEY UPDATE
    manager_name = VALUES(manager_name),
    short_name = VALUES(short_name),
    priority = VALUES(priority),
    source = VALUES(source),
    active = TRUE;

ALTER TABLE stock_sec_financial_standard
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NULL AFTER period_days,
    ADD COLUMN IF NOT EXISTS eps_unit VARCHAR(40) NULL AFTER operating_cash_flow;

CREATE TABLE IF NOT EXISTS api_call_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(40) NOT NULL,
    endpoint VARCHAR(120) NOT NULL,
    symbol VARCHAR(20) NULL,
    status_code INT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_api_call_log_provider_requested (provider, requested_at),
    KEY idx_api_call_log_symbol_requested (symbol, requested_at)
);

CREATE TABLE IF NOT EXISTS finnhub_batch_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_name VARCHAR(80) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    status VARCHAR(30) NOT NULL,
    requested_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    saved_rows INT NOT NULL DEFAULT 0,
    signal_refreshed_count INT NOT NULL DEFAULT 0,
    message TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_finnhub_batch_run_job_started (job_name, started_at)
);

CREATE TABLE IF NOT EXISTS sec_edgar_batch_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_name VARCHAR(80) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    status VARCHAR(30) NOT NULL,
    requested_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    message TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_sec_edgar_batch_run_job_started (job_name, started_at)
);

CREATE TABLE IF NOT EXISTS yahoo_candle_batch_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_name VARCHAR(80) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    status VARCHAR(30) NOT NULL,
    requested_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    message TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_yahoo_candle_batch_run_job_started (job_name, started_at)
);

CREATE TABLE IF NOT EXISTS etf_profile (
    symbol VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    issuer VARCHAR(120) NULL,
    category VARCHAR(120) NULL,
    strategy VARCHAR(120) NULL,
    region VARCHAR(80) NULL,
    asset_class VARCHAR(80) NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    benchmark VARCHAR(255) NULL,
    expense_ratio DECIMAL(12, 6) NULL,
    dividend_yield DECIMAL(12, 6) NULL,
    aum_million DECIMAL(24, 4) NULL,
    holdings_count INT NULL,
    inception_date DATE NULL,
    description VARCHAR(1000) NULL,
    website VARCHAR(1000) NULL,
    logo_url VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 1000,
    source VARCHAR(80) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol),
    KEY idx_etf_profile_active_sort (active, sort_order),
    KEY idx_etf_profile_category (category)
);

CREATE TABLE IF NOT EXISTS etf_quote_cache (
    symbol VARCHAR(20) NOT NULL,
    current_price DECIMAL(24, 6) NULL,
    open_price DECIMAL(24, 6) NULL,
    high_price DECIMAL(24, 6) NULL,
    low_price DECIMAL(24, 6) NULL,
    previous_close DECIMAL(24, 6) NULL,
    volume BIGINT NULL,
    quote_time TIMESTAMP NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'yahoo-chart',
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol),
    KEY idx_etf_quote_fetched (fetched_at)
);

CREATE TABLE IF NOT EXISTS etf_candle_daily (
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    open_price DECIMAL(24, 6) NOT NULL,
    high_price DECIMAL(24, 6) NOT NULL,
    low_price DECIMAL(24, 6) NOT NULL,
    close_price DECIMAL(24, 6) NOT NULL,
    volume BIGINT NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'yahoo-chart',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, trade_date),
    KEY idx_etf_candle_daily_date (trade_date)
);
