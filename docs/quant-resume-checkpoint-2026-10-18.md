# Quant work checkpoint — user-requested PC shutdown

## Latest November9 — same-anchor outcome marker collision, not completed

Read-only plan anchorNovember6/missingIndices[] then `--run --publish-status` failed exit1: `data/quant-daily/20261109T105624Z-6b7a64f3/failure.json`. FileExistsError on preserved `data/quant-daily/outcome-attempt-2026-11-06.json` from successful November8 run. maintain_outcomes uses20hour recent-attempt reuse but a create-only anchor-keyed marker; after that reuse window, persistent KVUE missing labels enter marker creation again and collide. No marker deletion/overwrite or forced provider retry performed. This requires focused retry-policy regression/fix, not claiming success.
Failure recovery preserved18baseline evaluations/3769pending plus candidate report under failure-evaluation; no new forecasts or maturity claim. Status publication was not reached; prior committed status remains. DB10061 recovered with existing script/log copies, no initialization. Latest Yahoo logNovember8 18:46:25; no new collection reached in this failed attempt. Dirty main/worktrees preserved; no model/source/code/commit/deployment changes. Next work: test same-anchor rerun beyond20hours with terminal missing prices, preserve immutable attempts and permit evaluation/status without unjustified repeat collection.

## Latest November8 — existing anchor maintenance only

Read-only plan anchorNovember6/missingIndices[] followed by `--run --publish-status`, `data/quant-daily/20261108T094124Z-ce8f236c/result.json`, exit0/COMPLETED. No new forecast/frozen manifest; collectionSkippedAlreadyFrozen=true refers to forecast collection, not outcome price maintenance. Outcome maintenance did collect prices: Yahoo55 COMPLETED513success/0fail,128080upserts; canonical remained711550/latestNovember6, net0. Required2565stock/15benchmark closes; KVUE November2-6 five missing stock closes remain, no missing benchmark closes or fabricated prices. INCOMPLETE_LABELS retained.
Baseline3769 across18cohorts and candidate30 remain pending; no new maturity/error or promotion. Status3rows COMMITTED, forecastWritesfalse/schemaWritesfalse, SHA8afb66217f92512cc6241e2a565bec3fdf7b0632635633641cc347ce6cc0a4b7. MariaDB10061 recovered via existing startup/log preservation, no initialization. No new cause/fix for permanent startup established.
Latest instructions/checkpoint/history/IFRS/source queue read; no new historical recovery claimed or previous append repeated. Dirty main/worktrees preserved; no model/code/transport/deployment changes. Routine unchanged results do not require notification. Retain existing missing-price diagnostics and maturity gates.

## Latest November7 — anchor November6 completed

Read-only plan then `--run --publish-status`, output `data/quant-daily/20261107T093756Z-d89c102f/result.json`, exit0/COMPLETED. Actual09:37:56UTC start, not punctual15:10KST evidence. MariaDB10061 recovered with existing script/log copies, no schema initialization; permanent startup issue remains unresolved.
Yahoo54 requested518/success517/fail1,128607upserts (not new rows). Canonical711550/latestNovember6, net512. WBD NO_VALID_CANONICAL_ROWS/rejected1/canonical0; canonical history preserved. Universe30DOW/103NASDAQ/503SP500. Recent logged errors0 does not establish current Finnhub/SEC permission; last logsOct17/July7. No additional provider batch manually launched.
Run6ea5abbe-cbdc-4716-a26c-3b030906c92e V2-admitted30DOW+101NASDAQ+497SP500=628 new observations, previous3141 retained. Baseline3769 across18cohorts pending, separatecandidate30 pending; no mature error or promotion. Publication COMMITTED/scopedRows3/forecastWritesfalse/schemaWritesfalse, SHA7bb2a33b3c7e59138617fd76f5f27f32b0f89c60277403dd3e5b9219f2031577.
Read latest checkpoint/plans, historical/IFRS notes/source queue and November5 operating bridge results. No repeated append/research/download, no new historical recovery claim. Existing main dirty/worktrees preserved, no code/model/commit/push/deploy changes. Continue frozen cohort price maintenance and independent maturity gates; historical price/PIT evidence gaps remain open.

## Latest November6 — anchor November5 daily execution completed

Read-only plan followed by `--run --publish-status`: `data/quant-daily/20261106T120647Z-3e077afc/result.json`, exit0/COMPLETED. Actual start12:06:47UTC (not15:10KST punctuality evidence). MariaDB10061 recovered through existing script after timestamped log preservation; no initialization/schema SQL. Permanent automatic startup remains unresolved.
Yahoo53 requested518/success517/fail1,128604upserts; canonical711038/latestNovember5, net512. WBD NO_VALID_CANONICAL_ROWS/rejected1/canonical0 preserved old canonical. Current universe DOW30=30/NASDAQ100=103/SP500=503. Recent logged API errors0, but Finnhub last logOct17/SECJuly7 are stale, not fresh permission evidence; no additional provider batch manually launched.
Run68292a1a-6aa2-4b3a-9d5d-a64329d1109a admittedV2/froze30DOW+101NASDAQ+497SP500=628. Existing2513 retained, baseline now3141 across15cohorts pending; separate30candidate remains pending, mature0/no promotion. Publication COMMITTED/scopedRows3/forecastWritesfalse/schemaWritesfalse, SHA c7512ebacd6df9ca9f34a099004d5486c1766e18db8ff7c9e7783fe70fb2c8de.
Read November5 source-financial operating results before execution: AMGN/GS8documents already admitted, not pending DB bridge and not reapplied. Forward helper explicitly enables source-financial policy; this successful new run is after that change, not evidence that old2013-19documents replace latest quarters or improve20Derror. History/IFRS notes and accession queue reviewed; no historical recovery or new holdout claimed. Existing dirty main/worktrees preserved; no code changes, commit, push or deployment. Continue immutable cohorts and maturity/eligibility gates; remaining historical price/PIT evidence work remains open.

## Latest November5 21:15 KST — AMGN/GS source-aware input admission and actual automation verified

최신 결과 `docs/quant-source-financial-operating-results-2026-11-05.md`, 계획 `docs/superpowers/plans/2026-11-05-source-financial-operating-bridge.md`, 증거 `tmp/quant-source-operating-20261105-v1/`. 이전 “AMGN/GS 운영 연결 pending”은 이번 별도 source 버전에 대해 갱신됨. 원래14exact누락/189복구장부는 그대로다.

새 표 `stock_source_financial_document`에 AMGN7/GS1 원문8행 실제추가. 버전 SOURCE_FINANCIAL_BRIDGE_V1_20261105, 검토된 extractionpackSHA dd0a028de57e6261485cb36393a82ba2fb15bd39293932a90de5b46251fc1912 고정, artifactSHA84c0bc4236a0b5ec58a852c2fe134683310e20145a5e694a08cf48b5e24b3a79. Python봉인/정확재구성+Java검토packpin후DB연결. 별도DDL/8INSERT실제rollback→0행검증후8행commit; 실제admission12:04:34UTC무렵. 재반영/최초관측시각덮기금지. SEC기존표준65251/원천706394/예측2527202/API71073와 AMGN/GS기존행hash/원래12cohort+별도bundle13파일SHA불변.

기본sourcefeaturefalse; 새전방batchhelper만true명시, 신규입력에configured정책/클래스해시/선택원문보존. 현재/직전분기운영cache와historicalseed경로연결, annual/weights불변. AMGNfiled/accessionnull유지, GS원문Apr1-Jun30은legacyMar31과별도전체statement로선택(기간간필드혼합금지), 동일기간기존null매출만detached보완. 공개일당일금지/실제admissiontime별도, 과거재구성은완전PIT아님.

실제DBresolver8원문다음날선택과당일현재ID미선택확인. 2015당일이미알려진이전원문fallback은해당새원문사용과구분. 현재liveAMGN/GS분기는legacy/selected모두2026-03-31로같음(오래된2013~19자료로최신입력억지교체없음). 실제2019-01-30이전AMGN가격0행/최소80미달로전체historicalfeaturebuildnull, 실패probe/진단보존; 가격임의생성안함. 실제과거모델재생·20D오차감소개선완료아님. 가격fixture자동테스트에서는실제buildSnapshot의별도q4-srcfin-20261105/선택증거보존확인.

Java전체307중280실행PASS27skip, Python전방/원문80PASS, operatorpolicy2PASS. 독립검토Important2(25자버전→VARCHAR20저장실패,검토된pack정체성pin누락)실패테스트→수정→전체suite통과. 작업mainb134d9fdirty보존, stage/commit/push/deploy/weightpromotion없음. beforeimage23파일 data/quant-preservation-20261105-source-before-v1/; finalreceipt/afterimage는같은실행폴더참조.

독립automation-check: Nov4/5실제각628(30/101/497)생성/V2admit/동결,1048결합artifact/이전91파일불변/21저장평가정확재구성. 기준2513관측12cohort+별도후보30모두pending/n0/MAEnull,새성능주장없음. 가격710526행/latestNov4;WBD실패/기존canonical보존지속. 예약ACTIVE15:10KST지만실제시작21:42/20:30KST=>정시성/PC꺼짐/DB상시uptime증명아님. 새예약/OS서비스설치없음.

변경후평가전용 `data/quant-daily/20261105T121148Z-7cfe0963/` COMPLETED/collectionSkippedAlreadyFrozen=true/상태3행게시. 현재세션새예측중복생성없음. 새source정책으로다음새코호트수집은다음실제세션실행후별도확인필요. 현재반영을미래오차감소로세지말것. 필요한과거가격/모집단/미사용구간자격과만기정답이후에만새성능검증;old60재튜닝금지유지.

## Latest November5 — daily anchor November4 completed

Actual run `data/quant-daily/20261105T113022Z-8e810cdc/result.json`: `--run --publish-status` exit0/COMPLETED. Read-only plan preceded execution. MariaDB10061 recovered with existing startup script after timestamped log copies; no schema initialization. Permanent service transition remains unresolved.
Yahoo52 requested518/success517/fail1,128601 upserts (not new rows); canonical710526/latest2026-11-04, net512 over previous710014. WBD NO_VALID_CANONICAL_ROWS/rejected1/canonical0; existing canonical preserved without retry or invented close. API log latest FinnhubOct17/SECJuly7; no additional provider batch was manually launched, and zero recent logged errors does not establish fresh provider permissions.
New V2 admissions30DOW/101NASDAQ/497SP500=628; previous1885 unchanged, now2513 baseline observations across12cohorts, all pending. Separate candidate30 pending, mature0, no performance claim/promotion. Status receipt COMMITTED/scopedRows3/forecastWritesfalse/schemaWritesfalse, SHA d0b17665422d7f20629a0209e7b116c8a62ba72de8ceeb33928fe93456905f74. Actual start11:30UTC, not evidence of punctual15:10KST execution.
Latest checkpoint/plans/execution notes/history/IFRS notes/source queue read. No repeat historical append/download/research tuning; AMGN/GS source-aware production mapping remains pending, no new historical recovery claimed. Dirty main confirmed through escalated read-only git status; unrelated files preserved. No commit/push/deployment. Next run retain all frozen evidence and continue known-session prices/evaluation, with independent eligibility and maturity gates unchanged.

## Latest November4 — daily V2 admission and status publication completed

Actual run `data/quant-daily/20261104T124218Z-55ecbfd3/result.json`: `--run --publish-status` exit0/COMPLETED, anchorNovember3. MariaDB10061 recovered using existing script/log preservation, no schema initialization. Yahoo51 requested518/success517/fail1,128598upserts; canonical710014/latestNov3 (prior709502, net512). WBD NO_VALID_CANONICAL_ROWS/rejected1; basis guard preserved old canonical. No retry or fabricated KVUE close.
New V2-admitted cohorts30DOW/101NASDAQ/497SP500=628, actual UUID/source evidence/success receipts retained. Prior1257 unchanged, now1885 baseline observations across9cohorts; candidate30 unchanged. No mature performance claim or promotion. Status receipt `status-published/receipt.json` COMMITTED/scopedRows3/forecastWritesfalse/schemaWritesfalse, publicationSHAb583ba331b17a46b82e2706e0561dbc7fc93babc865f048f189183a78c5d1363. Initial run timestamp12:42UTC proves actual execution, not15:10 punctuality.
Latest execution/worklist, IFRS/history notes and accession queue read. No historical re-download/US-IFRS-EPS reapplication/model study repeated. Source-aware AMGN/GS remains research-only; global unseen eligibilityUNKNOWN; inactive candidate not activated. Service transition and remaining provider freshness work remain open. Existing dirty main/user files preserved; no Git transport/deployment changes.

## Latest November3 23:43 KST — 후속 1–12 실행, 운영 안전/화면 반영; 성능 승격 없음

최신 결과: `docs/quant-20d-execution-results-2026-11-03.md`. 실행 장부 `.superpowers/sdd/2026-11-03-quant-20d-next-worklist/ledger.md`, 증거 `tmp/quant-20d-execution-20261103-v1/`. 아래 감사 당시 “운영 수정 없음/서버 미기동”은 그 당시 사실이며 이번 후속 실행으로 갱신됐다. 과거 원문·예측·실패 기록은 보존한다.

현재 main HEAD b134d9f, 기존 dirty 변경 보존. 코드/자료/동결 예측 before-image847개: `data/quant-preservation-20261103-v1/manifest.json`. 이번 변경은 기존 코호트 정답 유지 vs 새 예측 분리, 신규 runID/사전 정확 이름/성공 영수증/입력증거 V2 admission, 실제 선택 입력·순위 factor·클래스 해시·V9 직전 출력 보존, KVUE 진입 불가 진단, 별도 DB 검증 상태 UI다. 기존 6개1257예측+별도30후보 값/분모/진입청산/역사기준null 변경 없음. 새 실제 배치를 같은 세션에 중복 생성하지 않았다.

연구/운영 대조 1976exactkeys=966운영키없음+1008원시필드차이+2완전일치(오예측수아님). 189매출 exact운영키 미반영136행없음+53null. AMGN7/GS1의8원천정체성·14연결을 별도 dry-run정규화, 원래203분모/14미해결유지. 운영source-awareversion이 필요해 가짜accession/기존기간덮기 없음. 전역노출장부UNKNOWN, 새미사용과거인증0; 기존60정답재튜닝금지유지.

