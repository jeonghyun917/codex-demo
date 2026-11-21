# Historical index-membership source evidence — 2026-10-17

## Result

Acquired **48 full official S&P Dow Jones Indices press releases** (3,091,313 raw HTML bytes) and normalized **147 dated official change events**: **139 SP500** and **8 DOW30**. The source publication days span **2021-12-03–2026-10-01**, and event effective dates span **2021-12-20–2026-10-06**. All 147 extracted events explicitly use before-open timing. **Complete historical universe dates established: 0.** No full point-in-time constituent dataset was obtained or certified.

The quarantined evidence bundle is `tmp/pit-acquisition/membership/bundle.json`; its coverage classification is `PARTIAL_OFFICIAL_CHANGE_EVENTS`, not `OFFICIAL_PIT` or `LIVE_CAPTURE`. Full responses are under the adjacent `raw/` directory. The reproducible, bounded acquisition helper is `tmp/pit-acquisition/membership/acquire_official_events.py`. No application tables or model parameters were changed by this acquisition work.

This work applied the data-quality workflow to separate event-level validity from universe-level completeness, and verification-before-completion to verify raw hashes, dates and event lineage. The requested source-evidence Markdown document is the primary report; no dashboard or investment recommendation is implied.

## Grain, coverage and checks

Each event represents one publisher assertion that a security share class is added to or removed from an index, on a specific effective date. Each source records its canonical URL, publisher, publication calendar day, retrieval instant, raw-file path, SHA-256, content type and HTTP status. Publication time-of-day is **not established** and is not invented. The local retrieval time is not the historical knowledge time.

| Effective year | SP500 + DOW30 events |
| --- | ---: |
| 2021 | 6 |
| 2022 | 29 |
| 2023 | 24 |
| 2024 | 38 |
| 2025 | 32 |
| 2026 through October 6 | 18 |
| Total | 147 |

Fresh local QA verified all 48 raw-content hashes, unique source IDs, event-to-source referential integrity, and publication day not later than effective date. Two duplicate presentation rows were removed. Under Armour's publisher ticker `UA/UAA` was retained as two distinct share-class events, not silently collapsed into one company. Blank effective-date cells carry forward only the preceding explicit date in the same publisher table; repeated table headers are skipped.

