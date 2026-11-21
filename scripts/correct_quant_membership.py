"""Correct community membership states using bounded official evidence overrides.

No networking or DB writes. Raw inputs are immutable. No official-PIT promotion.
"""
from bisect import bisect_right
from datetime import date, timedelta

DAY=timedelta(days=1)


def correct_states(states, events, sources, max_shift_days=31):
    """Return minimal bounded overrides and a complete before/after event audit.

Only an adjacent community transition or an explicit opposite official event
can terminate a missing-state correction. Never extend baseline coverage.
"""
    if not states or max_shift_days<1:
        raise ValueError('Nonempty states and positive shift bound are required')
    days=[day for day,_ in states]
    if days!=sorted(set(days)):
        raise ValueError('Community state dates must be strictly increasing')
    baseline=[(day,set(names)) for day,names in states]
    first,last=days[0],days[-1]
    checked=[]
    seen={}
    for e in sorted(events,key=lambda e:(e['effectiveDate'],e['symbol'],e['action'])):
        day=date.fromisoformat(e['effectiveDate'])
        if e['action'] not in ('ADD','REMOVE') or e.get('indexCode')!='SP500':
            raise ValueError('Only SP500 ADD/REMOVE evidence is supported')
        source=sources[e['sourceId']]
        if date.fromisoformat(source['publishedDate'])>day:
            raise ValueError('Source publication is after the effective date')
        key=(e['symbol'],day)
        if key in seen:
            if seen[key]!=e['action']:
                raise ValueError('Contradictory actions for one security/day')
            continue
        seen[key]=e['action']
        checked.append((day,e))
    overrides=[]
    unresolved=[]
    out_of_range=[]

    def base_members(day):
        idx=bisect_right(days,day)-1
        return set(baseline[idx][1]) if idx>=0 else set()

    def members(day):
        names=base_members(day)
        for override in overrides:
            if date.fromisoformat(override['startDate'])<=day<=date.fromisoformat(override['endDate']):
                if override['member']:
                    names.add(override['symbol'])
                else:
                    names.discard(override['symbol'])
        return names

    def evidence(e):
        source=sources[e['sourceId']]
        return dict(sourceId=e['sourceId'],url=source['url'],publishedDate=source['publishedDate'],
                    effectiveDate=e['effectiveDate'],timing=e['timing'],action=e['action'])

    in_range=[]
    for day,e in checked:
        symbol=e['symbol']
        want=e['action']=='ADD'
        if not first<day<=last:
            out_of_range.append(e)
            continue
        in_range.append((day,e))
        if e.get('timing')!='BEFORE_OPEN':
            unresolved.append(dict(event=e,reason='NON_BEFORE_OPEN_TIMING_REQUIRES_SESSION_MAPPING'))
            continue
        before=symbol in members(day-DAY)
        after=symbol in members(day)
        if before!=want and after==want:
            continue
        start=end=None
        target=None
        supporting=[evidence(e)]
        transitions=[when for (prev,old),(when,new) in zip(baseline,baseline[1:])
                     if (symbol in old)!=(symbol in new) and (symbol in new)==want]
        if before==want and after==want:
            earlier=[when for when in transitions if day-timedelta(days=max_shift_days)<=when<day]
            if earlier:
                start,end,target=earlier[-1],day-DAY,not want
        elif before!=want and after!=want:
            later=[when for when in transitions if day<when<=min(last,day+timedelta(days=max_shift_days))]
            opposite=[(when,other) for when,other in checked if other['symbol']==symbol
                      and other['action']!=e['action'] and day<when<=min(last,day+timedelta(days=max_shift_days))]
            anchors=[(when,None) for when in later]+opposite
            if anchors:
                anchor,support=min(anchors,key=lambda item:item[0])
                start,end,target=day,anchor-DAY,want
                if support:
                    supporting.append(evidence(support))
        if start is None or any(other['symbol']==symbol and start<when<end
                                for when,other in checked if other is not e):
            unresolved.append(dict(event=e,reason='NO_UNAMBIGUOUS_BOUNDED_CORRECTION_ANCHOR'))
            continue
        overrides.append(dict(id=f'override-{len(overrides)+1:03d}',symbol=symbol,
                              startDate=str(start),endDate=str(end),member=target,
                              originalMemberOnStart=symbol in base_members(start),
                              correctedMemberOnStart=target,reason='BOUNDED_OFFICIAL_EFFECTIVE_DATE_CORRECTION',
                              evidence=supporting))
    boundaries=set(days)
    for o in overrides:
        boundaries.add(date.fromisoformat(o['startDate']))
        if date.fromisoformat(o['endDate'])+DAY<=last:
            boundaries.add(date.fromisoformat(o['endDate'])+DAY)
    corrected=[(day,members(day)) for day in sorted(boundaries) if first<=day<=last]
    qa=[]
    for day,e in in_range:
        symbol=e['symbol'];want=e['action']=='ADD'
        old_before=symbol in base_members(day-DAY);old_after=symbol in base_members(day)
        new_before=symbol in members(day-DAY);new_after=symbol in members(day)
        qa.append(dict(event=e,evidence=evidence(e),
                       originalMemberPreviousDay=old_before,originalMemberEffectiveDay=old_after,
                       correctedMemberPreviousDay=new_before,correctedMemberEffectiveDay=new_after,
                       beforeCorrect=old_before!=want and old_after==want,
                       afterCorrect=new_before!=want and new_after==want))
    return dict(states=corrected,overrides=overrides,qa=qa,unresolved=unresolved,outOfRange=out_of_range)


