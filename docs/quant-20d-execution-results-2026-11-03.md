# 20D 후속 1–12 실행 결과 — 2026-11-03

목표는 **진짜 미래 20거래일 예측 오차 감소**다. 자료 수집·테스트 통과·UI 완성을 성능 개선으로 세지 않았다. 기존 예측과 원문은 보존했다. 현재 운영 V9와 가중치는 변경하지 않았다.

## 실행 상태

| 번호 | 이번 실행 결과 | 남은 조건 |
|---|---|---|
| 1 | 847개 코드·자료·동결 예측의 content-addressed before-image 검증 | 로컬 보존이며 외부 재해 백업은 아님 |
| 2 | 기존 보유 종목 정답 유지 경로를 새 예측 허가와 분리. 달력 경계에서도 평가 지속 | 실제 알려진 세션만 수집, provider 차단/누락은 별도 보고 |
| 3 | UUID·정확한 사전 대상·모델 성공 영수증·실제 입력 증거를 신규 admission V2에 결합 | 향후 실제 새 배치부터 적용, 이미 동결한 6개는 변경 안 함 |
| 4 | KVUE 진입 불가와 가격 누락/진입 전 상태를 별도 진단 | 현재 KVUE 근거는 공식 웹 읽기 메타데이터, 원문 바이트/과거 취득 PIT 아님 |
| 5 | 실제 선택 재무/가격/뉴스 등, 원시 factor 가용성, 순위 factor, 실행 클래스 해시, V9 직전 상위 모델 출력 보존 구현 | 전체 V2–V7 과거 훈련 원천·취득 시각의 완전 재현은 미확보 |
| 6 | 연구 1,976개 exact key와 현재 운영 DB 대조, 실제 조회 선택 규칙 48건 대조 | 원시 필드 차이를 예측 오차나 잘못된 종목 수로 오해하지 않음 |
| 7 | AMGN 7개+GS 1개 원천 정체성/14개 기존 대상 연결을 source-aware dry-run으로 정규화 | 운영 미반영: null accession/기간 충돌을 보존하는 별도 source-aware 저장·조회 버전 필요 |
| 8 | 24개 프로토콜과 5D/20D/60D 299개 DB 구간 메타데이터 통합 | 전체 노출 장부 불완전. 새 미사용 역사 구간 인증 못 함 |
| 9 | 후보 1개·원인 가설·인과적 훈련 cutoff·양 MAE/RMSE/편향/커버리지/고변동성 판정 규칙 사전 봉인 | `INACTIVE_AWAITING_ELIGIBLE_LABELS`; 아직 실행·평가·운영 연결하지 않음 |
| 10 | 원래 1,257건+별도 30건을 변형 없이 다시 평가, 만기 대기/null 유지 | 최초 12월 1일 미장 종가 이후, 한국시간 **12월 2일 06:00 이후** 자료 완전성에 따라 판정 |
| 11 | 연구/실제 전방/운영 모델 상태를 별도 DB 버전 3행으로 게시. desktop/mobile 확인 | 새 전방 오차 점수는 실제 만기 후에만 표시 |
| 12 | 관련 회귀 테스트, 실제 rollback probe/postimage, 입력·예측 보존, 검토 Important 수정 및 재검토 | 모델 승격·원격 push·외부 배포는 하지 않음 |

“12개 모두 성능 검증 완료”라는 뜻이 아니다. 7의 운영 연결과 9–10의 독립 성능 판정은 실제 미확보 조건으로 남는다. 미래 가격을 만들거나 노출 구간을 미사용으로 바꿔서 완료 처리하지 않았다.

## 자료와 오차에 관한 결론

- 운영 DB 표준 재무 65,251행/원천 facts 706,394행을 읽기 전용으로 대조했다. 연구 1,976개 exact key 중 966개는 해당 운영 키 없음, 1,008개는 원시 필드 차이, 2개는 비교한 8개 필드와 EPS 단위까지 일치했다. 이는 엄격한 입력 정체성 대조이지 1,974개 오예측이라는 뜻이 아니다.
- 복구 189개 매출은 exact 운영 키 기준으로 아직 미반영(136개 행 없음, 53개 매출 null). 원래 203개 분모/14개 미해결은 유지했다. AMGN/GS의 다른 출처 연결 14개를 exact 복구 수에 더하지 않았다.
- 서로 다른 원천·기간 선택과 74개 non-null 값 충돌을 확인했다. 연구팩 전체를 DB에 복사하거나 기존 non-null/원문/공시 키를 덮어쓰지 않았다.
- 기존 노출 훈련 구간 609일/17,904행 결과: 기준 V9 MAE **가격 5.749702649%p / 초과 4.405489367%p**, 재무 보완 V3 **5.757472208 / 4.410768581%p**. 둘 다 악화다. 같은 결과를 다시 돌려 새로운 성과로 포장하지 않았다.
- 새 미사용 과거 구간은 인증하지 못했다. 2021년 두 시험 구간도 실제 실행 로그로 이미 노출됐음을 확인했다. 기존 60건 정답/예측 배열을 다시 열어 가중치를 맞추지 않았다.
- 후보 프로토콜은 두 예측 head 각각 과거 성숙 residual의 block median으로 최대 ±2%p만 보정하는 1개 후보를 제한했다. 최소 3개 선행 비중첩 날짜 블록, 각 블록 28종목 이상, 실제 exit close와 outcome 취득 시각 모두 판단일보다 이전이어야 한다. 현재 적격 post-seal 블록은 0개이며 **코드상 운영 후보를 활성화한 것이 아니다**.

## 실제 운영 확인

