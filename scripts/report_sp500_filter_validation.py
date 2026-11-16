"""Reconcile completed sensitivity cases without inventing out-of-sample evidence."""
import json
from datetime import datetime,timezone
from pathlib import Path
import numpy as np
import pandas as pd
from sp500_filter_validation import OUT,POLICIES
from build_sp500_daily_dataset import digest


def main():
    result=pd.read_csv(OUT/'comparisons.csv')
    fills=pd.read_csv(OUT/'fills.csv.gz')
    marks=pd.read_csv(OUT/'daily_equity.csv.gz')
    protocol=json.loads((OUT/'protocol.json').read_text())
    holdout=json.loads((OUT/'holdout_audit.json').read_text())
    training=pd.read_csv(OUT/'training_audit.csv')
    assert (training.fit_max_maturity<training.cal_start).all()
    assert (training.cal_max_maturity<training.cutoff).all()
    for p,h in protocol['input_hashes'].items():assert digest(Path(p))==h
    keys=['scenario','period','policy','fee']
    fg={key:group for key,group in fills.groupby(keys)}
    mg={key:group for key,group in marks.groupby(keys)}
    count=0
    for r in result.itertuples():
        key=tuple(getattr(r,k) for k in keys)
        f=fg.get(key,pd.DataFrame(columns=['pnl']))
        m=mg[key]
        assert len(f)==r.trades
        if r.status=='COMPLETE_RESEARCH_ONLY':
            assert np.isclose(f.pnl.sum()/1000,r.total_return_pct,atol=1e-8)
            assert np.isclose((m.iloc[-1].equity/100000-1)*100,r.total_return_pct,atol=1e-8)
            assert int(m.iloc[-1].positions)==0
            count+=1
        else:assert pd.isna(r.total_return_pct)
        peak=np.maximum.accumulate(np.r_[100000.,m.equity.to_numpy()])[1:]
        assert np.isclose(((m.equity.to_numpy()/peak-1)*100).min(),r.max_drawdown_pct,atol=1e-8)
    selected=result[result.fee==.001]
    completed_metrics=selected.copy()
    completed_metrics.loc[completed_metrics.status!='COMPLETE_RESEARCH_ONLY',['max_drawdown_pct','trade_change']]=np.nan
    summary=completed_metrics.groupby(['kind','period','policy']).agg(runs=('scenario','size'),completed=('total_return_pct','count'),
                  return_min=('total_return_pct','min'),return_max=('total_return_pct','max'),return_sd=('total_return_pct','std'),
                  worst_mdd=('max_drawdown_pct','min'),best_mdd=('max_drawdown_pct','max'),mean_trade_change=('trade_change','mean'))
    summary.to_csv(OUT/'sensitivity_summary.csv')
    labels={'VOL75':'변동성 단독','TREND50_VOL75':'두 조건 결합'}
    periods={'development':'개발','inspected_evaluation':'관찰된 평가','complete_price_window':'짧은 완전가격'}
    kinds={'training_drop1':'학습 표본 1% 제외','probability_noise':'확률 SD 0.01%p','volatility_noise':'변동성 약 1% 상대 교란'}
    lines=['# 고정 진입 필터의 미사용 구간·데이터 민감도 검증','',
           '## 결론',
           '- 미사용 구간 검증은 필요한 구성종목 이력이 없어 불가합니다. 데이터 변경 민감도는 120계좌를 검사했으며 118개 완료, 2개는 실제 필요한 가격 누락으로 중단됐습니다.',
           '- 두 조건 결합은 변동성 단독보다 학습 표본 변경에 상대적으로 덜 민감했습니다. 그러나 수익률의 크기는 달라졌고, 변동성 입력 교란에는 거래 구성이 크게 달라져 안정성 인증은 보류합니다.',
           '- 과거 데이터 버전 변경 시 개발 수익률은 변동성 단독 +8.50%→+16.53%, 결합 +6.24%→+9.33%였습니다. 개발 MDD는 각각 약 -26.65%, -17.21%로 거의 같았습니다. 최근 평가의 결합 결과는 두 버전 모두 +28.35%/-17.18%로 일치했습니다.',
           '- 학습 표본 약 1% 제외 5회에서 개발 수익률은 변동성 단독 -4.30%~+13.11%, 결합 +5.09%~+13.91%였습니다. 최근 평가에서는 각각 +21.89%~+25.12%, +27.13%~+29.19%였습니다. 결합의 낙폭도 상대적으로 작았지만 이 작은 표본은 미래 보증이 아닙니다.',
           '- 변동성 약 1% 상대 교란 시 개발 수익률은 단독 -5.87%~+8.15%, 결합 +1.55%~+9.57%였습니다. 최근 평가에는 두 전략 모두 5회 중 1회 중단이 있어 완료 사례의 범위만으로 안전성을 주장할 수 없습니다.',
           '',
           '## 미사용 구간: 검증 불가',
           '- 실제 신규 기간 성과는 계산하지 않았습니다. 검증하지 못한 수익률을 0% 또는 기존 평가 수익률로 대체하지 않습니다.',
           f"- 구성종목 근거 종료: {holdout['membership_end']}. 이전 성과 확인/청산 종료: {holdout['last_used_outcome']}. 저장된 가격·캘린더 종료: {holdout['price_calendar_end']}.",
           '- 기존 성과를 확인한 구간 이후에는 당시 구성종목 근거가 없습니다. 마지막 명단 연장, 현재 구성종목 역적용, 이미 본 구간을 홀드아웃으로 재명명하는 방법은 사용하지 않았습니다.',
           '- 가격만으로 20거래일 청산 여유가 남는 신규 판단일은 2026-09-17·09-18 두 날짜뿐입니다. 구성종목을 확보하더라도 이 표본만으로 안정성을 판단하기 어렵습니다.',
           '- 로컬 대체 파일도 확인했습니다. wikipedia_index_membership_2021_2026.csv는 2026-06-26까지이고 별도 10월 근거 문서는 Nasdaq-100의 1~3월 구간 확인으로 이 공백을 해결하지 못합니다.',
           '', '## 고정한 규칙',
           '- 기존 확률 상위 20%에 변동성 75분위 이하 조건, 또는 그 조건과 지수 50일선 위 조건을 결합. 신규 진입에만 적용하며 순위 밖 보충 매수는 없습니다.',
           '- 기업행사 미확정 FBIN, BBWI, DOC/PEAK, PSKY, AVB, EQR, VMRK 제외. 이 사후 고정 연구용 제외 범위는 공식 PIT 보증이 아닙니다.',
           '- 익절 +15%, 손절 -5%, 진입일 포함 최대 20거래일, 다음 거래일 시가, 현금 포함 $100,000, 최대 10종목·하루 2건·종목당 전일 NAV 10% 유지. 갭 손절은 시가, 양쪽 도달 시 손절 우선.',
           '- 임계값은 바꾸지 않았습니다. 월별 모델 재학습은 기존 절차·보정 창·라벨 성숙 분리를 그대로 사용했습니다. 현금 이자·배당·세금·환율 제외, 편도 10bp 기준이며 실제 버전 비교에는 20bp도 포함했습니다.',
           '', '## 실제 데이터 버전 비교 (편도 10bp)',
           '|구간|조건|보완 전 데이터 수익률 / MDD|보완 후 수익률 / MDD|','|---|---|---|---|']
    fmt=lambda x:'검증 중단' if pd.isna(x) else f'{x:.2f}%'
    for period in periods:
        for policy in POLICIES:
            a=selected[(selected.period==period)&(selected.policy==policy)&(selected.scenario=='OLD_SNAPSHOT')].iloc[0]
            b=selected[(selected.period==period)&(selected.policy==policy)&(selected.scenario=='CURRENT')].iloc[0]
            lines.append(f'|{periods[period]}|{labels[policy]}|{fmt(a.total_return_pct)} / {fmt(a.max_drawdown_pct)}|{fmt(b.total_return_pct)} / {fmt(b.max_drawdown_pct)}|')
    lines += ['', '- 보완 전/후 모두 동일 기업행사 제외와 동일 매매 규칙입니다. OLD_TRAIN_NEW_FEATURES도 별도 실행해 구형 학습 데이터와 최신 지표/가격을 교차 비교했습니다. 상세는 comparisons.csv에 있습니다.',
              '- 이 비교는 실제 저장된 데이터 버전 두 개 사이의 민감도이며 다른 모든 공급자 수정·분할 오류·상장폐지 정산에 대한 강건성 인증이 아닙니다.',
              '', '## 변경 시나리오별 민감도 (편도 10bp)',
              '- 학습 표본 1% 제외: 종목·날짜·seed 해시로 선택한 학습 표본을 제거하고 각 월 재학습. seed 0~4. 미래 결과에 따라 제거하지 않으며, 거래 자체를 제거하는 실험이 아닙니다.',
              '- 확률 교란: 평균 0, SD 0.01%p 정규 잡음을 기존 확률에 적용하고 [0,1]로 제한. 지표·가격은 고정.',
              '- 변동성 교란: vol20에 exp(N(0,0.01))를 곱하고 매일 75분위 경계를 다시 계산. 약 1%의 상대 오차이며 1%p가 아닙니다. 확률과 체결가격은 고정.',
              '- 후자의 두 실험은 합성 지표 민감도입니다. 일관된 OHLC 재수집·재계산이나 실제 공급자 수정 사례로 오인하면 안 됩니다.',
              '|구간|조건|변경|완료/전체|완료 계좌 수익률 범위|완료 계좌 최악 MDD|완료 계좌 평균 거래 교체율|','|---|---|---|---:|---|---:|---:|']
    for row in summary.reset_index().itertuples():
        if row.kind not in kinds:continue
        lines.append(f'|{periods[row.period]}|{labels[row.policy]}|{kinds[row.kind]}|{row.completed}/{row.runs}|{fmt(row.return_min)}~{fmt(row.return_max)}|{fmt(row.worst_mdd)}|{row.mean_trade_change*100:.2f}%|')
    lines += ['', '- 거래 교체율은 종목·진입일 거래 집합의 Jaccard 거리입니다. 동일 거래의 수량 차이는 포함하지 않습니다. 각 5회는 제한된 민감도 검사이며 신뢰구간이 아닙니다.',
              '- 중단 계좌는 전체 수익률을 비워 두고 상태를 보존했습니다. 요약 표의 수익률·MDD·거래 교체율은 완료 계좌만 집계하므로 completed/runs를 반드시 함께 봐야 합니다. 완료 사례만의 범위는 실패 가능성을 포함한 전체 안정성 범위가 아닙니다.',
              '- 중단 상세: volatility_noise_4의 관찰된 평가 구간, 두 전략 모두 2026-07-01 CAG 가격이 필요해졌지만 저장 이력이 2026-06-30에서 끊겼습니다. 해당 거래를 삭제하거나 마지막 가격으로 채우지 않았습니다. 중단 전 수익률을 전체 기간 수익률로 보고하지 않습니다.',
              '- 개발: 2023-01-03~2024-12-31 판단, 2025-01-31 청산 종료. 평가: 2025-01-02~2026-08-18 판단, 09-16 청산 종료. 짧은 구간: 2026-06-03~07-02 판단, 07-31 종료. 마지막 구간은 평가 구간에 포함되므로 독립 표본이 아닙니다.',
              '', '## 판정 범위와 다음 검증',
              '- 이번 결과만으로 미사용 기간에서도 수익률과 낙폭이 유지된다고 판정할 수 없습니다. 과거 버전·학습 표본·지표 교란에 대한 민감도와 미사용 기간 일반화는 서로 다른 질문입니다.',
              '- 신규 검증에는 2026-08-19 이후 날짜별 구성종목 근거, 최신 일봉/캘린더, 기업행사 근거 및 모든 마지막 진입의 20거래일 청산 여유가 필요합니다.',
              '- prospective_gate.json에 현재 조건을 보존했습니다. 이후 데이터로 검증할 때 기준을 다시 최적화하지 않고, 불명확한 기업행사나 보유 가격 누락은 중단/표시합니다. 자동 수집·예약 실행·실거래는 설정하지 않았습니다.',
              '', '## 검증 증거',
              f'- 총 {len(result)}계좌 중 {count}개 완료. 현금 포함 최종 NAV·체결 손익 합계·MDD를 독립 대사했습니다. 학습 시점 분리와 입력 파일 해시도 확인했습니다.',
              '- Python 관련 테스트 28개 통과. 같은 입력·해시 seed의 표본 재현, 미래 행을 추가해도 과거 표본 선택 불변, 미사용 기간의 구성종목/청산 여유 조건을 검사했습니다.',
              '- 현행 확률 재학습 재현 오차와 기본 계좌 일치는 verification.json에 기록했습니다. 결과의 계산 일치가 수익성 보증은 아닙니다.',
              '- 재현: python scripts/sp500_filter_validation.py → python scripts/report_sp500_filter_validation.py',
              '- 상세: comparisons.csv, sensitivity_summary.csv, price_revision_audit.json, sample_audit.csv, training_audit.csv, fills.csv.gz, daily_equity.csv.gz.']
    (OUT/'REPORT.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    gate=OUT/'prospective_gate.json'
    if not gate.exists():
        gate.write_text(json.dumps(dict(frozen_at_utc=datetime.now(timezone.utc).isoformat(),protocol_sha256=digest(OUT/'protocol.json'),
                 status='AWAITING_NEW_DATA_NOT_SCHEDULED',decision_start='First verified exchange session after freeze',
                 min_runoff_sessions=20,required=['dated membership evidence','OHLC and exchange calendar','corporate action verification'],
                 policies=POLICIES,threshold_optimization=False),indent=2),encoding='utf-8')
    (OUT/'report_verification.json').write_text(json.dumps(dict(status='PASS',accounts=len(result),completed=count,holdout='BLOCKED',input_hashes_unchanged=True),indent=2),encoding='utf-8')
    print(selected[selected.kind.isin(['baseline','actual_vintage','training_vintage'])][['scenario','period','policy','status','total_return_pct','max_drawdown_pct']].to_string(index=False))
    print(summary.to_string())


if __name__=='__main__':main()
