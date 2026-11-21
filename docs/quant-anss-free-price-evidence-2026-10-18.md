# ANSS public historical-price evidence — 2026-10-18

## Outcome and permitted interpretation

Recovered retained ANSS prices without an account, API key, payment, ticker substitution, or browser-challenge bypass. The longer provider-close research series contains **370 sessions, 2024-01-24 through 2025-07-16**. A second provider supplies **121 sessions, 2025-01-22 through 2025-07-16**, with an explicit split-adjustment statement. Neither is official exchange execution evidence, independently audited corporate-action adjustment evidence, a total-return series, or proof of commercial redistribution rights. No product, database, model, Git, or main pipeline files were changed by this source lane.

The 2025-05-30 candidate is present, with 338 strictly earlier observations in ChartExchange and 89 in StockAnalysis, satisfying the requested minimum 80-bar lookback. Both contain all 21 requested outcome dates from June 2 to July 1. A June 30 candidate's 21-session outcome crosses the July acquisition; it remains ineligible without separately evidenced settlement handling. Do not forward-fill the July 16 close, substitute SNPS, or impute merger recovery.

## Exact adapter inputs

All paths below are relative to repository root `C:/dev/codex-demo`.

| File | Rows | Interpretation |
| --- | ---: | --- |
| `tmp/quant-anss-free-evidence-20261018/chartexchange-anss.csv` | 370 | Canonical longer provider-reported close research series; adjustment method unverified |
| `tmp/quant-anss-free-evidence-20261018/stockanalysis-anss.csv` | 121 | Separate Tiingo-sourced, provider-declared split-adjusted corroboration/short series |
| `tmp/quant-anss-free-evidence-20261018/evidence.manifest.json` | — | Source URLs, request/completion UTC timestamps, response status/headers, byte counts, SHA-256 hashes, identity, basis and rights flags |
| `tmp/quant-anss-free-evidence-20261018/profile.json` | — | Per-provider profile, excluded row, full cross-provider field differences |

Both CSVs are ascending date order, one ANSS session per row, UTF-8 with header:

```text
date,open,high,low,close,adjusted_close,volume,provider,symbol,currency
```

CSV SHA-256:

```text
chartexchange-anss.csv d3bd7e80e31fa9a082f82ac30caa52de017aa6a06b36e8c127068d8bf52247c1
stockanalysis-anss.csv 94a98dbb15ca9ad3e7b6273736155511afcbb7c72da979abef690c7449cdead6
```

Dates are `YYYY-MM-DD`; OHLC values are provider numeric values; volume is a positive integer; symbol is `ANSS`; currency is `USD`; provider is `chartexchange` or `stockanalysis`. ChartExchange `adjusted_close` is deliberately empty, not copied from close. StockAnalysis retains numeric hydration-payload precision (including fractional cents), rather than rounding to its visible table. An adapter must preserve the source/basis label and must not blend these OHLCV series. No import was performed.

## Sources, identity, and adjustment basis

