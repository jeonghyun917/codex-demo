package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TossMarketDataService {

    private static final String PROVIDER = "TOSS";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_BATCH_SYMBOLS = 200;

    private final TossInvestClientService client;
    private final TossInvestProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TossMarketDataMapper> tossMapper;
    private final StockCacheService stockCacheService;

    public TossMarketDataService(TossInvestClientService client,
            TossInvestProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<TossMarketDataMapper> tossMapper,
            StockCacheService stockCacheService) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.tossMapper = tossMapper;
        this.stockCacheService = stockCacheService;
    }

    public SyncSummary sync(List<String> symbols, SyncOptions options) {
        if (!properties.configured()) {
            return new SyncSummary(0, 0, 0, 0, 0, List.of("Toss API key/secret is not configured."));
        }
        TossMarketDataMapper mapper = requireMapper();
        List<String> normalizedSymbols = symbols.stream()
                .map(TossMarketDataService::normalizeSymbol)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(Math.max(1, options.symbolLimit()))
                .toList();
        if (normalizedSymbols.isEmpty()) {
            return new SyncSummary(0, 0, 0, 0, 0, List.of("No symbols selected."));
        }

        Counter counter = new Counter();
        List<String> failed = new ArrayList<>();

        if (options.prices()) {
            for (List<String> batch : partition(normalizedSymbols, MAX_BATCH_SYMBOLS)) {
                runBatchCall(counter, failed, "prices:" + batch.size(), () -> savePrices(mapper, batch));
                sleep(options.delayMillis());
            }
        }
        if (options.stockInfo()) {
            for (List<String> batch : partition(normalizedSymbols, MAX_BATCH_SYMBOLS)) {
                runBatchCall(counter, failed, "stocks:" + batch.size(), () -> saveStockInfo(mapper, batch));
                sleep(options.delayMillis());
            }
        }
        if (options.exchangeRate()) {
            runBatchCall(counter, failed, "exchange-rate:USD-KRW", () -> saveExchangeRate(mapper));
            sleep(options.delayMillis());
        }
        if (options.calendar()) {
            runBatchCall(counter, failed, "calendar:KR", () -> saveCalendar(mapper, "KR"));
            runBatchCall(counter, failed, "calendar:US", () -> saveCalendar(mapper, "US"));
            sleep(options.delayMillis());
        }

        for (String symbol : normalizedSymbols) {
            if (options.candles()) {
                runBatchCall(counter, failed, "candles:" + symbol, () -> saveCandles(mapper, symbol, options.candleCount()));
                sleep(options.delayMillis());
            }
            if (options.warnings()) {
                runBatchCall(counter, failed, "warnings:" + symbol, () -> saveWarnings(mapper, symbol));
                sleep(options.delayMillis());
            }
            if (options.priceLimits()) {
                runBatchCall(counter, failed, "price-limits:" + symbol, () -> savePriceLimit(mapper, symbol));
                sleep(options.delayMillis());
            }
            if (options.orderbook()) {
                runBatchCall(counter, failed, "orderbook:" + symbol, () -> saveOrderbook(mapper, symbol));
                sleep(options.delayMillis());
            }
            if (options.trades()) {
                runBatchCall(counter, failed, "trades:" + symbol, () -> saveTrades(mapper, symbol, options.tradeCount()));
                sleep(options.delayMillis());
            }
        }

        return new SyncSummary(counter.requested, counter.success, counter.fail, counter.skipped,
                counter.savedRows, failed);
    }

    private int savePrices(TossMarketDataMapper mapper, List<String> symbols) {
        TossInvestResponse response = client.get("/api/v1/prices", Map.of("symbols", String.join(",", symbols)));
        if (!response.success()) {
            throw new IllegalStateException(errorMessage("prices", response));
        }
        JsonNode result = resultNode(response.body());
        int saved = 0;
        if (result != null && result.isArray()) {
            for (JsonNode item : result) {
                String symbol = normalizeSymbol(text(item, "symbol"));
                if (!StringUtils.hasText(symbol)) {
                    continue;
                }
                mapper.insertPriceSnapshot(symbol, dateTime(item, "timestamp"),
                        decimal(item, "lastPrice"), text(item, "currency"), item.toString());
                saved++;
            }
        }
        return saved;
    }

    private int saveStockInfo(TossMarketDataMapper mapper, List<String> symbols) {
        TossInvestResponse response = client.get("/api/v1/stocks", Map.of("symbols", String.join(",", symbols)));
        if (!response.success()) {
            throw new IllegalStateException(errorMessage("stocks", response));
        }
        JsonNode result = resultNode(response.body());
        int saved = 0;
        if (result != null && result.isArray()) {
            for (JsonNode item : result) {
                String symbol = normalizeSymbol(text(item, "symbol"));
                if (!StringUtils.hasText(symbol)) {
                    continue;
                }
                mapper.upsertStockInfo(symbol,
                        text(item, "name"),
                        text(item, "englishName"),
                        text(item, "isinCode"),
                        text(item, "market"),
                        text(item, "securityType"),
                        bool(item, "isCommonShare"),
                        text(item, "status"),
                        text(item, "currency"),
                        date(item, "listDate"),
                        date(item, "delistDate"),
                        decimal(item, "sharesOutstanding"),
                        decimal(item, "leverageFactor"),
                        item.toString());
                saved++;
            }
        }
        return saved;
    }

    private int saveWarnings(TossMarketDataMapper mapper, String symbol) {
        TossInvestResponse response = client.get("/api/v1/stocks/" + symbol + "/warnings", Map.of());
        if (!response.success()) {
            throw new IllegalStateException(errorMessage("warnings:" + symbol, response));
        }
        mapper.deleteWarnings(symbol);
        JsonNode result = resultNode(response.body());
        int saved = 0;
        if (result != null && result.isArray()) {
            for (JsonNode item : result) {
                String warningType = text(item, "warningType");
                if (!StringUtils.hasText(warningType)) {
                    continue;
                }
                mapper.upsertWarning(symbol, warningType, text(item, "exchange"),
                        date(item, "startDate"), date(item, "endDate"), item.toString());
                saved++;
            }
        }
        return saved;
    }

    private int saveCandles(TossMarketDataMapper mapper, String symbol, int candleCount) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("symbol", symbol);
        query.put("interval", "1d");
        query.put("count", String.valueOf(Math.max(1, Math.min(200, candleCount))));
        query.put("adjusted", "true");
        TossInvestResponse response = client.get("/api/v1/candles", query);
        if (!response.success()) {
            throw new IllegalStateException(errorMessage("candles:" + symbol, response));
        }
        JsonNode result = resultNode(response.body());
        JsonNode candles = result == null ? null : result.get("candles");
        int saved = 0;
        List<StockCandleDaily> dailyCandles = new ArrayList<>();
        if (candles != null && candles.isArray()) {
            for (JsonNode candle : candles) {
                LocalDateTime candleTime = dateTime(candle, "timestamp");
                if (candleTime == null) {
                    continue;
                }
                LocalDate tradeDate = candleTime.toLocalDate();
                BigDecimal open = decimal(candle, "openPrice");
                BigDecimal high = decimal(candle, "highPrice");
                BigDecimal low = decimal(candle, "lowPrice");
                BigDecimal close = decimal(candle, "closePrice");
                BigDecimal volume = decimal(candle, "volume");
                mapper.upsertCandle(symbol, "1d", candleTime, tradeDate, open, high, low, close,
                        volume, text(candle, "currency"), true, candle.toString());
                if (open != null && high != null && low != null && close != null) {
                    StockCandleDaily daily = new StockCandleDaily();
                    daily.setSymbol(symbol);
                    daily.setTradeDate(tradeDate);
                    daily.setOpenPrice(open);
                    daily.setHighPrice(high);
                    daily.setLowPrice(low);
                    daily.setClosePrice(close);
                    daily.setVolume(volume == null ? null : volume.setScale(0, RoundingMode.HALF_UP).longValue());
                    daily.setSource(PROVIDER);
                    dailyCandles.add(daily);
                }
                saved++;
            }
        }
        stockCacheService.saveCandles(dailyCandles);
        return saved;
    }

    private int saveOrderbook(TossMarketDataMapper mapper, String symbol) {
        TossInvestResponse response = client.get("/api/v1/orderbook", Map.of("symbol", symbol));
        if (!response.success()) {
            throw new IllegalStateException(errorMessage("orderbook:" + symbol, response));
        }
        JsonNode result = resultNode(response.body());
        if (result == null || result.isMissingNode()) {
            return 0;
        }
        JsonNode ask = first(result.path("asks"));
        JsonNode bid = first(result.path("bids"));
        BigDecimal askPrice = decimal(ask, "price");
        BigDecimal bidPrice = decimal(bid, "price");
        BigDecimal spread = askPrice == null || bidPrice == null ? null : askPrice.subtract(bidPrice);
        BigDecimal mid = askPrice == null || bidPrice == null ? null : askPrice.add(bidPrice).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        BigDecimal spreadBps = spread == null || mid == null || mid.compareTo(BigDecimal.ZERO) == 0
                ? null
                : spread.divide(mid, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(10_000));
        mapper.insertOrderbookSnapshot(symbol, dateTime(result, "timestamp"), text(result, "currency"),
                askPrice, decimal(ask, "volume"), bidPrice, decimal(bid, "volume"), spread, spreadBps, result.toString());
        return 1;
    }

    private int saveTrades(TossMarketDataMapper mapper, String symbol, int count) {
        TossInvestResponse response = client.get("/api/v1/trades",
                Map.of("symbol", symbol, "count", String.valueOf(Math.max(1, Math.min(50, count)))));
        if (!response.success()) {
            throw new IllegalStateException(errorMessage("trades:" + symbol, response));
        }
        JsonNode result = resultNode(response.body());
        int saved = 0;
        if (result != null && result.isArray()) {
            for (JsonNode item : result) {
                LocalDateTime tradeTime = dateTime(item, "timestamp");
                if (tradeTime == null) {
                    continue;
                }
                mapper.upsertTradePrint(symbol, tradeTime, decimal(item, "price"),
                        decimal(item, "volume"), text(item, "currency"), item.toString());
                saved++;
            }
        }
        return saved;
    }

    private int savePriceLimit(TossMarketDataMapper mapper, String symbol) {
        TossInvestResponse response = client.get("/api/v1/price-limits", Map.of("symbol", symbol));
        if (!response.success()) {
            throw new IllegalStateException(errorMessage("price-limits:" + symbol, response));
        }
        JsonNode result = resultNode(response.body());
        if (result == null || result.isMissingNode()) {
            return 0;
        }
        mapper.insertPriceLimitSnapshot(symbol, dateTime(result, "timestamp"),
                decimal(result, "upperLimitPrice"), decimal(result, "lowerLimitPrice"),
                text(result, "currency"), result.toString());
        return 1;
    }

    private int saveExchangeRate(TossMarketDataMapper mapper) {
        TossInvestResponse response = client.get("/api/v1/exchange-rate",
                Map.of("baseCurrency", "USD", "quoteCurrency", "KRW"));
        if (!response.success()) {
            throw new IllegalStateException(errorMessage("exchange-rate", response));
        }
        JsonNode result = resultNode(response.body());
        if (result == null || result.isMissingNode()) {
            return 0;
        }
        mapper.insertExchangeRateSnapshot(text(result, "baseCurrency"), text(result, "quoteCurrency"),
                decimal(result, "rate"), decimal(result, "midRate"), decimal(result, "basisPoint"),
                text(result, "rateChangeType"), dateTime(result, "validFrom"), dateTime(result, "validUntil"),
                result.toString());
        return 1;
    }

    private int saveCalendar(TossMarketDataMapper mapper, String marketCountry) {
        String endpoint = "KR".equals(marketCountry) ? "/api/v1/market-calendar/KR" : "/api/v1/market-calendar/US";
        TossInvestResponse response = client.get(endpoint, Map.of("date", LocalDate.now(KST).toString()));
        if (!response.success()) {
            throw new IllegalStateException(errorMessage("calendar:" + marketCountry, response));
        }
        JsonNode result = resultNode(response.body());
        if (result == null || result.isMissingNode()) {
            return 0;
        }
        JsonNode today = result.path("today");
        mapper.insertCalendarSnapshot(marketCountry, LocalDate.now(KST), date(today, "date"),
                marketOpen(today), date(result.path("previousBusinessDay"), "date"),
                date(result.path("nextBusinessDay"), "date"), result.toString());
        return 1;
    }

    private void runBatchCall(Counter counter, List<String> failed, String label, BatchCall call) {
        counter.requested++;
        try {
            int saved = call.run();
            counter.success++;
            counter.savedRows += saved;
        } catch (RuntimeException ex) {
            counter.fail++;
            failed.add(label + "=" + ex.getMessage());
        }
    }

    private JsonNode resultNode(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("result");
        } catch (Exception ex) {
            throw new IllegalStateException("Toss response parse failed: " + ex.getMessage(), ex);
        }
    }

    private static JsonNode first(JsonNode array) {
        return array != null && array.isArray() && array.size() > 0 ? array.get(0) : null;
    }

    private static String errorMessage(String endpoint, TossInvestResponse response) {
        String reason = response.errorMessage() != null ? response.errorMessage() : response.body();
        if (reason != null && reason.length() > 300) {
            reason = reason.substring(0, 300);
        }
        return endpoint + " failed with status " + response.statusCode() + ": " + reason;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        String text = value == null || value.isNull() ? null : value.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String text = text(node, field);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private static LocalDate date(JsonNode node, String field) {
        String text = text(node, field);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static LocalDateTime dateTime(JsonNode node, String field) {
        String text = text(node, field);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(KST).toLocalDateTime();
        } catch (RuntimeException ex) {
            try {
                return LocalDateTime.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static Boolean marketOpen(JsonNode today) {
        if (today == null || today.isMissingNode()) {
            return null;
        }
        for (String field : List.of("isOpen", "open", "regularOpen", "businessDay")) {
            JsonNode value = today.get(field);
            if (value != null && !value.isNull()) {
                return value.asBoolean();
            }
        }
        String status = text(today, "status");
        if (status != null) {
            return "OPEN".equalsIgnoreCase(status) || "REGULAR".equalsIgnoreCase(status);
        }
        return null;
    }

    private TossMarketDataMapper requireMapper() {
        TossMarketDataMapper mapper = tossMapper.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("TossMarketDataMapper is not available. Run with the mariadb profile.");
        }
        return mapper;
    }

    private static List<List<String>> partition(List<String> values, int size) {
        List<List<String>> parts = new ArrayList<>();
        for (int index = 0; index < values.size(); index += size) {
            parts.add(values.subList(index, Math.min(values.size(), index + size)));
        }
        return parts;
    }

    private static void sleep(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Toss batch interrupted.", ex);
        }
    }

    private static String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface BatchCall {
        int run();
    }

    private static final class Counter {
        int requested;
        int success;
        int fail;
        int skipped;
        int savedRows;
    }

    public record SyncOptions(
            int symbolLimit,
            int candleCount,
            int tradeCount,
            long delayMillis,
            boolean prices,
            boolean stockInfo,
            boolean candles,
            boolean warnings,
            boolean priceLimits,
            boolean exchangeRate,
            boolean calendar,
            boolean orderbook,
            boolean trades) {
    }

    public record SyncSummary(
            int requested,
            int success,
            int fail,
            int skipped,
            int savedRows,
            List<String> failedItems) {
    }
}
