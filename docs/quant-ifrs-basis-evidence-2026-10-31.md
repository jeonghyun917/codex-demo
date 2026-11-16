# IFRS period and quoted-share basis evidence — October 31, 2026

The offline registry independently rechecked all 507 archived IFRS observations across 16 accessions and 75 historical decision/accession references. It also reran exact primary inline-XBRL comparison for the two archived GFS documents: 67 matched observations, zero unmatched values and zero selected inline parse errors. This is source evidence, not a new import, historical receipt proof, feature acceptance, or an improved-error claim. This subtask made no network requests, database writes, Git changes, or production Java changes.

## Period evidence

Original observation `start`, `end`, `filed`, `fy`, `fp`, `frame`, units and taxonomy remain unchanged in `candidates.json`. `normalizedCalendarRange` is a separate range interpretation, not authority to override a native parser's stricter fiscal-period selection. Its fiscal-labelled fields are calendar-range candidates only. Native quarter approval is separately represented by `periodEvidence.exactQuarterFrameProven`, requiring both an exact calendar-quarter date range and its matching original `CYyyyyQn` frame. An instant never manufactures an income-statement period. Half-year and nine-month values are never relabelled as quarters or subtracted to invent standalone values.

SEC `fy`/`fp` describe filing-level reporting context and must not label every observation. Annual filings include prior-year comparative facts carrying the current filing year. GFS 6-K observations often have null `fy`/`fp`. AZN and TRI 6-K observations marked `fp=Q2` include half-year and actual standalone-quarter ranges together. The regression fixture also covers a quarter carrying `fp=FY`; this is a tested edge case, not a claim that such a row occurs in this selected archive.

All affected decisions are strictly later than the original accession filing date. This date-only gate excludes same-day filings; retrospective retrieval is not original point-in-time possession evidence.

| Issuer | Archived observations | Actual quarter observations | Matching exact quarter frame | Quarter observations lacking that proof |
| --- | ---: | ---: | ---: | ---: |
| GFS | 266 | 74 | 62 | 12 |
| AZN | 132 | 20 | 15 | 5 |
| CCEP | 28 | 0 | 0 | 0 |
| FER | 30 | 0 | 0 | 0 |
| TRI | 51 | 12 | 12 | 0 |

These are fact counts, including multiple concepts, duplicate revenue concepts and comparative periods; not statement or accepted-feature counts. Missing frames remain explicit, not synthesized. Exact annual January–December ranges are recorded separately. Instant balance-sheet facts still require same-accession/end coherence with a proven statement duration in native integration.

## Quoted-security and currency basis

GFS FY2024's original annual cover identifies ordinary shares, symbol GFS, on The NASDAQ Global Select Market. That is genuine non-ADS listing evidence, filed March 20, 2025. The phrase `par value US$0.02` is a nominal share-capital denomination, not by itself proof of the currency of a market quote. This registry therefore does not combine GFS EPS and a stock price, and does not backdate the cover proof to older decisions.

AZN and TRI selected facts use USD and USD/shares; CCEP and FER use EUR and EUR/shares. A USD/shares unit alone does not prove whether the quoted security matches an ordinary share, ADS, or another share basis. AZN's ADS ratio has not been independently proved by the two available GFS primary documents; it remains unresolved here. The registry approves zero price/EPS rows for every issuer. CCEP/FER additionally require an appropriate dated quote-currency/FX basis. No ADS ratio, FX conversion, synthetic EPS, or price-currency inference was introduced.

Same-unit dimensionless growth comparisons can be candidates without converting currency, provided native selection additionally proves the same concept, period, original filing eligibility and appropriate statement coherence. That possibility does not bless price/EPS valuation or bypass the native period gate.

## Accession-level support

