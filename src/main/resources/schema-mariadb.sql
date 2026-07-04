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

CREATE TABLE IF NOT EXISTS stock_candle_daily_source (
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    source VARCHAR(40) NOT NULL,
    open_price DECIMAL(24, 6) NOT NULL,
    high_price DECIMAL(24, 6) NOT NULL,
    low_price DECIMAL(24, 6) NOT NULL,
    close_price DECIMAL(24, 6) NOT NULL,
    volume BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, trade_date, source),
    KEY idx_stock_candle_daily_source_date (trade_date),
    KEY idx_stock_candle_daily_source_source_date (source, trade_date)
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

CREATE TABLE IF NOT EXISTS stock_risk_snapshot (
    symbol VARCHAR(20) NOT NULL,
    signal_date DATE NOT NULL,
    beta DECIMAL(18, 8) NULL,
    volatility_pct DECIMAL(18, 6) NULL,
    avg_dollar_volume DECIMAL(24, 2) NULL,
    observations INT NULL,
    source VARCHAR(80) NOT NULL DEFAULT 'CANDLE_TRAILING',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, signal_date),
    KEY idx_stock_risk_snapshot_date (signal_date),
    KEY idx_stock_risk_snapshot_beta (beta),
    KEY idx_stock_risk_snapshot_volatility (volatility_pct)
);

CREATE TABLE IF NOT EXISTS stock_market_snapshot (
    index_code VARCHAR(40) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    snapshot_date DATE NOT NULL,
    sector VARCHAR(160) NULL,
    industry VARCHAR(160) NULL,
    exchange VARCHAR(120) NULL,
    market VARCHAR(40) NULL,
    currency VARCHAR(10) NULL,
    close_price DECIMAL(30, 8) NULL,
    volume BIGINT NULL,
    shares_outstanding DECIMAL(30, 6) NULL,
    market_cap_usd DECIMAL(32, 6) NULL,
    price_source VARCHAR(40) NULL,
    shares_source VARCHAR(40) NULL,
    market_cap_source VARCHAR(80) NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, symbol, snapshot_date),
    KEY idx_stock_market_snapshot_date (index_code, snapshot_date),
    KEY idx_stock_market_snapshot_symbol_date (symbol, snapshot_date),
    KEY idx_stock_market_snapshot_cap (index_code, snapshot_date, market_cap_usd)
);

CREATE TABLE IF NOT EXISTS stock_index_membership_snapshot (
    index_code VARCHAR(40) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    snapshot_date DATE NOT NULL,
    sector VARCHAR(160) NULL,
    industry VARCHAR(160) NULL,
    exchange VARCHAR(120) NULL,
    is_member TINYINT(1) NOT NULL DEFAULT 1,
    source VARCHAR(80) NOT NULL DEFAULT 'CURRENT_MEMBERSHIP_PROXY',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, symbol, snapshot_date),
    KEY idx_stock_index_membership_snapshot_date (index_code, snapshot_date, is_member),
    KEY idx_stock_index_membership_snapshot_symbol_date (symbol, snapshot_date),
    KEY idx_stock_index_membership_snapshot_source (source)
);

ALTER TABLE stock_market_snapshot
    ADD COLUMN IF NOT EXISTS industry VARCHAR(160) NULL AFTER sector;

ALTER TABLE stock_index_membership_snapshot
    ADD COLUMN IF NOT EXISTS industry VARCHAR(160) NULL AFTER sector;

CREATE TABLE IF NOT EXISTS stock_shares_outstanding_snapshot (
    index_code VARCHAR(40) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    snapshot_date DATE NOT NULL,
    shares_outstanding DECIMAL(30, 6) NULL,
    source VARCHAR(80) NOT NULL DEFAULT 'CURRENT_SHARES_PROXY',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, symbol, snapshot_date),
    KEY idx_stock_shares_outstanding_snapshot_date (index_code, snapshot_date),
    KEY idx_stock_shares_outstanding_snapshot_symbol_date (symbol, snapshot_date),
    KEY idx_stock_shares_outstanding_snapshot_source (source)
);

CREATE TABLE IF NOT EXISTS stock_symbol_data_alias (
    symbol VARCHAR(20) NOT NULL,
    data_source VARCHAR(40) NOT NULL,
    data_symbol VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, data_source),
    KEY idx_stock_symbol_data_alias_data_symbol (data_source, data_symbol),
    KEY idx_stock_symbol_data_alias_active (active)
);

