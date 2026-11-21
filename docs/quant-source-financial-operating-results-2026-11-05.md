# AMGN·GS source-aware 운영 입력 연결 — 2026-11-05

## 결과

검토된 **AMGN 원문 7개·GS 원문 1개**를 별도 운영 입력 버전 `SOURCE_FINANCIAL_BRIDGE_V1_20261105`에 실제 추가했다. 기존 SEC 표준 행·원천 facts·예측·동결 프로토콜·가중치를 수정하지 않았다. 이 작업은 입력 연결/정합성 개선이며 **실제 미래 20D 오차 감소의 입증이나 모델 승격은 아니다.**

원래 203개 부족 자료 중 189개 exact 복구/14개 미해결 장부는 그대로다. 이번 8개 원천과 14개 기존 대상의 연결은 출처·기간이 다른 정당한 대안이지, 원래 14개 키를 전부 복구했다는 뜻이 아니다.

## 안전한 연결 방법

- 새 표 `stock_source_financial_document`를 추가했다. SEC 표준 표의 accession NOT NULL/기존 natural key를 바꾸지 않았다. 원문 ID·해시·공개일·전체 증거·실제 UTC 반영 시각을 독립 보존했다.
- 반영 가능한 원문 묶음은 검토된 pack SHA `dd0a028de57e6261485cb36393a82ba2fb15bd39293932a90de5b46251fc1912`로 고정했다. 일반 validator는 원문 바이트 해시와 검토된 추출 메타데이터/단위/기간/산술을 확인한다. 원문 전체를 새로 독립 추출한 것으로 주장하지 않는다. 다른 묶음은 새 버전과 별도 검토가 필요하다.
- `quant_source_operating.py`는 원본에서 admission을 정확히 재구성하고 봉인·바이트 해시를 확인한다. Java는 이를 **DB 연결 전에** 확인한다. 실제 DB admission 시각은 Java의 현재 UTC로 새로 기록하며 과거 취득시각으로 소급하지 않았다.
- AMGN의 SEC accession/filed date는 정당하게 null이다. 공개 당일은 사용하지 않는다. 출처별 부분 값은 동일 issuer·시작/종료일·분기·통화가 모두 맞고 기존 매출이 null일 때만 detached 입력 객체에 보완한다. 기존 객체/행의 non-null 값은 덮지 않는다.
- GS 원문의 **2013-04-01~06-30(91일)**과 기존 API의 **03-31~06-30(92일)**은 별도 정체성이다. 새 정책이 원문을 선택할 때는 전체 원문 statement로 읽으며 다른 기간의 자산·영업이익 등을 섞지 않는다. 영업이익 null은 그대로다. 기존 03-31 행은 보존했다.
- 과거 입력 재구성은 `RECONSTRUCTED_NOT_HISTORICAL_SYSTEM_PIT`로 표시한다. 실제 공개시각·과거 시스템 취득을 입증하지 않는다. 라이브 조회는 실제 admission 시각과 판단일 경계도 확인한다.
- 현재/직전 분기 읽기만 연결했다. 연간/EPS·가격/ADS 환산·예측 가중치 등은 바꾸지 않았다. 더 최신 분기가 있으면 오래된 원문은 대체하지 않는다.
- 기본 feature flag는 false다. 향후 실제 전방 batch helper에서만 명시적으로 true로 켜며, 실행 클래스 해시와 configured source policy version 및 실제 선택 원문이 새 입력 증거에 남는다. 이미 동결된 오늘 예측은 재생성하지 않았다.
- 새 과거 입력 snapshot 버전은 DB `VARCHAR(20)`에 맞는 `q4-srcfin-20261105`다. 기존 snapshot 버전/행과 구분한다. 이번에는 실제 historical snapshot 저장이나 학습·오차 재시험을 하지 않았다.

## 실제 DB 작업·보존 확인

작업 폴더: `tmp/quant-source-operating-20261105-v1/`.

