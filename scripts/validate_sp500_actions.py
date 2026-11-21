"""Repeat frozen scenarios and record the precise effect of action-data repair."""
import json
import sys
from pathlib import Path
import pandas as pd
import validate_sp500_extension as replay
from build_sp500_actions import OUT,DATA,BASE,START
from build_sp500_daily_dataset import read_csv,digest


def main():
    replay.DATA=DATA;replay.OUT=OUT
    original_load=replay.engine.load_features
    def checked_load():
        result=original_load()
        counts={s:sum(r['symbol']==s for r in result[3]) for s in START}
        (OUT/'target_feature_counts.json').write_text(json.dumps(counts,indent=2),encoding='utf-8')
        return result
    replay.engine.load_features=checked_load
    replay.main()
    compare()


def compare():
    before=read_csv(BASE/'inputs/prices.csv.gz')
    after={(r['symbol'],r['trade_date']):r for r in read_csv(DATA/'inputs/prices.csv.gz')}
    assert all(after[r['symbol'],r['trade_date']]==r for r in before)
    changes={}
    for name in ('revalidation.csv','revalidation_fills.csv','revalidation_equity.csv.gz','revalidation_events.csv','unseen_research_predictions.csv.gz'):
        a=pd.read_csv(BASE.parent/name);b=pd.read_csv(OUT/name)
        try:pd.testing.assert_frame_equal(a,b,check_exact=True);changes[name]='EXACTLY_EQUAL'
        except AssertionError:changes[name]='CHANGED_REVIEW_REQUIRED'
    # This window has now been inspected; it must not be represented as a fresh holdout.
    (OUT/'comparison.json').write_text(json.dumps(dict(original_rows_preserved=len(before),
        comparisons=changes,period_status='REPEATED_RETROSPECTIVE_WINDOW_NOT_UNUSED_HOLDOUT',
        exclusions_unchanged=True,strategy_module_sha256=digest(Path(replay.engine.__file__))),indent=2),encoding='utf-8')
    print(json.dumps(changes,indent=2),flush=True)


if __name__=='__main__':
    if '--compare-only' in sys.argv:compare()
    else:main()
