package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.ObjectMapper;

class StockPriceIntegrityTest {
    @Test
    void legacySignalIsUnavailableUntilRecalculatedWithCorrectedPricePolicy() {
        StockCacheService cache = mock(StockCacheService.class);
        StockSignalLatest stored = new StockSignalLatest();
        stored.setSymbol("APH"); stored.setSignalVersion("quant-v2"); stored.setIntegratedScore(70);
        stored.setCalculatedAt(LocalDateTime.now());
        when(cache.findLatestSignal("APH")).thenReturn(stored);
        var service = new StockSignalService(cache, mock(StockDataQualityService.class), new ObjectMapper());
        assertNull(service.buildStored("APH"));
        stored.setSignalVersion("q3-price-20261017");
        assertNull(service.buildStored("APH"));
        stored.setSignalVersion("q4-fin-20261031");
        assertNotNull(service.buildStored("APH"));
    }
    @Test
    void databaseSixDecimalRoundTripDoesNotQuarantineAnUnchangedProviderPrice() {
        StockCandleMapper mapper = mock(StockCandleMapper.class);
        when(mapper.findRecentBySymbol(eq("APH"), anyInt())).thenReturn(List.of(
                candle("2026-10-14", "78.123457", "80.123457", "yahoo-chart")));
        var factory = new StaticListableBeanFactory(); factory.addBean("candles", mapper);
        var cache = new StockCacheService(null, null, null, null, null, null,
                factory.getBeanProvider(StockCandleMapper.class), null, null, null, null, null, null);
        var result = cache.saveCandles(List.of(candle("2026-10-14", "78.12345678", "80.12345678", "yahoo-chart"),
                candle("2026-10-15", "79.2", "80", "yahoo-chart")));
        assertEquals(2, result.canonicalRows()); assertEquals("PERSISTED", result.status());
    }

    @Test
    void missingRequiredSourcePricesCannotRollbackOtherwiseValidRows() {
        StockCandleMapper mapper = mock(StockCandleMapper.class);
        var factory = new StaticListableBeanFactory(); factory.addBean("candles", mapper);
        var cache = new StockCacheService(null, null, null, null, null, null,
                factory.getBeanProvider(StockCandleMapper.class), null, null, null, null, null, null);
        var good = candle("2026-10-15", "50", "51", "yahoo-chart");
        var bad = candle("2026-10-14", "50", "51", "yahoo-chart"); bad.setOpenPrice(null);
        var result = cache.saveCandles(List.of(good, bad));
        assertEquals(1, result.sourceRows()); assertEquals(1, result.canonicalRows());
        verify(mapper).upsertSourceBatch(List.of(good));
    }
    @Test
    void impossibleQuoteRangeDoesNotOverwriteGoodStoredQuote() {
        StockCacheService cache = mock(StockCacheService.class);
        StockQuoteCache old = quote("100", "99", "2026-07-01T12:00:00");
        when(cache.findQuote("APH")).thenReturn(old);
        FinnhubClientService client = mock(FinnhubClientService.class);
        long timestamp = java.time.Instant.now().minusSeconds(60).getEpochSecond();
        when(client.get(anyString(), anyMap())).thenReturn(new FinnhubResponse(200,
                "{\"c\":100,\"pc\":99,\"o\":100,\"h\":90,\"l\":110,\"t\":" + timestamp + "}", null));
        var result = analysis(cache, client, "configured").refreshDashboard("APH");
        assertSame(old, result.quote()); verify(cache, never()).saveQuote(any());
    }
    @Test
    void listMapperUsesSamePricePolicyAndPreservesSingleCandleNullPreviousClose() {
        IndexConstituentMapper mapper = mock(IndexConstituentMapper.class, CALLS_REAL_METHODS);
        StockMarketRow row = new StockMarketRow(); row.setSymbol("APH");
        row.setCurrentPrice(new BigDecimal("157.43")); row.setPreviousClose(new BigDecimal("156"));
        row.setQuoteTime(LocalDateTime.parse("2026-07-24T16:00:00"));
        row.setCandleDate(LocalDate.parse("2026-10-15")); row.setCandleClose(new BigDecimal("81.489998"));
        row.setCandleHigh(new BigDecimal("89.93")); row.setCandleSource("yahoo-chart"); row.setCandleSourceCount(1);
        row.setThreeMonthHigh(new BigDecimal("89.93"));
        when(mapper.findRawMarketRows("SP500")).thenReturn(List.of(row));
        var selected = mapper.findMarketRows("SP500").getFirst();
        assertEquals(new BigDecimal("81.489998"), selected.getCurrentPrice());
        assertNull(selected.getPreviousClose()); assertEquals(new BigDecimal("89.93"), selected.getThreeMonthHigh());
        assertEquals("CLOSE", selected.getPriceKind()); assertEquals("yahoo-chart", selected.getPriceSource());
    }