CREATE TABLE IF NOT EXISTS stock_benchmark_return_series (
    index_code VARCHAR(40) NOT NULL,
    trade_date DATE NOT NULL,
    benchmark_level DECIMAL(24, 8) NULL,
    return_pct DECIMAL(18, 8) NULL,
    total_market_cap_usd DECIMAL(32, 6) NULL,
    constituent_count INT NULL,
    coverage_count INT NULL,
    source VARCHAR(80) NOT NULL DEFAULT 'MARKET_SNAPSHOT_WEIGHTED',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, trade_date),
    KEY idx_stock_benchmark_return_date (trade_date),
    KEY idx_stock_benchmark_return_source (source)
);

CREATE TABLE IF NOT EXISTS stock_risk_free_rate_snapshot (
    index_code VARCHAR(40) NOT NULL,
    series_code VARCHAR(40) NOT NULL,
    rate_date DATE NOT NULL,
    annual_rate_pct DECIMAL(18, 8) NULL,
    source VARCHAR(80) NOT NULL DEFAULT 'CONFIG_FIXED',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, series_code, rate_date),
    KEY idx_stock_risk_free_rate_date (index_code, rate_date),
    KEY idx_stock_risk_free_rate_source (source)
);

CREATE TABLE IF NOT EXISTS stock_macro_vintage_snapshot (
    index_code VARCHAR(40) NOT NULL,
    series_code VARCHAR(40) NOT NULL,
    observation_date DATE NOT NULL,
    realtime_start DATE NOT NULL,
    realtime_end DATE NOT NULL,
    value DECIMAL(24, 8) NULL,
    source VARCHAR(80) NOT NULL DEFAULT 'FRED_API',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, series_code, observation_date, realtime_start, realtime_end),
    KEY idx_stock_macro_vintage_observation (index_code, series_code, observation_date),
    KEY idx_stock_macro_vintage_realtime (index_code, series_code, realtime_start, realtime_end),
    KEY idx_stock_macro_vintage_source (source)
);

CREATE TABLE IF NOT EXISTS stock_macro_feature_snapshot (
    index_code VARCHAR(40) NOT NULL,
    snapshot_date DATE NOT NULL,
    short_rate_pct DECIMAL(18, 8) NULL,
    long_rate_pct DECIMAL(18, 8) NULL,
    yield_spread_pct DECIMAL(18, 8) NULL,
    fed_funds_pct DECIMAL(18, 8) NULL,
    cpi_yoy_pct DECIMAL(18, 8) NULL,
    cpi_mom_pct DECIMAL(18, 8) NULL,
    unemployment_rate_pct DECIMAL(18, 8) NULL,
    unemployment_change_3m_pct DECIMAL(18, 8) NULL,
    vix_level DECIMAL(18, 8) NULL,
    vix_change_20d_pct DECIMAL(18, 8) NULL,
    dollar_index DECIMAL(18, 8) NULL,
    dollar_change_20d_pct DECIMAL(18, 8) NULL,
    macro_tightness_score INT NULL,
    macro_growth_stress_score INT NULL,
    macro_risk_score INT NULL,
    macro_feature_score INT NULL,
    source VARCHAR(128) NOT NULL DEFAULT 'FRED_MACRO_FEATURE_V1',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, snapshot_date),
    KEY idx_stock_macro_feature_snapshot_date (snapshot_date),
    KEY idx_stock_macro_feature_snapshot_score (index_code, macro_feature_score),
    KEY idx_stock_macro_feature_snapshot_source (source)
);

CREATE TABLE IF NOT EXISTS stock_portfolio_view_snapshot (
    index_code VARCHAR(40) NOT NULL,
    view_version VARCHAR(80) NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(80) NOT NULL DEFAULT 'ON_DEMAND',
    payload_json LONGTEXT NOT NULL,
    PRIMARY KEY (index_code, view_version),
    KEY idx_stock_portfolio_view_generated_at (generated_at),
    KEY idx_stock_portfolio_view_source (source)
);

