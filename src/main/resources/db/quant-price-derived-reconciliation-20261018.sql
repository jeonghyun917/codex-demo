-- Bounded maintenance for the eight explicitly reconciled symbols.
-- Market caps retain their historical values; the price/share basis is marked unverified.
CREATE TABLE IF NOT EXISTS stock_market_snapshot_before_price_repair_20261018 LIKE stock_market_snapshot;
CREATE TABLE IF NOT EXISTS stock_benchmark_before_price_repair_20261018 LIKE stock_benchmark_return_series;
START TRANSACTION;
INSERT IGNORE INTO stock_market_snapshot_before_price_repair_20261018
SELECT m.* FROM stock_market_snapshot m
JOIN stock_candle_daily c ON c.symbol=m.symbol AND c.trade_date=m.snapshot_date
WHERE m.symbol IN ('KLAC','CTVA','CRWD','APH','MNST','DD','SPGI','HON')
  AND (NOT(m.close_price <=> c.close_price) OR NOT(m.volume <=> c.volume));
INSERT IGNORE INTO stock_benchmark_before_price_repair_20261018 SELECT * FROM stock_benchmark_return_series;
UPDATE stock_market_snapshot m
JOIN stock_candle_daily c ON c.symbol=m.symbol AND c.trade_date=m.snapshot_date
JOIN stock_market_snapshot_before_price_repair_20261018 b
    ON b.index_code=m.index_code AND b.symbol=m.symbol AND b.snapshot_date=m.snapshot_date
SET m.close_price=c.close_price, m.volume=c.volume, m.price_source=c.source,
    m.market_cap_source=CASE WHEN NOT(b.close_price <=> c.close_price)
         THEN CONCAT('BASIS_UNVERIFIED:',LEFT(COALESCE(b.market_cap_source,'UNKNOWN'),63))
         ELSE m.market_cap_source END,
    m.calculated_at=CURRENT_TIMESTAMP
WHERE m.symbol IN ('KLAC','CTVA','CRWD','APH','MNST','DD','SPGI','HON')
  AND (NOT(m.close_price <=> c.close_price) OR NOT(m.volume <=> c.volume));
COMMIT;