    @Test
    void newerQuoteCannotBorrowCandleHighWithoutAdjustmentProvenance() {
        IndexConstituentMapper mapper = mock(IndexConstituentMapper.class, CALLS_REAL_METHODS);
        StockMarketRow row = new StockMarketRow(); row.setSymbol("CRM");
        row.setCurrentPrice(new BigDecimal("240.70")); row.setPreviousClose(new BigDecimal("246.740005"));
        row.setQuoteTime(LocalDateTime.parse("2026-10-17T03:00:00"));
        row.setCandleDate(LocalDate.parse("2026-10-15")); row.setCandleClose(new BigDecimal("246.740005"));
        row.setCandleSource("yahoo-chart"); row.setCandleSourceCount(1); row.setThreeMonthHigh(new BigDecimal("260"));
        when(mapper.findRawMarketRowsBySymbols(List.of("CRM"))).thenReturn(List.of(row));
        var selected = mapper.findMarketRowsBySymbols(List.of("CRM")).getFirst();
        assertEquals(new BigDecimal("240.70"), selected.getCurrentPrice());
        assertEquals(new BigDecimal("246.740005"), selected.getPreviousClose()); assertNull(selected.getThreeMonthHigh());
        assertEquals("QUOTE", selected.getPriceKind());
    }

    @Test
    void signalUsesSelectedCloseAndDoesNotMixStaleMetricRange() {
        StockCacheService cache = mock(StockCacheService.class);
        when(cache.findQuote("APH")).thenReturn(quote("160", "80", "2026-07-24T16:00:00"));
        when(cache.findRecentCandles(eq("APH"), anyInt())).thenReturn(List.of(
                candle("2026-10-15", "81", "90", "yahoo-chart"), candle("2026-10-14", "82", "83", "yahoo-chart")));
        var factory = new StaticListableBeanFactory();
        var quality = new StockDataQualityService(factory.getBeanProvider(StockDataQualityMapper.class), cache, new ObjectMapper());
        StockSignalLatest signal = new StockSignalService(cache, quality, new ObjectMapper()).buildLatest("APH");
        assertTrue(signal.getSignalVersion().length() <= 20,
                "Signal version must fit both existing VARCHAR(20) persistence columns");
        assertTrue(signal.getSourceFreshnessJson().contains("yahoo-chart"));
        assertTrue(signal.getSourceFreshnessJson().contains("CLOSE"));
        assertTrue(signal.getCardsJson().contains("-1.22%"), signal.getCardsJson());
        assertFalse(signal.getCardsJson().contains("100%"));
    }
    @Test
    void successfulEmptyPayloadNeverOverwritesLastGoodQuoteProfileOrMetric() {
        StockCacheService cache = mock(StockCacheService.class);
        StockQuoteCache oldQuote = quote("100", "99", "2026-07-01T12:00:00");
        CompanyProfile oldProfile = new CompanyProfile(); oldProfile.setSymbol("APH"); oldProfile.setName("Amphenol");
        StockMetricSnapshot oldMetric = new StockMetricSnapshot(); oldMetric.setSymbol("APH"); oldMetric.setRoeTtm(BigDecimal.TEN);
        when(cache.findQuote("APH")).thenReturn(oldQuote);
        when(cache.findProfile("APH")).thenReturn(oldProfile);
        when(cache.findLatestMetric("APH")).thenReturn(oldMetric);
        FinnhubClientService client = mock(FinnhubClientService.class);
        when(client.get(anyString(), anyMap())).thenReturn(new FinnhubResponse(200, "{}", null));
        var result = analysis(cache, client, "configured").refreshDashboard("APH");
        assertSame(oldQuote, result.quote()); assertSame(oldProfile, result.profile()); assertSame(oldMetric, result.metric());
        assertTrue(result.quoteCacheHit()); assertTrue(result.profileCacheHit()); assertTrue(result.metricCacheHit());
        verify(cache, never()).saveQuote(any()); verify(cache, never()).saveProfile(any()); verify(cache, never()).saveMetric(any());
    }