후보1개 강건residual보정 프로토콜은 `candidate-protocol/sealed-protocol.json`에 비활성 준비만 했다. 2026-11-03T14:33:44.183320Z 봉인, SHA67d484ac921cbc0dde774a5fa755f9c70a1c45139a931ae6d1b8a3d5c86c27bf. 적격post-seal성숙블록0. 소스해시드리프트시 기존봉인수정말고 새미래버전필요. 운영코드활성화/새오차감소주장아님. 기존노출609일17904행 V9MAE5.749702649/4.405489367→재무V3 5.757472208/4.410768581악화, V9/가중치유지.

실제 `data/quant-daily/20261103T143939Z-0194d5e3/`는 --run --publish-status 완료. 원래예측그대로pending, KVUE가격조작없음, 상태DB3행별도버전만갱신. 게시물봉인/원본평가정확재구성/파일SHA확인후DB연결, backup/실제rollbackprobe/postimage통과. 만기전오차null/미평가, 원래분모/진입불가표시. 최초12월1일미장종가=12월2일06:00KST이후, 다음12월3일06:00이후 실제가격완전성따라평가.

Java298중271실행PASS27skip/실패0; Python326관련suitePASS+새게시물위변조회귀1PASS. 독립검토1회+Important수정재검토1회, 미해결Critical/Important0. 선택후속preV9개별파일재해시는미실시(영수증에는hash경로보존). GET전후API70037/예측2497058/표준65251변화없음, 동결7파일SHA동일. 서버8080PID29016(초기화/자동refresh끔). 첫GET5.254초/후속55.65ms, desktop1440/mobile390검증. 예약market-data-hourly-guard ACTIVE매일15:10KST를지원도구로새CLI경로연결; 다음정시성공증명아님, PC/Codex/DB필요. commit/push/merge/외부배포/모델승격없음.

다음은 실제새세션입력증거수집/근거있는source-aware운영버전·필드충돌판정/원래코호트만기오차다. 성숙시간을기다리는동안같은훈련결과를재계산해새성능증거로세지말것. 관련재시작명령·전후보존·검증파일은최신결과문서참조.

## Latest November3 — 전체 Quant 작업 검수 완료(중요 경로 범위); 운영 수정 없음

사용자 전체 검수 요청 결과: `docs/quant-full-work-audit-2026-11-03.md`, 그림 `docs/assets/quant-full-work-audit-20261103-errors.png`. 근거 `tmp/quant-full-work-audit-20261103-v1/`의 data-review/model-review/forward-code-review, source-SHA/독립 산술 JSON, read-only SQL/TSV, coverage-v2 장부, root-verification. 이전 실험/원본/아래 checkpoint는 보존한다. 이번은 audit-only: 모델 재적합·후보 탐색·기존60재계산·DB/예측/원천/스케줄/제품 코드/서버/Git 브랜치·index·commit·push 변경 없음. 새 감사 문서와 증거만 작성.

판정: 데이터/검증 기반 수리는 실제 성과이나 실제 미래20D오차 감소 모델 확보는 미완료. 안전한 기존 훈련 자료25,473쌍/26,401endpoint·20session을 독립 검산, root도주609dates/17,904rows 재검산. EPS기준V9 MAE5.749702649/4.405489367, 재무보완V3 5.757472208/4.410768581, 같은0예측5.813336780/4.405099990, 과거중앙값5.688408910/4.405343790. V3는기준양MAE악화/초과는0보다도높음;중앙값rawRMSEtradeoff라승자승격X. EPS복구등일부표본개선은실재하지만미래개선미입증. V2→V3마지막126매출만의효과+.001744099/−.000195221pp와전체재무팩효과를혼동하지말것. 고정혼합반λ0기준복제. 숫자오류P0발견없음은검산범위에한정.

실제 SELECT 기준 원천706394/표준65251/기대수익2497058/일봉709502(532종목,Nov2최신). 현재유니버스30/103/503,Nov2가격30/101/497. 공식화면용정정평가최신Oct18 INSUFFICIENT_DATA/validmonths0, rawdatasettargets33983이지만unprovenmembership·missingforecast등으로datasetvalid0;연구/전방파일과별도. 8080연결거부,실화면/모바일/첫접속지연은미검증(서버기동안함). 자동화지원도구view후localmetadataACTIVE/매일15:10확인,한국시간대확인;정시성공/무중단/PC꺼짐시실행증명아님.

독립 원문 감사:355EPS계획/27캐시원본정합0오류/핵심비EPS변경0,189고유매출복구/1,976재무키동일/126null-only증분 확인. 연구매출·확장재무는운영DB전체반영아님. AMGN7원문표/GS원XBRL기간확인,원래14target미반영유지. 진짜미사용과거·당시공식모집단·입력도착시점·주식종류/기업행사/ADR/비달력IFRS등한계지속. sourcecounts를accuracy·새독립표본수로세지말것.

새로발견한운영P2(현재6manifest손상증거아님):①달력에새20D가부족하면controller가평가후return하여기존코호트종가/벤치취득도멈춤. 현달력lasteligibleanchorDec1/firstblockedDec2,현재두번째코호트exitDec2. 새예측허가와기존정답수집을분리할것.②사전eligible정확명단·성공영수증을동결admission과강제결합하지않아부분모델결과/실패후남은archive가자동동결될수있음. 현629+628은실제사전명단수와일치/실패0. runmanifest/명시누락사유검사필요. KVUE원래SP498의Nov2진입불가진단을별도노출,기준complete-case와후보all30규칙구분;기존분모/예측바꾸지말것.

전방6manifest·각archiveSHA·20session·진입전동결·후보14파일bundle검증,1257기준관측+별도30후보모두PENDING/독립1287표본아님. 첫만기Dec1US종가=Dec2 06:00KST이후21종가완전시평가. 코드/입력해시와모집단유지. 실제당시전체원천입력보존은없어완전PIT증명아님.

새전체검증1회:오프라인Maven286중259실행PASS/27환경조건skip/실패오류0;Python287+46+91=424PASS. MariaDB환경통합27을운영DB에서강제실행하지않음. root PASS영수증14문서링크/metric6sourceSHA/실제포착source+class9SHA/기존bundle·manifest불변확인. Maven생성일회성개발credential로그만redact. source/HEAD는b134d9f main,검수시작기존81modified/442untracked,종료81modified/444untracked(새보고서+그림),stage/commit없음.

다음권장순서:1기존전방정답유지와달력/실패경로분리→2거래종료·미예측분모와동결입장조건→3연구/제품as-of입력대조→4AMGN/GS소스정규화·가치있는과거자료→5노출장부·미사용자격사전동결→6시장공통/고변동오차의제한후보→7충분한전방만기평가후승격및제품표시/버전저장·배포. 동일훈련을계속보고새검증이라부르지말것. 사용자이번요청은검수이므로이수정들을아직적용하지않음. 원문보고서의실패·한계결론은대체로정직하며전체검수완료를전체제품/투자성능완료로전달하지말것.

## Latest November3 19:49–19:53 KST — daily evaluation clock repaired; KVUE structural entry gap identified

Follow-up to the 19:42 receipt below; its collection/failed-run evidence is retained. `scripts/quant_daily_forward.py` now reads live time at the evaluation phase instead of reusing the pre-collection start time. The selected anchor/capture time remains fixed. Explicit API `now` injection remains fixed for synthetic tests and still rejects evaluation preceding a future freeze; no timestamp clamping, backdating, maturity bypass, or changed target. Successful receipts include `evaluationAsOfUtc`. Exception-path reevaluation exports to a new `failure-evaluation/` subdirectory so an existing create-only `held-closes.csv` cannot conceal the original error. No provider retry/lock/attempt-marker/collection/Java-model logic changed.

Regression `tests/python/test_quant_daily_clock.py`: real synthetic archives -> real freeze -> real evaluator reproduces the earlier failure before the fix, then passes; calendar-blocked phase uses the current evaluation clock; fixed synthetic future-freeze failure retains its original message and two separate exports. Three new tests PASS. Final relevant suites: scripts287 PASS, tests/python46 PASS (333 total; only3 added this turn). Logs `tmp/quant-daily-clock-fix-20261103-v1/scripts-suite.log` and `forward-suite.log`. No application Java/model calculation change, so Maven/full historical studies were not rerun. Scoped implementation self-review completed; no subagents or extra review rounds.

Actual evaluation-only smoke after read-only preflight `missingIndices=[]/priceAndForecastCollectionRequired=false`: `data/quant-daily/20261103T104955Z-c972eaba/result.json` COMPLETED. Start10:49:55.696Z -> evaluation10:49:57.530Z. Controller SHA b98f75a2ea008f776830010e6d4708b0f4c1d54d89b06103bd472ee7852297da. CollectionSkippedAlreadyFrozen=true/newFrozenManifests=[]; all6 baseline manifests and candidate/protocol/bundle files retain exact hashes. Existing1257 baseline observations across two overlapping cohorts plus separate30 candidate forecasts remain PENDING/evaluated0/errornull, not1257 independent outcomes. APIlog70037 and expected-return rows2497058 identical before/after. Receipt `tmp/quant-daily-clock-fix-20261103-v1/live-smoke.json`; default-sandbox DB connection was blocked, then the same preflight-guarded read-only smoke succeeded with approved local DB access. No collection/API/model replay/DB writes.

Entry-price inspection of that same export: original Nov2-entry DOW30=30/30 and NASDAQ100=101/101, with both benchmark entry closes present; SP500=497/498 and benchmark present, missing KVUE. New Nov3-entry628 forecasts have no entry close yet because the US Nov3 session has not opened at inspection time; this is expected, not a failed fetch. Evidence `entry-coverage.json` in the same tmp folder.

KVUE is a structural gap, not evidence of a transient HTTP denial: latest stored close Oct30; existing Nov3 Yahoo request HTTP200/errornull. Official Kimberly-Clark November2 release (Transaction Details) confirms KVUE stopped trading at the November2 market open after acquisition; per-share consideration is $3.50 +0.14625 KMB. Official URL and diagnostic metadata: `tmp/quant-kvue-trading-cessation-20261103-v1/source-metadata.json`. Web tool read of the official page succeeded; a single local raw download returned Access Denied and was stopped without retry, local original/hash unavailable. This is not admitted DB data or proof of official index removal date. Original frozen KVUE remains untouched. Do not invent/forward-fill a November2 close, substitute KMB/merger value for a frozen price-only target, silently drop its denominator, or backdate this newly acquired event evidence to forecast freeze. The original SP500 cohort cannot be presented as a fully tradeable498-name cohort. A separately reported non-tradable-entry diagnostic remains a next-step implementation; existing scorer semantics were not changed here. DOW30 candidate paired comparison is unaffected by this symbol-specific gap.

Remaining historical14 revenue targets/source-aware GS/AMGN canonicalization and globally unused early-history readiness remain unresolved; no accuracy gain or model promotion is claimed. First candidate maturity still Dec1 US close/Dec2 06:00KST, requiring complete21 stock+benchmark closes. Existing60 held-out outcomes never opened/tuned. main/HEAD/index/commit/push/server/automation schedules unchanged; unrelated dirty files retained.

## Latest November3 19:42 KST — new November2 cohort and evaluation recovery

MariaDB10061 recovered with existing start script/log preservation; no schema initialization. Read-only plan selected November2, all3indices missing. Controller run `data/quant-daily/20261103T103438Z-b0c61b50/` preserved scoped backups, ran Yahoo518requested/517success/1fail,128086upserts (not new-row count), then actual forward30DOW+101NASDAQ+497SP500=628 new forecasts and froze all3. Existing629 and candidate30 retained; no promotion.
First run exited1 during evaluation: `now` captured before collection was earlier than new cohort freeze (`evaluation cannot precede cohort freeze`). Do not delete attempt marker or re-run collection. Verified read-only plan missingIndices=[]/collectionRequired=false, then safe evaluation-only resume `data/quant-daily/20261103T104214Z-5093f69a/result.json` COMPLETED/collectionSkippedAlreadyFrozen=true. All1257 baseline observations and separate30candidate remain pending, mature0/errorsnull. First failed run and receipts preserved. Root cause identified but controller timestamp code NOT repaired this turn; add a focused regression before changing evaluation clock in a subsequent run.
Historical source queue/latest IFRS/GS/AMGN notes reviewed, no historical append/re-download/model re-tuning. SEC403 remains a stop condition. Remaining historical14 revenue targets/source identity work and DB service transition unchanged. This is actual19:34-19:42 execution evidence, not a15:10 firing claim.

## Latest November2 22:48 KST — daily evaluation after DB recovery

MariaDB connection refused (10061); preserved logs and used existing `.tools/start-mariadb.cmd`, then SQL health passed without schema initialization. No RUNNING states in the four inspected provider batch tables. Current universe503/103/30, active525; last logged Yahoo execution October31 16:15:22, last24h logged API errors0 (not proof of live provider access). SEC source403 from the latest research checkpoint remains a stop condition; no provider retry or source re-download this run.
Read-only plan then `--run` completed: `data/quant-daily/20261102T134836Z-b8ee8c2d/result.json`, controller SHA67e0cace46317a1f8beb2d2f56621543a9ab984fa250b5f62fe878043bb3757f. AnchorOctober30 already frozen: collection/model calculation/new cohorts skipped. Original629 pending plus separate DOW30 candidate30 pending, evaluated0/all errors null. No promotion or historical experiment rerun. This is actual22:48 execution evidence, not evidence of a15:10 run.
Latest GS/AMGN evidence and original accession queue reviewed; no new historical rows admitted. Remaining14 unresolved revenue targets still require source identity/exact-period normalization; do not reapply previously committed US/IFRS/EPS repairs or repeat inspected model studies. DB service transition and FRED/Toss live permission checks were not performed. Existing source/forecast/protocol artifacts and unrelated working-tree changes preserved.

## Latest November1 22:52 KST — 후보 전방 평가 연결·GS 기간 원인·AMGN 원문 확보; 정확도 미입증

최신 결과 `docs/quant-candidate-evaluation-results-2026-11-01.md`, 계획 `docs/superpowers/plans/2026-11-01-candidate-forward-evaluation.md`. 아래모든이전실험/15:07heartbeat보존. 같은모델재생/기존원문재다운로드 불필요.

