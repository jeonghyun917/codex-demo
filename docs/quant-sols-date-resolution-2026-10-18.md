# SOLS date conflict: bounded primary-source investigation

## Outcome

**The exact Nasdaq-100 event interval remains unresolved.** The October20/November10 dates are genuinely printed in Nasdaq's retrospective, not a text-extraction error. Newly acquired primary exchange records establish **SOLSV when-issued trading from October20 and a SOLS symbol/regular-way transition on October30, 2025**. They do not prove either index-inclusion date. No contemporaneous Nasdaq index addition/removal notice was found in this bounded public search; no membership correction or official-PIT promotion is justified.

Four freely accessible original documents totaling 539,872 bytes were preserved, within the four-document bound. No DB, application, model, main research script, Git, or shared-checkpoint changes were made by this evidence subtask. Existing dirty `main` files were preserved.

## Checks and evidence

| Evidence | Publication / event distinction | What it establishes |
| --- | --- | --- |
| Reused [Nasdaq January retrospective](https://indexes.nasdaq.com/docs/202601%20NDX%20Reconstitution%20and%20Performance%20Highlights.pdf), p3 | Matching [Nasdaq article](https://www.nasdaq.com/articles/global-indexes/2025-nasdaq-100-reconstitution-and-performance-highlights) displays January9,2026 11:18AM EST | In the actual Effective Date column, SOLS is stated added October20 and removed November10,2025. Visual inspection confirms both SOLS cells occupy the intended row; no shifted-column/OCR explanation. This is a later retrospective, not an event-time notice. |
| [Nasdaq Trader ECA2025-559](https://www.nasdaqtrader.com/TraderNews.aspx?id=ECA2025-559) | Page is dated October9,2025 and titled UPDATED: X/D established; exact update time is not supplied | October20 when-issued SOLSV first trading; October30 payment/ex date and regular-way SOLS trading; same CUSIP 83443Q103. Exchange corporate-action/trading evidence, not Nasdaq-100 membership evidence. |
| [Nasdaq security-status page182](https://www.nasdaqtrader.com/Trader.aspx?from=05%2F21%2F2024&id=nasdaq-security-status-updates&page=182) | Event effective date October20,2025; retrieved current page, not an archived as-of snapshot | Actual security-addition row for SOLSV, described as common stock when issued. A security-listing addition is not an index addition. |
| [Nasdaq security-status page185](https://www.nasdaqtrader.com/Trader.aspx?from=05%2F21%2F2024&id=nasdaq-security-status-updates&page=185) | Event effective date October30,2025 | SOLS appears as a name/symbol change, consistent with the same security's transition from SOLSV. No index action is specified. |
| [Nasdaq spin-off policy conclusion](https://indexes.nasdaq.com/docs/Corporate_Actions_Consultation%E2%80%93Clarification_Regarding_the_Treatment_of_Spin-off_Transactions%20%E2%80%93%20December_2025_Summary_and_Conclusion.pdf) | Consultation concluded January9,2026, change effective immediately | General clarification of spinco removal and notice treatment. No SOLS reference. It cannot be applied retrospectively as proof of an October/November2025 event. |

The reused original PDF SHA256 is `74f20d5f11bbaca127454e9902a59dc9021e1e93803b576e8cdead2710233dd4`. Its rendered page3 is `tmp/quant-sols-resolution-20261018/reused-nasdaq-page3.png`. Both policy pages were also rendered and visually inspected. All relevant page content was read; only the SOLS evidence is used here.

## Refuted explanations and remaining uncertainty

- **Refuted:** The October20/November10 pair arose merely from PDF parsing or row alignment. It is legible in the original table and repeated in Nasdaq's web article.
- **Refuted:** October20 cannot be a meaningful Solstice market date because the spin distribution was October30. Nasdaq explicitly records the earlier when-issued market. Whether that earlier trading milestone also equals index inclusion remains unknown.
- **Not established:** October30 regular-way trading automatically establishes index inclusion. Corporate listing, share distribution, index inclusion, publication time, and provider knowledge time are different fields.
- **Not established:** November6 is an administrator-certified removal effective date, or November5 the final constituent date. The [community table](https://en.wikipedia.org/wiki/Historical_components_of_the_Nasdaq-100) cites a [S&P Capital IQ brief on MarketScreener](https://www.marketscreener.com/news/solstice-advanced-materials-inc-nasdaqgs-sols-dropped-from-nasdaq-100-index-ce7d5fd8d980f625). The observed brief displays publication 11/06/2025 and says removal has occurred; it gives no separate effective timestamp. The community bibliography instead calls it November7. This is not Nasdaq's corporate-action notice. It also does not prove November6 was merely an announcement for November10.

The minimal evidence still required is a dated Nasdaq index notice or publicly authorized historical administrator delivery specifying SOLS/SOLSV entry and exit effectiveness, including before-open versus after-close semantics and original publication timing. Prefer separate `announced_at`, `effective_from`, `effective_until_exclusive`, and `source_observed_at` fields. Until then retain both candidate intervals as competing claims; do not choose silently by source count. Nasdaq article/PDF copies share one lineage.

## Impact on the research windows

**High severity, high confidence:** Exact daily constituent history cannot yet be certified official point-in-time. Both candidate intervals include October31, so the conflict does not by itself change that monthly target's conditional 102-security roster, but agreement at one date does not certify the interval or complete universe.

Date clarification alone cannot repair SOLS's predictor cold-start. The existing seed builder has `MIN_CANDLES = 80` in `HistoricalBacktestSeedService.java`; regular-way October30-31 provides only two sessions through the October31 cutoff. Even using the earlier when-issued market would provide at most ten weekday sessions from October20-31, still below eighty, and requires an explicit validated identity/price-basis policy. Do not backfill Honeywell history into SOLS or silently remove SOLS to manufacture a complete window. This evidence subtask did not alter thresholds or data.

## Acquisition discipline and verification

Original responses and headers are under `tmp/quant-sols-resolution-20261018/`. All four successful raw requests returned HTTP200 using normal Windows TLS verification. Initial sandbox DNS failures had no response bodies; network-authorized retries succeeded. No HTTP403, login, payment, private entitlement, 871(m) dataset, fund holdings, hidden endpoint, or access bypass was used. Public retrieval is not a product-use or redistribution licence, and mutable current pages are not archived proof that the same text was available at the historical cutoff. The policy PDF currently reports HTTP Last-Modified October16,2026; its internal January9 date is not substituted for that transport metadata.

`manifest.json` records identities, response sizes, SHA256 hashes, UTC filesystem save times, and header filenames. `web-reader-observations.json` preserves explicitly curated reader observations, not fabricated raw pages. The offline verifier checks all four HTTP200 headers, all four file hashes in the generated manifest, both actual exchange event rows, exact retrospective date assertions, policy scope/date, and document count. Manual inspection covers the three PDF pages used.

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tmp/quant-sols-resolution-20261018/verify_evidence.py
```

The generated `quality-checks.json` reports `exact_index_dates_resolved: false` and `new_primary_index_event_notices: 0`. No Maven run is warranted for a local evidence-only addition. PDF inspection and data-quality discipline separated the trading lifecycle from index membership and prevented promotion of a plausible explanation into a proven resolution.
