# New-period 20D validation implementation plan

> **For agentic workers:** Execute the bounded research tasks inline; delegate only independent source preparation / replay implementation. Do not repeat the completed20-date experiments.

**Goal:** Supplement genuinely missing earlier financial inputs and measure fixed candidates on two previously ungenerated target dates without further tuning.

**Architecture:** Seal target dates, populations, candidate formulas, fixed training cutoff and gates before outcome calculations. Use actual product model math in a read-only isolated replay; new SEC observations remain research overlays. Compare all candidates on the identical full population and distinguish temporal holdout from globally pristine untouched market history.

**Tech Stack:** Java21 product model helpers, Python/NumPy, local MariaDB read-only, archived free SEC and official Nasdaq notices.

**Spec:** Current user request: add missing historical training evidence, use a different historical evaluation interval, no repeated data or approval interruptions.

## Constraints and review focus

- Main checkout preserved; no production/DB/Git writes, no model promotion.
- Freeze training at2026-04-30 for BOTH decisions; signal and actual exit strictly before cutoff. Do not roll first test results into second training.
- Decisions2026-04-30 and2026-05-29; all101securities per population, no survivor/subset filtering. Official TEAM→SNDK and CSGP→LITE changes are required.
- Base roster reconstruction and benchmark remain proxies, not certified official PIT or total-return data. Two dates are a small correlated sample, not202independent time observations.
- Existing history already examined at other dates; report novelty only against audited saved artifacts/DB, not a universal never-seen claim.
- Missing/nonfinite model or price/benchmark/shares input blocks that full-date comparison. No invented zeros or guessed market cap.
- Fixed candidate menu: correctedV9 baseline; repaired-prepanelV9; training-only pooled median absolute/excess; fixed ridge(alpha10) on ten historical factors. Zero is a reference, never a trained improvement claim.
- Calendar21observed closes per holding; same price basis; cutoff exclusions and future-label perturbation tests; no adjustment/tuning after opening test outcomes.

## Tasks

- [x] Inventory36savedartifacts and DB date usage; seal protocol and hashes. Later audit found holding-window exposure, so do not call either globally untouched.
- [x] Recover new pre2024-07-31 statements through actual native normalizer; verify distinct keys and original values.97newrows/602values; FER unavailable, not synthesized.
- [x] Implement/test read-only actual model replay and prior financial-score repair, preserving nonfinancial inputs and3568maturetrainingdenominator. V9fullblocked11inputs; pre-MAEsealedV8fallback preserved originalprotocol.
- [x] Evaluate fixed candidate menu once on202outcomes; independent arithmetic. Financialrepair/ridgeworse;medianexcessonlybetter. No promotion. Historicalsourcegap4closesverified28overlaps,fileonly.
- [x] One scoped independent review, results/checkpoint saved. Added TDD strict all-horizon holding-window usage gate; actual currentperiods correctly reject untouchedclaim. Fullindependentvalidationremainsopen,notmarkedcomplete.
