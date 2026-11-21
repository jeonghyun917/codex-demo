# Free official macro evidence follow-up — 2026-10-18

## Result

무료·무계정 공개 경로로 **276개 원본 파일 / 1,712,306바이트**를 추가 확보했습니다.
핵심은 공식 ALFRED 빈티지 CSV **266개**와 관측값 **59,368행**입니다. 기존에 최신 수정치와
구별되는 원문 증거가 부족했던 과거 거시지표를 보강했습니다. 운영 DB, 동결한 연구 namespace,
기존 V2–V9 결과와 엄격한 PIT 승격 조건은 변경하지 않았습니다. 전체 모델을 재계산하지 않았습니다.

현재 무료 원문에서도 나스닥 지수의 2026-10-16 값은 확보되지 않았습니다. 공식 구성종목 전체
기준일 명부·완전한 변경 이력, 상장폐지 종목 가격, 다른 역사적 특성의 실제 발표시각 증명은
이 작업으로 해결되지 않았습니다. 실전 검증의 `INSUFFICIENT_DATA` 판정은 그대로입니다.

## What was actually acquired

Root: `tmp/free-quant-followup-20261018/`.

| Source/output | Verified contents | Interpretation |
| --- | --- | --- |
| Official ALFRED graph CSVs | 7 series × 38 as-of days = 266 files | Archived day-level values, not invented release timestamps |
| Historical as-of snapshots | 57,809 numeric rows; 37 decision dates, 2023-04-28–2026-03-27 | Each uses the previous calendar day's vintage |
| 2026-10-17 comparison snapshots | 1,559 numeric rows | Latest comparison only; never substituted into history |
| Official source/rights documents | 7 FRED series pages + ALFRED home/help = 9 files | Publisher notes retained alongside raw values |
| FRED Nasdaq-100 bounded re-probe | 1 CSV; 2026-10-15 = 29,935.590; October 16 absent | Missing benchmark remains missing |

Configured series were read from the application: `DGS3MO`, `DGS10`, `FEDFUNDS`, `CPIAUCSL`,
`UNRATE`, `VIXCLS`, `DTWEXBGS`. Decision dates came from a read-only distinct-date query against
the already completed `SP500_FREE_20261018` and `NASDAQ100_FREE_20261018` predictions, bounded
to March 2026. All 37 selected historical decisions have all seven series snapshots. This is not
coverage of all later LIVE dates or a new evaluation sample.

Each request asks for an 18-month observation window. Monthly periods can start before the exact
calendar-day window boundary. Returned observation dates span 2021-10-01–2026-10-15. Counts above
are **series/observation/as-of triples**, not independent economic releases: the same observation
appears in multiple vintages. There are 5,179 distinct series/observation pairs.

Acquisition timestamps: `2026-10-18T00:49:54.097891Z`–`2026-10-18T00:50:59.907565Z`.
Each source has its exact URL, final URL, timestamp, byte count, HTTP status and SHA-256 in
`manifest.json`, with per-file metadata beside the immutable raw download.

