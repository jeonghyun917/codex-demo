# Eight historical-price discrepancies: event/source evidence — 2026-10-18

## Finding

The eight cached histories contain **1,314 closes on a different adjustment vintage** from the saved current Yahoo chart histories. Their abnormal date ranges are mostly in 2021, while the relevant official corporate actions occurred in 2026. This is consistent with a rolling-window refresh replacing only newer dates and leaving an older prefix on a previous adjustment basis. It is not evidence that an actual split occurred on the cached seam date. Ingestion timestamps/lineage are needed to assign every seam to a specific prior batch.

The free issuer/SEC sources corroborate the dates and economic nature of the relevant actions. All eight saved Yahoo histories contain 1,707 valid daily OHLCV records with an identical date set from 2020-01-02 to 2026-10-16. The eight Stooq requests returned browser-verification HTML, not usable price CSV; therefore they provide **no independent numeric price validation**. No fee, login, purchase, or challenge bypass was used.

This independent workstream performed source acquisition and read-only comparison only: no canonical prices, application code, schema, database state, predictions, or backtest results were modified.

## Exact observed cached regimes

Ratios below are observed cached close divided by current Yahoo close; they are diagnostics, **not instructions to multiply or divide any history**. The comparison is the entire 1,347-date canonical overlap per symbol, from 2021-06-07 through 2026-10-15, at relative tolerance `0.00001`.

| Symbol | Dates differing from current Yahoo | Rows | Observed ratio | First subsequent matching date |
| --- | --- | ---: | ---: | --- |
| KLAC | 2021-06-07–2021-06-10 | 4 | 10 | 2021-06-11 |
| CTVA | 2021-06-07–2025-10-08 | 1,091 | 6.665 | 2025-10-09 |
| CRWD | 2021-06-07–2021-07-01 | 19 | 4 | 2021-07-02 |
| APH | 2021-06-07–2021-09-17 | 73 | 2 | 2021-09-20 |
| MNST | 2021-06-07–2021-09-17 | 73 | 2 | 2021-09-20 |
| DD | 2021-06-07–2021-06-25 | 15 | 1/3 | 2021-06-28 |
| SPGI | 2021-06-07–2021-07-01 | 19 | 1.057 | 2021-07-02 |
| HON | 2021-06-07–2021-06-25 and 2021-06-30–2021-07-02 | 18 | 0.9535 | 2021-07-06 |
| HON | 2021-06-28–2021-06-29 | 2 | 1.0117, rounded | 2021-07-06 |

HON's two exceptional dates additionally show why a single inferred factor is not a safe repair. All remaining matched dates for these symbols agree within tolerance.

## Official corporate-action cross-check

| Symbol | Official action and effective/trading date | Saved Yahoo event field | Interpretation |
| --- | --- | --- | --- |
| KLAC | 10-for-1 split; split-adjusted trading 2026-06-12 | 10:1 on 2026-06-12 | Official literal share ratio agrees, but historical Yahoo OHLC is already adjusted |
| CTVA | One VYLR share distributed per CTVA share, expected before market open 2026-10-01 | 6665:1000 on 2026-10-01 | Economic spinoff; vendor price factor is not 6.665 new CTVA shares |
| CRWD | 4-for-1 split; distribution after close 2026-07-01, adjusted trading 2026-07-02 | 4:1 on 2026-07-02 | Official literal share ratio agrees; do not apply again |
| APH | 2-for-1 split ex-dates 2021-03-05, 2024-06-12, 2026-09-03 | 2:1 on each date | All three official ex-dates agree; current Yahoo includes them already |
| MNST | 2-for-1 split; shares distributed after close 2026-08-10, adjusted trading 2026-08-11 | 2:1 on 2026-08-11; also 2:1 on 2023-03-28 | 2026 official action agrees; older 2023 action retained as provider evidence in this bounded review |
| DD | 1-for-3 reverse split; adjusted trading 2026-06-24 | 1:3 on 2026-06-24; also 239:100 on 2025-11-03 | Official reverse ratio agrees; older spinoff-like vendor factor is not certified as literal shares here |
| SPGI | Completed distribution of one MBGL share per SPGI share at 00:01 New York time on 2026-07-01 | 1057:1000 on 2026-07-01 | Spinoff, not a 1.057 SPGI share split |
| HON | One HONA share per two HON shares, 00:01 New York time 2026-06-29, followed by HON 1-for-2 reverse split at 00:02 | 1907:2000 on 2026-06-29; older 1061:1000 on 2025-10-30 | Combined spinoff/reverse-split price adjustment; 0.9535 is not the actual HON share-count ratio |

Primary source links:

