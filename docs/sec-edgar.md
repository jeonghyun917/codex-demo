# SEC EDGAR 데이터 수집

SEC EDGAR는 API 키 없이 사용할 수 있는 공식 원천 재무 데이터입니다. Finnhub가 quote, profile, metric 같은 요약 데이터를 제공한다면, SEC EDGAR는 회사별 XBRL 재무 facts를 제공합니다.

## 목적

- `ticker -> CIK` 매핑을 저장한다.
- CIK 기준 `companyfacts`를 호출해 재무제표 원천 facts를 저장한다.
- SEC facts를 표준 재무 항목으로 매핑한다.
- Quant AI와 Signal 재계산은 요청 시 외부 API를 호출하지 않고 DB 저장값을 읽는다.

## 테이블

### `stock_sec_company`

SEC의 회사 식별자 테이블입니다.

- `symbol`: 우리 서비스의 종목 코드
- `cik`: SEC 회사 고유 번호, 10자리 zero-padding 형식
- `ticker`: SEC ticker
- `company_name`: SEC 회사명
- `source`: 매핑 출처
- `fetched_at`: 매핑 수집 시간

### `stock_sec_fact`

SEC XBRL companyfacts에서 추출한 원천 facts입니다.

- `symbol`, `cik`
- `taxonomy`: 보통 `us-gaap`, 일부 식별자성 항목은 `dei`
- `concept`: XBRL concept 이름
- `unit`: `USD`, `shares`, `USD/shares` 등
- `fiscal_year`, `fiscal_period`
- `form`: `10-K`, `10-Q` 등
- `filed_at`, `start_date`, `end_date`
- `accession_number`
- `value`
- `raw_json`
- `fetched_at`

### `stock_sec_financial_standard`

Signal 계산에 쓰기 위해 SEC facts를 표준 재무 항목으로 정리한 테이블입니다.

- `revenue`: 매출
- `operating_income`: 영업이익
- `net_income`: 순이익
- `eps_diluted`: 희석 EPS
- `assets`: 자산
- `liabilities`: 부채
- `equity`: 자본
- `operating_cash_flow`: 영업현금흐름

## 수집 concept

기본 모드는 Signal에 바로 쓸 수 있는 핵심 항목만 저장합니다.

- 매출: `Revenues`, `RevenueFromContractWithCustomerExcludingAssessedTax`, `SalesRevenueNet`
- 원가/매출총이익: `CostOfRevenue`, `CostOfGoodsAndServicesSold`, `GrossProfit`
- 영업/순이익: `OperatingIncomeLoss`, `NetIncomeLoss`, `NetIncomeLossAvailableToCommonStockholdersBasic`
- EPS/주식수: `EarningsPerShareBasic`, `EarningsPerShareDiluted`, `WeightedAverageNumberOfDilutedSharesOutstanding`
- 재무상태: `Assets`, `AssetsCurrent`, `Liabilities`, `LiabilitiesCurrent`, `StockholdersEquity`
- 현금/현금흐름: `CashAndCashEquivalentsAtCarryingValue`, `NetCashProvidedByUsedInOperatingActivities`
- 부채/CapEx: `LongTermDebtAndFinanceLeaseObligationsCurrent`, `LongTermDebtAndFinanceLeaseObligationsNoncurrent`, `PaymentsToAcquirePropertyPlantAndEquipment`
- SEC 식별자: `dei.EntityCommonStockSharesOutstanding`

`sec.edgar.full-concept-enabled=true`로 실행하면 위 목록으로 제한하지 않고 SEC companyfacts에 포함된 모든 taxonomy/concept를 저장합니다. 일반 Signal 계산 목적이면 기본 핵심 concept 모드를 먼저 사용합니다.

## 표준 재무 매핑

수집 후 `SecFinancialStandardService`가 `stock_sec_fact`를 `stock_sec_financial_standard`로 정리합니다.

매핑은 concept 우선순위와 기간 길이를 같이 봅니다. 예를 들어 분기 손익 항목은 6개월 누적값보다 3개월 duration 값을 우선하고, 연간 항목은 약 1년 duration 값을 우선합니다. 자산, 부채, 자본은 해당 보고서의 instant 값을 붙입니다.