1. `before-operation.json` 및 `data/quant-preservation-20261105-source-before-v1/manifest.json`: 기존 코드/원문 연결/12개 동결 코호트 등 23개 before-image, 원래 예측과 AMGN/GS SEC 행 해시/행수 기준 보존.
2. `schema-install/`: 추가 전 table 존재 여부/DDL 해시 기록. DDL은 기존 표 변경이나 재초기화 없이 별도 표만 추가했다. DDL 자체를 트랜잭션 rollback이라고 주장하지 않는다.
3. `rollback-probe/receipt.json`: 8개 source 행을 실제 INSERT한 후 rollback, source 표 0행으로 복귀, 기존 SEC 전 행 preimage와 동일함을 확인.
4. `published/receipt.json`: 8행 postimage/원문 payload 일치 후 commit. 실제 admission 관측 시각 **2026-11-05T12:04:34Z 무렵(21:04 KST)**. 같은 버전을 재반영하여 최초 admission 시각을 바꾸는 경로는 거절한다.
5. `commit-receipt.json`: 원래 동결/후보 13파일 SHA, AMGN/GS 기존 행 해시, 전체 표준 65,251/원천 706,394/예상수익 snapshot 2,527,202/API 호출 71,073 기준이 유지됨. 정확한 API 기준은 before-operation.json/최종 영수증을 우선하며 API 수는 실제 조회해 기록한다.
6. 원래 전방 자료 전체 보존은 독립 검사 `automation-check/evidence.json`에서도 확인했다. 기존 Nov3 보존본에 있던 예측/프로토콜/후보 91개 파일이 byte-identical이고, 실행 결합 증거 1,048개를 확인했다.

## 실제 조회와 한계

- `read-probe/read-path-proof.json`: 실제 MariaDB/MyBatis/Spring resolver로 8개 공개일 다음날에 원문 분기/매출/출처를 읽었다. GS는 Apr1 시작일, AMGN은 null SEC accession을 유지했다.
- `actual-availability-proof.json`: **해당 원문 ID**가 그 원문의 공개 당일에는 선택되지 않고, 다음날에는 정확한 ID로 선택되는지 8개 모두 확인했다. 2015-01-27에는 이미 공개된 더 오래된 AMGN 원문이 fallback으로 선택될 수 있다. 이는 당일 새 원문을 쓴 것이 아니므로 단순 객체 변경 여부와 구분했다.
- 실제 최신 AMGN/GS 분기는 두 경로 모두 **2026-03-31**로 같았다. 2013~2019 원문을 최신 분기인 것처럼 강제로 넣지 않았다. 현재 live 입력/예측을 억지로 바꿨다는 성과를 주장하지 않는다.
- 실제 DB에는 AMGN의 2019-01-30 이전 canonical 가격이 **0개**였다. 따라서 그 날짜의 전체 `buildSnapshot`은 최소 80개 가격 문맥이 없어 정상적으로 null을 반환했다. 최초 probe의 assertion 실패/진단 로그를 보존했으며 이후 source 조회 성공과 전체 과거 특징 재생 준비를 분리했다. **과거 전체 모델 재생/20D 오차 검증은 완료가 아니다.**
- 80개 가격 fixture가 있는 자동 테스트에서는 실제 `HistoricalBacktestSeedService.buildSnapshot`이 별도 버전·선택 원문 증거를 남기는 것을 확인했다. 이를 운영 DB의 없는 가격이나 실제 MAE 검증으로 대신 세지 않는다.
- 기존 60건 정답/예측을 다시 열어 가중치에 맞추거나, 이미 노출된 훈련 결과를 재계산해 새 성능 근거로 삼지 않았다.

## 자동 전방 수집 확인

독립 읽기 전용 검증: `automation-check/README.md`, `evidence.json`.