CREATE TABLE IF NOT EXISTS stock_optimizer_shadow_snapshot (
    index_code VARCHAR(40) NOT NULL,
    signal_date DATE NOT NULL,
    horizon_days INT NOT NULL,
    top_count INT NOT NULL,
    weighting VARCHAR(40) NOT NULL,
    baseline_optimizer VARCHAR(40) NOT NULL,
    candidate_optimizer VARCHAR(40) NOT NULL,
    solver_status VARCHAR(80) NOT NULL,
    usable BOOLEAN NOT NULL DEFAULT FALSE,
    message VARCHAR(255) NULL,
    baseline_objective DECIMAL(24, 10) NULL,
    candidate_objective DECIMAL(24, 10) NULL,
    objective_gap DECIMAL(24, 10) NULL,
    weight_distance_pct DECIMAL(18, 8) NULL,
    baseline_net_return_pct DECIMAL(18, 8) NULL,
    candidate_net_return_pct DECIMAL(18, 8) NULL,
    benchmark_return_pct DECIMAL(18, 8) NULL,
    candidate_turnover_pct DECIMAL(18, 8) NULL,
    candidate_transaction_cost_pct DECIMAL(18, 8) NULL,
    candidate_beta DECIMAL(18, 8) NULL,
    candidate_volatility_pct DECIMAL(18, 8) NULL,
    candidate_liquidity DECIMAL(24, 4) NULL,
    candidate_max_sector_weight_pct DECIMAL(18, 8) NULL,
    candidate_max_position_weight_pct DECIMAL(18, 8) NULL,
    candidate_active_sector_deviation_pct DECIMAL(18, 8) NULL,
    candidate_invested_weight_pct DECIMAL(18, 8) NULL,
    beta_breach BOOLEAN NOT NULL DEFAULT FALSE,
    volatility_breach BOOLEAN NOT NULL DEFAULT FALSE,
    sector_breach BOOLEAN NOT NULL DEFAULT FALSE,
    position_breach BOOLEAN NOT NULL DEFAULT FALSE,
    turnover_breach BOOLEAN NOT NULL DEFAULT FALSE,
    objective_breach BOOLEAN NOT NULL DEFAULT FALSE,
    weight_distance_breach BOOLEAN NOT NULL DEFAULT FALSE,
    constraint_breach_count INT NOT NULL DEFAULT 0,
    source VARCHAR(80) NOT NULL DEFAULT 'OJALGO_QP_SHADOW_V1',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, signal_date, horizon_days, top_count, weighting, candidate_optimizer),
    KEY idx_stock_optimizer_shadow_date (index_code, signal_date),
    KEY idx_stock_optimizer_shadow_candidate (candidate_optimizer, calculated_at),
    KEY idx_stock_optimizer_shadow_status (index_code, usable, constraint_breach_count)
);

CREATE TABLE IF NOT EXISTS stock_dashboard_view_snapshot (
    index_code VARCHAR(40) NOT NULL,
    view_version VARCHAR(80) NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(80) NOT NULL DEFAULT 'ON_DEMAND',
    payload_json LONGTEXT NOT NULL,
    PRIMARY KEY (index_code, view_version),
    KEY idx_stock_dashboard_view_generated_at (generated_at),
    KEY idx_stock_dashboard_view_source (source)
);

CREATE TABLE IF NOT EXISTS stock_backtest_view_snapshot (
    index_code VARCHAR(40) NOT NULL,
    view_version VARCHAR(80) NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(80) NOT NULL DEFAULT 'ON_DEMAND',
    payload_json LONGTEXT NOT NULL,
    PRIMARY KEY (index_code, view_version),
    KEY idx_stock_backtest_view_generated_at (generated_at),
    KEY idx_stock_backtest_view_source (source)
);

CREATE TABLE IF NOT EXISTS stock_macro_regime_snapshot (
    index_code VARCHAR(40) NOT NULL,
    snapshot_date DATE NOT NULL,
    regime_label VARCHAR(64) NOT NULL,
    macro_score INT NULL,
    trend_score INT NULL,
    volatility_score INT NULL,
    breadth_score INT NULL,
    liquidity_score INT NULL,
    dollar_score INT NULL,
    rate_score INT NULL,
    benchmark_return_20d_pct DECIMAL(18, 8) NULL,
    benchmark_return_60d_pct DECIMAL(18, 8) NULL,
    realized_volatility_20d_pct DECIMAL(18, 8) NULL,
    realized_volatility_60d_pct DECIMAL(18, 8) NULL,
    breadth_advancer_pct DECIMAL(18, 8) NULL,
    benchmark_coverage_count INT NULL,
    vix_level DECIMAL(18, 8) NULL,
    vix_change_20d_pct DECIMAL(18, 8) NULL,
    dollar_proxy_return_20d_pct DECIMAL(18, 8) NULL,
    rate_proxy_return_20d_pct DECIMAL(18, 8) NULL,
    source VARCHAR(128) NOT NULL DEFAULT 'BENCHMARK_BREADTH_PROXY',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, snapshot_date),
    KEY idx_stock_macro_regime_snapshot_date (snapshot_date),
    KEY idx_stock_macro_regime_snapshot_score (index_code, macro_score),
    KEY idx_stock_macro_regime_snapshot_label (index_code, regime_label)
);

