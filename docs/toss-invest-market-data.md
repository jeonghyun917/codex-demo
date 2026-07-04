# Toss Invest Market Data

This project uses Toss Invest Open API only for read-only market and reference data.
Order creation, order modification, and order cancellation APIs are intentionally not implemented.

## Purpose

Toss data is stored in MariaDB before it is used by Quant AI.

The ingestion path is:

1. OAuth2 client credentials token is issued in memory.
2. Read-only market/reference endpoints are called.
3. Raw response JSON is stored in Toss-specific tables.
4. Daily candles are also copied into `stock_candle_daily` with `source = 'TOSS'`.
5. Quant Signal may use the stored data only after coverage and quality checks.

## Data Collected

Default enabled:

- `GET /api/v1/prices`
  - Stored in `toss_market_price_snapshot`
  - Useful for current price validation against Yahoo/Finnhub.
- `GET /api/v1/stocks`
  - Stored in `toss_stock_info`
  - Useful for ISIN, market, security type, list date, and shares outstanding.
- `GET /api/v1/candles`
  - Stored in `toss_candle`
  - Daily candles are also stored in `stock_candle_daily`.
- `GET /api/v1/stocks/{symbol}/warnings`
  - Stored in `toss_stock_warning`
  - Useful as a risk warning factor.
- `GET /api/v1/exchange-rate`
  - Stored in `toss_exchange_rate_snapshot`
  - Useful for USD/KRW conversion and portfolio reporting.
- `GET /api/v1/market-calendar/KR`
- `GET /api/v1/market-calendar/US`
  - Stored in `toss_market_calendar_snapshot`
  - Useful for trading-day alignment.

Optional, disabled by default:

- `GET /api/v1/price-limits`
  - Stored in `toss_price_limit_snapshot`
- `GET /api/v1/orderbook`
  - Stored in `toss_orderbook_snapshot`
  - Useful for spread/liquidity features, but expensive to collect broadly.
- `GET /api/v1/trades`
  - Stored in `toss_trade_print`
  - Useful for intraday activity features, but expensive to collect broadly.

Not implemented:

- `POST /api/v1/orders`
- `POST /api/v1/orders/{orderId}/modify`
- `POST /api/v1/orders/{orderId}/cancel`

## Batch Usage

Small smoke test:

```powershell
java -jar target\codex-demo-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=mariadb `
  --app.batch.toss.enabled=true `
  --app.batch.toss.exit-on-complete=true `
  --app.batch.toss.symbols=NVDA,AAPL,MSFT `
  --app.batch.toss.candle-count=30 `
  --app.signal.refresh.run-on-startup=false
```

Index batch:

```powershell
java -jar target\codex-demo-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=mariadb `
  --app.batch.toss.enabled=true `
  --app.batch.toss.exit-on-complete=true `
  --app.batch.toss.index-codes=SP500 `
  --app.batch.toss.symbol-limit=100 `
  --app.batch.toss.candle-count=120 `
  --app.signal.refresh.run-on-startup=false
```

Optional orderbook/trade collection should be narrow:

```powershell
java -jar target\codex-demo-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=mariadb `
  --app.batch.toss.enabled=true `
  --app.batch.toss.exit-on-complete=true `
  --app.batch.toss.symbols=NVDA,AAPL `
  --app.batch.toss.orderbook=true `
  --app.batch.toss.trades=true `
  --app.signal.refresh.run-on-startup=false
```

## Security

Credentials must be provided by environment variables:

- `TOSS_API_KEY`
- `TOSS_SECRET_KEY`

They must not be stored in Git, logs, database tables, templates, or browser JavaScript.
The OAuth2 access token is held in memory only.

If a key is shown in a screenshot or chat, rotate it immediately.

## Quant AI Use

The recommended order for using Toss data in Signal is:

1. Data coverage and conflict checks against existing Yahoo/Finnhub/SEC data.
2. Exchange-rate and market-calendar integration.
3. Daily candle source comparison and fallback rules.
4. Shares outstanding validation for point-in-time market cap estimates.
5. Warning/risk factors.
6. Optional spread/liquidity factors from orderbook snapshots.

Do not immediately overwrite existing Signal behavior with Toss values.
The stored data should be evaluated by backtest and data-quality diagnostics first.

## Current Quant AI Integration

Toss is now used by `stock_market_snapshot` as the preferred source for `shares_outstanding`.

The current market snapshot logic is:

1. Daily close and volume come from `stock_candle_daily`.
2. Shares outstanding comes from `toss_stock_info.shares_outstanding` when present.
3. Finnhub share outstanding is used as a fallback.
4. Market cap is calculated as `close_price * shares_outstanding`.
5. `stock_benchmark_return_series` builds a reproducible market-cap weighted index proxy from those snapshots.

This improves benchmark and beta validation, but it is still not perfect point-in-time shares history. Historical share counts, splits, buybacks, and new issuance remain future data-quality work.
