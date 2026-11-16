# V9 return-calibration candidates — frozen before follow-up replay metrics

Goal: reduce actual20D absolute/excess return MAE, without tuning the previously scored60 outcomes. This is research only; existing operational weights/DB/archives remain unchanged.

## Evidence before this decision

Already exposed training diagnostic (same17,904 rows/609dates): expanded financial data changes MAE by −0.018804/−0.020165pp atV2, −0.008176/−0.011670pp atV5, then +0.005863/+0.005493pp atV6. LaterV9 differences remain +0.006025/+0.005474pp. V6 explicitly scales both return heads toward0 using confidence and upside-probability calibration error, not directly fitted return MAE. Its formula reproduces all17,904 old and new V6 outputs. Keeping each old scale but updatingV5 still gives +0.003834/+0.007798pp; adaptive scale movement adds +0.002029pp absolute but improves excess−0.002305pp. Thus blaming adaptive-scale change alone would be incorrect. These are code/mechanical decompositions, not causal proof of market predictability.

## Fixed candidates

Only two candidates, with no new indicators, coefficients, sectors, regime thresholds or late winner selection:

1. `half_upstream`: exactly0.5×V9+0.5×V5 for each return head. Tests whether partially restoring pre-zero-shrink return magnitude improves future errors. No fitted parameters.
2. `past_mae_blend`: convex weightλ∈[0,.25,.5,.75,1] betweenV9 andV5. Fit only prior mature OOF records (decision<cutoff AND exit<cutoff), most recent252decision dates, minimum60dates/1000rows. Date-balanced loss. A nonzeroλ is eligible only if both mean MAEs strictly improve and both RMSEs do not exceedV9. Among eligible weights minimize the average of two MAE/baseline-MAE ratios; ties favor smallerλ. If none or insufficient history,λ=0 (exactV9). No probability calibration error used for return scaling.

For internal chronological diagnostics, freeze each fit at the quarter start, evaluate all rows in that quarter through2020-08-28, and do not consume outcomes until their true exit<fitcutoff. This remains exposed-training research, not a new holdout. The final forward coefficient is fitted once from mature source OOF history and frozen with source/class/formula hashes; no updates based on pending forward outcomes.

## Truly unused evaluation gate

The new global metadata audit cannot certify a ready historical holdout. 2021–2026 contains recorded5/20/60D or conservative whole-market exposure; earlier strict inputs are incomplete. Do not score an exposed period as unused. Any future historical holdout requires audited original inputs, universe/share/event coverage and fully preceding training, and a separate protocol before outcomes.

If existing October30 liveV5 and frozenV9 can be joined exactly before the planned November2 entry, freeze candidate predictions in a separate research-only forward archive. Require exact symbol/index/day/horizon and source cutoff guards; all frozen V9 names remain in the denominator. If V5 is missing, do not publish a cherry-picked subset; record not-ready. No mutation of existing629 liveV9 forecasts. Earliest current cohort exit is December1; until actual outcomes mature, error metrics remain null. Candidate loss/interval definitions are fixed before labels, and no retrospective baseline or candidate re-selection is permitted.

## Alternatives not selected

Repeated arbitrary indicator/weight searches on the same training period would not establish future gain. SkippingV6–V9 entirely discards other safeguards without testing. A small convex blend preserves a clear baseline, bounded input interpolation and an auditable MAE-oriented hypothesis. Neither candidate is a recommended investment system or an accuracy claim before unused/prospective results.
