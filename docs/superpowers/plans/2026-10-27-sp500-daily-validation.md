# S&P500 Daily Validation Implementation Plan

**Goal:** Complete bounded data-gap triage, evidence-backed alias recovery, period qualification,
and daily cash-account research without pretending incomplete historical AI signals are available.

**Architecture:** Immutable file-only research versions; reuse existing price parsing and panel builder.
Separate contemporaneous feature selection from future outcome labeling. Fail portfolios on unknown
held/ordered prices rather than deleting inconvenient trades. No production DB changes or orders.

**Tech Stack:** Python standard library, numpy, scikit-learn; local CSV/JSON inputs.

**Spec:** User delegated remaining decisions and approved proceeding without interim questions.

## Fixed Protocol
- Initial USD100000; no margin, no shorting; whole shares; maximum10positions.
- At close rank eligible members; at most2new names next session; budget10%of previous close NAV/name.
- Reserve only cash available at decision close; do not recycle same-day sales into new orders.
- Target15%, stop5%, maximum20sessions inclusive of entry; gaps can lose more than5%.
- Daily simultaneous touch: stop first. Costs10bps/side; stress20bps/side. Cash interest0.
- Prices are vendor-adjusted, not total-return or original execution prices. No tax/FX model.
- Research starts2023; expanding monthly training, labels mature20sessions before training.
- Development2023-2024; previously-inspected evaluation2025-2026. No claimed untouched holdout.
- Fixed comparisons: cash, momentum20 top20%, new PRICE-ONLY barrier model top20%, model+indexMA50.
- Existing20D and signal comparison requires genuinely available daily histories; never forward-fill
  sparse reconstructed snapshots or relabel momentum20 as the existing20D model.
- No parameter search or winner-based threshold changes. No deployment/promotion from these results.

## Tasks
- [x] Classify65missing symbols and recover11evidenced ticker-continuity mappings; unresolved causes remain explicit.
- [x] Build a new version; qualify daily coverage and complete contiguous windows. Formal qualification fails; official calendar remains missing.
- [x] Implement/test causal labeling, daily order lifecycle and cash accounting.
- [x] Run fixed comparisons and cost stress; independently check24accounts and443838probabilities.
- [x] Deliver result table, unusable comparisons and prioritized acquisition queue.

## Execution Clarifications
- Weekly fixed training subsample(session index modulo5), daily scoring; last126sessions used for calibration, with20session label maturity/purge.
- Short complete-price window decisions stop2026-07-02, allowing20session runoff through2026-07-31.
- Feature/label quality filters exclude unresolved large moves. Execution retains valid observed OHLC,
  including large moves; corporate-action uncertainty remains a report-level blocker, not silently erased trades.
- A ledger export field-name collision was fixed before final result generation; no strategy thresholds changed.

## Review Focus
- Ticker changes are not mergers; do not splice different securities.
- Future price availability cannot determine today's ranking.
- Membership removal does not erase existing positions.
- Missing future held marks stop evaluation, not silently force convenient exit.
- Censored labels, historical reconstruction and selection bias must remain explicit.
