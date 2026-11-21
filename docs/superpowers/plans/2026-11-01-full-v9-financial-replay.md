# Full V9 Financial Replay and Primary Revenue Recovery

**Goal:** Rebuild actual historical financial scores/training/OOF V9 using expandedquarterly sources, and recover remaining203revenues only from free originalfilings. Measure actual20Derrors, never fit or rescoreold60targets.

**Execution:** Parallelindependent primary-document work(goodsissuers vsservices/banks) whilemain implements sealedDTOmerge+readonlyreplay. Preserve dirtymain81tracked/393untracked, HEADb134d9fb0fbe57d30e08829518982b3ef2a7c83c; noGitcommit/push/DBwrite/serverrestart. Userwaivedquestions. One scopedindependentreview.

## Primary-source recovery contract

- Start exactmissing203rows in `tmp/quant-quarterly-financials-20261101-v3/financials.json`. Two disjoint source outputs: `tmp/quant-revenue-primary-goods-20261101-v1/overrides.json` (AMGN/JNJ/KO/MRK) and `tmp/quant-revenue-primary-services-20261101-v1/overrides.json` (AXP/CRM/GS). No overlappingedits.
- Eachacceptedentry: `identity` contains symbol,cik,accessionNumber,filedAt,startDate,endDate,form,fiscalPeriod; `revenueUsd` numericstring; `proof` contains sourceURL,localdocumentPath,sourceSha256,sourceDocumentAccession,sourcePublicationDate,periodStart,periodEnd,currency,unitsScale,printedValue,tableLabel,excerpt,pageOrLocator,totalScopeConfirmed=true. Exactsourceperiod/value/unit andasfiledaccession required. Do not use laterrestatement or sumcomponents/crossfilings. Retain rejected/blocked/gapsreceipts withwhy.
- OfficialSECfiling orissueroriginalannual/quarterlyreport preferred; existinglocaloriginals beforedownloads. CurrentAPIXBRL saysgoods/services/noninterestcomponents do not proveconsolidatedtotal. Need originalstatementline proof; onlyeconomicidentity confirmedforan exactvalue maypromote a componenttag. If printedtotaldiffers, requireactualsamefilingtotalproof. No assumedcomponent=total/proforma/segment substitution.
- Publicfree dataonly, noaccounts/paywalls. Respect403/429andSECfairaccess; noidentityrotation/authbypass/retryloops. Eachagentbounded12primarydocuments initially,prioritizegreatestcoverage, thenreturnresultsandconcretegaps. Originaldocument/bytes/provenance saved. Reportsmustdistinguishactuallydownloadedfromcache/retrospectivereconstruction.

## Full-model experiment (frozen before outcomes)

- Same902decisions2017Jan31..2020Aug28,26401trainingrows; lastallowedlabel2020Sep29; sameprices/benchmarks/shares/eligibility/sourcecutoffs. testDecisions=[]; old60files/targetarraysforbidden.
- Baseline is EPS-recovered genuineactualV9 replay `tmp/quant-eps-v9-training-20261101-v2/replay/forecast-only.json`; primary17904rows609dates2018Q2..2020Aug28; allOOF25473rows870dates ancillary.
- Expanded1973statementrows plusacceptedprimaryrevenueproofs are mapped to actualSecFinancialStandard DTOs. Retain legitimateoldassets/liabilities/equity/CFO/IDs onlywhen exactsameCIK/accession/filed/form/start/end/periodDays/currency. Keepoldunmatchedprovenstatements, but never manufacturebalancefields. Incomingnon-nullconflictwithsame-keyoldnon-null=>failclosedforinspection, no silentoverwrites. OriginalsourceFP/derivedQ4/fieldconcept/unit/proofs preservedinrawJson. Matching ignores rawfycomparative-metadata foridentity, but resultingperiodphase uses auditednewsource. Allnonfinancialinputsidentical.
- Newfinancialreadshim uses SEALEDinputfinancialunion only, no nativeDBfinancialfallback thatcouldoverride enrichedstatements byIDtie. Allotherprovenreadonlyprice/share/unit/issuerpolicy unchanged.
- Clone streamingtraining-only replay to NEWscript/class/namespace; do noteditoldsealedhelpers. ActualunchangedV2–V9 algorithms produce genuinecausalOOF; targetlabelsmasked, trainingANDexitstrictlybeforedecision. No candidate weight search orposthocperiodselection.
- Metrics: datebalancedabsolute/excessMAE,RMSE,bias; fixedquarters; highvolobservedmarketvol20>=25%, same109dates/3250primaryrows; allOOFancillary. Paired21fullcalendar-dateblock1000bootstrapseed20261101; highvolCIconditionsafterfullcalendarresampling. Thisispreviouslyexposedinternalresearch, notnewholdout/prospectiveproof/promotion.
- Freeze source/doc/code/input/runtimehashes beforemodeloutcomes. Revenue additionsbeforeseal included; lateevidencekeptseparate, notsilentlymutatedintorunningexperiment. No liveDBmigration; no operatingweightchanges.

