INSERT INTO main_menu (code, label, href, sort_order, enabled)
VALUES
    ('dashboard', 'Dashboard', '/dashboard', 10, TRUE),
    ('stocks', 'Stocks', '/stocks', 20, TRUE),
    ('heatmap', 'Heatmap', '/stocks/heatmap', 30, TRUE),
    ('etfs', 'ETF', '/etfs', 35, TRUE),
    ('atelier', 'Atelier', '/atelier', 40, TRUE),
    ('systems', 'Systems', '#systems', 50, TRUE),
    ('signal', 'Signal', '/signals/backtest', 60, TRUE),
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

INSERT INTO stock_signal_weight_profile (code, name, description, active, source)
VALUES
    ('DEFAULT', 'Default Quant Signal', 'Current production reference profile. The live list still reads stored integrated Signal values.', TRUE, 'MANUAL_BASELINE'),
    ('BACKTEST_V1', 'Backtest v1 Candidate', 'Diagnostic candidate generated from Factor Performance Diagnostics. It is not applied to live Signal until explicitly promoted.', FALSE, 'BACKTEST_DIAGNOSTIC')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    source = VALUES(source);

INSERT INTO stock_signal_weight_profile_item (profile_code, factor, weight, enabled, reason, sort_order)
VALUES
    ('DEFAULT', 'Quality', 17.0000, TRUE, 'Current default factor ranking weight.', 10),
    ('DEFAULT', 'Valuation', 14.0000, TRUE, 'Current default factor ranking weight.', 20),
    ('DEFAULT', 'Growth', 14.0000, TRUE, 'Current default factor ranking weight.', 30),
    ('DEFAULT', 'Earnings', 13.0000, TRUE, 'Current default factor ranking weight.', 40),
    ('DEFAULT', 'Stability', 9.0000, TRUE, 'Current default factor ranking weight.', 50),
    ('DEFAULT', 'Momentum', 8.0000, TRUE, 'Current default factor ranking weight.', 60),
    ('DEFAULT', 'Risk', 8.0000, TRUE, 'Current default factor ranking weight.', 70),
    ('DEFAULT', 'Analyst', 7.0000, TRUE, 'Current default factor ranking weight.', 80),
    ('DEFAULT', 'Institution', 6.0000, TRUE, 'Current default factor ranking weight.', 90),
    ('DEFAULT', 'News', 4.0000, TRUE, 'Current default factor ranking weight.', 100)
ON DUPLICATE KEY UPDATE
    weight = VALUES(weight),
    enabled = VALUES(enabled),
    reason = VALUES(reason),
    sort_order = VALUES(sort_order);