새 `scripts/quant_v9_blend_evaluate.py`:동결후보/원baseline/프록시/entryNov2exitDec1/달력21종가/전체30이름/파일해시 검증. 만기전또는1종목중간종가누락이면전체모델MAE/RMSE/bias·pairedDelta/zeroRef null. 후보재학습·새승자선택·promotion없음. create-only평가영수증.
내구bundle `data/quant-candidate-evaluation-20261101-v1/DOW30/bundle.json` SHA bd5225e226ff2281b3c68ff990ccc3145676e2abcebae736ade526647fed7b37,원출처14파일정확bytes포함. 임시연구파일없어도종가CSV만있으면offline평가가능. 기존30후보프로토콜07ae26c7...와예측/629원baseline수정X. 이runtime폴더및candidateforward폴더를.gitignore기존규칙에추가(기존무관16줄변경보존).
`scripts/quant_daily_forward.py`의evaluate_all이동일held-closes.csv로기준평가와후보평가를분리기록. 정상/달력만료/수집실패경로에연결. 변조·후보실패는별도FAILED_CANDIDATE_EVALUATION/CLIexit1;원baseline평가보존. 기존스케줄재생성·앱자동화설정변경없음;원래PC/Codex/DB가동조건같음.

실제smoke `data/quant-daily/20261101T135200Z-d8cfc3cc/result.json` exit0COMPLETED. 실행전3index동결·missing[]·가격/예측필요false확인후실행. 새수집/모델/새동결0;기준30+101+498=629PENDING,후보30PENDING/평가0/전오차null. APIlog69519·예측2481986행전후동일. 성과판정아님. 미국Dec1종가=한국Dec2 06:00이후에도21종가완전성확인필요.

원문추가8physicalsource후보: GS `tmp/quant-gs-period-reconciliation-20261101-v1/`에서cached동일accession원EX101.INS context Apr1..Jun30(91일),Revenue8612m/NetIncome1931m/EPS3.70같은무차원문맥+HTML표확인. APIcompanyfacts의Mar31date불일치를기존row가계승;원공시/프로젝트날짜뺄셈오류아님. 실제API내부원인미확정. OperatingIncome raw없어null,pretax대체X. canonical-research-rows새Apr1행만별도제안,기존Mar31target/203분모/DB수정X.
AMGN `tmp/quant-amgn-quarter-originals-20261101-v1/` 공식issuerQ4원문7개(2013~2019),product/other와분리된TotalRevenues5011/5331/5536/5965/5802/6230/6197USDm직접확인. 모든발표일13관련10K이전이나같은accn미입증(원accessionnull),eligibleOverride=false. Oct1은명시3months-endedDec31의달력경계이지rawXBRL문자아님. quarter7/target연관13/원문hash/검증보존. 기존189/203복구·14미반영집계유지, sourceidentity/period의명시정규화검증후별도실험필요. 이번모델반영/오차감소주장X.

검증:new평가9+일일3TDD,기존daily10포함;전체scripts280PASS +tests/python43PASS. 로그 `tmp/quant-candidate-evaluation-continuation-20261101-v1/` 두suite. 독립reviewer candidate_evaluation_review에서source14bundle/모집단·달력·성숙/pairedarith·실패처리/GS원문/AMGN7표PASS,Critical/Important0. minorRMSE/bias직접assert는최종suite전추가완료,남은pending zero/paired 전필드 unitassert나열은nonblockingdefer. smoke/review-receipt.json보존. 구현준비완료≠정확도개선완료. 앱Java모델/DB/서버/브랜치·commit·push변경X. 기존60시험정답미사용.

## Latest November1 — 추가126매출 반영·V9 원인/후보검증·30전방동결; 실제개선 미입증

최신 보고서 `docs/quant-v9-cause-forward-results-2026-11-01.md`; 계획 `docs/superpowers/plans/2026-11-01-v9-cause-unseen-validation.md`. 기존 아래실험과15:07heartbeat영수증은 보존한다.

원래203누락중189복구/14미확정(AMGNQ4제품매출범위13,GS2013시작일충돌1). 이전별도24+이번새102=126빈매출을V2입력에추가; 기존63과합계189모두이번모델포함. goods/services v2-v4 source디렉터리의126원문/68공시·단위·정확기간·발행사·원문SHA 검증. JNJ38/KO43/MRK41/AXP42/CRM25해소. 기존비공백/가격/주식수/기업행사/공시기간/ID/정답/모집단 불변. 재다운로드·이중반영 금지.

새모델 `tmp/quant-full-financial-study-20261101-v3/`, protocol89d05f9c0a83e06df419817b17cc4df851a40d5f32e20bb255c45a69aa756ec5,222해시.16:30:07완주exit0,902결정/26401훈련/203784전체OOF·25473V9. compare완료. 주17904행609일MAE5.749702649→5.757472208 /4.405489367→4.410768581,둘CI0포함. 고변동두MAE악화/CI>0. 운영개선아님/승격X/DB쓰기0·전후동일,기존60미사용. 같은훈련내부이지미사용시험아님.

원인 `tmp/quant-v9-financial-cause-20261101-v1/`:이미노출된63개입력실행의V2–V5재무보완오차감소가V6zero-shrink에서증가로전환. 실제V6두머리공식17904쌍재현,날짜·종목·분기기여합일치. 고정과거scale반사실도+.003834/+.007798이므로adaptive변화하나만범인이라고하지말것. source모듈/진단결과이미해시기록되므로수정하지말것.

후보 `tmp/quant-v9-return-blend-20261101-v1/`,protocoledec6ad56675432cb10466ffd37c85e349a41ec2ef7607a964af0626c52fff9e,V3성과보기전고정2개. half(V5+V9)/2는수익률MAE5.716283개선/초과4.438273악화. 과거만기252날짜·60dates/1000rows·jointMAE/RMSEguard는10분기모두λ0,final7560rows252datesmaxexit2020Sep29에서도0. 기준과동일이지개선모델아님. 내부결과보고후후보추가/승자선택안함.

진짜미사용과거구간검증은미완료. `tmp/quant-v9-unseen-audit-20261101-v1/audit.json`은33프로토콜/5·20·60D·공통시장노출검사,2021–2026재사용불가/중앙장부불완전. early26readiness/source공유디렉터리에2011실제instant후보26개(동일키충돌0),genericraw값일뿐경제적전체classcoverage미입증/admittedForModel=false. 첫SECcover403에서중단. 기존28명guard낮춰조용히26명실행하거나2017학습과거적용금지.

무료early자료추가: `tmp/quant-djia-early-free-20261101-v1/v2/` Zenodo3634200price-indexCSV,2010~2016=1762일,노출중첩264일수준/263일변화정밀도일치. 초기가격권리/빈티지/PIT미입증. `tmp/quant-early-split-evidence-20261101-v1/split-events.json`:AAPL2014Jun9 7:1,KO2012Aug13 2:1(Eurex공식ex고지+issuer확인),CRM2013Apr18 4:1. 전체초기행사완전성아님,현재V3엔미반영source-only. 403429확인화면우회하지말것.

실제미사용전방후보동결 `data/quant-candidate-forward-20261101-v1/DOW30/`30개전체. 기존DOW30frozencohort와동일anchorOct30,originalSignalOct31,entryNov2close/exitDec1close. LIVEV5동일30읽기전용export `tmp/quant-v9-return-blend-forward-read-20261101-v1/upstream.json`;읽기07:36:48UTC≤동결07:38:24<entryNov2 21:00UTC. 미래labels조회X. guardedλ0정확baseline;half는실험용. pending-evaluation전30PENDING/MAEnull. 기존629V9/원프로토콜변경X. 아직미사용오차검증완료라고말하지말것. 만기후동일21종가·기존index벤치·전이름분모/고정후보로평가필요. 이번작업에서자동평가스케줄은새로설치하지않았음.

검증:새Python18 RED→GREEN+전체277PASS,Java readonlyboundary7PASS(실제yyyyMMddsource cutoff양식고친실패로그보존),독립126원문/222해시/모든메인산술/전방30정확값·11해시PASS. reviewer `tmp/quant-v9-followup-review-20261101-v1/review.md` Critical/Important구현결함0;미사용평가미완료는명시적미충족요건. root-handoff-check.json에서주MAE직접평균/실제첫fit미래y변경불변/원래3manifest·모든archiveSHA와629유지/새30pendingnull/222출처검사PASS. V3comparisonSHA eee6c20508a923d2ef7021390ddfb935b73ce6adfef607836ba401cf31116777,새forwardprotocolSHA07ae26c7ea32a331efdfa336fcf49f950dcf048965e0d65997f02a9ffbfca718. 전방은원stored시장가중프록시벤치(과거DJIA와정의구분). 그림`v9-cause-followup.png`. 앱production·DB·자동화·서버·branch/index/commit/push변경X. 같은모델/입력재생반복불필요.

## Latest November1 — V9 전체 재생 검증 완료; 개선 미입증·원문 매출87건 복구

계획 `docs/superpowers/plans/2026-11-01-full-v9-financial-replay.md`.
무료원문 v1은 goods34(JNJ16/KO9/MRK9)+services29(AXP16/CRM13)=63건을 모델에 포함. 봉인 후 새 v2 source 디렉터리에 goods12(JNJ8/KO2/MRK2)+services12(AXP12)를 별도 복구·독립검증. 원래203개 중 총87개 복구/116미확정; 중복0. 이번 모델 수치는63개만 포함하고 late24는 미포함. 기존봉인 입력에 섞거나 덮어쓰지 말것. 같은 CIK/accession/기간/공시일/USD/연결total 원문 증거 보존. GS시작일 하루차이/AMGNproduct-only는 거절. 현재취득이지PIT스냅샷아님. 원문재다운로드·중복반영 금지.
전체모델입력 `tmp/quant-full-financial-study-20261101-v1/`:protocolSHA d5ca47928477d91f14393cda9cc286b10a9b3659e780efccdf01e4dbcfcbe43d. source1973행+기존unmatched3=1976union,496matched의기존assets/liabilities/equity/CFO/ID보존.
비공백44차이는전부다른개념.기존product우선순위(Revenues>ContractExTax>SalesNet, NetIncomeLoss>CommonBasic)로원본재선택하여32higher-priority업그레이드만명시기록.7Revenues→Contract축소/5Common→ProfitLoss변경은안함.동일개념금액충돌은차단. USProfitLoss임의대체금지.
새Java `QuantFullFinancialTrainingReplay`는nativeDB재무fallback차단,나머지기존실제V2–V9수식그대로.902시점/26401행/목표203784OOF,가격·주식수·기업행사·모집단동일.기존60시험데이터미사용,입력가격/정답종료2020Sep29제한.
V1은15:37완주exit0,902/26401/203784OOF. 첫 오차를 보기 전에 latest손익공시 balance/CFO 누락을 발견하여 `balance-completion-prespec.json`에 V2 주판단/V1 진단 고정. V1 비교는 V2 봉인·기동 후에만 열었음. V1 주MAE 5.749702649→5.769487402/초과4.405489367→4.423562798 악화. 결과를 보고 V2를 선택한 것이 아님.
V2 `tmp/quant-full-financial-study-20261101-v2/`, protocolSHA fc4361c0b8e52326dd597630480b95d6ab857fd669b73599b42330392d51b0cd. 정확한 동일공시 원문 자산1324/부채709/자본1711/CFO679 빈값 추가. income·비재무·기간·ID 불변, 기존453비공백값 불변, YTD차감 금지. 15:53:23 완주exit0,902결정/26401훈련행/전체203784OOF·V9 25473OOF. compare exit0. 재실행 불필요.
주17904행609일 MAE 수익률5.749702649→5.755728110,초과4.405489367→4.410963802 소폭악화. 차이95%CI[-.004097,+.015306]/[-.006831,+.015427] 모두0포함. 고변동3250행109일 MAE8.060250703→8.089974437/5.771604591→5.801946077 악화,두CI>0. 양MAE공동개선은10분기중2개. 전체보조OOF도악화. 개선·운영승격 근거없음. 같은훈련내부결과이지새미노출/전방시험아님; Dow30고정모집단편향보존.
Java240실제모델출력/미래정답불변/nativefallback차단PASS,새Python19집중/전체259PASS. 독립 원문87개/32업그레이드/499기존기간/4876추가사실/105330재무조회·155330단위조회·26401시장조회,모든지표·분기·CI·902만기수검산PASS. runtime10클래스동일/DB전후동일·쓰기0/heldout미계산. Critical/Important없음. source90해시/결과비교SHA7f763a8dfd416d716d4b516bda38fcdfd64bd4b2177b4eff29b92cc62c6fcc61.
결과 `docs/quant-full-v9-financial-results-2026-11-01.md`; 그림 `full-v9-financial-validation.png`, `root-independent-check.json`, `independent-review-receipt.json` 모두V2폴더. 남은정확116대상 `remaining-revenue-targets.json`. 다음은late24+남은원문사전고정통합,미노출또는만기전방평가; 같은60건/같은표본반복가중치맞춤금지. 운영코드/DB/자동화/서버/branch/index/commit/push변경없음. 기존15:07일일컨트롤러영수증은아래보존.

## Latest November1 15:07 KST — daily controller execution receipt

Heartbeat-triggered execution completed at `data/quant-daily/20261101T060708Z-42018482/result.json` (not proof of a separate 15:10 scheduled fire). Read-only plan and `--run` both selected October30; all indices already frozen, no collection/model recalculation/new cohort. Evaluations: DOW30 30, NASDAQ100 101, SP500 498 pending; mature N=0, MAE=null. Existing 629 forecasts unchanged; no promotion.
MariaDB SQL healthy; active symbols525/current universe503/103/30. Yahoo49 last completed-with-errors October31 16:15:22; logged last24h API errors0; no RUNNING in inspected batch groups. Raw facts706394/standards65251 reflect the later EPS repair: do not reapply US/IFRS/EPS plans. Read latest quarterly/high-volatility results and original accession queue; no source re-download or repeated historical model study this run. Remaining priority:203 unresolved consolidated-revenue observations and separately predeclared full-V9/new-unseen evaluation; foreign quoted-EPS/FX limitations remain. FRED/Toss current endpoint permission was not probed. Preserve all existing user changes.

## Latest November1 — 무료 분기 손익·연속 공시 복구 및 고변동성 검증 완료; 운영 승격 없음

