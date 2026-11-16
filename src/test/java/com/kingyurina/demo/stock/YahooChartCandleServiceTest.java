package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import tools.jackson.databind.ObjectMapper;

class YahooChartCandleServiceTest {

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void stopsAfterFirstHttpFailure(int status) throws IOException {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(status, "{}", calls);
        StockCacheService cache = mock(StockCacheService.class);
        try {
            var result = service(server, cache).syncSymbols(List.of("AAPL", "MSFT"), 1, 0);
            assertEquals(1, calls.get());
            assertEquals(1, result.requested());
            assertEquals(1, result.fail());
            verifyNoInteractions(cache);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void stopsAfterNetworkFailure() throws IOException {
        HttpServer server = server(200, "{}", new AtomicInteger());
        StockCacheService cache = mock(StockCacheService.class);
        YahooChartCandleService service = service(server, cache);
        server.stop(0);

        var result = service.syncSymbols(List.of("AAPL", "MSFT"), 1, 0);

        assertEquals(1, result.requested());
        assertEquals(1, result.fail());
        verifyNoInteractions(cache);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void excludesMarketTodayAndFutureButPreservesZeroForSourceValidation() throws IOException {
        ZoneId market = ZoneId.of("America/New_York");
        LocalDate today = LocalDate.now(market);
        long yesterday = today.minusDays(1).atTime(12, 0).atZone(market).toEpochSecond();
        long current = today.atTime(12, 0).atZone(market).toEpochSecond();
        long future = today.plusDays(1).atTime(12, 0).atZone(market).toEpochSecond();
        String body = """
                {"chart":{"result":[{"timestamp":[%d,%d,%d],"indicators":{"quote":[{
                "open":[0,10,10],"high":[12,12,12],"low":[9,9,9],
                "close":[11,11,11],"volume":[10,10,10]}]}}],"error":null}}
                """.formatted(yesterday, current, future);
        HttpServer server = server(200, body, new AtomicInteger());
        StockCacheService cache = mock(StockCacheService.class);
        try {
            when(cache.saveCandles(anyList())).thenReturn(new StockCacheService.CandleSaveResult(
                    1, 1, 0, 1, null, "NO_VALID_CANONICAL_ROWS"));
            var result = service(server, cache).syncSymbols(List.of("AAPL"), 1, 0);
            ArgumentCaptor<List<StockCandleDaily>> saved = ArgumentCaptor.forClass((Class) List.class);
            verify(cache).saveCandles(saved.capture());
            assertEquals(0, result.savedRows());
            assertEquals(0, result.success());
            assertEquals(1, result.fail());
            assertEquals(today.minusDays(1), saved.getValue().getFirst().getTradeDate());
            assertEquals(0, saved.getValue().getFirst().getOpenPrice().signum());
            assertEquals(List.of("AAPL"), result.failedSymbols());
        } finally {
            server.stop(0);
        }
    }

    private static YahooChartCandleService service(HttpServer server, StockCacheService cache) {
        var properties = new YahooChartProperties("http://127.0.0.1:" + server.getAddress().getPort() + "/", 1, 0);
        return new YahooChartCandleService(properties, cache,
                new StaticListableBeanFactory().getBeanProvider(ApiCallLogMapper.class), new ObjectMapper());
    }

    private static HttpServer server(int status, String body, AtomicInteger calls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
