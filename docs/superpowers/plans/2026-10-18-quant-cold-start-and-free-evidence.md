# Quant first-request latency and free evidence follow-up

**Goal:** Repair the reproduced 70.96-second cold `/quant` request and acquire additional free,
traceable historical evidence without claiming unavailable PIT certification.

**Architecture:** Keep DB-only page rendering and existing aggregates. The main worker diagnoses
and repairs the SQL access path; an independent acquisition worker stages official source data.
Use the current authorized dirty main checkout; no Git integration, replay or frozen-data rewrite.

## Scope and execution

- [x] Reproduce after an actual application restart; capture request timing, thread stack and SQL plan.
  Evidence: `tmp/quant-cold-baseline-*20261018*`; 70.961 seconds, `findQuantModelHealth` full scans.
- [x] Add real MariaDB temporary-table regression tests for model-scoped lookup indexes using the
  production schema and mapper SQL. Observe RED, then add only the necessary indexes.
- [x] Save an idempotent additive index migration plus fresh-install schema definitions. Apply it
  explicitly; do not alter values, labels, provenance, model math or background-refresh behavior.
- [x] Compare health aggregate rows before/after exactly (excluding no dynamic time fields unless
  justified), inspect real plans, run focused tests then the full relevant suite once when stable.
- [x] Restart app and measure first requests for representative indexes without warm-up requests.
  Confirm `/quant`, CPB and backtest remain available. Do not hide first-hit work in prewarming.
- [x] Acquire bounded primary free evidence, keeping URL/retrieval/hash, actual publication/as-of
  dates and source limits. Keep candidates separate from frozen research and production tables.
- [x] Record final counts, tests, timing and remaining evidence limits in a concise follow-up report.

## Execution ledger

Initial narrow model indexes changed the genuine first request from 70.961s to 17.986s; all health
aggregates were byte-identical. Full tests at that intermediate tree: 192/192. The request still
spent 4.124s in market, 7.312s in health layers and 4.392s in PIT source quality. Per-layer profiling
identified wide historical row fetches for market/membership/shares/factor/covariance aggregates.

Ruling: extend the same SQL-access-path repair to seven covering indexes, rather than change
displayed metrics, prewarm on startup or permit stale dashboards. This also makes the model index
support the per-stock latest-prediction lookup. It costs index storage and additional write work;
it preserves every table value and query formula. Only the two intermediate indexes created by
this task are superseded; no original index is dropped. Fresh-install schema and explicit existing
DB migration remain aligned. Tests shadow all seven tables with connection-local temporary tables
before executing any migration. The migration test now exercises an unindexed fixture then repeat.

TDD: original two plan regressions RED then four tests GREEN; covering-access regressions RED
then five tests GREEN. Independent data worker acquired 276 free originals / 59,368 numeric as-of
rows; main independently reran its five tests and all-file/raw-to-derived verifier successfully.
No frozen namespace import or model rerun is part of this follow-up.

Final SQL delta: source classification now runs once per source group, preserving the original
row-weighted score and overlapping flags. Original-case fixture values passed before optimization;
plan regression failed as expected, then seven tests passed. Actual full PIT output byte-identical,
query3.325s→0.703s. Reviewer checked the weighted formula and empty behavior; no blocker.

Final acceptance: fresh JVM/no prewarm first SP5005.734s (baseline70.961s), Nasdaq2.487s,
Dow2.061s, all200. Expired-cache SP5004.895s then cached0.083s. FinalJava195/195 andPython28/28
passed; no further product edits after this verification. Health/PIT before/after bytes identical,
actual migration repeat succeeded. No optional broader audit or profitability claim added.
Details: `docs/quant-followup-first-request-fix-2026-10-18.md`. Local main only; no commit/push.

## Review focus

Schema change is additive and must be idempotent, support fresh and existing databases, and never
overwrite table data. Tests must use connection-local temporary tables. Missing models must still
produce zero-row health aggregates; every existing metric and provenance warning must be preserved.
Cold timing must measure the first `/quant` request after restart, not a cached second request.
Historical as-of snapshots must not be relabeled as complete publication-time or LIVE evidence.