결과 `docs/quant-quarter-financial-highvol-results-2026-11-01.md`; 계획 `docs/superpowers/plans/2026-11-01-quarter-financial-highvol-validation.md`.
`tmp/quant-quarterly-financials-20261101-v3/`:31기존공식원본SHA/CIK확인,1973재무행(509FY/1464Q),854고유공시,236직접Q4/7902관측근거. 현재취득원본의과거복원이지PIT스냅샷아님.새다운로드/DB반영아님.
sourceSHA bdc1c1cf79bd3dc929dfb5b364b18b43be53fcfa11e88a26994a86ddeebcdd99. v1/v2보존.출력덮어쓰기차단TDD,원문DOW세금포함매출3개만추가(개념분리),나머지203매출미확정은null.상품/서비스/비이자/부문/proforma를연결매출로대체하지않음.
`tmp/quant-quarter-highvol-study-20261101-v1/`:protocol31505b4090f301dbd5a4d099f5861e4b4dc9aecab42480c28b9677c8ce272996. 47해시/동일OOF25473행870날짜,주17904행609날짜/고변동3250행109날짜. 기존60시험정답미사용.
사용가능OOF분기EPSYoY11707→23818(93.5%),가속3735→21274(83.5%),순이익률1090→22802(89.5%).모집단/독립정답수확장아님.현재V9예측고정한연구보정실험으로전체V9재생실험아님.
사전고정5후보:V9/확장재무/고변동시장/결합/최근63일국면보정.분기고정학습과매일만기된정답갱신구분.모든signal·exit<해당cutoff.선택3분기bothmeanMAE개선/RMSE불악화+두머리정규화MAE비율평균<1인분기2/3,없으면V9.
전체선택MAE5.749702649/4.405489367→5.755483144/4.405895731악화,95%차이구간0포함.고변동성선택수익률8.060250703→8.031313233개선이나초과5.771604591→5.774826864악화.개별후보소폭개선도CI0/일부RMSE악화,사후승자승격금지.
전체235PythonPASS/신규source16+모델13=29집중PASS.독립1973원본복원/모든25473지표/7902관측/47해시/모든지표·CI/609일별보정/10분기재적합PASS.최대예측차4.3e-14/정규방정식1.1e-13.고변동성CI는609전체날짜블록재표집후조건부계산(1001시도1000유효).
기록 `independent-review.json`,그림 `quarter-highvol-validation.png`.Critical/Important없음.분기고정후보전체/일별후보현재시점미래정답변경불변검증;일별후보가정답만기후갱신하는것은정상.
운영코드·DB·자동수집·서버·branch/index/commit/push변경없음.다음:203개누락연결매출원문범위대조,확장자료로V9전체재생/새미노출또는실제전방평가를별도사전고정.반복훈련내부결과를새시험이라부르지않고기존60건튜닝금지.

## Latest November1 — V9 EPS 성장·가속 시간순 검증 완료; 개선 미입증/운영 변경 없음

결과 `docs/quant-v9-eps-growth-results-2026-11-01.md`; 계획 `docs/superpowers/plans/2026-11-01-v9-eps-growth-validation.md`.
`tmp/quant-v9-eps-growth-study-20261101-v1/`: protocolSHA4b5fb828400f4a63b4ed2427f3ab30414fc8b810e2321f8eecabf82ce670e740, resultSHA17a551b1f95fc11194e9c6440ea79f2f69314bf3a0751a2e10b8fa114f7e0f43.
기존복구OOF25473행/870날짜만사용, 기존60시험결과미사용. 주평가17904행609날짜2018Q2~2020Aug28. 재사용훈련내부연구이지새시험아님.
5후보(V9/시장맥락/EPS성장/EPS가속/실적확인) 사전고정. Ridge100,blend.25,rawcorrection±2pp(max±.5),과거3분기MAE/RMSEgate,학습signal·exit<cutoff.4b5...프로토콜 봉인후한번실행/사후후보추가없음.
자료조건:같은CIK/통화/EPS개념/실제기간/공시일;550/220일anchor신선도,분할·음수/제로구분,Q4빼기생성금지.성장가능연간19748/분기11707/가속3735/마진1090of25473.
V9MAE5.749702649/4.405489367→nested5.759968306/4.405697199.차이+.010265657/+.000207832pp;두95%구간0포함,두RMSE악화. EPS단독추가도두MAE공동개선아님.승격안함.
오차관찰:날짜공통수익률MSE성분48.24%,시장과거변동성25%이상109일MAE8.0603vs15%미만369일5.1515pp.연관/회계분해이지인과증명아님.2020부분구간이절대오차41.7%.
전체scriptsPython206PASS/Quant93PASS/새15PASS;실제firstouter미래정답변경불변. 독립48집계/400분기지표/40직접재적합/5CI/10선택/102528source사용검산PASS, `independent-review.json`.
비차단후속:기본/희석EPS혼합시두YoY쌍사이개념가드보완(현재454EPS모두희석으로영향0).81기존EPS원문내장관측없어이번독립검토에서SEC원문재감사안함.재사용확장전확인.
프로덕션/DB/API/자동화/서버/branch/index/commit/push변경없음.차트검수완료.다음자료우선순위는실제분기손익·마진·연속공시복구와시장공통/초과수익분리설계;동일60건튜닝금지.새미노출/실제전방평가필요.

## Latest November1 — EPS 원본 누락 수정·355건 DB 복구 및 시간순 재검증 완료

결과: `docs/quant-eps-recovery-results-2026-11-01.md`; 계획: `docs/superpowers/plans/2026-11-01-eps-history-recovery.md`.
원인: SEC concept별 마지막80개만 보존하여 과거 EPS 탈락; refresh가 전체 원본 아닌 이번 입력만으로 표준화 이력을 교체함.
기본 cap0=전체이력, refresh는 저장된 원본 전체에서 재구성, 두 rebuild 진입점 transactional, 각 행 실제CIK 보존으로 수정.
Maven 전체286건(259통과/27skip/실패0), Python183통과, 실제모델240출력·미래정답불변 테스트 통과. 기존 dirty main 및 무관 파일 보존, commit/push 없음.
공식 companyfacts30종목+legacyDIS를 대조하여 기존499선택키 중355nullEPS만 복구;99기존EPS 불변/45미복구null 유지. 비EPS 컬럼·공시일·기간·단위 검사 완화 없음.
입력팩 `tmp/quant-eps-recovered-inputs-20261101-v2/`; 원본SHAf6724b4710b8ca68f2f4a338c55f5cc63124c514e40498fcdc1beb5c2c48bd43, 복구SHA c1949e7cbccd152cc1928aeb0a99c8594e38dc2adcb9e1589c8430d401f2e64f.
실제MariaDB rollback probe 성공 후355표준행 EPS만 수정+누락원본355insert 커밋. 백업 `tmp/quant-eps-live-recovery-20261101-v1/backup-and-plans.json` 및 봉인manifestSHA bb5ef2cc128b65133d21340b876ccdd5373c43783fabd104a640df990abc29f1.
`postimage-readonly.json`: 새연결355표준행 전체컬럼/355원본검산PASS, 비EPS모두백업일치. 원본706394/표준65251행. 재적용하지 말 것(이미커밋됨).
V1 계산902일 완주했으나 거대문자열 저장 중 NegativeArraySizeException; 오차비교결과 없음. V1보존/재사용금지.
V2는 수식·입력 그대로 출력만 streaming/중복제거. 완료root `tmp/quant-eps-v9-training-20261101-v2/`, protocolSHA d3619189eea848f46e2e2c098593c6dbe0ae057369a25cbc34cbecfbd86cec10.
902시점/26401행, 예측2017Jan31~2020Aug28/정답종료Sep29만, testDecisions=[]; 기존시험60건 미사용. 전체OOF203784/V9OOF25473완료, 재생DB쓰기0/전후동일.
V1/V2 재무조회102804개는바이트동일;시장26401키/단위135719키는직렬화순서만다르고전체값동일. 결과SHA98963fc2a3764ec52fefb2fd0bd9c980ee41de30118125235bae7f7389799088.
주평가609날짜/17904행 MAE수익률5.753122986→5.749702649,초과4.413736466→4.405489367. 차이-0.003420337/-0.008247099pp;95%구간[-.016286,+.011237]/[-.017842,+.003694]모두0포함.10분기중5분기만공동개선.
전체870OOF날짜 수익률MAE5.234811695→5.236503157악화,초과4.111686924→4.103981806감소. 안정적공동개선입증아님.
최신연간EPS존재3317→23250/26401,분기4540→23082/26401.valuation19511행/종합22609행변화,다른10점수변화0. 현모델EPS경로는주로PE/valuation;성장·실적은매출/순이익기반.
사전고정기존10후보/33지표nested동일절차완료: `tmp/quant-eps-nested-training-20261101-v1/`,protocol4a8e1cfc3322649760726c91599a8fd6f1f2563e53fbcbadecdbeb32e4ff7952,10집중테스트PASS/실제첫fold미래정답변경불변PASS.
선택절차MAE5.757539181/4.412951894로복구후V9보다+.007836532/+.007462527pp악화. 운영가중치·후처리승격없음. DB복구는유지/재적용금지, 기존서버·원격배포·자동화설정은이작업에서재시작/변경하지않음.
독립기술검토receipt `independent-review-receipt.json`; 수치최종검산별도영수증참조. 이미노출한훈련자료내부비교이지새미사용시험아님. 이후개선도같은60건튜닝금지,미노출/실제전방검증필요.

## Latest November1 — 훈련 내부 오차 진단·시간순 후보 검증 완료; 운영 변경 없음

먼저 `docs/quant-v9-training-nested-results-2026-11-01.md`를 읽는다. 목적은 여전히 실제20D오차 감소이며 이번 결과를 개선 성공으로 과장하지 않는다.
`tmp/quant-v9-nested-training-20261101-v1/`: protocolSHA494412809d98e97e9aa04768020274a450311599fb843ad91e1e1d7a8ad8676c,
sourceforecast-onlySHAf3fa85da248107034a2c52a0a22208c20c28788c55780a31613d3877375e0c0e에서trainingRows/oofPredictions만사용.
기존60시험정답result/evaluation및target/baseline예측배열미사용. 원본inputs의2020Aug28이후가격은지표에사용하지않음.
33개시점가용지표/25473OOF패널. Outer2018Q2~2020Q3부분분기10개,17904행/609날짜. 같은온라인V9기준에분기고정후처리,3개이전분기에서만선택.
모든학습signal/exit<fitcutoff;inner선택exit<outercutoff.300행/20날짜미달fallbackV9. 후보10개와lambda10/100사전고정;사후추가금지.
NestedMAE수익률5.753041300vsV95.753122986/초과4.405546654vs4.413736466. 차이-0.000081686/-0.008189812pp.
21날짜이동블록95%구간수익률[-0.188597,+0.195236],초과[-0.024941,+0.005017]모두0포함;수익률RMSE소폭악화. 실질적개선미입증.
직접/잔차Ridge와명시적시장/가격/가용성제외후보모두단독전체MAE가V9보다나쁨. 단순중앙값은두MAE낮지만수익률RMSE는높음;사후최고모델승격하지않음.
원인관찰: 전26401행sectorUNKNOWN,뉴스/기관/애널리스트점수각상수50. 선택공시어느곳에도EPS없는행20899,있는행5502.50을missing으로추정하지않았음.
날짜공통제곱오차성분45.36%(2020:58.67%)는회계분해이며인과원인증명아님. 실제지표내용/발표시점/누락과단순점수기본값구분이후속우선순위.
추가EPS추적:2017~19최신연간/분기EPSnull비공백선택37712건(고유훈련행수아님)은candidateFieldEvidence에EPS후보없음. 수집부재vs후보전필터탈락구분불가,무료공시EPS없다는뜻아님.
2020EPSnull692선택은후보있음;AMGN연간에92일Q4EPS만있는것은365일FY와맞지않아정상차단. 기간/공시일/주당단위검사완화금지. 다음우선순위원본→후보전필터→표준화사유증거복구.
173PythonPASS,실제firstnestedfold미래정답변경불변,독립전체지표/10선택/240내부점수및6fit/7feature표본검산PASS.
근거independent-method-review-final.json,minor상수열float스케일≈1e-12정리1건보류(예측영향미미/독립일치). 새미사용시험아닌내부개발검증으로기록.
운영V9/DB/기존자동수집/브랜치·인덱스·커밋·push변경없음. 이미사용한전체훈련과60시험창노출기록은그대로유지.

## Latest October31 — 장기 시간순 OOF 검증 완료; 일관된 20D 오차 개선 없음

먼저 `docs/quant-long-history-oof-results-2026-10-31.md`를 읽는다. 목적은 미사용 정답 기간에서 V9 20D 오차가 실제 줄어드는지 확인하는 것이다.
30종목 무료 Zenodo 원본/게시자 해시 확보. 2019~2020 훈련 8,220행/OOF V9 7,290건은 TRV64/107<200으로 NOT_READY였고 시험 정답은 열지 않았다.
2017년 확장 첫 봉인 `tmp/quant-long-extended-oof-study-20261031-v1/`은 HON2018 조정 누락을 입력 생성 중 발견해 취소했다. OOF/시험 정답 전에 중단; 봉인본을 덮어쓰지 않는다.
두 번째 봉인 `tmp/quant-long-extended-oof-study-20261031-v2/`도 NKE2015분할전주식수/V2010ClassA만선택 문제를 발견해 OOF830/902에서 시험출력 전 취소했다. 재사용·정답평가 금지.
최종 입력 `tmp/quant-long-verified-oof-study-20261031-v1/`, protocol SHA256 `0484973296cf1b29c2ba06fbf487247efad9398f730d8033ccb172125f632033`.
NKE는 공식원문7개 A+B총량, V는 공식공시6개 ClassA환산전체(전환참가우선주 포함/백만주정밀도/원문바이트아닌명시적readertranscript)로 교체했다.
새 Java는 V/NKE에 완전한 과거 공시 지분 근거가 없으면 중단하며 DB의 부분 주식수로 돌아가지 않는다. OOF 전에 모든 시장·재무·단위 근거를 내보냈고 독립검토는 OOF 중 시험정답 계산 전에 완료했다.
훈련 2017-01-31~2020-08-28, 902시점/26,401행. DOW 정규거래 80개 종가 이후만, DIS 구/신 CIK 구분 및 2019-03-20~05-08 제외.
HON 공급자 원본의 2016/2018 분사 이벤트 3건은 가격 단위만 복원하고 보통주 수를 늘리지 않는다. 복원2017말153.36000777≈SEC153.36.
시험 2020-09-30/10-30, 동일한 2020-09-30 이전 만기 훈련/OOF만 사용. 모든30종목/120개 기능 보정조건>=200 통과 전 시험정답/MAE 금지.
기존136,933개 노출창과 두 시험 보유창 겹침0/미해결0. 후속 전역 노출 장부에는 이번 실제완료훈련+계산한시험만 추가한다.
최종 실행 SCORED, 훈련26,401/전체OOF203,784/V9OOF25,473, 기능120/120PASS(최소일치545>=200). 두시험60건 실제계산완료; 이제미사용구간이라고재평가하지않는다.
수익률/초과수익률 MAE(%p): 동일장기훈련V9무OOF8.550703638/5.557421995; OOF8.555325008/5.559814942로둘다소폭악화.
OOF중앙잔차보정8.499276304/5.566129075:수익률만0.051427pp개선,초과수익률악화. 훈련중앙값8.493901449/5.579353188;0%8.596188300/5.574594639.
잔차보정9월수익률MAE5.598854→6.526072악화/10월11.502553→10.472480개선. 안정적공동개선아님; 운영승격/가중치변경금지.
`data/quant-research-exposure-20261031-v1.json`을 후속 전역노출장부에 반드시 합친다. 이번훈련2017Feb1~2020Sep29와두시험Oct1~Oct29/Nov2~Dec1라벨을이미사용했다.
동일60건으로다시튜닝하고새시험이라부르지않는다. 다음후보는훈련내부시간분리선별후별도겹치지않는구간/실제전방에서확인한다.
Python 전체163개 PASS. 최종 Java 2017 실제모델232출력·미래정답불변·IPO/발행사 경계 및 전체 지분/정밀도/분할단위 테스트 PASS. 운영DB/가중치/배포/Git 변경 없음.
행 수는 독립 표본 수가 아니다. 과거 고정 모집단/입력누락/PIT·라이선스·분사 반올림 제한이 있으며 아직 운영 승격 근거가 아니다.
최종독립재계산은26,401+60주식/DJIA수익률/120기능조건/모든오차·잔차/133해시와DB전후동일쓰기0을확인했다. `independent-result-review.json` 참고.