## 실행 방식

SEC 수집은 기본적으로 꺼져 있습니다. 필요할 때 배치로 실행합니다.

```cmd
run-sec-edgar-batch.cmd
```

직접 실행 예:

```cmd
mvn spring-boot:run -Dspring-boot.run.profiles=mariadb -Dspring-boot.run.arguments="--app.batch.sec-edgar.enabled=true --app.batch.sec-edgar.exit-on-complete=true --app.batch.sec-edgar.symbol-limit=1000 --app.batch.sec-edgar.index-codes=SP500,NASDAQ100,DOW30 --app.signal.refresh.enabled=false --spring.main.web-application-type=none"
```

특정 종목만 직접 수집:

```cmd
mvn spring-boot:run -Dspring-boot.run.profiles=mariadb -Dspring-boot.run.arguments="--app.batch.sec-edgar.enabled=true --app.batch.sec-edgar.exit-on-complete=true --app.batch.sec-edgar.symbols=NVDA,AAPL,MSFT --app.batch.sec-edgar.symbol-limit=3 --app.signal.refresh.enabled=false --spring.main.web-application-type=none"
```

## 설정

- `sec.edgar.base-url`: `https://data.sec.gov`
- `sec.edgar.files-base-url`: `https://www.sec.gov`
- `sec.edgar.user-agent`: SEC 요청 User-Agent
- `sec.edgar.company-facts-cache-hours`: 같은 종목 companyfacts 재수집 캐시 시간
- `sec.edgar.max-facts-per-concept`: concept/unit별 최근 저장 개수
- `sec.edgar.request-delay-millis`: SEC 요청 사이의 딜레이
- `sec.edgar.full-concept-enabled`: 모든 SEC concept 저장 여부
- `app.batch.sec-edgar.index-codes`: 기본 수집 지수. 현재 `SP500,NASDAQ100,DOW30`
- `app.batch.sec-edgar.symbol-limit`: 수집 상한. 현재 `1000`
- `app.batch.sec-edgar.symbols`: 쉼표로 구분한 직접 수집 대상. 값이 있으면 `index-codes`보다 우선합니다.
- `app.batch.sec-edgar.rebuild-standard`: SEC를 다시 호출하지 않고 저장된 facts 기준으로 표준 재무 테이블을 재생성할지 여부

운영에서는 `SEC_EDGAR_USER_AGENT`를 실제 연락 가능한 값으로 바꾸는 게 좋습니다.

## Signal 반영

화면은 계속 `stock_signal_latest`만 조회합니다. SEC EDGAR 배치가 실행되면 SEC facts 수집, 표준 재무 매핑, 해당 종목 Signal 재계산 순서로 진행됩니다.

현재 반영 방식:

- Valuation: Finnhub `psTTM`이 없으면 SEC 연간 매출과 Finnhub 시가총액으로 PSR을 계산합니다.
- Quality: Finnhub ROE/EPS/순이익률이 없으면 SEC 연간 순이익, 자본, EPS, 매출로 보조 계산합니다.
- Earnings: Finnhub EPS surprise가 없으면 SEC 최근 분기 매출/순이익 성장률로 대체 신호를 만듭니다. EPS surprise가 있으면 SEC 분기 성장률을 보정값으로 사용합니다.
- `source_freshness_json`에는 SEC 연간 filing 기준일도 저장합니다.

SEC EDGAR는 Quant AI의 원천 데이터 보강 레이어입니다.
## DB-only rebuild note

Use `app.batch.sec-edgar.sync-enabled=false` with
`app.batch.sec-edgar.rebuild-standard=true` when you want to rebuild
`stock_sec_financial_standard` and refresh `stock_signal_latest` from existing
`stock_sec_fact` rows without calling SEC again.

The normal collection script keeps `sync-enabled=true` because it is intended
to refresh CIK mappings and companyfacts from SEC.

`stock_sec_financial_standard.currency` stores the SEC monetary unit selected
for the standardized row. Non-USD rows can still support same-currency ratios
such as net margin and ROE, but Stock Signal does not use non-USD SEC revenue
as a PSR fallback against Finnhub USD market cap.
