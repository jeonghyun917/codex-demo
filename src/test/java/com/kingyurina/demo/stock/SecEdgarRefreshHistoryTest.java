package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.ObjectMapper;

class SecEdgarRefreshHistoryTest {
    @Test void aPartialRefreshCannotErasePreviouslyPersistedCoherentEps() {
        var properties=new SecEdgarProperties("https://unused.invalid","https://unused.invalid","test",168,0,0,false);
        var client=mock(SecEdgarClientService.class);var companies=mock(SecCompanyMapper.class);var facts=mock(SecCompanyFactMapper.class);var standards=mock(SecFinancialStandardMapper.class);
        when(client.companyTickers()).thenReturn(new FinnhubResponse(200,"{\"0\":{\"cik_str\":123,\"ticker\":\"TEST\",\"title\":\"Test\"}}",null));
        when(client.companyFacts(anyString())).thenReturn(new FinnhubResponse(200,"""
                {"facts":{"us-gaap":{"Revenues":{"units":{"USD":[{
                  "start":"2024-01-01","end":"2024-12-31","val":1000,"accn":"new-2025",
                  "fy":2024,"fp":"FY","form":"10-K","filed":"2025-02-01"
                }]}}}}}
                """,null));
        var company=new SecCompany();company.setSymbol("TEST");company.setCik("0000000123");when(companies.findBySymbol("TEST")).thenReturn(company);
        when(companies.findSymbolsWithStaleFacts(anyList(),anyInt(),anyInt())).thenReturn(List.of("TEST"));
        var stored=new ArrayList<>(List.of(old("Revenues","USD","400"),old("EarningsPerShareDiluted","USD/shares","3.82")));
        when(facts.findBySymbol("TEST")).thenAnswer(x->new ArrayList<>(stored));
        doAnswer(x->{stored.addAll(x.<List<SecCompanyFact>>getArgument(0));return null;}).when(facts).upsertBatch(anyList());
        var persisted=new ArrayList<SecFinancialStandard>();
        doAnswer(x->{persisted.clear();return null;}).when(standards).deleteBySymbol("TEST");
        doAnswer(x->{persisted.addAll(x.<List<SecFinancialStandard>>getArgument(0));return null;}).when(standards).upsertBatch(anyList());
        when(standards.findSymbolsMissingStandard(anyList(),anyInt())).thenReturn(List.of());
        var beans=new StaticListableBeanFactory();beans.addBean("facts",facts);beans.addBean("standards",standards);beans.addBean("companies",companies);
        var mapper=new ObjectMapper();var normalizer=new SecFinancialStandardService(beans.getBeanProvider(SecCompanyFactMapper.class),beans.getBeanProvider(SecFinancialStandardMapper.class),mapper);
        var sync=new SecEdgarService(properties,client,mock(StockCacheService.class),mapper,normalizer,beans.getBeanProvider(SecCompanyMapper.class),beans.getBeanProvider(SecCompanyFactMapper.class));
        sync.syncSymbols(List.of("TEST"),1);
        var historical=persisted.stream().filter(r->r.getAccessionNumber().equals("old-2016")).findFirst().orElse(null);
        assertNotNull(historical,"Refresh erased the previously persisted annual statement");
        assertEquals(new BigDecimal("3.82"),historical.getEpsDiluted());
        assertTrue(persisted.stream().anyMatch(r->r.getAccessionNumber().equals("new-2025")));
    }
    private static SecCompanyFact old(String concept,String unit,String value) {
        var f=new SecCompanyFact();f.setSymbol("TEST");f.setCik("0000000123");f.setTaxonomy("us-gaap");f.setConcept(concept);f.setUnit(unit);f.setFiscalYear(2015);f.setFiscalPeriod("FY");f.setForm("10-K");f.setFiledAt(LocalDate.parse("2016-02-01"));f.setStartDate(LocalDate.parse("2015-01-01"));f.setEndDate(LocalDate.parse("2015-12-31"));f.setAccessionNumber("old-2016");f.setValue(new BigDecimal(value));return f;
    }
}
