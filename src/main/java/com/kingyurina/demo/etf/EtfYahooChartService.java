package com.kingyurina.demo.etf;

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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.kingyurina.demo.stock.ApiCallLog;
import com.kingyurina.demo.stock.ApiCallLogMapper;
import com.kingyurina.demo.stock.YahooChartProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class EtfYahooChartService {

    private static final Logger log = LoggerFactory.getLogger(EtfYahooChartService.class);
    private static final String PROVIDER = "yahoo-chart-etf";
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private final YahooChartProperties properties;
    private final ObjectProvider<EtfMapper> etfMapper;
    private final ObjectProvider<ApiCallLogMapper> apiCallLogMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EtfYahooChartService(YahooChartProperties properties, ObjectProvider<EtfMapper> etfMapper,
            ObjectProvider<ApiCallLogMapper> apiCallLogMapper, ObjectMapper objectMapper) {
        this.properties = properties;
        this.etfMapper = etfMapper;
        this.apiCallLogMapper = apiCallLogMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SyncSummary syncSymbols(List<String> symbols, int years, long delayMillis) {
        EtfMapper mapper = etfMapper.getIfAvailable();
        if (mapper == null) {
            return new SyncSummary(0, 0, 0, 0, List.of("EtfMapper unavailable"));
        }
        int requested = 0;
        int success = 0;
        int fail = 0;
        int savedRows = 0;
        List<String> failedSymbols = new ArrayList<>();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(years <= 0 ? properties.defaultYears() : years);
        long resolvedDelay = delayMillis < 0 ? properties.requestDelayMillis() : delayMillis;

        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            requested++;
            try {
                List<EtfCandleDaily> candles = fetch(symbol, from, to);
                if (candles.isEmpty()) {
                    fail++;
                    failedSymbols.add(symbol);
                } else {
                    mapper.upsertCandles(candles);
                    updateQuote(mapper, symbol, candles);
                    success++;
                    savedRows += candles.size();
                }
            } catch (RuntimeException ex) {
                fail++;
                failedSymbols.add(symbol);
                log.warn("Failed to sync Yahoo chart ETF candles for {}", symbol, ex);
            }
            sleep(resolvedDelay);
        }
        return new SyncSummary(requested, success, fail, savedRows, failedSymbols);
    }

    private List<EtfCandleDaily> fetch(String symbol, LocalDate from, LocalDate to) {
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
                .header("User-Agent", "Mozilla/5.0 king-yurina-etf-research/0.1")
                .GET()
                .build();
        String endpoint = "/v8/finance/chart/" + normalized;
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logApiCall(endpoint, normalized, response.statusCode(), "Yahoo chart returned HTTP " + response.statusCode());
                throw new IllegalStateException("Yahoo chart returned HTTP " + response.statusCode());
            }
            logApiCall(endpoint, normalized, response.statusCode(), null);
            return parse(normalized, response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logApiCall(endpoint, normalized, null, "Yahoo chart request interrupted.");
            throw new IllegalStateException("Yahoo chart request interrupted.", ex);
        } catch (Exception ex) {
            logApiCall(endpoint, normalized, null, ex.getMessage());
            throw new IllegalStateException("Yahoo chart request failed: " + ex.getMessage(), ex);
        }
    }

    private List<EtfCandleDaily> parse(String symbol, String json) {
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
            List<EtfCandleDaily> candles = new ArrayList<>();
            for (int index = 0; index < timestamps.size(); index++) {
                BigDecimal open = decimalAt(opens, index);
                BigDecimal high = decimalAt(highs, index);
                BigDecimal low = decimalAt(lows, index);
                BigDecimal close = decimalAt(closes, index);
                if (open == null || high == null || low == null || close == null) {
                    continue;
                }
                EtfCandleDaily candle = new EtfCandleDaily();
                candle.setSymbol(symbol);
                candle.setTradeDate(Instant.ofEpochSecond(timestamps.get(index).longValue())
                        .atZone(MARKET_ZONE)
                        .toLocalDate());
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
            throw new IllegalStateException("Yahoo chart ETF parse failed: " + ex.getMessage(), ex);
        }
    }

    private static void updateQuote(EtfMapper mapper, String symbol, List<EtfCandleDaily> candles) {
        List<EtfCandleDaily> ordered = candles.stream()
                .sorted(Comparator.comparing(EtfCandleDaily::getTradeDate).reversed())
                .toList();
        if (ordered.isEmpty()) {
            return;
        }
        EtfCandleDaily latest = ordered.get(0);
        EtfCandleDaily previous = ordered.size() > 1 ? ordered.get(1) : null;
        EtfQuoteCache quote = new EtfQuoteCache();
        quote.setSymbol(normalizeSymbol(symbol));
        quote.setCurrentPrice(latest.getClosePrice());
        quote.setOpenPrice(latest.getOpenPrice());
        quote.setHighPrice(latest.getHighPrice());
        quote.setLowPrice(latest.getLowPrice());
        quote.setPreviousClose(previous == null ? null : previous.getClosePrice());
        quote.setVolume(latest.getVolume());
        quote.setQuoteTime(LocalDateTime.now());
        quote.setSource(PROVIDER);
        mapper.upsertQuote(quote);
    }

    private void logApiCall(String endpoint, String symbol, Integer statusCode, String errorMessage) {
        ApiCallLogMapper mapper = apiCallLogMapper.getIfAvailable();
        if (mapper != null) {
            mapper.insert(new ApiCallLog(PROVIDER, endpoint, symbol, statusCode, errorMessage));
        }
    }

    private static BigDecimal decimalAt(JsonNode values, int index) {
        if (values == null || !values.isArray() || values.size() <= index) {
            return null;
        }
        JsonNode value = values.get(index);
        return value == null || value.isNull() || !value.isNumber() ? null : value.decimalValue();
    }

    private static Long longAt(JsonNode values, int index) {
        if (values == null || !values.isArray() || values.size() <= index) {
            return null;
        }
        JsonNode value = values.get(index);
        return value == null || value.isNull() || !value.isNumber() ? null : value.longValue();
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
            throw new IllegalStateException("Yahoo chart ETF batch interrupted.", ex);
        }
    }

    public record SyncSummary(int requested, int success, int fail, int savedRows, List<String> failedSymbols) {
    }
}