INSERT INTO etf_profile (
    symbol, name, issuer, category, strategy, region, asset_class, currency, benchmark,
    expense_ratio, dividend_yield, aum_million, holdings_count, inception_date, description,
    website, sort_order, source, active
)
VALUES
    ('SPY', 'SPDR S&P 500 ETF Trust', 'State Street', 'US Large Blend', 'S&P 500', 'US', 'Equity', 'USD', 'S&P 500 Index', 0.094500, NULL, NULL, 503, '1993-01-22', 'Tracks the S&P 500 large-cap US equity index.', 'https://www.ssga.com', 10, 'MANUAL_V1', TRUE),
    ('IVV', 'iShares Core S&P 500 ETF', 'BlackRock', 'US Large Blend', 'S&P 500', 'US', 'Equity', 'USD', 'S&P 500 Index', 0.030000, NULL, NULL, 503, '2000-05-15', 'Low-cost ETF tracking the S&P 500 index.', 'https://www.ishares.com', 20, 'MANUAL_V1', TRUE),
    ('VOO', 'Vanguard S&P 500 ETF', 'Vanguard', 'US Large Blend', 'S&P 500', 'US', 'Equity', 'USD', 'S&P 500 Index', 0.030000, NULL, NULL, 503, '2010-09-07', 'Vanguard ETF tracking the S&P 500 index.', 'https://investor.vanguard.com', 30, 'MANUAL_V1', TRUE),
    ('VTI', 'Vanguard Total Stock Market ETF', 'Vanguard', 'US Total Market', 'Total Market', 'US', 'Equity', 'USD', 'CRSP US Total Market Index', 0.030000, NULL, NULL, NULL, '2001-05-24', 'Broad exposure to the US equity market.', 'https://investor.vanguard.com', 40, 'MANUAL_V1', TRUE),
    ('QQQ', 'Invesco QQQ Trust', 'Invesco', 'US Large Growth', 'Nasdaq 100', 'US', 'Equity', 'USD', 'Nasdaq-100 Index', 0.200000, NULL, NULL, 101, '1999-03-10', 'Tracks the Nasdaq-100 index.', 'https://www.invesco.com', 50, 'MANUAL_V1', TRUE),
    ('QQQM', 'Invesco NASDAQ 100 ETF', 'Invesco', 'US Large Growth', 'Nasdaq 100', 'US', 'Equity', 'USD', 'Nasdaq-100 Index', 0.150000, NULL, NULL, 101, '2020-10-13', 'Lower-cost Nasdaq-100 ETF.', 'https://www.invesco.com', 60, 'MANUAL_V1', TRUE),
    ('DIA', 'SPDR Dow Jones Industrial Average ETF Trust', 'State Street', 'US Large Value', 'Dow 30', 'US', 'Equity', 'USD', 'Dow Jones Industrial Average', 0.160000, NULL, NULL, 30, '1998-01-14', 'Tracks the Dow Jones Industrial Average.', 'https://www.ssga.com', 70, 'MANUAL_V1', TRUE),
    ('IWM', 'iShares Russell 2000 ETF', 'BlackRock', 'US Small Blend', 'Small Cap', 'US', 'Equity', 'USD', 'Russell 2000 Index', 0.190000, NULL, NULL, NULL, '2000-05-22', 'Small-cap US equity exposure.', 'https://www.ishares.com', 80, 'MANUAL_V1', TRUE),
    ('SCHD', 'Schwab U.S. Dividend Equity ETF', 'Schwab', 'US Dividend', 'Dividend', 'US', 'Equity', 'USD', 'Dow Jones U.S. Dividend 100 Index', 0.060000, NULL, NULL, 100, '2011-10-20', 'US dividend equity ETF.', 'https://www.schwabassetmanagement.com', 90, 'MANUAL_V1', TRUE),
    ('VYM', 'Vanguard High Dividend Yield ETF', 'Vanguard', 'US Dividend', 'Dividend', 'US', 'Equity', 'USD', 'FTSE High Dividend Yield Index', 0.060000, NULL, NULL, NULL, '2006-11-10', 'High dividend yield US equity ETF.', 'https://investor.vanguard.com', 100, 'MANUAL_V1', TRUE),
    ('VIG', 'Vanguard Dividend Appreciation ETF', 'Vanguard', 'US Dividend', 'Dividend Growth', 'US', 'Equity', 'USD', 'S&P U.S. Dividend Growers Index', 0.060000, NULL, NULL, NULL, '2006-04-21', 'Dividend growth US equity ETF.', 'https://investor.vanguard.com', 110, 'MANUAL_V1', TRUE),
    ('XLK', 'Technology Select Sector SPDR Fund', 'State Street', 'Sector Technology', 'Sector', 'US', 'Equity', 'USD', 'Technology Select Sector Index', 0.090000, NULL, NULL, NULL, '1998-12-16', 'US technology sector ETF.', 'https://www.sectorspdrs.com', 120, 'MANUAL_V1', TRUE),
    ('XLV', 'Health Care Select Sector SPDR Fund', 'State Street', 'Sector Health Care', 'Sector', 'US', 'Equity', 'USD', 'Health Care Select Sector Index', 0.090000, NULL, NULL, NULL, '1998-12-16', 'US health care sector ETF.', 'https://www.sectorspdrs.com', 130, 'MANUAL_V1', TRUE),
    ('XLF', 'Financial Select Sector SPDR Fund', 'State Street', 'Sector Financials', 'Sector', 'US', 'Equity', 'USD', 'Financial Select Sector Index', 0.090000, NULL, NULL, NULL, '1998-12-16', 'US financials sector ETF.', 'https://www.sectorspdrs.com', 140, 'MANUAL_V1', TRUE),
    ('XLE', 'Energy Select Sector SPDR Fund', 'State Street', 'Sector Energy', 'Sector', 'US', 'Equity', 'USD', 'Energy Select Sector Index', 0.090000, NULL, NULL, NULL, '1998-12-16', 'US energy sector ETF.', 'https://www.sectorspdrs.com', 150, 'MANUAL_V1', TRUE),
    ('XLY', 'Consumer Discretionary Select Sector SPDR Fund', 'State Street', 'Sector Consumer Discretionary', 'Sector', 'US', 'Equity', 'USD', 'Consumer Discretionary Select Sector Index', 0.090000, NULL, NULL, NULL, '1998-12-16', 'US consumer discretionary sector ETF.', 'https://www.sectorspdrs.com', 160, 'MANUAL_V1', TRUE),
    ('VNQ', 'Vanguard Real Estate ETF', 'Vanguard', 'US Real Estate', 'REIT', 'US', 'Equity', 'USD', 'MSCI US Investable Market Real Estate 25/50 Index', 0.130000, NULL, NULL, NULL, '2004-09-23', 'US real estate and REIT ETF.', 'https://investor.vanguard.com', 170, 'MANUAL_V1', TRUE),
    ('SOXX', 'iShares Semiconductor ETF', 'BlackRock', 'Semiconductors', 'Industry', 'US', 'Equity', 'USD', 'NYSE Semiconductor Index', 0.350000, NULL, NULL, NULL, '2001-07-10', 'Semiconductor industry ETF.', 'https://www.ishares.com', 180, 'MANUAL_V1', TRUE),
    ('SMH', 'VanEck Semiconductor ETF', 'VanEck', 'Semiconductors', 'Industry', 'US', 'Equity', 'USD', 'MVIS US Listed Semiconductor 25 Index', 0.350000, NULL, NULL, NULL, '2011-12-20', 'US-listed semiconductor ETF.', 'https://www.vaneck.com', 190, 'MANUAL_V1', TRUE),
    ('TLT', 'iShares 20+ Year Treasury Bond ETF', 'BlackRock', 'Long Treasury', 'Bond', 'US', 'Fixed Income', 'USD', 'ICE U.S. Treasury 20+ Year Bond Index', 0.150000, NULL, NULL, NULL, '2002-07-22', 'Long-duration US Treasury bond ETF.', 'https://www.ishares.com', 200, 'MANUAL_V1', TRUE),
    ('BND', 'Vanguard Total Bond Market ETF', 'Vanguard', 'Core Bond', 'Bond', 'US', 'Fixed Income', 'USD', 'Bloomberg U.S. Aggregate Float Adjusted Index', 0.030000, NULL, NULL, NULL, '2007-04-03', 'Broad US investment-grade bond ETF.', 'https://investor.vanguard.com', 210, 'MANUAL_V1', TRUE),
    ('AGG', 'iShares Core U.S. Aggregate Bond ETF', 'BlackRock', 'Core Bond', 'Bond', 'US', 'Fixed Income', 'USD', 'Bloomberg U.S. Aggregate Bond Index', 0.030000, NULL, NULL, NULL, '2003-09-22', 'Core US aggregate bond ETF.', 'https://www.ishares.com', 220, 'MANUAL_V1', TRUE),
    ('HYG', 'iShares iBoxx $ High Yield Corporate Bond ETF', 'BlackRock', 'High Yield Bond', 'Bond', 'US', 'Fixed Income', 'USD', 'Markit iBoxx USD Liquid High Yield Index', 0.490000, NULL, NULL, NULL, '2007-04-04', 'US high-yield corporate bond ETF.', 'https://www.ishares.com', 230, 'MANUAL_V1', TRUE),
    ('GLD', 'SPDR Gold Shares', 'World Gold Council', 'Gold', 'Commodity', 'Global', 'Commodity', 'USD', 'Gold Bullion', 0.400000, NULL, NULL, NULL, '2004-11-18', 'Gold bullion-backed ETF.', 'https://www.spdrgoldshares.com', 240, 'MANUAL_V1', TRUE),
    ('SLV', 'iShares Silver Trust', 'BlackRock', 'Silver', 'Commodity', 'Global', 'Commodity', 'USD', 'Silver Bullion', 0.500000, NULL, NULL, NULL, '2006-04-21', 'Silver bullion-backed ETF.', 'https://www.ishares.com', 250, 'MANUAL_V1', TRUE),
    ('EFA', 'iShares MSCI EAFE ETF', 'BlackRock', 'Developed ex-US', 'International', 'Developed Markets', 'Equity', 'USD', 'MSCI EAFE Index', 0.330000, NULL, NULL, NULL, '2001-08-14', 'Developed markets outside the US and Canada.', 'https://www.ishares.com', 260, 'MANUAL_V1', TRUE),
    ('EEM', 'iShares MSCI Emerging Markets ETF', 'BlackRock', 'Emerging Markets', 'International', 'Emerging Markets', 'Equity', 'USD', 'MSCI Emerging Markets Index', 0.700000, NULL, NULL, NULL, '2003-04-07', 'Emerging markets equity ETF.', 'https://www.ishares.com', 270, 'MANUAL_V1', TRUE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    issuer = VALUES(issuer),
    category = VALUES(category),
    strategy = VALUES(strategy),
    region = VALUES(region),
    asset_class = VALUES(asset_class),
    currency = VALUES(currency),
    benchmark = VALUES(benchmark),
    expense_ratio = VALUES(expense_ratio),
    dividend_yield = VALUES(dividend_yield),
    aum_million = VALUES(aum_million),
    holdings_count = VALUES(holdings_count),
    inception_date = VALUES(inception_date),
    description = VALUES(description),
    website = VALUES(website),
    sort_order = VALUES(sort_order),
    source = VALUES(source),
    active = VALUES(active);
