"""Static figure for the existing repository Markdown research format."""
import argparse
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib import font_manager

def render(root):
    result=json.loads((root/'result.json').read_text(encoding='utf-8'))
    diagnosis=json.loads((root/'diagnostics.json').read_text(encoding='utf-8'))
    font=Path('C:/Windows/Fonts/malgun.ttf')
    if font.exists():font_manager.fontManager.addfont(str(font));plt.rcParams['font.family']='Malgun Gothic'
    plt.rcParams['axes.unicode_minus']=False
    fig,(ax,bx)=plt.subplots(1,2,figsize=(13,5.3),gridspec_kw={'width_ratios':[1.25,1]})
    fig.subplots_adjust(left=.15,right=.98,bottom=.24,top=.75,wspace=.33)
    fig.patch.set_facecolor('#fafafa')
    candidates=['context','eps_growth','eps_acceleration','eps_confirmation','nested_selected']
    labels=['시장 맥락만','EPS 성장 추가','EPS 가속 추가','실적 확인 추가','과거만 보고 선택']
    for j,(head,color,label) in enumerate([('Absolute','#1b628b','수익률'),('Excess','#aa5e12','초과수익률')]):
        for i,name in enumerate(candidates):
            interval=result['bootstrap'][head] if name=='nested_selected' else result['candidateBootstrap'][name][head]
            delta,lo,hi=(interval[n] for n in ['deltaMaePp','lower95','upper95'])
            ax.errorbar(delta,i+(j-.5)*.22,xerr=[[delta-lo],[hi-delta]],fmt='o' if j==0 else 's',color=color,capsize=3,markersize=5,label=label if i==0 else None)
    ax.set_yticks(range(len(labels)),labels);ax.invert_yaxis();ax.axvline(0,color='#555',lw=1)
    ax.set_xlabel('후보 - V9의 MAE 차이 (%p)\n음수 개선 / 양수 악화 · 21날짜 블록 95% 구간')
    ax.set_title('후보 선택 절차는 V9보다 개선되지 않음',pad=15,fontsize=12)
    handles,legend_labels=ax.get_legend_handles_labels()
    fig.legend(handles,legend_labels,loc='upper center',bbox_to_anchor=(.5,.90),ncol=2,frameon=False,fontsize=10)
    ax.grid(axis='x',alpha=.2)
    names=['below15','15to25','25plus'];display=['15% 미만','15–25%','25% 이상']
    segments={s['value']:s for s in diagnosis['segments'] if s['cut']=='prior_market_volatility'}
    values=[segments[n]['metrics']['Absolute']['maePp'] for n in names]
    bars=bx.bar(display,values,color=['#a4becb','#5a8ca5','#1b628b'],width=.58)
    for bar,n,value in zip(bars,names,values):bx.text(bar.get_x()+bar.get_width()/2,value+.12,f'{value:.2f}\n{segments[n]["dates"]}일',ha='center',va='bottom',fontsize=10)
    bx.set_ylim(0,max(values)*1.24);bx.set_ylabel('기존 V9 수익률 MAE (%p)')
    bx.set_xlabel('예측 시점까지 관측한 시장 연율 변동성')
    bx.set_title('고변동성 구간에서 오차가 더 큼',pad=15,fontsize=12);bx.grid(axis='y',alpha=.2);bx.set_axisbelow(True)
    for a in [ax,bx]:a.set_facecolor('#fafafa');a.spines[['top','right']].set_visible(False)
    fig.suptitle('20D 예측: EPS 성장·실적 가속 후보의 시간순 검증',fontsize=16,y=.95)
    fig.text(.03,.045,'609거래일 · 17,904건 · 2018Q2–2020Q3 부분 구간 · 이미 사용한 훈련 자료 내부 비교\n기존 시험 60건 제외 · 오른쪽 구간 차이는 연관성이지 인과 증명이 아님',fontsize=10,color='#4b5563')
    output=root/'eps-growth-validation.png';fig.savefig(output,dpi=160);plt.close(fig)
    print(output.resolve())

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('root',type=Path);args=parser.parse_args();render(args.root)
