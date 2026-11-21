# V9 historical common-share evidence: TEAM and MDB

Scope: free, read-only primary-source share observations for July 2024–May 2025 research decisions. This is filing-date-bounded research, not proof of a contemporaneously captured prediction or an investment recommendation. No database, application, model, or Git state was changed by this evidence task.

## TEAM: sum common classes, not votes or EPS denominators

| Measurement date | Filed | Class A | Class B | Total common shares |
| --- | --- | ---: | ---: | ---: |
| 2024-03-31 | 2024-04-26 | 159,483,342 | 101,012,393 | 260,495,735 |
| 2024-06-30 | 2024-08-16 | 159,544,123 | 101,012,393 | 260,556,516 |
| 2024-09-30 | 2024-11-01 | 160,713,952 | 99,995,049 | 260,709,001 |
| 2024-12-31 | 2025-01-31 | 163,081,602 | 98,977,705 | 262,059,307 |
| 2025-03-31 | 2025-05-02 | 165,150,665 | 98,008,049 | 263,158,714 |

Each row is manually curated from the balance sheet's exact issued-and-outstanding share counts, not weighted-average EPS shares. Class B voting power must not multiply its economic share count. The annual report establishes equal per-share dividend and liquidation participation, although voting power differs. Sources: [March 2024 10-Q](https://www.sec.gov/Archives/edgar/data/1650372/000165037224000016/team-20240331.htm), [June 2024 10-K](https://www.sec.gov/Archives/edgar/data/1650372/000165037224000036/team-20240630.htm), [September 2024 10-Q](https://www.sec.gov/Archives/edgar/data/1650372/000165037224000056/team-20240930.htm), [December 2024 10-Q](https://www.sec.gov/Archives/edgar/data/1650372/000165037225000009/team-20241231.htm), [March 2025 10-Q](https://www.sec.gov/Archives/edgar/data/1650372/000165037225000022/team-20250331.htm).

## MDB: outstanding is not issued

The [January 2024 annual report](https://www.sec.gov/Archives/edgar/data/1441816/000144181624000049/mdb-20240131.htm) reports 72,840,692 issued common shares, 72,741,321 outstanding shares, and 99,371 treasury shares. The outstanding count is appropriate here; issued minus treasury reconciles exactly. Do not add a hypothetical second class to an already reported total.

Original saved SEC companyfacts provide further actual outstanding observations, including quarter ends April 30 (73,350,595), July 31 (73,863,712), and October 31, 2024 (74,393,775). Their first available filing dates are May 31, August 30, and December 10 respectively. Cover-date total-common-share observations are kept with their actual measurement dates: May 29 (73,350,695), August 28 (73,869,454), December 6, 2024 (74,470,596), and March 18, 2025 (81,187,663). The latter was filed March 21, 2025 and is not a January 31 observation.

The issuer-hosted 2023 annual report surfaced in search explicitly describes all Class B converting into Class A in June 2020, with no later Class B issuance. That search preview was not separately opened within the six-filing budget; it is semantic corroboration only, not numerical evidence. The inspected 2024 annual balance sheet reports total common stock rather than two outstanding classes.

## Eligibility and provenance

- Require `measurementDate <= decisionDate` and strictly `filedDate < decisionDate`. A filing on the decision date is excluded: particularly TEAM January 31, 2025 and MDB August 30, 2024.
- Prefer the latest eligible measurement date; do not substitute report-period end for a cover-date observation, interpolate, or import later comparative restatements into earlier decisions.
- Saved companyfacts and submissions are original JSON acquired October 18, 2026. Their current SHA-256 hashes are recorded in `tmp/quant-v9-dual-shares-20261030/manifest.json`.
- TEAM exact class counts and MDB's January 2024 annual count were read through the authorized SEC web reader on October 30, 2026. They are explicitly manual curations; no original HTML bytes were downloaded and no raw JSON hash is presented as proof of those manually read counts.
- TEAM's April 26 and August 16, 2024 filings have matching date/accession/form metadata in saved companyfacts but are outside saved submissions' recent range. Archived submissions metadata was not downloaded. Other observation filings match saved recent submissions.
- MDB's January 31, 2025 outstanding count of 80,467,811 appears in saved companyfacts only from June 4, 2025; it is excluded from the older decision range rather than backdated.

Machine-readable observations: `tmp/quant-v9-dual-shares-20261030/observations.json`. Evidence and explicit gaps: `tmp/quant-v9-dual-shares-20261030/evidence.json`. Raw-original acquisition flags refer to the actual supporting source format: MDB companyfacts rows have original JSON bytes; manually curated filing rows do not.

## Focused checks

Verify JSON shape, positive integer units, uniqueness by symbol/measurement/accession, A+B arithmetic, original-input hashes, metadata matches where present, and all eleven decision-date eligibility boundaries. The checks do not establish market-price compatibility, split adjustment, provider licensing for redistribution, contemporaneous data capture, or investment usefulness.
