# Free historical evidence continuation — October 31, 2026

Two additional original GFS SEC filing documents were downloaded over ordinary public access, HTTP 200, 4,721,981 bytes total. They add primary-document evidence, not new independent validation dates or imported financial features. No database/model/production Java changes occurred.

## Existing archive versus new evidence

All 75 outstanding foreign historical decision/accession references already have original companyfacts observations in the archived free sources: GFS 39 references/8 unique accessions, AZN 32/4, CCEP 1/1, FER 1/1 and TRI 2/2. The new queue independently checks archived SHA256/CIK, original submissions filing dates and strictly-prior decision eligibility. It selects 507 source-faithful IFRS observations across 16 unique accessions: GFS 266, AZN 132, CCEP 28, FER 30 and TRI 51. These are observations including comparative periods, not 507 statements, new downloads or model-ready rows.

The two new GFS documents are:

| Accession | Original filing date | Report period | Document bytes | SHA256 |
| --- | --- | --- | ---: | --- |
| 0001709048-25-000024 | 2025-03-20 | FY2024, 20-F | 3,533,168 | 723c6ea56a78e47b26c3fce4349100fd913786e2a4f009a394181aa0683ca937 |
| 0001709048-25-000071 | 2025-11-12 | September 30, 2025, 6-K | 1,188,813 | e4affbb0ff20b6441c272c7b96f9132867e9d865730bc6a52741f04ff00e7371 |

The annual [original GFS filing](https://www.sec.gov/Archives/edgar/data/1709048/000170904825000024/gfs-20241231.htm) and quarterly [original GFS filing](https://www.sec.gov/Archives/edgar/data/1709048/000170904825000071/gfs-20250930.htm) were acquired at 07:43:04 UTC on October 31, 2026. Their dates are not backdated receipt evidence. A preceding sandbox DNS-only failure is retained separately; one approved ordinary-network retry fetched exactly these two documents. No 403 or 429 occurred; the reusable acquisition stops on 401/403/429 and performs no retry, authentication, payment or access workaround.

Offline primary-document verification reconciled all 67 selected GFS observations for these two accessions, with zero unmatched values and zero selected inline parse errors. This covers 20 instant, 21 annual, 14 nine-month and 12 actual standalone-quarter observations. The 31 annual-filing and 36 quarterly-filing observations retain original IFRS names, period starts/ends, units, inline contexts, scales, signs, source URLs and document hashes. Dimensioned contexts are excluded from default companyfacts matching. All 507 queued observations were also checked against their original archived JSON rows.

Examples verified directly against inline-XBRL bytes: FY2024 Revenue USD6.750bn and diluted EPS USD-0.48/share; July–September 2025 Revenue USD1.688bn and diluted EPS USD0.44/share. January–September 2025 Revenue USD4.961bn is a nine-month value, not a quarter. The annual cover lists GFS ordinary shares on Nasdaq, rather than ADSs; this is retained as supporting listing evidence, not an automatic EPS-price conversion or approval of application normalization.

## Exact outputs and rerun

Definitive directory: `tmp/quant-free-history-queue-primary-approved-20261031/`.

- `manifest.json`: pinned source hashes, issuer/reference counts and acquisition distinction; SHA256 `0ac4a7e14576a2a6bc8c0dccff0a0aa0b736e817e395d249b1577d3b4edead58`.
- `accession-queue.json`: 16 accession-level items, affected decisions, filing/report dates, units, source-document URLs and explicit blockers.
- `source-observations.json`: all 507 exact archived IFRS observations, each source-linked and marked `acceptedForModel=false`.
- `primary-downloads.json` and the two `GFS-*.html` documents: actual request/retrieval times, headers, HTTP status, bytes and hashes.
- `primary-verification-v2.json`: definitive offline archive/primary-file reconciliation; SHA256 `9b45c1a4f34fc0334c48fd41fb231355f64d39594ba6439eae4a856ba75233e3`. V1 is preserved and superseded because V2 also verifies all 507 original archived observations.

`scripts/quant_free_history_queue.py` builds a new output directory offline by default. `--fetch-primary-gfs` enables only the two explicitly bounded document requests. Existing output directories are refused, so scheduled reuse cannot silently overwrite old receipts. `scripts/verify_quant_free_primary.py` verifies an existing acquired directory entirely offline and writes a new verification receipt.

Verification: eight queue tests and four primary-parsing tests passed. They cover original unit preservation, half-year/nine-month versus quarter classification, filing-date mismatch, future period end, corrupted source hash, wrong CIK, unsafe primary-document paths, 401/403/429 stopping, dimensional contexts, signs/scales and unsupported inline transforms. Full application/Maven verification belongs to the coordinating task; no production classes were changed here.

## Remaining obstacles

The incremental progress is two new primary documents and 67 exact byte-level reconciliations, with 507 archive-backed observations queued. Newly accepted model rows and database writes remain zero. Native IFRS normalization, 6-K support, exact financial-field semantics, statement coherence and approved quoted-share/EPS compatibility still require explicit implementation/regression evidence. AZN/CCEP/FER/TRI share/FX issues are not resolved by the GFS evidence. No currency conversion, ADS scaling, half-year subtraction, invented historical analyst/news inputs, neutral fill or lower-MAE claim was made. The previously committed 718 raw/101 standard US-row append was neither repeated nor touched.