## Files / tasks

- [x] Primary-original recoveries with receipts; exact source SHA/period/nullable targets validated. Initial63 in replay, late24 separate, total87/203;116 unresolved explicitly retained.
- [x] TDD `scripts/test_quant_full_financial_study.py` and `scripts/QuantFullFinancialTrainingReplayTest.java`; literal merge/conflict/proof tests, actual240 model outputs/future-label invariance/native fallback blocked. Balance helper/wrapper tests added.
- [x] New full-model adapter/harness, sealed DTO union and V1/V2 protocols; no production math or DB changes.
- [x] Both readonly902-date full replays completed; cohorts/labels/DB before-after checked, primary V2 errors and input/score coverage compared.
- [x] One incremental independent source/method/numeric review completed; full259 Python tests passed once after stable model implementation; no unnecessary full Maven.
- [x] Markdown report/static figure/checkpoint saved with remaining116, honest no-improvement conclusion and no60 tuning/promotion.

## Rulings / progress

- ExistingdataenrichmentdidnotrebuildV9 itself. Thisexperimentdoes so; no assumptionthatbettercoverageguaranteesbetterprediction.
- NativeDBfinancialfallbackmustbedisabledinsandboxreplayonly tokeepfinancialselectionidenticaltosealedresearchinputs. Modelmath/productionapplicationunchanged.
- Pre-outcome merge ruling:44non-null differences traceONLYdifferentconcepts, notsameconceptconflicts. ReadactualproductconceptPriority:Revenue=Revenues>ContractExTax>SalesNet;NI=NetIncomeLoss>CommonBasic. Canonicaladapter reselects literalexactperiod facts fromoriginalarchives usingtheseUNCHANGEDproductionpriorities, notv3researchtagorder. Higherpriority source-proved upgrades explicitlyaudited; sameconceptdifferentvalue stillFAIL. UnsupportedUSProfitLoss doesnotreplaceparent/commonNI. Existingbalances/CF/IDs preserved. Thusno silentdefinitionmix or choosingconceptsbyperformance.
- Primary bounded search produced 63 accepted null-revenue rows (JNJ16/KO9/MRK9/AXP16/CRM13), 140 unresolved; exact GS period mismatch rejected. All before model outcomes. Goods inspected 12 annual filings plus collateral; services retained 12 primary documents plus index. These are not necessarily 24 unique filings. Retrieval receipts retained; no paid/login.
- Pre-performance input-completeness ruling: actualselectionfieldaudit shows newer income-onlyperiods lose usablebalance/CFOfields despitealloldrowpreservation. BeforeANYV1performancecomparison, prespec `tmp/quant-full-financial-study-20261101-v1/balance-completion-prespec.json` fixesV2 to add4same-originalbalance/CFO fields only, exactinstant/end orCFstart/end+issuer/accession/filed/form/USD. NOcrossperiodcarry/YTDsubtraction/modeltuning. V1keptasdiagnostic; primaryjudgmentV2fixedbeforeperformanceseen, bothagainstsamebaseline. Allincomefields/nonfinancialinputs/labels unchanged.
- 15:50 KST bounded continuation of the same user-requested 203-source search: each source task may inspect at most 6 additional exact-accession originals within 10 minutes, coverage-only priority and no outcome access. New `quant-revenue-primary-{goods,services}-20261101-v2` outputs stay separate from sealed model inputs. Do not silently rerun, overwrite or revise the V2 primary experiment to include late recoveries. Report included and late-only recovery counts separately.
- Completion: V2 exit0 at15:53:23; comparison exit0. Primary MAE5.749702649→5.755728110 /4.405489367→4.410963802, both intervals include0. High-volatility both MAE worse, both intervals>0. Only2/10 quarters jointly improve. No promotion. Late24 original-source evidence independently verified; total87/203 recovered, remaining116 exact targets saved. All review findings Critical/Important0; root separate mean/RMSE/bias and all87 nullable-source checks pass. Report `docs/quant-full-v9-financial-results-2026-11-01.md`.
