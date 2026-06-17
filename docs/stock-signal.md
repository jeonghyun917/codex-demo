# Stock Signal

## 원칙

Signal은 화면 요청 중 계산하지 않고 DB에 저장된 최신 값을 조회한다.

`/stocks` 리스트 화면과 `/stocks/{symbol}` 상세 화면은 `stock_signal_latest` 기준으로 표시한다. 요청 중에는 종목별 `valuation`, `quality`, `growth`, `stability`, `earnings`, `analyst`, `news`, `momentum`, `risk`, `institution` 계산을 반복하지 않는다.

검증용 이력은 `stock_signal_snapshot`에 저장한다. 백테스트 화면은 `stock_signal_latest`가 아니라 snapshot과 candle 기반 결과 테이블을 사용한다. 자세한 내용은 `docs/stock-backtest.md`를 본다.

## 저장 테이블

최신 Signal은 `stock_signal_latest`에 저장한다.

주요 컬럼:

- `symbol`: 종목 코드
- `calculated_at`: Signal 계산 시각
- `signal_version`: 계산 로직 버전
- `integrated_score`, `integrated_label`, `tone`, `confidence`: 통합 Signal 결과
- `valuation_score`, `quality_score`, `growth_score`, `stability_score`, `earnings_score`, `analyst_score`, `news_score`, `momentum_score`, `risk_score`, `institution_score`: 10개 항목 점수
- `reasons_json`, `cards_json`: 상세 화면 근거와 카드 데이터
- `source_freshness_json`: 계산에 사용한 원천 데이터 기준 시각
- `raw_json`: 디버깅용 원본 계산 결과

기존 `stock_analysis_snapshot`은 과거 3개 신호 구조용 테이블이며, 현재 Signal 표시 기준 테이블은 `stock_signal_latest`다.

## Quant Signal v2

`signal_version=quant-v2`는 10개 핵심 Signal 원점수와 factor ranking을 함께 사용한다.

1차 원점수는 종목별로 계산한다.

- Valuation: PER, PBR, PSR, 시가총액, 업종 평균 PER 비교
- Quality: ROE, EPS, 순이익률, SEC 표준 재무 보조값
- Growth: SEC 연간 매출/순이익 성장률, 최근 분기 매출/순이익 성장률
- Stability: 유동비율, 부채/자본, 부채/자산, 영업현금흐름률
- Earnings: EPS surprise, SEC 분기 매출/순이익 성장률 보정
- Analyst: 애널리스트 추천 추세
- News: 최근 뉴스 제목/요약 기반 룰 점수
- Momentum: quote, 52주 위치, SMA20, 20일 추세, RSI14
- Risk: 일봉 변동성, 최근 고점 대비 낙폭, 52주 위치
- Institution: 13F 기관 보유 수량/평가금액/보유 기관 수 변화

2차 최종 점수는 같은 재계산 대상 안에서 factor ranking을 적용한다.

- Quality 17%
- Valuation 14%
- Growth 14%
- Earnings 13%
- Stability 9%
- Momentum 8%
- Risk 8%
- Analyst 7%
- Institution 6%
- News 4%

원점수만 쓰면 시장 전체가 양호한 날 대부분 종목이 높게 표시될 수 있다. 그래서 `StockSignalRefreshService`는 한 번에 재계산되는 종목들의 10개 항목별 백분위 순위를 계산하고, 원점수 35% + factor ranking 65%로 최종 `integrated_score`를 저장한다.

품질, 실적, 밸류에이션, 안정성, 리스크, 애널리스트 점수가 극단적으로 약한 종목은 guardrail로 최종 점수 상단을 제한한다. 리스트 화면의 `Signal`은 이 최종 `integrated_score`다.

## Weight Profile

Signal Weight Profile은 검증용 가중치 프로필이다.

현재 프로필:

- `DEFAULT`: 현재 live Signal 기준 프로필
- `BACKTEST_V1`: Factor Performance Diagnostics로 만든 후보 프로필

`BACKTEST_V1`은 `stock_signal_weight_profile.active=false`로 저장한다. 따라서 `/stocks`와 `/stocks/{symbol}`은 여전히 `stock_signal_latest.integrated_score`만 조회한다.

`/signals/backtest`만 `BACKTEST_V1`을 사용해 가상의 Signal을 계산하고, `DEFAULT`와 5D/20D/60D 성과를 비교한다. 충분히 개선되는지 검증하기 전에는 live Signal 계산에 연결하지 않는다.

## 데이터 신뢰도

`confidence`는 Signal 방향의 강도가 아니라 데이터 완성도다.

예를 들어 점수가 26점이면 하향 리스크가 강하다는 뜻이지만, 10개 원천 중 충분한 데이터가 있으면 `데이터 신뢰도 보통` 또는 `데이터 신뢰도 높음`이 나올 수 있다.

별도로 `stock_data_quality_latest`는 원천 데이터 자체의 품질을 저장한다. Signal의 `confidence`는 사용 가능한 Signal 항목 수를 기준으로 삼고, 데이터 품질 점수가 낮으면 `데이터 품질 주의` 또는 `데이터 품질 낮음`으로 낮춰 표시한다.

기준:

- 8개 이상 사용 가능: 데이터 신뢰도 높음
- 6개 이상 사용 가능: 데이터 신뢰도 보통
- 4개 이상 사용 가능: 데이터 신뢰도 낮음
- 3개 이하: 데이터 신뢰도 매우 낮음

## 데이터 품질 레이어

데이터 품질 레이어는 `StockDataQualityService`가 담당한다.

원칙:

- 원천 데이터는 수정하지 않는다.
- 이상치나 충돌이 있는 값은 `excluded_fields_json`에 기록하고 분석 계산에서만 제외한다.
- 품질 점수는 `stock_data_quality_latest`에 저장한다.
- 상세 화면은 `stock_data_quality_latest` 기준으로 품질 점수, 커버리지, 최신성, 제외 필드 수, 주요 이슈를 보여준다.
- 리스트 화면도 `stock_data_quality_latest`를 조인해 품질 점수를 표시한다.

저장 테이블:

- `stock_data_quality_latest.symbol`: 종목 코드
- `quality_score`, `quality_label`, `tone`: 종합 품질 점수와 표시 상태
- `coverage_score`: profile, quote, metric, SEC annual, SEC quarter, candle, news, recommendation, EPS, 13F 보유 데이터 커버리지
- `freshness_score`: quote, metric, candle, SEC, news의 오래된 데이터 감점
- `outlier_score`: PER, PBR, PSR, ROE, EPS, 순이익률, 52주 범위 등의 이상치 감점
- `consistency_score`: Finnhub, SEC, Yahoo candle, quote 간 충돌 감점
- `excluded_metric_count`: 분석 계산에서 제외된 필드 수
- `stale_sources_json`, `excluded_fields_json`, `issues_json`: 화면과 디버깅용 상세 근거

현재 제외 룰:

- `peNormalizedAnnual`: 0 이하 또는 300배 이상
- `pbAnnual`: 0 이하 또는 100배 이상
- `psTTM`: 0 이하 또는 100배 이상
- `roeTTM`: 절대값 300% 초과
- `epsTTM`: 절대값 1000 USD 초과
- `netProfitMarginTTM`: 절대값 100% 초과
- `currentRatioQuarterly`: 0 이하 또는 20배 이상
- `totalDebt/totalEquityQuarterly`: 절대값 1000% 초과
- `52WeekHigh`, `52WeekLow`: 0 이하이거나 고가가 저가 이하

충돌 검증:

- `company_profile.market_cap`과 `quote.current_price * share_outstanding` 차이가 60% 초과
- quote 현재가와 Yahoo 일봉 최신 종가 차이가 30% 초과
- 현재가가 52주 고가/저가 범위에서 10% 이상 벗어남
- Finnhub ROE와 SEC 기반 ROE 차이가 100%p 초과
- Finnhub PSR과 SEC 기반 PSR 차이가 3배 이상

Signal 계산은 `StockDataQualityService.clean()`을 통해 제외 필드를 `null`로 처리한다. 따라서 ROE 1675%, PBR 1299배 같은 값은 DB에 원본으로 남아도 Valuation, Quality, Momentum, Risk 계산에는 들어가지 않는다.

최종 `integrated_score`에는 데이터 품질 가드레일도 적용한다.

- 품질 점수 35점 미만: 최종 점수 상단 55점 제한
- 품질 점수 50점 미만: 최종 점수 상단 60점 제한
- 제외 필드 4개 이상: 점수를 중립 방향으로 당기고 상단 62점 제한
- 제외 필드 2개 이상: 점수를 중립 방향으로 일부 보정

## 재계산 담당

Signal 재계산은 `StockSignalRefreshService`가 담당한다.

흐름:

1. 대상 종목 목록을 정한다.
2. 각 종목에 대해 `StockSignalService.buildLatest(symbol)`를 호출한다.
3. DB에 저장된 원천 데이터(profile, quote, metric, news, recommendation, eps, candle, SEC financial standard)를 읽는다.
4. `StockDataQualityService`가 품질 점수와 제외 필드를 계산해 `stock_data_quality_latest`에 저장한다.
5. 10개 Signal과 통합 Signal을 계산한다. 제외 필드는 계산에 사용하지 않는다.
6. 같은 batch 대상 안에서 factor ranking을 반영한다.
7. 데이터 품질 가드레일을 최종 점수에 다시 적용한다.
8. `StockSignalLatestMapper.upsert()`로 `stock_signal_latest`에 저장한다.

스케줄 실행은 `StockSignalScheduler`가 담당한다.

기본 설정:

```yaml
app:
  signal:
    refresh:
      enabled: true
      run-on-startup: true
      index-code: SP500
      initial-delay-millis: 3600000
      fixed-delay-millis: 3600000
```

앱 시작 시 한 번 재계산하고 이후 1시간마다 재계산한다. Finnhub, Yahoo candle, SEC EDGAR 수집 배치가 개별 종목 데이터를 갱신한 경우에도 해당 종목 Signal을 다시 계산해 저장한다.

## 화면 조회 기준

리스트 화면은 `stock_signal_latest` 기준이다.

`IndexConstituentMapper.findMarketRows()`는 `stock_signal_latest`를 조인해 `integrated_score`, `tone`, `confidence`, `calculated_at`을 가져온다. 따라서 `/stocks` 요청 중 종목별 Signal 계산을 수행하지 않는다.

같은 쿼리는 `stock_data_quality_latest`도 조인해 `quality_score`, `quality_label`, `tone`을 가져온다.

상세 화면도 저장된 Signal을 조회한다.

`StockController.stockDetail()`은 `StockSignalService.buildStored(symbol)`을 사용한다. 저장값이 없으면 Signal 패널은 표시하지 않는다. 이 경우 `StockSignalRefreshService` 재계산 또는 수집 배치 실행 후 값이 표시된다.

## 주의

`calculated_at`은 Signal 계산 시각이고, 원천 데이터의 최신성을 보장하지 않는다. 원천 데이터 기준은 `source_freshness_json`에서 확인해야 한다.

예를 들어 Signal은 방금 계산했지만 `quoteFetchedAt`이 오래됐다면 최신 주가 기반 Signal이 아닐 수 있다.
