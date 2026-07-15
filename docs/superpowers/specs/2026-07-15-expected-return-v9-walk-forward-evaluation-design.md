# Expected Return v9 워크포워드 평가 및 승격 게이트 설계

## 1. 목적

이 설계의 목적은 미래 가격을 단정적으로 맞히는 모델을 만드는 것이 아니라, 미국 대형주의 향후 20거래일 초과수익 분포를 시점 일관성 있게 예측하고 그 예측이 실제 투자 의사결정에 유효한지 반복 검증할 수 있는 기반을 구축하는 것이다.

첫 개발 사이클에서는 `EXPECTED_RETURN_V9`의 계산식을 변경하지 않는다. v9을 동결된 production 기준선으로 삼고, 누수 방지 워크포워드 평가기와 자동 승격 게이트를 먼저 만든다. 후속 후보 모델은 동일한 평가 계약을 통과해야만 production 후보가 될 수 있다.

실거래 주문 전송은 범위에 포함하지 않는다. 최종 단계는 Shadow 예측과 사후 성과 비교까지다.

## 2. 고정 예측 계약

계약 버전은 `SP500_EXCESS_20D_V1`이다.

- 유니버스: 각 예측일 당시의 S&P 500 구성 종목
- 기준지수: S&P 500
- 예측 기간: 20거래일
- 리밸런싱 주기: 월 1회
- 목표값: 같은 시작일과 종료일을 사용한 `종목 총수익률 - S&P 500 총수익률`
- 기준 모델: `EXPECTED_RETURN_V9`
- 핵심 출력: 예상 초과수익률, 상승 확률, P10/P50/P90, 하방 위험, confidence
- 사용 목적: 종목 순위화와 long-only 포트폴리오 구성
- 평가 방식: 날짜 단위 expanding-window walk-forward

계약을 변경하는 경우 기존 결과를 덮어쓰지 않고 새 계약 버전을 만든다.

## 3. 설계 대안과 결정

### 대안 A: 기존 대형 서비스에 직접 추가

`StockBacktestService`와 `StockPortfolioBacktestService`에 평가 로직을 추가하면 구현은 빠르지만, 이미 각각 약 2,800줄과 6,600줄인 서비스의 결합도와 회귀 위험이 증가한다.

### 대안 B: 독립 평가 모듈 구축

기존 snapshot과 backtest 데이터를 재사용하되 계약, 데이터셋, window, 지표, 포트폴리오 평가, 승격 판단을 독립 컴포넌트로 분리한다. 테스트 가능한 순수 계산 경계를 만들 수 있고 기존 화면과 배치를 단계적으로 연결할 수 있다.

### 대안 C: Python 연구 파이프라인 분리

복잡한 ML 실험에는 유리하지만 Java/Python 데이터 계약과 운영 경계가 추가된다. PIT 데이터와 평가 계약이 안정되지 않은 현재 단계에서는 재현성 비용이 더 크다.

### 결정

대안 B를 채택한다. Python 연구 파이프라인은 평가 계약과 Shadow 운영이 안정된 뒤 별도 설계로 검토한다.

## 4. 컴포넌트 경계

### `ExpectedReturnPredictionContract`

유니버스, 기준지수, horizon, 리밸런싱, 최소 표본, 비용 가정과 계약 버전을 불변 값으로 제공한다. 평가 실행 중 설정이 바뀌지 않도록 한다.

### `ExpectedReturnEvaluationDatasetService`

v9 예측, 실제 20일 종목 수익률, 같은 기간 벤치마크 수익률, 당시 지수 구성과 데이터 가용 시점을 결합한다. 유효한 행과 제외된 행을 함께 반환하며 제외 사유를 집계한다.

### `WalkForwardWindowGenerator`

날짜 단위 expanding-window를 생성한다. 학습 label과 테스트 기간이 겹치지 않도록 purge/embargo를 적용한다.

### `ExpectedReturnMetricsCalculator`

입력 행만으로 예측 지표를 계산하는 순수 컴포넌트다. DB, Spring context 또는 현재 시각에 의존하지 않는다.

### `ExpectedReturnPortfolioEvaluator`

동일한 테스트 행으로 예측 상위 종목 포트폴리오를 만들고 거래비용 차감 성과를 계산한다. 기존 포트폴리오 비용 계산 규칙은 인터페이스 뒤로 추출해 재사용한다.