    @Test
    void detailInfoAssessmentNeverPersistsQuality() {
        StockCacheService cache = mock(StockCacheService.class);
        StockDataQualityMapper mapper = mock(StockDataQualityMapper.class);
        var factory = new StaticListableBeanFactory(); factory.addBean("quality", mapper);
        var quality = new StockDataQualityService(factory.getBeanProvider(StockDataQualityMapper.class), cache, new ObjectMapper());
        var info = new StockInfoViewService(cache, quality, new ObjectMapper());
        info.build(analysis(cache, mock(FinnhubClientService.class), "").dashboard("APH"));
        verify(mapper, never()).upsert(any());
    }

    @Test
    void sameSourceCorporateActionRestatementIsQuarantinedWithoutGuessingASplit() {
        StockCandleMapper mapper = mock(StockCandleMapper.class);
        when(mapper.findRecentBySymbol(eq("APH"), anyInt())).thenReturn(List.of(
                candle("2026-10-14", "100", "102", "yahoo-chart")));
        var factory = new StaticListableBeanFactory(); factory.addBean("candles", mapper);
        var cache = new StockCacheService(null, null, null, null, null, null,
                factory.getBeanProvider(StockCandleMapper.class), null, null, null, null, null, null);
        var result = cache.saveCandles(List.of(candle("2026-10-14", "50", "51", "yahoo-chart"),
                candle("2026-10-15", "51", "52", "yahoo-chart")));
        assertEquals(0, result.canonicalRows()); assertEquals(2, result.rejectedRows());
        assertEquals("QUARANTINED_BASIS_CHANGE", result.status());
        verify(mapper, never()).upsertBatch(anyList());
    }

    @Test
    void coherentNewCandlesReturnValidatedCanonicalCountAndDate() {
        StockCandleMapper mapper = mock(StockCandleMapper.class);
        var factory = new StaticListableBeanFactory(); factory.addBean("candles", mapper);
        var cache = new StockCacheService(null, null, null, null, null, null,
                factory.getBeanProvider(StockCandleMapper.class), null, null, null, null, null, null);
        var good = candle("2026-10-15", "50", "51", "yahoo-chart");
        var bad = candle("2026-10-14", "100", "99", "yahoo-chart");
        var result = cache.saveCandles(List.of(good, bad));
        assertEquals(1, result.canonicalRows()); assertEquals(1, result.rejectedRows());
        assertEquals(LocalDate.of(2026, 10, 15), result.latestMarketDate());
        verify(mapper).upsertBatch(List.of(good));
    }
    @Test
    void detailWithoutApiKeyUsesStoredAdjustedCloseInsteadOfStaleAphQuote() {
        StockCacheService cache = mock(StockCacheService.class);
        when(cache.findQuote("APH")).thenReturn(quote("157.43", "156", "2026-07-24T16:00:00"));
        when(cache.findRecentCandles(eq("APH"), anyInt())).thenReturn(List.of(
                candle("2026-10-15", "81.489998", "89.93", "yahoo-chart"),
                candle("2026-10-14", "82", "83", "yahoo-chart")));
        FinnhubClientService client = mock(FinnhubClientService.class);
        StockDashboard result = analysis(cache, client, "").dashboard("APH");
        assertNotNull(result.quote(), "Stored data must not depend on API credentials");
        assertEquals(new BigDecimal("81.489998"), result.quote().getCurrentPrice());
        assertEquals(new BigDecimal("82"), result.quote().getPreviousClose());
        verifyNoInteractions(client);
    }