CREATE TABLE IF NOT EXISTS stock_correlation_snapshot (
    index_code VARCHAR(40) NOT NULL,
    snapshot_date DATE NOT NULL,
    symbol_a VARCHAR(20) NOT NULL,
    symbol_b VARCHAR(20) NOT NULL,
    correlation DECIMAL(18, 8) NULL,
    observations INT NULL,
    lookback_days INT NOT NULL DEFAULT 126,
    source VARCHAR(80) NOT NULL DEFAULT 'TRAILING_DAILY_RETURN',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, snapshot_date, symbol_a, symbol_b),
    KEY idx_stock_correlation_snapshot_date (index_code, snapshot_date),
    KEY idx_stock_correlation_snapshot_symbol_a (symbol_a, snapshot_date),
    KEY idx_stock_correlation_snapshot_symbol_b (symbol_b, snapshot_date)
);

CREATE TABLE IF NOT EXISTS stock_covariance_snapshot (
    index_code VARCHAR(40) NOT NULL,
    snapshot_date DATE NOT NULL,
    symbol_a VARCHAR(20) NOT NULL,
    symbol_b VARCHAR(20) NOT NULL,
    covariance DECIMAL(24, 12) NULL,
    volatility_a_pct DECIMAL(18, 6) NULL,
    volatility_b_pct DECIMAL(18, 6) NULL,
    observations INT NULL,
    lookback_days INT NOT NULL DEFAULT 126,
    source VARCHAR(80) NOT NULL DEFAULT 'TRAILING_DAILY_RETURN',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, snapshot_date, symbol_a, symbol_b),
    KEY idx_stock_covariance_snapshot_date (index_code, snapshot_date),
    KEY idx_stock_covariance_snapshot_symbol_a (symbol_a, snapshot_date),
    KEY idx_stock_covariance_snapshot_symbol_b (symbol_b, snapshot_date)
);

CREATE TABLE IF NOT EXISTS stock_expected_return_snapshot (
    index_code VARCHAR(40) NOT NULL,
    signal_date DATE NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    horizon_days INT NOT NULL,
    expected_return_pct DECIMAL(18, 8) NULL,
    expected_excess_return_pct DECIMAL(18, 8) NULL,
    return_p10_pct DECIMAL(18, 8) NULL,
    return_p50_pct DECIMAL(18, 8) NULL,
    return_p90_pct DECIMAL(18, 8) NULL,
    excess_p10_pct DECIMAL(18, 8) NULL,
    excess_p50_pct DECIMAL(18, 8) NULL,
    excess_p90_pct DECIMAL(18, 8) NULL,
    upside_probability_pct DECIMAL(18, 8) NULL,
    calibrated_upside_probability_pct DECIMAL(18, 8) NULL,
    downside_probability_pct DECIMAL(18, 8) NULL,
    drawdown_risk_pct DECIMAL(18, 8) NULL,
    confidence INT NULL,
    sample_count INT NULL,
    sector_sample_count INT NULL,
    score_bucket INT NULL,
    calibration_bucket INT NULL,
    calibration_error_pct DECIMAL(18, 8) NULL,
    model_version VARCHAR(40) NOT NULL DEFAULT 'EXPECTED_RETURN_V1',
    source VARCHAR(80) NOT NULL DEFAULT 'HISTORICAL_SCORE_CALIBRATION',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, signal_date, symbol, horizon_days, model_version),
    KEY idx_stock_expected_return_snapshot_date (index_code, signal_date, horizon_days),
    KEY idx_stock_expected_return_snapshot_symbol (symbol, signal_date),
    KEY idx_stock_expected_return_snapshot_expected (index_code, signal_date, horizon_days, expected_excess_return_pct),
    KEY idx_stock_expected_return_snapshot_confidence (index_code, signal_date, horizon_days, confidence)
);