### `ExpectedReturnPromotionGate`

절대 품질 기준과 기준 모델 대비 상대 기준을 평가해 `PROMOTE`, `HOLD`, `REJECT`, `INSUFFICIENT_DATA`를 반환한다. 판정과 함께 통과·실패한 각 조건을 저장한다.

### 평가 실행 서비스와 저장소

하나의 실행 ID 아래 계약, 모델, 기간, 데이터 coverage, window별 지표, 종합 지표와 판정을 저장한다. 동일한 입력 snapshot과 설정으로 다시 실행하면 동일한 결과가 나와야 한다.

## 5. 시점 일관성과 누수 방지

각 평가 행은 다음 필드를 가져야 한다.

- `signalDate`: 예측 생성 거래일
- `availableAt`: 입력 데이터가 실제 사용 가능해진 시각
- `labelEndDate`: 20거래일 후 결과가 확정되는 거래일
- `modelVersion`: 예측 모델 버전
- `contractVersion`: 평가 계약 버전

다음 규칙을 강제한다.

1. `availableAt <= signalDate`인 입력만 사용한다.
2. 재무 데이터는 회계기간 종료일이 아니라 실제 공시 시각을 사용한다.
3. 거시 데이터는 현재 수정값이 아니라 예측일 당시 공개된 빈티지를 사용한다.
4. 지수 구성은 `signalDate` 당시의 구성 종목을 사용한다.
5. 편출·상장폐지 종목을 과거 표본에서 사후 제거하지 않는다.
6. 종목과 벤치마크 수익률은 동일한 시작일과 종료일을 사용한다.
7. 결측치는 0으로 대체하지 않고 제외 사유를 기록한다.
8. 모델 비교에는 날짜·종목이 동일한 교집합 표본을 사용한다.
9. 동일 계약, 모델, 날짜, 종목에는 하나의 예측만 허용한다.
10. 평가 실행은 명시적으로 전달된 `asOfDate`를 사용하며 `LocalDate.now()`에 의존하지 않는다.

제외 사유의 최소 분류는 `MISSING_PREDICTION`, `MISSING_REALIZED_RETURN`, `MISSING_BENCHMARK`, `MISSING_MEMBERSHIP`, `PIT_VIOLATION`, `DUPLICATE_PREDICTION`, `INVALID_VALUE`다.

## 6. 워크포워드 window

- 최소 학습기간: 3년
- 테스트 구간: 1개월
- 학습 방식: expanding window
- purge/embargo: 테스트 시작일 전 최소 20거래일
- 분할 단위: 종목이 아니라 `signalDate`
- 최소 학습 표본: 1,000행
- 최소 테스트 표본: 월 100행
- 종합 판정 최소 유효 테스트 월: 12개월
- 조건 미달 window: 제거하지 않고 `INSUFFICIENT_DATA`로 저장

학습 데이터에는 `labelEndDate`가 embargo 경계 이전인 행만 들어갈 수 있다. 같은 `signalDate`의 모든 종목은 항상 같은 fold에 속한다.

## 7. 기준선

첫 평가 사이클에서는 다음 기준선을 함께 계산한다.

- 예측값 기준선: 예상 초과수익 0%, 상승 확률 50%
- 순위 기준선: 20거래일 단순 Momentum
- 포트폴리오 기준선: 당시 S&P 500 구성 종목 균등가중
- 시장 기준선: S&P 500
- production 모델 기준선: `EXPECTED_RETURN_V9`

v9 자체의 첫 판정은 `BASELINE_QUALIFIED`, `BASELINE_UNSTABLE`, `INSUFFICIENT_DATA` 중 하나다. 이후 후보 모델부터 `PROMOTE`, `HOLD`, `REJECT`를 사용한다.

## 8. 예측 품질 지표

모든 cross-sectional 지표는 먼저 `signalDate`별로 계산한 후 월별 및 전체로 집계한다. 종목 수가 많은 날짜가 전체 결과를 지배하지 않게 한다.

