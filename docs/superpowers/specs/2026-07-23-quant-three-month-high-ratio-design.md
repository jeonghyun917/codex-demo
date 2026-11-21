# Quant 3-Month High Ratio Design

## Goal

Replace the four trailing Quant table columns `Value`, `Growth`, `Momentum`,
and `Risk` with one sortable column that shows the current price as a percentage
of each stock's highest intraday price during the latest three-month market-data
window.

Example:

```text
Current price: 325.04
3-month high: 334.99
3M high ratio: 97.03%
```

The change applies to the `/quant` and `/dashboard` table because both routes
render `dashboard.html`.

## Definition

- **As-of date:** the latest `trade_date` available in `stock_candle_daily`.
- **Window:** dates from `as-of date - 3 calendar months` through the as-of date,
  inclusive.
- **3-month high:** `MAX(stock_candle_daily.high_price)` per symbol in that
  window.
- **Ratio:** `current_price / three_month_high * 100`.
- **Display:** two decimal places followed by `%`, for example `97.03%`.
- **Unavailable data:** display `-` and sort after numeric values.

Using the latest stored market date instead of the application server's current
date keeps every row on the same DB-backed window and avoids weekend, holiday,
or ingestion-lag drift.

## Approaches Considered

### 1. Aggregate once in the market-row SQL and format in Java — selected

`IndexConstituentMapper.findMarketRows` joins a grouped candle subquery that
returns one raw three-month high per symbol. `StockMarketViewService` validates
the current price and high, calculates the ratio, assigns a display tone, and
formats the result.

This keeps page rendering DB-backed, avoids N+1 queries, exposes a testable raw
boundary between persistence and presentation, and requires no new table.

### 2. Fetch recent candles per row in Java — rejected

This is easy to prototype but would issue hundreds of additional queries for the
S&P 500 page and make the interactive request path slower and harder to reason
about.

### 3. Persist a precomputed ratio snapshot — rejected

A batch-maintained snapshot would make reads cheap, but it adds schema,
scheduling, freshness, and recovery concerns that are unnecessary for one
aggregate derived from already indexed daily candles.

## Architecture and Data Flow

1. `IndexConstituentMapper.xml`
   - Build a single grouped derived table from `stock_candle_daily`.
   - Anchor the three-month window to the table's latest `trade_date`.
   - Select `threeMonthHigh` into each `StockMarketRow`.
   - Apply the same projection to `findMarketRowsBySymbols` so the row model
     remains consistent across mapper entry points.
2. `StockMarketRow`
   - Add nullable `BigDecimal threeMonthHigh`.
3. `StockMarketView.Row`
   - Replace the four trailing factor-score fields used only by this table with
     `threeMonthHighRatio`, `threeMonthHighRatioValue`, and
     `threeMonthHighRatioTone`.
   - The underlying factor data remains in `StockMarketRow` and the signal model;
     this change removes only their Quant table presentation.
4. `StockMarketViewService`
   - Calculate the ratio only when current price and three-month high are both
     positive.
   - Round to two decimal places using `HALF_UP`.
   - Return `-`, `null`, and `neutral` when data is unavailable or invalid.
5. `dashboard.html`
   - Remove the four factor headers and row cells.
   - Add one help-enabled sortable header labeled `3M 고점비율`.
   - Explain the formula and stored-data window in its tooltip.
   - Render the ratio with its semantic tone class.
   - Update the client-side column index map so numeric sorting continues to
     work.
6. `main.css`
   - Reduce the Quant row grid from 14 columns to 11.
   - Give the new ratio enough width for its label and tooltip.
   - Add three restrained tones:
     - `near-high`: ratio at least 95%
     - `mid-range`: ratio at least 85% and below 95%
     - `extended`: ratio below 85%

## Error Handling

- Missing candles, missing current price, zero/negative current price, or
  zero/negative high produce `-`.
- A quote above the stored three-month high is valid and may display above
  `100.00%`; the value is not clamped.
- Null ratios sort after numeric ratios using the table's existing sort behavior.
- No external API call is added to the page request path.

## Performance

The candle table is aggregated once per page query and joined by symbol. The
implementation must use the existing daily candle table and avoid per-row
mapper/service calls. The table currently contains roughly 61 trading-day rows
per covered symbol for the three-month window, which is appropriate for a
grouped database aggregate.

## Testing

Automated tests will cover:

- ratio calculation and rounding;
- values above 100%;
- each display-tone boundary;
- null, zero, and negative input handling;
- mapper SQL contract for the latest-market-date three-month aggregate;
- template contract proving the four old columns are absent and the new
  sortable column is present;
- client-side column-index alignment;
- CSS grid column count and tone classes.

Verification will include the focused tests, the full Maven suite, and a browser
check of the Quant page for data rendering, sorting, horizontal layout, and
console errors.
