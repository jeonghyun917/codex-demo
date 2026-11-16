# Extended Quant input windows implementation plan

> Execute inline using executing-plans; independent roster evidence may run in parallel.

Goal: extend the already completed three-date research experiment into earlier complete-price
dates using actual free historic inputs, without altering frozen data or promotion rules.
Spec: current user request; prior TEAM model results in docs/quant-team-model-recovery-results-2026-10-18.md.
Architecture: source manifests and filing-bounded normalization feed the existing offline
in-memory model adapter. Whole-population eligibility is per model/date; missing V9 market
inputs cannot silently remove a security. Existing completed outputs stay immutable.
Tech: Python, Java21 existing Spring/MyBatis model services, read-only MariaDB.

- [x] Inventory missing names for all previously price-complete dates, select by coverage only.
- [x] Acquire AZN/GFS SEC financial facts/submissions and inspect foreign-issuer forms/units;
      preserve original bytes/hash/times, fail closed on access or identity ambiguity.
- [x] TDD issuer/form/taxonomy extraction, strict filing cutoff, annual/standalone-quarter
      duration, ADR EPS basis and missing-value behavior. Preserve prior TEAM extraction.
- [x] Run only newly targeted earlier dates through unchanged V2–V9 formulas in a new file-only
      namespace, retain full member denominators and explicit per-model exclusions.
- [x] Verify all-member prices, chronological labels, target-outcome perturbation, independent
      forecast metrics and nonoverlap top20 costs; compare DB invariants; one scoped review.
- [x] Save results/checkpoint and new dated official/issuer-republished roster evidence with
      publication/effective-date and licensing distinctions. No claim of official PIT without proof.

Review focus: foreign annual 20-F vs quarter6-K durations; ADR/ordinary EPS mismatch; prior shared
outcome mutation regression; missing member exclusion; retrospective roster not predictor input.
Rulings: reuse user-authorized main dirty workspace; no Git mutations, model tuning, production
API/data changes, fees/accounts/access bypass, or redundant full replay/Maven run. No approval gate.
Financial normalization tests precede implementation. Audit-only/new source acquisition uses
hash/schema checks. Native implementation plus one independent evidence researcher/reviewer.

Outcome: 5newdates4040forecasts plus3preserved dates2424 =8dates6464; all V2–V9full101members.
6nonoverlapperiods (previous2). New5financial testsRED→GREEN, full53PASS, independent64group
metrics/accounting matched,4040futurelabel-invariant forecasts, DB invariantsunchanged.
Ruling: preserve prior adapter/sourcehashes; versioned audit adapter for new experiment instead
of changing oldscript and invalidating existing evidence. No production abstraction introduced.
Ruling: exclude fullOct31102member andMar13missing14predictor dates, no partialpopulation.
Roster evidence2newfullissuer tables, officialPITstillunproven, SOLSdateconflict unresolved.
Review: noCritical/Important inextractor/adapter; verification scripts locally crosschecked,
not independentreviewed. Deferredminor: comparative filingfiscalYear vsobservationyear label.
Primary saved technical results docs/quant-extended-model-results-2026-10-18.md.
