# March 13 foreign-issuer evidence

Six original SEC companyfacts/submissions JSON responses for CCEP, FER and TRI were acquired from normal public `data.sec.gov` endpoints: HTTP200, 3,072,656 bytes total. Each response has a checked issuer CIK, SHA-256, byte size, request/retrieval UTC times, request headers and response headers. These are retrospectively acquired, filing-date-bounded research inputs, not archived point-in-time deliveries.

The sandbox DNS-only failure is retained in the root manifest. The approved normal-network retry succeeded without authentication, payment, proxy, host substitution or access-control workaround. No 403 occurred. Exactly six original JSON files were downloaded; no extra companyfacts, submissions-history files or filing documents were downloaded.

## Sources and observed coverage

The usable raw files and manifest are in `tmp/quant-march-foreign-evidence-20261031/network-approved/`. `coverage.json` retains tag/unit-specific counts and latest observations, including accession, start/end dates, form, filing date and value. Counts are observations, not distinct fiscal periods; comparative facts may repeat across filings. Only `filed < 2026-03-13` and `end < 2026-03-13` qualify. Later-filed comparative history is not backdated.

| Issuer | Latest eligible standard annual revenue | Annual diluted EPS, original unit | Annual filing | Standalone-quarter coverage |
| --- | --- | --- | --- | --- |
| CCEP, CIK1650107 | FY2024 EUR20.438bn | EUR3.08/ordinary share | 20-F, 2025-03-21, accession0001650107-25-000039 | Latest standard Revenue quarter ends2018-06-29; do not portray it as recent2025 quarter |
| FER, CIK1468522 | FY2025 EUR9.627bn | EUR1.24/ordinary share | 20-F, 2026-02-25, accession0001628280-26-011789 | No eligible standalone quarter for standard Revenue |
| TRI, CIK1075124 | FY2025 USD7.476bn | USD3.33/share | 40-F, 2026-03-05, accession0001193125-26-094222 | Latest standard Revenue quarter2025-04-01–2025-06-30, USD1.785bn, 6-K filed2025-08-07 |

Primary raw endpoint patterns: [CCEP facts](https://data.sec.gov/api/xbrl/companyfacts/CIK0001650107.json), [CCEP submissions](https://data.sec.gov/submissions/CIK0001650107.json), [FER facts](https://data.sec.gov/api/xbrl/companyfacts/CIK0001468522.json), [FER submissions](https://data.sec.gov/submissions/CIK0001468522.json), [TRI facts](https://data.sec.gov/api/xbrl/companyfacts/CIK0001075124.json), [TRI submissions](https://data.sec.gov/submissions/CIK0001075124.json).

All three use `ifrs-full:Revenue`, `DilutedEarningsLossPerShare`, `ProfitLossAttributableToOwnersOfParent`, `Assets`, `Liabilities`, `Equity` and `CashFlowsFromUsedInOperatingActivities`. Monetary units are EUR for CCEP/FER and USD for TRI; EPS units are corresponding currency/shares. Instant balance-sheet facts must match the annual accession/end date, never a duration. CCEP2025 half-year Revenue EUR10.274bn (January1–June27, filedAugust7) is not a standalone quarter. FER RevenueFromContractsWithCustomers FY2025 EUR9.516bn is a narrower alternative to Revenue EUR9.627bn, not an interchangeable tag. FER2025 operating income appears under `OperatingProfitLossOperating` EUR131m; the old `ProfitLossFromOperatingActivities` tag ends2024. Do not match stale operating income from another accession onto2025 revenue.

Existing `quant_extended_financials.foreign_rows` is explicitly limited to AZN/GFS, USD and annual20-F, so these are source inputs, not directly accepted output of that function. TRI requires explicitly bounded annual40-F recognition. CCEP/FER annual margins and ratios can remain in consistent source currency; their EUR EPS must not be divided into USD prices without a documented conversion policy and dated FX evidence. Missing comparable EPS stays null under the existing neutral-factor policy. Annual-only issuers must not acquire invented quarterly rows or half-year-as-quarter observations.

## ARM, PDD and ASML price/EPS units

Curated primary-reader observations are saved separately in `share-unit-observations.json`; these are not raw HTML/XBRL or automatically validated filing bytes.

ARM's [FY2025 annual20-F](https://www.sec.gov/Archives/edgar/data/1973239/000197323925000016/arm-20250331.htm) cover confirms one ordinary share per ADS. Its consolidated statements use USD, so verified USD ordinary-share EPS needs no ADS scaling against USD/ADS price. GBP nominal/par value is not earnings currency. The filing's 2025 period and May2025 audit date precede the cutoff.

PDD's [FY2024 annual20-F](https://www.sec.gov/Archives/edgar/data/1737806/000141057825000951/pdd-20241231x20f.htm) cover and Note17 establish four ordinary shares per ADS. Its USD2.60 diluted EPS is per ordinary share, a convenience translation of RMB19.00. Using2.60 against a Nasdaq ADS price overstates PE fourfold. Multiplying the rounded convenience figure by4 gives USD10.40/ADS as a derived rounded proxy, not an observed precise ADS EPS. The same annual earnings table and Note17 contain ordinary-share EPS, and reader searches for10.40,10.41 and76.01 found no direct USD diluted per-ADS figure. This is bounded reader evidence, not an exhaustive raw-HTML parse. The existing DB context supplies filedApril28,2025; this task did not download PDD submissions to independently verify that date. Leave comparable EPS missing if the runner does not already support explicit, provenance-bearing ADS conversion; do not relabel unscaled2.60. No later2025 annual results were used.

FER's downloaded `OperatingProfitLossOperating` object has null label and null description. Its name/value are recorded, but equivalence to the older operating-income tag is not independently established by taxonomy definition in this task; normalization approval remains with the owning runner. Do not infer equivalence merely from similar tag spelling.

ASML's [2025 issuer annual highlights](https://www.asml.com/investors/annual-report/2025) show EUR24.73 basic ordinary EPS; its [listing page](https://www.asml.com/en/investors/shares) distinguishes EUR Amsterdam and USD Nasdaq trading. The supplied DB value24.71 is diluted EUR EPS, not USD. The [SEC annual document](https://www.sec.gov/Archives/edgar/data/937966/000162828026011378/asml-20251231.htm) was found but its reader exceeded the size budget: this task does not independently verify24.71 from acquired raw bytes. With no dated EUR/USD conversion evidence, keep USD-price-comparable EPS null rather than silently mixing currencies. Unit evidence from the current listing page is corroboration, not proof of historical page availability.

## Verification and scope

Run `python -X utf8 tmp/quant-march-foreign-evidence-20261031/verify_evidence.py` for offline hash/CIK/HTTP/metadata/cutoff checks and independent matching of the three selected annual accessions to submissions filing/report dates. Acquisition refuses completed artifact overwrite. No database, product model, shared extractor, Git, deployment or server state was changed. Model validity, precise FX conversion and recent standalone quarters remain outside what these files establish.