- 11월 4일 `20261104T124218Z-55ecbfd3`와 11월 5일 `20261105T113022Z-8e810cdc`는 각각 **628건(30/101/497)**을 실제 생성·V2-admit·동결했다. UUID, 정확 대상, 선택 입력 해시, 실행별 클래스 증거, V9 직전 입력, entry cutoff/20거래일 경계를 대조했다.
- 저장 평가 21개를 그대로 재구성했다. 현재 12코호트의 기준 관측 **2,513건**, 별도 후보 **30건** 모두 만기 대기, 평가 n=0/MAE=null이다. 서로 겹친 관측을 독립 표본으로 세지 않는다.
- 현재 canonical 가격 **710,526행**, 최신 **2026-11-04**. Yahoo Nov5 작업은 요청518/성공517/실패1, upsert128,601이며 새 행 수가 아니다. WBD basis guard 실패와 기존 canonical 보존을 숨기지 않았다.
- 예약은 기존 `market-data-hourly-guard`, ACTIVE/매일15:10KST다. 실제 두 작업 시작은 21:42/20:30KST였다. **수집 동작은 확인했지만 정시 실행·PC 종료 상태 실행·DB 상시 uptime은 증명하지 않았다.** 신규 예약이나 OS 서비스 설치는 하지 않았다.
- 이번 변경 후 `20261105T121148Z-7cfe0963` 평가/상태 게시 smoke도 COMPLETED다. `collectionSkippedAlreadyFrozen=true`, 신규 forecast/provider collection 없음. 이미 동결한 현재 세션을 반복 생성하지 않았다.
- 새 source 정책을 담은 **다음 새 코호트 자체의 실수집은 다음 적격 세션 실행 후 확인해야 한다.** 현재 설정/compile/읽기 경로/과거 실제 자동 작업을 검증한 것을 미래 예약 성공 보장으로 바꾸지 않는다.

## 테스트·검토

- JUnit 전체 관련 suite: **307건 중 280 실행 통과, 27 조건부 skip**, 실패/오류0. skip은 통과로 세지 않음.
- Python `tests/python`: **80개 통과**(전방/원문 기존 회귀 포함). Java operator admission 정책 standalone **2개 통과**, helper javac 성공.
- 신규 집중 9개는 원문 공개일/실제 admission 경계, 단위/중복 거절, detached null-only 보완, GS 기간 분리, 최신 행 보존, 실제 운영 cache wiring, 과거 raw 증거, 실제 mapper를 확인한다.
- 독립 코드 검토 1회에서 Important 2건 발견. 버전명 25자→17자 수정은 실제 VARCHAR(20) INSERT 실패→통과로, 검토된 extraction pack pin 누락은 별도 operator guard 실패→통과로 고쳤다. 수정 후 전체 suite 통과. 검토가 제외한 전역 기존 코드 감사/아키텍처 확장/옛 정답 재시험은 이번 범위가 아니므로 추가 수행하지 않았다.

## 복구·다음 작업

- 기존 SEC 표준/원문/예측을 되돌리는 작업은 필요 없다. 이번에 변경하지 않았다. 새 표와 admitted 문서는 삭제하지 않고 증거로 남긴다.
- 정책 비활성화 시 `QuantStartForwardCollection`의 명시적 source feature 옵션을 false로 돌리는 좁은 변경을 먼저 하고 확인한다. 기본 앱 feature=false이므로 새 설정 없이 기존 web GET은 이 정책을 자동 활성화하지 않는다. 원본 helper는 before-image에 보존되어 있으나, 다른 이후 변경을 덮지 않도록 diff 후 필요한 옵션만 되돌린다. 전체 checkout reset/drop/무조건 덮기 금지.
- 과거 원문을 실제 훈련에 쓸 다음 단계는 필요한 과거 canonical 가격/모집단/공개시점 근거와 새 입력 버전을 함께 검증하는 것이다. 없는 가격을 0/전일가격/추정값으로 메우지 않는다. 이미 시험한 60건이나 노출 구간으로 새 가중치를 승격하지 않는다.
- 첫 기존 코호트의 가장 이른 평가 가능 시각은 한국시간 **2026-12-02 06:00 이후**, 실제 21개 종가 완전성에 따른다. 자료 연결을 정확도 향상으로 부풀리지 않으며 충분한 전방 오차 근거 전에는 운영 V9/가중치를 유지한다.
- main dirty 파일/기존 작업은 보존했고 commit/push/merge/외부 배포는 하지 않았다.
