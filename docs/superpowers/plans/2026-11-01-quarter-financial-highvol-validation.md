# Quarterly Financial Recovery and High-Volatility Validation Plan

**Goal:** Restore free source-proved quarterly income/margins/continuity and test bounded high-volatility corrections for actual20Derror reduction. Existing60targets are forbidden; no production writes or promotion from internal results.

**Execution:** Independent offline source-recovery task plus main research/test implementation, then one scoped independent final review. User waived questions. Preserve dirtymain (81tracked/386untracked atstart), oldsealedstudies, HEAD/index. No commits/push/worktree/database/server changes.

## Source contract (before outcomes)

- Reuse30officialSECcompanyfacts pluslegacyDIS archives whose originalSHA/CIK are verified from `tmp/quant-eps-primary-20261101-v1/manifest-{first3,rest}.json` and legacyDIS path. Current acquisition is not historical API capture. No fabricated newdownload claim. Free additional official sources only if a concrete gap remains; no paid accounts. SEC API docs checked2026Nov1.
- Extract originalUSD revenue/netIncome/operatingIncome and USD/shares dilutedEPS for exact(symbol,CIK,accession,filed,start,end). Keep raw observation/concept/archiveSHA proof perfield, originalsourceFP and derivedfiscalphase separately. Forms10K/Q+amendments, filed<=2020Aug28, periods2013Jan1..2020Aug28, end<filed. FY330–380days/Q70–110days. No recentrevision may inform earlierdecision.
- Do not combine differentaccessions/periods to fill one statement. Sameconcept/naturalkey conflictingvalues=>fieldmissing+conflictreceipt, not silentoverwrite. Revenue tag priorities explicit; preservebank netinterest-revenue identity. No sourceBasic/proformaEPS substitution.
- Fiscal phase: Q1–Q3 currentfiling uses reportedFP only for its report-end quarter; samephasecomparatives allowed onlywholeyear offsets. FY90day observations becomeQ4 only when theirend exactlymatches a trueannualperiodend in samefiling. OriginalFP=FY stays inproof. No FYminusQ1..Q3EPS arithmetic, no approximatequarter inventedfromYTD. ExplicitFYlongperiod observation remainsFY.
- Report-end anchor from latestduration-bearing actualfinancialperiod in filing (not arbitraryinstantsharesmeasurement). No calendar-frame Q# substitution for fiscalphase. Rejectambiguousphase. Dollar-fieldcomparisons need sameconcept or explicit auditedequivalence; EPScomparisons must sameDilutedconcept acrossall4accelerationobservations.
- Outputnewresearchpack `tmp/quant-quarterly-financials-20261101-v1/{financials.json,source-manifest.json,receipt.json}` with map symbol→statementrows. Rowfieldscompatiblewithresearchhelpers, plus `_epsConcept`, `_fieldConcepts`, `_proofs`. NoDBwrites. Missingvaluesremainnull.

## Frozen research design

- Keep recovered genuineV9OOF forecasts/labels fixed:25473rows/870dates; primary17904rows/609dates2018Q2..2020Aug28, lastlabelSep29. No old60outcome file orarrays. Internalpreviouslyexposeddevelopment, notnewholdout.
- Compare previous499rowfinancialsource features and expandedsource features on EXACTsameOOFcohort. FreshnessFY550days/Q220days and strictlypriorfiled/end, DISissuerboundary, exactYoYstart/end/duration andcontiguousquarterguards remain. Correct oldminor cross-pairconceptguard in NEW helper only; leave oldsealedsourceunchanged.
- Financial featuregroup: existing12EPSgrowth+4EPSacceleration+6confirmation, plus quarteroperatingmarginYoYdelta/missing, netmarginlevel/missing, YoYrevenuechange acceleration/missing, bank-netinterest-revenue identityflag (7extra). Allsameconcept/duration/issuer. No missingrowdrop. Context V9twoheads+pastmarket20Dreturn/volatility.
- Fixed quarterly financialresidualRidge100, datebalancedloss/trainscaling/zclip5, .25blend*clip(rawresidual,±2pp), correctionmax±.5pp/head. No penaltygrid.
- Highvolmarket candidate: unique-date maturetraining benchmark20D residual = actualbenchmark−(V9absolute−V9excess), predictors observedmarket5/20/60returns,vol20,dd60 andimpliedV9market. Ridge100; trainedatquarterstart; .25*clip(rawmarketresidual,±4pp) applied onlywhenobservedmarketvol20>=25%. Changesabsolutehead only, leavesexcessunchanged. This is an ablation, not expectedtojointlyimprovebothheads byitself.
- Fastregimecandidate: eachdecision uses only labelswithsignalANDexit<decision, latest63maturedecisiondates in sameobservedvolregime(low<15/mid15–25/high>=25); require20distinctdates, else latest63globalmaturedates; require20elsezero. Date-mean residual perhead, date-median acrosstrailingdates, .25*clip(estimatedresidual,±2pp). Dailycausalupdate isexplicit, not falselyquarterfrozen.
- Fivefixedcandidates: V9; expandedfinancial; highvolmarketonly; expandedfinancial+highvolmarket; fastregimebias. Candidatelearningrules/featuregroups/thresholds frozen beforeoutputs, no afterresult additions.
- Outers10quarters; candidatechoice from3priorquarters; bothmeanMAE<V9, neithermeanRMSE>V9, jointbetter>=2/3 elseV9. Allinnerselectionlabels exit<outercutoff. Financial/market fits quarterfrozen; fastregimeupdatesdailywithmaturelabels byalgorithm. PrimarydatebalancedMAEbothheads, RMSE/bias/quarter/highvolguardrails; paired21date1000bootstrapseed20261101.
- Actualfuturelabelperturbation must preserveearlierfit/selection/prediction, including dailyfastregime. No source orpointin timeguard loosening toincreasecoverage. No candidatepromotion basedonthisinternalstudy.

