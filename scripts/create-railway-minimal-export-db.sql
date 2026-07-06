DROP DATABASE IF EXISTS king_yurina_railway_export;
CREATE DATABASE king_yurina_railway_export CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE king_yurina_railway_export.stock_symbol AS
SELECT
    s.symbol,
    s.exchange,
    s.name,
    s.currency,
    s.active,
    s.collect_tier,
    s.first_seen_at,
    s.last_seen_at,
    s.last_collected_at
FROM stock_symbol s
WHERE s.symbol IN (
    SELECT DISTINCT i.symbol
    FROM index_constituent i
    WHERE i.index_code IN ('SP500', 'NASDAQ100', 'DOW30')
      AND i.current_member = TRUE
      AND i.symbol NOT IN ('TICKER', 'SECURITY')
);

CREATE TABLE king_yurina_railway_export._top_company_symbol AS
SELECT symbol
FROM company_profile
WHERE symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol)
ORDER BY market_cap DESC
LIMIT 40;

CREATE TABLE king_yurina_railway_export.index_constituent AS
SELECT
    i.index_code,
    i.symbol,
    i.name,
    i.exchange,
    i.sector,
    i.current_member,
    i.first_seen_at,
    i.last_seen_at,
    i.removed_at,
    CONCAT(i.source, ':RAILWAY_SAMPLE') AS source
FROM index_constituent i
WHERE i.index_code IN ('SP500', 'NASDAQ100', 'DOW30')
  AND i.current_member = TRUE
  AND i.symbol NOT IN ('TICKER', 'SECURITY');

CREATE TABLE king_yurina_railway_export.company_profile AS
SELECT
    p.symbol,
    p.name,
    p.country,
    p.currency,
    p.exchange,
    p.finnhub_industry,
    p.market_cap,
    p.share_outstanding,
    p.logo,
    p.weburl,
    CAST(NULL AS CHAR) AS raw_json,
    p.fetched_at
FROM company_profile p
WHERE p.symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol);

CREATE TABLE king_yurina_railway_export.stock_quote_cache AS
SELECT
    q.symbol,
    q.current_price,
    q.open_price,
    q.high_price,
    q.low_price,
    q.previous_close,
    q.quote_time,
    CAST(NULL AS CHAR) AS raw_json,
    q.fetched_at
FROM stock_quote_cache q
WHERE q.symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol);

CREATE TABLE king_yurina_railway_export.stock_metric_snapshot AS
SELECT
    m.symbol,
    m.metric_date,
    m.pe_normalized_annual,
    m.pb_annual,
    m.roe_ttm,
    m.eps_ttm,
    m.week_52_high,
    m.week_52_low,
    CAST(NULL AS CHAR) AS raw_json,
    m.fetched_at
FROM stock_metric_snapshot m
INNER JOIN (
    SELECT symbol, MAX(metric_date) AS metric_date
    FROM stock_metric_snapshot
    WHERE symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol)
    GROUP BY symbol
) latest
    ON latest.symbol = m.symbol
   AND latest.metric_date = m.metric_date;

CREATE TABLE king_yurina_railway_export.stock_signal_latest AS
SELECT
    sl.symbol,
    sl.calculated_at,
    sl.signal_version,
    sl.integrated_score,
    sl.integrated_label,
    sl.tone,
    sl.confidence,
    sl.summary,
    sl.valuation_score,
    sl.valuation_label,
    sl.quality_score,
    sl.quality_label,
    sl.growth_score,
    sl.growth_label,
    sl.stability_score,
    sl.stability_label,
    sl.earnings_score,
    sl.earnings_label,
    sl.analyst_score,
    sl.analyst_label,
    sl.news_score,
    sl.news_label,
    sl.momentum_score,
    sl.momentum_label,
    sl.risk_score,
    sl.risk_label,
    sl.institution_score,
    sl.institution_label,
    sl.reasons_json,
    sl.cards_json,
    sl.source_freshness_json,
    CAST(NULL AS CHAR) AS raw_json
FROM stock_signal_latest sl
WHERE sl.symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol);