## Latest October31 —11missing-share evidence recovered; genuine earlier labels tested; no promotion

Read `docs/quant-v9-shares-unused-results-2026-10-31.md` FIRST. Do not repeat either this2021study or earlierApr/May/old20datesasunused.
11missing Nasdaqtickers ABNB APP DASH DDOG GOOG GOOGL META MSTR PDD PLTR SHOP:22strictpriorfiledclassobservationsfrom39SECfilingoriginalHTML,
hashes/units/classrights/quoteADS/precisionindependentlyverified. A`tmp/v9-shares-a-20261031-v1/shares-reviewed-v2.json`10rows,
B`tmp/v9-shares-b-20261031-v1/observations.json`12rows. Combinedcoverageonly`tmp/quant-strict-unused-20261031-v1/v9-share-coverage.json`;
coveragevaliddespite2022protocolinthatdirectorycancelled. APPcompleteclass337278000bothcutoffs,rounded/stale;GOOGreportedmillionssameissuer;
ABNBH9.2msubholdingexcluded;SHOPFoundernoeconomic1excluded;PDD5693585848ordinary/4=1423396462ADS. NoDBimport/prodchange/noAprMayMAErerun.
CRITICALglobalusage: DBearliest2023wasinsufficient;existingoutput/sp500-*file-onlytraininguses2021/22labels. Cancelled2022sealedtest
BEFOREANYoutcomes/MAE;originalprotocol/input/codecopypreserved `tmp/quant-strict-unused-20261031-v1/CANCELLED-BEFORE-EVALUATION.json`.
FinalROOT`tmp/quant-strict-unused-2021-20261031-v1/`:protocolSHA9fbf0840961007b034a6fd0890d2f11acc7c447ba47441fd4d5c5164e4f73a2a,
actualglobal128084DBlabelrows+202oldreplayrows+7conservativeWHOLESPinputcalendars=128293auditrows:bothnewwindows0overlap/unresolved0.
TESTMar31→Apr1-Apr30andApr30→May3-Jun1 2021 beforeearliestSPsourcecalendarJune7. ThisisDOW30historicalresearchNOTNasdaq100validation,
notprospective/humannever-sawhistory/fullPITclaim. Full30includesWBA(noAMZNreplacement),same188days/21holdingcloses.
WBAfreeUI5YplaincredentiallessrequestidentifiedfrompublicreferencedJS(notpaidJe);raw1256HTTP200 earliestAug27 2020,explicitCcloseNOTadjustedA.
Extended609rowsSep1 2020-Feb1 2023exactrawmatch;ALL418priorbrowsercentclosesmatch. `tmp/quant-unused-dow-20261031-v1/source-manifest-extended-success.json`.
WBAactualSECstandards29/shares34 (`tmp/quant-strict-dow-inputs-20261031-v1/`),42invalidcomparativeanchorsdropnotguess;selected2021WBAfacts307verified.
OfficialFREDDJIApriceindex(notTR/freecopyright≠redistributionlicence). Other29sourcearchivesundofutureprovideradjustments(MMM/HON/IBM/MRKseparations,
WMT3:1) fornominalEPS/sharealignment;roundedseparationratiosNOTcertifiedexactexecution/PIT;alltheseeventsafternewtestholdingend.
Newtraining360rows/12overlappingdategroupsDec24 2020-Feb26 2021,strictsignalANDexit<Mar31,maxexitMar29;samefixedtrainbothdates,noroll.
CRITICALOOFREADINESS:all12OOFgroups0predictions(maxmature150<unchanged300);V9calibrationErrornull. BaselineisproductionV9fallbackWITH
unavailableOOFcalibration,NOTfullycalibratedcurrentoperatingV9validation. Unknownhistoricsector/macro/othermissingsourcefallbacksretained.
Actual480V2-V9outputs/60outcomes;futuretargetlabels−95/benchmark+80bothdatesallfields invariantexcepttime,0DBwrites/statebefore=after.
MAEabsolute/excess:V9baseline3.671302714/3.732305476;median3.826975333/3.696157545(exonlysmallbetter.036147931ppbutabsworse);
ridgealpha10 4.168907966/4.065814135worse;ZERO3.655210900/3.700582078beatsbaselineboth. No consistentimprover/promotion/Git/deploy.
Do NOTcomparethis3.67tooldNasdaq7.9or12.3asmodelgain:period/universediffer. Separateindependentreview420actual+benchmarkreturns/sourcehashes/
fits/allmetrics/cutoffs/windowexposurePASSwithcaveats. Python154PASS;Java10boundaries+240syntheticrealmodeloutputs+3sourcechecksPASS.
NEXT:longerFREEhistoricalinputandtime-separatedOOF/calibrationhistory BEFOREnewuntouchedoractualforwardtest. No moretuningonthis60outcomes.
FutureglobalusageinventoryMUSTincludeTHIS360training+60test labelwindows plusoldSPfilecalendars;notjustDBandnotonlydateabsence.
ExistingfirstactualforwardcohortNov2entry/Dec1exitpendingunchanged;full20DimprovementprogramNOTcomplete.

## Latest October31 — NEW target dates tested; no promotion; usage-overlap gate added

Read `docs/quant-new-period-results-2026-10-31.md` FIRST. Do not repeat either old20dates or newApr30/May29as fresh tests.
Newprepanelnative financial97rows(AZN43/CCEP22/GFS11/TRI21,FER0),602originalselectedvaluesverified,FILEONLY.
`tmp/quant-prepanel-financial-expansion-20261031-v1/`:97distinctnewkeys,not97independentperiods. Actuallyused5newkeys/21lookups,
CCEP7existingtrainingrowschanged. Otherissuers'oldtrainingpanelsmissing;don'tclaim97alltrained.
`tmp/quant-new-period-20261031-v1/`:originalprotocolandv1codepreserved;input-onlyamendedV8protocolSHA
e8336f371b712810849f7e28560b422834918631828b8165759bf75ea9fd63b2beforeMAE. Fixed3568mature20Drows/38dates
signalANDexit<Apr30(maxexitApr28),samefixedtrainingbothApr30/May29;noholdoutroll. populations101eachofficialTEAM→SNDK/CSGP→LITE.
V9FULLBLOCKED11priorrawshares/quotedunitproofmissing(ABNB APP DASH DDOG GOOG GOOGL META MSTR PDD PLTR SHOP):only90/daydiagnostics,
never90subsetMAE. V2–V8full101/day;fullcomparisonV8notV9. Definitive`replay-v8-complete/result.json`+`evaluation.json`.
202outcomes V8baselineabs12.328114289/ex13.141908408;newfinancial12.330894617/13.143027372 BOTHWORSE.
Median12.387246238/13.005410736(exonlybetter.136497672ppbutabsoluteMAE/excessRMSEworse);ridgealpha10 20.019736063/13.566455653 worse.
FirstdateRidgebetterbutseconddatebad;nocherrypick/refit/promotion. Source trainingrerankbotharmsdiffersfromprevioussavedbaseline.
INSM/ZSremovednames'Jun29/30actualcloses4gapsfilledFILEONLY:28allprovideroverlapswithin2e-5USD,canonicalpreserved.
SourcewebcuratedcentsnotrawHTML/PIT/licence/TRproof;Yahoo429/Nasdaq403/StooqchallengeSTOPPED,noaccessbypass.
IMPORTANTNOVELTY:dateabsenceisNOTglobaluntouched. Prior20DholdingwindowoverlapApr30=0/May29=1009;
ANY5/20/60Doverlap287/3201. Newtargetdatesonly;Apr30fresh20Dlabelsbutnotglobalpristineholdout,May29overlapdiagnostic.
`scripts/quant_history_usage.py --require-unused`nowfailclosedANYpriorwindowoverlap/unknown;actualstrictauditrejectsbothasexpected.
Nooutcome-basedreselection:preserveall202prespecifiedmetrics. FuturepureholdoutclaimsMUSTpassdateANDfullwindowusageauditBEFOREoutcomes.
Two dates×twoarms targetlabelperturbationallforecasts invariant,DBbefore=after/0writes;independent10MAE/ridge404/cohort/hashchecksPASS.
Python scripts145PASS/Javanewperiod8boundaryPASS;productionunchanged,norepeatedMaven. NoGit/push/deploy. Nextrealwork:
11missingmulti-class/foreignquotedshareprimaryproof+earliercompletePITtrainingpanels,notreplaythesealreadyinspecteddates.
TrueforwardcohortentryNov2/exitDec1pendingunchanged. Full20Dprogram/fullyindependentvalidationNOTcomplete.

## Latest October31 — nativeIFRS/6K actually integrated; paired error NOT improved

Read `docs/quant-ifrs-6k-model-results-2026-10-31.md` FIRST.
NativeSecparser includes11ifrs-fulltags+6K/A; standardizer8fields, canonicalFYJan-Dec/exactQ+CYframe,
nohalf/ninemonth/noncalendar/unframedquarterguess, noFX/ADR/foreignquotedEPSconversion.
6KcanonicalFY/FPnonnullbeforepersistence, originalsourcepreserved;instantlegacykeyunchanged.
NativewritesnowinsertNativeBatch, samekeyNOOPpreservesALLoldfields/evidence/time,newaccessionadds;
empty/unsupportedcan'tdeletehistory. USnonemptylegacybehaviorunchanged. RealH2samekeytestsPASS.
Definitive `tmp/quant-ifrs-replay-all20-20261031-v3/` (V1/V2supersededdevelopmentprovenance):
20dates/2020outcomes;16160controlforecastfieldsreproducedexactlyexcepttime;DBreadonlyfingerprintunchanged.
MAEabs7.912899969→7.913521403(+0.000621434pp),excess7.664448799→7.665493386(+0.001044586pp),BOTHWORSE.
65foreignsymbol-datesalsoWORSE;don'tpromote/cherrypickdirection. Weightsunchanged. Frozenhistorictraining/
market/macro/prices/outcomes+nonfinancialscores: input-repairSENSITIVITY, NOTfullyretrainedwalkforward/newholdout.
ContinuedBEYONDthatnegativecomparison: `tmp/quant-ifrs-rolling-training-20261031-v2/` actualfile-only
pairedrollingrepair20prior101-memberpanels+eacharm'spriorpredictions. StrictsignalANDactualexit<nextdecision,
samepairedlabels/windows/denominators.20dates/2020outcomes/16160forecastsPERARM. MAEabs
7.914532391→7.915635821(+0.001103430),excess7.665019525→7.665597203(+0.000577678),ALSO WORSE.
firstDateBaselineMaxAbsDifference0isONLYfirstdate;subsequentcontrolsintentionallyuseownrollinghistory,
notall16160unchanged-controlclaim. Marchcurrenttargetlabels101perturbed−50%still808fieldsinvariant.
AuditmaxTrainingExit<decisionall20;countsinclude5/20/60sourcehorizons. Pre-panelhistoryFROZEN:
boundedrollingrepairNOTcompletehistoricalPIT/newholdout. IndependentreviewnoC/I; source/summarytestsPASS.
March13targetfuturelabels101perturbed−50%,808allforecastfieldsinvariantexcepttime. CodeSnapshotsretained.
ACTUALDBappendCOMMITTED35newIFRSstandards(AZN9/CCEP3/FER3/GFS16/TRI4),old65216unchanged;
DBstd65251/raw706039unchanged.331sourcefacts=327IFRS+4ancillaryDEIkeptFILEONLY,norawDBimport.
Plan `tmp/quant-ifrs-standard-append-plan-20261031-v1/manifest.json`
SHA9d409bc5af17329f6f3a33f44e8e85ba250366221215abb8aff206c83767b0d5;oldstdpreimage
b54d6884d3a34d2082f5e6326cbca6aeb4e605843a836e4b3a75c7f12a636a84. Backup/probes/applyreceiptsinside.
Actualpartial/fullInnoDBrollbackPASSbeforecommit;244selectedsourcefieldschecked;NOreapplynow-nonemptyscopes.
ActualproductionasofMyBatis260reads/144present/116matchingmissingPASS,0writes;db-model-lookup-check.json.
Original507sourceobs/16accessions/75refs/67primarymatchesreverified;nativeunframed17quarterobsblocked.
FinalMaven278total/251executedPASS/27skipped0fail0error;Python31+91+117=239PASS.
OneindependentreviewtwoImportantfixed(samekeyreplacement,annualrangeyearguard);sourceCIKpadding+
ancillaryDEIbundlefalsepositiveguardsfixedbeforepreparewithtests,noguardbypass. NoGit/deploy/schema.
DailyautomationACTIVE15:10KST,promptupdatedwithactualappend+negativeMAE+nextwork. Manualsmoke
data/quant-daily/20261031T081246Z-b5645c18/result.json completed;batch49unchanged,RUNNING0,errors24h0,
629pendingunchanged/noactualscheduledfireyet(firstnewtimefuture). EntryNov2/exitDec1close;nofutureaccuracyclaim.
Postappend+rolling finalsmoke data/quant-daily/20261031T085441Z-e492322c/result.jsonCOMPLETED,
duplicatecollectionskipped;batch49/RUNNING0/raw706039/std65251unchanged. Automationupdatedwithbothnegative
experimentsandcompletedrollingprogress,don'trepeattheseasnewwork.
NEXT: PRE-PANELconsistenttrainingfeaturehistory+newtimealignedfreeperiods/officialrosters,purged/preseparated
evaluation;don'tretunesame20daysorrepeatcompletedrollingrepairasnewexperiment. Preserve629liveoutputs.

