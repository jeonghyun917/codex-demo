# Safe native IFRS/6-K inputs and actual20D sensitivity

Goal: support source-proven foreign filings in the existing signal/model input path and measure actual same-population past20D error before versus after the input change. No weight optimization or accuracy claim from row count alone.

Architecture: taxonomy-specific native IFRS mappings and real-period gates in the existing SEC parser/standardizer; existing FinancialInputPolicy still enforces currencies, adjacent comparable periods and quoted EPS. Independently SHA-verified source/basis registry constrains historical use. Run a new read-only replay from the already frozen matched-source20date context, changing only foreign financial inputs; preserve original outcomes, prices, nonfinancial factors, training, macro and market context. All model writes are intercepted in memory. Existing prospective629observations stay immutable.

Constraints: current dirty main reused, preserve unrelated files, no Git/deploy/schema change, no paid source/access bypass, no missing-data0/50 fabrication, no historical receipt backdating. User opted out of design/approval questions. One scoped review plus one direct important-fix pass, focusedTDD/fullrelevant suites once stable.

## Task1 native support (agent)

- [x] RED/GREEN tests: explicitIFRStaxonomyaliases,6-K forms, nullfy/fp, frame/real-duration annual/quarter proof, half/nine-month exclusion, currency/coherence, unknowntaxonomy rejection and unchangedUS-GAAP.
- [x] Preserve legacy instant DB keys and sourceProvesInstant boundary. Do not alter foreign USD/shares into quoted-security EPS without dated evidence.
- [x] Inspect actual supported raw files and expose exact normalized row/field evidence through existing standardize path, no mock-only integration.

## Task2 source/basis registry (independent agent)

- [x] Verify507queued observations/16accessions/75references; distinguish fiscal filing-yearlabels from actual observation periods. Derive only provable standalonequarters/annualperiods; do not subtract YTD for missingquarters.
- [x] Gate any GFS ordinaryshare or otherADR/FX basis evidence by actual filing date. Samecurrency growth can be valid even when quoted-EPS remains blocked. Newfreeprimaryrequests only if a genuine missing proof needs them.

## Task3 actual replay and safe integration (main)

- [x] Produce NEW helper/output with hashes and before/afterDB fingerprints; compile verified product classes first, isolated helperdir. Keep existingmatchedresult unchanged.
- [x] Read original verified foreign companyfacts through native parser→standardizer, with filed<decision cutoff; rebuild only affected financial panels with existing HistoricalBacktestSeedService. Preserve every nonfinancial score and full101-member rank population.
- [x] Replay existingV2–V9 on frozen20dates, compare V9 to matched-baseline outputs and rerun unchanged-signal controls. Report all2020 outcomes and affected-subset error, dates, nonoverlap limitations, no new holdoutclaim.
- [x] Perturb currenttarget futurelabels for one representative date; forecastvaluesmustnotchange. Independent arithmetic/source check.
- [x] Insert35newstandardsONLYafterbothactualrollbackprobes+source244fields+preimageguard; old65216unchanged/raw706039unchanged. ActualMyBatis260lookups match source.

## Task4 automation and delivery

- [x] Independently inspect ACTIVE daily15:10KST automation and providerstate; actual duplicate-skip smoke, no forcedfuturecapture or redundantbatch49.
- [x] Focused tests, fullMaven and relevantPython once stable, scopedreview, results/checkpoint. Preserveprospectivefiles and don'tpromotewithoutloweroutofsampleerror.

### Continuation after primary result

- [x] Run paired chronological training repair of the recovered20panels, bothsamepastlabels/periods/denominators, strictactualexit<nextdecision, eacharmownpastpredictions, nohyperparametersearch/noDBconnection.
- [x] Independentmaturity/leakage/arithmetic review, standaloneRED/GREENguards and exactfirstdatebaseline metadata; recorddefinitiveV2rollingoutput andnegativeMAE. Pre-panelhistoryremainsfrozen/notPIT, so no newholdout/promotionclaim.

Rulings: nativecalendar-only/frame-proofsupport is intentionallybounded; foreignquotedEPS/FX remainsblocked. Useappend-onlynativekeys ratherthansparseupsertmerges toavoidbasis/evidenceerasure. Standards-only35appendkeepsexactrawfiles/manifestbutdoesnotbulkimport331raw. TwoImportantreviewfindingsfixed; read-onlypreparecaughtCIKpaddingand4DEIancillarysource-shapeassumptions, fixedwithtestswithoutfinancialguardweakening. DefinitiveV3negativeMAEmeansNO modelpromotion. ContinuingpairedrollingtrainingrepairV2alsonegative;pre-panelhistoryconsistentrebuildandnewindependentperiodsremainfuturework,notclaimedcomplete.

Review focus: mixingtaxonomies, misleadingfy/fp, selecting half-year asquarter, laterfiling/shareproof leaking intoearlierdecision, EPS/ADS/FX mismatch, stalecompiledclasses, onlyrerankingtargetsubset, readonlyDBproxy accidentallydelegatingwrites, incompletecohort/modeloutputs, statisticsusedaspromotiondespiteinspectedoverlappingdates.