def main():
    import argparse
    import csv
    import hashlib
    import json
    from datetime import datetime, timezone
    from pathlib import Path

    root=Path(__file__).resolve().parents[1]
    free=root/'tmp/free-quant-data-2026-10-18'
    original=free/'raw/fja05680_sp500/S&P 500 Historical Components & Changes (Updated).csv'
    evidence_path=root/'tmp/pit-acquisition/membership/bundle.json'
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=root/'tmp/free-quant-corrected-2026-10-18')
    args=parser.parse_args()
    output=args.output.resolve()
    if output.is_relative_to(free/'raw') or output.is_relative_to(evidence_path.parent/'raw'):
        raise ValueError('Output cannot be inside an immutable raw-source directory')

    def sha(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    free_manifest=json.loads((free/'manifest.json').read_text(encoding='utf-8'))
    baseline_record=next(s for s in free_manifest['sources'] if free/s['path']==original)
    if sha(original)!=baseline_record['sha256']:
        raise ValueError('Community baseline hash mismatch')
    bundle=json.loads(evidence_path.read_text(encoding='utf-8'))
    for source in bundle['sources']:
        if sha(evidence_path.parent/source['path'])!=source['sha256']:
            raise ValueError('Official raw evidence hash mismatch: '+source['id'])
    with original.open(encoding='utf-8',newline='') as inp:
        states=[(date.fromisoformat(row['date']),{s.replace('.','-') for s in row['tickers'].split(',')})
                for row in csv.DictReader(inp)]
    result=correct_states(states,[e for e in bundle['events'] if e['indexCode']=='SP500'],
                          {s['id']:s for s in bundle['sources']})
    if result['unresolved'] or not all(row['afterCorrect'] for row in result['qa']):
        raise ValueError('In-range official transition correction remains unresolved')

    # Independently transcribed facts from original Nasdaq releases read through
    # the web reader. These are not represented as downloaded original files.
    annual=[
        ('2021-12-10','2021-12-20','104321',
         ['ABNB','FTNT','PANW','LCID','ZS','DDOG'],['CDW','FOXA','FOX','CERN','CHKP','TCOM','INCY']),
        ('2022-12-09','2022-12-19','105641',
         ['CSGP','RIVN','WBD','GFS','BKR','FANG'],['VRSN','SWKS','SPLK','BIDU','MTCH','DOCU','NTES']),
        ('2023-12-08','2023-12-18','106896',
         ['CDW','CCEP','DASH','MDB','ROP','SPLK'],['ALGN','EBAY','ENPH','JD','LCID','ZM']),
        ('2024-12-13','2024-12-23','108396',
         ['PLTR','MSTR','AXON'],['ILMN','SMCI','MRNA']),
        ('2025-12-12','2025-12-22','109786',
         ['ALNY','FER','INSM','MPWR','STX','WDC'],['BIIB','CDW','GFS','LULU','ON','TTD']),
    ]
    n_event_path=free/'derived/nasdaq100_community_events.csv'
    with n_event_path.open(encoding='utf-8',newline='') as inp:
        n_events=list(csv.DictReader(inp))
    annual_checks=[]
    for published,effective,node,adds,removes in annual:
        actual_add={r['symbol'] for r in n_events if r['date']==effective and r['action']=='ADD'}
        actual_remove={r['symbol'] for r in n_events if r['date']==effective and r['action']=='REMOVE'}
        annual_checks.append(dict(publishedDate=published,effectiveDate=effective,timing='BEFORE_OPEN',
                                  url='https://ir.nasdaq.com/node/'+node,
                                  accessMethod='WEB_READER_NO_ORIGINAL_RAW_DOWNLOAD',
                                  expectedAdd=sorted(adds),expectedRemove=sorted(removes),
                                  actualAdd=sorted(actual_add),actualRemove=sorted(actual_remove),
                                  matches=actual_add==set(adds) and actual_remove==set(removes)))
    if not all(row['matches'] for row in annual_checks):
        raise ValueError('Nasdaq annual changes disagree with original release facts')

    source_class='COMMUNITY_RECONSTRUCTION_CORRECTED'
    base_id='community-fja-'+baseline_record['commit']
    output.mkdir(parents=True,exist_ok=True)
    active={}
    intervals=[]
    for day,names in result['states']:
        provenance={symbol:(base_id,) for symbol in names}
        for override in result['overrides']:
            if override['member'] and date.fromisoformat(override['startDate'])<=day<=date.fromisoformat(override['endDate']):
                provenance[override['symbol']]=(base_id,override['id'])
        for symbol in set(active)-names:
            start,ids=active.pop(symbol)
            intervals.append((symbol,start,day-DAY,ids))
        for symbol in names:
            ids=provenance[symbol]
            if symbol in active and active[symbol][1]!=ids:
                start,old_ids=active.pop(symbol)
                intervals.append((symbol,start,day-DAY,old_ids))
            if symbol not in active:
                active[symbol]=(day,ids)
    last=result['states'][-1][0]
    intervals.extend((symbol,start,last,ids) for symbol,(start,ids) in active.items())
    interval_path=output/'sp500_membership_intervals.csv'
    with interval_path.open('w',encoding='utf-8',newline='') as out:
        writer=csv.writer(out)
        writer.writerow(['index_code','symbol','start_date','end_date','source','provenance_ids','is_member'])
        writer.writerows(('SP500',symbol,start,end,source_class,';'.join(ids),'true')
                         for symbol,start,end,ids in sorted(intervals))
    state_path=output/'sp500_membership_states.csv'
    with state_path.open('w',encoding='utf-8',newline='') as out:
        writer=csv.writer(out)
        writer.writerow(['date','tickers','member_count','source'])
        writer.writerows((day,','.join(sorted(names)),len(names),source_class) for day,names in result['states'])
    original_ndx=free/'derived/nasdaq100_jmccarrell_intervals_RESEARCH_ONLY.csv'
    ndx_path=output/'nasdaq100_membership_intervals.csv'
    ndx_path.write_bytes(original_ndx.read_bytes())
    qa=[dict(row,status='IN_RANGE_CHECKED') for row in result['qa']]
    qa.extend(dict(event=e,status='OUTSIDE_BASELINE_COVERAGE',beforeCorrect=None,afterCorrect=None)
              for e in result['outOfRange'])
    report=dict(generatedAt=datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),
                sourceClassification=source_class,officialPitEligible=False,
                coverage=dict(firstDate=str(states[0][0]),lastDate=str(last),completeOfficialChain=False,
                              extensionPolicy='NO_CARRY_FORWARD_BEYOND_ACQUIRED_COMMUNITY_FINAL_STATE'),
                summary=dict(officialSP500Events=len(qa),inRangeEvents=len(result['qa']),
                             outOfRangeEvents=len(result['outOfRange']),overrides=len(result['overrides']),
                             officialTransitionsCorrectBefore=sum(r['beforeCorrect'] for r in result['qa']),
                             officialTransitionsCorrectAfter=sum(r['afterCorrect'] for r in result['qa']),
                             originalStates=len(states),correctedStates=len(result['states']),
                             sp500Intervals=len(intervals),
                             modifiedSecurityCalendarDays=sum((date.fromisoformat(o['endDate'])-
                                                               date.fromisoformat(o['startDate'])).days+1
                                                              for o in result['overrides'])),
                baselineProvenance=dict(id=base_id,**baseline_record),
                officialEvidenceBundle=dict(path=str(evidence_path),sha256=sha(evidence_path)),
                overrides=result['overrides'],officialEventQA=qa,unresolved=result['unresolved'],
                nasdaqAnnualAnnouncementChecks=annual_checks,
                nasdaqStatus='COMMUNITY_UNCHANGED_56_ANNUAL_SYMBOL_ACTIONS_VERIFIED_NO_FULL_CHAIN_CERTIFICATION',
                outputs=[dict(path=p.name,sha256=sha(p),bytes=p.stat().st_size)
                         for p in [interval_path,state_path,ndx_path]],
                inputs=[dict(path=str(p),sha256=sha(p)) for p in [original,n_event_path,original_ndx]],
                limitations=['Corrected known date errors do not certify every historical membership transition.',
                             'Positive intervals require complete-universe replacement semantics, not proxy overlay.',
                             'Historical membership effective time and announcement knowledge time remain distinct.',
                             'No DB writes or model performance claims are made by this generator.'])
    (output/'manifest.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report['summary']))


if __name__=='__main__':
    main()
