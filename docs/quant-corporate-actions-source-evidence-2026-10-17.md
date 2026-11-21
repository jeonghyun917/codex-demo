# Corporate-action source evidence — 2026-10-17

## Result and permitted interpretation

Actual public split/dividend history has been acquired and saved locally, with original response bytes, URLs, retrieval times, SHA-256 hashes, normalized events, and explicit failures. This is **evidence-only backfill**, not a complete corporate-action master, a canonical price-adjustment instruction, proof of historical predictor inputs, or prediction/backtest performance validation. No application, schema, database, canonical candle, prediction, or model state was changed by this acquisition workstream.

The data-quality workflow prioritized event grain, provenance, adjustment semantics, completeness, missing issuers, and point-in-time limitations. It does not expand the acceptance criteria into unrelated model changes.

## Acquired artifacts

All paths below are relative to the repository root.

| Artifact | Content |
| --- | --- |
| `tmp/pit-acquisition/actions/bundle.json` | 603 Yahoo chart source records; 11,798 normalized provider events: 110 SPLIT records and 11,688 DIVIDEND records |
| `tmp/pit-acquisition/actions/events.csv` | Same normalized provider events in CSV form |
| `tmp/pit-acquisition/actions/raw/` | 603 unchanged JSON responses, 106,909,370 bytes; 985,939 daily timestamps and retained OHLC/volume/adjusted-close arrays |
| `tmp/pit-acquisition/actions/manifest.json` | All 660 requested symbols, per-symbol success/failure, bar/date/event counts, and collection times |
| `tmp/pit-acquisition/actions/official-bundle.json` | Nine issuer/SEC raw-source records and four normalized APH events: three official share splits and one dividend payment |
| `tmp/pit-acquisition/actions/official/` | Nine unchanged issuer/SEC HTML response snapshots |
| `tmp/pit-acquisition/actions/complex-events.json` | Three explicitly limited official examples: GE→GEV spinoff, ATVI cash merger, VMW cash-or-stock merger |
| `tmp/pit-acquisition/actions/verification.json` | Hash, source-reference, duplicate-grain, positive-ratio/amount, upper-date-bound checks, plus all failures and short histories |

Universe: the distinct union of database `stock_index_membership_snapshot` and `stock_candle_daily` symbols, the existing Wikipedia-derived membership CSV, and a small explicit event sample. Symbol retrieval was a read-only local database query. Index instruments and ETFs present in that union were retained rather than silently narrowing the request.

Yahoo acquisition: 660 requested, 603 successful responses (91.36%), 57 failed (8.64%), zero unattempted symbols after the bounded continuation. Every failure was HTTP 404; no retries were made for those failures. Two workers with a 120 ms per-request pause were used; HTTP 429 stops further requests. Initial conservative failure budgeting stopped the first pass after 12 failures; the continuation requested only previously unattempted symbols and preserved all first-pass successes and failures. First-pass manifests are retained with `.pass1` suffixes.

Original source requests were initiated between approximately 14:45 and 14:48 UTC on 2026-10-17. The final continuation finished at `2026-10-17T14:48:31.075910Z`; its `startedAt` is the continuation start, not the first fetch. Per-source `retrievedAt` is the UTC request-start capture immediately preceding the response, not a historical publication timestamp.

Request parameters: `/v8/finance/chart/{symbol}?period1=1577836800&period2=1792195200&interval=1d&events=div,splits,capitalGains&includeAdjustedClose=true`. Requested dates were 2020-01-01 through 2026-10-17 exclusive. Observed provider-event dates are **2019-12-31 through 2026-10-16**: the provider included 11 prior-boundary dividend records despite the requested start; these remain visible in the raw and normalized evidence rather than being relabeled. Observed bar range starts 2020-01-02. Event timestamps were converted using each response's exchange timezone; US samples use America/New_York.

## APH: verified dates and adjustment hazard

