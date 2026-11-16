package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

class StockSourceFinancialServiceTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void dateOnlyPublicationIsUnavailableSameDayButReconstructionCanUseNextDay(){
        var service=service(true,List.of(document("AMGN","2013-10-01","2013-12-31","2014-01-28",null,"5011000000")));
        assertNull(service.resolveQuarter("AMGN",LocalDate.parse("2014-01-28"),null,null,true));
        var value=service.resolveQuarter("AMGN",LocalDate.parse("2014-01-29"),null,null,true);
        assertEquals(new BigDecimal("5011000000"),value.getRevenue());
        assertNull(value.getAccessionNumber());assertNull(value.getFiledAt());
        assertEquals("ISSUER_RELEASE",value.getForm());
        assertTrue(value.getRawJson().contains("RECONSTRUCTED_NOT_HISTORICAL_SYSTEM_PIT"));
        assertNull(service.resolveQuarter("AMGN",LocalDate.parse("2014-01-29"),null,null,false));
    }

    @Test void exactPeriodNullFillIsDetachedAndDoesNotReplaceNonNullFacts(){
        var old=legacy("AMGN","2013-10-01","2013-12-31");old.setNetIncome(new BigDecimal("45"));
        var service=service(true,List.of(document("AMGN","2013-10-01","2013-12-31","2014-01-28",null,"5011000000")));
        var result=service.resolveQuarter("AMGN",LocalDate.parse("2014-02-01"),null,old,true);
        assertNotSame(old,result);assertNull(old.getRevenue());assertEquals(new BigDecimal("45"),result.getNetIncome());
        assertEquals(new BigDecimal("5011000000"),result.getRevenue());assertEquals(old.getAccessionNumber(),result.getAccessionNumber());
        old.setRevenue(new BigDecimal("99"));
        assertSame(old,service.resolveQuarter("AMGN",LocalDate.parse("2014-02-01"),null,old,true));
    }

    @Test void gsOriginalPeriodIsAWholeDistinctStatementNeverBorrowingLegacyAssetsOrIncome(){
        var old=legacy("GS","2013-03-31","2013-06-30");old.setAccessionNumber("0001193125-13-324664");
        old.setAssets(new BigDecimal("999"));old.setOperatingIncome(new BigDecimal("88"));
        var service=service(true,List.of(document("GS","2013-04-01","2013-06-30","2013-08-08","0001193125-13-324664","8612000000")));
        var result=service.resolveQuarter("GS",LocalDate.parse("2013-08-09"),null,old,true);
        assertEquals(LocalDate.parse("2013-04-01"),result.getStartDate());assertEquals(91,result.getPeriodDays());
        assertNull(result.getAssets());assertNull(result.getOperatingIncome());
        assertEquals(LocalDate.parse("2013-03-31"),old.getStartDate());assertEquals(new BigDecimal("999"),old.getAssets());
    }

    @Test void olderSourcesOtherIssuersAndDisabledModeLeaveLegacyUntouched(){
        var doc=document("AMGN","2013-10-01","2013-12-31","2014-01-28",null,"5011000000");
        var newer=legacy("AMGN","2026-04-01","2026-06-30");
        assertSame(newer,service(true,List.of(doc)).resolveQuarter("AMGN",LocalDate.parse("2026-11-05"),null,newer,false));
        assertSame(newer,service(false,List.of(doc)).resolveQuarter("AMGN",LocalDate.parse("2014-02-01"),null,newer,true));
        var different=legacy("AMGN","2013-10-01","2013-12-31");different.setCik("0000886982");
        assertSame(different,service(true,List.of(doc)).resolveQuarter("AMGN",LocalDate.parse("2014-02-01"),null,different,true));
    }

    @Test void previousQuarterSelectionUsesExactAdjacentEndAndRetainsObservedAdmissionCutoff(){
        var doc=document("AMGN","2013-10-01","2013-12-31","2014-01-28",null,"5011000000");
        var service=service(true,List.of(doc));
        assertNotNull(service.resolveQuarter("AMGN",LocalDate.parse("2014-05-01"),LocalDate.parse("2014-03-31"),null,true));
        assertNull(service.resolveQuarter("AMGN",LocalDate.parse("2014-08-01"),LocalDate.parse("2014-06-30"),null,true));
        assertNotNull(service.resolveQuarter("AMGN",LocalDate.now(ZoneOffset.UTC).plusDays(1),null,null,false));
    }

    @Test void unknownUnitsAndDuplicateSourcePeriodsFailClosed(){
        var doc=document("AMGN","2013-10-01","2013-12-31","2014-01-28",null,"5011000000");
        var bad=new StockSourceFinancialDocument(doc.packVersion(),doc.identitySha256(),doc.symbol(),doc.publicationDate(),
            doc.admittedAtUtc(),doc.payloadJson().replace("\"revenue\":\"USD\"","\"revenue\":\"EUR\""));
        assertThrows(IllegalArgumentException.class,()->service(true,List.of(bad)).resolveQuarter("AMGN",LocalDate.parse("2014-02-01"),null,null,true));
        assertThrows(IllegalArgumentException.class,()->service(true,List.of(doc,doc)).resolveQuarter("AMGN",LocalDate.parse("2014-02-01"),null,null,true));
    }

    @SuppressWarnings("unchecked") private StockSourceFinancialService service(boolean enabled,List<StockSourceFinancialDocument> docs){
        var provider=(ObjectProvider<StockSourceFinancialMapper>)mock(ObjectProvider.class);var mapper=mock(StockSourceFinancialMapper.class);
        when(provider.getIfAvailable()).thenReturn(mapper);when(mapper.findBySymbol(anyString(),anyString())).thenReturn(docs);
        return new StockSourceFinancialService(provider,json,enabled);
    }
    private StockSourceFinancialDocument document(String symbol,String start,String end,String published,String accession,String revenue){
        try{
            var identity=new LinkedHashMap<String,Object>();identity.put("symbol",symbol);identity.put("cik",symbol.equals("GS")?"0000886982":"0000318154");
            identity.put("sourceKind",accession==null?"OFFICIAL_ISSUER_EARNINGS_RELEASE":"SEC_ORIGINAL_SAME_FILING_XBRL");
            identity.put("startDate",start);identity.put("endDate",end);identity.put("publicationDate",published);identity.put("currency","USD");
            identity.put("fiscalPeriod",symbol.equals("GS")?"Q2":"Q4");identity.put("secAccessionNumber",accession);identity.put("secFiledDate",accession==null?null:published);
            var body=new LinkedHashMap<String,Object>();body.put("canonicalIdentity",identity);body.put("values",Map.of("revenue",revenue));
            body.put("units",Map.of("revenue","USD"));body.put("sourceForm",accession==null?"ISSUER_RELEASE":"10-Q");
            body.put("canonicalIdentitySha256","a".repeat(64));
            return new StockSourceFinancialDocument(StockSourceFinancialService.VERSION,"a".repeat(64),symbol,LocalDate.parse(published),
                LocalDateTime.now(ZoneOffset.UTC),json.writeValueAsString(body));
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private SecFinancialStandard legacy(String symbol,String start,String end){
        var value=new SecFinancialStandard();value.setSymbol(symbol);value.setCik(symbol.equals("GS")?"0000886982":"0000318154");
        value.setStartDate(LocalDate.parse(start));value.setEndDate(LocalDate.parse(end));value.setFiscalPeriod(symbol.equals("GS")?"Q2":"Q4");
        value.setCurrency("USD");value.setAccessionNumber("0000000000-14-000001");value.setForm("10-Q");return value;
    }
}