- Spearman Rank IC: 예상 초과수익 순위와 실제 초과수익 순위의 상관
- IC 양수 월 비율
- MAE: 예상 초과수익과 실제 초과수익의 절대 오차
- 방향 정확도: 예상 초과수익과 실제 초과수익 부호 일치율
- Brier score: 초과수익 양수 확률의 오차
- calibration error: probability bucket별 예측 확률과 실제 비율 차이의 표본가중 평균 절댓값
- P10-P90 coverage: 실제 초과수익이 예측 구간 안에 포함되는 비율
- 분위수 spread: 상위 20% 실제 초과수익 평균에서 하위 20% 평균을 뺀 값
- coverage: 전체 대상 행 중 유효 평가 행 비율

평균뿐 아니라 월별 median, 10/90 percentile, 최악 월, 시장 국면별 결과를 저장한다.

## 9. 투자 성과 평가

월별 예측일에 예상 초과수익 상위 20개 종목을 선택한다. long-only이며 동일가중을 1차 기준으로 사용한다. Optimized v5 연결은 평가기 안정화 후 후속 사이클에서 추가한다.

성과 지표는 다음과 같다.

- 비용 차감 누적수익률과 연환산 수익률
- S&P 500 대비 연환산 초과수익률
- Sharpe와 Sortino
- 최대낙폭
- 월별 벤치마크 승률
- turnover
- 거래비용과 슬리피지
- 최악 5% 월 평균인 tail loss
- 섹터 최대 비중과 종목 최대 비중

거래비용은 기존 동적 거래비용 규칙을 공용 인터페이스로 추출해 사용한다. 비용 규칙 버전도 평가 실행에 저장한다.

## 10. 승격 게이트

### 데이터 충분성

아래 조건 중 하나라도 충족하지 못하면 `INSUFFICIENT_DATA`다.

- 유효 테스트 월 12개월 이상
- 전체 유효 평가 행 3,000개 이상
- coverage 70% 이상
- 각 유효 월 표본 100개 이상

### v9 기준선 자격

v9은 아래 조건을 모두 만족하면 `BASELINE_QUALIFIED`다.

- 월별 median Rank IC가 0보다 큼
- IC 양수 월 비율이 50% 이상
- 분위수 spread가 0보다 큼
- Brier score가 0.25 이하
- calibration error가 10%p 이하
- P10-P90 coverage가 70% 이상 90% 이하
- 비용 차감 연환산 초과수익률이 0보다 큼
- 최악 시장 국면 Rank IC가 -0.05 이상

데이터는 충분하지만 하나 이상 실패하면 `BASELINE_UNSTABLE`이다.

### 후보 모델 승격

후보 모델은 v9과 동일한 교집합 표본에서 아래 조건을 모두 충족해야 `PROMOTE`다.

- 월별 median Rank IC 개선폭이 0.01 이상
- IC 양수 월 비율이 v9 이상
- 분위수 spread 개선폭이 0보다 큼
- Brier score가 v9보다 나쁘지 않음
- calibration error가 v9보다 2%p 이상 나빠지지 않음
- coverage가 v9의 95% 이상이며 절대 70% 이상
- 비용 차감 연환산 초과수익률 개선폭이 1%p 이상
- Sharpe 개선폭이 0.10 이상
- 최대낙폭이 v9보다 2%p 이상 나빠지지 않음
- turnover가 v9 대비 25% 이상 증가하지 않음
- 어떤 주요 시장 국면에서도 Rank IC가 -0.05 미만이 아님

하드 실패 조건은 `REJECT`다.

- median Rank IC가 0 이하
- 분위수 spread가 0 이하
- 비용 차감 초과수익률이 0 이하
- Brier score가 0.25 초과
- PIT 위반이 한 건이라도 발견됨
- 후보에만 유리한 표본 불일치가 발견됨

그 외 혼합 결과는 `HOLD`다. 임계값은 코드 상수가 아니라 버전이 있는 정책 객체로 관리하되, `SP500_EXCESS_20D_V1` 실행 중에는 변경할 수 없다.

## 11. Shadow 운영

승격 게이트를 통과한 후보도 즉시 production에 사용하지 않는다.

- 최소 Shadow 기간: 3개월 또는 60거래일 중 더 긴 기간
- 매일 예측 입력 snapshot ID, 모델 버전, 출력과 추천 상위 20개를 불변 저장
- 결과가 확정되면 같은 평가 계약으로 사후 측정
- Shadow 기간 중 모델 재학습 또는 설정 변경 시 새 모델 버전 발급
- Shadow 결과가 승격 기준을 유지할 때만 production 전환을 별도 승인

