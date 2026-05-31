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
    earnings_score INT NULL,
    earnings_label VARCHAR(120) NULL,
    analyst_score INT NULL,
    analyst_label VARCHAR(120) NULL,
    news_score INT NULL,
    news_label VARCHAR(120) NULL,
    momentum_score INT NULL,
    momentum_label VARCHAR(120) NULL,
    reasons_json LONGTEXT NULL CHECK (reasons_json IS NULL OR JSON_VALID(reasons_json)),
    cards_json LONGTEXT NULL CHECK (cards_json IS NULL OR JSON_VALID(cards_json)),
    source_freshness_json LONGTEXT NULL CHECK (source_freshness_json IS NULL OR JSON_VALID(source_freshness_json)),
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    PRIMARY KEY (symbol),
    KEY idx_stock_signal_latest_calculated (calculated_at),
    KEY idx_stock_signal_latest_score (integrated_score)
);

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
    message TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_finnhub_batch_run_job_started (job_name, started_at)
);