The [issuer's split-history table](https://investors.amphenol.com/stock-information/dividend-and-stock-split-history/default.aspx) explicitly identifies the following ex-dates. The current table has no historical publication timestamp, so its source `publishedDate` is null.

| Official ex-date | Official delivered-share ratio | Announcement date | Distribution date |
| --- | --- | --- | --- |
| 2021-03-05 | 2 new shares per 1 old share | 2021-01-27 | 2021-03-04 |
| 2024-06-12 | 2 new shares per 1 old share | 2024-05-20 | 2024-06-11 |
| 2026-09-03 | 2 new shares per 1 old share | 2026-08-06 | 2026-09-02 |

Announcement sources are the [2021 announcement](https://investor.amphenol.com/news-and-events/news-details/2021/Amphenol-Reports-Record-Fourth-Quarter-2020-Results-and-Announces-2-for-1-Stock-Split/default.aspx), [2024 announcement](https://investor.amphenol.com/news-and-events/news-details/2024/Amphenol-Corporation-Announces-Two-for-One-Stock-Split/default.aspx), and [2026 announcement](https://investors.amphenol.com/news-and-events/news-details/2026/Amphenol-Announces-Two-for-One-Stock-Split-and-Third-Quarter-2026-Dividend/default.aspx). Their calendar publication dates do not establish intraday announcement times or actual receipt by the application's historical data pipeline.

The 2026 issuer announcement also declares $0.25 per pre-split share, equivalent to $0.125 per post-split share, payable 2026-10-14 to holders of record 2026-09-22. The official normalized dividend therefore uses `dateBasis=PAYMENT_DATE`, `effectiveDate=2026-10-14`, and `amountBasis=POST_SPLIT_PER_SHARE`. It is deliberately **not** labeled an ex-date from this announcement alone. Yahoo independently reports a dividend event on 2026-09-22 with amount 0.125, labeled provider convention rather than official date verification.

The saved APH Yahoo response already contains split-adjusted historical `quote.close`: 2026-07-24 = 76.33499908447266; 2026-09-02 = 80.04000091552734; 2026-09-03 = 82.06999969482422. `adjclose` is a separate field (for example July 24 = 76.21678924560547). The near-continuous series across the official September split shows that multiplying/dividing historical Yahoo OHLC by this split again would be erroneous. Official share ratios do not authorize modifying an already adjusted price series.

Yahoo's [adjusted-close explanation](https://in.help.yahoo.com/kb/adjusted-close-sln28256.html) describes split and dividend adjustment. Its content was available in search results but direct fetch returned 429 during this work, so no local snapshot or source-hash claim is made for that help page. The saved chart responses, official APH table, and concrete price examples are the independently inspectable evidence here.

Historical provider dividend amounts also require a share-basis contract. The APH 2020-03-16 vendor amount is 0.03125; later splits can retrospectively change per-share representations. Do not combine such amounts with original-date share counts without verified conversion. All vendor dividend amounts retain `amountBasis=PROVIDER_REPORTED_UNVERIFIED`.

## Provider SPLIT does not necessarily mean delivered shares

Vendor `events.splits` includes nonliteral distribution-price adjustment factors. The saved GE response includes 1281:1000 on 2023-01-04 and 1253:1000 on 2024-04-02; MMM includes 1196:1000 on 2024-04-01. These are not automatically literal increases in the number of GE or MMM shares held.

For the GE Vernova distribution, the [GE announcement](https://www.ge.com/news/press-releases/ge-board-of-directors-approves-spin-off-of-ge-vernova-ge-vernova-and-ge-aerospace-to) and [GE Vernova completion statement](https://www.gevernova.com/news/articles/ge-vernova-completes-spin) instead establish one **GEV** share distributed per four **GE** shares, before market open on 2024-04-02, with parent GE shares retained. A portfolio must track the distributed security and entitlement, not apply 1.253 as a GE share multiplier.

Therefore every normalized provider SPLIT has `ratioBasis=PROVIDER_ADJUSTMENT_FACTOR_UNVERIFIED`; official APH splits have `ratioBasis=OFFICIAL_SHARE_RATIO`. The normalized action name preserves the vendor field group and is not an economic-action classification. Neither bundle instructs a canonical adjustment.

## Merger evidence and unresolved settlement accounting

The [Microsoft filing](https://www.sec.gov/Archives/edgar/data/789019/000119312523255762/d537928d8k.htm) confirms completion of the ATVI acquisition on 2023-10-13 and $95.00 cash consideration per eligible share, subject to the filing's ownership/appraisal exceptions. Yahoo returned 404 for ATVI; official consideration evidence does not supply the missing pre-delisting price series.

The [Broadcom filing](https://www.sec.gov/Archives/edgar/data/1730168/000114036123054354/ef20015100_form8k.htm) confirms completion of the VMW acquisition on 2023-11-22 and election terms of $142.50 cash or 0.25200 AVGO shares per eligible VMW share, subject to election/proration mechanics. The ratio is the as-transacted 2023 basis, not a later AVGO split-adjusted figure. The Broadcom completion press-release request returned 403 and is recorded as failed; the SEC filing was successfully saved and hashed. Yahoo returned 404 for VMW. Fractional-share cash, holder-specific elections/proration, settlement timing, and terminal portfolio accounting remain unmodeled.

## Coverage and quality findings

1. **High: incomplete delisted/renamed security coverage.** The 57 failures are ABMD, ALXN, ANSS, ATVI, CCR, CDAY, CERN, CMA, COG, CTLT, CTRA, CTXS, DAY, DFS, DISCA, DISCK, DISH, DLPH, DRE, FBHS, FI, FLT, FRC, FSR, GPS, HBI, HES, HOLX, HRS, IPG, JEC, JNPR, JOYG, K, KFT, KORS, KSU, LUK, MRO, MXIM, NCC, NLSN, PBCT, PXD, RE, SATS, SEE, SGEN, SIVB, SPLK, TSO, TWTR, VMW, WBA, WLP, WLTW, and XLNX. These are missing data, not proven zero actions. Their exclusion from a backtest can retain survivorship bias.

2. **High: HTTP success is not completeness.** AVB, EA, EQR, LEG, QRVO, and WBD each returned only one historical bar and did not reach the requested latest trading date. Nine successful responses have fewer than 100 bars, including recent listings. Forty-five start after 2020-01-02; some are valid IPO/spinoff starts, but identity/lifetime coverage has not been audited. Of 603 successful symbols, 493 have events; the other 110 empty event sets are not certification of no actions.

3. **High: ticker identity is not stable.** The ECHO response's current issuer name is EchoStar Corporation. A historical index symbol of the same spelling does not establish it represents the same security. An effective-dated security-ID/issuer mapping is needed before ticker-only histories are admitted into PIT evaluation. Raw provider identity metadata is retained for inspection.

4. **High: present-day backfill is not PIT predictor evidence.** Vendor event histories expose event dates, not announcement/first-available timestamps. Provider `publishedDate` is null. Source retrieval time is today; setting it to an old ex-date would fabricate historical availability. Official announcements have calendar dates only, and the mutable current issuer table was fetched today. None proves a historical model used only then-available inputs. Backfilled adjusted prices can embed future corporate actions even if price returns are economically useful for labels.

5. **High: no complete total-return/settlement contract.** Price-return, dividend-adjusted return, reinvestment assumptions, original/current share basis, spinoff entitlement, merger elections, delisting proceeds, and cash-in-lieu are separate concepts. A vendor adjustment field cannot stand in for all of them. These snapshots can support reconciliation and explicit exclusions but cannot by themselves qualify historical portfolio accounting as correct.

6. **Verified local integrity, not external accuracy.** All 612 saved source hashes matched their raw files; both bundles have zero duplicate `(symbol, action, effectiveDate)` rows, zero orphan source references, nonnegative dividend amounts, positive split factors, and no event on/after 2026-10-17. This verifies acquisition/normalization integrity only. Full issuer-by-issuer date/ratio/cash validation was not performed.

## Reproduction and operating boundary

Durable utilities are `scripts/acquire_quant_actions.py`, `scripts/acquire_quant_official_action_evidence.py`, and `scripts/verify_quant_actions.py`. They use only Python's standard library and default to the local evidence directory described above. The acquisition utility uses a read-only database query and public Yahoo requests; the official-source utility requests the explicit issuer/SEC sample. They refuse ordinary overwrites of existing evidence. First-pass raw files were preserved during the provider continuation. The original temporary helper versions remain as execution audit artifacts; use the durable versions for inspection or future work.

Raw third-party JSON/HTML snapshot directories are explicitly ignored by Git. Public accessibility is not a redistribution license: redistribution rights, provider terms, and long-term production-use permissions were **not** verified, so raw bodies must remain local. Normalized metadata and citations are not a claim of commercial data licensing. The quality manifest explicitly marks the collection uncertified, ECHO's issuer mapping ambiguous, and one-bar histories ineligible for certification.

Local verification (no network or database writes):

```powershell
python scripts/verify_quant_actions.py
```

The verification helper checks existing ratio-basis metadata and writes only `verification.json`; it never relabels or rewrites source bytes, normalized bundles, or CSVs. Regression tests enforce these properties. Java integration and final database validation are documented separately in `docs/quant-source-acquisition-results-2026-10-17.md`.

Safe downstream use: store sources/actions in an evidence-only lane, preserve partial/provider labels and hashes, require verified identity and price/share basis for any canonical transformation, and keep full-history/PIT/performance gates unqualified until missing delisted histories and historical input-availability evidence are actually supplied. No paid source, authentication, purchase, or license acceptance was used.
