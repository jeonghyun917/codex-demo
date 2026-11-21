# Quant 과거 데이터 확보 및 검증 결과 — 2026-10-17

## 결론

공개 원문 수집, 근거 DB 적재, 가격 대조와 소프트웨어 검증은 수행했다. **예측 성능 및 완전한 과거 백테스트 검증은 완료되지 않았다.** 실제 평가 run 3은 `INSUFFICIENT_DATA`다. 수집 성공, 공식 문서의 존재, 코드 테스트 통과를 모델 성능 증명으로 취급하지 않았다.

## 실제 확보·적재

| 구분 | 원문 | 행사/변경 기록 | 해석 |
|---|---:|---:|---|
| S&P 공식 구성종목 변경 공지 | 48 | 147 | SP500 139건, DOW30 8건; 전체 일별 구성종목 데이터 아님 |
| Yahoo 공개 기업행사 응답 | 603 | 11,798 | 배당 11,688건, split-like 조정계수 110건; 공급자 보고값 |
| 기업·SEC 공식 보조 근거 | 9 | 4 | APH 공식 분할 3건·배당 지급 1건; 기타 합병/분사 원문 포함 |
| 합계 | 660 | 11,949 | 원문 해시 검증, 정규화 manifest 3개 원형 보존 |

- 660개 종목 요청 중 603개 응답, 57개 HTTP 404. 종목 수와 위 표의 원문 수는 다른 집계다.
- 기업행사 관측 범위 2019-12-31~2026-10-16. 요청 시작일 이전에 공급자가 반환한 11건도 명시적으로 보존했다.
- 공식 구성종목 변경 적용일 범위 2021-12-20~2026-10-06. **완전성이 입증된 전체 구성종목 날짜는 0개**다.
- Nasdaq 공식 연간 공지는 웹에서 확인했지만 원문 다운로드는 403/timeout으로 실패했다. 원문 확보 건수에 포함하지 않았다.
- 원문 응답은 로컬에 보존하며 Git에서 제외한다. 공개 접근 가능성이 재배포·상용 이용 허가를 의미하지 않는다.

세부 출처·실패 목록:

- [구성종목 근거](quant-membership-source-evidence-2026-10-17.md)
- [기업행사 근거](quant-corporate-actions-source-evidence-2026-10-17.md)
- `tmp/pit-acquisition/actions/verification.json`
- `tmp/pit-acquisition/membership/bundle.json`

## 코드 및 DB 변경

추가 스키마 `src/main/resources/db/quant-historical-evidence.sql`:

- `stock_historical_evidence_source`: URL, 원문 SHA-256, 발표일, 최초 수집시점(UTC), 보존 경로, 부분 커버리지 등급.
- `stock_historical_evidence_event`: 종목, 지수, ADD/REMOVE/SPLIT/DIVIDEND, 날짜 의미, 거래 전후 시점, 정규화 값.
- `stock_historical_evidence_bundle`: 전체 정규화 JSON 원형 및 해시. 공시 연결, 라이선스 주의, 발표일 정밀도, `DO_NOT_REAPPLY` 경고 등 부가 근거도 누락 없이 보존.

`StockHistoricalEvidenceBundle`은 경로 이탈, 원문 해시 불일치, 고아 공시 참조, 중복 출처/행사, 잘못된 비율·통화·날짜 의미를 거부한다. 일부 행사 목록에 `OFFICIAL_PIT` 완전성 등급을 붙일 수 없다.

`StockHistoricalEvidenceImportBatchRunner`는 명시적 오프라인 실행 전용이다. 원자적 트랜잭션으로 적재하며 기존 출처/행사 의미와 충돌하면 전체 롤백한다. 같은 원문을 재수집하거나 경로만 옮긴 경우 최초 원문 행의 수집시점·경로를 유지하고 새 manifest는 별도 보존한다. 기존 가격·구성종목·예측 테이블은 수정하지 않는다.

검증 스크립트는 원문이나 등급을 자동 수정하지 않는다. `verify_quant_actions.py`는 잘못된 등급을 발견하면 실패하고 보고서만 쓴다.

## 실제 가격·수익률 대조

재현 도구: `scripts/audit_quant_price_basis.py` (읽기 전용).
결과: `tmp/pit-acquisition/price-basis-audit.json`.

- 수정된 결과 6,885건 중 6,858건의 진입·청산일을 새 공급자 가격과 매칭했다.
- 0.01%p를 초과하는 수익률 차이 **0건**. 최대 차이 약 0.000006274%p.
- 나머지 27건은 비교 가격이 없어 미검증이다. 이를 일치로 집계하지 않았다.
- 현재 518개 종목의 기존 가격 685,615행 중 679,048행 대조 가능.
- 상대 오차 허용치 0.00001 이내 677,734행. 불일치 1,314행, 종목은 **KLAC, CTVA, CRWD, APH, MNST, DD, SPGI, HON**.
- 새 공급자 응답과 매칭되지 않은 기존 날짜 6,567행. 최근 새 상장 이력과 과거 다른 기업의 같은 코드 이력이 섞일 수 있어 자동 연결하지 않았다.
- APH 2026년 198개 날짜와 9월 분할 전후 가격은 일치. APH의 불일치 73개 날짜는 더 오래된 구간이다.

이 검사는 공급자 간/시점 간 가격 일관성 검사다. 공식 미조정 가격이나 총수익률의 인증이 아니다. 이미 분할 조정된 OHLC에 분할 비율을 다시 곱하지 않았다. Yahoo의 일부 SPLIT 값은 실제 주식수 비율이 아니라 분사 조정계수이며, 과거 배당 금액도 현재 주식수 기준으로 소급 표현될 수 있다.

