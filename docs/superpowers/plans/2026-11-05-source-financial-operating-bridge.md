# Source-aware financial operating bridge implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:executing-plans inline. The independent automation check is read-only; one final code review, not per-step review loops.

**Goal:** safely connect the eight proved AMGN/GS documents to actual model-input readers without changing existing statements, historical forecasts, targets or weights; establish whether new forward collection actually ran.

**Architecture:** append source-aware documents to a new versioned table, never the SEC-only standard table. An explicitly enabled resolver selects separate source rows or exact-period null-only supplements; publication dates and actual admission timestamps remain separate. Live readers require current acquisition availability; reconstructed historical readers are explicitly not historical acquisition/PIT proof. Existing DB-backed request architecture remains unchanged.

**Tech stack:** Java21/Spring/MyBatis/MariaDB; Python existing source validator; H2 fixture/JUnit and Python unit tests.

**Spec:** latest user request plus the preserved source bridge contract/pack in `tmp/quant-20d-execution-20261103-v1/source-bridge/` and original approved worklist task7. User opted out of questions/approval gates.

## Global constraints

- Preserve main dirty tracked/untracked files, all old forecasts/protocols/source bytes and original 203/189/14 recovery accounting.
- No synthetic SEC accession, filing date, publication clock or legacy GS period overwrite. No merging Apr1 values into a Mar31 statement.
- Feature enablement is explicit for future batch; old cohort admission/PIT claims unchanged. No duplicate same-session forecast generation or old60 outcome tuning.
- New additive DDL is separate from transactional row import; a rollback leaves an empty compatible table, never drops unrelated DB objects. Publish source rows once after a real rollback probe and postimage check. No schema init on page GET.
- Operational linkage/data correctness is not proof of smaller future MAE. No model/weight promotion or remote transport.

## Review focus

1. Same-day date-only release must not be used; live admission after decision time must not leak into an earlier decision.
2. Same-end different-start GS record must remain a whole separate source statement, never a mixed-period null fill.
3. An old source quarter cannot replace a newer operating quarter; exact-period fills cannot replace non-null values or mutate original objects.
4. Duplicate/tampered source identities, incorrect units/issuer or missing original evidence must fail before writes; duplicate admission cannot replace first observation time.
5. Legacy standard/frozen forecast bytes and DB rows must be unchanged across import; automatic success must be verified from exact run receipts, not ACTIVE schedule alone.

### Task 1: Source-aware reader and real model wiring

**Files:** new StockSourceFinancialDocument/Mapper/Service and mapper XML/schema fragment; modify only quarter reads in StockCacheService and HistoricalBacktestSeedService, plus future batch enablement/evidence.

**Interfaces:** `resolveQuarter(symbol, decisionDate, beforeEndDate, legacy, reconstructed)` returns a detached SecFinancialStandard or original legacy object; source reader uses fixed VERSION and explicit feature flag. Full selected source/version metadata lives in derived rawJson and future input evidence. No annual substitution.

- [ ] Write failing JUnit fixtures: source unavailable on publication date; live admission cutoff; AMGN missing-quarter source; exact-period revenue null fill with original values preserved; GS whole Apr1 source/no borrowed balance sheet; later legacy quarter/other issuer/disabled mode unchanged.
- [ ] Implement append-only source reader and deterministic guarded selector. Wire latest/previous quarter reads (historical previous quarter must use the newly selected current end date). Preserve unselected original object and nulls.
- [ ] Run focused tests RED→GREEN, actual mapper fixture tests and compile helper.

### Task 2: Validated import, preservation and actual read-path proof

**Files:** new `scripts/quant_source_operating.py`, `scripts/QuantImportSourceFinancial.java`, focused Python tests, versioned runtime evidence folder.

- [ ] Write failing Python tests for source hash/identity/association mutation and observed-time separation. Reuse existing source validator; no downloads or original writes.
- [ ] Create immutable import artifact with actual now acquisition/admission observations, eight distinct identities/proof payloads, original pack SHA and scope. Guard artifacts by exact original-source rebuild before Java DB connection.
- [ ] Back up current code + original forecasts + AMGN/GS standard preimages/hash; install only additive table; transaction rollback probe, then create-only row admission and postimage checks. Existing standard/raw/forecast tables untouched.
- [ ] Run actual read-only Java mapper/service probes against chosen publication boundaries and latest dates; report which inputs change, not a manufactured backtest/MAE. Future batch includes source policy version, code hash and actual inputs; do not regenerate today's already frozen forecasts.

### Task 3: Automation proof and final verification

- [ ] Independent read-only check of Nov4/Nov5 run UUID/admissions/input/freeze/timing/pending outcomes and current canonical latest session. ACTIVE schedule alone is insufficient and exact15:10 punctuality is not claimed.
- [ ] Full relevant Java suite once after stable implementation, focused Python and bridge regression suites; one fresh reviewer/one important-fix pass. Preserve DB integration skips explicitly.
- [ ] Store final source/forecast/DB pre/post receipt, rollback recipe, current acquisition-vs-publication limitations and resume checkpoint. Leave source code/old dirty work uncommitted; no push/deploy or unproven weight change.

## Execution rulings

- Reuse the actual dirty checkout with scoped before-images; a clean linked worktree would omit the currently operating uncommitted Quant code. No other agent writes product files. H2 fixtures isolate DB tests. Cost if wrong: unmanaged overlap; mitigate with explicit file ownership/preimages and final diffs.
- Use a dedicated source table rather than fake SEC keys or stuffing input facts into a view-snapshot payload. Cost: one additive table and explicit install requirement, bounded by rollback probe and disabled-by-default feature gate.
- Commit/workspace-cleanup helper ceremony is not used because the user requires preserving existing mixed work and did not request commits. Evidence and this plan remain durable, not deleted.

## Execution outcome

Tasks1–3 implemented and scoped verification completed; see `docs/quant-source-financial-operating-results-2026-11-05.md`. Actual8-source admission, rollback/pre/postimage and reader identity boundaries verified. Future same-session collection was not duplicated. Actual old-date full feature generation is NOT ready: operating AMGN prices before2019-01-30 are0/minimum80; fixture wiring passes but no production historical replay/MAE claim is made.

One independent review found2Important issues; both fixed RED→GREEN (actual VARCHAR20insert, exact reviewed-pack admission gate), then fullJava307/280executedPASS27skip andPython80PASS plusstandaloneoperator2PASS. No unrelated legacy audit or old60outcome read. The review's declined architecture expansion/old-outcome audit/repeatedsuite requests are outside this bounded user task and remain excluded; cost is explicitly limited historical/performance assurance, not hidden data mutation. No additional polish rounds or commits.
