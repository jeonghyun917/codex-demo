# SOLS and older Quant windows implementation plan

Goal: investigate exact SOLS dates and expand earlier full-population research using newly found
free ANSS history, without reducing existing model input or eligibility requirements.
Architecture: new immutable source manifests and a versioned file-only adapter; reuse unchanged
V2–V9 services. Preserve all earlier experiments and DB namespaces. No source guessing.
Spec: current user request; prior docs/quant-extended-model-results-2026-10-18.md.

- [x] Independently inspect primary SOLS index/exchange records; distinguish trading/index dates.
- [x] Preserve retained ANSS OHLCV and second-source overlap; never bridge acquisition/delist.
- [x] Obtain ANSS/MDB/ILMN SEC facts+filing metadata; reuse TEAM/AZN/GFS originals.
- [x] TDD generic US filing extraction, 80-observation gate, exact21-session coverage and no gaps.
- [x] Select older dates by data coverage only, build existing signals and V2–V9 in memory.
      Missing market/provenance input leaves entire V9 date ineligible, not fewer selected members.
- [x] Independently verify metrics/top20/nonoverlap/costs and target-label perturbation;
      one scoped review, full relevant Python suite once stable, compare original DB invariants.
- [x] Save exact date/model coverage, unresolved source conflicts, results and restart checkpoint.

Rulings: native execution in user-authorized dirty main; independent source lanes only. No Git,
DB, deployment, production Java/formula or promotion-rule changes; no user approval ceremony.
Do not rerun completed prior model stages. At most one implementation review/fix round. SOLS
cannot be forced through the historical builder with fewer than its existing80candles.

October30 continuation:6newSECoriginals/9374220bytes, ANSS370barsretained. 11olderdates
full101forV2–V8; combined19dates/14nonoverlapperiods. V9prior8dates/6periods unchanged;
4–6missingmarketinputs/date mean partial95–97forecasts not evaluated. Acceptedtotal14241.
57PythonPASS,8829rawforecasttarget-labelinvariant,141admittedgroupsPython/Javaaccountingmatched.
Ruling: versionedv2output after copied market-query range bug found inmainverification;
fix restored actual95/96/97knownmarkets, kept all7777V2–V8fields unchanged. Initialoutputs
superseded, oldcompletedexperimentsintact. FreshOct30pre/postDBinvariants unchanged.
Scopedreviewfirstpassdidnotcatchrangeissue; nosecondreviewclaim. SOLSindexdatesunresolved
despite verifiednewexchangerecords;80candlecoldstart independentlyblocksOct31. No rulesrelaxed.
Primaryresults docs/quant-older-model-results-2026-10-30.md. NoGit/production/serverwrites.