- `data/quant-daily/20261103T141242Z-2314eb0c/result.json`: 평가 전용 smoke. 신규 가격 수집/모델 계산 없음. KVUE 1개 종가가 없어 `INCOMPLETE_LABELS`, 진입 불가 별도 진단. 이를 가짜 가격으로 채우지 않음.
- `data/quant-daily/20261103T143939Z-0194d5e3/result.json`: 수정 후 `--run --publish-status` 실제 완료. 이미 동결한 세션은 재생성하지 않았고, 검증 상태 3행만 별도 버전으로 갱신했다.
- 상태 버전: `QUANT_FORWARD_VALIDATION_STATUS_V1`. 기존 `DASHBOARD_*` 스냅샷이나 예측 테이블을 대체하지 않는다. Python은 원래 봉인 평가/가격 export/분모에서 게시물을 재구성하고, Java는 봉인·정확한 재구성·파일 해시를 검사한 뒤 DB에 연결한다.
- `status-rollback-probe`: 트랜잭션 쓰기 후 실제 rollback/preimage 동일 확인. `status-published`: postimage 일치 후 commit. 이후 변경을 덮지 않도록 같은 시각/이전 상태 게시를 거절한다.
- API 호출 70,037 / 예상수익 snapshot 2,497,058 / 표준 재무 65,251행이 서버 조회 전후 동일했다. 원래 6개 코호트+별도 후보 bundle SHA도 동일했다.
- 서버: [로컬 Quant](http://127.0.0.1:8080/quant), 이번 실행에서 기동한 PID 29016. DB 초기화와 scheduler/startup refresh를 껐다. 실측 재시작 후 첫 `/quant` 약 5.254초, 같은 지수 후속 요청 약 55.65ms. 다른 지수 첫 요청은 약 1.85–2.36초다. 첫 요청 지연을 0으로 해결했다고 주장하지 않는다.
- 1440px desktop /390px mobile 패널을 확인했다. 연구 수치, 원래 분모, 진입 불가, 만기 대기/null, proxy/배당/비용/중복 표본 한계를 구분한다. 모바일 패널 폭 347px, viewport 390px.
- 기존 일일 예약 `market-data-hourly-guard`만 갱신했다. 매일 15:10 KST, `--run --publish-status` 사용. 원문 차단/실패/실제 만기/의미있는 변화만 알리며, PC·Codex·MariaDB가 실행되어야 한다. ACTIVE 설정은 다음 예약 실행의 성공 증명이 아니다.

## 검증과 검토

- Java 전체 관련 suite: **298건 중 271 실행 통과, 27 skip**, 실패/오류 0. skip은 DB fixture 등 별도 조건이며 통과로 세지 않음.
- Python: `tests/python` 76 + `src/test/python` 91 + `scripts/test_quant*.py` 159 =326개 통과. 마지막 게시물 위변조 회귀 1개 추가(해당 클래스 총4개) 통과: 현재 구별되는 관련 테스트 **327개**.
- 새 배치 도우미 javac/class-origin preflight 통과. outcome-only 정책 도우미 7개 경계 테스트 통과. 실제 새 예측 생성 자체는 동일 세션 중복 금지 때문에 반복하지 않았다.
- 독립 검토 1회 + Important 수정/재검토 1회. 발견한 게시물 값/근거 결합 누락을 고쳤다. 미해결 Critical/Important 없음. 선택적 후속: admission에서 `preV9Inputs`의 개별 파일 해시도 재검사하기(현재 영수증에는 경로·해시가 남고 전체 과거 재현을 주장하지 않음).
- 서버 GET이 공급자 호출이나 전체 모델 재생을 하지 않는 실제 전후 수치 확인. provider 실패/부분 자료는 성공이나 전체 정확도로 숨기지 않았다.

## 재시작·보존·후속 게이트

1. `data/quant-preservation-20261103-v1/manifest.json`은 작업 전 847개 before-image다. `python -X utf8 scripts/quant_work_preserve.py verify --output data/quant-preservation-20261103-v1`로 검증한다.
2. 작업 후 상태와 증거는 별도 `data/quant-preservation-20261103-post-v1/` 및 실행 폴더 final-verification receipt로 연결한다. 전체 DB dump나 외부 재해 백업은 아니다. 해당 DB 쓰기 범위의 preimage/rollback 증거는 개별 실행 폴더에 있다.
3. 원본 복구는 manifest의 SHA 객체를 **별도 점검 폴더에** 꺼내 비교한 뒤 필요한 파일만 복원한다. dirty checkout 전체 reset/자동 덮어쓰기 금지. DB 상태 복원은 게시 preimage와 최신 postimage를 비교하고 별도 검토된 트랜잭션으로 수행한다.
4. 다음 실제 진전은 새 세션 입력 증거가 있는 전방 수집, 7의 source-aware 운영 입력 버전/74개 충돌의 필드별 판정, 원래 코호트 만기 평가다. 미승인/미입증 입력을 중립 점수로 위장하거나 새 후보의 오차 감소를 먼저 선언하지 않는다.
5. 커밋·Push·병합·외부 배포는 이번 실행에서 하지 않았다. 무관한 기존 변경과 미추적 파일을 보존했으며 이 결과만으로 모델 승격을 허용하지 않는다.

세부 증거: `tmp/quant-20d-execution-20261103-v1/`의 operations-smoke, parity-unseen, source-bridge, source-bridge-adapter, candidate-protocol, status-rollback-probe, status-published, final-maven, final-python*, web-smoke 및 final-verification. 기존 감사 보고서/원문/동결 프로토콜은 삭제하거나 다시 쓰지 않았다.