One downloaded release is excluded from the normalized sources/events: [Kenvue, August 9, 2023](https://press.spglobal.com/2023-08-09-Kenvue-Set-to-Join-S-P-500) gives an effective date of **TBA**. Its unresolved parse is recorded in the bundle's `failures`; raw response `raw/spglobal-014.html` remains available. The later [August 21, 2023 release](https://press.spglobal.com/2023-08-21-Kenvue-Set-to-Join-S-P-500-Advance-Auto-Parts-to-Join-S-P-SmallCap-600) supplies separately dated evidence. No date was inferred for the TBA announcement.

The collector inspected two official archive pages of 100 results each, selected titles containing `Set to Join S&P 500`, and downloaded 49 releases including three explicitly selected DJIA releases. This is a bounded **sample**, not an exhaustive index-change crawl. Title filtering can miss removals, corporate-action adjustments, temporary additions, dual listings and differently titled releases. The earliest sampled event occurs after the project's earlier 2021-06-21 evaluation start. No denominator exists for a credible percentage-completeness claim.

## Primary-source examples

The complete per-release manifest and event records are in the JSON bundle, including source hashes. Representative verified releases:

| Publication day | Index | Effective before open | Change | Primary source |
| --- | --- | --- | --- | --- |
| 2024-02-20 | DOW30 | 2024-02-26 | Add AMZN; remove WBA | [S&P official release](https://press.spglobal.com/2024-02-20-Amazon-com-Set-to-Join-Dow-Jones-Industrial-Average-Uber-to-Join-Dow-Jones-Transportation-Average) |
| 2024-09-06 | SP500 | 2024-09-23 | Add PLTR, DELL, ERIE; remove AAL, ETSY, BIO | [S&P official release](https://press.spglobal.com/2024-09-06-Palantir-Technologies,-Dell-Technologies,-and-Erie-Indemnity-Set-to-Join-S-P-500-Others-to-Join-S-P-MidCap-400-and-S-P-SmallCap-600) |
| 2024-11-01 | DOW30 | 2024-11-08 | Add NVDA, SHW; remove INTC, DOW | [S&P official release](https://press.spglobal.com/2024-11-01-NVIDIA-and-Sherwin-Williams-Set-to-Join-Dow-Jones-Industrial-Average-Vistra-to-Join-Dow-Jones-Utility-Average) |
| 2026-06-23 | DOW30 | 2026-06-29 | Add GOOGL; remove VZ | [S&P official release](https://press.spglobal.com/2026-06-23-Alphabet-Set-to-Join-and-Honeywell-International-to-Remain-in-Dow-Jones-Industrial-Average) |

The June 2026 DJIA change is material to source reconciliation: the current Wikipedia helper explicitly hardcodes the 2024 changes but not this later change. This observation does not prove every other DJIA change has been captured. June 29 is outside the earlier June 26 evaluation endpoint, but matters for later snapshots.

## Nasdaq research: public evidence exists; raw acquisition is blocked

The official Nasdaq investor-relations pages were readable through the web reader. Direct public HTML and PDF download attempts returned HTTP 403; alternative `www.nasdaq.com/press-release/` download attempts timed out. No login, bot challenge, paid entitlement or terms acceptance was bypassed. Consequently **no Nasdaq source is included in the raw-download bundle**, and no downloaded Nasdaq membership count is claimed.

These annual announcement dates and effective dates are supported by the official pages, and can guide a later authorized acquisition:

| Publication day | Effective before market open | Announced company additions / removals | Official page |
| --- | --- | --- | --- |
| 2021-12-10 | 2021-12-20 | 6 / 6; Fox has two ticker classes | [Nasdaq 2021](https://ir.nasdaq.com/node/104321) |
| 2022-12-09 | 2022-12-19 | 6 / 7 | [Nasdaq 2022](https://ir.nasdaq.com/node/105641) |
| 2023-12-08 | 2023-12-18 | 6 / 6 | [Nasdaq 2023](https://ir.nasdaq.com/node/106896) |
| 2024-12-13 | 2024-12-23 | 3 / 3 | [Nasdaq 2024](https://ir.nasdaq.com/news-releases/news-release-details/annual-changes-nasdaq-100-indexr-1) |
| 2025-12-12 | 2025-12-22 | 6 / 6 | [Nasdaq 2025](https://ir.nasdaq.com/node/109786) |

Annual announcements do not establish year-round membership: Nasdaq itself describes additions and deletions outside the December reconstitution in [Looking at Nasdaq-100 Index Adds and Deletes](https://www.nasdaq.com/articles/looking-nasdaq-100-index-adds-and-deletes). Company counts must not be confused with constituent share-class counts.

## Access and licensing

No free, legitimate **complete** PIT membership series for all three requested indices was verified in this bounded research. This is an access finding, not a claim that no such dataset exists anywhere.

- S&P's [official SPICE product page](https://www.spglobal.com/spdji/en//landing/topic/spice/) describes a subscription-based platform with S&P 500 constituents from 1964 and additions/removals from 1989, and constituent/corporate-action exports. This is a verified documented route to historical data; no subscription or entitlement is available or assumed here.
- Nasdaq's [official Daily Pro Forma access guide](https://indexes.nasdaq.com/docs/GIW%20Website%20-%20Daily%20Pro%20Forma%20Guide.pdf) requires GIW credentials and says the data are available only for licensed indices. Credentialed data access was not attempted.
- Public readability of a press release is not an open-data redistribution license. No open data license, bulk reuse right or production redistribution right was verified for the downloaded raw responses. Local source-evidence preservation does not confer those rights. Do not publish the raw corpus or represent the index history as open licensed data without checking applicable terms.
- Current constituent pages, ETF portfolio holdings and Wikipedia histories are not interchangeable with a complete official historical index universe. ETF holdings are portfolio proxies and can include cash, tracking differences, derivatives and differently timed changes. No such proxy was relabeled as official PIT evidence.

## Integration recommendation and remaining blockers

1. Import this bundle only into an append-only **source-evidence / membership-change staging** layer. Verify SHA-256 before ingestion; preserve original URL, publication-date precision, retrieval instant, effective-date timing and coverage classification. Do not promote these events into daily `OFFICIAL_PIT` snapshots merely because the publisher is official. Source IDs are local bundle IDs; use URL/content hashes or a bundle namespace for cross-run identity.
2. The existing `StockPointInTimeImportBatchRunner` accepts dates, member flags and a source string, but discards announcement/source-document lineage. Feeding it sparse official events does not establish index-wide completeness. Keep those data separate until a provenance-aware continuity reconstruction is supported.
3. A historical universe requires an official dated complete baseline, a demonstrably complete chain of all subsequent changes, stable security/share-class identifiers, and effective market-session timing. A current baseline walked backward through an incomplete change list retains survivorship bias. Historical sector classifications and symbol changes also need their own PIT evidence.
4. With publication day only, avoid treating the fact as known at that day's opening or intraday. Use a conservative next-session knowledge bound for decision features unless exact release time is separately supported. Historical effective membership and announcement knowledge are distinct dimensions.
5. Keep model-promotion eligibility blocked for any evaluation requiring a complete official PIT universe. These 147 events improve source evidence and enable event-specific reconciliation tests; **they do not unblock full-universe historical validation**. Event correctness, price/corporate-action integrity, constituent completeness and walk-forward model performance are separate gates.

No database writes, Java/schema edits, Maven runs, commercial purchases, authentication or external publication were performed for this source-evidence subtask.
