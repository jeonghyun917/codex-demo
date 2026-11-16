# 20D error reduction implementation plan

**Goal:** Measure the current corrected V9 against actual 20-session outcomes and evaluate causally learned weights before any promotion.
**Architecture:** Immutable, file-only experiments reuse the five verified reconstruction outputs; no DB writes or web request changes. Compare identical date/security keys. Separate historical exploration from forward validation.
**Spec:** User-approved strategy in this chat: lower real out-of-time 20D errors, not acquisition counts.
**Tech:** Python/numpy, unittest; Java/JUnit for the promotion guard.

## Constraints and rulings
- Main and the existing dirty checkout are intentionally reused; preserve all prior files and outputs. No commit/push or database mutation.
- Existing research is not official PIT and has already been inspected. No promotion based on these experiments alone.
- Forecast target stays next-session entry close to the existing 20-session exit close, separately for absolute and excess price return; not total return or next-open execution.
- Same date groups stay together; only rows whose exit is strictly before the test decision enter training. Minimum eight mature training dates. Fixed ridge strength 10, train-only standardization; no winner-driven search.
- Candidate families: zero, historical median, V9, V8 (remove V9 source shrinkage), learned V9 affine calibration, price-only ridge, V9 plus price ridge, V2–V9 ridge stack. No new broad data acquisition until error attribution justifies it.
- Price features use only archived close history through decision (5/20/60-session returns, 20-session realized volatility, current close relative to the 60-session peak). They are exploratory vendor price proxies with adjustment limitations, not new official PIT proof.
- Full eight-version baseline table uses all 20 dates; candidate comparisons use identical eligible test rows. Report date-average MAE/RMSE/bias, per-date changes, worst errors and date-block uncertainty; never count 16,160 forecasts as independent observations.

## Tasks
- [x] Test causal training selection, train-only scaling, label perturbation isolation, coefficient fitting and equal-date metrics, malformed inputs.
- [x] Implement `scripts/quant_20d_error_research.py`; output source hashes, protocol, per-row predictions, per-date/aggregate errors and per-fold coefficients explicitly not approved for deployment.
- [x] Run fixed experiments and compare errors; independently reconcile saved rows and metrics.
- [x] Add a tested finite strict MAE improvement guard to `ExpectedReturnPromotionGate` without weakening existing checks.
- [x] One focused final review, one fix pass, relevant tests; write Korean findings and concrete forward/data queue.

## Execution record / scope status

This plan's bounded experiment and promotion guard are complete, NOT the whole20D improvement program.
No learned candidate shows defensible absolute-error improvement; no rollout. Two additional
price feature-group ablations added as explicitly post-initial-result exploration, saved in v2.
Review Critical/Important0; optional invalidbaselineMAE test deferred. Metrics shape mismatch
independently reproduced RED→GREEN before review completed.9new Python tests/82total PASS.
Gate equal-MAE promotion regression observed RED→GREEN;6focusedJUnitPASS. FullMaven200tests,
0failures/0errors/27skipped (173executed), BUILD_SUCCESS. Initial sandbox fullrun stopped on
Java attach namedpipe accessdenied5; same suite passed in authorized normal environment15.620sec.
See tmp/quant-20d-maven-authorized.log. No rerun of unchanged tree for transport/commit.
User declined questions, so no approval pause. No Git or DB writes. Larger remaining stages and
evidence gaps recorded in docs/quant-20d-error-results-2026-10-31.md, not marked complete.

## Review focus
Training outcome maturity; same keys across models; no future-price-dependent feature selection; unchanged source artifacts; no automatic deployment from explored historical data. Absolute and excess targets must never be mixed.
