"""Prespecified source-complete second replay; V1 remains an immutable diagnostic."""
import argparse
import copy
import json
from pathlib import Path
import quant_full_financial_study as base
import quant_balance_completion as balance

ROOT=Path('tmp/quant-full-financial-study-20261101-v2')
V1_PROTOCOL_SHA='d5ca47928477d91f14393cda9cc286b10a9b3659e780efccdf01e4dbcfcbe43d'
PRESPEC=base.ROOT/'balance-completion-prespec.json'

def assert_completion_only(before,after):
    if before.keys()!=after.keys():raise ValueError('Issuer map changed')
    allowed=set(balance.PRIORITIES)|{'rawJson'}
    for symbol in before:
        old={base.key(r):r for r in before[symbol]};fresh={base.key(r):r for r in after[symbol]}
        if len(old)!=len(before[symbol]) or len(fresh)!=len(after[symbol]) or old.keys()!=fresh.keys():raise ValueError('Physical source rows changed')
        for key,row in old.items():
            if {k:v for k,v in row.items() if k not in allowed}!={k:v for k,v in fresh[key].items() if k not in allowed}:raise ValueError('Non-balance field changed')
            old_evidence=json.loads(row.get('rawJson') or '{}').get('selectedFieldEvidence',{})
            new_evidence=json.loads(fresh[key].get('rawJson') or '{}').get('selectedFieldEvidence',{})
            for field in base.PRIORITY:
                if old_evidence.get(field)!=new_evidence.get(field):raise ValueError('Income/primary proof changed')

def assert_input_parity(before,after):
    allowed={'financialsBySymbol','inputHashes'}
    if {k:v for k,v in before.items() if k not in allowed}!={k:v for k,v in after.items() if k not in allowed}:raise ValueError('Nonfinancial inputs or schedules changed')

def prepare():
    if ROOT.exists():raise ValueError('Never overwrite a study')
    if base.sha(base.ROOT/'protocol.json')!=V1_PROTOCOL_SHA:raise ValueError('Original sealed protocol drift')
    protocol=base.load(base.ROOT/'protocol.json');base.utility.verify_sources(protocol['inputHashes'])
    if base.sha(base.ROOT/'inputs.json')!=protocol['inputsSha256']:raise ValueError('V1 inputs drift')
    prespec=base.load(PRESPEC)
    if prespec['performanceViewed'] is not False or prespec['newStudyRoot']!=str(ROOT).replace('\\','/'):raise ValueError('Prespecified second study required')
    before=base.load(base.ROOT/'inputs.json');value=copy.deepcopy(before);archives={}
    for record in base.load(base.PACK/'source-manifest.json')['records']:archives[(record['symbol'],base.utility.cik(record['cik']))]=base.load(record['archivePath'])
    completed,audit=balance.complete_financials(before['financialsBySymbol'],archives);assert_completion_only(before['financialsBySymbol'],completed)
    value['financialsBySymbol']=completed;assert_input_parity(before,value)
    ROOT.mkdir();base.save(ROOT/'merged-financials.json',completed);base.save(ROOT/'balance-audit.json',audit)
    hashes=dict(protocol['inputHashes']);paths=[base.ROOT/'inputs.json',base.ROOT/'protocol.json',PRESPEC,ROOT/'merged-financials.json',ROOT/'balance-audit.json']
    paths += [Path(p) for p in ['scripts/quant_full_financial_complete.py','scripts/test_quant_full_financial_complete.py','scripts/quant_balance_completion.py','scripts/test_quant_balance_completion.py']]
    hashes.update({str(p):base.sha(p) for p in paths});value['inputHashes']=hashes;base.utility.verify_sources(hashes)
    base.save(ROOT/'inputs.json',value)
    sealed={**protocol,'inputsPath':str(ROOT/'inputs.json'),'inputsSha256':base.sha(ROOT/'inputs.json'),'inputHashes':hashes,'classification':'PRESPECIFIED_BALANCE_COMPLETE_INTERNAL_FULL_V9_REPLAY_NOT_NEW_HOLDOUT',
            'prespecSha256':base.sha(PRESPEC),'comparedAgainst':'Same EPS-recovered V9 baseline; V1 preserved as income-only input diagnostic',
            'balanceSummary':{k:v for k,v in audit.items() if k!='changes'},'nonfinancialAndIncomeFieldsUnchangedFromV1':True}
    base.save(ROOT/'protocol.json',sealed)
    print(json.dumps(dict(protocolSha256=base.sha(ROOT/'protocol.json'),balanceSummary=sealed['balanceSummary'],rows=sum(map(len,completed.values())))))

def compare():
    # The shared comparison consumes an explicit immutable study root in this CLI process.
    previous=base.ROOT
    try:base.ROOT=ROOT;base.compare()
    finally:base.ROOT=previous

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['prepare','compare']);args=parser.parse_args();prepare() if args.mode=='prepare' else compare()
