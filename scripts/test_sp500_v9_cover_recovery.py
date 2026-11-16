import unittest
from sp500_v9_cover_recovery import cover_facts


def document(value='12',unit='xbrli:shares',extra='',cik='1'):
    return f'''<html><xbrli:context id="c"><xbrli:entity><xbrli:identifier>{cik}</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:instant>2024-10-01</xbrli:instant></xbrli:period></xbrli:context>
    <xbrli:unit id="u"><xbrli:measure>{unit}</xbrli:measure></xbrli:unit>
    <ix:nonFraction name="dei:EntityCommonStockSharesOutstanding" contextRef="c" unitRef="u" scale="3">{value}</ix:nonFraction>{extra}</html>'''


class CoverRecoveryTest(unittest.TestCase):
    def test_real_xml_encoding_declaration(self):
        self.assertEqual(cover_facts('<?xml version="1.0" encoding="UTF-8"?>'+document(),'1')[0]['value'],12000)

    def test_scale_unit_and_issuer(self):
        self.assertEqual(cover_facts(document(),'0001')[0]['value'],12000)
        with self.assertRaises(ValueError):cover_facts(document(unit='iso4217:USD'),'1')
        with self.assertRaises(ValueError):cover_facts(document(cik='2'),'1')

    def test_duplicate_and_conflict(self):
        duplicate='<ix:nonFraction name="dei:EntityCommonStockSharesOutstanding" contextRef="c" unitRef="u" scale="3">12</ix:nonFraction>'
        self.assertEqual(len(cover_facts(document(extra=duplicate),'1')),1)
        with self.assertRaises(ValueError):cover_facts(document(extra=duplicate.replace('>12<','>13<')),'1')

    def test_duration_and_negative_disallowed(self):
        with self.assertRaises(ValueError):cover_facts(document(value='-1'),'1')
        with self.assertRaises(ValueError):cover_facts(document().replace('instant','endDate'),'1')


if __name__=='__main__':unittest.main()
