"""Static scientific figure for the repository's Markdown research record."""
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib import font_manager

root=Path('tmp/quant-v9-nested-training-20261101-v1')
r=json.loads((root/'result.json').read_text(encoding='utf-8'))
font=Path('C:/Windows/Fonts/malgun.ttf')
if font.exists():
    font_manager.fontManager.addfont(str(font));plt.rcParams['font.family']='Malgun Gothic'
plt.rcParams['axes.unicode_minus']=False
fig,ax=plt.subplots(figsize=(9,4.3))
fig.subplots_adjust(left=.25,right=.98,top=.82,bottom=.30)
fig.patch.set_facecolor('#fafafa');ax.set_facecolor('#fafafa')
labels=['20D 수익률','벤치마크 대비 수익률']
for y,(name,label) in enumerate(zip(['Absolute','Excess'],labels)):
    a=r['bootstrap'][name];delta=a['deltaMaePp'];lo=a['lower95'];hi=a['upper95']
    ax.errorbar(delta,y,xerr=[[delta-lo],[hi-delta]],fmt='o',color='#175b9b',capsize=5,markersize=7,lw=2)
    ax.annotate(f'{delta:+.5f}%p  [{lo:+.3f}, {hi:+.3f}]',(0,y),xytext=(0,15),textcoords='offset points',ha='center',fontsize=11,color='#1f2933')
ax.axvline(0,color='#525a61',lw=1)
ax.set_yticks([0,1],labels);ax.invert_yaxis();ax.set_ylim(1.5,-.55)
ax.set_xlabel('자동 선택 절차 - 기존 V9의 MAE 차이 (%p)\n음수는 개선 · 양수는 악화',fontsize=11)
ax.set_title('훈련 내부 시간순 검증: 두 불확실성 구간 모두 0 포함',fontsize=14,pad=18)
ax.grid(axis='x',alpha=.18);ax.spines[['top','right','left']].set_visible(False)
ax.tick_params(axis='both',labelsize=11)
fig.text(.03,.03,'609개 시점 · 17,904건 · 21시점 이동 블록 부트스트랩 95% 구간 · 새 미사용 시험이 아님',fontsize=10,color='#4b5563')
fig.savefig(root/'mae-difference.png',dpi=170,bbox_inches='tight')
print(root/'mae-difference.png')
