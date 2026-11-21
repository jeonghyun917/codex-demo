# Long-history OOF validation implementation plan

**Goal:** Build genuinely chronological calibration history from free older inputs, then measure V9 point-error change on newly sealed, previously unused label periods.

**Architecture:** Independent original-source acquisition and model readiness preflight, followed by one read-only product-model replay. Freeze sources/cohort/calendar/candidates before target labels. Build train-only OOF, verify functional V5→V6 and V6→V7 calibration, save target forecasts before calculating target outcomes, then compare identical full populations once.

**Tech:** Java21 product services through in-memory mappers; Python/NumPy/CSV; MariaDB read-only; archived free SEC, Zenodo author datasets and FRED DJIA. Existing main checkout and dirty user changes preserved.

## Constraints and review focus

- No paid data/accounts/access bypass, DB/product/Git/deployment writes, model promotion or threshold reduction.
- Older fixed30target-cohort history is research, not certified historical index membership, vendor licence, receiptPIT or prospective performance.
- Initial daily training2019-07-31 through2020-08-28 failed functional TRV coverage before outcomes. Readiness-driven expansion uses2017-01-31 through2020-08-28,902dates/26401rows with explicit DOW IPO/DIS issuer exclusions. Both tests retain the same matured train/OOF archive frozen2020-09-30; Aug31wouldexitSep30andfailstrictcutoff.
- Test decisions2020-09-30 and2020-10-30, next-session-close entry and20intervals/21closes; no test1roll-in. Global usage inventory includes DB, all known file-only S&P calendars, earlier360train/60test and202new-date outcomes.
- Coherent provider Close, not AdjClose, is used for returns. Historical feature prices are uniformly adjusted as of each decision; never independently undo each candle across AAPL4:1 split. EPS and actual shares need independently evidenced split-unit alignment; no FX/ADS guessing.
- Before target outcomes: all30permodel/date outputs; matching horizon/probability bucket matured OOFcount≥200 at functionalV5/V6 lookups and nonnull targetV6/V7calibration errors. V8/V9diagnostic50bucket receipts do not prove functional calibration.
- Prespecified arms: same long train with no priorOOF, same train with chronologicalOOF, chronologicalOOF plus train-only medianV9residual point correction. Zero/pooled-train-median are references. No hyperparameter search or post-test changes.
- Residual point correction is research-only, not a consistent probability/interval contract or deployable model until separately validated. Upside probability means beating benchmark, not positive absolute return.

## Tasks

- [x] Acquire30older price originals and verify hashes/dates/identity/Close column/calendar/overlap; DOWstandalone regular-way history only. Record limitations.
- [x] Read actual model functional calibration gates; compute schedule counts without target returns. More rows do not imply independent samples.
- [x] Add new Python preparation and isolated Java OOF harness with boundary/split/causality TDD. Previous sealed sources preserved.
- [x] Audit global prior exposure and freeze all inputs and candidate formulas. Final OOF203784/V9 25473;120/120functionalchecks pass, minimum545>=200.
- [x] Evaluate identical60target outcomes once after readiness, all-field future-label perturbation invariant, DB/source fingerprints unchanged.
- [x] Independent scoped source/method/calculation review, Python163/Java actual2017model232+shareguards pass; results/checkpoint and exposure ledger saved. No promotion: no joint MAE improvement.

## Actual execution amendments

Initial2019study stoppedNOT_READYbeforeheldoutlabels. First2017extension cancelledduringinputbuild on missingHON2018separationadjustments. Secondextension cancelledatOOF830/902beforetargetoutputs on NKEpre2015splitunits/VClassA-onlyincompleteddenominator. Allsource/protocolcopiesremainimmutable; thesearedata-correctnessrepairs, not outcome-driven selection.

Finalverifiedroot `tmp/quant-long-verified-oof-study-20261031-v1`, protocol0484973296cf1b29c2ba06fbf487247efad9398f730d8033ccb172125f632033. Newprior-filedNKE7originalA+B observations andVisa6issuer-reportedasconvertedtotals replaceincompletevalues; fullinputsareexportedBEFOREOOF andindependentlyreviewedDURINGOOF beforetargetoutcomes. No other28source-boundary blockerfound; missingEPSfallback and partialcorporateaction/PIT/licensing limitations remain explicit.

Final MAEabsolute/excess(pp):baseline8.550703638/5.557421995,OOF8.555325008/5.559814942,residual8.499276304/5.566129075. Residualabsolute-onlysmallgain is date-inconsistent andworseexcess; notoperator-ready. See `docs/quant-long-history-oof-results-2026-10-31.md`; all26401training+60testlabels nowregisteredused.
