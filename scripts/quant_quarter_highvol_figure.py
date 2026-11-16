"""Reproducible static figure for the repository Markdown research record."""
import argparse
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib import font_manager

def render(root):
    result=json.loads((root/'result.json').read_text(encoding='utf-8'))
    coverage=json.loads((root/'coverage.json').read_text(encoding='utf-8'))
    font=Path('C:/Windows/Fonts/malgun.ttf')
    if font.exists():font_manager.fontManager.addfont(str(font));plt.rcParams['font.family']='Malgun Gothic'
    plt.rcParams['axes.unicode_minus']=False
    fig,(ax,bx)=plt.subplots(1,2,figsize=(14,5.8),gridspec_kw={'width_ratios':[1,1.35]})
    fig.subplots_adjust(left=.12,right=.98,bottom=.25,top=.73,wspace=.47)
    fig.patch.set_facecolor('#fafafa')
    keys=['quarter_yoy_available','quarter_yoy_acceleration_available','net_margin_yoy_available','annual_yoy_available']
    labels=['분기 EPS 성장','분기 EPS 가속','순이익률 비교','연간 EPS 성장']
    for i,key in enumerate(keys):
        before=coverage['oldAvailable'][key]/coverage['rows']*100;after=coverage['newAvailable'][key]/coverage['rows']*100
        ax.barh(i-.16,before,height=.27,color='#b6c5cd',label='보완 전' if i==0 else None)
        ax.barh(i+.16,after,height=.27,color='#1b628b',label='보완 후' if i==0 else None)
        ax.text(after+1,i+.16,f'{after:.1f}%',va='center',fontsize=10)
        ax.text(before+1,i-.16,f'{before:.1f}%',va='center',fontsize=9,color='#4b5563')
    ax.set_yticks(range(4),labels);ax.invert_yaxis();ax.set_xlim(0,110)
    ax.set_xticks([0,25,50,75,100]);ax.set_xlabel('동일한 25,473개 OOF 행 중 비교 가능한 비율 (%)')
    ax.set_title('무료 원본에서 회계 정보량 복구',fontsize=12,pad=15)
    ax.legend(loc='upper center',bbox_to_anchor=(.5,1.24),ncol=2,frameon=False,fontsize=10)
    names=['expanded_financial','highvol_market','financial_highvol','fast_regime','nested_selected']
    names_display=['확장 분기 재무','고변동 시장만','분기 재무 + 시장','최근 시장국면 보정','과거만 보고 선택']
    for j,(head,color,label,marker) in enumerate([('Absolute','#1b628b','수익률','o'),('Excess','#aa5e12','초과수익률','s')]):
        for i,name in enumerate(names):
            interval=result['bootstrap'][head] if name=='nested_selected' else result['candidateBootstrap'][name][head]
            delta,lo,hi=(interval[n] for n in ['deltaMaePp','lower95','upper95'])
            bx.errorbar(delta,i+(j-.5)*.23,xerr=[[delta-lo],[hi-delta]],fmt=marker,color=color,capsize=3,markersize=5,label=label if i==0 else None)
    bx.axvline(0,color='#555',lw=1);bx.set_yticks(range(5),names_display);bx.invert_yaxis()
    bx.set_xlabel('후보 - V9의 MAE 차이 (%p)\n음수 개선 / 양수 악화 · 21날짜 블록 95% 구간')
    bx.set_title('자료 보완이 예측 개선을 보장하지는 않음',fontsize=12,pad=15)
    bx.legend(loc='upper center',bbox_to_anchor=(.5,1.24),ncol=2,frameon=False,fontsize=10)
    for a in [ax,bx]:a.set_facecolor('#fafafa');a.grid(axis='x',alpha=.18);a.set_axisbelow(True);a.spines[['top','right']].set_visible(False)
    fig.suptitle('분기 손익·마진 보완과 고변동성 20D 예측 검증',fontsize=16,y=.96)
    fig.text(.03,.045,'오른쪽: 609거래일 · 17,904건 · 2018Q2–2020Q3 부분 구간 · 반복 사용한 훈련 자료 내부 비교\n기존 시험 60건 제외 · 원본 V9 예측 고정 · 후보를 사후 선택해 승격하지 않음',fontsize=10,color='#4b5563')
    output=root/'quarter-highvol-validation.png';fig.savefig(output,dpi=160);plt.close(fig);print(output.resolve())

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('root',type=Path);args=parser.parse_args();render(args.root)
