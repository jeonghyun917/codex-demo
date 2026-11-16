# V9 Training-Only Nested Validation Implementation Plan

**Goal:** Diagnose V9 training errors and test finite indicator/weight candidates chronologically without reusing the previously scored60outcomes.
**Architecture:** Sanitize only historical training labels and genuine V9 OOF from the frozen forecast-only archive. Fit quarter-frozen correction/alternative weights over the online causal V9 base. Nested prior-quarter selection and strictly mature labels; no live model changes.
**Tech Stack:** Python, NumPy, immutable local JSON evidence; no DB/network/dependencies/deployment.
**Spec:** User request2026-11-01 and existing source contract in docs/quant-long-history-oof-results-2026-10-31.md; thisplan fixes the experimental choices.

## Global Constraints

- Never read previousresult.json/evaluation.json, targetpredictions or baselinepredictions. Allowed forecast-only fields: trainingRows26401/oofPredictions25473 only.
- Source root tmp/quant-long-verified-oof-study-20261031-v1; forecast-onlySHAf3fa85da248107034a2c52a0a22208c20c28788c55780a31613d3877375e0c0e. Feature dates≤2020-08-28; permitted existing training label exits≤2020-09-29. Reject old test decision dates2020-09-30/10-30.
- Fit labels require signalDate<fitCutoff AND exitTradeDate<fitCutoff. Inner selection additionally requires exitTradeDate<outerCutoff. No same-day maturity.
- Main remains untouched except new isolated research files/checkpoint. No Git operations or production/DB modifications. Preserve all older sealed studies.
- Descriptive baseline diagnostics were inspected before thisprotocol, but no candidate performance. This is internal model-development validation, NOT a new untouched holdout, prospective or complete historical-membership study.

## Fixed Experiment

Outer quarters2018Q2..2020Q3(10folds; finalquarterpartialthroughAug28). First quarter delayed because2017Q2 has no matured V9 OOF residuals at its start. Inner last3calendarquarters; fit min300rows AND20distinct dates, otherwise deterministicV9fallback. All candidates use identical OOF-joined validation population.

Features, available at decision close:
- V9 absolute/excess point forecasts.
- All12reported scores (including integrated/dataquality), log marketcap; training-constant columns receive zero coefficients, never infer missingness from50.
- Stock past5/20/60return,20volatility,60drawdown,20/60relative benchmark returns.
- Benchmark past5/20/60return,20volatility,60drawdown.
- Latest annual/quarter actual missing-record flags, missing-EPSflags and log period ages from real prior-filed audit records. No realized benchmark/entry/exit prices asfeatures.

Finite library: V9, zero, date-balanced past median; direct-full ridge λ10/100; residual-full ridge λ10/100; residualridgeλ100 without explicitmarket, without explicitstocktechnical, without explicitavailability. No further candidates after outcomes. Loss=sum_date(mean_stock(squared_error))+λ||β||²; interceptunpenalized, training-only date-balanced normalization, standardizedfeatures clipped±5. Heads absolute/excess fitted separately; these are point-only research candidates.

Selector: inner date-balanced MAE for both heads must improve on V9 and jointrelativeMAE<1 in≥2/3innerquarters. Lowest mean relativeMAE, fixedlibraryorder tie-break. Includes eligible simplebaselines; otherwise V9. Outer primary result is this nested selection procedure, not the pooled best candidate chosen afterwards.

Report per-quarter/year and overall date-balanced MAE/RMSE/bias; row MAE ancillary. Date-block paired bootstrap21decisiondates,1000draws seed20261101 for selectedprocedurevsV9; not IID/independentstocks. No operational promotion even if internal improvement found; separate unused/future validation required.

## Review Focus

1. Equality-boundary maturity and prior-quarter labels extending into outerquarter.
2. Neutral50 versus true observed financial missingness, and missingness proxying collectiontime.
3. Constantfeatures/future scaling/extremefutureprice perturbations.
4. Identical full admitted cohorts/dateweights and smallfirstinnertraining.
5. Outcome-dependent candidate discovery, posthoc best-model selection and reused60testlabels.

## Tasks

- [x] TDD causal helpers and build scripts/quant_v9_nested_training.py +scripts/test_quant_v9_nested_training.py.10focusedtests REDmissingimplementation→GREEN; includes actualmissingness/evidence distinction.
- [x] Prepare25473rows/870dates/33features sanitizedpanel; protocol494412809d98e97e9aa04768020274a450311599fb843ad91e1e1d7a8ad8676c sealedbeforecandidatefitting. No old study edits.
- [x] Execute10outerfolds17904rows/609dates; predictions/weights/cutoffs/21datebootstrap saved; actualfuturelabelperturbation invariant.
- [x] Independentallmetrics/10choices/240innerscores/cutoffs +6fit/7featuresamples reviewed. Full173PythonPASS. Report+chart/checkpoint saved. No provenimprovement/promotion.

## Execution Notes

User explicitly requested immediate continuous execution without questions; no design approval gate. Native implementation with independent method/input audits already completed and one final scoped review. No worktree/commit ceremony for file-only research; preserve dirty main. Preserve evidence rather than delete research workspace.

Outcome: nestedselectedAbsoluteMAE5.753041300vsV95.753122986;Excess4.405546654vs4.413736466. Bothpairedblockintervalsincludezero;absoluteRMSEslightlyworse. No newcandidates/retuningaftertheseoutcomes. Minorconstant-columnscale≈1e-12hasnegligiblecoefficients,independentfitsagree; deferredwithoutchangingsealedrun. Full60previousheldoutoutcomes/targetarrays unused.