## Tasks

- [x] Offlineprimary extraction+literalTDD tests for filingphase/YTD/conflicts/proofs/issuer; sourceinventory independent of outcomes.16testsPASS, v3has1973rows/236directQ4/7902rawobservations; noAPI/DBwrites.
- [x] New researchhelper/tests with sourceconcept continuity, highvolmarket decomposition, dailymaturity/rollingfallback, caps, futurelabelinvariance; RED→GREEN.13testsPASS; correctedcross-pairEPSconceptguard innewhelperonly.
- [x] Buildfullcohortpanel, comparecoverage, sealcode/source/panel/protocol beforecandidateperformance. Preservepreviousbaselineforecastsidentically.47sourcehashes, protocol31505b4090f301dbd5a4d099f5861e4b4dc9aecab42480c28b9677c8ce272996.
- [x] Runfixedlibrary once, reportjointMAE/RMSE/highvol/fullperiodstability and confidences; donotpickposthocwinner. MainselectedbothMAEsworse; highvolabsoluteimproves/excessworsens. No robustgain/promotion.
- [x] Scopedindependentarithmetic/source/causalreview, focusedtests+onefullPython suite whenstable; inspectstaticfigure.235PythonPASS; independentallfeatures/47hashes/7902proofs/609dailywindows/metrics/CIs/refitsPASS; noCritical/Important.
- [x] Save Markdown researchreport+checkpoint+receipts, leaveoperatingmodelunchanged unlessfutureindependentvalidation justifiespromotion. docs/quant-quarter-financial-highvol-results-2026-11-01.md.

## Rulings / progress

- Research enrichment is separated from liveDBstandardization toavoidunsafewholesalerewrites while sourcecoherence is beingvalidated. No networkneeded torediscover values alreadyinverifiedoriginals; concreteadditionalprimary gaps willbe documented.
- Sourcepackv1 generated before outcomeaccess, then output-overwrite guard added withRED→GREEN; immutablev1retained and financials-identicalv2 selected. Source extraction13testsPASS;1973rows/236directQ4. Maininitial12testsRED→GREEN.
- Beforecandidateoutcomes: exactrevenuegapmetadatareview found3safeDOWtaxinclusive totals; sourcev3 addsONLYthose3values withdistinctconceptidentity/lowestpriority and16source testsPASS. Other203missingrevenue rows remainnull: goods/services/noninterest/proforma/segmenttags do not prove consolidatedtotal. Sourcev3SHA bdc1c1cf79bd3dc929dfb5b364b18b43be53fcfa11e88a26994a86ddeebcdd99.
- Conditionalhighvol uncertainty resamples21dateblocks from FULL609dates and conditionswithinresample, not21selectedhighvoldates. Extra literalRED→GREEN test passes. Sourceandcandidateparameters frozen before outcomes; sourcev1/v2retained.
- Finalreportclarification: joint2of3 is mean normalizedMAEratio acrossheads<1 in>=2quarters, withseparate3quartermean bothMAE/RMSEgates. No afterresult rulechange. Allsourcepackoutputs anddesign-prespecsnapshot retained; progresscheckboxes are not researchinputchanges.
- Finaldeliveryruling: source/calendar/coverage/dataquality is improved, not demonstrated futurepredictionaccuracy. OperatingV9/DB/automation/branch/index/commit/push unchanged; no old60fit. Record remaining203total-revenue identities for laterprimaryfilingreconciliation, no goods/services/proforma proxy substitution.
