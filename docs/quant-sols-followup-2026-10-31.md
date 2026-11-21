# SOLS exact index-date follow-up — 2026-10-31

## Result

**Unresolved: no stronger dated Nasdaq index-event notice was found in this bounded public follow-up.** Preserve the competing October20/November10 and October30/November6 claims. Do not correct membership, certify official point-in-time data, or reinterpret a ticker transition as index entry.

Nasdaq's [January2026 retrospective](https://www.nasdaq.com/articles/global-indexes/2025-nasdaq-100-reconstitution-and-performance-highlights) still supplies the administrator-authored October20 and November10 date pair. This follow-up found no contemporaneous administrator notice specifying before-open/after-close effectiveness or its original publication timestamp. Its repeated article/PDF/landing-page copies share one lineage and are not independent confirmations. The prior [investigation](quant-sols-date-resolution-2026-10-18.md) and [whole-window evidence limits](quant-complete-window-membership-evidence-2026-10-18.md) remain in force.

## New, limited finding

The issuer's [October30 completion release](https://honeywell.gcs-web.com/news-releases/news-release-details/honeywell-completes-spin-solstice-advanced-materials) explicitly locates regular-way SOLS trading at that day's market opening, with the distribution effective 00:01 Eastern Time. The [October16 board approval release](https://www.honeywell.com/us/en/news/press-releases/2025/10/honeywell-board-of-directors-approves-spin-off-of-solstice-advanced-materials) separately describes expected when-issued SOLSV trading on/about October20 and regular-way SOLS October30. These are primary issuer lifecycle statements, **not Nasdaq-100 membership notices**. They strengthen the separation between distribution time, trading time and index-effectiveness; they do not resolve the latter.

| Evidence | Publication | Event semantics | Retrieval/provenance |
| --- | --- | --- | --- |
| Previously saved Nasdaq retrospective | Matching article: Jan9,2026 11:18AM EST; PDF: January2026 research | Claimed index entry Oct20 and removal Nov10; no before-open/after-close timestamp | Reused original hash, not freshly downloaded; prior acquisition manifest retained |
| Previously saved ECA2025-559 | Oct9,2025 page date; update time unknown | Exchange when-issued SOLSV Oct20 / regular-way SOLS Oct30, not an index event | Reused original saved Oct18,2026 UTC |
| Newly read Honeywell completion release | Oct30,2025; publication time unknown | Distribution00:01 ET; regular-way SOLS at market opening Oct30 | Public reader reviewed Oct30 UTC/Oct31 Seoul; curated observation only; raw request later403 |
| Newly read Honeywell approval release | Oct16,2025; publication time unknown | Expected when-issued / regular-way dates only | Public reader observation only; no raw acquisition |

No effective index timestamp is inferred from an issuer publication day, HTTP header, filename, search crawl date, or reader retrieval date. The proposed fields remain `announced_at`, `effective_from`, `effective_until_exclusive`, and `source_observed_at`; unknown values remain null.

## Acquisition and use boundary

New successful original downloads: **0** (ceiling5); new primary index notices: **0**. One ordinary TLS-verified raw issuer request returned HTTP403 and a506-byte error body; no subsequent attempt was made to that host or an alternate endpoint to obtain the blocked original. The saved `honeywell-completion.html` is **only an access-failure artifact**, not the release. Earlier local missing-directory and sandbox-DNS failures had no response bytes. Public-reader observations were obtained before that403 and are saved as curated summaries, not fabricated raw HTML.

Evidence resides only in `tmp/quant-sols-followup-20261031/`. `manifest.json` records URLs, response/header files, hashes, byte sizes, actual server Date and filesystem save time separately; exact request-start time was not captured. `web-reader-observations.json` contains the reader summaries and bounded search groups. Search non-discovery is not proof that no notice exists. No purchase, account, authentication, private endpoint, entitlement, licence acceptance,871(m) source, ETF-holdings substitution, or access bypass was used. Public readability does not establish model-use or redistribution rights.

## Research consequence and smallest useful next step

The exact index interval remains unfit for `OFFICIAL_PIT` promotion. Both competing intervals include October31,2025, but that agreement neither certifies the complete universe nor repairs the SOLS predictor cold start. Even granting a validated SOLSV→SOLS price-identity policy, October20–31 contains at most10 weekdays; the existing80-prior-bar requirement remains a hard block. Do not splice HON history into SOLS, drop SOLS, lower thresholds, or silently accept a partial window.

The actionable unresolved input is a freely public dated Nasdaq index notice, or an already-authorized historical administrator delivery, explicitly naming SOLS/SOLSV, entry/removal effectiveness and before-open/after-close semantics, plus original publication timing. Do not add more derivative copies of the same retrospective as a substitute. No DB/application/shared-script/checkpoint/Git changes were made by this subtask; dirty `main` was preserved.

## Offline verification

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -B tmp/quant-sols-followup-20261031/verify_evidence.py
```

Fresh execution exited 0: all 3 raw hashes/sizes matched (two reused originals and one error body); the 403 header/body and curated-observation separation checks passed; the weekday upper bound was 10, below 80. It does not prove index dates, issuer historical delivery availability, full roster completeness or usage rights. `quality-checks.json` preserves the actual result. No Maven run is relevant to this local evidence-only addition.

The data-quality workflow kept issuer events, exchange security events, administrator index events and retrieval provenance separate; it did not force a date resolution.
