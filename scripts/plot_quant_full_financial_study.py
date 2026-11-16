"""Render the sealed full-financial study comparisons without model selection."""
import json
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np


def main():
    root = Path('tmp/quant-full-financial-study-20261101-v2')
    v1 = json.loads((root.parent / 'quant-full-financial-study-20261101-v1/comparison.json').read_text())
    v2 = json.loads((root / 'comparison.json').read_text())
    fig, axes = plt.subplots(2, 2, figsize=(12, 8), layout='constrained')
    fig.patch.set_facecolor('#f6f7fa')
    colors = ['#707a8c', '#aab4c5', '#d17836']
    labels = ['Baseline', 'V1 diagnostic', 'V2 primary']
    for ax, head in zip(axes[0], ['Absolute', 'Excess']):
        values = [v2['comparison']['primary']['before'][head]['maePp'],
                  v1['comparison']['primary']['after'][head]['maePp'],
                  v2['comparison']['primary']['after'][head]['maePp']]
        bars = ax.bar(labels, values, color=colors, width=.6)
        ax.bar_label(bars, labels=[f'{v:.4f}' for v in values], padding=5, fontsize=11)
        ax.set_ylim(0, max(values)*1.16)
        ax.set_ylabel('20D MAE (percentage points)')
        ax.set_title(f'{head} return | primary evaluation', loc='left', weight='bold', pad=15)
        ax.grid(axis='y', alpha=.15)
        ax.set_axisbelow(True)
    for ax, key, title in [(axes[1, 0], 'bootstrap', 'Primary: 609 dates / 17,904 rows'),
                            (axes[1, 1], 'highVolBootstrap', 'High volatility: 109 dates / 3,250 rows')]:
        records = [v2[key]['Absolute'], v2[key]['Excess']]
        values = np.array([r['deltaMaePp'] for r in records])
        lower = np.array([r['lower95'] for r in records])
        upper = np.array([r['upper95'] for r in records])
        ax.errorbar(values, [1, 0], xerr=[values-lower, upper-values], fmt='o',
                    color=colors[-1], capsize=5, markersize=7, linewidth=2)
        ax.axvline(0, color='#707a8c', linewidth=1, linestyle='--')
        ax.set_yticks([1, 0], ['Absolute', 'Excess'])
        ax.set_ylim(-.55, 1.55)
        ax.set_xlabel('V2 minus baseline MAE (pp); left is better')
        ax.set_title(title, loc='left', weight='bold', pad=15)
        ax.grid(axis='x', alpha=.15)
        for y, r in zip([1, 0], records):
            ax.annotate(f"{r['deltaMaePp']:+.4f} [{r['lower95']:+.4f}, {r['upper95']:+.4f}]",
                        (r['deltaMaePp'], y), xytext=(0, 15), textcoords='offset points',
                        ha='center', fontsize=9)
        lo, hi = ax.get_xlim()
        ax.set_xlim(min(lo, 0)-.005, hi+.005)
    for ax in axes.flat:
        ax.spines[['top', 'right']].set_visible(False)
        ax.set_facecolor('white')
    fig.suptitle('Full V9 replay after primary-source financial recovery', x=.01, ha='left',
                 fontsize=18, weight='bold')
    fig.supxlabel('Previously exposed internal research, not a new holdout. Old 60 tests excluded.\n'
                  'Date-balanced errors; 95% paired 21-date block intervals. V2 fixed before V1 metrics were viewed.',
                  fontsize=10, color='#4c586b')
    output = root / 'full-v9-financial-validation.png'
    fig.savefig(output, dpi=170, facecolor=fig.get_facecolor())
    print(output.resolve())


if __name__ == '__main__':
    main()
