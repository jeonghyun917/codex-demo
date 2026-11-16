# Daily20D collection and continuing historical recovery

## Activated schedule

Updated the existing `market-data-hourly-guard` heartbeat through the supported app automation tool, rather than adding a competing job. Name: **Quant20D 매일 수집·전방 검증**. Status verified ACTIVE. Cadence: daily15:10 Korea Standard Time (Asia/Seoul, UTC+09:00); existing destination and notification preference retained. Local host timezone was independently checked. The former hourly job is now this daily job.

15:10KST is after New York midnight in both DST seasons. This intentionally matches the existing Yahoo collector's conservative exclusion of the current New York date; early KST morning would otherwise miss the most recent US close. Weekends/holidays reuse an already frozen exchange anchor rather than create extra samples. The PC and desktop app must be running and local project/DB accessible. This is not a cloud service or a guarantee of collection while the PC is off. Official app requirements: https://learn.chatgpt.com/docs/automations?surface=app .

The durable automation prompt includes source/provider freshness checks, existing-free-credential refreshes, daily price+forecast controller, maturity evaluation, official calendar extension, and bounded highest-value historical input continuation. No new account/payment/access bypass. Existing provider 403/429 stops and daily limits remain; no automated Git transport, schema initialization, bulk DB restandardization or model promotion.

## Controller and actual smoke result

`scripts/quant_daily_forward.py` defaults to a read-only plan. `--run` holds an OS-exclusive lock, preserves immutable output/attempt markers, refuses duplicate frozen anchors, performs scoped data-only backups before new writes, uses verified `target/classes` ahead of an isolated operator class directory, saves code hashes, freezes actual new observations before entry close, and exports retained stock/index closes for every previous frozen cohort. Original frozen securities remain targets even when no longer current members.

Latest actual smoke output: `data/quant-daily/20261031T075424Z-fe12ec7e/result.json`, controller SHA256 `14e0682e475eb3309183fbf7bbfa22da473e3a7b7cf6802022ad4bb50ee8f039`.

- Correctly selected completed session2026-10-30; all three index cohorts already frozen.
- No redundant API batch or model recalculation. Yahoo latest batch remained49; V9 counts remain30/101/498,629index-specific observations.
- Wrote three fresh pending evaluations, all629PENDING_OUTCOME; matureN=0 and MAE=null.
- Raw facts706,039 and standards65,216 unchanged; this turn did not repeat the earlierUS append or rebuild the DB.
- First frozen cohort entry2026-11-02close, exit2026-12-01close. The model is not yet proven more accurate.

The controller's first new-session network-to-forecast path has automated boundary tests and reuses the prior actually executed Java batch, but has not been fired on a nonexistent future session. The real smoke exercised the already-frozen/duplicate-suppression/evaluation path. Future source availability, runtime permissions and machine uptime are operational dependencies, not verified future successes.

## Sustainable financial input correction

SEC's legacy parser stores absent instant `start` as `end`; changing that persisted key directly would create nullable-natural-key duplicate risk. The final fix leaves parser/storage keys untouched and recovers effective instant semantics only during standardizer selection when original raw JSON omits `start` and its `end` matches exactly. Genuine durations, missing/malformed proof, explicit null/start and mismatched ends fail closed. Stored input objects/raw evidence are not mutated. New tests cover source→standardizer balance recovery and bad evidence. Existing DB standards were not silently recalculated; the correction applies when a supported refresh is subsequently run.

## Additional free primary evidence

Two additional original GFS filings were downloaded successfully:4,721,981bytes.67/67selected observations were reconciled against original inline-XBRL contexts, units, periods, scales and signs. A queue separately verifies507already-archived observations across16foreign accessions/75historical references. These are not507new imported rows. Newly accepted model rows remain0: IFRS/6-K semantic mapping and share/EPS comparability need explicit supported implementation. See `docs/quant-free-history-continuation-2026-10-31.md` and `tmp/quant-free-history-queue-primary-approved-20261031/`.

## Verification and scoped review

- Maven257total,230executed successfully,27skipped,0failures/errors;14.809sec; `tmp/quant-daily-maven-final-20261031.log`.
- Python:31tests in `tests/python`,91in `src/test/python`,100in `scripts`:222passed. Three retained logs `tmp/quant-daily-python-*-20261031.log`.
- Independent scoped review found3Important issues. One directly relevant fix pass addressed all3: reuse a recent price job only with correct NY/session and required-symbol evidence; recheck provider403/429 after a nominally successful partial batch; block new captures when calendar coverage ends but continue evaluating existing cohorts. RED→GREEN tests cover each. The same review found no blocker in the source-backed instant selection fix.
- Minor findings: none. Unrelated historical audit/UI changes were excluded from this review, not declared clean. No repeated whole-suite/second independent review solely for transport or documentation.

The active calendar ends2026-12-31 and supports new20Dcohorts only until its future-session horizon runs out. The automation explicitly checks official exchange sources and creates a new versioned protocol root before further captures; existing frozen calendars are never edited. If extension or another prerequisite is blocked, old-cohort evaluation continues and the actual blocker is recorded. No weekday-only invented exchange calendar is used.

Remaining goal work continues in each daily run: real future observations and matured error versus0%baseline; highest-value free historical gaps; tested IFRS/6-K/quoted-share mappings; honest analyst/news missingness. The initialized historical baseline is null and must not be fitted after seeing future results. Lower20Derror remains the acceptance criterion, not raw row count, a backfilled success rate or a visually better dashboard.
