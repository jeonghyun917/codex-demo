# Safe DB restandardization and matched20D replay

**Goal:** Repair actual standardized inputs without losing evidence, then measure whether corrected inputs lower20D prediction errors under the same evaluation conditions.
**Spec:** User explicitly requested safe existing DB repair, corrected-data replay and real prospective validation preparation, delegated choices without interim approval gates.
**Mode:** Deep verification for DB mutation/data-loss risk; bounded to affected tables, replay and forward contract.
**Architecture:** Existing raw facts and pure coherent standardizer; immutable file backups/candidates; transaction updates existing IDs only, source facts unchanged. Unreconstructible groups quarantined with backup rather than fabricated values. Separate read-only research replay and offline prospective evaluator.
**Tech:** Java21/Spring/JDBC/MariaDB, H2 safety tests, Python stdlib/numpy.

## Global constraints
- Main existing dirty checkout deliberately reused, no Git stage/commit/push, no Railway configuration changes.
- No truncate/delete, no raw-fact changes, no monetary zeros invented. Preserve original IDs/unique keys and table rowcount.
- Full originals+source backup, SHA256, deterministic preimage/current-source guard, transaction rollback on failure.
- All file outputs append-only/new paths; no prior experiment overwrite.
- Replay fixed old20 dates/populations/holdings; calculate absolute and excess separately, no dropping failed names or future-availability selection.
- Past-asof cutoffs, correct currency/duration and missing input policies. Reconstructed research is not officialPIT or LIVE proof.
- Future20D outcomes cannot be claimed before they mature. No candidate promoted merely for small explored-sample improvements.

## Execution
- [x] Back up standard/raw tables and inventory engine/count/source completeness; record fingerprint.
- [x] TDD safe prepare/apply with drift/hash/duplicate/empty/mutation/rollback cases; generate immutable dryrun and inspect quarantine/coverage changes.
- [x] Focused independent pre-apply safety review; apply only verified plan atomically, verify originalIDs/rowcount/rawfacts and restored eligibility.
- [x] Rebuild historical target inputs through repairedselection; replay unchanged formulas with frozencontroltraining; exactcohort/errorcomparison and future-label perturbation.
- [x] Prepare UTC exchange-session prospective selection/maturity/evaluation tooling; run only honest real available observations, otherwise explicit pending/no observations.
- [x] Full relevant tests once stable, independent arithmetic, final findings/checkpoint and remaining externally dependent gaps.

## Review focus
Backup restoration, source/preimage drift, transaction atomicity, quarantine masking valid older records, preserved IDs/keys, replay control parity and no future leakage, explicit entry-close/exchange-calendar contract.

## Initial evidence
2026-10-31: stock_sec_fact705321rows/521symbols; stock_sec_financial_standard65115rows/518symbols; bothInnoDB; no standardizedsymbolwithout rawfacts. Query first tried nonexistentstock_sec_company_fact, failedread-only and correctedto actual stock_sec_fact (no statechange).

## Rulings and measured completion
ActualDBrepairCOMMITTED plan-v2:51831coherent+13284quarantine,65115sameIDs/keys,705321rawunchanged;21extrasnotinserted. Backuprestore65115rows/checksum2574788078matches.
IndependentpreapplyreviewGO noC/I; H2safety9 tests. Unknownoriginalgroupsretainbackup/quarantine, nofabricatedvalues.
RealDBpathreplay20dates101cohort/controlparity0; detected80symbol-days loseoldfileoverlays, so addedmatched-source sensitivity preserving90panels/233requiredrefs0uncovered. MatchedV9excessMAE7.658563242→7.664448799(worse), noimprovementclaim.
Addedfixedactual10-factor learning using nowavailablecorrectedfullpanel; 9families predeclared, allunconvincing. No search/promote. NewPythononly/immutableoutputs.
Forwardindex/benchmarkbindingbug found byparentreview, regressionRED→GREEN; actualV2protocols3indicesinitialized,0realcohorts (stale10/29forecastsnotrelabelled).
FinalPython120PASS,Java231with27skipped/204executedPASS0fail0error. Largerfuturepredictorvalidity NOTcomplete: actualfutureoutcomesnotmature, freshliveinput/officialPIT/longhistoricalcoverage stillneeded. See docs/quant-20d-db-restandardize-results-2026-10-31.md.
