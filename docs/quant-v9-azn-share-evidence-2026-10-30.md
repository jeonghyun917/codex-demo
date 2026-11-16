# AZN historical ordinary-share evidence for V9

Prepared 2026-10-30. Evidence-only work: no database, application, model, or Git changes.

## Accepted observations and actual availability

| Measurement | Ordinary shares | Contemporaneous filing | Filed | First calendar decision date under strict prior-day rule |
|---|---:|---|---|---|
| 2023-12-31 | 1,550,162,626 | [SEC 6-K voting rights](https://www.sec.gov/Archives/edgar/data/901832/000165495424000032/a3874y.htm) | 2024-01-02 | 2024-01-03 |
| 2023-12-31 | 1,550,162,626 | [SEC 2023 Form 20-F](https://www.sec.gov/Archives/edgar/data/901832/000110465924025553/azn-20211231x20f.htm) | 2024-02-20 | 2024-02-21 |
| 2024-12-31 | 1,550,546,239 | [SEC 6-K voting rights](https://www.sec.gov/Archives/edgar/data/901832/000165495425000031/a9281r.htm) | 2025-01-02 | 2025-01-03 |

The annual filing independently confirms the 2023 count using its contemporaneous cover and Note 24, not a later comparative. The unusual 20211231 filename is not its reporting period: the actual document and submissions metadata identify fiscal 2023. Its cover reports outstanding ordinary shares separately from preference shares.

Within the requested V9 month-end interval, the 2023 observation is available for 2024-07-31, 2024-08-30, 2024-09-30, 2024-10-31, 2024-11-29 and 2024-12-31. The 2024 year-end observation is available for 2025-01-31, 2025-02-28, 2025-03-31, 2025-04-30 and 2025-05-30. This is availability coverage, not proof of exact contemporaneous shares at those decisions. No monthly 2024 updates were acquired within this bounded three-filing review.

## Basis and caveats

- Counts are actual ordinary shares, not diluted/basic EPS weighted-average shares, ADSs, or preference shares. Both voting-rights reports state zero treasury shares.
- Preserve the issuer's gross issued/outstanding basis. The already identified [2024 annual Note 24](https://www.astrazeneca.ch/content/dam/az/Investor_Relations/annual-report-2024/pdf/AstraZeneca_AR_2024_Financial_Statements.pdf) separately describes 442,342 shares held by controlled Employee Benefit Trust arrangements. That is a definition caveat, not grounds to silently subtract them or substitute EPS shares.
- Enforce filedDate < decisionDate, never <=. The 6-K announcement/report date is not the share measurement date. SEC metadata supplies accession, filing and acceptance dates; text supplies measurement date.
- The annual 2023 cover independently identifies the historical ratio as one ADS per one-half ordinary share. Under the owning task's separately established Yahoo ordinary-equivalent price basis, use these ordinary shares unscaled with ordinary-equivalent closes. Do not double them and do not claim the archived series proves as-traded historical ADS prices.
- These are curated primary SEC web-reader observations. Original filing bytes were not acquired or hashed. A direct 2023 annual HTML download failed DNS resolution; no retry or access bypass was performed. Every JSON observation has rawOriginalSourceAcquired=false.

## Reproducible handoff

- `tmp/quant-v9-azn-shares-20261030/observations.json`: three provenance-bearing records, two unique measurement-date/count pairs.
- `tmp/quant-v9-azn-shares-20261030/manifests/source-manifest.json`: exact accession/filing/acceptance metadata and acquisition limitations.
- `tmp/quant-v9-azn-shares-20261030/evidence/curated-reader-observations.md`: inspected passages and interpretation.
- Metadata was matched read-only against the existing `tmp/quant-extended-inputs-20261018/AZN-submissions.json` archive. This does not turn reader-derived contents into raw original-source acquisition.

This evidence supports a point-in-time available share-denominator fallback. It is not evidence of V9 model quality, model profitability, a daily share register, or a new as-traded price series.
