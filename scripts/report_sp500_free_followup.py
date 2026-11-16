"""Render the recovery evidence and matched frozen-protocol results."""
import json
from pathlib import Path
from build_sp500_daily_dataset import read_csv,write_csv

ROOT=Path(__file__).resolve().parents[1]/'output'
OUT=ROOT/'sp500-free-followup-20261027'


def main():
    summary=json.loads((OUT/'recovery_summary.json').read_text())
    previous=json.loads((ROOT/'sp500-validation-20261027/research/results.json').read_text())
    current=json.loads((OUT/'research/results.json').read_text())
    verified=json.loads((OUT/'research/verification.json').read_text())
    audit=read_csv(OUT/'secondary_candidate_audit.csv')
    comparisons=[]
    for r in current['comparisons']:
        old=next(o for o in previous['comparisons'] if all(o[k]==r[k] for k in ('period','rule','fee')))
        comparisons.append({k:r[k] for k in ('period','rule','fee','status','evaluated_end','total_return_pct','prefix_return_pct','max_drawdown_pct','average_cash_pct','trades')} |
                           dict(previous_return_pct=old['total_return_pct'],previous_mdd_pct=old['max_drawdown_pct'],halt=json.dumps(r['halt'])))
    write_csv(OUT/'matched_comparison.csv',comparisons,list(comparisons[0]))
    lines=['# 무료 누락 데이터 복구 및 동일 조건 재검증','',
           f"- 누락 종목: 54 → {summary['remaining_symbols']}개. 누락 구성종목·거래일: 25,095 → {summary['after_missing']:,}건. 복구: {summary['recovered_member_rows']:,}건.",
           '- 기존 원본 가격과 구성종목 분모는 보존했습니다. 운영 DB·실거래·배포에는 반영하지 않았습니다.',
           '- NLOK→GEN, GPS→GAP, FBHS→FBIN, LB→BBWI의 공식 연속성 근거를 적용했습니다. FERG·RDDT의 누락 일봉도 보완했습니다.',
           '- 분사 분배금·교환비율·배당 재투자는 해결된 것이 아닙니다. 공급자 quote OHLCV이며 원시 체결가격 또는 총수익률 인증이 아닙니다.',
           '', '## 추가 무료 자료와 미해결',
           f"- Yahoo 63종목 요청에서 JSON 22건 확보. Stooq는 브라우저 검증 응답으로 중단했습니다. 추가 HTML {len(audit)}개를 저장했고 표 {sum(int(x['rows']) for x in audit):,}행을 검사했습니다.",
           '- 추가 표는 짧은 기간만 공개되고 가격 조정 기준·과거 발행사 정체성이 미확인되어 연구 입력에 합치지 않았습니다. 거래량 1 이하 또는 결측인 행도 후보 감사표에 표시했습니다.',
           '- 추가 HTML 중 22개는 다른 URL로 이동했습니다. 예를 들어 ABMD 요청은 JNJ로 이동했으므로 피인수기업의 과거 일봉으로 사용하지 않았습니다. 원본 해시 85건 검사와 격리 구간 체결 0건 검사를 통과했습니다.',
           '- LB(LandBridge), INFO(ETF), PARA(Banzai) 등 티커 재사용 응답은 옛 기업 가격으로 연결하지 않았습니다.',
           '- PSKY 합병 전 선행기업 이력, AVB/EQR 합병 후 가격은 정체성·결제 근거가 미해결인 구간을 격리했습니다. 해당 가격으로 주문하거나 보유 평가해야 하면 검증을 중단합니다.',
           '- 미해결 48종목별 누락 범위와 필요한 조치는 unresolved_data.csv, 추가 표 감사는 secondary_candidate_audit.csv, 가격 충돌은 price_basis_conflicts.csv에 있습니다.',
           '- 추가 필요: 상장폐지 전 전체 일봉, 날짜별 영구 증권 식별자, 분할·분사 조정계수, 합병 현금/주식 선택 및 실제 결제일, 상장폐지 잔여가치. 공식 과거 구성종목과 거래소 캘린더도 별도 확보가 필요합니다.',
           '', '## 고정 검증 조건',
           '- 장 마감 판단 → 다음 거래일 시가, 익절 +15%, 손절 -5%, 진입일 포함 최대 20거래일. 같은 일봉에서 양쪽 도달 시 손절 우선, 갭 손절은 시가.',
           '- 초기 10만 달러, 현금 포함 NAV, 최대 10종목·하루 신규 2종목·종목당 전일 NAV 10%, 정수 주식, 레버리지 없음. 편도 10bp 및 20bp. 현금 이자·배당·세금·환율 제외.',
           '- 이전과 같은 기간·모델·월별 확장 학습·성숙 라벨 분리·주간 학습 표본을 유지했습니다. 입력 복구 및 기업행사 격리 외 조건 최적화는 하지 않았습니다.',
           '- 새 가격 전용 모델입니다. MOM20은 과거 20일 수익률이며 기존 20D/signal 모델이 아닙니다. 과거 시점별 예측 기록이 없어 기존 모델 우열 비교는 불가능합니다.',
           '', '## 비교 결과 (편도 10bp)',
           '|구간|전략|이전 수익률|재검증 수익률|재검증 MDD|상태|',
           '|---|---|---:|---:|---:|---|']
    fmt=lambda x:'검증 중단' if x is None else f'{x:.2f}%'
    for r in comparisons:
        if r['fee']==.001 and r['rule']!='CASH':
            lines.append(f"|{r['period']}|{r['rule']}|{fmt(r['previous_return_pct'])}|{fmt(r['total_return_pct'])}|{fmt(r['max_drawdown_pct'])}|{r['status']}|")
    lines += ['', '- 검증 중단 계좌의 MDD는 중단 전 구간만 해당합니다. prefix 수익률을 전체 기간 수익률로 해석하면 안 됩니다. 20bp 결과와 정확한 중단 사유는 matched_comparison.csv에 있습니다.',
              '- 이번 24개 계좌는 모두 계산 완료했습니다. 다만 개발 구간 AI 단독 수익률이 +5.62%에서 -5.29%로 바뀌어 입력 보완에 민감합니다. 변경의 개별 원인 기여도는 분리하지 않았으며 최근 평가 구간 개선만으로 우수성을 주장할 수 없습니다.',
              '- development: 2023-01-03~2024-12-31 판단, 2025-01-31까지 청산. inspected_evaluation: 2025-01-02~2026-08-18 판단, 2026-09-16까지 청산. complete_price_window: 2026-06-03~07-02 판단, 07-31까지 청산.',
              '- 모든 기간은 이미 관찰한 구간입니다. 41거래일 완전 가격 구간도 장기 성과의 증거가 아닙니다. 결측·생존편향·기업행사 미해결이 남아 있어 실전 전략 승격 불가.',
              '', '## 검증 증거',
              f"- 데이터셋 657,407행 독립 검사 PASS. 계좌 {verified['accounts']}개, 체결 {verified['filled_trades_verified']:,}건, 확률 {verified['probabilities_recalculated']:,}건 재계산 PASS.",
              f"- 이전 원본 가격 {verified['original_price_rows_unchanged']:,}행 불변. Python 관련 테스트 16개 통과.",
              '- 재현: python scripts/finalize_sp500_free_followup.py / python scripts/report_sp500_free_followup.py',
              '- 공식 연속성 출처는 dataset/inputs/aliases.csv의 source_url, 기업행사 경계 출처는 dataset/inputs/quality_overrides.csv에 보존했습니다. 다운로드 원본·URL·SHA256은 acquisition.json과 secondary_acquisition.json에 있습니다.']
    (OUT/'REPORT.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps(comparisons,indent=2))


if __name__=='__main__':main()
