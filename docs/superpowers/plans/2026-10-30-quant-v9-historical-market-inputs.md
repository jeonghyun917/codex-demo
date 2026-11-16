# V9 historical market input recovery plan

Goal: close the59missing symbol/date market-input keys in11older complete-price research dates,
using actual pre-decision public common-share facts and unit-compatible historical prices.
Spec: user asks free inputs and longer backtest without questions; prior older results retained.
Architecture: separate local share/provenance staging, cached V8 predictions and unchanged V9
service in a write-capturing mapper. Do not rerun V2–V8 or overwrite prior experiment artifacts.

- [x] Extract ANSS/GFS/ILMN unambiguous actual-share XBRL, excluding EPS weighted averages,
      wrong units, future/same-day filings, ambiguous conflicts and wrong issuer identities.
- [x] Verify TEAM/MDB share-class semantics and AZN ordinary-equivalent price/count units from
      public primary evidence. Preserve manually curated text separately from raw XBRL.
- [x] TDD share selection/conflicts/precision, strict filing cutoff and basis-safe capitalization.
      Select latest actual measurement known strictly before decision, not today's shares.
- [x] Require all59new keys and101market/prediction rows each date. Execute V9 only using saved
      V8 forecasts and actual existing market rows. Capture writes locally; no DB/model imports.
- [x] Verify1111forecasts, no future-label feedback, independent metrics/accounting/nonoverlap,
      actual input hashes, one focused review and fresh pre/post DB invariants.
- [x] Save results/checkpoint with explicit research/PIT/missing-factor/price-rights limitations.

Review focus: stock-issued versus outstanding; A+B versus EPS denominators; old cover dates
versus fiscal report dates; GFS rounded IFRS versus exact DEI; AZN ordinary/ADS equivalence.
Rulings: native implementation in existing authorized dirtymain, no Git or production writes,
fees/accounts/access bypass/new external publishing. Missing evidence keeps entire date excluded.
Keep the19-date V2–V8 experiment frozen; only the previously blocked V9 stage is new work.

## Continuation ledger — 2026-10-31

Ruling: constrain extraction to2023Jan1<=filed<2025May30 because ten ANSS zero-count facts
filed2014 are unrelated to this experiment. Do not ignore malformed counts in scope; new test
observed failing before the bound was implemented and passing afterward. Cost if an older
fact were needed: fail coverage instead of silently accepting/fabricating shares.

Tasks1–4 complete:59keys/11dates,1111V9forecasts;1052knownforecasts match; noV2–V8replay.
Task5 verification:65PythonPASS, independentPython/Java152groups/15352forecasts,
14nonoverlapperiods permodel,1111futurelabelinvariant. FreshDBbefore/after identical.
Task5 scoped fresh review complete: noCritical/Important/Minor findings. Review independently
checked8focusedtests, input/reporthashes,1111normal/counterfactualforecasts,1052priorcomparisons,
selectedsharesemantics andaccounting. Main independently verified liveDBinvariants/startupwrite
absence; reviewer deliberately had noDBnetwork permission.
Final ruling: external filing authenticity/provideradjustment/exactdailycap/native1052quality and
officialPIT/investmentusefulness stay unproven as documented, not review-certified. Reacquisition,
modeltuning and repository-wideaudit are out of this bounded recovery. Cost if evidence wrong:
research reconstruction changes; promotion remains prohibited rather than claiming readiness.
Task6 complete: results/checkpoint saved; no Git or DB import. Keep artifacts, not ephemeral
workspace deletion, because the user is resuming across PC shutdowns and no commit was requested.