## Latest October31 — daily automation ACTIVE and controller verified

Read `docs/quant-daily-forward-automation-results-2026-10-31.md` FIRST.
Existingautomation `market-data-hourly-guard` updated(nameQuant20D매일수집·전방검증), ACTIVE/daily15:10KST;
hourlyversionreplaced, existingtarget/notificationpreferencepreserved. LocalPC+Codex+DBmustbeavailable.
Durablepromptcontinuesfreshinputs→actualcapture/freeze→maturederror→boundedfreehistoricalrecovery;
no modelpromotion/Gittransport/paidsources/accessbypass/bulkDBrebuild. Normalunchangedstatequiet.
Controller `scripts/quant_daily_forward.py` defaultsreadOnly; --run realexecution.
Latestsmoke `data/quant-daily/20261031T075424Z-fe12ec7e/result.json` completed;
SHA14e0682e475eb3309183fbf7bbfa22da473e3a7b7cf6802022ad4bb50ee8f039.
ExpectedOct30alreadyfrozen: no newAPI/modelrun, Yahoobatch49unchanged; all629pendingreevaluated.
CurrentDBraw706039/standard65216unchanged. FirstcohortentryNov2/exitDec1close,noaccuracyclaim.
OSexclusive lock/createOnlyattempts/backups; target/classesfirst+isolatedhelper; heldnamespreserved.
PricejobreusemustproveNYcutoff+requiredsymbolcoverage; postcollection403/429blocksnewforecasts.
Calendarinsufficient/expiredblocksnewcapturebutnotoldevaluation. Officialversionedextensionneeded
beforeactivecalendar(endsDec31) lacks21futurecloses; automationhasinstructionstocreateit,noguessedweekdays.
SECsafeinstantfix in SecFinancialStandardService: deriveeffectiveNULLonlyrawJsonno-start/exactend,
leavelegacyparser/persistedstart=endkeysandfactsunchanged (noNULLuniquedupregression). Futuremappingonly,
notarewriteofcurrentstandards. NewlyaddedSecEdgarFactDateTest14cases; focused26PASS.
Freehistory:2newGFSprimaryfilingsHTTP200/4721981bytes/67of67inlineobservationsreconciled;
507existingarchiveobservations/16accessions/75refsqueued,nativeIFRS6Kstillunsupported,noDBimport.
Evidence `tmp/quant-free-history-queue-primary-approved-20261031/`; seecontinuationdoc.
FinalMaven257total/230executedPASS/27skipped,0fail0error; Python31+91+100=222PASS.
Oneindependentreview3ImportantfixedwithRED/GREENinonescopedpass; nounrelatedauditexpansion.
Nextactualscheduledrunsmustreadthischeckpointandcurrentplan; donotreplayoldUSappend/oldfinancialplan,
relabelmissedhistoryasprospective, inventnews/analystinputs, orclaimgenuineerrorimprovementbeforematurity.

## Latest October31 — actual fresh forward collection started

Read `docs/quant-live-forward-start-results-2026-10-31.md` FIRST; this supersedes older zero-cohort notes.
Yahoo batch49:518requested/517success/1WBDfail; known latest2026-10-30 eligible513unique symbols.
Actual run `tmp/quant-live-start-20261031/actual-forward-run-v3/result.json` completed07:36:19UTC,
zero failed symbols/model stages; V9 20D DOW30=30/SP500=498/NASDAQ100=101 (629 index-specific rows).
Three actual append-only capture files in `data/quant-forward/`, SHA-pinned frozen cohorts in
`data/quant-forward-validation-20261031-v2/<INDEX>/cohort-first-actual.json`; pending evaluations
all629PENDING_OUTCOME, zero mature outcomes, no accuracy claim. Entry2026-11-02close/exit2026-12-01close.
Independent DB/archive verification matched all629 return/excess/probability/source values,
input datesOctober30, pre-generation eligibility and capture/manifest hashes.
Latest benchmarksOctober30/1347dates each; existingSeptember21levelsunchanged;29newdates each.
Actual US historical append COMMITTED718facts+101standards (ANSS/ILMN/MDB/TEAM), old65115standardsunchanged.
DB now raw706039/standard65216; partial/full rollback probes passed; V2manifestSHAf81d3a85b68c3db46b03f2540b4dc249ee419eec6e86ceea88ddcb111964947c.
DO NOT reapply append; issuer-scope guards reject retry. Do not blindly source filtered price dump with DDL.
Backups16data-onlyruntimefiles in `tmp/quant-live-start-20261031/runtime-before/` plus price/financial evidence.
The first helper failed at instance invocation before signalwrites; second exposed stale tmp classes,
stopped and produced no forward archives. Definitive runtime uses target/classes FIRST and isolated
`tmp/quant-live-operator-classes-20261031`; new `--verify-classpath` guard rejects mixed roots.
Never put generic tmp ahead of target/classes for product runs. No old attempt counts as a new observation.
Maven243total/216executedPASS/27skipped,0fail0error; focusedPython21forward+7historyPASS.
No weight change, promotion, Gittransport, deployment, webserver start or unattended schedule.
Continue new real sessions and eventual matured error evaluation; foreign IFRS/6-K and analyst/news history
remain gaps. Current prices do not establish freshness of every macro/financial/analyst/news input.

## Latest October31 — ACTUAL DB repaired and same-cohort20D replay completed

Read docs/quant-20d-db-restandardize-results-2026-10-31.md FIRST. DBstandardtable ACTUALLYCOMMITTED
65,115sameIDs/keys:51,831coherent+13,284quarantine; noDelete/Truncate; raw705,321unchanged.
Backups tmp/quant-db-restandardize-20261031/{standard-before.sql,raw-facts-before.sql,backup-manifest.json}.
Restoretestschemaquant_restore_check_20261031 retainsoriginal65115rows/checksum2574788078.
Finalplan-v2 committed-2026-10-31T00-20-03.874388400Z.json postimagef2ced3179cb4aacc78327676dec64f24ef9d0f5445fc69e03ebb361d56ba253b.
DO NOTreapplysamepreimageplan/blindrestore/drop.21newkeys stagedonly/notinserted.
ActualDBpath20dateV9MAEabs7.909838→7.915637/excess7.658563→7.665513; inputavailabilityconfound.
Authoritativematchedsource tmp/quant-matched-financial-all20-20261031/ restores90panels/233refs0uncovered,
355selectionchecks, V9abs7.912900/excess7.664449 STILLWORSE; oldcontrolparity0. All16160preds/readOnly.
Futurelabelperturb101Marchtarget−50%,808predictionfieldsinvariant. Actual10factorfixedweightresearch
tmp/quant-20d-factor-weights-matched-v1-20261031/9families no convincingimprovement, no promotion.
Forward actualindexboundV2protocols data/quant-forward-validation-20261031-v2/3indicesinitialized,
0realcohorts. V1unboundpreparation supersededunchanged. Lateststoredcandle10/28/V9signal10/29;
doNOTrelabeloldforecastasnewLIVE. Needfreshinputs/newbatch+future20Dresults (notclaimedcompleted).
Python120PASS;Maven231/27skipped/204executedPASS0fail0error; noGittransport/serverstart/automation.
Nextmeaningfulworkfreshrealforwardobservations+continuousasofhistoricalinputs, notparametersearchonsame20days.

## Latest October31 — financial root causes, prospective archive, robust followup

Read docs/quant-20d-followup-results-2026-10-31.md FIRST. q4-fin-20261031 andq4-hist-fin-20261031
sourcechanges: period/adjacency/currency/EPS guards; actualcurrentbindings propagated; standardizer
coherentperfieldcurrency/duration+rawevidence. ExistingDBlegacyrows NOTrebuilt (same-rowhiddenmixtures
remainunproven), noDBwrites/serverrefresh/deployment. SELECTprofile shows many FY/Qlabeldurations
ineligible; do not sayallrawobservationswrong. Newforwardarchive wiredSpringV9batch, DBpersisted20D
values observedUTC appendCREATE_NEW,data/quant-forwardignored, historicalV8inputs rejected beforewrites.
Not executed on actualLIVEbatch yet; no completePITclaim.
Robust final v6 directory + scripts/verify_quant_20d_robust_research.py durablehistoricalsource snapshot.
All44quantileslopes0; tinyexcessgain vsV9explainedpastmedian, nonoverlapdoesnotconfirm. No promotion.
4newSEC PDD/ALNY originals3,541,431bytes verified;PDDsamefilingCNYpair +59.0362%rev/+87.3080%income
in tmp/quant-20d-primary-inputs-20261031/verified-native-pair.json, notDBimported.
Python91PASS;Maven222with27skipped/195executedPASS0failure0error finalfollowuplog. Oneindependentreview
ImportantLIVErelabelRED→GREEN; extraassembler7testsRED→GREEN+selfreview. Wholeprogramnotcomplete:
safelegacyrestandardization, continuoushistoricalasofinput, sameprotocolreplay, trueforward20Dvalidation.
Do not resume blinddataforaging or repeat unchangedexperiment/refit/tests justforcommittransport.

## Latest October31 — purpose reset to real20D error reduction

Read docs/quant-20d-error-results-2026-10-31.md before further acquisition. New experiment:
tmp/quant-20d-error-research-v2-20261031/result.json, summary.csv, verification.json.
20 reconstructed dates, common11test dates/1111rows pertarget/family; learned ridge candidates
do NOT improve V9 absolute MAE8.5296pp; ZERO8.5271 essentiallysame. Excess pastmedian8.3410vsV9
8.3854 tiny explored improvement only, NOT promote. No new weights rolled out. Do not report
prediction quality improved just because tests pass. Only product change this round: finite,
nonnegative strict MAE decrease required in ExpectedReturnPromotionGate. Other prior dirty
changes preserved.82Python/6focusedJava pass; fullMaven200tests/27skipped/0failure0error,
173executedPASS. Final authorized log tmp/quant-20d-maven-authorized.log (sandboxattachblocked).
Independent focused review noCritical/Important; invalidbaselineMAE testminor deferred.
Remaining program: LIVE immutable snapshots/model-binding contract, continuous past-asof inputs,
productionperiod/currency/ADS guards, free primary-evidence expansion justified by usable windows,
robust conditional-median candidates + timegroup validation + actual forward observation. Notdone.

## Latest October31 continuation — March13 recovered, SOLS still blocked

Authoritative March run is `tmp/quant-march-model-v3-final-20261031/` (result.json,
independent-verification.json,combined-java-accounting.json). Counterfactual v3-perturbed is
regression-only. Staging uses `tmp/quant-march-inputs-20261031/inputs-v3.json` with native-inputs-v2.json.
Unversioned/v2 March models are SUPERSEDED: independent check caught ALNY273day FY-label,
fresh reviewer caught PDD FY2024USD/FY2023CNY falsegrowth. Both now guarded RED→GREEN.

All14missing names recovered for2026Mar13;101members×V2–V9=808newforecasts; combined20full
decisiondates permodel/160groups/16160forecasts. Cash-to-cash periods remain14: March13overlaps
priorselectedholding, so no extra month/calendar-end/performance-extension claim. Allprior19date
results and combined compounding unchanged.73PythonPASS;808futurelabelinvariant; Python/Java
metrics/accountingmatch. CurrentDBinvariants byte-identical,namespace0rows,strictrecord5insufficient.

Six new original SEC JSON forCCEP/FER/TRI3,072,656bytes200/hash/CIK/metadata checked. TRI40F,
CCEP/FERnoinventedquarters, EUR EPSnullUSDvaluationwithoutFX. ARMADS1:1;PDD2.60USDordinary×4
derivedroundedADS10.40. Incompatible priorCNYfinancialcomparison omitted, PDDgrowthfallback50,
notfalse14; no older-year skip. FERunverifiednewoperatingtagleftnull. Existingmissingfactor/UNKNOWN
sector/native100market/training limitations retained. No product/Git/server/databasewrites.

SOLSboundedfreefollowupstillnoindexeventnotice; issuer confirmsOct30regularwaynotmembership.
HTTP403errorretainedseparatefromcuratedprimaryreadertext; no retry/bypass. Evenwhenissued<=10bars
<80; noHONbackfill/drop/thresholdwaiver. See docs/quant-sols-followup-2026-10-31.md and
docs/quant-march-foreign-evidence-2026-10-31.md. Fullfinaldetails docs/quant-march-recovery-results-2026-10-31.md.

One fresh review +onefixpass only. ReviewImportantcurrencyissuefixedwithregression+73suite;
no secondreviewclaim. Next substantive gaps: actualcompatiblepriorPDDcurrency/recentforeignquarters,
productionfinancialperiodmislabels(separatetask;DBnotchanged), explicitSOLSindexnotices butcoldstart
cannotfixOct31, ANSSacquisitionsettlement andofficialrosters/rights. Researchonly, noofficialPIT/liveproof.

## Latest resumed work on October31 2026 — V9 market inputs recovered

Authoritative new artifacts: `tmp/quant-v9-historical-market-final-20261031/` contains
result.json, combined-java-accounting.json and independent-verification.json. Counterfactual
`tmp/quant-v9-historical-market-perturbed-20261031/` is regression-only. Source-share/price
staging is `tmp/quant-v9-market-inputs-20261030/inputs.json`; older outputs stay untouched.

Filled59missing symbol/date keys (ANSS/AZN/GFS/ILMN/MDB/TEAM) using prior-reported actual
common shares × archived decision closes, not current shares or exact daily/float capitalization.
V9-only reuse of saved V8 restores11whole101-member decisions2024Jul31–2025May30,1111forecasts.
All1052previously known forecasts unchanged except research namespace/calculation timestamp.
Combined V2–V9 now19full dates each/14nonoverlap periods each,152groups/15352admittedforecasts,
firstentry2024Aug1/lastexit2026Mar30, not an uninterrupted month-by-month calendar backtest.