1. [ChartExchange ANSS historical prices](https://chartexchange.com/symbol/nasdaq-anss/historical/) identifies ANSS as Ansys Inc, NASDAQ, in USD, inactive with last price dated July 16, 2025. The downloaded HTML table contains 370 daily OHLCV rows. No split/dividend-adjustment methodology is stated in the retained page, so its field is provider-reported close only. Its `marketDataLicensed` client flag describes the provider's own state, not a license granted to this project.
2. [StockAnalysis ANSS history](https://stockanalysis.com/stocks/anss/history/) identifies ANSYS, Inc., ANSS, NASDAQ, USD, CIK `0001013462`, and delisting July 17 due to acquisition by SNPS. It attributes historical data to Tiingo and explicitly states that past prices are adjusted for stock splits. The visible HTML table has 50 rows including a July 17 zero-volume carry row; the same public HTML's hydration payload has 122 rows. Extraction retains 121 actual rows and rejects the July 17 zero-volume/post-last-trade row. No extra API endpoint, JavaScript execution, or hidden authenticated interface was requested. Adjusted close equals close in all 121 retained rows; this alone does not prove absence of dividends or independently validate split factors.
3. [Stooq daily-download endpoint](https://stooq.com/q/d/l/?s=anss.us&i=d&d1=20200101&d2=20250716) did not supply usable prices: the ordinary network response was an HTTP 200 JavaScript browser-verification challenge. The challenge was saved but not executed, solved, or bypassed. No conclusion about whether Stooq possesses ANSS history is supported by this response.

## Bounded acquisition log

Exactly four local retrieval invocations, three distinct provider URLs:

| Attempt | UTC start | Result | Preserved body |
| --- | --- | --- | --- |
| 1 | 14:24:29.723551 | Stooq request failed sandbox DNS; no HTTP response | `stooq.body`, 0 bytes |
| 2 | 14:24:41.161685 | Ordinary approved-network retry to same Stooq URL: HTTP 200 challenge | `stooq-network.body`, 796 bytes |
| 3 | 14:24:48.235273 | StockAnalysis HTTP 200 | `stockanalysis.body`, 109,743 bytes |
| 4 | 14:25:02.012920 | ChartExchange HTTP 200 | `chartexchange.body`, 335,177 bytes |

All timestamps are 2026-10-18 UTC. The individual `*.manifest.json` files contain exact request and completion times and response headers. Search discovery used ordinary web search; no further provider endpoints were requested. Full 2020–2023 price history was not recovered in this bounded lane.

Response-body SHA-256:

```text
stockanalysis.body a77bd805540b0791c481d726ebb940967d11aebb78893ecbeffaebe497833ced
chartexchange.body 6f306b6222ed586812b206a8f0098eac9ed301f943a5135eb97377dffcef4ff0
stooq-network.body a265749152c3c3d17d21321e69df5bcb5992ab3f71a25e42ac021a5eb62896e4
```

## Coverage and quality findings

- Both series have zero duplicate dates and zero OHLC ordering violations; retained rows have finite positive OHLC and positive volume. These checks do not certify quote accuracy.
- All 121 StockAnalysis dates also appear in ChartExchange. 120 closes agree exactly; February 13 differs by half a cent (339.165 versus 339.17), consistent with precision/rounding but not independently proven. All 49 closes from May 6 through July 16 agree exactly.
- Volume differs on 46 common dates, mostly January 22–March 26, with potentially material differences; e.g. February 26 is 404,780 versus 271,092. Different venue/consolidation or historical revision practices are possible, not established. Treat volume-sensitive models as unsupported by cross-provider equivalence; do not splice providers to repair volume. OHLC non-close differences largely reflect displayed cent precision. Full differences are retained in `profile.json`.
- The explicit requested outcome-date set is present exactly: June 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 16, 17, 18, 20, 23, 24, 25, 26, 27, 30, and July 1. This is a check against the task-supplied 21-date set, not independent certification of the entire exchange calendar.
- ChartExchange's earlier history could support earlier price-only research windows after sufficient bars; downstream membership, fundamentals, benchmark and prediction gates must still be evaluated by the main pipeline. No such eligibility or Quant skill claim is made here.
- `license_unknown=true`, `permission_to_redistribute_verified=false`. Public accessibility is not permission to commercially reuse or redistribute provider data. Keep this evidence research-scoped until rights are reviewed.

## Reproduction and verification

Offline reproduction and validation (no network):

```powershell
python tmp/quant-anss-free-evidence-20261018/profile.py
python tmp/quant-anss-free-evidence-20261018/verify.py
```

Fresh verification on October 18 passed: StockAnalysis 121 rows / 89 earlier bars / 21 outcome sessions; ChartExchange 370 rows / 338 earlier bars / 21 outcome sessions; all preserved source and derived-output byte lengths and SHA-256 hashes. This validates extraction and the stated bounded checks only, not model performance, exchange adjustment accuracy, or licensing.