| Issuer/accession | Form | Observations | Quarter observations / matching frame | Outcome |
| --- | --- | ---: | ---: | --- |
| GFS 0001709048-23-000047 | 6-K | 36 | 12 / 12 | Half-year separate; quarter frame evidence available |
| GFS 0001709048-23-000058 | 6-K | 36 | 12 / 6 | Six quarter facts remain unproved under strict frame gate |
| GFS 0001709048-24-000013 | 20-F | 31 | 0 / 0 | Annual ranges and instant facts |
| GFS 0001709048-24-000063 | 6-K | 36 | 12 / 6 | Six quarter facts remain unproved under strict frame gate |
| GFS 0001709048-25-000024 | 20-F | 31 | 0 / 0 | All 31 primary-document matched; ordinary listing cover |
| GFS 0001709048-25-000042 | 6-K | 24 | 14 / 14 | Quarter frame evidence available |
| GFS 0001709048-25-000057 | 6-K | 36 | 12 / 12 | Half-year separate; quarter frame evidence available |
| GFS 0001709048-25-000071 | 6-K | 36 | 12 / 12 | All 36 primary-document matched; nine-month separate |
| AZN 0001104659-24-025553 | 20-F | 31 | 0 / 0 | Annual ranges and instant facts |
| AZN 0001104659-24-082531 | 6-K | 35 | 10 / 5 | Five quarter facts remain unproved under strict frame gate |
| AZN 0001104659-25-014750 | 20-F | 31 | 0 / 0 | Annual ranges and instant facts |
| AZN 0001104659-25-071432 | 6-K | 35 | 10 / 10 | Half-year separate; quarter frame evidence available |
| CCEP 0001650107-25-000039 | 20-F | 28 | 0 / 0 | Annual EUR ranges; price/EPS basis blocked |
| FER 0001628280-26-011789 | 20-F | 30 | 0 / 0 | Annual EUR ranges; price/EPS basis blocked |
| TRI 0000950170-25-105085 | 6-K | 32 | 12 / 12 | Half-year separate; quarter frame evidence available |
| TRI 0001193125-26-094222 | 40-F | 19 | 0 / 0 | Annual USD ranges; share/price basis unresolved |

All rows still require native financial normalization and regression evidence. No accession is automatically accepted merely because it appears in this table. Source URLs, filing dates, report dates, affected decisions and original units are in the machine-readable accession file.

## Exact evidence and reproduction

Output directory: `tmp/quant-ifrs-basis-20261031-v1/`.

- `manifest.json` SHA256: `7522b4d82cd82cc2955172659e60b484cdd97e21656c11eba17d945754a3bb2d`.
- `candidates.json` SHA256: `c94a97dd339e2ae88647cb79e6f6f946d508f6bd983d778c92bff006b09ce7e8`.
- `accession-support.json` SHA256: `efc7c245caaea51bc111c4c5f8e22228725d5c1456ab0d5896da338d591bef0f`.
- `issuer-basis.json` SHA256: `064a44248363135add5c1522a53a9ceb9663684e9f9fd58f893c830f97922c91`.
- `source-reverification.json` SHA256: `7c279226a892f1505bb6f839f8b62d967735acceda7e2dde50b5d50ce2a8b067`.

The unchanged input queue manifest SHA256 is `0ac4a7e14576a2a6bc8c0dccff0a0aa0b736e817e395d249b1577d3b4edead58`. The output manifest pins all ten original companyfacts/submissions file hashes and all registry outputs. The original GFS FY2024 HTML SHA256 is `723c6ea56a78e47b26c3fce4349100fd913786e2a4f009a394181aa0683ca937`; the Q3 2025 HTML SHA256 is `e4affbb0ff20b6441c272c7b96f9132867e9d865730bc6a52741f04ff00e7371`. Both remain in the input queue directory with their original request/retrieval receipts.

```powershell
python scripts/quant_ifrs_basis.py --output tmp/quant-ifrs-basis-20261031-rerun
python -m unittest discover -s scripts -p test_quant_ifrs_basis.py -v
```

The builder is offline and refuses existing output directories. Thirteen focused tests pass, including exact archive integration, comparative-year correction, half-year/nine-month separation, quarter frame mismatch/missing-frame rejection, leap year/Q4, invalid/reversed/future ranges, strictly-prior decisions and blocked quote/share basis. Eight initial behavior tests were observed failing before implementation and then passing. Full application/Maven verification belongs to the coordinating native-integration task, which owns application changes and replay/error conclusions.