RawSEC XBRL37keys; curatedTEAM/AZNprimarytext22keys retains weak source scaling. AZNordinary
counts unscaled with ordinary-equivalent archive prices. Strict filed<decision, no same-day filing
or EPS denominator. Max sharemeasurementage366days explicitly reported. InvalidANSS2014zeros
resolved by boundedfiling scope2023Jan1<=filed<2025May30; invalid in-scope values still failclosed.

65Python testsPASS;1111counterfactual forecasts invariant aftertargetlabel-50%; separatePython/Java
metrics/accountingmatch; V2–V8results unchanged. FreshOct31DBinvariants byte-identical, newnamespace
0rows, strictrecord5 insufficient/0months. Scoped fresh review noCritical/Important/Minor findings.
Reviewer verified hashes/comparisons/selection/accounting; DB invariants verified by main.
External authenticity/price adjustment/native quality remain explicit limits. No product/Git/server writes.

Details `docs/quant-v9-market-recovery-results-2026-10-31.md`. Next:14missingMar13predictors,
explicitSOLSindexnotice/80historybarrier, ANSSacquisitionsettlement andofficialrosters/rights.
Do not reruncompletedmodels or treat reconstructed/provider/curatedinputs as officialPIT/liveproof.

## Latest resumed work on October30 2026

ANSS provider history + SEC ANSS/MDB/ILMN facts are now staged and run through existingV2–V8
for11older full101member dates2024Jul31–2025May30. Combinedwith8preserved dates:19decision
dates/14nonoverlapperiods forV2–V8, firstentry2024Aug1, lastexit2026Mar30. Acceptedforecasts
14241across141model/dategroups. V9 remains8dates/6periods; newpartial95–97forecasts are NOT
eligible portfolios (4–6market-input gaps eachdate,1052partial rows excluded). No currentcapfill.

Authoritative outputs are tmp/quant-older-model-recovery-v2-final-20261030/{result.json,
independent-verification.json,combined-java-accounting.json}. Counterfactualv2-perturbeddirectory
isregressiononly. Initialnon-v2older-modeldirs superseded after mainfoundcopiedmarketquery
daterangeerror. Correctedrange recovers95/96/97actualknownmarkets, not101; all7777V2–V8fields
unchangedbyfix. Preservealloldexperiments/outputs; do not rerun completedmodelstagesonresume.

NewrawSEC6originals9374220bytes in tmp/quant-older-inputs-20261018. ANSSChartExchange370bars
Jan2024–Jul16,2025; adjustment/licensingunproven, secondprovidercomparison keptseparate.
Staging tmp/quant-older-staged-20261030/ includes11dates1111holdings strict21sessions/80history.
SOLS exactindexdates remainunresolved; fourprimaryNasdaqdocumentsverified and actualOctober20
when-issued/October30regularwaytradingdistinguishedfromindexdates. Evenwhen-issued<=10bars,
soOctober31cannotpass80candles. NoHONsubstitution orSOLSdropping; June2025ANSSacqnotbridged.

57Python testsPASS, all8829rawforecastobjects counterfactual-invariant exceptcalcAt,141fullgroups
metrics/accountingmatchedPythonandJava. Scopedreviewfirstpassno blockers; mainfixedonequery
rangebug afterward; no secondreviewclaim. FreshOct30DBinvariants unchanged, namespace0rows,
strictrecord5 stillinsufficient/0months. Operatingmacro/outcometotalsdifferlegitimatelyfromOct18;
use tmp/quant-older-before-invariants-20261030.tsv baseline, notoldtotals. No product/Git/serverwrites.

Details docs/quant-older-model-results-2026-10-30.md. Next: V9actualhistoricalshares/caps,
Mar13missing14predictors, explicitSOLSindexnotice, officialdatedrosters/rights. Researchonly.

## Latest extension after the three-date TEAM checkpoint

Five additional complete101-member decisions (2025Jul31,Aug29,Sep30,Nov28,Dec31) now run
unchanged V2–V9 formulas in a new file-only namespace. New4040forecasts; combining prior2424
unchanged forecasts gives8dates/6464forecasts/64model-dategroups and6nonoverlap20Dperiods,
firstentry2025Aug1/lastexit2026Mar30. Research reconstruction only, never LIVE or officialPIT.

New free SEC AZN/GFS facts+submissions4originals and TEAM earlier-share primary text recovered.
IFRS20-F/6-K actual standalonequarter support, GFS null-fp calendarlabels, strict priorfilings.
AZN archived close already ordinary-equivalent after2026Feb2 1:2conversion; ordinaryEPS/shares
must stay unscaled. Manual TEAM/AZN share text remains distinct from actual GFS rawXBRL.
Existing missing-factor, nonadjacentquarter and frozen earlier-training policies retained.

New issuer-republished full101tables effective2025Feb26/Aug26 corroborate10sets conditionally,
not officialNasdaqPIT. SOLS dateconflictOct20–Nov10 vscommunityOct30–Nov5 unresolved; Oct31
has102members and stays excluded whole, as does Mar13's14missingpredictors. Do not dropnames.

Latest details: docs/quant-extended-model-results-2026-10-18.md and
docs/quant-extended-roster-evidence-2026-10-18.md. Normal output directory:
tmp/quant-extended-model-recovery-final-20261018/ (result.json, independent-verification.json,
combined-java-accounting.json). Perturbed result is regression-only. Both refuse overwrite.
Do not rerun completed model stages; old3dateexperiment still passes its original file verifier.

53Python tests passed, new5RED→GREEN. All4040newforecastfields unchanged by targetlabel-50%
exceptcalcAt. SeparatePython/Java accounting and forecastmetrics matched64fullgroups.
Frozen DB invariants unchanged; namespace0DBrows, strictrecord5 insufficient/0months. ProductJava
unchanged; no redundant Maven. Scoped review noCritical/Important; comparativefiscalyearlabel
minor deferred (selection usesendDate). No Git/deploy/server writes. Maindirtypreserved.

## Latest follow-up on TEAM inputs and existing models

The previous17:22 price-only state below is historical. TEAM SEC companyfacts/submissions and
curated prior-quarter actual A+B shares have now enabled a separate file-only existing-formula
experiment: **3 dates × 101 members × V2–V9 = 2,424 forecasts**, with2nonoverlap20D portfolios.
Classification remains RECONSTRUCTED_RESEARCH_NOT_PIT; original missing-factor policies remain.
No models/DB/operating code or strict promotion rules were modified; zero DB rows in the new
NASDAQ100_TEAM_INPUT_RESEARCH_20261018 output namespace. Strict record5 still insufficient/0months.

Primary latest technical results: docs/quant-team-model-recovery-results-2026-10-18.md.
Normal artifacts: tmp/quant-team-model-recovery-final-20261018/result.json and independent-verification.json.
Counterfactual directory quant-team-model-recovery-perturbed-20261018 is regression-only, not performance.
Do not use earlier exploratory tmp/quant-team-model-recovery-20261018 output: superseded by final
source-object-isolated run. Both completed final outputs refuse overwrite. Do not repeat replays.

Checks: 48Python tests passed; full101key accounting/MAE/Brier/direction/IC matched; future target
labels changed to-50% leave all2424forecasts identical except actual calculation timestamps.
Frozen original prediction/macro/outcome invariants match prior checkpoint. Fresh scoped review
no directly related Critical/Important. Production Java unchanged; no redundant Maven full rerun.
An additional SGX-hosted issuer-republished101-security list corroborates all3sets after a documented
later swap inversion, but official contemporary whole rosters are still0. No PIT promotion.

No commit/push/merge/deploy/server restart. Current main dirty changes preserved. Next: earlier
incomplete historical prices/identity/event settlements and official dated roster/change chain;
do not claim 2 sparse positive research periods prove an edge or rerun saved stages on resume.

## Newest follow-up — 2026-10-18 about17:22 KST

**Bounded price recovery finished; full Quant backtest still blocked. No models/DB were changed.**

- Price-only full-population staging:44symbols/32114additional closes from already archived free
  evidence + canonical DB export. All matching overlaps checked, no canonical overwrite. New
  program scripts/quant_complete_windows.py; outputs tmp/quant-complete-windows-recovered-20261018/.
- Nasdaq10complete-price dates, first2025-07-31,last2026-03-27;6chronological nonoverlapping
  periods. Whole-population equal-weight PRICE_ONLY data baseline gross1.215884318%,
  net0.008914809%, benchmark6.20092998% (10bps per side). Not Quant strategy performance/PIT.
- Existing Quant full-forecast complete dates0, S&P price-complete dates0. Latest Nasdaq missing
  historical forecasts are TEAM (and more names for March13); do not fabricate them from prices.
- New actual BNY public issuer notice + Yahoo BNY prices1707rows acquired.752BK-mapped close
  candidates saved but NOT imported or merged into completed-window count. scripts/acquire_quant_window_prices.py
  --verify checks hashes/identity text/quotes; no automatic ticker alias. New membership evidence
 3responses saved; Jan20WMT/AZN corroborated, complete official dated rosters still0.
- Python full suite43passed; new9tests RED→GREEN. New standalone Java accounting helper matched
  fullmember keys and Python6period arithmetic. Product Java unchanged; no need repeat200Maven suite.
- Fresh reviewer main staging scope noCritical/Important. Frozen pre/post invariants still identical.
- Primary local report output/quant-complete-windows-20261018/report.html (portable, partial state).
  Packaging/structural QA passed; no headless Chromium, so browser/visual QA not performed.
  Supporting audit docs/quant-complete-window-recovery-notes-2026-10-18.md and membership note.
- No commit/push/merge/deploy/serverrestart/modelreplay. main dirty preserved. MariaDB27208 remained
  listening3306, web8080 not started. Do not rerun completed baseline/model calculations on resume.
- Next focus: actual TEAM historical predictor inputs in new research version; safe BNY alias
  confirmation plus CTRA/HOLX historical closes; dated official baseline/change chain. Never use
  equalweight baseline success as Quant full-performance certification.

## Newest follow-up — 2026-10-18 about 16:10 KST

**Macro integration and bounded validation are saved. Do not replay the completed models.**

- New SP500_MACRO_FREE_20261018 / NASDAQ100_MACRO_FREE_20261018 namespaces were atomically
  committed at10:54. Each has37 previous-day ALFRED macro feature rows. V7 was copied;
  only V8/V9 were recalculated. Exact input/code hashes are registered. Frozen old namespaces
  and operating macro inputs remain unchanged under recorded before/after invariants.
- Same-population S&P V9 Brier .24923966→.24873374, direction53.5370→54.4540%; Nasdaq
  .24876104→.24802863, direction54.1214→55.5272%. Improvement is small, not investment proof.
  S&P V9 IC worsened; interval coverage worsened. No parameter tuning or LIVE retagging.
- All37 decision dates are incomplete for a full-population portfolio; periodCount0, netReturnNaN.
  Strict operating evaluationID5 remains INSUFFICIENT_DATA, unchanged and intentionally reused.
- Independent raw-to-feature verification74rows and SQL-to-Java12metricgroups passed. All source,
  cutoff, timestamp and prediction-value flags0. V7 control unchanged. IC not independently checked.
- Full Java200 passed (DB tests enabled, failures/errors/skips0), Python34 passed. Logs:
  tmp/quant-macro-final-tests-20261018.log and tmp/quant-macro-python-tests-20261018.log.
  Do not rerun unchanged suites merely to resume/commit/push.
- Added8 official terminal-event facts for previously missing symbols. They are evidence, not
  OHLCV: all57 missing-price symbols still require prices; no fake recovery/CVR values imported.
  See docs/quant-extra-evidence-2026-10-18.md and tmp/free-quant-extra-20261018/.
- Main report docs/quant-macro-asof-validation-results-2026-10-18.md; result JSON
  tmp/quant-macro-asof-validation-20261018.json; independent verification script
  scripts/verify_quant_macro_integration.py and result tmp/quant-macro-independent-verification-20261018.json.
- No Git staging/commit/push/merge/deployment performed. All unrelated dirty files preserved.
- On resume MariaDB PID27208 port3306 was listening; web8080 was not listening. No server
  restart was requested/performed in this follow-up. No replay process remains active.
- Next work: explicitly prioritize missing member/forecast/price windows; terminal-event staging
  needs cash/share/CVR settlement contracts and regression tests before actual return accounting.
  Never reuse same namespace with different code/input hashes, weaken strict gate or fabricate PIT.

## Newest follow-up — 2026-10-18 10:05 KST

User authorized additional free evidence acquisition and fixing restart first-request delay.
Both follow-up workstreams are finished; this section supersedes the earlier cold-latency limitation.

- Additional official free evidence: 276 raw files, 59,368 day-as-of macro observations, seven
  series, 37 historical decision dates plus current comparison. All hashes/raw-to-derived maps
  independently verified. No DB import or frozen namespace replay; strict gate remains insufficient.
  See `docs/quant-free-evidence-followup-2026-10-18.md` and `tmp/free-quant-followup-20261018/`.
- Root cause: repeated whole-table model scans, wide historical row fetches and repeated per-row
  source classification. Seven covering indexes applied via
  `src/main/resources/db/quant-health-query-indexes.sql`, fresh-install schema aligned. Only two
  intermediate indexes created by this follow-up were replaced; no original index/data removed.
- `findPitQuality` now classifies source groups with row-count weights; all original filters,
  scores, category overlaps and empty-input behavior are preserved. Actual entire health and PIT
  TSV outputs are byte-identical to before the fix. Seven real DB regression tests pass.
- Genuine first request after app restart, no prewarm: SP500 **5.734s** (was70.961s), Nasdaq
  **2.487s**, Dow **2.061s**, all HTTP200. Cache-expired S&P4.895s; next cached request0.083s.
  Do not claim instantaneous uncached response or cold OS/disk testing. No old snapshot bypass.
- Final full Java suite **195/195 passed**, failures/errors/skips0; Python **28/28 passed**.
  Reuse this evidence for unchanged files; do not rerun research models/tests merely to resume.
- Current web PID **8520**, MariaDB PID **25476**, ports8080/3306. Startup SQL initialization
  and external refresh disabled. Source/runtime mapper hash matched. No active replay helpers.
- Main local changes only; no Git staging/commit/push, merge or deployment requested/performed.
- Consolidated follow-up report: `docs/quant-followup-first-request-fix-2026-10-18.md`.
  Runtime evidence: `tmp/quant-final-cold-timing-20261018.json`,
  `tmp/quant-followup-final-web-smoke-20261018.json`,
  `tmp/quant-first-request-final-full-tests-20261018.log`,
  `tmp/quant-free-followup-python-tests-20261018.log`.

## Latest state — resumed work finished, 2026-10-18 09:16 KST

