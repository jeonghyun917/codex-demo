"""Static figure for the existing repository Markdown research report."""
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np


def main():
    root=Path('tmp/quant-v9-financial-cause-20261101-v1')
    stages=json.loads((root/'diagnostic.json').read_text())['modelStages']
    candidate=json.loads(Path('tmp/quant-v9-return-blend-20261101-v1/internal-result.json').read_text())
    colors=['#2f5f98','#cc7834'];fig,axes=plt.subplots(1,2,figsize=(12.5,5.8),layout='constrained')
    fig.patch.set_facecolor('#f6f7fa')
    x=np.arange(2,10)
    for j,name in enumerate(['Absolute','Excess']):
        values=[v['deltaMaePp'][j] for v in stages.values()]
        axes[0].plot(x,values,marker=['o','D'][j],linestyle=['-','--'][j],color=colors[j],label=name,linewidth=2)
    axes[0].set_xticks(x,[f'V{n}' for n in x]);axes[0].set_ylim(-.026,.014)
    axes[0].set_title('Financial-repair effect by stage\n63-recovery run vs original baseline',loc='left',weight='bold',pad=18)
    axes[0].set_ylabel('Change in MAE (percentage points)')
    axes[0].axvline(5.5,color='#9ba4b1',linestyle=':',linewidth=1)
    axes[0].text(5.65,.011,'V6 scaling stage',fontsize=10,color='#4c586b')
    axes[0].legend(frameon=False,loc='lower right')
    for k,(candidate_name,head) in enumerate([('half_upstream','Absolute'),('half_upstream','Excess'),('past_mae_blend','Absolute'),('past_mae_blend','Excess')]):
        row=candidate['intervals'][candidate_name][head];y=3-k;color=colors[head=='Excess']
        value=row['deltaMaePp'];lo=row['lower95'];hi=row['upper95']
        axes[1].errorbar(value,y,xerr=[[value-lo],[hi-value]],fmt='o',color=color,capsize=4,markersize=7,linewidth=2)
        axes[1].annotate(f'{value:+.4f} [{lo:+.4f}, {hi:+.4f}]',(value,y),xytext=(0,14),textcoords='offset points',ha='center',fontsize=9)
    axes[1].set_yticks([3,2,1,0],['Half: absolute','Half: excess','Guarded: absolute','Guarded: excess'])
    axes[1].set_ylim(-.6,3.65);axes[1].set_xlim(-.135,.083)
    axes[1].set_title('Fixed candidate effect\n189-recovery run vs same rebuilt V9',loc='left',weight='bold',pad=18)
    axes[1].set_xlabel('Change in MAE (pp); left is better')
    for ax in axes:
        ax.axvline(0,color='#929baa',linewidth=1,linestyle='--') if ax==axes[1] else ax.axhline(0,color='#929baa',linewidth=1,linestyle='--')
        ax.spines[['top','right']].set_visible(False);ax.grid(alpha=.12);ax.set_axisbelow(True)
        ax.set_facecolor('white')
    fig.suptitle('Internal diagnostics do not establish lower future error',x=.01,ha='left',fontsize=17,weight='bold')
    fig.supxlabel('Exposed training only: 17,904 rows / 609 dates. Positive is worse. Right: paired 95% block intervals.\n'
                  'Guarded weight is zero: identical to baseline, not an improved model. Prospective 30-name cohort remains pending.',fontsize=10,color='#4c586b')
    output=root/'v9-cause-followup.png';fig.savefig(output,dpi=170,facecolor=fig.get_facecolor());print(output.resolve())


if __name__=='__main__':main()
