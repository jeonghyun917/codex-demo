"""Primary inline XBRL scaling, contexts and source-match regression tests."""
import unittest
from decimal import Decimal

import verify_quant_free_primary as verify


class PrimaryEvidenceTests(unittest.TestCase):
    def test_millions_and_negative_sign_are_applied_not_inferred_from_unit(self):
        self.assertEqual(verify.inline_value('6,750', {'scale': '6', 'format': 'ixt:num-dot-decimal'}), Decimal('6750000000'))
        self.assertEqual(verify.inline_value('265', {'scale': '6', 'sign': '-'}), Decimal('-265000000'))
        self.assertEqual(verify.inline_value('0.48', {}), Decimal('0.48'))

    def test_unsupported_transform_and_nil_are_not_silently_treated_as_zero(self):
        with self.assertRaises(ValueError):
            verify.inline_value('one', {'format': 'ixt-sec:numwordsen'})
        with self.assertRaises(ValueError):
            verify.inline_value('', {'xsi:nil': 'true'})

    def test_dimensioned_context_is_not_matched_to_default_companyfacts(self):
        html = '''<html><xbrli:context id="c1"><xbrli:entity><xbrli:identifier>0001709048</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:startdate>2025-07-01</xbrli:startdate><xbrli:enddate>2025-09-30</xbrli:enddate></xbrli:period><xbrldi:explicitmember dimension="d">segment</xbrldi:explicitmember></xbrli:context><xbrli:unit id="usd"><xbrli:measure>iso4217:USD</xbrli:measure></xbrli:unit><ix:nonfraction name="ifrs-full:Revenue" contextref="c1" unitref="usd" scale="6">1,688</ix:nonfraction></html>'''
        rows, _ = verify.parse_inline(html)
        self.assertEqual(rows, [])

    def test_exact_source_period_and_currency_retained(self):
        html = '''<html><xbrli:context id="c1"><xbrli:entity><xbrli:identifier>0001709048</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:startdate>2025-07-01</xbrli:startdate><xbrli:enddate>2025-09-30</xbrli:enddate></xbrli:period></xbrli:context><xbrli:unit id="usd"><xbrli:measure>iso4217:USD</xbrli:measure></xbrli:unit><ix:nonfraction name="ifrs-full:Revenue" contextref="c1" unitref="usd" scale="6">1,688</ix:nonfraction></html>'''
        rows, _ = verify.parse_inline(html)
        self.assertEqual(rows[0]['value'], '1688000000')
        self.assertEqual(rows[0]['start'], '2025-07-01')
        self.assertEqual(rows[0]['end'], '2025-09-30')
        self.assertEqual(rows[0]['unit'], 'USD')
        self.assertEqual(rows[0]['cik'], 1709048)


if __name__ == '__main__':
    unittest.main()
