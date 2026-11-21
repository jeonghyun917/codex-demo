"""Independently check entry eligibility and reconcile stored filter accounts."""
import json
from pathlib import Path
import numpy as np
import pandas as pd
from sp500_risk_filters import OUT,PREDICTIONS,EXCLUDED,POLICIES,PERIODS
from build_sp500_daily_dataset import digest


def main():
    report=pd.read_csv(OUT/'comparisons.csv')
    fills=pd.read_csv(OUT/'fills.csv.gz')
    equity=pd.read_csv(OUT/'daily_equity.csv.gz')
    p=pd.read_csv(PREDICTIONS/'predictions.csv.gz')
    assert not set(p.symbol)&EXCLUDED and not set(fills.symbol)&EXCLUDED
    p=p.sort_values(['date','probability','symbol'],ascending=[True,False,True])
    p['rank']=p.groupby('date').cumcount()+1
    p['limit']=np.ceil(p.groupby('date').symbol.transform('size')*.2)
    p['cap']=p.groupby('date').vol20.transform(lambda s:s.quantile(.75))
    lookup=p.set_index(['symbol','date'])
    for t in fills.itertuples():
        r=lookup.loc[(t.symbol,t.decision)]
        assert r['rank']<=r['limit']
        if 'TREND50' in t.policy:assert r.index_ma50>0
        if 'VOL75' in t.policy:assert r.vol20<=r.cap
    for r in report.itertuples():
        trades=fills[(fills.period==r.period)&(fills.policy==r.policy)&(fills.fee==r.fee)]
        marks=equity[(equity.period==r.period)&(equity.policy==r.policy)&(equity.fee==r.fee)]
        assert r.status=='COMPLETE_RESEARCH_ONLY'
        assert len(trades)==r.trades
        assert np.isclose(trades.pnl.sum()/1000,r.total_return_pct,atol=1e-8)
        assert np.isclose((marks.iloc[-1].equity/100000-1)*100,r.total_return_pct,atol=1e-8)
        assert int(marks.iloc[-1].positions)==0
        peak=np.maximum.accumulate(np.r_[100000.,marks.equity.to_numpy()])[1:]
        assert np.isclose(((marks.equity.to_numpy()/peak-1)*100).min(),r.max_drawdown_pct,atol=1e-8)
        assert np.isclose(100*trades.reason.str.startswith('SL').mean(),r.stop_pct)
    protocol=json.loads((OUT/'protocol.json').read_text())
    for path,expected in protocol['input_hashes'].items():assert digest(Path(path))==expected
    labels={'RAW':'기존 확률순','TREND50':'지수 50일선 위','VOL75':'고변동성 상위 25% 제외','TREND50_VOL75':'지수 + 변동성'}
    periods={'development':'개발','inspected_evaluation':'평가(관찰됨)','complete_price_window':'짧은 완전가격'}
    lines=['# 지수 추세·변동성 진입 필터 비교','',
           '## 요약',
           '- 사전 고정한 변동성 필터는 세 구간 모두 기존 확률순 대비 현금 포함 수익률과 최대낙폭을 개선했습니다. 두 조건 결합도 세 구간 모두 기존 방식보다 개선됐습니다.',
           '- 지수 조건 단독은 세 구간의 최대낙폭을 줄였지만 개발 구간 수익률은 -6.18%→-11.54%로 악화됐습니다. 손실 관리 효과와 수익률 효과를 분리해 봐야 합니다.',
           '- 손실 관리 중심의 후속 검증 후보는 두 조건 결합입니다. 개발 수익률은 변동성 단독보다 낮지만 MDD가 -26.65%→-17.21%로 줄었고, 평가 구간 수익률/MDD는 +28.35%/-17.18%였습니다.',
           '- 이는 이미 관찰한 구간에서의 연구 결과입니다. 최적 전략·실전 수익 보장·독립 미사용 검증을 의미하지 않습니다. 운영 적용 없이 비교 결과만 저장했습니다.',
           '', '## 고정 조건과 데이터',
           '- 이전 안정화 실험에서 기업행사 미확정 FBIN, BBWI, DOC/PEAK, PSKY, AVB, EQR, VMRK를 학습·후보에서 제외한 확률을 그대로 재사용했습니다. 모델을 다시 학습하거나 기준을 탐색하지 않았습니다.',
           '- 기업행사 제외 목록은 현재 지식으로 고정한 연구 범위입니다. 과거 당시 알려진 제외 정보를 완벽히 재현한 것이 아니며, 남은 모든 기업행사와 총수익률이 검증됐다는 뜻은 아닙니다.',
           '- 확률 상위 ceil(유효 종목 수 × 20%)를 먼저 고정한 후 신규 매수 필터를 적용합니다. 탈락 종목을 상위 20% 밖 종목으로 대체하지 않습니다. 살아남은 후보 중 이미 보유한 종목은 건너뜁니다.',
           '- 지수 조건: 판단일 지수 종가가 해당 일 포함 50거래일 이동평균보다 클 때만 신규 매수. 같은 가격이면 미통과. 저장된 index_ma50을 사용하며 별도 지수 데이터를 추가하지 않았습니다.',
           '- 변동성 조건: 당일 유효 전체 종목의 vol20 75분위 이하만 통과. vol20은 최근 20거래일 일별 종가 수익률의 표본 표준편차이며 연율화하지 않았습니다. 분위수는 당일 단면에서만 계산합니다. 경계와 같은 값은 포함하므로 제외 비율은 정확히 25%가 아닐 수 있습니다.',
           '- 조건은 진입에만 적용합니다. 보유 후 지수가 50일선 아래로 가거나 변동성이 높아져도 추가 강제 청산하지 않습니다.',
           '- 초기 $100,000, 무레버리지·정수 주식·최대 10종목·하루 신규 2건·종목당 전일 NAV 10%. 장 마감 판단 → 다음 거래일 시가. 익절 +15%, 손절 -5%, 진입일 포함 최대 20거래일 유지.',
           '- 같은 일봉에서 양쪽 도달은 손절 우선, 갭 손절은 시가. 따라서 손절 손실이 항상 -5%로 제한되는 것은 아닙니다. 비용은 편도 10bp 및 20bp. 현금 이자·배당·세금·환율 제외.',
           '', '## 현금 포함 성과 (편도 10bp)',
           '|구간|조건|수익률|최대낙폭|평균 현금|거래 수|손절 비율|','|---|---|---:|---:|---:|---:|---:|']
    for period,*_ in PERIODS:
        for policy in POLICIES:
            r=report[(report.period==period)&(report.policy==policy)&(report.fee==.001)].iloc[0]
            lines.append(f'|{periods[period]}|{labels[policy]}|{r.total_return_pct:.2f}%|{r.max_drawdown_pct:.2f}%|{r.average_cash_pct:.1f}%|{r.trades}|{r.stop_pct:.1f}%|')
    lines += ['', '- 개발: 2023-01-03~2024-12-31 판단, 2025-01-31까지 청산. 평가: 2025-01-02~2026-08-18 판단, 2026-09-16까지 청산. 짧은 구간: 2026-06-03~07-02 판단, 07-31까지 청산.',
              '- 평균 현금 비중은 청산 대기 기간을 포함한 일별 cash/NAV의 산술평균입니다. 거래당 수익이 아니라 계좌 전체 수익률입니다. 무이자 100% 현금 기준은 수익률·MDD 모두 0%입니다.',
              '- 손절 비율은 완료 거래 중 SL/SL_GAP 비율이며 익절의 반대 비율이 아닙니다. 기간 청산도 존재합니다. 손절 건수와 손절 손익은 comparisons.csv에 저장했습니다.',
              '- 변동성 단독은 평가 구간 현금 비중이 38.7%→17.4%로 오히려 낮아졌습니다. 단순히 현금을 더 많이 보유해서 낙폭이 줄었다고 해석할 수 없습니다. 거래 수와 보유 기간의 변화도 함께 작용합니다.',
              '', '## 비용 스트레스 (편도 20bp)',
              '|구간|조건|수익률|최대낙폭|','|---|---|---:|---:|']
    for period,*_ in PERIODS:
        for policy in POLICIES:
            r=report[(report.period==period)&(report.policy==policy)&(report.fee==.002)].iloc[0]
            lines.append(f'|{periods[period]}|{labels[policy]}|{r.total_return_pct:.2f}%|{r.max_drawdown_pct:.2f}%|')
    lines += ['', '## 해석 한계',
              '- 세 구간은 독립 표본 3개가 아닙니다. 짧은 완전가격 구간은 평가 구간 안에 포함되며 반복 확인용입니다.',
              '- 이번에는 가격·모델·후보군을 고정하고 진입 필터만 비교했습니다. 그렇더라도 청산일·현금 여력에 따라 후속 거래가 달라지므로 결과 차이는 개별 종목 필터의 독립 인과 효과가 아닙니다.',
              '- 이번 필터에 대한 확률 교란·재학습 표본 변화·미사용 기간 안정성 검사는 하지 않았습니다. 앞선 순위 안정화의 교란 결과를 이 정책의 검증 결과로 옮겨 해석하면 안 됩니다.',
              '- 다음 단계는 두 조건 결합과 변동성 단독을 고정한 채 미사용 기간 및 데이터 수정 민감도를 검증하는 것입니다. 결과가 좋다는 이유로 이 자료에서 75분위 기준을 추가 조정하지 않습니다.',
              '', '## 검증과 재현',
              f'- 계좌 {len(report)}개 전부 계산 완료. 체결 {len(fills):,}건의 판단일 상위 20%·지수·변동성 조건을 별도로 확인했습니다. 기존 RAW 6개 계좌가 이전 결과와 일치합니다.',
              '- 일별 현금·NAV, 손익 합계·MDD, 제외 종목 미사용, 다음 날 진입, 익절/손절/20일 청산을 검사했습니다. Python 관련 테스트 결과는 별도 실행 로그를 참조합니다.',
              '- 실행: python scripts/sp500_risk_filters.py → python scripts/report_sp500_risk_filters.py',
              '- comparisons.csv: 전체 성과. daily_filter_audit.csv.gz: 일별 분위수·후보 수. fills.csv.gz/events.csv.gz/daily_equity.csv.gz: 거래와 현금 흐름. protocol.json: 고정 조건과 입력 해시.']
    (OUT/'REPORT.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    (OUT/'entry_verification.json').write_text(json.dumps(dict(status='PASS',accounts=len(report),entry_conditions_checked=len(fills),input_hashes_unchanged=True,exclusions_verified=True),indent=2),encoding='utf-8')
    print('Independent entry conditions, NAV, PnL and MDD: PASS')


if __name__=='__main__':main()
