# Historical input supplement — preparation and subsequent operator apply

Operator update: the coordinating task subsequently ran both real rollback probes and committed the definitive V2 plan (718 raw facts, 101 standards). Actual totals 706,039/65,216 were independently checked. See `docs/quant-live-forward-start-results-2026-10-31.md` and `tmp/quant-live-start-20261031/history-append-apply.log`. The preparation-only descriptions below record what this component task itself did, not the final database state. Do not reapply the now-nonempty issuer scopes.

No database writes, new public requests, model changes, retraining or performance claims were made by this preparation task. The evidence is prior-filed historical reconstruction, not proof of historical receipt or official point-in-time availability.

## Verified scope and usable candidate

The 90 previously recovered issuer/date panels refer to 233 accession references. Actual read-only database inspection found only 22 references with any raw fact present; this does not mean 22 usable financial statements. The earlier report's 158 missing annual/quarter **lookups** is a different denominator from these accession references.

Existing original public SEC companyfacts/submissions archives were sufficient. Their acquisition manifests, status 200, source CIKs and SHA256 hashes were checked. No additional download was needed. Broad, source-faithful staging contains 18,021 missing raw rows, with zero same-key value conflicts after normalizing CIK padding; this is archival coverage, not the proposed database import size.

The bounded append candidate contains only four previously empty US issuers, supported US-GAAP concepts and real USD/USD-per-share units:

| Issuer | New raw facts | New coherent standards | Historical accession references |
| --- | ---: | ---: | ---: |
| ANSS | 166 | 26 | 33 |
| ILMN | 149 | 28 | 15 |
| MDB | 148 | 24 | 30 |
| TEAM | 255 | 23 | 60 |
| Total | 718 | 101 | 138 |

Those 138 references cover 46 existing issuer/date panels, 2024-07-31 through 2026-03-27. Every reference has a candidate statement filed strictly before its decision, with original source values, actual annual/quarter duration and explicit units. This repairs source availability for previously explored dates; it creates no new independent validation dates. Accession coverage alone does not establish that every comparison, EPS feature, price basis or model input is usable.

The unchanged current standardizer generated 493 non-null fields: revenue 64, operating income 64, net income 101, diluted EPS 56, assets 40, equity 101, operating cash flow 46 and liabilities 21. No missing field was filled with zero or a neutral score.

Important compatibility detail: the current SEC parser replaces absent instant-fact `start` with `end`, while the current standardizer requires instant facts to have no start. The candidate preserves the original SEC absence as SQL NULL (253 facts), uses explicit NULL-aware in-memory natural-key deduplication, and validates the original raw JSON. This recovers genuine balance-sheet fields without changing application classes, manufacturing dates or treating an instant as a duration. The earlier V1 candidate retained the parser normalization and is superseded.

## Definitive candidate and operator handoff

- Candidate: `tmp/quant-historical-us-append-plan-v2-20261031/manifest.json`.
- Manifest SHA256: `f81d3a85b68c3db46b03f2540b4dc249ee419eec6e86ceea88ddcb111964947c`.
- Full current corrected standard-table backup: the same directory's `current-coherent-standard-backup.json`, 65,115 rows; its hash and complete preimage are pinned in the manifest. This is not the older pre-correction SQL backup.
- Candidate files: `candidate-facts.json`, `candidate-standards.json`; exact per-row values, accession/date/unit/duration and original raw JSON remain inspectable.
- Independent evidence: `independent-verification.json`; it checks every raw fact against original archived SEC JSON, every selected financial field against raw facts, all 138 prior-filed references, unique keys and all pinned hashes.
- Broad evidence: `tmp/quant-historical-source-staged-v4-20261031/coverage.json`, `hashes.json`, per-issuer missing-fact files and accession/filed-date import queue.
- Actual DB snapshot: `tmp/quant-historical-source-snapshot-v3-20261031/`; all four proposed issuers have zero existing raw and zero existing standard rows. Financial tables only were inspected; independently changing candle/market/signal tables were not used as drift guards.

`scripts/QuantHistoricalUsAppend.java` offers separate `--prepare`, `--verify-partial-rollback`, `--verify-rollback` and `--apply` modes. Preparation is read-only. The executing operator must review the frozen source/manifest, run both real transactional rollback probes and inspect their evidence before deciding to apply. This preparation task did **not** execute either probe or apply.

The operator tool uses plain INSERT only, rejects any existing scoped issuer row/collision, checks database identity/full current standard preimage/source/candidate/code hashes, validates raw JSON and CIKs, locks scoped ranges, verifies exact inserted postimages and unchanged old standards, then commits only in explicit apply mode. Probe modes always roll back, including an injected failure after raw insertion. A retry after an uncertain or completed commit is intentionally rejected by nonempty-scope guards; inspect current state instead. No UPDATE, upsert, DELETE, TRUNCATE, schema change or existing-row replacement is provided.

Every new `fetched_at` and `mapped_at` is preparation time in UTC, never backdated to the SEC filing date. The manifest's timestamps are not historical arrival evidence. Backup recovery, if ever necessary, remains a separate explicitly reviewed operation; do not source the older pre-correction backup over the current DB.

## Remaining gap and validation limits

The five foreign issuers account for 75 required references (AZN 32, GFS 39, CCEP 1, FER 1, TRI 2). Original SEC raw facts are staged, but the current native parser omits 6-K and the standardizer does not map IFRS concept names. They are excluded from the four-US apply plan. Their future safe path needs explicit supported mapping with currency, duration, fiscal-period, quoted-share/ADR basis and regression tests; importing raw rows alone would not restore those financial features.

Analyst/news gaps remain separate: the previous factor readout found analyst training scores constant at 50 and 1,987/2,020 rows with no recorded news. Missing evidence is not proof of absent real news or no economic effect. No current-news backfill, invented historical recommendation, neutral-input fabrication or flow-weight claim was made. New 21 standard extras from the earlier DB correction are not part of this candidate.

Verification run: seven Python transformation tests and eight Java append-validation cases passed; Java utility compiled directly against existing classes. The independent file verifier passed for 718 raw facts, 101 statements, 493 selected fields and 138 prior-filed references. Full Maven/application tests were deliberately left to the coordinating task; there were no production-class edits. The initial sandbox DB connection was denied before queries; read-only preparation succeeded with the permitted local-DB connection. Superseded V1/V2/V3 partial outputs were preserved, not overwritten.