ALTER TABLE stock_expected_return_snapshot
    ADD COLUMN IF NOT EXISTS return_p10_pct DECIMAL(18, 8) NULL AFTER expected_excess_return_pct,
    ADD COLUMN IF NOT EXISTS return_p50_pct DECIMAL(18, 8) NULL AFTER return_p10_pct,
    ADD COLUMN IF NOT EXISTS return_p90_pct DECIMAL(18, 8) NULL AFTER return_p50_pct,
    ADD COLUMN IF NOT EXISTS excess_p10_pct DECIMAL(18, 8) NULL AFTER return_p90_pct,
    ADD COLUMN IF NOT EXISTS excess_p50_pct DECIMAL(18, 8) NULL AFTER excess_p10_pct,
    ADD COLUMN IF NOT EXISTS excess_p90_pct DECIMAL(18, 8) NULL AFTER excess_p50_pct,
    ADD COLUMN IF NOT EXISTS calibrated_upside_probability_pct DECIMAL(18, 8) NULL AFTER upside_probability_pct,
    ADD COLUMN IF NOT EXISTS calibration_bucket INT NULL AFTER score_bucket,
    ADD COLUMN IF NOT EXISTS calibration_error_pct DECIMAL(18, 8) NULL AFTER calibration_bucket;

CREATE TABLE IF NOT EXISTS stock_expected_return_calibration (
    index_code VARCHAR(40) NOT NULL,
    model_version VARCHAR(40) NOT NULL,
    horizon_days INT NOT NULL,
    probability_bucket INT NOT NULL,
    sample_count INT NOT NULL,
    average_predicted_upside_pct DECIMAL(18, 8) NULL,
    actual_upside_rate_pct DECIMAL(18, 8) NULL,
    calibration_error_pct DECIMAL(18, 8) NULL,
    brier_score DECIMAL(18, 8) NULL,
    source VARCHAR(80) NOT NULL DEFAULT 'HISTORICAL_PREDICTION_VALIDATION',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, model_version, horizon_days, probability_bucket),
    KEY idx_stock_expected_return_calibration_horizon (index_code, horizon_days),
    KEY idx_stock_expected_return_calibration_error (index_code, model_version, calibration_error_pct)
);

CREATE TABLE IF NOT EXISTS stock_factor_exposure_snapshot (
    index_code VARCHAR(40) NOT NULL,
    signal_date DATE NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    factor VARCHAR(40) NOT NULL,
    raw_score INT NULL,
    exposure_score DECIMAL(18, 8) NULL,
    sector VARCHAR(120) NULL,
    market_cap DECIMAL(24, 4) NULL,
    data_quality_score INT NULL,
    source VARCHAR(80) NOT NULL DEFAULT 'SIGNAL_FACTOR_SCORE',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, signal_date, symbol, factor),
    KEY idx_stock_factor_exposure_snapshot_date (index_code, signal_date),
    KEY idx_stock_factor_exposure_snapshot_factor (index_code, signal_date, factor),
    KEY idx_stock_factor_exposure_snapshot_symbol (symbol, signal_date)
);

CREATE TABLE IF NOT EXISTS stock_expected_return_factor_contribution (
    index_code VARCHAR(40) NOT NULL,
    signal_date DATE NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    horizon_days INT NOT NULL,
    model_version VARCHAR(40) NOT NULL,
    factor VARCHAR(40) NOT NULL,
    exposure_score DECIMAL(18, 8) NULL,
    coefficient DECIMAL(18, 8) NULL,
    contribution_pct DECIMAL(18, 8) NULL,
    sample_count INT NULL,
    source VARCHAR(80) NOT NULL DEFAULT 'FACTOR_EXPOSURE_WALK_FORWARD',
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, signal_date, symbol, horizon_days, model_version, factor),
    KEY idx_stock_er_factor_contribution_symbol (symbol, signal_date, horizon_days),
    KEY idx_stock_er_factor_contribution_factor (index_code, signal_date, horizon_days, factor),
    KEY idx_stock_er_factor_contribution_contrib (index_code, signal_date, horizon_days, contribution_pct)
);

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

CREATE INDEX IF NOT EXISTS idx_stock_sec_fact_symbol_concept_filed
    ON stock_sec_fact (symbol, taxonomy, concept, unit, filed_at, end_date);

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

CREATE TABLE IF NOT EXISTS toss_market_price_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    price_time TIMESTAMP NULL,
    last_price DECIMAL(30, 8) NULL,
    currency VARCHAR(10) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_toss_market_price_symbol_time (symbol, price_time),
    KEY idx_toss_market_price_fetched (fetched_at)
);

