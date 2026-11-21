import unittest
from integrate_sp500_free_followup import identity_gate


class IdentityTests(unittest.TestCase):
    def test_reused_ticker_blocked(self):
        for s in ('INFO','LB','PARA','VMRK','SBNY'):
            self.assertFalse(identity_gate(s,dict(symbol=s,currency='USD',instrumentType='EQUITY'))[0])

    def test_whitelist_requires_name_and_type(self):
        m=dict(symbol='RDDT',currency='USD',instrumentType='EQUITY',longName='Reddit, Inc.')
        self.assertTrue(identity_gate('RDDT',m)[0])
        self.assertFalse(identity_gate('RDDT',dict(m,longName='Another company'))[0])
        self.assertFalse(identity_gate('RDDT',dict(m,instrumentType='ETF'))[0])


if __name__=='__main__':unittest.main()
