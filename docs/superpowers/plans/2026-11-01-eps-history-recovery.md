# EPS History Recovery and Chronological Validation

Goal: Fix proven EPS loss at ingestion and historical refresh, restore only source-proved coherent EPS, compare unchanged V9 chronologically without old60targets.

Confirmed: per-concept tail80drops dense historicalEPS; sparse legacy cash/revenue remain. Normal sync rebuilds domestic standards from incoming partial batch after deleting historical standards. OfficialAAPL8.31/MSFT2.71/JNJ1.39 exact historicalaccession/period source observations prove loss before standardization. Duration/coherence policy is not relaxed.

## Constraints and decisions

- Retain complete target-concept history by default (cap0=unbounded; positivecap remains explicitlossyoptin). Refresh rebuild reads complete persisted raw union, not only incomingbatch. Both rebuild entrypoints transactional so delete+insert rollback together.
- TDD rootcause regression: historicalEPS outside last80 survivescap0; explicitpositivecap stilllimits; synchronization retains pastfinancialhistory; persistence failure restores originalstandards on bothentrypoints using realH2transactions.
- Recover355nullEPSselectedkeys only from30officialCFarchives pluslegacyDIS, sameCIK/accession/filed/form/fy/fp/start/end/days/currency. PreserveIDs/allnonEPSfields.99nonnullEPS unchanged,45unrecoverable keepnull.
- No oldresult/evaluation/60targets. Newtraining-only902date replay, same26401labels, oldforecast-only baseline, same2018Q2..2020Q3/609dates evaluation and21dayblockbootstrap. No weight retuning/algorithmchanges.
- Stage and validate allrepair artifacts before live application. IfliveSQLrepairused: targetednullEPS/primaryfact-only inserts, immutablebackup+preimageguards+transactionrollback; never whole-tabledelete, never overwriteexistingnonnullEPS, preserveoriginalIDs/keys/nonEPS.
- Productiondata-loss prevention gets focusedtransaction/failure verification, notopen-endedreleaseaudit. Existingdirtymain preserved; noGitstage/commit/push/worktreeceremony. Userexplicitnoapprovalgates.

## Tasks

- [x] Reproduce oldparser/DBloss and collectofficialsources. Independentraw andcodeaudit receipts.
- [x] Add failingtests, implementdefaultfullhistory+persistedrawrefresh+atomicrebuild;focusedtests then fullMavenonce stable.
- [x] Prepare EPS-only repairpack, independentall-field/source/missingness verification.
- [x] Freeze newtraining-only protocol, runactualV2–V9OOF and identical-cohort comparison, noold60scoring. V2 completed902dates/26401rows/203784OOF; primary17904pairs609dates. Tiny MAE decreases bothCIinclude0; ancillaryabsoluteMAEworse.
- [x] Before viewing recoveredmodelperformance, freeze followup repeating the EXACT prior10candidate nestedlibrary/features/loss/folds/selection on recovereddata. No newcandidate orpenalty choices; distinguish data-only V9 effect from postprocessor effect. Prespec tmp/quant-eps-v9-training-20261101-v1/nested-followup-prespec.json. V2 output-only retry amendment retained separately.
- [x] Safelyapplyeligible historicalEPSrepair ifstaging/preimagesverified;otherwise reportconcreteblocker withoutunsafeoverwrite. Actual rollback probe, atomic355repair commit and fresh read-only355postimages/355rawfacts passed; backup and receipts preserved.
- [x] Scopedindependentreview, report/checkpoint/sourceproofs; donotclaimpredictionimprovement unlesscomparison supportsit. Independent exactarithmetic/cohort/causality/runtime/DBreviewPASS. Unchanged10candidate nestedrerun completed and worsensbothMAEs; no promotion. Report docs/quant-eps-recovery-results-2026-11-01.md.
