# Stock Signal

## 원칙

Signal은 화면 요청 시 계산하지 않는다.

`/stocks` 리스트 화면과 `/stocks/{symbol}` 상세 화면은 DB에 저장된 최신 Signal 값을 조회해서 표시한다. 요청 중에는 `valuation`, `quality`, `earnings`, `analyst`, `news`, `momentum` 계산을 반복하지 않는다.

## 저장 테이블

최신 Signal은 `stock_signal_latest`에 저장한다.

주요 컬럼:

- `symbol`: 종목 코드
- `calculated_at`: Signal 계산 시각
- `signal_version`: 계산 로직 버전
- `integrated_score`, `integrated_label`, `tone`, `confidence`: 통합 Signal 결과
- `valuation_score`, `quality_score`, `earnings_score`, `analyst_score`, `news_score`, `momentum_score`: 6개 항목 점수
- `reasons_json`, `cards_json`: 상세 화면 표시용 근거와 카드 데이터
- `source_freshness_json`: 계산에 사용한 원천 데이터의 기준 시각
- `raw_json`: 디버깅용 원본 계산 결과

기존 `stock_analysis_snapshot`은 과거 3개 신호 구조용 테이블이며, 현재 6개 Signal 표시 기준 테이블은 `stock_signal_latest`다.

## 재계산 담당

Signal 재계산은 `StockSignalRefreshService`가 담당한다.

흐름:

1. `IndexConstituentMapper.findCurrentSymbols(indexCode)`로 현재 지수 구성 종목을 조회한다.
2. 각 종목에 대해 `StockSignalService.buildLatest(symbol)`를 호출한다.
3. DB에 저장된 원천 데이터(profile, quote, metric, news, recommendation, eps, candle)를 읽는다.
4. 6개 Signal과 통합 Signal을 계산한다.
5. `StockSignalLatestMapper.upsert()`로 `stock_signal_latest`에 저장한다.

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

앱 시작 시 한 번 재계산하고, 이후 1시간마다 재계산한다. Finnhub 수집 배치가 개별 종목 데이터를 갱신한 경우에도 해당 종목의 Signal을 다시 계산해 저장한다.

## 화면 조회 기준

리스트 화면은 `stock_signal_latest` 기준이다.

`IndexConstituentMapper.findMarketRows()`는 `stock_signal_latest`를 조인해 `integrated_score`, `tone`, `confidence`, `calculated_at`을 가져온다. 따라서 `/stocks` 요청 중 종목별 Signal 계산을 수행하지 않는다.

상세 화면도 저장된 Signal을 조회한다.

`StockController.stockDetail()`은 `StockSignalService.buildStored(symbol)`을 사용한다. 저장값이 없으면 Signal 패널은 표시되지 않는다. 이 경우 `StockSignalRefreshService` 재계산 또는 스케줄러 실행 후 값이 표시된다.

## 주의점

`calculated_at`은 Signal 계산 시각이고, 원천 데이터의 최신성을 보장하지 않는다. 원천 데이터 기준은 `source_freshness_json`을 확인해야 한다.

예를 들어 Signal은 방금 계산됐지만 `quote_fetched_at`이 오래됐다면 최신 주가 기반 Signal이 아닐 수 있다.
