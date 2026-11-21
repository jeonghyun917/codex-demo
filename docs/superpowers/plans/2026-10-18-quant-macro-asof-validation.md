# Macro as-of research integration and validation

**Goal:** Actually use the acquired previous-day ALFRED snapshots in prediction/backtest validation,
while preserving frozen baselines and continuing bounded free primary-source acquisition.

**Architecture:** Offline-only research adapter converts verified as-of observations to the existing
macro-feature representation, sharing the current score formulas without fabricating realtime
intervals. New `SP500_MACRO_FREE_20261018` and `NASDAQ100_MACRO_FREE_20261018` namespaces receive
the same decision-date membership/price benchmark/context and V7 inputs as the frozen research data.
Run only the affected V8/V9 stages, not V2–V7. Compare against frozen V8/V9 on exactly those dates.

**Constraints:** Java21/MariaDB/MyBatis; local Maven repo; no paid data, LIVE relabeling, fake
publication timestamps, promotion-gate changes, page-request external calls, Git integration or
overwrites of frozen/operating namespaces. Current main checkout is explicitly authorized; preserve
all unrelated dirty files. Execution proceeds without approval prompts as the user requested.

## Tasks

- [x] TDD `StockMacroAsOfResearchFeatures` and tests: exact previous-day mapping, all seven series,
  duplicate/future/wrong-source rejection, research-only namespace, hand-derived values/scores,
  missing historical comparisons stay null. Share existing macro scoring methods, not availability rules.
- [x] Add an explicit offline helper `scripts/QuantMacroAsOfValidation.java`: verify pinned input
  hashes and every derived source mapping before writes; atomic namespace registration/copy plus
  feature insertion. Retain original input timestamps. A resume may reuse identical registered inputs,
  never overwrite a different namespace. V8/V9 each commit transactionally.
- [x] Evaluate same-population no-macro vs as-of V8/V9 with existing diagnostics. Also check
  complete-date nonoverlapping portfolio eligibility; do not silently drop unavailable members or
  convert partial research coverage into a certified portfolio return.
- [x] Independently cross-check counts, return/Brier/MAE changes, input dates and no-mutation
  invariants. Run focused tests while editing and one full relevant suite when stable.
- [x] Parallel independent worker acquires genuinely additional primary free terminal-event or
  membership evidence under new artifacts, with URL/timestamp/hash; no integration writes by worker.
- [x] Save result report and checkpoint, including measured improvements or regressions, explicit
  research-only status, and blockers still preventing live-performance certification. No tuning to
  improve the measured scores, no repeated full baseline replay.

## Review focus

Latest comparison-vintage rows must never enter a historical decision. Numeric rows are as-of
snapshots, not realtime intervals. Baseline and candidate must have identical membership/date
denominators. Missing members and missing benchmark sessions remain exclusions, not zero returns.
Prediction calculation timestamps remain actual. Complete-period eligibility must include every
target member, and later labels cannot change earlier prediction adjustments. Empty/partial runs
must not be reported as a pass. Registry/input hashes and transactional boundaries protect resume.

## Execution ledger

Intent confirmed by user's explicit request; prior recommended separate research integration is
selected. No new approval gate. The previous no-import limitation is intentionally superseded only
for the two new namespaces; all old datasets and production promotion decisions remain untouched.

Resumed and finished verification 2026-10-18 about 16:10 KST. Both namespaces had already
committed at 10:54; no replay was performed on resume. Independent SQL/Python verified 74
feature rows and 12 metric groups. Full Java200/Python34 tests pass. Source invariants are
byte-identical. Full-population portfolio periods remain0 and strict evaluation5 is still
INSUFFICIENT_DATA. External review completion was unavailable after interruption; direct scoped
review and independent calculations are recorded, not represented as an external review pass.
See `docs/quant-macro-asof-validation-results-2026-10-18.md` for results and limitations.
