# V9 EPS Growth and Acceleration Validation Implementation Plan

> Execution: inline implementation and one scoped independent final review. User explicitly waived approval questions. No per-task commits, new worktree, DB writes, server restart, or production promotion.

**Goal:** Diagnose actual V9 20D error and test a small prespecified library of EPS growth/acceleration residual corrections without using the previous60test outcomes.

**Architecture:** Reuse the recovered training-only V9 OOF panel and source-proved historical standards. Build causal EPS features and run quarterly nested walk-forward corrections in Python; preserve the baseline and old sealed studies unchanged. Deliver repository Markdown results and a reproducible static figure, matching the existing research record.

**Sources:** `tmp/quant-eps-v9-training-20261101-v2/inputs.json`, its financial audit, and `tmp/quant-eps-nested-training-20261101-v1/panel.json`. Their SHA256 values are sealed before candidate execution. Previous heldout result/evaluation files and target arrays are forbidden.

## Fixed design and limits

- Existing25473OOFrows/870dates; primary2018Q2..2020Q3partial17904rows/609dates. Lastsignal2020-08-28/lastlabel2020-09-29. This is repeatedly exposed internal development, NOT a new unseen test. Never tune to or rescore old60.
- Select only original499standard keys, filedAt/endDate strictly before decision, same issuer, same currency/unit and duration. FY330–380days; Q70–110days. Match YoY using actual end-date gap330–380 and same FY/Q designation, NOT fiscalYear metadata. Adjacent quarters require70–110day gap and consecutive quarter number. Preserve DIS issuer cutover/excluded interval.
- ActualUSD/shares10-K/Q(+amendment) only; require identical actual EPS concept across pairs (never mix basic and diluted). Align EPS with pure SPLIT events after filing and <=decision; filing on split day is ambiguous and blocked. No separation share inference. Record selected keys and normalized observations. Reject duplicate/conflicting source keys.
- Latest anchor freshness: FY end age<=550days, Q end age<=220days. Older anchors retain source evidence but all their growth comparisons are masked missing. Prior-year/adjacent historical comparators are not mistaken for current anchors. YoY start and end gaps330–380, duration difference<=14days; adjacent quarter starts exactly nextday after prior end.
- Positive-baseline EPS growth: current/prior−1 only for prior>0/current>=0, clipped[-2,+2]. For loss/zero comparisons also compute symmetric change2*(current−prior)/(abs(current)+abs(prior)), bothzero=0, plus missing and sign-transition indicators; do not call a loss-base ratio an ordinary growth percentage.
- Growth group12: annual and same-quarter YoY symmetric/positive growth, pair-missing, positive-growth-missing, loss-to-profit, profit-to-loss.
- Acceleration group4: currentquarter YoY symmetric minus previousquarter YoY symmetric (clip[-4,+4]), missingflag; adjacentquarter symmetricEPSchange, missingflag. Seasonal QoQ is not a substitute for YoY.
- Confirmation group6: same-quarter YoY revenue symmetric change and missingflag; netmargin YoY difference in percentagepoints (clip[-100,+100]) and missingflag; quarterEPSsymmetricYoY × paststock20Dreturn/100 (clipped[-1,+1]) and missingflag. No earnings-surprise feature without historical consensus.
- Context4: genuineV9absolute/excess forecasts, observedpastmarket20Dreturn/volatility. Five fixed candidates: V9; context-only; context+growth; context+growth+acceleration; context+growth+acceleration+confirmation. No added penalties or candidates after inspecting outcomes.
- Residual Ridge penalty100, date-balanced squared loss, training-only mean/std, zclip5, unpenalized intercept (reuse established fitter). Correction=0.25*clip(predictedResidual,−2,+2) percentagepoints. Max correction0.5pp/head; fixed as a stability guard, not optimized on results.
- At each outerquarter train only signal ANDexit<quarterstart. Inner3previousquarters also require selectionexit<outerstart; readiness>=300rows/20dates. Candidate needs both meaninnerMAEs<V9, neither meaninnerRMSE>V9, and jointMAEbetter in>=2of3quarters; select smallest jointMAEratio/libraryorder, elseV9.
- Primary date-balanced MAE of both heads, RMSE/bias/tail and eachquarter are guardrails. Bootstrap paired whole dates in21-session blocks1000draws/seed20261101. No promotion from this internal run even if favorable; future untouched/prospective evidence required.
- Descriptive error cuts fixed: year/quarter/symbol, prior20Dmarketdirection, marketvolatility<15/15–25/>=25%, EPScomparisonmissingness and filingage. Decompose date-common squared error exactly with residual term. These are associations/accounting, not causal proof. Never use realized future market direction as a feature.

## Tasks

### 1. Source and test contracts

- [x] Independently audit EPS source comparability/coverage, separate from model outcomes.
- [x] Create `scripts/test_quant_v9_eps_growth.py` with literal fixtures for dates/periods/issuer/splits/losses/zeros/acceleration; maturity/selection/bounds and actual nested future-label invariance. Initial13testsRED,15testsGREEN.

### 2. Causal panel and baseline diagnosis

- [x] Create `scripts/quant_v9_eps_growth.py`: feature helpers, prepare/diagnose/run CLI; reuse existing Ridge/metric/CI functions, preserve original source files.
- [x] Produce complete paired panel and feature-evidence/source-admission audit, no outcome-dependent row exclusions. Reproduce baseline before segmentation. GREEN tests.
- [x] Seal protocol including exact script/test/source/panel hashes, fixed formulas/library/folds/gates before any candidate outcomes. SHA4b5fb828400f4a63b4ed2427f3ab30414fc8b810e2321f8eecabf82ce670e740.

### 3. Walk-forward and delivery

- [x] Run all10quarters once; perturb future labels and prove earlier selection/fit/prediction unchanged. Report all candidate results without posthoc winner selection.
- [x] Focused independent review: source admission, arithmetic, gates, cohort identity and conclusions. Fullscripts206PASS, Quant93PASS, new15PASS. Java unchanged, no Maven rerun. Independent48aggregate/400foldmetrics/40modelrefits/5CIs/10choices/sourceuses102528allverified.
- [x] Save results `docs/quant-v9-eps-growth-results-2026-11-01.md`, update checkpoint and inspect static figure. Operatingmodel unchanged; no demonstrated improvement.

## Progress / rulings

- Scope ruling: source/feature audit and main diagnostics/implementation can run independently; one read-only audit agent is used. No parallel implementers editing shared files.
- Existing dirty main preserved (81tracked modifications/381untracked at start). No commits/push/worktree/index changes.
- Pre-outcome source audit ruling: use explicit550/220day anchor freshness and same-concept safeguards after audit found1724quarter reads older than365days and165fiscalYear/calendar mismatches. No candidate performance was inspected for this ruling. Bothzero symmetricEPSchange=0 is a labeled feature convention; ordinary positive-baseline growth remains missing.
- Final minor deferred: cross-pair EPS-concept acceleration guard before future mixed-basic/diluted source reuse; current454EPSallDiluted so no result impact. No sealed experiment reopened. Source/code10hashes verified after allwork.
- Delivery: preserve all research artifacts/ledger for reproducibility rather than delete workspace; no commit/push. No DB/API access in this experiment. MainselectedMAE5.759968/4.405697vsV95.749703/4.405489, bothRMSEworse, no promotion.