**This section supersedes the historical shutdown instructions below. Do not replay V3 or Nasdaq
again. No model replay or validation helper remains active.**

- S&P V2 was retained. V3–V9 all committed successfully; resumed process exited 0 at 09:08:31.
  V2–V8 each have 107,412 predictions / 81 dates; V9 has 106,946 / 81 dates.
- Nasdaq V2–V9 all committed and exited 0. V2–V8 each have 21,164 predictions / 82 dates;
  V9 has 21,108 / 82 dates. Total saved research predictions: 1,028,086 across both namespaces.
- Both namespace diagnostics are finished. All 16 model sample counts, Brier, MAE and directional
  accuracy match independently calculated SQL endpoint-level results within stated tolerances.
  Rank IC was not independently recomputed by SQL. Missing rows stay in target denominators.
- V9 S&P: 38,318 / 41,247 matured targets, coverage 92.8989%, rank IC 0.02181756,
  Brier 0.24993905, direction 50.1957%, excess-return MAE 6.622908 percentage points.
- V9 Nasdaq: 7,575 / 8,542 matured targets, coverage 88.6795%, rank IC 0.00558310,
  Brier 0.25204314, direction 50.3498%, excess-return MAE 8.557325 percentage points.
- All usable research predictions were calculated after their historical decision dates. They
  are NOT LIVE/PIT predictions and do not establish investment edge or measured live q3 results.
- Strict production evaluation ran exactly once after replay, ID 5, status/decision
  `INSUFFICIENT_DATA`, no execution error, 0 valid rows / 0 valid months. Raw dataset targets
  33,983, not 0: summary targetRowCount 0 is the eligible-window population. Provenance,
  eligible forecasts and benchmark evidence remain insufficient; no gate was relaxed.
- Corrected outcomes 119,393: invalid prices 0, invalid date orders 0, arithmetic mismatches 0.
  Research LIVE/cutoff/date/value flags and quantile-order failures are 0. Legacy 87,105 rows,
  last calculation 2026-10-17 22:09:46 and return sum 126767.450705 are unchanged.
- Final application verification remains **188/188 tests passed**, real DB tests enabled,
  finished 08:43:44. No product code changed after this full run; do not repeat it for reporting.
- Web app restarted with corrected mapper, PID **3764**, port **8080**. Workspace MariaDB
  PID **25476**, port 3306. SQL init and external startup refresh remain disabled.
- Final smoke: cold `/quant` timed out at 45.23s; warm `/quant` HTTP 200 in 0.56s;
  `/stocks/CPB` 200 in 0.58s; `/signals/backtest` 200 in 0.19s. **Cold-start latency remains
  unresolved**; do not mark all runtime issues fixed. Source/runtime mapper SHA256 matched.
- All changes remain saved locally on main; no commit, push, merge or deployment requested.

Final artifacts (reuse; do not recompute merely to resume):

- `docs/quant-free-data-replay-2026-10-18.md` — consolidated results and limitations.
- `tmp/quant-research-validation-20261018.json` — combined 16 saved diagnostics and strict result.
- `tmp/quant-research-validation-{sp500,nasdaq}-20261018.json` — original diagnostic outputs.
- `tmp/quant-research-independent-sp500-20261018.tsv` / corresponding crosscheck JSON.
- `tmp/quant-research-independent-nasdaq-keyed-20261018.tsv` / corresponding crosscheck JSON.
- `tmp/quant-research-final-counts-20261018.tsv` — committed counts and legacy invariants.
- `tmp/quant-resume-final-full-tests-20261018.log` — 188 tests, zero failures/errors/skips.
- `tmp/quant-research-final-web-smoke-20261018.json` and final warm-quant JSON.

Remaining genuine work, not an unfinished replay: sufficient historical PIT membership/features,
strict benchmark/macro evidence and newly matured q3 outcomes before any live-performance claim;
separately diagnose the cold `/quant` delay if requested. Do not fill gaps with today's universe,
backdated calculation timestamps, revised macro values or invented prices/caps.

## Historical shutdown checkpoint (superseded for current execution)

Saved on 2026-10-18 (Asia/Seoul). User explicitly requested stopping to turn off the PC.
Repository: `C:/dev/codex-demo`; branch `main`; HEAD `b134d9f`.
Code remains saved on disk as working-tree changes. No commit or push was requested or performed.
Preserve all unrelated tracked/untracked files; do not reset, clean, stash, or stage everything.

## Resume here

1. Read `AGENTS.md` and `docs/quant-free-data-replay-2026-10-18.md`.
2. Start the workspace MariaDB if needed (config `.tools/mariadb-data/my.ini`, binary
   `.tools/mariadb-11.8.2-winx64/bin/mariadbd.exe`). Keep helpers hidden on Windows.
3. Confirm the saved DB counts below and that no other replay is active.
4. **Keep completed S&P V2. Resume S&P at V3, using the optimized, tested code.**
   Compile `scripts/QuantResearchReplay.java` with `target/classes` plus dependencies from
   `tmp/quant-runtime-classpath.txt`, output classes to `tmp`.
   Run `QuantResearchReplay SP500_FREE_20261018 3` with `tmp;target/classes;<dependencies>` and
   `-Xmx3g`. The helper uses headless MariaDB, `spring.sql.init.mode=never`, and disables signal
   collection/startup refresh. Every model stage is a separate transaction.
5. Run `QuantResearchReplay NASDAQ100_FREE_20261018 2` (no Nasdaq stages completed yet).
6. Compile/run `scripts/QuantResearchValidate.java` to save
   `tmp/quant-research-validation-20261018.json`, including descriptive 20D diagnostics for all
   V2–V9 models and the **unchanged strict production evaluator**.
7. Append actual results to the report; distinguish research metrics from PIT/profitability proof.
   Do not fabricate LIVE timestamps or weaken promotion gates.
8. Restart the app on 8080 if desired, with SQL init and external startup refresh disabled.

## Exact saved progress

- Corrected community S&P data: six bounded overrides repair seven official-action mismatches;
  135/135 comparable actions now match; raw inputs unchanged, still NOT official PIT.
- Data artifacts: `tmp/free-quant-corrected-2026-10-18/manifest.json` and CSVs.
- Imported separate research namespaces (production universe untouched):
  - `SP500_FREE_20261018`: 41,247 membership keys, 2,514 FRED benchmark dates, 30,715 copied
    same-symbol/same-date market-context rows; manifest SHA
    `e0f717c1e5fbd762cc7f1bf1db9c0353e8f9d2a4d805ec040629411efd715b78`.
  - `NASDAQ100_FREE_20261018`: 8,845 membership keys, 2,514 benchmark dates, 6,424 copied context
    rows; manifest SHA `d98285cdea9b95fe4e0ff4811db0f9ca2119d25c79d30083831ce65f57692434`.
- Importer `scripts/import_quant_research.py` is hash-checked, research-namespace-only and atomic.
  Registry migration: `src/main/resources/db/quant-research-dataset.sql` (already applied).
- Fixed immature-outcome starvation in `StockBacktestMapper.xml`; the main drain added 112,486
  labels and exhausted eligible work. Eight real MariaDB regression cases cover queue boundaries.
- CPB free-history gap recovered: **88 appended dates, 2026-06-12–2026-10-16**; all old 1,260
  OHLCV rows unchanged. Archive/audit in `tmp/cpb-gap-20261018/`; source SHA
  `49970dc659f6641e5be6b9d9bef4bf7ce1284b1a7e722141239d51f6f364226b`.
  `scripts/QuantAppendCompatibleCandles.java` performed explicit full-overlap dry-run and apply.
- CPB follow-up added 22 labels (24 upserts); corrected outcomes now **119,393**.
  Historical 5D/20D/60D each **19,433**; stored LIVE 5D/20D/60D = 24,205 / 23,124 / 13,765.
  All stored return arithmetic agrees with endpoints within 1e-6 percentage points.
  Legacy table remains **87,105** rows, unchanged.
- **S&P V2 COMMITTED**: `EXPECTED_RETURN_V2_CORRECTED_20261017`, **107,412 rows**, 81 decision
  dates, 3,410,104 aggregate training-row uses, 9 calibration rows.
  Log: `tmp/quant-research-replay-20261018.log`, `REPLAY_STAGE_DONE V2 elapsedSeconds=704.3`.
- The old JVM entered V3 when the user requested shutdown. It was stopped; the uncommitted V3
  transaction is rolled back, not saved as a half-completed model. Next stage is **V3**, not V2.
- A preceding incomplete V2 run (before the CPB repair) was already rolled back. Its outputs do
  not exist. The committed V2 above includes the CPB repair.
- Local per-date/horizon neighborhood-order reuse is implemented in
  `StockPortfolioBacktestService`: 30 added lines, no global cache, unchanged model math.
  Exact-value and date/horizon/cutoff isolation tests pass. Bounded compute benchmark improved
  roughly 3–7x (advisory elapsed times, not a production throughput guarantee).
  Compiled new classes are ready for the resumed V3 pipeline.
- New pure `ReconstructedReturnDiagnostics` retains the full matured-membership denominator,
  reports omissions/duplicates/gaps/monthly metrics and actual calculatedAt; promotion disabled.

## Verification evidence (do not repeat just because resuming)

- Final Java tree: **185 tests passed, 0 failures/errors/skips**, real DB tests enabled.
  `tmp/quant-neighborhood-full-suite-20261018.log` (finished 01:14:17 KST).
- Python: **23 tests passed** (`python -m unittest discover -s src/test/python`, workspace TEMP/TMP).
- `/quant` warm HTTP200, 0.36s; CPB detail and backtest HTTP200. First cold `/quant` request
  exceeded 25s; retain as a performance follow-up, not a silently passed cold-start check.
- Do not rerun the full suite unless code changes; remaining work is model execution, runtime
  data diagnostics and reporting. No commit/push/merge was requested.

## Service shutdown notes

Owned replay PID 9972 was stopped after V2 committed; V3 rollback finished (no active DB
transactions remained). Owned web app PID 12332 was stopped. Workspace MariaDB PID 9784 was
shut down gracefully using `mariadb-admin shutdown`. A subsequent process check confirmed all
three processes absent. Files, committed DB data and this checkpoint are ready for PC shutdown.

## Resume execution — 2026-10-18, 08:26 KST

User requested resuming from this checkpoint. Workspace MariaDB restarted (PID 25476).
Verified before execution: S&P V2 still 107,412 rows; corrected outcomes still 119,393;
both research manifest hashes unchanged; no active transactions or duplicate replay processes.
Product code and its compiled optimized class match the previously verified saved files.

Execution now in progress:

- S&P `QuantResearchReplay SP500_FREE_20261018 3` — PID 12532,
  log `tmp/quant-research-resume-sp500-20261018.log`.
- Nasdaq `QuantResearchReplay NASDAQ100_FREE_20261018 2` — PID 13128,
  log `tmp/quant-research-resume-nasdaq-20261018.log`.

Ruling: reuse the existing authorized main checkout, checkpoint ledger and reviewed code; do not
create a new worktree, recommit unrelated files, or repeat the already passed full suite solely
because the PC restarted. Remaining acceptance criteria are actual stage commits, runtime data
checks, descriptive diagnostics and the unchanged strict evaluator. No implementation change is
currently needed. The two jobs have different index namespaces and no shared model-output keys.

### Progress at 08:44 KST

- Nasdaq V2–V9 all committed, exit0, 396.5s total. V2–V8 each21,164 rows; V9 21,108.
  Research diagnostics saved in `tmp/quant-research-validation-nasdaq-20261018.json`.
  V9 matured targets8,542 / usable7,575 / immature303 / coverage88.6795%.
  RankIC0.00558310, Brier0.25204314, direction50.3498%, MAE8.557325pp: no demonstrated edge.
  All7,575 usable predictions were calculated after their historical decision dates, not LIVE/PIT.
- Original independent SQL endpoint-level calculation matched all8 Nasdaq model counts,
  Brier, MAE and direction rates (see crosscheck JSON). Keyed SQL rerun in progress to measure
  the optimized lookup. The SQL and comparison scripts are saved in `scripts/`.
- S&P V3 committed107,412rows /81dates /694.2s. Same PID12532 now executingV4 automatically,
  followed byV5–V9. Do not restartV2 orV3. Preserve the active process.
- App started on8080, PID8452. Warm `/quant` HTTP200 in0.43s, CPB and backtestHTTP200.
  Cold `/quant` still exceeded45s; keep as a separate existing performance limitation.

### Scoped query correction during validation

The evaluation population lookup joined corrected outcomes by symbol/date, scanning ~1,733
same-date candidates per member. `EXPLAIN` verified the date-key fan-out. Replaced only
`findResultsForEvaluation` with a parenthesized LEFT JOIN of (signal INNER JOIN outcome), using
the existing signal identity and outcome PRIMARY(snapshot_id,horizon_days). Missing members
remain a single null row even with unused signal versions; actual duplicate outcomes remain
visible for strict exclusion. Actual119,393 outcomes have0orphan sources/identity mismatches.
No schema/index migration, labels, predictions, formulas or strict eligibility rules changed.

TDD: new4realMariaDB temporary-table tests; RED1planfailure+3behaviorpasses; GREEN4/4.
An old source-text assertion requiring the previous join spelling was replaced by these real
behavior tests rather than weakening population rules. Final changed application tree:
**188tests passed,0failures/errors/skips**, full suite finished08:43:44KST, log
`tmp/quant-resume-final-full-tests-20261018.log`. No more full reruns without further code changes.
The running replay uses unchanged mathematical code; the new query is for the fresh strict
validator process. Restart web app once after final validation to load the updated mapper.

Ruling: validate completed namespaces separately using an optional namespace argument to
`QuantResearchValidate`, avoiding repeated Nasdaq calculations. The strict production evaluator
runs only with the S&P validation, once after S&P finishes. No permission prompt or Git operation.

### Progress at 08:51 KST

S&P V4 committed107,412rows /81dates /744.0s; activePID12532 is nowV5, thenV6–V9.
V2–V4 are complete and must not be rerun. Nasdaq validation and its keyed independent SQL
comparison are complete (`MATCHED`,8models). Files:
`tmp/quant-research-independent-nasdaq-keyed-20261018.tsv` and
`tmp/quant-research-crosscheck-nasdaq-keyed-20261018.json`.

The new application mapper was evaluated directly from its bound SQL: production S&P population
33,983rows /2,790missing outcomes,2.199s on currentDB. Final strict evaluation still pending the
S&P replay. Core application verification remains188/188; no new product changes since that run.
