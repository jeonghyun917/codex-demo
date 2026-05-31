INSERT INTO main_menu (code, label, href, sort_order, enabled)
VALUES
    ('dashboard', 'Dashboard', '#dashboard', 10, TRUE),
    ('stocks', 'Stocks', '/stocks', 20, TRUE),
    ('heatmap', 'Heatmap', '/stocks/heatmap', 30, TRUE),
    ('atelier', 'Atelier', '#atelier', 40, TRUE),
    ('systems', 'Systems', '#systems', 50, TRUE),
    ('signal', 'Signal', '#signal', 60, TRUE),
    ('contact', 'Contact', '#contact', 70, TRUE)
ON DUPLICATE KEY UPDATE
    label = VALUES(label),
    href = VALUES(href),
    sort_order = VALUES(sort_order),
    enabled = VALUES(enabled);

INSERT INTO side_menu (code, label, href, sort_order, enabled)
VALUES
    ('overview', 'Overview', '#overview', 10, TRUE),
    ('stocks', 'Stocks', '/stocks', 20, TRUE),
    ('reports', 'Reports', '#reports', 30, TRUE),
    ('settings', 'Settings', '#settings', 40, TRUE),
    ('archive', 'Archive', '#archive', 50, TRUE)
ON DUPLICATE KEY UPDATE
    label = VALUES(label),
    href = VALUES(href),
    sort_order = VALUES(sort_order),
    enabled = VALUES(enabled);

INSERT INTO stock_symbol (symbol, name, exchange, currency, active)
VALUES
    ('AAPL', 'Apple Inc', 'NASDAQ', 'USD', TRUE),
    ('MSFT', 'Microsoft Corp', 'NASDAQ', 'USD', TRUE),
    ('NVDA', 'NVIDIA Corp', 'NASDAQ', 'USD', TRUE),
    ('GOOGL', 'Alphabet Inc Class A', 'NASDAQ', 'USD', TRUE),
    ('AMZN', 'Amazon.com Inc', 'NASDAQ', 'USD', TRUE),
    ('META', 'Meta Platforms Inc', 'NASDAQ', 'USD', TRUE),
    ('TSLA', 'Tesla Inc', 'NASDAQ', 'USD', TRUE),
    ('AVGO', 'Broadcom Inc', 'NASDAQ', 'USD', TRUE),
    ('JPM', 'JPMorgan Chase & Co', 'NYSE', 'USD', TRUE),
    ('V', 'Visa Inc', 'NYSE', 'USD', TRUE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    exchange = VALUES(exchange),
    currency = VALUES(currency),
    active = VALUES(active);

UPDATE index_constituent
SET current_member = FALSE,
    removed_at = COALESCE(removed_at, CURRENT_TIMESTAMP)
WHERE symbol IN ('TICKER', 'SECURITY')
   OR UPPER(COALESCE(sector, '')) = 'TICKER';

UPDATE stock_symbol
SET active = FALSE
WHERE symbol IN ('TICKER', 'SECURITY');