CREATE TABLE king_yurina_railway_export.stock_data_quality_latest AS
SELECT
    dq.symbol,
    dq.calculated_at,
    dq.quality_score,
    dq.quality_label,
    dq.tone,
    dq.coverage_score,
    dq.freshness_score,
    dq.outlier_score,
    dq.consistency_score,
    dq.issue_count,
    dq.excluded_metric_count,
    dq.stale_sources_json,
    dq.excluded_fields_json,
    dq.issues_json,
    CAST(NULL AS CHAR) AS raw_json
FROM stock_data_quality_latest dq
WHERE dq.symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol);

CREATE TABLE king_yurina_railway_export.stock_expected_return_snapshot AS
SELECT er.*
FROM stock_expected_return_snapshot er
INNER JOIN (
    SELECT index_code, symbol, horizon_days, model_version, MAX(signal_date) AS signal_date
    FROM stock_expected_return_snapshot
    WHERE index_code IN ('SP500', 'NASDAQ100', 'DOW30')
      AND model_version = 'EXPECTED_RETURN_V9'
      AND horizon_days IN (5, 20, 60)
      AND symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol)
    GROUP BY index_code, symbol, horizon_days, model_version
) latest
    ON latest.index_code = er.index_code
   AND latest.symbol = er.symbol
   AND latest.horizon_days = er.horizon_days
   AND latest.model_version = er.model_version
   AND latest.signal_date = er.signal_date;

CREATE TABLE king_yurina_railway_export.stock_expected_return_factor_contribution AS
SELECT c.*
FROM stock_expected_return_factor_contribution c
INNER JOIN (
    SELECT index_code, symbol, horizon_days, model_version, MAX(signal_date) AS signal_date
    FROM stock_expected_return_factor_contribution
    WHERE index_code IN ('SP500', 'NASDAQ100', 'DOW30')
      AND model_version = 'EXPECTED_RETURN_V9'
      AND horizon_days = 20
      AND symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol)
    GROUP BY index_code, symbol, horizon_days, model_version
) latest
    ON latest.index_code = c.index_code
   AND latest.symbol = c.symbol
   AND latest.horizon_days = c.horizon_days
   AND latest.model_version = c.model_version
   AND latest.signal_date = c.signal_date;

CREATE TABLE king_yurina_railway_export.stock_market_snapshot AS
SELECT ms.*
FROM stock_market_snapshot ms
INNER JOIN (
    SELECT index_code, symbol, MAX(snapshot_date) AS snapshot_date
    FROM stock_market_snapshot
    WHERE index_code IN ('SP500', 'NASDAQ100', 'DOW30')
      AND symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol)
    GROUP BY index_code, symbol
) latest
    ON latest.index_code = ms.index_code
   AND latest.symbol = ms.symbol
   AND latest.snapshot_date = ms.snapshot_date;

CREATE TABLE king_yurina_railway_export.stock_candle_daily AS
SELECT c.*
FROM stock_candle_daily c
WHERE c.symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol)
  AND c.trade_date >= (
      SELECT DATE_SUB(MAX(trade_date), INTERVAL 180 DAY)
      FROM stock_candle_daily
  );

CREATE TABLE king_yurina_railway_export.stock_benchmark_return_series AS
SELECT b.*
FROM stock_benchmark_return_series b
WHERE b.index_code IN ('SP500', 'NASDAQ100', 'DOW30')
  AND b.trade_date >= (
      SELECT DATE_SUB(MAX(trade_date), INTERVAL 180 DAY)
      FROM stock_benchmark_return_series
  );

CREATE TABLE king_yurina_railway_export.company_news AS
SELECT
    n.symbol,
    n.news_id,
    n.headline,
    n.summary,
    n.url,
    n.source,
    n.published_at,
    CAST(NULL AS CHAR) AS raw_json,
    n.fetched_at