    @Test
    void newerCrmQuoteWinsAsAWholeTupleWithoutCallingProvider() {
        StockCacheService cache = mock(StockCacheService.class);
        when(cache.findQuote("CRM")).thenReturn(quote("240.70", "246.740005", "2026-10-17T03:00:00"));
        when(cache.findRecentCandles(eq("CRM"), anyInt())).thenReturn(List.of(
                candle("2026-10-15", "246.740005", "250", "yahoo-chart")));
        FinnhubClientService client = mock(FinnhubClientService.class);
        when(client.get(anyString(), anyMap())).thenReturn(new FinnhubResponse(503, "{}", "unavailable"));
        StockDashboard result = analysis(cache, client, "configured").dashboard("CRM");
        assertEquals(new BigDecimal("240.70"), result.quote().getCurrentPrice());
        assertEquals(new BigDecimal("246.740005"), result.quote().getPreviousClose());
        verifyNoInteractions(client);
    }

    @Test
    void singleCandleDoesNotBorrowPreviousCloseFromStaleQuote() {
        StockCacheService cache = mock(StockCacheService.class);
        when(cache.findQuote("APH")).thenReturn(quote("160", "159", "2026-07-24T16:00:00"));
        when(cache.findRecentCandles(eq("APH"), anyInt())).thenReturn(List.of(
                candle("2026-10-15", "81.49", "89.93", "yahoo-chart")));
        var result = analysis(cache, mock(FinnhubClientService.class), "").dashboard("APH");
        assertNotNull(result.quote());
        assertNull(result.quote().getPreviousClose());
    }

    @Test
    void sourceSwitchCannotAppendAdjustedHalfPricesToOldCanonicalHistory() {
        StockCandleMapper mapper = mock(StockCandleMapper.class);
        when(mapper.findRecentBySymbol(eq("APH"), anyInt())).thenReturn(List.of(
                candle("2026-10-14", "100", "101", "yahoo-chart")));
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("candles", mapper);
        StockCacheService cache = new StockCacheService(null, null, null, null, null, null,
                factory.getBeanProvider(StockCandleMapper.class), null, null, null, null, null, null);
        List<StockCandleDaily> incoming = List.of(candle("2026-10-14", "50", "51", "stooq"),
                candle("2026-10-15", "51", "52", "stooq"));
        cache.saveCandles(incoming);
        verify(mapper).upsertSourceBatch(incoming);
        verify(mapper, never()).upsertBatch(anyList());
    }

    @Test
    void qualityFreshnessUsesMarketTimestampNotSuccessfulFetchTimestamp() {
        StockCacheService cache = mock(StockCacheService.class);
        var factory = new StaticListableBeanFactory();
        StockDataQualityService quality = new StockDataQualityService(
                factory.getBeanProvider(StockDataQualityMapper.class), cache, new ObjectMapper());
        StockQuoteCache quote = quote("10", "9", LocalDateTime.now().minusDays(20).toString());
        quote.setFetchedAt(LocalDateTime.now());
        var dashboard = new StockDashboard("APH", true, true, true, true, true, true, true, true, true,
                "", null, quote, null, List.of(), List.of(), List.of(), List.of(), "");
        assertTrue(quality.assessAndSave(dashboard).getStaleSourcesJson().contains("quote"));
    }

    static StockAnalysisService analysis(StockCacheService cache, FinnhubClientService client, String key) {
        return new StockAnalysisService(new FinnhubProperties(key, "http://unused", 60, 24, 24, 30, 24, 24, 24),
                client, cache, new ObjectMapper(), false, false, false, false);
    }

    static StockQuoteCache quote(String price, String previous, String timestamp) {
        StockQuoteCache quote = new StockQuoteCache();
        quote.setSymbol("APH");
        quote.setCurrentPrice(new BigDecimal(price));
        quote.setPreviousClose(new BigDecimal(previous));
        quote.setQuoteTime(LocalDateTime.parse(timestamp));
        quote.setFetchedAt(LocalDateTime.now().minusMonths(3));
        return quote;
    }

    static StockCandleDaily candle(String date, String price, String high, String source) {
        StockCandleDaily candle = new StockCandleDaily();
        candle.setSymbol("APH");
        candle.setTradeDate(LocalDate.parse(date));
        candle.setOpenPrice(new BigDecimal(price));
        candle.setClosePrice(new BigDecimal(price));
        candle.setLowPrice(new BigDecimal(price));
        candle.setHighPrice(new BigDecimal(high));
        candle.setSource(source);
        return candle;
    }
}
