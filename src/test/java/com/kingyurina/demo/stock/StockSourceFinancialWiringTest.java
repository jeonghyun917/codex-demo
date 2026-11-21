package com.kingyurina.demo.stock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

class StockSourceFinancialWiringTest {
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value){
        var p=(ObjectProvider<T>)mock(ObjectProvider.class);when(p.getIfAvailable()).thenReturn(value);return p;
    }
    @Test void liveCacheQuarterActuallyReadsAdmittedBridgeInsteadOfStoppingAtLegacyMapper(){
        var legacy=mock(SecFinancialStandardMapper.class);
        var cache=new StockCacheService(provider(null),provider(null),provider(null),provider(null),provider(null),provider(null),
            provider(null),provider(null),provider(null),provider(legacy),provider(null),provider(null),provider(null));
        var sourceMapper=mock(StockSourceFinancialMapper.class);
        String payload="""
            {"canonicalIdentitySha256":"%s","sourceForm":"ISSUER_RELEASE",
             "canonicalIdentity":{"symbol":"AMGN","cik":"0000318154","sourceKind":"OFFICIAL_ISSUER_EARNINGS_RELEASE",
              "startDate":"2019-10-01","endDate":"2019-12-31","publicationDate":"2020-01-30","fiscalPeriod":"Q4","currency":"USD"},
             "values":{"revenue":"50"},"units":{"revenue":"USD"}}
            """.formatted("a".repeat(64));
        when(sourceMapper.findBySymbol("AMGN",StockSourceFinancialService.VERSION)).thenReturn(List.of(
            new StockSourceFinancialDocument(StockSourceFinancialService.VERSION,"a".repeat(64),"AMGN",LocalDate.parse("2020-01-30"),
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1),payload)));
        cache.setSourceFinancialService(new StockSourceFinancialService(provider(sourceMapper),new ObjectMapper(),true));
        assertEquals(new BigDecimal("50"),cache.findLatestSecQuarterFinancial("AMGN").getRevenue());
        assertNull(cache.findLatestSecAnnualFinancial("AMGN"));
        assertNull(cache.findPreviousSecQuarterFinancial("AMGN",LocalDate.parse("2020-06-30")));
    }
    @Test void actualHistoricalSnapshotCarriesDistinctVersionAndSelectedSourceProof() throws Exception {
        var mapper=mock(HistoricalBacktestSeedMapper.class);var sourceMapper=mock(StockSourceFinancialMapper.class);
        LocalDate day=LocalDate.parse("2020-02-03");var candles=new ArrayList<StockCandleDaily>();
        for(int i=0;i<80;i++){var c=new StockCandleDaily();c.setSymbol("AMGN");c.setTradeDate(day.minusDays(i));
            c.setClosePrice(BigDecimal.TEN);c.setHighPrice(BigDecimal.TEN);c.setLowPrice(BigDecimal.TEN);c.setOpenPrice(BigDecimal.TEN);c.setVolume(1000L);candles.add(c);}
        when(mapper.findCandlesOnOrBefore("AMGN",day,260)).thenReturn(candles);
        String payload="""
            {"canonicalIdentitySha256":"%s","sourceForm":"ISSUER_RELEASE",
             "canonicalIdentity":{"symbol":"AMGN","cik":"0000318154","sourceKind":"OFFICIAL_ISSUER_EARNINGS_RELEASE",
              "startDate":"2019-10-01","endDate":"2019-12-31","publicationDate":"2020-01-30","fiscalPeriod":"Q4","currency":"USD"},
             "values":{"revenue":"50"},"units":{"revenue":"USD"}}
            """.formatted("a".repeat(64));
        when(sourceMapper.findBySymbol("AMGN",StockSourceFinancialService.VERSION)).thenReturn(List.of(new StockSourceFinancialDocument(
            StockSourceFinancialService.VERSION,"a".repeat(64),"AMGN",LocalDate.parse("2020-01-30"),LocalDateTime.now(ZoneOffset.UTC),payload)));
        var service=new HistoricalBacktestSeedService(provider(mapper),provider(null),provider(null),mock(StockBacktestService.class),new ObjectMapper());
        service.setSourceFinancialService(new StockSourceFinancialService(provider(sourceMapper),new ObjectMapper(),true));
        var method=HistoricalBacktestSeedService.class.getDeclaredMethod("buildSnapshot",HistoricalBacktestSeedMapper.class,String.class,LocalDate.class);
        method.setAccessible(true);var result=(StockSignalSnapshot)method.invoke(service,mapper,"AMGN",day);
        assertNotEquals(HistoricalBacktestSeedService.SIGNAL_VERSION,result.getSignalVersion());
        assertTrue(result.getRawJson().contains(StockSourceFinancialService.VERSION));
        assertTrue(result.getRawJson().contains("RECONSTRUCTED_NOT_HISTORICAL_SYSTEM_PIT"));
        assertTrue(result.getRawJson().contains("selectedQuarter"));
        var db=new org.h2.jdbcx.JdbcDataSource();db.setURL("jdbc:h2:mem:"+UUID.randomUUID());
        try(var connection=db.getConnection();var statement=connection.createStatement()){
            statement.execute("CREATE TABLE feature_snapshot(signal_version VARCHAR(20))");
            try(var insert=connection.prepareStatement("INSERT INTO feature_snapshot VALUES(?)")){
                insert.setString(1,result.getSignalVersion());assertEquals(1,insert.executeUpdate());
            }
        }
    }
}
