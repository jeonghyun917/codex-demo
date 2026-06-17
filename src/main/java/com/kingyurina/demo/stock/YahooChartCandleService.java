package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class YahooChartCandleService {

    private static final Logger log = LoggerFactory.getLogger(YahooChartCandleService.class);
    private static final String PROVIDER = "yahoo-chart";
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private final YahooChartProperties properties;
    private final StockCacheService cacheService;
    private final ObjectProvider<ApiCallLogMapper> apiCallLogMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public YahooChartCandleService(YahooChartProperties properties,
            StockCacheService cacheService,
            ObjectProvider<ApiCallLogMapper> apiCallLogMapper,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.cacheService = cacheService;
        this.apiCallLogMapper = apiCallLogMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SyncSummary syncSymbols(List<String> symbols, int years, long delayMillis) {
        int requested = 0;
        int success = 0;
        int fail = 0;
        int savedRows = 0;
        List<String> failedSymbols = new ArrayList<>();
        int resolvedYears = years <= 0 ? properties.defaultYears() : years;
        long resolvedDelay = delayMillis < 0 ? properties.requestDelayMillis() : delayMillis;
        LocalDate cutoffExclusive = LocalDate.now(MARKET_ZONE);
        LocalDate to = cutoffExclusive.minusDays(1);
        LocalDate from = to.minusYears(resolvedYears);

        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            requested++;
            try {
                List<StockCandleDaily> candles = fetch(symbol, from, to, cutoffExclusive);
                if (candles.isEmpty()) {
                    fail++;
                    failedSymbols.add(symbol);
                } else {
                    cacheService.saveCandles(candles);
                    success++;
                    savedRows += candles.size();
                }
            } catch (RuntimeException ex) {
                fail++;
                failedSymbols.add(symbol);
                log.warn("Failed to sync Yahoo chart candles for {}", symbol, ex);
            }
            sleep(resolvedDelay);
        }
        return new SyncSummary(requested, success, fail, savedRows, failedSymbols);
    }

    private List<StockCandleDaily> fetch(String symbol, LocalDate from, LocalDate to, LocalDate cutoffExclusive) {
        String normalized = normalizeSymbol(symbol);
        long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        URI uri = URI.create(properties.baseUrl()
                + encodePath(normalized)
                + "?period1=" + period1
                + "&period2=" + period2
                + "&interval=1d"
                + "&events=history"
                + "&includeAdjustedClose=true");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", "Mozilla/5.0 king-yurina-stock-research/0.1")
                .GET()
                .build();
        String endpoint = "/v8/finance/chart/" + normalized;
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logApiCall(endpoint, symbol, response.statusCode(),
                        "Yahoo chart returned HTTP " + response.statusCode());
                throw new IllegalStateException("Yahoo chart returned HTTP " + response.statusCode());
            }
            logApiCall(endpoint, symbol, response.statusCode(), null);
            return parse(symbol, response.body(), cutoffExclusive);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logApiCall(endpoint, symbol, null, "Yahoo chart request interrupted.");
            throw new IllegalStateException("Yahoo chart request interrupted.", ex);
        } catch (Exception ex) {
            if (!String.valueOf(ex.getMessage()).startsWith("Yahoo chart returned HTTP ")) {
                logApiCall(endpoint, symbol, null, ex.getMessage());
            }
            throw new IllegalStateException("Yahoo chart request failed: " + ex.getMessage(), ex);
        }
    }

    private void logApiCall(String endpoint, String symbol, Integer statusCode, String errorMessage) {
        ApiCallLogMapper mapper = apiCallLogMapper.getIfAvailable();
        if (mapper == null) {
            return;
        }
        mapper.insert(new ApiCallLog(PROVIDER, endpoint, normalizeSymbol(symbol), statusCode, errorMessage));
    }

    private List<StockCandleDaily> parse(String symbol, String json, LocalDate cutoffExclusive) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode chart = root == null ? null : root.get("chart");
            JsonNode error = chart == null ? null : chart.get("error");
            if (error != null && !error.isNull()) {
                return List.of();
            }
            JsonNode results = chart == null ? null : chart.get("result");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return List.of();
            }
            JsonNode result = results.get(0);
            JsonNode timestamps = result.get("timestamp");
            JsonNode quote = result.path("indicators").path("quote").path(0);
            if (timestamps == null || !timestamps.isArray() || quote.isMissingNode()) {
                return List.of();
            }

            JsonNode opens = quote.get("open");
            JsonNode highs = quote.get("high");
            JsonNode lows = quote.get("low");
            JsonNode closes = quote.get("close");
            JsonNode volumes = quote.get("volume");
            List<StockCandleDaily> candles = new ArrayList<>();
            for (int index = 0; index < timestamps.size(); index++) {
                BigDecimal open = decimalAt(opens, index);
                BigDecimal high = decimalAt(highs, index);
                BigDecimal low = decimalAt(lows, index);
                BigDecimal close = decimalAt(closes, index);
                if (open == null || high == null || low == null || close == null) {
                    continue;
                }

                LocalDate tradeDate = Instant.ofEpochSecond(timestamps.get(index).longValue())
                        .atZone(MARKET_ZONE)
                        .toLocalDate();
                if (!tradeDate.isBefore(cutoffExclusive)) {
                    continue;
                }

                StockCandleDaily candle = new StockCandleDaily();
                candle.setSymbol(symbol.trim().toUpperCase(Locale.ROOT));
                candle.setTradeDate(tradeDate);
                candle.setOpenPrice(open);
                candle.setHighPrice(high);
                candle.setLowPrice(low);
                candle.setClosePrice(close);
                candle.setVolume(longAt(volumes, index));
                candle.setSource(PROVIDER);
                candles.add(candle);
            }
            return candles;
        } catch (Exception ex) {
            throw new IllegalStateException("Yahoo chart parse failed: " + ex.getMessage(), ex);
        }
    }

    private static BigDecimal decimalAt(JsonNode values, int index) {
        if (values == null || !values.isArray() || values.size() <= index) {
            return null;
        }
        JsonNode value = values.get(index);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.decimalValue();
    }

    private static Long longAt(JsonNode values, int index) {
        if (values == null || !values.isArray() || values.size() <= index) {
            return null;
        }
        JsonNode value = values.get(index);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.longValue();
    }

    private static String normalizeSymbol(String symbol) {
        return (symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT)).replace('.', '-');
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void sleep(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Yahoo chart batch interrupted.", ex);
        }
    }

    public record SyncSummary(int requested, int success, int fail, int savedRows, List<String> failedSymbols) {
    }
}
