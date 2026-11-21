# Free historical quant-data alternatives — 2026-10-18

## Acquired, not just linked

Downloaded **43 public files / 13,871,963 bytes**, without accounts, payments or accepting new licenses. GitHub files are pinned to exact commits; every saved source has its URL, retrieval instant, byte count and SHA-256 in `tmp/free-quant-data-2026-10-18/manifest.json`. Local profiling and derived-file hashes are in `profile.json` beside it. Acquisition and profiling scripts are preserved in the same directory. No database, application code or schema was modified.

The data-quality workflow materially influenced this work: a recently updated file was not treated as a trustworthy history merely because its current constituent count looked correct. The requested durable Markdown report is the primary deliverable.

| Actual downloaded dataset | Measured coverage | Useful output | Reliance status |
| --- | --- | --- | --- |
| [fja05680/sp500](https://github.com/fja05680/sp500) | 2,720 dated membership states, 1996-01-02–2026-08-18; 1,209 tickers | 1,262 normalized membership intervals | Community reconstruction; historical gaps and dated-transition errors remain |
| [chinobing/historical_sp500_constituents](https://github.com/chinobing/historical_sp500_constituents) | 3,916 dated states, 1996-01-02–2026-10-17; 1,159 tickers | Raw comparison dataset only | 18 duplicate dates and substantial effective-date discrepancies; not adopted |
| [jmccarrell/n100tickers](https://github.com/jmccarrell/n100tickers) | 20 annual data files, 2007–2026; 451 symbol change events through 2026-10-09 | 325 normalized intervals, bounded to 2007-02-01–2026-10-11 | Community reconstruction; no publication-time proof or official certification |
| [FRED SP500](https://fred.stlouisfed.org/series/SP500) and [DJIA](https://fred.stlouisfed.org/series/DJIA) | Each 2,514 nonmissing closes, 2016-10-17–2026-10-16 | Two complete downloaded CSV files | Copyrighted **price indices**, not total-return indices |
| [FRED NASDAQ100](https://fred.stlouisfed.org/series/NASDAQ100) | 10,278 nonmissing closes, 1986-01-02–2026-10-15 | Downloaded CSV | Copyrighted Nasdaq price index; not an open-data license |
| [Kenneth French daily factors](https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/data_library.html) | 26,317 daily rows, 1926-07-01–2026-08-31 | Derived broad-market return = `Mkt-RF + RF`, retaining percent units | Academic US-market proxy, **not SP500/NASDAQ100/DOW30** |

All derived membership files use `COMMUNITY_RECONSTRUCTION_UNVERIFIED`. They are deliberately **not** `OFFICIAL_PIT` or `LIVE_CAPTURE`. Publication-date columns remain blank where no historical publication evidence exists. An effective membership date is not an announcement timestamp, and a Git commit timestamp is not the historic availability date of every row in that file. Interval files contain positive membership assertions only, not negative daily snapshots; importing them on top of current-membership proxy rows would not remove that proxy's survivorship bias. Keep their universe and absence semantics separate.

## Membership findings

### S&P 500: useful independent candidate, not a drop-in certification

The downloaded fja05680 dataset has no duplicate dates or duplicate symbols within a row. Its member counts range from 487 to 507 over the full file, and 503 to 505 in its 91 post-2020 states. It contains dated membership **states**, not a guaranteed row for every trading day. The maximum gap between recorded states is 91 calendar days; unchanged intervals are derived by carrying the previous state only through the file's final recorded date, not through today.

The maintainer describes the original 1996–2019 history as accompanying Andreas Clenow's book, with later Wikipedia/manual updates; the README explicitly discusses incomplete early membership and selected-change limitations. Its GitHub repository has an MIT license, but this does not independently resolve underlying book-derived or index-provider data rights. Keep the license and attribution with the data and review upstream rights before redistribution or production distribution. [Original maintainer and methodology](https://github.com/fja05680/sp500).

We compared both S&P datasets against the **139 official S&P change events** independently downloaded in the earlier evidence batch. Dot/dash share-class punctuation was normalized for comparison, but ticker identity was not otherwise rewritten. This tests whether membership flips on the stated effective date—not just whether a name eventually appears.

| Candidate | Correct dated transitions | Mismatches | Outside candidate date coverage |
| --- | ---: | ---: | ---: |
| fja05680 | 128 | 7 | 4 |
| chinobing | 58 | 81 | 0 |

The fja05680 discrepancies include the temporary MasterBrand (`MBC`) addition on 2022-12-15 and removal on 2022-12-19, absent from that candidate, plus five removals whose candidate membership had already changed before the official effective day (`GPS`, `VNO`, `DXC`, `VFC`, `XRAY`). Detailed event-level results are in `profile.json`. The 139-event check is itself a bounded sample; passing it would not prove the entire history complete.

The more recent chinobing derivative shows why freshness is not enough: `APP`, `EME` and `HOOD` appear before their official 2025-09-22 effective addition, disappear again, then reappear after the effective day. On 2025-09-08 it holds those three instead of `CZR`, `ENPH`, `MKTX`; on September 22 the disagreement is reversed. These dates are consistent with update/capture artifacts rather than a sound effective-date series. Its 18 duplicated dates and full-history minimum of 442 members are additional reasons to keep it quarantined. The [original official September 2025 release](https://press.spglobal.com/2025-09-05-AppLovin,-Robinhood-Markets-and-Emcor-Group-Set-to-Join-S-P-500-Others-to-Join-S-P-100,-S-P-MidCap-400-and-S-P-SmallCap-600) establishes the effective date.

The two S&P repositories are not independent primary index sources; similar histories do not provide independent corroboration merely because they live in different repositories.

### Nasdaq-100: structurally stronger free reconstruction

The original jmccarrell repository supplies annual baseline member sets and dated union/difference changes, including contemporaneous ticker symbols. All 20 annual YAML files were downloaded at a fixed commit. Checks found **zero year-boundary continuity breaks** and **zero no-op additions/removals**. Different share classes remain distinct. Year-end counts range from 100 to 105, which must not automatically be rejected as wrong merely because the index name contains 100.

The maintainer claims supported coverage from 2007-02-01 through at least 2026-10-11; that boundary, not December 31, bounds the normalized candidate. The latest actual change in the captured data is October 9. This is a useful free source for independent membership reconciliation and an explicitly exploratory universe. It is still community-maintained and has no original announcement timestamps, security-master mapping, independently proven complete event chain, or official dated-baseline certification. [Original README and MIT license](https://github.com/jmccarrell/n100tickers).

Downloaded Python source was never executed or imported. YAML is parsed with a text-only loader: ordinary YAML 1.1 parsing would convert the real ticker `ON` into boolean `True`. Local checks preserve it as a ticker string. No automatic universe promotion follows from structural continuity alone.

Pinned commits:

- fja05680/sp500: `a2430f2af0c79ddf0748e91de11bdeb1616ab5a7` (repository commit 2026-09-07; data ends August 18).
- chinobing/historical_sp500_constituents: `d05cfeb36300f456757bfbe78f428f585138be1d` (2026-10-17).
- jmccarrell/n100tickers: `2d7be392976e339174ece643acbb00a53e973eb8` (2026-10-13).

## Benchmarks: freely accessible does not mean interchangeable

FRED supplies practical no-key download endpoints, and all three CSVs were acquired. Missing holiday observations remain missing; they must not become zero index values or falsely counted trading sessions. FRED's S&P/Dow arrangement supplies a rolling ten years of daily observations, enough for the project's 2021–2026 comparison window but not unlimited history. Its S&P series notes explicitly identify a price-only index and copyright restrictions. Nasdaq's series is also copyright-tagged. Preserve attribution and assess redistribution permission rather than calling these public-domain data. [FRED S&P series notes](https://fred.stlouisfed.org/series/SP500), [Nasdaq series notes](https://fred.stlouisfed.org/series/NASDAQ100).

French daily factors provide an additional long-history academic reference. The downloaded file identifies the August 2026 CRSP database. `derived/french_market_return_PROXY.csv` adds `Mkt-RF` and `RF` as daily percent returns; it does **not** add dividends a second time, reconstruct a target index, or assert an SP500 total-return benchmark. The source describes its CRSP-derived research-return methodology and a January 2025 FIZ-to-CIZ source-format change; the underlying data may be revised. The file is copyrighted by Eugene F. Fama and Kenneth R. French, with no open-data redistribution license verified. [Author's data library](https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/data_library.html).

## ETF holdings, SEC and delisted-price limitations

[SEC Form N-PORT datasets](https://www.sec.gov/data-research/sec-markets-data/form-n-port-data-sets) are a genuinely free official route to fund holdings, including ETFs, not an official index-membership feed. Portfolio dates, filing availability dates and amendments must remain separate; fund holdings can include cash, derivatives and tracking differences. [University-hosted N-PORT research data](https://j-kahn.com/nport/) is another original academic route at fund-quarter granularity, not daily constituent or delisted-stock price history.

The SEC catalog was readable through the web reader, but the direct raw download request returned HTTP 403. No SEC holdings archive was downloaded, and no claim of recovered ETF-level membership is made. Bulk archives were not downloaded speculatively because they are large and require first selecting the correct fund/series and availability fields. No access restriction was bypassed.

Three bounded Stooq probes for historical `ATVI`, `VMW`, `CERN` daily files returned HTTP 404. Importantly, a current-ticker `AAPL` control returned the same 404: these probes establish a general endpoint/access failure in this run, **not proof that Stooq has deleted those symbols**. No delisted prices were recovered from this route. The downloaded constituent histories identify former names but do not supply their price paths, terminal cash settlements, bankruptcy recoveries or effective-dated security identities.

No freely downloadable, rights-verified and complete delisted-price plus corporate-action dataset covering all missing former constituents was established within this bounded search. Public Form 25/issuer records can support delisting/settlement facts case by case; they do not replace missing daily OHLCV. Do not silently substitute an acquirer's prices or a reused ticker's current issuer.

## Concrete local handoff

- Raw source provenance: `tmp/free-quant-data-2026-10-18/manifest.json` (43 SHA-256 checks passed).
- Profiling, transition discrepancies and derived-file hashes: `tmp/free-quant-data-2026-10-18/profile.json`.
- S&P candidate: `derived/sp500_fja_intervals_RESEARCH_ONLY.csv` (1,262 intervals).
- Nasdaq candidate: `derived/nasdaq100_jmccarrell_intervals_RESEARCH_ONLY.csv` (325 intervals).
- Nasdaq raw event candidate: `derived/nasdaq100_community_events.csv` (451 symbol events).
- Benchmark CSVs: `raw/fred_SP500.csv`, `raw/fred_DJIA.csv`, `raw/fred_NASDAQ100.csv`.
- Academic return proxy: `derived/french_market_return_PROXY.csv` (26,317 daily returns).

All listed paths beginning `derived/` or `raw/` are relative to `tmp/free-quant-data-2026-10-18/`. These are research/staging artifacts, not authorization to weaken the existing official-PIT promotion gate. The smallest safe next integration is a separate **exploratory community-universe evaluation** with visible source-quality warnings, cross-checks against known official changes and explicit missing-price exclusions. Production model certification remains distinct from obtaining a free research universe.

No fees, credentials, GitHub writes, DB writes, Java/schema edits or Maven runs occurred in this acquisition subtask.
