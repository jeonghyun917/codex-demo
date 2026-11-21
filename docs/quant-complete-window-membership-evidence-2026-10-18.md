# Nasdaq-100 window membership evidence - 2026-10-18

## Outcome

Newly preserved **three successful public primary responses (1,592,789 bytes)**: Nasdaq's 2025 reconstitution research PDF, Nasdaq's WMT index-change press-release mirror, and the GIW weighting access page. An additional saved 403 response is an access-failure artifact, **not source evidence**. Initial sandbox DNS failures and the subsequent public requests are recorded separately without replacing either attempt.

**Complete dated official constituent lists acquired: 0.** The three requested snapshots, **2026-01-30, 2026-02-27 and 2026-03-27**, remain `RECONSTRUCTED_RESEARCH_ONLY`, not `OFFICIAL_PIT`. Official event corroboration is useful but does not certify all 101 securities or a complete intervening change chain.

Evidence is quarantined in `tmp/quant-complete-window-membership-20261018/`. This subtask performed no database writes, model changes, application changes, Maven runs, staging, commits, purchases, authentication or external publication.

## Primary sources and actual access

| Source | Preserved result | Scope |
| --- | --- | --- |
| [Nasdaq 2025 reconstitution research](https://indexes.nasdaq.com/docs/202601%20NDX%20Reconstitution%20and%20Performance%20Highlights.pdf) | HTTP 200; PDF, 1,379,501 bytes; four pages | January 2026 research; six December 22, 2025 additions, not a full constituent list |
| [Nasdaq WMT change release](https://www.nasdaq.com/press-release/walmart-inc-join-nasdaq-100-indexr-beginning-january-20th-2026-2026-01-10) | Public HTML download succeeded; 157,204 bytes; status code not captured by downloader | Published January 9, 2026; WMT replaces AZN before January 20 open. The URL's January 10 date is not the publication date. |
| [GIW weighting page](https://indexes.nasdaq.com/Index/Weighting/NDX) | HTTP 200; HTML, 56,084 bytes | Current October 16, 2026 view; login required for full access; no constituent rows visible. Not a historical baseline. |
| [Nasdaq IR WMT original](https://ir.nasdaq.com/node/109851) | Web-reader readable, direct raw HTTP 403, 380-byte error preserved | Same event, no additional full-roster coverage |
| [Nasdaq 2025 annual release](https://ir.nasdaq.com/node/109786) | Reused existing `officialEventQA` in corrected membership manifest; corroborated in public web reader; no redundant raw download | Already-known 6 additions and 6 removals; not claimed as new membership events |

Successful raw SHA-256 values:

- `raw/ndx-2025-reconstitution-public.pdf`: `74f20d5f11bbaca127454e9902a59dc9021e1e93803b576e8cdead2710233dd4`
- `raw/wmt-nasdaq-press-release.html`: `50db85eee8598f597a580df6c0adff618ee5222901fe3c3854756e12d1d8ac69`
- `raw/ndx-weighting-access-public.html`: `7d7cb17db1440ebe22e73c5a16e0df96d326de1288334c222f38cb440cea59c0`

`manifest.json`, `manifest-public-supplement.json` and `manifest-public-mirror.json` preserve URLs, actual status, local retrieval/save time and original-byte hashes where available. Web-reader observations are manually curated facts with calendar-day review precision, not manufactured raw-source downloads. A filesystem save timestamp is explicitly distinguished from an exact request instant.

## Checks and implications

The 325-row community interval file has SHA-256 `a6a0bdfc3302553b8993d57a1ef0883eda6f3b51f290fabbd8332b270f87492f`. Its full interval boundaries were compared at the day before and effective day of two official event groups:

| Effective before open | Official additions | Official removals | Interval agreement |
| --- | --- | --- | --- |
| 2025-12-22 | ALNY, FER, INSM, MPWR, STX, WDC | BIIB, CDW, GFS, LULU, ON, TTD | Exact; no extra boundary changes |
| 2026-01-20 | WMT | AZN | Exact; no extra boundary changes |

No new date discrepancy was found in these bounded checks. This is **two event-group checks**, not a claim that every change has been checked. WMT and AZN are the two newly primary-corroborated membership facts; the annual event was already recorded locally.

The read-only DB export `tmp/quant-complete-window-members-20261018.tsv` has SHA-256 `586b6b7de115332c606b15aa7d39ac7fd90309663f198956f9de694dd381d2c7`. For namespace `NASDAQ100_MACRO_FREE_20261018`, each target snapshot has **101 unique symbols**, matches the community intervals exactly, and all three target rosters are equal. No official whole-roster match is implied; both compared datasets derive from the same reconstruction lineage.

The saved report `interval-comparison.json` contains the full reconstructed sets and event comparisons. This can support consistency diagnostics and price-coverage denominators, but must not promote official membership completeness or forecast validity. A complete dated baseline plus a demonstrably complete change chain remains absent. VSNT's short January spin-off membership interval/removal timing was not established by a primary index notice in this bounded pass; it lies before all three requested target snapshots in the current reconstruction.

## Rejected evidence and licensing

- The public `FS_NDX.pdf` search snippet described March 31, 2026, but opening its mutable URL returned the September 30, 2026 document. A stale search cache is not an archived March source. It also contains only top-ten weights rather than a full roster. It was not downloaded or used for historical QA.
- A Nasdaq 871(m) count-only document was inspected for access/terms and rejected: it explicitly restricts using its information to verify or correct other data. No raw copy, count-based dataset validation or integration was performed. Its initial sandbox request failed before any bytes were retrieved; it is removed from future acquisition specifications.
- Login-protected GIW data, hidden endpoints, entitlements and account creation were not attempted. ETF portfolios were not relabeled as official index membership.
- Public readability is not an open-data, model-use or redistribution license. The raw files are local source evidence only; no production rights or permission to redistribute the corpus were established. The newly saved research PDF itself carries usage restrictions; do not import it into product/model data or use it to certify an official constituent database without the necessary rights.

## Verification

Run `python scripts/acquire_quant_window_membership.py --verify` for offline checks. Fresh execution verified **4 raw hashes (3 successful originals + 1 error body)**, passed both event boundary comparisons, checked WMT HTML assertions and the PDF signature, and matched all three unique exported symbol sets. A separate read-only `pypdf` extraction confirmed the PDF has four pages and the six named additions/effective December 22 date. No test or completion claim extends beyond this bounded evidence scope.

The data-quality workflow kept event validity, reconstructed internal consistency, whole-universe completeness and permission to use data separate. The requested source-evidence Markdown is the primary artifact; no analytical dashboard or investment recommendation is implied.