FROM (
    SELECT n.*,
           ROW_NUMBER() OVER (PARTITION BY n.symbol ORDER BY n.published_at DESC, n.news_id DESC) AS rn
    FROM company_news n
    INNER JOIN king_yurina_railway_export._top_company_symbol t
        ON t.symbol = n.symbol
) n
WHERE n.rn <= 5;

CREATE TABLE king_yurina_railway_export.stock_recommendation_trend AS
SELECT
    r.symbol,
    r.period_date,
    r.strong_buy,
    r.buy,
    r.hold,
    r.sell,
    r.strong_sell,
    CAST(NULL AS CHAR) AS raw_json,
    r.fetched_at
FROM (
    SELECT r.*,
           ROW_NUMBER() OVER (PARTITION BY r.symbol ORDER BY r.period_date DESC) AS rn
    FROM stock_recommendation_trend r
    WHERE r.symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol)
) r
WHERE r.rn <= 4;

CREATE TABLE king_yurina_railway_export.stock_eps_surprise AS
SELECT
    e.symbol,
    e.period_date,
    e.fiscal_year,
    e.fiscal_quarter,
    e.actual,
    e.estimate,
    e.surprise,
    e.surprise_percent,
    CAST(NULL AS CHAR) AS raw_json,
    e.fetched_at
FROM (
    SELECT e.*,
           ROW_NUMBER() OVER (PARTITION BY e.symbol ORDER BY e.period_date DESC) AS rn
    FROM stock_eps_surprise e
    WHERE e.symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol)
) e
WHERE e.rn <= 4;

CREATE TABLE king_yurina_railway_export.stock_institution_flow_quarterly AS
SELECT
    f.symbol,
    f.report_quarter,
    f.holder_count,
    f.total_value_usd_thousands,
    f.total_shares,
    f.previous_total_value_usd_thousands,
    f.previous_total_shares,
    f.value_change_pct,
    f.shares_change_pct,
    f.net_shares_change,
    f.top_manager_name,
    f.top_manager_value_usd_thousands,
    f.source_filing_count,
    f.mapped_holding_count,
    f.fetched_at,
    f.created_at,
    f.updated_at
FROM (
    SELECT f.*,
           ROW_NUMBER() OVER (PARTITION BY f.symbol ORDER BY f.report_quarter DESC) AS rn
    FROM stock_institution_flow_quarterly f
    WHERE f.symbol IN (SELECT symbol FROM king_yurina_railway_export.stock_symbol)
) f
WHERE f.rn <= 1;

CREATE TABLE king_yurina_railway_export.etf_candle_daily AS
SELECT e.*
FROM etf_candle_daily e
WHERE e.trade_date >= (
      SELECT DATE_SUB(MAX(trade_date), INTERVAL 180 DAY)
      FROM etf_candle_daily
  );

CREATE TABLE king_yurina_railway_export.stock_macro_feature_snapshot AS
SELECT mf.*
FROM stock_macro_feature_snapshot mf
WHERE mf.index_code IN ('SP500', 'NASDAQ100', 'DOW30')
  AND mf.snapshot_date >= (
      SELECT DATE_SUB(MAX(snapshot_date), INTERVAL 180 DAY)
      FROM stock_macro_feature_snapshot
  );

CREATE TABLE king_yurina_railway_export.stock_macro_regime_snapshot AS
SELECT mr.*
FROM stock_macro_regime_snapshot mr
WHERE mr.index_code IN ('SP500', 'NASDAQ100', 'DOW30')
  AND mr.snapshot_date >= (
      SELECT DATE_SUB(MAX(snapshot_date), INTERVAL 180 DAY)
      FROM stock_macro_regime_snapshot
  );

DROP TABLE king_yurina_railway_export._top_company_symbol;

CREATE TABLE king_yurina_railway_export.stock_risk_free_rate_snapshot AS
SELECT rf.*
FROM stock_risk_free_rate_snapshot rf
WHERE rf.index_code IN ('SP500', 'NASDAQ100', 'DOW30')
  AND rf.rate_date >= (
      SELECT DATE_SUB(MAX(rate_date), INTERVAL 180 DAY)
      FROM stock_risk_free_rate_snapshot
  );
