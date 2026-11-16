# 무료 공식 상장폐지·합병 근거 추가 확보 — 2026-10-18

## 결과

기존 가격 공급자 조회가 실패한 **57개 심볼 중 8개**에 대해 새로운 공식 기업행사 근거를
확보했다. 이는 **일별 가격 누락 8개를 해결했다는 뜻이 아니다**. 새로 내려받은 응답은
**9개 / 9,837,807바이트**이며, 그중 JNJ 점검 안내문 1개(556바이트)를 내용 검증에서
거부했다. 유효 공식 원문은 **8개 / 9,837,251바이트**, 정규화한 행사 근거는 **8건**이다.
모든 파일은 새 `tmp/free-quant-extra-20261018/` 안에 있으며 기존 원본을 수정하거나
다시 수집하지 않았다. 기존 660개 source의 URL과 새 9개 URL의 정확 중복은 **0개**다.

데이터 품질 skill에 따라 현금 대가·주식 대가·조건부 권리·미확정 회수액을 분리했다.
검증 skill에 따라 내려받은 원문 해시와 실제 본문을 다시 읽어 확인했다. 이 작업은 별도
원천 확보 작업이며 Java·스키마·DB·가격·예측·모델·기존 연구 결과를 변경하지 않았다.

## 실제 확보한 행사

아래 금액·비율은 **거래 당시 적격 보통주 1주 기준**이다. 과거 공급자 조정가격 기준이나
인수기업의 이후 분할 기준으로 자동 변환하지 않았다. 발표일, 효력일, 마지막 거래일,
거래정지일, 실제 계좌 정산일은 서로 다른 개념이다.