- [KLA announcement/SEC exhibit](https://ir.kla.com/sec-filings/all-sec-filings/content/0001193125-26-212093/d116682dex991.htm): announced 2026-05-07; nine additional shares per old share, adjusted trading June 12.
- [Corteva board distribution announcement](https://www.corteva.com/resources/media-center/corteva-board-approves-vylor-distribution.html): announced 2026-09-14; one-for-one Vylor distribution. This saved source describes expected timing, not an independently saved completion certificate.
- [CrowdStrike 2026-06-03 filing](https://www.sec.gov/Archives/edgar/data/1535527/000153552726000022/crwd-20260603.htm): four-for-one split and July 2 adjusted trading.
- [Amphenol official split-history table](https://investors.amphenol.com/stock-information/dividend-and-stock-split-history/default.aspx): explicit ex-dates and ratios; mutable current table, historical publication time unknown.
- [Monster issuer announcement](https://investors.monsterbevcorp.com/news-releases/news-release-details/monster-beverage-declares-two-one-stock-split) and [SEC quarterly filing](https://www.sec.gov/Archives/edgar/data/865752/000110465926092193/mnst-20260630x10q.htm): announced 2026-07-08. The issuer HTML was readable through web search but direct snapshot fetch returned 403; the SEC source was successfully saved and hashed.
- [DuPont reverse-split announcement](https://www.investors.dupont.com/news-and-media/press-release-details/2026/DuPont-Announces-Reverse-Stock-Split-and-Reaffirms-2026-Financial-Guidance/default.aspx): announced 2026-05-26; 1-for-3 and June 24 adjusted trading.
- [S&P Global completion filing](https://www.sec.gov/Archives/edgar/data/64040/000110465926080005/tm2619099d1_8k.htm): completed July 1; one MBGL per SPGI.
- [Honeywell board announcement](https://www.honeywell.com/us/en/news/press-releases/2026/06/honeywell-board-of-directors-sets-record-date-and-announces-expected-timing-for-spin-off-of-honeywell-aerospace-and-honeywell-reverse-stock-split) and [Honeywell Aerospace completion filing](https://www.sec.gov/Archives/edgar/data/2089271/000162828026045874/hona-20260625.htm): combined June 29 distribution and parent reverse split. Do not collapse the two legal actions into an inferred share multiplier.

## Current provider-price basis and continuity

The retained `indicators.quote` OHLC is the provider's present-day historical series; `adjclose` is a separate array. Event dates do not produce the literal split jumps in these histories. Examples:

| Symbol | Previous trading close | Action-day close |
| --- | --- | --- |
| KLAC | 2026-06-11: 241.164001 | 2026-06-12: 254.539993 |
| CRWD | 2026-07-01: 193.184998 | 2026-07-02: 193.979996 |
| APH | 2026-09-02: 80.040001 | 2026-09-03: 82.069999 |
| MNST | 2026-08-10: 45.715000 | 2026-08-11: 45.529999 |
| DD | 2026-06-23: 140.009995 | 2026-06-24: 137.820007 |

These observations support an already-adjusted provider basis, not raw contemporaneous execution prices. Dividend treatment, portfolio entitlements, and spinoff proceeds cannot be inferred from continuity alone. Numeric authenticity was not independently matched against another free provider during this bounded check.

## Replacement decision boundary

No obvious current-issuer mismatch, one-bar truncation, duplicate-date defect, nonpositive/null OHLC, invalid high/low envelope, negative/null volume, or missing-day disagreement **between these eight saved provider histories** was found. All eight current issuer names and symbols agree with the named companies. That is a narrower check than a complete effective-dated security-identity audit, particularly for CTVA, DD, SPGI and HON restructurings.

For a repair whose explicit contract is a consistent **current Yahoo provider-adjusted OHLCV history**, the source evidence supports replacing an entire validated history (with old rows backed up and provenance retained), rather than scaling only guessed anomaly segments or mixing refreshed prefixes/tails. This is not approval to label the result official-unadjusted, total-return, executable historical prices, or PIT-certified model inputs. Application-side transactionality, reconciliation tests, downstream invalidation/recomputation, and final residual-difference checks are owned by the main implementation workstream.

If the canonical contract requires original-date unadjusted prices or literal historical portfolio share/cash accounting, the existing Yahoo `quote` series alone is insufficient; use an explicit alternative contract instead of silently changing the meaning of canonical prices. Free public evidence does not remove that distinction.

## Reproducible local evidence

`tmp/price-reconciliation-2026-10-18/evidence/crosscheck.json` contains the original URLs, local paths, UTC retrieval times, SHA-256 hashes, all date regimes, provider event-day adjacent closes, and free-source outcomes. It contains 17 hashed response bodies: nine issuer/SEC documents and eight Stooq verification-page bodies. One issuer request failed. Original Yahoo responses are referenced by their existing SHA-256 and are not modified or duplicated.

`local-verification.json` records a fresh check of 25 hashes (17 new bodies plus eight existing Yahoo histories), identical complete date sets, and OHLCV validity for 13,656 provider bars. The scripts `acquire_and_compare.py`, `finalize_crosscheck.py`, and `verify_local_crosscheck.py` preserve the exact read-only acquisition and checks. The local-only verification command is:

```powershell
python tmp/price-reconciliation-2026-10-18/evidence/verify_local_crosscheck.py
```

Raw source bodies should remain local: redistribution and commercial production-use licensing have not been verified. Public source announcements and current retrieval times do not prove what any model actually knew before its historical decision date. This work does not qualify the historical constituents, prediction inputs, merger/spinoff settlement accounting, or model performance.
