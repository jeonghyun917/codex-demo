# Daily 20D forward collection implementation plan

Goal: automatically collect completed-session free inputs, save real forecasts once per exchange anchor, freeze before entry close, and evaluate matured outcomes without changing the model to fit inspected dates.

Architecture: reuse the existing verified Java price/forward operators and offline protocol evaluator; add one standard-library Python controller for calendar eligibility, exclusive OS lock, duplicate prevention, backups, immutable logs and evaluation exports. Update the existing Market Data Hourly Guard automation to daily15:10 Asia/Seoul, including bounded source-backed historical recovery. This local task requires the PC/Codex and project DB to be available; no unattended cloud hosting is implied.

Constraints: main existing dirty checkout retained; no Git operations/schema migration, no paid sources/account creation, no interactive-page external calls, no model promotion. Never reconstruct missed forecasts as prospective. Existing captures/protocols remain immutable. Stop403/429; a failed attempt is not automatically spam-retried. Scoped backups contain no DDL. Superseded attempts remain evidence.

## Task1: controller (main)

- [x] Test completed-session selection (NY previous-day guard/DST/weekend/holiday), stale/out-of-range calendar rejection, duplicate anchor preservation, lock exclusion/release, no overwrite and provider-block preflight.
- [x] Implement scripts/quant_daily_forward.py and tests/python/test_quant_daily_forward.py with CLI default read-only plan and explicit --run. Reuse scripts/QuantStartForwardCollection.java with target/classes first and isolated compilation. Frozen held symbols remain collection targets even if removed from current universe.
- [x] Retain per-run SQL/date-backed input export and pending/mature evaluations for every existing frozen cohort; never exclude a name based on future result.
- [x] Current actual run should reuse the already frozen October30 cohort, skip redundant Yahoo and forecast generation, and generate fresh pending evaluation evidence.

## Task2: sustainable inputs (independent agents)

- [x] Fix SEC instant-date source fidelity with RED/GREEN tests; final ruling avoids changing persisted keys and derives instant semantics at selection only with original raw proof. No existing-data repair is implied.
- [x] Build a verified incremental foreign-primary-evidence recovery queue from actual archives/source filing/currency/period facts; distinguish already acquired bytes from new usable evidence and unsupported model mappings.

## Task3: activate and verify (main)

- [x] Update existing automation via supported app tool, preserve its existing provider rate/error constraints, target and notification preference, daily15:10 Asia/Seoul. Include current controller invocation and bounded historical input continuation; save progress and stop only when a task cannot safely proceed.
- [x] Run focused/full relevant Python once stable and Maven once for any production Java changes. One independent scoped review and one directly relevant fix pass.
- [x] Inspect actual automation record/status/time, run controller on current state, update results/checkpoint with remaining gaps and local-machine requirement.

Review focus: two overlapping jobs; provider403/429; already captured but not frozen interrupted run; missed entry cutoff after PC shutdown; calendar needing extension; null financial starts producing duplicate facts; held security leaving current roster. Tests must exercise these behaviors, not source-text matching.

Ruling: user explicitly opted out of further design approvals. Reuse established data/model/protocol structure rather than a new architecture. No commits requested; no workflow-driven staging or broad unrelated audit.

Ruling: parser NULL-start correction would undermine existing SQL nullable-key idempotence. Preserve stored key, recover instant only on source-proof selection; no schema migration/DBrewrite. Cost: existingstandardrowsrequirefutureexplicitrefresh, notautomaticallyrepairednow.

Review fixed3Important inonepass; tests31+91+100PythonPASS, Maven257/27skippedPASS. Actualsmoke has629pending/no duplicatecollection. Fullresults in docs/quant-daily-forward-automation-results-2026-10-31.md. Do not mistake activated future schedule for future runtime success or lowerMAE.