| 기존 누락 심볼 | 공식 확인 사실 | 사용상 제한 | 원문 |
| --- | --- | --- | --- |
| CERN | 2022-06-08 장 시작 전 합병 완료; $95 현금; 마지막 거래일 6월 7일; suspension 6월 9일; CUSIP 156782104 | 현금 입금일·당시 가격 미확보 | [Nasdaq Trader](https://www.nasdaqtrader.com/TraderNews.aspx?id=eca2022-127) |
| MXIM | 2021-08-26 장 시작 전 완료; ADI 0.630주; 마지막 거래일 8월 25일; suspension 8월 27일; CUSIP 57772K101 | ADI 당시 평가가격·단주 정산 미확보 | [Nasdaq Trader](https://www.nasdaqtrader.com/TraderNews.aspx?id=ECA2021-172) |
| SPLK | 2024-03-18 인수 완료; $157 현금 | 가격 이력·개인별 정산일 미확보 | [Cisco](https://newsroom.cisco.com/c/r/newsroom/en/us/a/y2024/m03/cisco-completes-acquisition-of-splunk.html) |
| XLNX | 2022-02-14 인수 완료; AMD 1.7234주와 단주 현금 | 단주 현금 금액·입금일 미확보 | [AMD](https://www.amd.com/en/newsroom/press-releases/2022-2-14-amd-completes-acquisition-of-xilinx.html) |
| DRE | 2022-10-03 인수 완료; PLD 0.475주 | 확보 문서는 10월 28일 공시; 효력일 당시 알려진 입력으로 사용 금지 | [Prologis 10-Q](https://ir.prologis.com/financials/sec-filings/content/0001564590-22-035526/pld-10q_20220930.htm) |
| FRC | 2023-05-01 은행 폐쇄·FDIC receivership; JPMorgan가 예금을 인수 | 주주 회수액은 **미확정**, 예금보호를 주식 회수액으로 바꾸지 않음 | [FDIC](https://www.fdic.gov/news/press-releases/2023/pr23034.html) |
| ABMD | 2022-12-22 인수 완료; $380 현금 + 조건부 CVR 최대 $35 | $415 확정 현금으로 합산 금지; CVR 실현·정산 미확보 | [SEC에 제출한 JNJ 발표문](https://www.sec.gov/Archives/edgar/data/200406/000119312522311072/d428734dex991.htm) |
| SGEN | 2023-12-14 인수 완료; $229 현금 | 확보 문서는 2024-01-30 실적 발표; 효력일 당시 예측 입력으로 사용 금지 | [SEC에 제출한 Pfizer 발표문](https://www.sec.gov/Archives/edgar/data/78003/000007800324000017/pfe-12312023xex99.htm) |

유효 행사일 범위는 **2021-08-26–2024-03-18**이다. 각 행은 한 종목의 특정 행사 사실이며
해당 종목의 모든 배당·분사·티커 변경 이력이 아니다. 정확한 종목/주식종류 유효기간을
갖춘 security master도 아니다. 인수기업의 현재 가격을 소멸한 종목 가격에 대신 넣지 않는다.

## 실패도 보존한 획득 기록

총 10개 독립 URL을 각각 한 번 요청했다. 최초 8개 경로 중 Pfizer는 HTTP 403,
JNJ는 HTTP 200이지만 실제 내용은 `Site Maintenance`였다. JNJ 556바이트 원문은 그대로
보존했지만 행사 정규화에서 제외했다. 두 실패는 최초 manifest에 남아 있다.
차단/점검 페이지를 재시도하거나 접근제한을 우회하지 않았다. 별도의 공개 SEC 제출
문서 2개를 새로운 `sec-supplement/` namespace에 확보해 ABMD·SGEN 근거를 추가했다.

원문 보존 시각은 **2026-10-18T01:47:29Z–01:48:16Z 부근**이며 정확한 요청 시작시각은
각 manifest의 `retrievedAt`에 있다. 현재 내려받은 시간은 과거 발표를 수신한 시각이 아니다.
Nasdaq 두 공지는 사후 업데이트된 페이지이므로 화면 최초 게시일(2022-06-03,
2021-08-23)을 완료 사실의 역사적 가용 시각으로 사용하지 않는다. 해당 source의
`publishedDate`는 null이며 `initialPageDate`에만 화면 최초 날짜를 보존했다.
나머지 발표문 날짜도 source text의 달력 날짜이며 SEC 접수 초·분 인증이 아니다.

무료·무계정 공개 접근만 사용했다. 결제, 가입, API key, 라이선스 동의, 타인의 계정,
접근제한 우회는 없었다. 공개 열람 가능하다는 사실은 원문 전체·지수 데이터의 오픈
재배포 권리를 뜻하지 않는다. URL·발행자·수집시각·응답 상태·크기·SHA-256·검증할
본문 값·권리 미확인 상태를 함께 보존했다. 원문 corpus를 외부 공개하지 않았다.

## 검증과 artifact 계약

각 bundle은 `PARTIAL_PRIMARY_TERMINAL_EVENT_EVIDENCE`이며 `OFFICIAL_PIT`가 아니다.
`sources[]`는 원문 provenance, `events[]`는 sourceId로 원문을 참조하는 명시적 근거다.
두 bundle 사이에서 유효 행사 종목 중복은 없다. 정규화한 수치는 문자열로 보존하며
부동소수점 수익률이나 price candle로 변환하지 않는다.

- 원문 9개 해시·바이트 수와 bundle 해시 재검사: 일치.
- 유효 행사 8건의 source 참조, 중복, 원문 본문 값, 사전에 명시한 행사 필드: 일치.
- 모든 행사 종목이 이전 57개 공급자 실패 목록에 속함: 확인.
- 본문에 없고 script 안에만 있는 값, 누락 주장, CVR 합산, FRC 회수액 0 대체를 막는
  focused Python 검사: **6개 통과**.
- 본문 검사에서 실패한 JNJ 페이지는 해시 검증 통과 원본이지만 **유효 근거가 아니다**.
  `VERIFIED`는 저장·계보·파싱 계약 검증이며 모든 요청 성공이나 경제적 완전성 인증이 아니다.

재현 도구: `scripts/acquire_quant_extra_evidence.py`.
`--acquire`는 이미 있는 manifest/raw 파일 덮어쓰기를 거부한다. 아래 명령은 네트워크 없는
검증만 수행하며 원본과 최초 검증 기록도 덮어쓰지 않는다.

```powershell
python -m unittest discover -s src/test/python -p test_acquire_quant_extra_evidence.py -v
python scripts/acquire_quant_extra_evidence.py --verify
python scripts/acquire_quant_extra_evidence.py --verify --supplement
```

| 파일 | SHA-256 |
| --- | --- |
| `tmp/free-quant-extra-20261018/manifest.json` | `ca624c5761c765ae21539e834b25580031a0ddc6053dadbbf82630d18f848795` |
| `tmp/free-quant-extra-20261018/bundle.json` | `02db557a9dcbe8e23ea154606cddef207db54755442bec61765500c983ecfe0f` |
| `tmp/free-quant-extra-20261018/sec-supplement/manifest.json` | `8390c8331c1b917d227e00b0017df00d974f6bf5e74592b47d8209083511f629` |
| `tmp/free-quant-extra-20261018/sec-supplement/bundle.json` | `1bae7a611807f44a3fc7634544ff0b7a9f353e805108b0176d077f90ef472ae7` |

## 예측·백테스트에 어떤 공백을 줄였는가

줄어든 공백은 **8종목의 소멸·대가·거래중단 사유에 대한 공식 원문 부재**다. 예를 들어
MXIM과 XLNX를 무조건 $0 청산하거나, ABMD의 최대 CVR를 확정 지급액으로 처리하거나,
FRC의 예금 인수를 주주 인수로 해석하는 오류를 검증하는 근거로 사용할 수 있다.

남은 공백은 해당 종목의 거래일별 OHLCV, 적격 보유 증명, 통화/주식수 조정 기준,
단주 처리, 실제 지급일, 상장폐지 후 OTC 가격·회수액, 전체 공식 구성종목 기준명부와
모든 변경 사슬, 과거 입력의 실제 발표·수신시점이다. **기존 57개 가격 조회 실패는
57개 그대로**이며, 신규 OHLCV 관측값은 0개다. 나스닥 2026-10-16 벤치마크도
이 작업에서 새로 확보하거나 같은 경로를 반복 조회하지 않았다.

현재 `StockHistoricalEvidenceBundle`은 ADD/REMOVE/SPLIT/DIVIDEND만 지원하므로 이
행사들을 기존 SPLIT/DIVIDEND 형식으로 바꾸어 적재하지 않았다. 가장 작은 안전한
후속 통합은 별도의 terminal-event staging/diagnostics에서 원문 해시와 행사 유형을
보존하고 누락가격 제외 사유를 구체화하는 것이다. 실제 포트폴리오 정산은 현금/주식/CVR
및 지급·단주 규칙을 지원하고 별도 회귀 검증을 갖춘 후에만 가능하다.

이 증거는 공식 행사 사실을 보강하지만 예측 모델 성능, 완전한 PIT 모집단, 총수익률
백테스트 완료를 입증하지 않는다. 이 독립 획득 작업에서는 DB 읽기/쓰기, Maven,
애플리케이션 재시작, Git stage/commit/push를 수행하지 않았다.
