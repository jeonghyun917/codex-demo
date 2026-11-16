# Actual forward collection — October 31, 2026

Purpose: measure genuine future 20-trading-session error of the existing displayed model, then accept changes only when error reduction survives time-separated validation. This is not a claim that the current model is accurate.

## Fresh prices and market data

Public Yahoo chart batch 49 completed: 518 symbols requested, 517 successful, WBD failed. Its 128,843 saved canonical rows include overlapping upserts, not that many new trading days. A successful response is not proof of freshness. Expected completed exchange session: October 30.

Pre-generation price eligibility: DOW30 30/30, SP500 498/503, NASDAQ100 101/103. Missing current closes were known before forecasts: EA and WBD in both SP500/NASDAQ100; AVB, EQR and PSKY additionally in SP500. No stale price was forward-filled or relabelled current.

All three stored benchmark series were advanced from September 21 through October 30 using an additive LIVE path, not rebuilt from 1000. Existing September 21 levels remain exactly DOW30 1478.29670750, NASDAQ100 2104.80335486, SP500 1818.16108814, all `MARKET_SNAPSHOT_WEIGHTED`. Each series now has 1,347 dates (29 additions). These are stored weighted reconstructions, not official index total-return histories or proof of historical membership.

## Historical inputs actually added

Original archived free SEC evidence supplied 718 raw facts and 101 coherent statements for previously empty supported-US issuers: ANSS 166/26, ILMN 149/28, MDB 148/24, TEAM 255/23. Real partial-failure and complete-insertion rollback probes passed before the final INSERT-only transaction committed. Original 65,115 standard rows were unchanged. Actual DB totals are now 706,039 raw facts and 65,216 standards.

These inputs repair coverage of 138 accession references over 46 already explored issuer/date panels, July 2024–March 2026. They do not create new independent validation dates or demonstrate lower prediction error. Receipt timestamps are actual acquisition/preparation times, never backdated to SEC filing dates. Foreign IFRS/6-K references and missing historical analyst/news inputs remain unproven; they are not filled with guessed values.

## Actual forecast evidence

Definitive operational output: `tmp/quant-live-start-20261031/actual-forward-run-v3/`. Actual captures: `data/quant-forward/`. Index-bound prospective protocols/cohorts: `data/quant-forward-validation-20261031-v2/`. The independent artifact check is `scripts/verify_quant_live_start.py`; its output and pending evaluations are retained alongside the operational logs.

The completed definitive run saved 513 unique current signals and 629 index-specific V9 20D observations: DOW30 30, SP500 498, NASDAQ100 101. Index overlap is intentional; 629 is not a unique-company count. Every forecast has a finite expected excess value. All three dashboard snapshots were published successfully, with no failed symbols/model stages. Captures occurred 07:32:40–07:36:10 UTC, after protocol initialization and before entry close. All 629 observations are frozen and `PENDING_OUTCOME` (zero mature outcomes, MAE null).

`independent-db-archive-verification.log` confirms every archived absolute return, excess return, upside probability and source matches the actual DB, every used market input is October 30, archive hashes match frozen manifests, and frozen names equal predeclared eligible names. The report's JDBC DATE values serialize at Korea midnight (October 29 15:00 UTC means October 30 local DATE); independent SQL confirms the intended October 30 benchmark date. Benchmark additions happened in the first attempt, so the definitive idempotent run correctly reports zero further benchmark rows.

The one-shot operator uses current DB inputs and unchanged V2–V9, without recomputing historical outcomes. Only archived fresh V9 LIVE 20D rows are prospective observations. Full raw upstream inputs are not archived, so these remain observed outputs, not complete input point-in-time proof. Latest prices/market snapshots do not imply every news, analyst, financial and macro input is current.

First observation anchor: October 30; planned entry: November 2 close; planned 20-session exit: December 1 close. Outcomes cannot be scored before that close. Frozen names remain in the denominator even if later prices are missing. The initialized protocols freeze a zero-return baseline; their optional historical baseline is null, so historical-baseline errors remain null rather than invented. Do not silently drop inconvenient outcomes, reuse inspected dates as a holdout, retrofit a baseline after outcomes, or promote weights from this one cohort.

## Execution incidents and safeguards

The first operator stopped before saving signals because an instance ranking method was invoked without its receiver. This was corrected. The second attempt exposed stale application classes in a generic `tmp` classpath; it was stopped, produced no forward archive, and is not counted as an observation. The definitive third run uses `target/classes` first and an isolated operator class directory. A new preflight rejects mixed application class origins; the stale path fails and the correct path passes without opening the DB.

Backups: price scope `candles-before.sql` plus 16 data-only runtime dumps in `runtime-before/`. The price dump is filtered and contains DDL: never source it as a whole-table restore. Runtime dumps contain data only. Preserve the append plan manifest, source hashes, transaction evidence and raw archive files. No schema change, deployment, Git transport or recurring scheduler was performed.

## Verification and remaining work

Stable production source: Maven 243 tests, 216 executed successfully, 27 skipped, zero failures/errors; log `tmp/quant-live-start-20261031/maven-final.log`. Focused Python: 21 forward-protocol cases and 7 historical-source cases passed. Additional operator classpath failure/success checks passed. No full-suite rerun solely for operational output or documentation.

Next meaningful work is new completed-session observations, maturity evaluation using retained closes/benchmarks, and explicit tested foreign financial mappings. No improved accuracy or model promotion is claimed. The one-shot run starts real collection but does not install an unattended daily collection schedule.
