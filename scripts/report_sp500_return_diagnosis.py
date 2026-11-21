"""Produce a source-linked explanation with fully reconciled trade attribution."""
import json
import numpy as np
import pandas as pd
from diagnose_sp500_return_change import OLD,NEW,OUT,trades


def main():
    info=json.loads((OUT/'diagnosis.json').read_text())
    verification=json.loads((OUT/'training_verification.json').read_text())
    trade=pd.read_csv(OUT/'trade_attribution.csv')
    symbols=pd.read_csv(OUT/'symbol_attribution.csv')
    experiments=pd.read_csv(OUT/'counterfactuals.csv')
    ablations=pd.read_csv(OUT/'training_ablations.csv')
    training=pd.read_csv(OUT/'added_training_summary.csv')
    spin=json.loads((OUT/'spin_exclusion.json').read_text())
    spin_fills=pd.read_csv(OUT/'spin_exclusion_fills.csv')
    assert spin['status']=='COMPLETE_RESEARCH_ONLY'
    assert np.isclose(spin_fills.pnl.sum()/1000,spin['total_return_pct'],atol=1e-9)
    for r in experiments.itertuples():
        assert r.status=='COMPLETE_RESEARCH_ONLY'
        fills=pd.read_csv(OUT/(r.experiment+'_fills.csv'))
        assert len(fills)==r.trades
        assert np.isclose(fills.pnl.sum()/1000,r.total_return_pct,atol=1e-9)
    for r in ablations.itertuples():
        assert r.status=='COMPLETE_RESEARCH_ONLY'
        fills=pd.read_csv(OUT/(r.variant+'_fills.csv'))
        assert np.isclose(fills.pnl.sum()/1000,r.return_pct,atol=1e-9)
    common=trade[trade._merge=='both']
    for c in ('entry_price','exit_price','exit','reason'):
        assert (common[c+'_old']==common[c+'_new']).all(),c
    for label,path,expected in [('old',OLD,info['old_return']),('new',NEW,info['new_return'])]:
        original=trades(path)
        assert np.isclose(original.pnl.sum()/1000,expected,atol=1e-9)
        reconstructed=pd.read_csv(OUT/(label+'_scores_new_execution_fills.csv'))
        assert len(original)==len(reconstructed)
        for c in ('symbol','decision','entry','exit','reason'):
            assert (original[c].to_numpy()==reconstructed[c].to_numpy()).all(),c
        for c in ('shares','entry_price','exit_price','pnl','budget','proceeds'):
            assert np.allclose(original[c],reconstructed[c],rtol=1e-12,atol=1e-8),c
    assert np.isclose(trade.pnl_delta.sum(),symbols.pnl_delta.sum(),atol=1e-8)
    categories=trade.groupby('_merge').agg(count=('symbol','size'),delta=('pnl_delta','sum'))
    monthly=trade.copy()
    monthly['month']=monthly.entry.str[:7]
    monthly.groupby('month').pnl_delta.sum().to_csv(OUT/'entry_month_attribution.csv')
    lines=['# AI 단독 수익률 변화 원인 검증','',
           '## 결론',
           '- 개발 구간 수익률 +5.6211% → -5.2890%, 차이 -10.9101%p($10,910.12)를 거래 손익으로 전액 대사했습니다.',
           '- 직접 원인은 기존 체결가격 변경이나 평가 종목 교체가 아니라, 추가 과거 학습 표본으로 생긴 작은 순위 변화와 보유·현금 제약의 경로 의존성입니다.',
           '- 확률 모델의 수치적 폭주나 무작위 재학습 차이는 확인되지 않았습니다. 그러나 작은 점수 차이를 확정적 매수 우선순위로 사용하는 포트폴리오 규칙은 민감합니다.',
           '- 데이터 위험도 남아 있습니다. FBIN 분사일을 포함하는 추가 학습 표본 4개를 제외하면 -2.4567%가 됩니다. 따라서 기업행사 불확실성을 무시할 수 없으며, 모델 수치 안정성과 데이터 적정성은 별개입니다.',
           '', '## 범위와 고정 조건',
           '- 2023-01-03~2024-12-31 장 마감 판단, 2025-01-31까지 청산. 새 가격 전용 AI 단독, 초기 $100,000, 현금 포함, 편도 10bp.',
           '- +15% 익절 / -5% 손절 / 최대 20거래일, 다음 거래일 시가, 최대 10종목·하루 2건·종목당 전일 NAV 10%를 그대로 사용했습니다. 원본 결과나 운영 코드는 바꾸지 않았습니다.',
           '', '## 교차 실험',
           '|실험|계좌 수익률|MDD|', '|---|---:|---:|']
    names={'old_scores_new_execution':'기존 예측 + 새 가격/기업행사 처리','old_model_new_universe':'기존 모델 + 새 지표/후보군','new_model_old_universe':'새 모델 + 기존 지표/후보군','new_scores_new_execution':'새 예측 + 새 가격/기업행사 처리',
           'old_except_first_decision':'기존 예측에서 2023-06-21만 새 예측으로 교체','new_except_first_decision':'새 예측에서 2023-06-21만 기존 예측으로 교체'}
    for r in experiments.itertuples():
        if r.experiment in names:
            lines.append(f'|{names[r.experiment]}|{r.total_return_pct:.4f}%|{r.max_drawdown_pct:.4f}%|')
    lines += ['', '- 개발 구간 예측 242,464개 종목·날짜가 전부 일치하며 7개 입력 지표의 변경은 0건입니다. 동일 진입 거래 585건은 진입가·청산가·청산일·청산 사유도 모두 같습니다.',
              '- 따라서 이 구간의 차이는 모델 재학습으로 매수 순서가 달라지는 경로에서 발생했습니다. 교차 실험은 진단용이며 실전 과거 시점에 새 모델을 알 수 있었다는 의미가 아닙니다.',
              '', '## 학습 데이터 영향',
              f"- 공통 주간 학습 표본 {verification['common_training_rows']:,}건의 입력·라벨 변경 0건, 삭제 0건. 추가 {verification['added_training_rows']}건.",
              '|정규화된 종목|추가 학습 표본|최초 날짜|최종 날짜|익절 라벨 수|','|---|---:|---|---|---:|']
    for r in training.itertuples():
        lines.append(f'|{r.symbol}|{r.rows}|{r.first_date}|{r.last_date}|{int(r.tp_labels)}|')
    lines += ['', '|재학습 실험|수익률|MDD|','|---|---:|---:|']
    for r in ablations.itertuples():
        lines.append(f'|{r.variant}|{r.return_pct:.4f}%|{r.mdd_pct:.4f}%|')
    lines += ['', '- add_only는 기존 학습 데이터에 해당 종목의 추가 표본만 넣은 월별 재학습입니다. 모델 비선형성과 거래 경로 때문에 각각의 결과를 더해서 원인 기여율로 해석할 수 없습니다.',
              '- 실제 학습 표본으로 24개월의 기존·새 모델을 다시 학습해 저장된 예측을 재현했습니다. 6월 모델은 random_state를 731에서 999로 바꾼 추가 실험도 수행했습니다. 오차는 training_verification.json에 기록했습니다.',
              '', '## 기업행사 데이터 위험 분리',
              '- FBIN 추가 표본 중 2022-11-22, 11-30, 12-07, 12-14 판단의 20거래일 라벨 기간이 2022-12-15 분사일을 포함합니다. 실제 청산이 분사일 이후인 라벨은 2개입니다.',
              f"- 4개 모두를 보수적으로 제외해 24개월 모델을 재학습한 수익률: {spin['total_return_pct']:.4f}%, MDD {spin['max_drawdown_pct']:.4f}%, 체결 {spin['trades']}건. 기존 새 결과보다 {spin['total_return_pct']-info['new_return']:+.4f}%p 변화합니다.",
              '- 이는 해당 4개 라벨이 오류라고 입증한 것이 아닙니다. 분사 조정·분배금의 정확한 대체 라벨을 확보하지 못했으므로 제외 민감도를 확인한 것입니다. 4개를 빼도 +5.6211%는 복원되지 않습니다. 전체 차이를 데이터 오류만으로 설명할 수 없습니다.',
              '- 상세: spin_spanning_training_samples.csv, spin_spanning_label_exits.csv, spin_exclusion.json. 이 제외 결과를 새 운영 모델로 채택하지 않았습니다.',
              '', '## 최초 갈림과 후속 영향',
              '- 2023-06-21 마감에 9종목을 보유해 신규 자리는 1개였습니다. 1~4위 ZION/NWL/KEY/ENPH는 이미 보유 중이었습니다. AMD는 그날 청산한 상태였습니다.',
              '- 기존: WBD 16.882574%, AMD 16.861485% → WBD 우선. 새 모델: WBD 16.944293%, AMD 16.950119% → AMD 우선.',
              '- 6월 22일 기존은 WBD 매수 후 6월 23일 손절(-$476.83). 새 모델은 AMD 매수 후 7월 20일 기간 청산(-$179.21). 이 교체 자체는 +$297.61로 손실을 줄였습니다.',
              '- 하지만 신규 자리를 하루 만에 비우는 경우와 20거래일 점유하는 경우의 차이가 이후 거래·매수 수량을 바꿉니다. 최초 교체 손익만으로 최종 -$10,910.12를 설명하면 잘못입니다.',
              '', '## 거래별 손익 대사',
              '|구분|거래 수|새 결과 - 기존 결과|계좌 기여|','|---|---:|---:|---:|']
    for k,label in [('left_only','기존에만 있던 거래 제거'),('right_only','새로 생긴 거래'),('both','동일 진입 거래의 수량 변화')]:
        r=categories.loc[k]
        lines.append(f'|{label}|{int(r["count"])}|${r.delta:,.2f}|{r.delta/1000:+.4f}%p|')
    lines += ['', '- 공통 585건 중 421건의 매수 수량이 달라졌습니다. 매수 여력·NAV의 변화이며 동일 거래의 가격이나 청산 규칙 변화는 아닙니다.',
              '', '## 종목별 순기여 (악화 상위)',
              '|종목|기존 순손익|새 순손익|계좌 기여|','|---|---:|---:|---:|']
    for r in symbols.sort_values('pnl_delta').head(8).itertuples():
        lines.append(f'|{r.symbol}|${r.old_pnl:,.2f}|${r.new_pnl:,.2f}|{r.return_contribution_pp:+.4f}%p|')
    lines += ['', '- 이는 종목별 거래 경로의 순손익 차이입니다. 해당 기업의 데이터가 잘못됐다는 뜻이나 독립적인 인과 효과가 아닙니다.',
              '', '## 영향 큰 개별 거래',
              '|종목|진입일|기존 청산일/사유|기존 손익|새 결과|','|---|---|---|---:|---|']
    for r in trade[trade._merge=='left_only'].sort_values('pnl_delta').head(6).itertuples():
        lines.append(f'|{r.symbol}|{r.entry}|{r.exit_old}/{r.reason_old}|${r.pnl_old:,.2f}|해당 진입 거래 없음|')
    lines += ['', '- 같은 종목의 다른 날짜 진입은 별개 거래로 취급합니다. 유리한 거래만 빠졌다는 해석을 피하도록 전체 제거·추가·공통 거래와 양의 상쇄 효과를 trade_attribution.csv 및 symbol_attribution.csv에 보존했습니다.',
              '', '## 안정성 검사',
              f"- 전후 확률 상관계수 {info['probability_correlation']:.8f}, 평균 절대 확률 변화 {info['probability_abs_change_mean']*100:.6f}%p, 99분위 변화 {info['probability_abs_change_p99']*100:.6f}%p.",
              '- 고정된 예측에 독립 정규 잡음을 추가하는 보조 검사: 표준편차 0.0001%p 및 0.01%p, 각각 고정 seed 5개. 통계적 신뢰구간이 아닌 소규모 민감도 검사입니다.']
    for prefix in ('old','new'):
        for amp in ('1e-06','0.0001'):
            subset=experiments[experiments.experiment.str.startswith(prefix+'_noise_'+amp+'_')]
            lines.append(f'- {prefix}, 잡음 크기 {float(amp)*100:g}%p: 수익률 {subset.total_return_pct.min():.4f}%~{subset.total_return_pct.max():.4f}% ({len(subset)}회).')
    lines += ['', '## 판단과 다음 조치',
              '- 기존 누락 학습 표본을 보완한 것이 작은 점수 변화를 만들었고, 근소한 순위 차이로 단 하나의 자리를 채우는 규칙이 계좌 결과를 크게 바꿨습니다. 모델 수치 폭주보다는 거래 선정 규칙의 민감성이 핵심입니다.',
              '- 좋아 보였던 기존 결과로 데이터를 되돌리거나 이번 수익률에 맞춰 조건을 조정하지 않습니다. 추가 데이터의 기업행사 기준을 계속 확인하고, 근소한 순위 차이 처리·회전율·재진입 제한 등은 별도 사전 고정 실험으로 검증해야 합니다.',
              '- 표본 교체 검사는 원인 진단이지 새 전략 추천이나 실전 승인 근거가 아닙니다. 새 미사용 기간과 데이터 보완 없이는 장기 우월성을 판단할 수 없습니다.',
              '', '## 재현과 파일',
              '- 실행 순서: python scripts/diagnose_sp500_return_change.py → python scripts/diagnose_sp500_training_change.py → python scripts/diagnose_sp500_spin_sensitivity.py → python scripts/report_sp500_return_diagnosis.py',
              '- diagnosis.json: 입력 해시·교차 실험 대사. training_verification.json: 재학습 재현. counterfactuals.csv: 고정 조건 교차 실험. training_ablations.csv: 종목별 추가 학습 실험.',
              '- trade_attribution.csv: 전체 거래 대사. symbol_attribution.csv: 종목별 기여. added_training_samples.csv: 추가 학습 표본. first_divergence_ranks.csv: 최초 갈림 전일 순위.',
              '- 모든 산출물은 이 폴더에 저장했으며 기존 두 결과 폴더와 운영 DB는 수정하지 않았습니다.']
    (OUT/'REPORT.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    (OUT/'attribution_verification.json').write_text(json.dumps(dict(status='PASS',matched_trade_prices_and_exits=len(common),trade_cash_replay='PASS',symbol_and_trade_delta_reconciliation='PASS'),indent=2),encoding='utf-8')
    print('Report and independent attribution checks PASS')


if __name__=='__main__':main()