## 12. 데이터 흐름

1. 평가 실행 요청이 계약, 기준 모델, 후보 모델, `asOfDate`를 고정한다.
2. 데이터셋 서비스가 PIT 규칙에 따라 유효 행과 제외 행을 생성한다.
3. window 생성기가 expanding-window fold를 만든다.
4. 각 fold에서 동일한 교집합 표본으로 모델별 예측 지표를 계산한다.
5. 포트폴리오 평가기가 월별 상위 20개 전략을 시뮬레이션한다.
6. 저장소가 window 결과와 종합 결과를 실행 ID 아래 저장한다.
7. 승격 게이트가 저장된 지표만 사용해 판정한다.
8. `/quant`는 최신 완료 실행의 요약과 실패 조건을 읽기 전용으로 표시한다.

화면 요청 중에는 평가를 재실행하거나 외부 API를 호출하지 않는다.

## 13. 오류 처리와 관측성

- PIT 위반, 중복 예측, 날짜 역전은 실행 실패로 처리한다.
- 결측 데이터와 표본 부족은 실행 실패가 아니라 명시적 제외 또는 `INSUFFICIENT_DATA`다.
- 일부 window 계산 실패 시 전체 결과를 성공으로 표시하지 않는다.
- 실행 상태는 `RUNNING`, `COMPLETED`, `FAILED`, `INSUFFICIENT_DATA`로 저장한다.
- 실패 시 단계, 오류 코드, 안전한 오류 메시지와 처리 행 수를 저장한다.
- 재실행은 새 실행 ID를 만들며 이전 실행을 수정하지 않는다.
- 로그에는 계약 버전, 모델 버전, window 기간, 입력·유효·제외 행 수를 포함한다.

## 14. 테스트 전략

### 순수 단위 테스트

- 날짜별 Spearman Rank IC와 tie 처리
- Brier score와 calibration error
- P10-P90 coverage
- portfolio turnover, 비용, Sharpe, Sortino, MDD, tail loss
- `PROMOTE`, `HOLD`, `REJECT`, `INSUFFICIENT_DATA`의 경계값

### 누수 방지 테스트

- `availableAt > signalDate` 행 제외
- 학습 행의 `labelEndDate`가 embargo 이후면 제외
- 같은 날짜 종목이 서로 다른 fold로 분리되지 않음
- 후보와 v9이 동일한 교집합 표본을 사용함
- 현재 날짜를 바꿔도 명시적 `asOfDate` 실행 결과가 동일함

### 저장소 통합 테스트

- schema와 MyBatis 매핑 round-trip
- 실행 ID 아래 window 및 종합 결과 저장
- 실패 또는 재실행 시 기존 결과 불변

### 회귀 테스트

- 작은 고정 fixture에서 모든 지표와 판정이 기대값과 일치
- 동일 fixture 재실행 결과가 byte-for-byte 동일한 canonical summary 생성
- 기존 `EXPECTED_RETURN_V9` snapshot 생성과 `/quant` 조회가 유지됨

## 15. 구현 순서

1. 예측 계약과 순수 지표 타입을 테스트 주도로 추가한다.
2. 날짜 단위 window와 누수 방지 검증을 추가한다.
3. 평가 데이터셋 조회와 제외 사유 집계를 추가한다.
4. 평가 실행 및 결과 저장 schema/MyBatis를 추가한다.
5. 동일가중 상위 20 포트폴리오 평가와 비용 인터페이스를 추가한다.
6. v9 기준선 자격 및 후보 승격 게이트를 추가한다.
7. 배치 실행기와 `/quant` 읽기 전용 요약을 추가한다.
8. 실제 DB 데이터로 v9 기준선 실행을 수행하고 결과를 문서화한다.
9. 평가기가 안정된 뒤 PIT 데이터 강화와 v10 후보 설계를 별도 사이클로 시작한다.

## 16. 범위 제외

- v9 계산식 변경
- v10 또는 외부 ML 라이브러리 도입
- Optimized v5와의 결합 변경
- 실거래 주문 또는 브로커 연결
- Railway 배포 변경
- PIT 원천 데이터 자체의 신규 수집

이 항목들은 평가 기반이 검증된 뒤 각각 별도 설계와 구현 계획으로 진행한다.
