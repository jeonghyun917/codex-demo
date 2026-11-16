# 20D follow-up execution ledger

**Goal:** Correct actual feature selection, preserve prospective predictions, and compare MAE-oriented candidates. Not collect files for their own sake.
**Spec:** User-approved error reduction strategy, delegated implementation and decisions without approval pauses.
**Architecture:** Three independent lanes, integrated once: financial selection/policy, append-only forward archives on batch refresh, fixed file-only robust experiments.
**Constraints:** Preserve dirty main/prior results; no Git transport, destructive DB changes, paid data, invented FX/ADS/PIT, or interactive external API. Do not auto-promote explored historical candidates. No pretend forward20D outcomes before time passes.

- [x] Financial lane: actual annual/quarter periods, conservative historical publication cutoff, comparable adjacent-period currencies and valuation units; RED→GREEN.
- [x] Forward lane: trace displayed20D contract; append-only local batch archive including observed timestamp, exact saved predictions and available upstream V8/market inputs; no rewrites/reconstructed-history relabeling.
- [x] Robust lane: pre-fixed median residual/conditional-median/Huber candidates, causal mature date groups, matched absolute/excess comparisons, saved weights and independent arithmetic.
- [x] Integration: one focused review, one fix round, Python+Maven once on stable combined tree, documented outcomes/gaps.
- [x] Material root-cause extension: standardizer per-field currency/duration coherence, not only comparison guards; tests before implementation. No legacy DB rebuild.

Ruling: Existing checkout reused, no nested worktree or automatic commit. Independent agents own nonoverlapping source files.
Ruling: Forward archive is an observed model-output record, not proof of complete historic raw-data receipt. No schema migration needed. Capture errors must surface in batch failure diagnostics; page GET remains DB-only.
Ruling: Existing reconstructed experiments keep their existing formulas/results. New financial policy version must not silently relabel old saved forecasts.
Ruling: Official PDD/ALNY SEC originals4files acquired200 and hashed. Same-filing PDD CNY annual pair restored to evidence only, not automatically inserted into shared DB.
Ruling: Robust followup reuses exact historical V9 source snapshot (verified original hash) because current archive code legitimately changed the working source. No hash bypass; original output preserved.
Review: Important current-day historicalV8→LIVEV9 relabel reproducedRED; require LIVE_V8 provenance before forward batch writes. One fixpass. Optional validator baseline-value/ALNYmanifest checks remain nonblocking provenance details.
Ruling: Reviewer identified preexisting within-row mixedunits as material limitation. User's full data-correctness scope authorizes a bounded standardizer root-cause correction as additional task, not a new prediction search or review loop.

Completed evidence: Python91PASS; Maven222/27skipped/0failure0error(195executedPASS),21.827sec.
Forward5testsPASS includes persistedprecision/multihorizon/researchupstreamreject; financial17focusedPASS.
Robust17,776rows/132fits/46hashPASS; primary4originals/3,541,431bytes200verified. No learnedcandidatepromoted.
Full larger program NOTcomplete: legacyDBrows notrebuilt, continuousPITinput/officialpopulation and live20Doutcomes notproven.
See docs/quant-20d-followup-results-2026-10-31.md for precise outcome/remaining steps; no approval question.
