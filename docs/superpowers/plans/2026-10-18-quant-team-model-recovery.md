# Existing Quant model input recovery

Goal: obtain TEAM historic public financial/price inputs and run existing model formulas for
full-population research windows, separately from frozen databases and strict promotion decisions.

Implementation: SEC companyfacts with original filing dates/accessions, archived full TEAM OHLCV,
existing historical signal builder and V2–V9 services. Supply research inputs through read-only
mapper adapters, capture model writes in memory, retain actual calculation times. No production
or frozen data writes, model tuning, synthetic forecast fill or missing-member dropping.

- [x] Acquire new SEC TEAM facts/submissions and independently search new official roster sources.
- [x] Add fail-closed as-of financial extraction (strict filed-before decision; correct annual/quarter
  durations/units; no later restatements; no inferred missing values) and tests RED→GREEN.
- [x] Reuse existing historical feature/model code against acquired inputs; establish source/data
  quality omissions explicitly. Run only latest complete-price candidate dates, not entire replay.
- [x] Require entire exported membership/predictor/price/benchmark coverage; evaluate existing
  Quant models with unchanged cost and nonoverlap evaluator, not another equal-weight baseline.
- [x] Verify chronology, input hashes, unchanged DB invariants, outcome-perturbation/no-future
  regression evidence, scoped review and save actual results/caveats/checkpoint.

Existing namespace and production data remain immutable; new reconstructed outputs are local
files labeled RESEARCH_NOT_PIT. Current main dirty checkout preserved; no commit/push or approvals.
No complete official historical roster means no promotion, regardless of arithmetic completion.

Completed: 3 decisions × 101 securities × V2–V9 = 2,424 reconstructed predictions;
2 sequential nonoverlap holding periods. Independent arithmetic and target-label perturbation
checks PASS, Python48/48, scoped review no direct Critical/Important, original DB invariants
unchanged. Official target-date rosters still0: issuer-republished later101-row list corroborates
the research sets but does not prove historical PIT membership. Missing factor policies retained.
Results: docs/quant-team-model-recovery-results-2026-10-18.md. No product/Git/server changes.
