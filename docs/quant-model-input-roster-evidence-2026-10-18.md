# Historical roster follow-up - 2026-10-18

## Outcome

Preserved one new complete **issuer-republished** Nasdaq-100 constituent table: **101 security rows, 101 unique symbols**, explicitly effective **2026-04-20**, in Macquarie Bank's official supplemental listing document dated **2026-05-04** and hosted by SGX. This is an issuer's primary offering document, but a **secondary republication of Nasdaq membership**, not a Nasdaq-authenticated historical constituent delivery. It is not ETF holdings.

Target-date official full lists acquired: **0** for 2026-01-30, 2026-02-27 and 2026-03-27. None of those snapshots can be promoted to `OFFICIAL_PIT`. No database, model, application, Maven, staging or commit changes were made by this bounded subtask.

## Sources and provenance

- [Macquarie supplemental listing document, SGX FileID 69863](https://links.sgx.com/FileOpen/.NDX_26%2C000_Put_SLD.ashx?App=Prospectus&FileID=69863). The 70-page PDF's printed pages 39-42 contain the complete table. Page 39 attributes the list to Nasdaq's public website and explicitly says the issuer has not independently verified it. The effective date is April 20, not the document's May 4 publication date or the April 28 footer identifier. Page 42 carries the index-sponsor disclaimer.
- [Nasdaq Sandisk/Atlassian change announcement](https://ir.nasdaq.com/news-releases/news-release-details/sandisk-corporation-join-nasdaq-100-indexr-beginning-april-20), published April 10: SNDK replaces TEAM before the April 20 market open. The public web reader returned the release; the direct PDF request was HTTP 403. The event is recorded as a curated observation, not falsely presented as a successful raw download.

All new artifacts are in `tmp/quant-model-input-roster-20261018/`. Successful original PDF: `raw/macquarie-ndx-effective-20260420.pdf`, **477,582 bytes**, SHA-256 `5b004e03d7d9a5b98880eae9ff19e188897d4ad6c08bccf9141a0fe347b4e5d4`.

`manifest.json` preserves the first verified-TLS Python certificate-chain failure and the Nasdaq 403 error body (388 bytes). `manifest-native-tls.json` separately records the SGX HTTP 200 request, its 2026-10-18T11:48:10.878767Z start and 11:48:13.796019Z end, original-byte hash, and Windows Schannel transport with certificate verification enabled. No certificate verification was disabled. HTTP response headers are preserved separately. The 403 body is access-failure evidence only, despite its requested `.pdf` filename; its signature flag is false.

`issuer-republished-roster-effective-20260420.tsv` is the extracted dated list, retaining distinct GOOG and GOOGL securities. `roster-pages-39-42-extracted.txt` and four rendered page PNGs provide inspectable companions. These derived files do not alter the original.

## Bounded quality checks

Grain: one security symbol in one dated issuer-republished snapshot. Expected unique count is checked from the complete table, not from a restricted count-only source. No missing symbol or duplicate symbol was found in the 101 extracted rows. The four complete relevant pages were visually inspected after local Poppler rendering; no row was truncated. Poppler reported fallback-font warnings, but the relevant English table was legible.

The April 20 list includes SNDK and excludes TEAM. Inverting only Nasdaq's documented April 20 swap gives a **research-derived** 101-symbol set. Compared against the existing read-only export, restricted to `NASDAQ100_MACRO_FREE_20261018` and the three target dates:

| Target snapshot | Existing unique symbols | Derived symbols missing from existing | Existing symbols absent from derived |
| --- | ---: | ---: | ---: |
| 2026-01-30 | 101 | 0 | 0 |
| 2026-02-27 | 101 | 0 | 0 |
| 2026-03-27 | 101 | 0 | 0 |

Export SHA-256: `586b6b7de115332c606b15aa7d39ac7fd90309663f198956f9de694dd381d2c7`. The first scratch comparison accidentally mixed index namespaces; the final script explicitly filters the intended index and dates and asserts 303 rows. Only the corrected scoped comparison above is retained as the result.

This adds whole-roster corroboration from a different publication source. It does **not** establish that the issuer's data lineage is independent of Nasdaq's mutable public webpage, that the table is error-free, that no other events intervened, or that it was available by any January-March forecast cutoff. The April event and May document are later information and must not be used as contemporaneous prediction inputs.

## Remaining blocker and permitted use

**High-severity completeness/availability gap, high confidence:** no complete dated Nasdaq-authenticated target snapshot plus demonstrably complete intervening change chain was acquired. A single inverse swap, even with exact set agreement, does not close that gap. The snapshots remain `RECONSTRUCTED_RESEARCH_ONLY`. Use the result only as local source-quality diagnostics; do not claim official point-in-time membership or investment-model validity.

No fund holdings were relabeled as index membership. No prior WMT, annual-reconstitution, or GIW files were redownloaded; no login probes, accounts, payment, license acceptance, restricted 871(m) data access, entitlement bypass, or external publication occurred. Public access and an issuer's index license do not establish our production/model-use or redistribution rights. The offering document itself references distribution restrictions, so the raw corpus remains local and quarantined.

## Verification

From the repository root, run the bundled Python executable with:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tmp/quant-model-input-roster-20261018/acquire_roster_evidence.py
```

Fresh offline execution verifies both saved response hashes, the PDF's dated-table and provenance assertions, the 101/101 count and uniqueness, SNDK/TEAM inclusion, the exact export hash, the 303-row index/date scope, and the three zero-difference set comparisons. The Nasdaq change event remains a manual primary-source web-reader observation; the failed raw download is not asserted as an event-content check.

The data-quality workflow kept source identity, dated-list completeness, string-set consistency, contemporary availability and usage permissions separate. The PDF workflow supported extraction and visual QA. This evidence Markdown is the requested primary artifact; no dashboard or product data import was appropriate.
