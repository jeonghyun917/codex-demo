# Quant source acquisition and validation implementation plan

> For agentic workers: use executing-plans for implementation. Independent source research is delegated; application integration remains in this workspace.

**Goal:** Acquire missing historical membership and corporate-action evidence, retain provenance, and rerun validation without manufacturing point-in-time coverage.

**Architecture:** Preserve existing price/history and legacy results. Collect public source documents and normalized change/action events into an append-only evidence store. Sparse announcements are not complete historical universes; recently downloaded history is not contemporaneously recorded predictions. Keep model promotion closed where evidence is missing.

**Tech stack:** Java 21, Spring Boot, MariaDB, JUnit, public HTTPS sources, bounded offline acquisition scripts.

**Spec:** Latest user request: acquire historical constituents and corporate actions and finish prediction/backtest verification without further design questions.

## Constraints and review focus

- Preserve unrelated dirty files and existing canonical data. No commit/push or paid data purchase.
- No external requests in interactive page paths; acquisition/import is explicit offline batch only.
- Distinguish announcement day, effective/ex day, payment/distribution day, and retrieval timestamp.
- Detect missing/modified source files, malformed events, and duplicate/conflicting keys before import.
- Do not certify a complete universe from sparse events or double-adjust already adjusted prices.
- Do not label newly reconstructed historical predictions as live predictions.

## Tasks

- [x] Acquire bounded public official membership announcements and provider-reported actions; save raw documents, SHA-256, date semantics, coverage and failures. Complete historical coverage remains unavailable.
- [x] Add `StockHistoricalEvidenceBundle` parser/validator with tests for hash mismatch, path escape, invalid values, date basis and partial coverage. Add explicit batch importer and additive evidence schema; do not mutate the existing membership/candle tables.
- [x] Verify imported counts against normalized bundles, replay idempotently, inspect representative official/provider cross-checks. Assess adjusted-price compatibility separately from event acquisition.
- [x] Rerun corrected evaluation and relevant regression tests once stable. Report acquired coverage and exact remaining barriers; do not equate a green software test with investment performance evidence.

Commands: `mvn.cmd "-Dmaven.repo.local=.m2/repository" test`; controlled application runs use MariaDB profile, SQL auto-init disabled, and signal scheduler disabled.

No approval gate: the user already requested direct implementation. A complete licensed PIT dataset or unavailable historical model inputs may remain an external limitation, which must be reported explicitly.

## Execution record

- Ruling: continue the explicitly resumed dirty-main task without moving existing work or creating a new approval gate. No commit/push; unrelated work preserved.
- Ruling: stage partial evidence and preserve canonical history. Incorrect promotion would create survivorship/adjustment bias; the cost is that model performance remains unqualified.
- Review: two important provenance issues fixed (conflicting metadata and discarded context); malformed source links and auxiliary warnings covered by regression tests. Full manifest retained in an additive third table.
- Reviewer non-goals accepted: no profitability certification from partial evidence, no unrelated legacy/visual changes, no exhaustive hand-audit of every publisher document. Automated hashes plus bounded semantic cross-checks are source-evidence validation, not complete market-data certification.
- Verification: Java 150/150 (including real temporary-DB import tests), Python 2/2, 660 raw hashes, idempotent final counts 660 sources / 11,949 events / 3 manifests. Evaluation run 3: INSUFFICIENT_DATA, 0 eligible rows.
- Full outcome and blockers: `docs/quant-source-acquisition-results-2026-10-17.md`. The broad requested model-validation outcome is NOT complete; external data and historical feature-provenance gaps are explicit.
