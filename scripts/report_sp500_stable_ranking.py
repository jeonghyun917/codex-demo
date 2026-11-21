"""Independent summary reconciliation and Korean comparison report."""
import json
import numpy as np
import pandas as pd
from sp500_stable_ranking import OUT,BASE,POLICIES,PERIODS,EXCLUDED
from build_sp500_daily_dataset import digest


def main():
    results=pd.read_csv(OUT/'comparisons.csv')
    noise=pd.read_csv(OUT/'noise_runs.csv')
    stability=pd.read_csv(OUT/'stability.csv')
    fills=pd.read_csv(OUT/'fills.csv.gz')
    equity=pd.read_csv(OUT/'daily_equity.csv.gz')
    predictions=pd.read_csv(OUT/'predictions.csv.gz',usecols=['symbol','date'])
    assert not (set(predictions.symbol)|set(fills.symbol))&EXCLUDED
    audit=pd.read_csv(OUT/'training_audit.csv')
    assert (audit.fit_max_maturity<audit.cal_start).all() and (audit.cal_max_maturity<audit.cutoff).all()
    protocol=json.loads((OUT/'protocol.json').read_text())
    for path,expected in protocol['input_hashes'].items():assert digest(__import__('pathlib').Path(path))==expected
    for r in results.itertuples():
        marks=equity[(equity.period==r.period)&(equity.policy==r.policy)&(equity.fee==r.fee)]
        trade=fills[(fills.period==r.period)&(fills.policy==r.policy)&(fills.fee==r.fee)]
        assert len(trade)==r.trades
        if r.status=='COMPLETE_RESEARCH_ONLY':
            assert np.isclose(trade.pnl.sum()/1000,r.total_return_pct,atol=1e-8)
            assert np.isclose((marks.iloc[-1].equity/100000-1)*100,r.total_return_pct,atol=1e-8)
            assert int(marks.iloc[-1].positions)==0
        peak=np.maximum.accumulate(np.r_[100000.,marks.equity.to_numpy()])[1:]
        assert np.isclose(np.min((marks.equity.to_numpy()/peak-1)*100),r.max_drawdown_pct,atol=1e-8)
    labels={'RAW':'기존 확률순','EMA5':'5일 EMA','BAND05':'0.5%p 동급·전일 순서','EMA5_BAND05':'EMA + 동급 유지'}
    periods={'development':'개발 구간','inspected_evaluation':'관찰된 평가 구간','complete_price_window':'짧은 완전가격 구간'}
    lines=['# 매수 순위 안정화 비교','',
           '## 결론',
           '- 이번 사전 고정 세 개선안 중 수익률·최대낙폭·교란 안정성을 전 구간에서 함께 개선한 정책은 없었습니다. 운영 방식의 전면 교체는 보류합니다.',
           '- EMA+동급 유지는 개발 구간의 낙폭과 큰 교란 민감도를 줄였지만, 수익률은 -6.18%에서 -8.87%로 낮아졌습니다. 평가 구간도 +18.74%에서 +16.46%로 낮아졌고 MDD는 -26.97%에서 -27.19%로 악화됐습니다.',
           '- 0.1%p 교란에서 EMA+동급 유지의 개발 수익률 SD는 4.82%p→0.86%p, 평균 거래 교체율은 9.48%→4.15%로 개선됐습니다. 그러나 평가 구간 SD는 1.72%p→2.42%p, 거래 교체율은 1.73%→3.05%로 악화됐습니다.',
           '- 단순 동급 순서 유지도 묶음 경계와 과거 순서의 영향 때문에 전면적인 안정성 해법이 아니었습니다. EMA 단독은 일부 안정성을 높였지만 수익률 손실이 컸습니다.',
           '- 추가 실험 후보로는 EMA+동급 유지를 남길 수 있으나, 이미 관찰한 이 구간에서 파라미터를 더 맞추지 말고 미사용 기간에서 확인해야 합니다. 현재 RAW도 수익성·위험이 충분히 검증된 운영 전략은 아닙니다.',
           '',
           '## 비교 범위',
           '- 전략을 운영에 적용하지 않았습니다. 이미 관찰한 데이터를 이용한 고정 규칙 비교이며 최적 전략 탐색이나 미사용 최종 검증이 아닙니다.',
           '- 현재 확인된 기업행사 미확정 종목 FBIN, BBWI, DOC/PEAK, PSKY, AVB, EQR, VMRK를 전 기간 학습과 후보에서 제외했습니다. 기존 원본은 보존했습니다.',
           '- FBIN 분사 라벨 4개만 제외한 이전 실험보다 제외 범위가 넓습니다. 아래 RAW를 공정한 새 기준선으로 사용해야 하며 이전 -5.29% 또는 -2.46%와 전략 효과를 직접 비교하면 안 됩니다.',
           '- 이 목록은 현재 지식으로 고정한 보수적 연구용 범위입니다. 과거 당시 이용 가능한 기업행사 정보를 완벽히 재현한 것이 아니며, 남은 기업의 모든 기업행사가 인증됐다는 뜻도 아닙니다.',
           '', '## 사전 고정 규칙',
           '- RAW: 당일 확률 내림차순.',
           '- EMA5: 확률을 alpha=1/3 지수이동평균으로 평활화. 5거래일 단순평균이 아니며 최신 관측에 1/3 가중치. 종목 관측이 끊기면 초기화.',
           '- BAND05: 높은 확률부터 각 묶음의 최고점 대비 0.5%p 이내를 동급 처리하고, 묶음 안에서 전일 전체 순서를 유지. 전일 없는 종목은 뒤에 두고 티커로 결정. 묶음 간 확률 우선순위는 유지.',
           '- EMA5_BAND05: 위 두 규칙 결합. 고정 확률 눈금 반올림 방식이 아니라 최고점 기준 묶음이며, 묶음 경계에서도 민감성은 남을 수 있습니다.',
           '- 매일 후보 수는 동일하게 상위 ceil(20% × 유효 종목 수). 이미 보유한 종목은 건너뜁니다. 순위 하락만으로 기존 보유를 매도하지 않습니다.',
           '- 초기 $100,000, 현금 포함 NAV, 종목당 전일 NAV 10%, 최대 10종목·하루 신규 2종목, 정수 주식, 다음 거래일 시가 진입.',
           '- 익절 +15%, 손절 -5%, 진입일 포함 최대 20거래일 유지. 갭 손절은 시가, 동일 일봉 양쪽 도달은 손절 우선. 편도 10bp와 20bp 비교. 현금 이자·배당·세금·환율 제외.',
           '- 월별 확장 학습과 주간 표본, 라벨 성숙 분리, 기존 보정 창 유지. 각 정책은 동일하게 재학습한 확률을 사용합니다.',
           '', '## 현금 포함 성과 (편도 10bp)',
           '|구간|정책|수익률|MDD|평균 현금|거래 수|상태|','|---|---|---:|---:|---:|---:|---|']
    fmt=lambda x:'미완료' if pd.isna(x) else f'{x:.2f}%'
    for r in results[results.fee==.001].sort_values(['period','policy']).itertuples():
        lines.append(f'|{periods[r.period]}|{labels[r.policy]}|{fmt(r.total_return_pct)}|{fmt(r.max_drawdown_pct)}|{r.average_cash_pct:.1f}%|{r.trades}|{r.status}|')
    lines += ['', '- 무이자 현금 100% 기준은 모든 기간 수익률 0%, MDD 0%입니다. 미완료 계좌가 있으면 해당 MDD는 중단 전 구간으로 전체 성과가 아닙니다.',
              '- 개발: 2023-01-03~2024-12-31 판단 / 2025-01-31까지 청산. 평가: 2025-01-02~2026-08-18 판단 / 2026-09-16까지 청산. 짧은 구간: 2026-06-03~07-02 판단 / 07-31까지 청산.',
              '', '## 비용 스트레스 (편도 20bp)',
              '|구간|정책|수익률|MDD|','|---|---|---:|---:|']
    for r in results[results.fee==.002].sort_values(['period','policy']).itertuples():
        lines.append(f'|{periods[r.period]}|{labels[r.policy]}|{fmt(r.total_return_pct)}|{fmt(r.max_drawdown_pct)}|')
    lines += ['', '## 예측 교란 안정성',
              '- 원시 확률에 평균 0 정규 잡음을 넣은 뒤 전체 순위를 다시 계산합니다. 표준편차 0.01%p와 0.1%p, 각각 seed 0~4를 모든 정책에 공통 사용했습니다. 각 시나리오는 이전 날부터 순서와 EMA 상태를 다시 계산합니다.',
              '- 거래 교체율 = 1 - (기준/교란 공통 종목·진입일 거래 수 ÷ 합집합 거래 수). 수량 변화는 이 지표에 포함하지 않습니다. 0%면 동일 거래 집합입니다.',
              '- 범위·표준편차는 각 5회 실험의 표본 요약이며 신뢰구간이나 미래 수익률 예측이 아닙니다. 실험 규모를 이유로 안정성을 과장하면 안 됩니다.',
              '|구간|정책|잡음 SD|수익률 범위|수익률 SD|평균 거래 교체율|최악 MDD|','|---|---|---:|---|---:|---:|---:|']
    for r in stability.itertuples():
        lines.append(f'|{periods[r.period]}|{labels[r.policy]}|{r.noise_sd*100:g}%p|{r.return_min:.2f}~{r.return_max:.2f}%|{r.return_sd:.2f}%p|{r.mean_trade_change*100:.2f}%|{r.worst_mdd:.2f}%|')
    lines += ['', '## 해석 범위',
              '- 안정성 개선과 수익률 개선은 별개입니다. 한 구간에서 수익률이 높더라도 다른 구간의 낙폭·잡음 민감도·비용 스트레스가 악화되면 전면 개선으로 판단하지 않습니다.',
              '- EMA는 잡음을 줄이는 대신 대응이 늦어지고, 동급 순서 유지는 미세 순위 변경을 줄이는 대신 이전 순서를 오래 유지할 수 있습니다. 매매 빈도와 현금 비중도 함께 비교해야 합니다.',
              '- 고정한 0.5%p/EMA5는 최적화된 값이 아닙니다. 이 결과를 보고 수치를 재탐색하면 같은 자료에 맞추는 위험이 커집니다.',
              '- 기존 20D/signal의 과거 시점 기록은 없어 비교하지 않았습니다. 누락 종목·배당/기업행사·구성종목 재구성 문제로 전체 S&P500의 실전 성과로 일반화할 수 없습니다.',
              '', '## 검증 및 재현',
              f"- 기본 계좌 {len(results)}개, 교란 계좌 {len(noise)}개. 학습 창 {len(audit)}개에 성숙 시점 검증. 제외 종목 예측·체결 0건.",
              '- Python 관련 테스트 22개 통과. 과거만 사용하는 평활화, 관측 공백 초기화, 동급 순서 유지, 큰 확률 차이 우선, 미래 데이터 추가 불변성, 제외 종목 처리도 검사했습니다.',
              '- 일별 현금/NAV와 거래 손익 합계, 최대낙폭, 다음 날 진입, 20일 제한, 익절/손절 우선순위를 검사했습니다. PASS는 계산·인과 순서 검사이며 전략 수익성 인증이 아닙니다.',
              '- 실행: python scripts/sp500_stable_ranking.py → python scripts/report_sp500_stable_ranking.py',
              '- 전체 결과: comparisons.csv, stability.csv, noise_runs.csv, fills.csv.gz, daily_equity.csv.gz. 제외 표본: excluded_samples.csv. 고정 설정·해시: protocol.json.']
    (OUT/'REPORT.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    (OUT/'report_verification.json').write_text(json.dumps(dict(status='PASS',base_accounts=len(results),noise_accounts=len(noise),all_base_complete=bool((results.status=='COMPLETE_RESEARCH_ONLY').all()),all_noise_complete=bool((noise.status=='COMPLETE_RESEARCH_ONLY').all()),input_hashes_unchanged=True),indent=2),encoding='utf-8')
    print(results[results.fee==.001][['period','policy','total_return_pct','max_drawdown_pct','trades']].to_string(index=False))
    print(stability[stability.noise_sd==.001].to_string(index=False))


if __name__=='__main__':main()
