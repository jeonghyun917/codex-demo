# Candidate Forward Evaluation Continuation Plan

**Goal:** Make the already frozen30-name candidate comparison executable by the existing daily controller after the original20D exit, without re-fitting candidates, replacing baselines, dropping missing names or scoring before maturity. Independently investigate remaining14 source gaps without modifying sealed experiments.

**Scope:** Existing dirty main HEAD b134d9fb0fbe57d30e08829518982b3ef2a7c83c,508initialdirtyentries; preserve all prior protocols/code they hash and unrelated edits. No commit/push/serverrestart, operating model changes, paid data or old60 use. The existing daily collection schedule is not recreated or broadened; this adds a research evaluation stage to its existing path. Do not repeat unchanged full model replay.

**Architecture:** New offline `scripts/quant_v9_blend_evaluate.py` validates the candidate protocol/cohort hash and referenced original frozen cohort, reuses the same index-bound calendar/benchmark target, computes each candidate on exactly the same complete rows, and publishes null summary metrics unless all30 original names have complete21-session paths after original exit close. `quant_daily_forward.evaluate_existing` calls the offline evaluator with the same just-exported held-closes CSV, storing new append-only evaluation receipts beside its existing reports. The candidate freeze module and original evaluator remain immutable.

## Tasks and checks

- [x] TDD offline evaluator9cases: paired literalMAE/RMSE/bias/zero, pendingbarrier, full21closes, no partialscores, duplicate/tamper/index/denominator/outputimmutability. Self-contained exact-source bundle preserves14payloads for later offline evaluation.
- [x] TDD daily connection3cases +existing10dailytests: separatecandidateEvaluations, samecloseexport, existingnormal/calendarblocked/failurepaths, rejectedcandidateexplicitstatus/CLIexit1.
- [x] Real no-collection smoke completed13:52UTC:alreadyfrozen3indices,missing[],629baselinepending/30candidatepending,allerrorsnull,APIlog/predictionrowcountsame,no newfreeze ormodelrun.
- [x] GS original periodreconciled +AMGN7issuerquarter originals/13associations saved separately. No originalkeyoverwrite/recoverycountincrement/DB/model change. Original189/203and14unresolved-retained.
- [x] Stable fullsuite scripts280 +tests/python43PASS; scopedindependentreviewnoC/I; documentation/checkpointwithgenuinependingstatussaved.

## Completion rulings / evidence

- Read-only evaluation readiness does not establish lower20Derror. Dec1originalexit and complete21prices remain required; unusedhistoricalevaluation is stillunfulfilled from the preceding task.
- Bundling snapshots existing source bytes, not new predictions, and avoids future dependency on temporary compiled classes; originalcandidateprotocol/cohort remain unchanged.
- The existing daily invocation automatically reaches the added evaluation stage; no duplicate automation/scheduler installed and no runtime model/DB writes performed in this smoke.
- Source-only observations do not solve sourceidentity automatically: GSneedsoriginal-periodcanonicalization;AMGNreleasesareindependentpubidentities,noteligible10Koverrides. Preserve189/203ratherthaninflatecounts.
- ReviewerMinor literalRMSE/bias tests were alreadyadded beforefinalsuite. Extraenumerationofeverypendingzero/paired-nullunitfieldisnonblockinganddeferred; implementationinspected andrealpendingvalueschecked. No repeatreview/fullmodelrun.
- Receipts/logs `tmp/quant-candidate-evaluation-continuation-20261101-v1/`; actualdaily `data/quant-daily/20261101T135200Z-d8cfc3cc/result.json`; report `docs/quant-candidate-evaluation-results-2026-11-01.md`.

## Acceptance and review focus

- Original entry2026Nov2close and exitDec1close are immutable; use embedded exchange close timestamps, not localcalendar midnight.
- Candidate predictions and original baseline all30names are fixed. Missing labels or one missing intermediateclose leave full-cohort metricsnull, while observed completeness counts remain inspectable.
- The live benchmark is the original explicit storedDOW30proxy, not newly acquiredDJIA or anotherindex.
- This module evaluates frozen outputs only: no fitting/selection, no historical reconstruction and no promotion.
- Recheck source/code hashchain but don't change sealed originalcandidate/forward scripts. Existing-controller changes are minimal and tested.
