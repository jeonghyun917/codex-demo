"""Fail closed on stale publication or changes to already captured decisions."""


def validate_publication(candidate,previous):
    days={d['date']:d for d in candidate.get('days',[])}
    immutable=('recordedAt','nextOpen','trend','volatilityCap')
    fields=('symbol','rank','probability','selected','interest','reason')
    for old in previous.get('days',[]):
        new=days.get(old['date'])
        if new is None:raise ValueError('PUBLISHED_DECISION_REMOVED: '+old['date'])
        if any(new.get(k)!=old.get(k) for k in immutable):raise ValueError('CAPTURED_DECISION_CHANGED')
        identity=lambda day:[tuple(r.get(k) for k in fields) for r in day.get('predictions',[])]
        if identity(new)!=identity(old):raise ValueError('CAPTURED_PREDICTIONS_CHANGED')
        a=old.get('tracking',{});b=new.get('tracking',{})
        if a.get('available') and (not b.get('available') or b.get('asof','')<a.get('asof','')):
            raise ValueError('TRACKING_REGRESSION: '+old['date'])
        if a.get('available') and b.get('asof')==a.get('asof') and b.get('rows')!=a.get('rows'):
            raise ValueError('SAME_DATE_TRACKING_REPLACEMENT_REQUIRES_REVIEW')
    accounts={a['feeKey']:a for a in candidate.get('accounts',[])}
    for old in previous.get('accounts',[]):
        new=accounts.get(old['feeKey'])
        if new is None or new.get('end','')<old.get('end','') or new.get('trades',0)<old.get('trades',0):
            raise ValueError('ACCOUNT_PROJECTION_REGRESSION')