The successful no-key route is, for example,
[ALFRED CPI as of 2023-04-27](https://alfred.stlouisfed.org/graph/alfredgraph.csv?id=CPIAUCSL&vintage_date=2023-04-27&cosd=2021-10-27&coed=2023-04-27).
The returned column is explicitly vintage-tagged (`CPIAUCSL_20230427`), which the parser checks.
ALFRED describes its purpose as retrieving data available on a historical date, and explains that
its release dates can come from the original source, a provider, or FRED's first availability date.
It does not universally provide source-specific intraday availability. [Official ALFRED help](https://alfred.stlouisfed.org/help).

## Quality findings and safeguards

The data-quality workflow changed the handoff: downloaded graph snapshots are not labeled as a
complete realtime event history or automatically imported as certified PIT features.

- All 276 saved source hashes and sizes matched; all 59,368 derived numeric rows matched raw
  values and source identities exactly. Duplicate keys, future observation dates and raw/derived
  mismatches: **0**. All numeric values are finite.
- **2,090 missing raw observations** remain in the originals and are omitted from numeric staging,
  not zero-filled or carried forward. Latest available observation dates differ by series: the
  October 17 dollar-index snapshot ends October 9, while rates/VIX end October 15. These lags
  remain visible in `profile.json` and must not be presented as same-day freshness.
- Among **6,994 historical-as-of rows** whose observation dates also occur in the October 17
  comparison, **1,054 differ**: CPI 42, unemployment 13, broad dollar index 999. These are 203
  distinct series/observation pairs; repeated vintages are not counted as independent releases.
  This directly demonstrates revision risk in substituting current data into past decisions.
  Example: April 2025 CPI is 320.321 in the 2025-05-29 vintage and 320.302 in the 2026-10-17
  comparison. The source identifies CPI's seasonally adjusted monthly definition.
  [Official CPI series](https://fred.stlouisfed.org/series/CPIAUCSL).
- Date selection is conservative: `as_of_date = decision_date - 1 calendar day`. No announcement
  hour, original publication date, full `realtime_start/end` interval or intervening daily state
  is inferred from a graph snapshot. New data released on decision day are intentionally absent.
- Public access is not a blanket redistribution license. Series metadata/attribution is retained;
  VIX includes third-party CBOE rights. No account, payment, key, new license acceptance, paid
  provider or access-control bypass was used. [Official VIX notes](https://fred.stlouisfed.org/series/VIXCLS).

## Staging candidate, not an import claim

`derived/macro_asof_snapshots_RESEARCH_ONLY.csv` contains 59,368 rows with
`series_code,observation_date,as_of_date,value,source_id`. The separate 37-row
`decision_asof_map.csv` defines the exact previous-day mapping. Keeping `as_of_date` distinct
from `vintage_date`/`realtime_start` deliberately prevents this file from being a drop-in input
to the application's existing macro CSV importer: the current importer requires realtime
validity fields and also updates risk-free observations. Do not rename headers to bypass that.

The smallest safe future integration is an explicitly new research dataset that reads these
snapshots by their exact as-of mapping and records the conservative lag policy. It would need its
own validation and must not overwrite `SP500_FREE_20261018`, `NASDAQ100_FREE_20261018`, operating
macro snapshots or risk-free rows. This task did not execute that integration, retrain models,
rerun research metrics, or certify source publication times. Obtaining better macro inputs cannot
retroactively turn reconstructed predictions into recorded-at-decision LIVE forecasts.

## Reproducibility and verification

Acquisition: `scripts/acquire_quant_macro_evidence.py`. It uses a read-only date inventory query,
bounded four-worker public downloads and integrity-checked local caching. It does not execute
downloaded source code or contain a database mutation. Independent offline verification:
`scripts/verify_quant_macro_evidence.py`.

```powershell
python -m unittest discover -s src/test/python -p test_acquire_quant_macro_evidence.py -v
python scripts/verify_quant_macro_evidence.py tmp/free-quant-followup-20261018
```

Focused tests: **5 passed**. They check vintage-tagged headers, missing-value preservation,
precision, invalid/future/duplicate observations and leap-date lookbacks. The independent
verifier rereads every raw CSV and compares its complete key/value/source map against staging,
as well as all source/output/profile hashes. Result: `VERIFIED`, 276 sources, 59,368 rows,
37 historical decisions. Detailed evidence is in `verification.json`; acquisition has **0 failures**.
No Maven run, DB write, Java/product-code change, Git staging/commit/push or service restart
occurred in this data-acquisition workstream.

| Artifact | SHA-256 |
| --- | --- |
| `manifest.json` | `f40f6eb8f1682bd8ef71f9b0b1997f88d2f3e32801c7d3c903560742e72e591b` |
| `derived/macro_asof_snapshots_RESEARCH_ONLY.csv` | `02f7775aed5e8e19403175dc90ec889aad73856a473502f81410d9977f2a6700` |
| `derived/decision_asof_map.csv` | `7c3c32840e130912b910f450ee63745e65d4fb45f5348c4feacee9717bc821bc` |
| `derived/revision_comparison.csv` | `4ac069d18cb965ed0431e6bc344522d5184a831f5b657466ce0df03d7bc86460` |

The separate server cold-request fix belongs to the main workstream and is not claimed here.
