"""Final source, raw-preservation, account and extension evidence audit."""
import json
from pathlib import Path
import numpy as np
import pandas as pd
from build_sp500_extension import OUT,DATA,BASE
from build_sp500_daily_dataset import read_csv,digest,write_csv


def main():
    acquired=[]
    for name in ('acquisition.json','price_acquisition.json','supplement_acquisition.json'):
        acquired.extend(json.loads((OUT/name).read_text()))
    saved=0
    for r in acquired:
        if r.get('sha256'):
            assert digest(OUT/r['path'])==r['sha256'];saved+=1
    parent=read_csv(BASE/'inputs/prices.csv.gz')
    current={ (r['symbol'],r['trade_date']):r for r in read_csv(DATA/'inputs/prices.csv.gz') }
    for r in parent:assert current[r['symbol'],r['trade_date']]==r
    lineage=read_csv(OUT/'added_price_lineage.csv.gz')
    assert len(current)==len(parent)+len(lineage)
    parent_count=len(parent);del current,parent
    results=pd.read_csv(OUT/'revalidation.csv')
    fills=pd.read_csv(OUT/'revalidation_fills.csv')
    equity=pd.read_csv(OUT/'revalidation_equity.csv.gz')
    for r in results.itertuples():
        f=fills[(fills.scenario==r.scenario)&(fills.policy==r.policy)&(fills.fee==r.fee)]
        m=equity[(equity.scenario==r.scenario)&(equity.policy==r.policy)&(equity.fee==r.fee)]
        assert r.status=='COMPLETE_RESEARCH_ONLY' and len(f)==r.trades
        assert np.isclose(f.pnl.sum()/1000,r.total_return_pct,atol=1e-8)
        assert np.isclose((m.iloc[-1].equity/100000-1)*100,r.total_return_pct,atol=1e-8)
        assert int(m.iloc[-1].positions)==0
        peak=np.maximum.accumulate(np.r_[100000.,m.equity.to_numpy()])[1:]
        assert np.isclose(((m.equity.to_numpy()/peak-1)*100).min(),r.max_drawdown_pct,atol=1e-8)
    baseline=pd.read_csv(OUT.parent/'sp500-risk-filters-20261027/comparisons.csv')
    for r in results[results.scenario=='FROZEN_BASELINE_REPLAY'].itertuples():
        old=baseline[(baseline.period=='inspected_evaluation')&(baseline.policy==r.policy)&(baseline.fee==r.fee)].iloc[0]
        assert np.isclose(r.total_return_pct,old.total_return_pct,atol=1e-9)
    training=pd.read_csv(OUT/'unseen_training_audit.csv')
    assert (training.fit_max_maturity<training.cal_start).all() and (training.cal_max_maturity<training.cutoff).all()
    feasible=json.loads((OUT/'unseen_feasibility.json').read_text())
    decisions=pd.read_csv(OUT/'integration_decisions.csv')
    unresolved=decisions[~decisions.status.str.startswith('APPROVED')].copy()
    unresolved['next_action']='Resolve corporate identity/share conversion or obtain valid same-security history; do not substitute successor prices.'
    unresolved.to_csv(OUT/'unresolved_extension.csv',index=False)
    verification=dict(status='PASS',raw_files_hashed=saved,original_price_rows_unchanged=parent_count,added_price_rows=len(lineage),
                      accounts_reconciled=len(results),completed_accounts=len(results),membership_status='COMMUNITY_RECONSTRUCTION_NOT_OFFICIAL_PIT')
    (OUT/'final_verification.json').write_text(json.dumps(verification,indent=2),encoding='utf-8')
    sources={r.get('name'):r for r in acquired if r.get('name')}
    labels={'FROZEN_BASELINE_REPLAY':'기존 기준 사례 재현','INTERRUPTED_VOLATILITY_NOISE4_REPLAY':'중단된 변동성 교란 seed4 재실행',
            'NEWLY_INSPECTED_RETROSPECTIVE_WINDOW':'처음 평가한 짧은 과거 구간'}
    strategies={'VOL75':'변동성 단독','TREND50_VOL75':'두 조건 결합'}
    lines=['# 무료 데이터 추가 확보 및 고정 전략 재검증','',
           '## 결론',
           '- CAG의 2026-07-01 이후 누락 일봉 81건을 추가해 중단됐던 두 계좌를 끝까지 계산했습니다. 기존 예측과 교란 seed를 그대로 사용했으며 해당 거래를 삭제하거나 임의 가격으로 대체하지 않았습니다.',
           '- 새 일봉 총 2,952건을 반영했습니다. 기존 원본 가격은 모두 보존했습니다. 현재 공개 명단과 공식 변경 사건을 대조해 연구용 구성종목 범위를 확장했습니다.',
           '- 처음 평가한 2026-09-17~09-25 판단 / 10-23 청산 구간의 두 조건 결합은 편도 10bp에서 현금 포함 +2.17%, MDD -3.18%입니다. 판단일 7일·거래 14건이므로 안정성 판정 자료로는 부족합니다.',
           '- 이것은 새로 평가한 과거 구간이며, 전략 고정 시각(2026-10-27) 이후의 전향적 실험이 아닙니다. 미래 미사용 구간의 안정성은 아직 검증되지 않았습니다. 실거래·운영 DB·배포는 변경하지 않았습니다.',
           '', '## 확보와 반영',
           f'- 공개 요청 {len(acquired)}건 중 원본 {saved}건 저장 및 SHA256 확인. 약 510개 증권의 Yahoo 응답, 현재/과거 Wikipedia 명단, S&P 공식 변경 공지, SEC 티커 변경 공시, FRED 지수 CSV를 확보했습니다.',
           '- CAG는 기존 1,272개 일봉과 새 quote OHLC가 전부 일치했습니다. 거래량 수정 1건은 발견했지만 원본을 덮어쓰지 않았습니다. 이번 지표는 거래량 수치가 아니라 종가 수익률 변동성을 사용하며 거래량은 양수 유효성 검사에만 사용합니다.',
           '- 가격 통합은 기존 종목의 유효 OHLC 겹침 20개 이상·가격 불일치 0건을 요구했습니다. 새 종목은 발행사명을 확인한 단일 공급자 이력을 사용했습니다. adjclose로 OHLC를 다시 변환하지 않았습니다.',
           '- 신규 단일 공급자 이력: BE, P, TWLO 각각 122개 일봉. 다른 종목은 기존 가격 기준과 겹침 검사를 거쳐 누락분만 추가했습니다. 원본 가격은 10월 26일까지 받았지만, FRED 지수는 23일까지라 검증용 공통 범위는 23일까지입니다.',
           '- 수집한 최신 quote가 공급자 조정가격이라는 기존 한계는 유지됩니다. 이는 분배금·합병 정산·배당 재투자를 포함한 총수익률 또는 당시 실시간 이용 가능 데이터 인증이 아닙니다.',
           '', '## 구성종목 근거',
           f"- [S&P 9월 변경 공지]({sources['sp-september']['url']}): 9월 21일 BE·P·ILMN 편입, TAP·TTD·BLDR 편출.",
           f"- [S&P 10월 변경 공지]({sources['sp-october']['url']}): 10월 1일 VYLR 추가, 10월 6일 CTVA·WBD 편출 및 TWLO 편입.",
           f"- [SEC 티커 변경 공시]({sources['skydance-sec']['url']}): PSKY→SKYD 경계를 10월 6일로 기록. 가격 이력은 합병/권리 분배가 미확정이라 자동 연결하지 않았습니다.",
           '- 기존 8월 18일 명단에 위 사건을 적용한 결과가 이번에 확보한 현재 공개 503개 명단과 일치합니다. 10월 1~5일은 분사에 따른 일시적 504개 구간도 보존했습니다.',
           '- 공식 공지는 개별 사건의 근거이며 전체 명단의 완전성을 인증하지 않습니다. Wikipedia 명단·변경 이력을 함께 사용한 COMMUNITY_RECONSTRUCTION 연구 데이터이고 official_pit_verified=false를 유지했습니다. 현재 명단만 과거에 일괄 적용하지 않았습니다.',
           '', '## 고정 조건과 기업행사',
           '- 기존 확률 상위 20% → 변동성 75분위 이하 → 지수 50일선 위. 신규 진입만 필터링, 탈락분을 순위 밖 종목으로 채우지 않음.',
           '- 익절 +15%, 손절 -5%, 진입일 포함 최대 20거래일, 다음 날 시가, 초기 $100,000·현금 포함 NAV, 최대 10종목·하루 신규 2개·종목당 전일 NAV 10%, 정수 주식 유지. 편도 10/20bp, 현금 이자·배당·세금·환율 제외.',
           '- 기존 기업행사 제외 목록은 유지했습니다. 새로 확인한 CTVA 분사/WBD 종료 경계에는 품질 격리를 추가했습니다. VYLR·SKYD·VMRK의 미확정 선행기업 이력을 가격으로 연결하지 않았습니다. 필요한 보유 가격이 격리/누락이면 계좌는 중단하는 원칙을 유지했습니다.',
           '', '## 재실행 성과',
           '|사례|전략|편도 비용|현금 포함 수익률|최대낙폭|거래|','|---|---|---:|---:|---:|---:|']
    for r in results.itertuples():
        lines.append(f'|{labels[r.scenario]}|{strategies[r.policy]}|{r.fee*100:.1f}%|{r.total_return_pct:.2f}%|{r.max_drawdown_pct:.2f}%|{r.trades}|')
    lines += ['', '- 기존 기준 재현과 중단 사례는 2025-01-02~2026-08-18 판단, 2026-09-16 청산 종료입니다. 중단 사례는 변동성에 약 1% 상대 오차를 넣은 고정 seed4 실험이지 원래 전략 기준 수익률이 아닙니다.',
              '- 기존 기준은 +28.35%/-17.18%로 정확히 재현됐습니다. CAG로 중단됐던 결합 교란 사례는 +20.67%/-17.45%로 완료됐습니다. 이 두 결과를 혼동하면 안 됩니다.',
              '', '## 새 구간 검증의 한계',
              '- 신규 판단일은 9월 17·18·21·22·23·24·25일입니다. 마지막 진입도 최대 20거래일 뒤 10월 23일까지 청산 가능하도록 고정했습니다. 종료일 이후 결과가 부족한 거래를 성과에 따라 선택하지 않았습니다.',
              '- 9월 모델은 9월 시작 이전에 성숙한 학습/보정 라벨만 사용했습니다. 새 구성종목에는 실제 존재하는 과거 가격으로만 63거래일 지표 준비 기간을 계산했습니다.',
              '- 판단일 7일, 거래 14건은 장기 안정성·최적성·통계적 신뢰구간의 근거가 아닙니다. 사후 재구성된 명단과 기업행사 격리도 있어 엄격한 과거 시점 검증과 구분해야 합니다.',
              '- 전향적 기준 고정 시각은 prospective_gate.json의 2026-10-27입니다. 이번에 확보한 모든 종가는 그 이전이므로 고정 후 미사용 기간 검증은 아직 시작되지 않았습니다.',
              '', '## 남은 데이터',
              '- PSKY/SKYD, VMRK, VYLR: 합병·분사·권리 분배와 선행기업 가격 기준 확인. WBD: 종료 전 일봉과 현금/주식 교환 및 결제 근거. unresolved_extension.csv에 분리했습니다.',
              '- 10월 23일 현재 명단의 미확보 가격은 SKYD·VMRK·VYLR 3개이며 숨기지 않고 분모에 포함했습니다. 기존 장기 상장폐지 종목 누락도 여전히 남아 있습니다.',
              '- 전체 과거 공식 명단과 완전한 변경 체인, 공식 거래소 캘린더, 10월 26일 이후 FRED 지수 및 최신 일봉, 기업행사 정산 근거가 더 필요합니다. 전체 누락은 dataset/missing_symbols.csv, 범위별 상태는 dataset/daily_coverage.csv를 참조합니다.',
              '- Paramount 발행사 페이지 직접 수집은 403으로 중단했고 우회하지 않았습니다. SEC 공개 공시를 사용했습니다. 유료 서비스·로그인·계정 생성·API 키 사용은 없었습니다.',
              '', '## 검증과 재현',
              f'- 데이터셋 681,051행 독립 검증 PASS. 기존 원본 {parent_count:,}행 불변, 추가 {len(lineage):,}행 출처 보존. 재실행 8계좌의 NAV·거래 손익·MDD 대사 PASS.',
              '- Python 관련 테스트 32개 통과. 새 원본에서 드러난 Windows의 1970년 이전 상장일 변환과 캘린더 마지막 날 검사 오류를 회귀 테스트와 함께 수정했습니다.',
              '- 재실행: python scripts/validate_sp500_extension.py → python scripts/report_sp500_extension.py. 원본 수집 및 데이터 빌드는 이미 생성된 버전을 덮어쓰지 않습니다.',
              '- acquisition.json/price_acquisition.json/supplement_acquisition.json: URL·수집 시각·원본 해시. integration_decisions.csv: 종목별 승인/보류. revalidation.csv: 결과. unseen_feasibility.json: 검증 범위 판정.']
    (OUT/'REPORT.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps(verification,indent=2))


if __name__=='__main__':main()
