# S&P500 일별 예측 저장과 실패 복구

## 고정 계약

기존 봉인 프로토콜과 모델을 그대로 사용한다. 다음 거래일 시가 진입,
익절15%·손절5%·진입일 포함 최대20거래일, 확률 상위20%를 먼저 정한 뒤
지수50일선·변동성75백분위 필터, 기존 배분, 편도10/20bp 비용은 변경하지 않는다.
브로커 주문은 없으며 관심TOP20과 모의 계좌를 혼합하지 않는다.

## 실행

작업 폴더는 `C:/dev/codex-demo`, Python은 `C:/Users/user/anaconda3/python.exe`다.

```powershell
# 읽기 전용: 최신 완료 거래일과 기존 실행 폴더 확인
& C:/Users/user/anaconda3/python.exe scripts/sp500_paper_daily.py

# 일별 실행 또는 동일 폴더의 중단 지점 재개
& C:/Users/user/anaconda3/python.exe scripts/sp500_paper_daily.py --run

# 저장된 예측은 유지하고 발행 단계만 복구
& C:/Users/user/anaconda3/python.exe scripts/sp500_paper_daily.py --out <기존-runDirectory> --stage publish
```

자동 선택은 최신 완료 거래일의 기존 예측이 있으면 그 데이터셋 부모 폴더를 사용한다.
없으면 `output/sp500-paper-daily/<판단일>`에서 시작한다. 기존 실패를 우회하려고
새 폴더를 지정하지 않는다. 첫 실제 실행 폴더는 `output/sp500-paper-daily-20261117`이다.

## 단계와 재개 규칙

1. `config.json`: 기준 판단일, 이전 데이터셋과 입력 해시를 고정한다.
2. `sources/*.json`: 공급자 응답과 SHA256을 개별 보관한다. 완료 응답은 재호출하지 않는다.
3. `acquisition.json`: 전체 구성종목·지수·구성 변경 확인 자료의 완료 목록이다.
4. `dataset/verification.json`: 독립 검증 통과 후에만 다음 단계로 간다.
   미완성 빌드는 같은 원본으로 재생성할 수 있으나 이미 저장된 예측의 입력은 변경하지 않는다.
5. 원래 `sp500_paper_ledger.capture`가 예측과 영수증을 개장 전에 생성한다.
   기존 유효 예측은 재사용한다. 예측만 있고 영수증이 없으면 자동 소급 복구하지 않는다.
6. 같은 판단일/가격 해시의 기존 모의평가가 있으면 재사용한다. 없으면 기존 엔진으로 평가를 추가한다.
7. `paper-web-before.json`, `paper-web-after.json`, `publication-prepared.json`을 먼저 보관한다.
   이후 비교-후-교체와 DB 읽기 확인으로 발행한다. DB만 반영된 채 중단됐어도 동일 결과를 확인하여 재개한다.
8. `complete.json` 이후 재실행은 원본/예측 해시와 현재 발행본을 확인한다.
   수집·예측을 반복하지 않는다. 준비된 발행 전 상태로만 돌아간 경우 해당 준비 파일로 복구하며,
   다른 작업이 발행한 상태와 충돌하면 덮어쓰지 않고 중단한다.

`failure-*.json`은 중단 단계와 원인을 보존한다. 접근거부403/429, 공급자 검증도전,
원본 해시 변경, 편입편출, 분할/분사, 가격 기준 불일치는 자동 재시도 대상이 아니다.
실패 소스 영수증은 삭제하지 않는다. 네트워크 실패도 무제한 재시도하지 않고 검토 후 재개한다.
당시 개장 시한을 놓쳤으면 예측을 소급 저장하지 않고 누락으로 남긴다.

## 중복 수집과 남은 제약

- 전체 실행은 `data/quant-daily/paper-daily.lock`, 공급자 수집은 기존 `collection.lock`을 사용한다.
- 진행 중인 공급자 배치와 최근403/429를 확인한다.
- 다른 배치가 새 DB 가격을 이미 저장했다면 중복 수집 대신 `NEW_DB_PRICES_REQUIRE_REUSE_RECONCILIATION`으로 보류한다.
  DB 가격만으로는 기업행사 원문을 확인할 수 없어 자동 합성하지 않는다. 이 경로의 완전한 DB 재사용은 후속 과제다.
- 현재/과거 구성종목이 달라지거나 중간 변경 공지가 있으면 명시적 검토가 필요하다.
- 새 일봉은 기존 OHLC 겹치는 구간 및 기업행사를 검증한다. 공급자 조정 가격과 과거 데이터의 기존 한계는 남는다.
- 달력은 봉인된 달력을 사용한다. 만료 후 평일 추정으로 연장하지 않는다.

## DB와 사이트 기동

DB가 내려가 있으면 기존 설정과 데이터 디렉터리로만 시작한다. 초기화나 복원을 하지 않는다.

```powershell
Start-Process C:/dev/codex-demo/.tools/mariadb-11.8.2-winx64/bin/mariadbd.exe -ArgumentList '--defaults-file=C:/dev/codex-demo/.tools/mariadb-data/my.ini' -WindowStyle Hidden -WorkingDirectory C:/dev/codex-demo
```

사이트가 내려가 있을 때만 포트8080의 리스너를 확인하고 현재 컴파일된 클래스로 시작한다.
Java21, `target/classes`와 `tmp/quant-runtime-classpath.txt`를 사용한다.
필수 인수는 `--spring.profiles.active=mariadb --server.port=8080 --spring.sql.init.mode=never
--app.signal.refresh.enabled=false --app.signal.refresh.run-on-startup=false
--spring.config.import=optional:file:./config/absent-top20.yml`이다.
`Start-Process -WindowStyle Hidden`을 사용하고 DB 초기화/자동 신호 갱신은 실행하지 않는다.

## 자동화

사용자 승인으로 `Quant 일별 예측·복구` 자동화(id: `quant`)를 등록했다.
한국시간 화~토07:30와21:30에 실행한다. 두 번째 실행은 완료 여부 확인 또는 실패 지점 복구이며
완료된 예측을 다시 만들지 않는다. 기존 `v9` 예약은 복원하지 않았다.
PC와 Codex가 실행 가능해야 한다. 예약 등록은 실제 실행 성공 보장이 아니며 장애는 별도로 보고한다.

## 2026-11-17 실행 결과

- 판단일2026-11-16, 다음 개장2026-11-17 14:30UTC(23:30KST).
- 원래 모델 예측 저장2026-11-17 12:41:56UTC(21:41:56KST).
- 구성종목503개 모두 최신 일봉 기본 품질 검사 통과. 기존 모델의 과거 이력 조건을 충족한495개를 예측했다.
- 매수 조건 통과0개. 관심TOP20은 그대로 표시하며 기존 전략의 현금 대기를 유지한다.
- 이전 예측·영수증·모델·프로토콜은 보존했다. 새 판단일/영수증과 같은 전략의 새 평가만 추가했다.
- 개별 응답 재사용, 해시 불일치 거부, 편입편출 거부, 발행 충돌 및 DB 반영 직후 중단 복구를 테스트했다.
