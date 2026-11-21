# Extended historical constituent evidence - 2026-10-18

## Outcome

Preserved **two new complete issuer-republished Nasdaq-100 tables**, each **101 rows / 101 unique security symbols**, dated **2025-02-26** and **2025-08-26**. Together the original PDFs are **957,312 bytes**. Both are Macquarie Bank's official warrant offering documents hosted by SGX, not ETF holdings, but neither is a Nasdaq-authenticated historical constituent delivery. The issuer explicitly states that its index information derives from Nasdaq's public webpage and has not been independently verified.

Also newly reviewed Nasdaq's July 18, 2025 syndicated change release: TRI replaces ANSS before July 28 market open. The public reader exposed the content; a direct raw request returned HTTP 403 and was stopped. Its 459-byte error body is preserved **only as access-failure evidence**, not an original release download.

Official Nasdaq-authenticated full target lists acquired: **0**. No membership was promoted to `OFFICIAL_PIT`; existing snapshots remain `RECONSTRUCTED_RESEARCH_ONLY`. No DB, model, application, Git, or shared-checkpoint writes were performed by this subtask.

## New source inventory

| Source | Document date | Constituent effective date | Scope / actual access |
| --- | --- | --- | --- |
| [Macquarie call warrant SLD, SGX FileID 65000](https://links.sgx.com/FileOpen/.NDX_22%2C500_Call_SLD.ashx?App=Prospectus&FileID=65000) | March 3, 2025 | February 26, 2025 | HTTP 200 original PDF; 480,732 bytes; 70 pages, full table printed pp.39-42 |
| [Macquarie put warrant SLD, SGX FileID 67231](https://links.sgx.com/FileOpen/.NDX_22%2C000_Put_SLD.ashx?App=Prospectus&FileID=67231) | September 1, 2025 | August 26, 2025 | HTTP 200 original PDF; 476,580 bytes; 70 pages, full table printed pp.39-42 |
| [Nasdaq TRI/ANSS release via GlobeNewswire](https://www.globenewswire.com/news-release/2025/07/19/3118236/6948/en/index.html) | July 18, 2025, 20:00 ET | July 28, 2025, before open | Official source-attributed syndicated **event only**; public-reader observation, direct HTTP 403 |

The PDF document dates come from their cover pages. The filename/footer dates (February 26 / August 27) are not substituted for the publication date. The release URL's July 19 path is not its displayed July 18 publication time. The August table was published after the July 31 and August 29 forecast cutoffs and cannot be treated as information available then.

## Quality checks and bounded comparisons

All new artifacts are quarantined in `tmp/quant-extended-official-rosters-20261018/`. `manifest.json` records source identity, HTTP status, bytes, original-byte SHA-256, filesystem save timestamps and unknown exact request times. Headers retain the server's response dates. One initial sandbox DNS failure received no body; the later public download used Windows Schannel with certificate validation enabled. No disabled TLS verification or authentication was used.

Both entire relevant four-page tables were extracted and visually inspected after Poppler rendering. Checks found 101/101 rows/unique symbols, no duplicate or missing symbols, and retained GOOG and GOOGL separately. Poppler emitted font-fallback warnings, but the English tables were legible and complete. Derived TSV and text files are separate from originals.

The February-to-August set difference is exactly:

- February only: `ANSS`, `MDB`.
- August only: `SHOP`, `TRI`.

This is consistent with the official May 19 and July 28 swaps; it does not prove that no intermediate temporary members existed. The two lists share the same republisher and underlying mutable Nasdaq webpage lineage, so they are not treated as independent source lineages.

Conditional set checks compared the August baseline plus already-known annual/January events against the read-only export, strictly filtering namespace `NASDAQ100_MACRO_FREE_20261018`:

| Existing snapshot dates | Candidate and existing counts | Set differences |
| --- | ---: | ---: |
| 2025-07-31, 2025-08-29, 2025-09-30, 2025-11-28 | 101 each | 0 each |
| 2025-10-31, conditionally adding SOLS | 102 | 0 |
| 2025-12-31, 2026-01-30, 2026-02-27, 2026-03-13, 2026-03-27 | 101 each | 0 each |

These are **10 conditional consistency checks**, not 10 official historical snapshots. They assume no undocumented changes and use later-published evidence. October's SOLS state is conditional on either currently reported event interval covering October 31; exact dates remain unresolved below. Export SHA-256 remains `586b6b7de115332c606b15aa7d39ac7fd90309663f198956f9de694dd381d2c7`.

## Source conflict and limits

The [Nasdaq January 9 retrospective off-cycle table](https://www.nasdaq.com/articles/global-indexes/2025-nasdaq-100-reconstitution-and-performance-highlights), also present in the already-acquired research PDF, states SOLS was added October 20 and removed November 10, 2025. An [issuer SEC spin-off filing](https://www.sec.gov/Archives/edgar/data/2064953/000162828025047305/sols-20251029.htm) instead describes completed distribution / regular-way trading beginning October 30. The community reconstruction uses October 30 / November 6. A corporate trading date is not automatically an index-membership date, so these facts do not resolve the exact event interval. No automatic correction was made; a dated official corporate-action/index notice is still needed. VSNT's January temporary interval likewise remains unconfirmed by a primary index notice.

**High severity / high confidence:** full historical membership authorization and a demonstrably exhaustive, contemporaneously available change chain are still missing. This prevents official-PIT certification and leakage-free backtest claims. Whole-set agreement is research corroboration only, not model validation.

Public access and Macquarie's index licence do not establish our product/model-use or redistribution rights. The warrant documents reference distribution restrictions and carry index-sponsor disclaimers. Raw documents remain local and quarantined; no import is authorized by this evidence pass. No 871(m) source, restricted count-only validation, GIW login, hidden endpoint, account, payment, entitlement bypass, or fund-holdings substitution was used. Existing annual/WMT/PDF sources were reused, not redownloaded.

## Verification and handoff

Fresh offline verification command:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tmp/quant-extended-official-rosters-20261018/verify_evidence.py
```

This verifies 3 preserved response hashes (2 PDFs and 1 access-error body), the reused primary PDF hash, both cover/effective-date assertions, 202 full security rows, both 101-symbol uniqueness checks, the export hash, the baseline difference and all 10 conditional comparisons. `quality-checks.json` is the machine-readable result; `web-reader-observations.json` separately labels curated source observations without manufacturing original content downloads. Manual visual inspection covers all eight table pages. No Maven run was appropriate for this evidence-only subtask.

The data-quality and source-discipline workflows kept publication timing, document authorship, republished membership, event evidence, universe completeness and usage permission separate. PDF inspection supported the table extraction and visual QA. This scoped evidence note feeds the broader research-input recovery task; it does not claim the backtest is ready.