CREATE TABLE IF NOT EXISTS toss_stock_info (
    symbol VARCHAR(20) NOT NULL,
    name VARCHAR(255) NULL,
    english_name VARCHAR(255) NULL,
    isin_code VARCHAR(40) NULL,
    market VARCHAR(40) NULL,
    security_type VARCHAR(60) NULL,
    common_share BOOLEAN NULL,
    status VARCHAR(40) NULL,
    currency VARCHAR(10) NULL,
    list_date DATE NULL,
    delist_date DATE NULL,
    shares_outstanding DECIMAL(30, 6) NULL,
    leverage_factor DECIMAL(18, 6) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol),
    KEY idx_toss_stock_info_market (market),
    KEY idx_toss_stock_info_security_type (security_type),
    KEY idx_toss_stock_info_status (status)
);

CREATE TABLE IF NOT EXISTS toss_stock_warning (
    symbol VARCHAR(20) NOT NULL,
    warning_type VARCHAR(80) NOT NULL,
    exchange VARCHAR(40) NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, warning_type, start_date),
    KEY idx_toss_stock_warning_active (end_date),
    KEY idx_toss_stock_warning_type (warning_type)
);

CREATE TABLE IF NOT EXISTS toss_candle (
    symbol VARCHAR(20) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    candle_time TIMESTAMP NOT NULL,
    trade_date DATE NULL,
    open_price DECIMAL(30, 8) NULL,
    high_price DECIMAL(30, 8) NULL,
    low_price DECIMAL(30, 8) NULL,
    close_price DECIMAL(30, 8) NULL,
    volume DECIMAL(30, 6) NULL,
    currency VARCHAR(10) NULL,
    adjusted BOOLEAN NOT NULL DEFAULT TRUE,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (symbol, interval_code, candle_time),
    KEY idx_toss_candle_date (trade_date),
    KEY idx_toss_candle_fetched (fetched_at)
);

CREATE TABLE IF NOT EXISTS toss_orderbook_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    orderbook_time TIMESTAMP NULL,
    currency VARCHAR(10) NULL,
    best_ask_price DECIMAL(30, 8) NULL,
    best_ask_volume DECIMAL(30, 6) NULL,
    best_bid_price DECIMAL(30, 8) NULL,
    best_bid_volume DECIMAL(30, 6) NULL,
    spread DECIMAL(30, 8) NULL,
    spread_bps DECIMAL(18, 6) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_toss_orderbook_symbol_time (symbol, orderbook_time)
);

CREATE TABLE IF NOT EXISTS toss_trade_print (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    trade_time TIMESTAMP NOT NULL,
    price DECIMAL(30, 8) NULL,
    volume DECIMAL(30, 6) NULL,
    currency VARCHAR(10) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_toss_trade_print (symbol, trade_time, price, volume),
    KEY idx_toss_trade_symbol_time (symbol, trade_time)
);

CREATE TABLE IF NOT EXISTS toss_price_limit_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    limit_time TIMESTAMP NULL,
    upper_limit_price DECIMAL(30, 8) NULL,
    lower_limit_price DECIMAL(30, 8) NULL,
    currency VARCHAR(10) NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_toss_price_limit_symbol_time (symbol, limit_time)
);

CREATE TABLE IF NOT EXISTS toss_exchange_rate_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    base_currency VARCHAR(10) NOT NULL,
    quote_currency VARCHAR(10) NOT NULL,
    rate DECIMAL(30, 8) NULL,
    mid_rate DECIMAL(30, 8) NULL,
    basis_point DECIMAL(18, 6) NULL,
    rate_change_type VARCHAR(20) NULL,
    valid_from TIMESTAMP NULL,
    valid_until TIMESTAMP NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_toss_exchange_rate_pair_time (base_currency, quote_currency, valid_from),
    KEY idx_toss_exchange_rate_fetched (fetched_at)
);

CREATE TABLE IF NOT EXISTS toss_market_calendar_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    market_country VARCHAR(10) NOT NULL,
    query_date DATE NULL,
    today_date DATE NULL,
    today_open BOOLEAN NULL,
    previous_business_date DATE NULL,
    next_business_date DATE NULL,
    raw_json LONGTEXT NULL CHECK (raw_json IS NULL OR JSON_VALID(raw_json)),
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_toss_calendar_market_date (market_country, query_date),
    KEY idx_toss_calendar_fetched (fetched_at)
);

CREATE TABLE IF NOT EXISTS toss_batch_run (
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
    message TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_toss_batch_run_job_started (job_name, started_at)
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
