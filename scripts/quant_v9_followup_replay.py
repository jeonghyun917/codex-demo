"""Seal a null-only original-revenue follow-up; use the unchanged actual replay."""
import argparse
import copy
import json
from pathlib import Path

import quant_full_financial_study as base
import quant_v9_financial_followup as followup

ROOT=Path('tmp/quant-full-financial-study-20261101-v3')
V2=followup.V2
V2_PROTOCOL_SHA='fc4361c0b8e52326dd597630480b95d6ab857fd669b73599b42330392d51b0cd'


def assert_overlay_parity(before,after):
    ignored={'financialsBySymbol','inputHashes'}
    if {k:v for k,v in before.items() if k not in ignored}!={k:v for k,v in after.items() if k not in ignored}:
        raise ValueError('Nonfinancial input changed')
    for value in [before,after]:
        if any(r.get('currency')!='USD' for rs in value['financialsBySymbol'].values() for r in rs):
            raise ValueError('Mixed financial currency')
    if before['financialsBySymbol'].keys()!=after['financialsBySymbol'].keys():
        raise ValueError('Issuer population changed')
    for symbol,rows in before['financialsBySymbol'].items():
        old={base.key(r):r for r in rows};new={base.key(r):r for r in after['financialsBySymbol'][symbol]}
        if len(old)!=len(rows) or len(new)!=len(after['financialsBySymbol'][symbol]) or old.keys()!=new.keys():
            raise ValueError('Physical statement population changed')
        for key,row in old.items():
            after_row=new[key]
            if {k:v for k,v in row.items() if k not in {'revenue','rawJson'}}!={k:v for k,v in after_row.items() if k not in {'revenue','rawJson'}}:
                raise ValueError('Non-revenue financial field changed')
            if row.get('revenue') is not None and row['revenue']!=after_row.get('revenue'):
                raise ValueError('Original non-null revenue changed')


def prepare():
    if ROOT.exists():raise ValueError('Never overwrite follow-up study')
    if base.sha(V2/'protocol.json')!=V2_PROTOCOL_SHA:raise ValueError('V2 protocol drift')
    old_protocol=base.load(V2/'protocol.json');base.utility.verify_sources(old_protocol['inputHashes'])
    if base.sha(V2/'inputs.json')!=old_protocol['inputsSha256']:raise ValueError('V2 inputs drift')
    before=base.load(V2/'inputs.json');after=copy.deepcopy(before)
    paths=[Path('tmp')/f'quant-revenue-primary-{group}-20261101-v{version}'/'overrides.json'
           for version in [2,3,4] for group in ['goods','services']]
    entries=[e for path in paths for e in base.load(path)]
    after['financialsBySymbol'],audit=followup.overlay_revenues(before['financialsBySymbol'],entries)
    assert_overlay_parity(before,after)
    target_source=base.load(base.PACK/'financials.json')
    targets={base.key(r,base.OVERRIDE_IDENTITY):r for rs in target_source.values() for r in rs if r.get('revenue') is None}
    all_paths=[Path('tmp')/f'quant-revenue-primary-{group}-20261101-v{version}'/'overrides.json'
               for version in [1,2,3,4] for group in ['goods','services']]
    all_entries=[e for path in all_paths for e in base.load(path)];seen=set()
    for entry in all_entries:
        key=base.key(entry['identity'],base.OVERRIDE_IDENTITY)
        if key not in targets or key in seen:raise ValueError('Unknown/duplicate original missing target')
        base.validate_override(entry,targets[key]);seen.add(key)
    if len(targets)!=203 or len(entries)!=126 or len(seen)!=189 or audit['filled']!=126:
        raise ValueError('Unexpected recovery population; investigate before sealing')
    remaining=[r for key,r in targets.items() if key not in seen]
    ROOT.mkdir();base.save(ROOT/'merged-financials.json',after['financialsBySymbol'])
    base.save(ROOT/'revenue-overlay-audit.json',audit);base.save(ROOT/'remaining-revenue-targets.json',remaining)
    plan=Path('docs/superpowers/plans/2026-11-01-v9-cause-unseen-validation.md')
    base.save(ROOT/'design-prespec.json',dict(text=plan.read_text(encoding='utf-8'),path=str(plan),sha256=base.sha(plan)))
    hashes=dict(old_protocol['inputHashes']);hashes.update(base.document_hashes(entries))
    files=[V2/'protocol.json',V2/'inputs.json',ROOT/'merged-financials.json',ROOT/'revenue-overlay-audit.json',ROOT/'remaining-revenue-targets.json',ROOT/'design-prespec.json',
           Path(__file__),Path('scripts/test_quant_v9_followup_replay.py'),Path('scripts/quant_v9_financial_followup.py'),Path('scripts/test_quant_v9_financial_followup.py'),*all_paths]
    hashes.update({str(path):base.sha(path) for path in files});base.utility.verify_sources(hashes)
    after['inputHashes']=hashes;base.save(ROOT/'inputs.json',after)
    protocol={**old_protocol,'inputsPath':str(ROOT/'inputs.json'),'inputsSha256':base.sha(ROOT/'inputs.json'),'inputHashes':hashes,
              'classification':'ORIGINAL_REVENUE_FOLLOWUP_INTERNAL_FULL_V9_NOT_UNSEEN',
              'recoverySummary':dict(priorInReplay=63,newlyIncluded=126,totalRecovered=189,remaining=14,filled=audit['filled']),
              'nonRevenueFinancialsAndNonfinancialInputsUnchangedFromV2':True,'unseenEvaluationPerformed':False}
    base.save(ROOT/'protocol.json',protocol)
    print(json.dumps(dict(protocolSha256=base.sha(ROOT/'protocol.json'),recoverySummary=protocol['recoverySummary'],sealedFiles=len(hashes))))


def compare():
    previous=base.ROOT
    try:base.ROOT=ROOT;base.compare()
    finally:base.ROOT=previous


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['prepare','compare']);args=parser.parse_args()
    prepare() if args.mode=='prepare' else compare()