## 실제 예측 평가

`tmp/quant-evidence-evaluation.log`, DB `stock_expected_return_evaluation_run.id=3`:

- 상태/판정: `INSUFFICIENT_DATA`
- 데이터셋 후보: 33,983행
- 유효 과거 예측: 0행
- 유효 OOS 월: 0
- 사유별 제외: 구성종목 근거 미확인 33,983; 수정 모델 과거 예측 없음 33,983; 실현수익률 없음 32,644; 완전한 벤치마크 없음 33,483. 한 행에 여러 사유가 있으므로 합산하면 안 된다.
- 기존 87,105개 구형 결과와 6,885개 수정 결과 보존. 평가에 쓰이는 기존 구성종목 source별 행 수도 변경 전과 동일.

따라서 IC, Sharpe, 초과수익 성능 통과를 주장할 수 없다. 현재 계약의 수익률은 배당·상장폐지 회수액을 포함하지 않는 가격수익률이며 벤치마크도 공식 총수익 지수가 아닌 가중 가격수익 proxy다.

## 검증 기록

- `mvn.cmd "-Dmaven.repo.local=.m2/repository" test`: **150개, 실패 0, 오류 0, skip 0**, 2026-10-17 23:57 KST. `QUANT_EVIDENCE_DB_TEST=true`로 실제 MariaDB 임시 테이블 테스트 3개 포함.
- 신규 원문/스키마 입력 테스트 12개; 실제 DB 중복 적재·충돌 롤백·발표일 불일치 테스트 3개.
- `python -m unittest discover -s src/test/python -p test_quant_evidence_verifier.py`: 2개 통과. 원문 재작성/등급 덮어쓰기 실패를 먼저 재현 후 수정.
- 실제 원문 660개 SHA 확인. 전체 적재 재실행 후 660 source / 11,949 event / 3 manifest 유지.
- 출처 메타데이터 충돌과 부가 근거 유실을 독립 리뷰에서 발견해 수정했다. 신규 Java 회귀 테스트 3개 실패를 재현 후 통과 확인.
- `git diff --check`: 오류 없음(LF/CRLF 안내만 존재).

## 남은 외부 데이터와 미완료 항목

1. 전체 일별 구성종목을 검증하려면 완전한 기준 명단, 모든 변경 이력, 유효기간이 있는 증권 식별자·주식 종류 매핑이 필요하다. 일부 공식 공지를 역으로 이어 붙이는 것만으로는 생존편향이 해소되지 않는다.
2. 57개 조회 실패, 1개 봉만 반환한 6개 이력, ECHO 코드 재사용 의심 등은 별도 확인이 필요하다. 상장폐지 정산액·분사 교부주식·합병 선택권/대가의 전체 이력도 미확보다.
3. 8개 종목 과거 가격 불일치는 발견했지만 근거 없는 비율 추정으로 덮어쓰지 않았다. 해당 구간의 입력을 검증된 조정 기준으로 재구성해야 한다.
4. 수정 모델은 과거 당시 생성된 예측·입력/보정 데이터 버전이 없다. 새로 계산한 과거 값을 LIVE로 소급 기록하지 않는다. 출처 시점이 입증되는 입력을 확보한 재현 파이프라인 또는 실제 미래 관측기간이 필요하다.
5. 완전한 공식 구성종목과 기업행사 제공 경로는 확인했다: [S&P SPICE](https://www.spglobal.com/spdji/en//landing/topic/spice/)는 구독 서비스이며 S&P500 장기 구성종목/변경 자료를 제공한다. [Nasdaq GIW 안내](https://indexes.nasdaq.com/docs/GIW%20Website%20-%20Daily%20Pro%20Forma%20Guide.pdf)는 라이선스가 있는 지수와 계정이 필요하다고 명시한다. 사용 가능한 계정·계약은 확인되지 않았고 구매·가입·약관 동의는 수행하지 않았다.

위 누락 때문에 사용자가 요청한 **완전한 데이터 확보 및 모델 성능 검증 완료는 아직 불가능**하다. 추가 권한 없이 할 수 있는 공개 자료 확보와 누락 진단은 수행했으며, 미확보 자료를 임의 생성하거나 통과 기준을 낮추지 않았다.

## 재실행

먼저 로컬 DB에 추가 스키마만 적용한다(기존 schema/data 전체 초기화는 하지 않는다):

```powershell
& .\.tools\mariadb-11.8.2-winx64\bin\mariadb.exe --host=127.0.0.1 --user=root --database=king_yurina --execute="SOURCE C:/dev/codex-demo/src/main/resources/db/quant-historical-evidence.sql;"
```

애플리케이션 실행 인자:

```text
--spring.profiles.active=mariadb
--spring.sql.init.mode=never
--spring.main.web-application-type=none
--app.signal.refresh.enabled=false
--app.signal.refresh.run-on-startup=false
--app.batch.historical-evidence.bundle=tmp/pit-acquisition/actions/bundle.json
--app.batch.historical-evidence.exit-on-complete=true
```

동일 방식으로 `actions/official-bundle.json`, `membership/bundle.json`을 적재한다. 새 원문이 실제로 필요할 때만 획득 스크립트를 사용한다. 현재 파일은 보존하며 동일 경로 덮어쓰기를 피한다. 이 변경은 커밋·푸시하지 않았다.
