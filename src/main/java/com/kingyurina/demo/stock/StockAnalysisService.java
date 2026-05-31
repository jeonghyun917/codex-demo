package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class StockAnalysisService {

    private static final String PROVIDER = "FINNHUB";

    private final FinnhubProperties properties;
    private final FinnhubClientService client;
    private final StockCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final boolean newsEnabled;
    private final boolean recommendationEnabled;
    private final boolean epsSurprisesEnabled;
    private final boolean candleEnabled;

    public StockAnalysisService(FinnhubProperties properties, FinnhubClientService client,
            StockCacheService cacheService, ObjectMapper objectMapper,
            @Value("${finnhub.news-enabled:true}") boolean newsEnabled,
            @Value("${finnhub.recommendation-enabled:true}") boolean recommendationEnabled,
            @Value("${finnhub.eps-surprises-enabled:true}") boolean epsSurprisesEnabled,
            @Value("${finnhub.candle-enabled:true}") boolean candleEnabled) {
        this.properties = properties;
        this.client = client;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.newsEnabled = newsEnabled;
        this.recommendationEnabled = recommendationEnabled;
        this.epsSurprisesEnabled = epsSurprisesEnabled;
        this.candleEnabled = candleEnabled;
    }

    public StockDashboard dashboard(String requestedSymbol) {
        String symbol = normalizeSymbol(requestedSymbol);
        boolean databaseEnabled = cacheService.databaseEnabled();

        if (!properties.hasApiKey()) {
            return new StockDashboard(symbol, false, databaseEnabled, false, false, false, false, false, false, false,
                    "FINNHUB_API_KEY is not configured.", null, null, null, List.of(), List.of(), List.of(), List.of(),
                    "Set FINNHUB_API_KEY to load quote, profile, metric, news, and daily candle data.");
        }

        CachedValue<StockQuoteCache> quote = quote(symbol);
        CachedValue<CompanyProfile> profile = profile(symbol);
        CachedValue<StockMetricSnapshot> metric = metric(symbol);
        CachedValue<List<CompanyNews>> news = news(symbol);
        CachedValue<List<StockRecommendationTrend>> recommendations = recommendations(symbol);
        CachedValue<List<StockEpsSurprise>> epsSurprises = epsSurprises(symbol);
        CachedValue<List<StockCandleDaily>> candles = candles(symbol);

        String message = quote.value() == null && profile.value() == null && metric.value() == null
                ? "Finnhub did not return usable data. Check API limits and api_call_log."
                : "Finnhub data loaded.";

        return new StockDashboard(symbol, true, databaseEnabled, quote.cacheHit(), profile.cacheHit(), metric.cacheHit(),
                news.cacheHit(), recommendations.cacheHit(), epsSurprises.cacheHit(), candles.cacheHit(), message,
                profile.value(), quote.value(), metric.value(), news.value(), recommendations.value(),
                epsSurprises.value(), candles.value(), summarize(quote.value(), metric.value()));
    }

    private CachedValue<StockQuoteCache> quote(String symbol) {
        StockQuoteCache cached = cacheService.findQuote(symbol);
        if (isFresh(cached == null ? null : cached.getFetchedAt(), properties.quoteCacheSeconds(), TimeUnit.SECONDS)) {
            return new CachedValue<>(cached, true);
        }

        FinnhubResponse response = client.get("/quote", Map.of("symbol", symbol));
        log("/quote", symbol, response);
        if (!response.success()) {
            return new CachedValue<>(cached, true);
        }

        StockQuoteCache quote = parseQuote(symbol, response.body());
        cacheService.saveQuote(quote);
        return new CachedValue<>(quote, false);
    }

    private CachedValue<CompanyProfile> profile(String symbol) {
        CompanyProfile cached = cacheService.findProfile(symbol);
        if (isFresh(cached == null ? null : cached.getFetchedAt(), properties.profileCacheHours(), TimeUnit.HOURS)) {
            return new CachedValue<>(cached, true);
        }

        FinnhubResponse response = client.get("/stock/profile2", Map.of("symbol", symbol));
        log("/stock/profile2", symbol, response);
        if (!response.success()) {
            return new CachedValue<>(cached, true);
        }

        CompanyProfile profile = parseProfile(symbol, response.body());
        cacheService.saveProfile(profile);
        return new CachedValue<>(profile, false);
    }

    private CachedValue<StockMetricSnapshot> metric(String symbol) {
        StockMetricSnapshot cached = cacheService.findLatestMetric(symbol);
        if (isFresh(cached == null ? null : cached.getFetchedAt(), properties.metricsCacheHours(), TimeUnit.HOURS)) {
            return new CachedValue<>(cached, true);
        }

        FinnhubResponse response = client.get("/stock/metric", Map.of("symbol", symbol, "metric", "all"));
        log("/stock/metric", symbol, response);
        if (!response.success()) {
            return new CachedValue<>(cached, true);
        }

        StockMetricSnapshot metric = parseMetric(symbol, response.body());
        cacheService.saveMetric(metric);
        return new CachedValue<>(metric, false);
    }

    private CachedValue<List<CompanyNews>> news(String symbol) {
        List<CompanyNews> cached = cacheService.findRecentNews(symbol, 5);
        if (!newsEnabled) {
            return new CachedValue<>(cached, true);
        }
        if (isFresh(cacheService.findLatestNewsFetchedAt(symbol), properties.newsCacheMinutes(), TimeUnit.MINUTES)) {
            return new CachedValue<>(cached, true);
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(7);
        FinnhubResponse response = client.get("/company-news",
                Map.of("symbol", symbol, "from", from.toString(), "to", to.toString()));
        log("/company-news", symbol, response);
        if (!response.success()) {
            return new CachedValue<>(cached, true);
        }

        List<CompanyNews> news = parseNews(symbol, response.body());
        cacheService.saveNews(news);
        return new CachedValue<>(news.stream().limit(5).toList(), false);
    }

    private CachedValue<List<StockRecommendationTrend>> recommendations(String symbol) {
        List<StockRecommendationTrend> cached = cacheService.findRecentRecommendations(symbol, 4);
        if (!recommendationEnabled) {
            return new CachedValue<>(cached, true);
        }
        LocalDateTime latestFetch = latestFetch(
                cacheService.findLatestRecommendationFetchedAt(symbol), "/stock/recommendation", symbol);
        if (isFresh(latestFetch, properties.recommendationCacheHours(), TimeUnit.HOURS)) {
            return new CachedValue<>(cached, true);
        }

        FinnhubResponse response = client.get("/stock/recommendation", Map.of("symbol", symbol));
        log("/stock/recommendation", symbol, response);
        if (!response.success()) {
            return new CachedValue<>(cached, true);
        }

        List<StockRecommendationTrend> recommendations = parseRecommendations(symbol, response.body());
        cacheService.saveRecommendations(recommendations);
        return new CachedValue<>(recommendations.stream().limit(4).toList(), false);
    }

    private CachedValue<List<StockEpsSurprise>> epsSurprises(String symbol) {
        List<StockEpsSurprise> cached = cacheService.findRecentEpsSurprises(symbol, 4);
        if (!epsSurprisesEnabled) {
            return new CachedValue<>(cached, true);
        }
        LocalDateTime latestFetch = latestFetch(
                cacheService.findLatestEpsSurpriseFetchedAt(symbol), "/stock/earnings", symbol);
        if (isFresh(latestFetch, properties.epsSurprisesCacheHours(), TimeUnit.HOURS)) {
            return new CachedValue<>(cached, true);
        }

        FinnhubResponse response = client.get("/stock/earnings", Map.of("symbol", symbol, "limit", "4"));
        log("/stock/earnings", symbol, response);
        if (!response.success()) {
            return new CachedValue<>(cached, true);
        }

        List<StockEpsSurprise> surprises = parseEpsSurprises(symbol, response.body());
        cacheService.saveEpsSurprises(surprises);
        return new CachedValue<>(surprises.stream().limit(4).toList(), false);
    }

    private CachedValue<List<StockCandleDaily>> candles(String symbol) {
        List<StockCandleDaily> cached = cacheService.findRecentCandles(symbol, 30);
        if (!candleEnabled) {
            return new CachedValue<>(cached, true);
        }
        if (isFresh(cacheService.findLatestCandleUpdatedAt(symbol), properties.candleCacheHours(), TimeUnit.HOURS)) {
            return new CachedValue<>(cached, true);
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(1);
        FinnhubResponse response = client.get("/stock/candle", Map.of(
                "symbol", symbol,
                "resolution", "D",
                "from", String.valueOf(epochStart(from)),
                "to", String.valueOf(epochStart(to.plusDays(1)))));
        log("/stock/candle", symbol, response);
        if (!response.success()) {
            return new CachedValue<>(cached, true);
        }

        List<StockCandleDaily> candles = parseCandles(symbol, response.body());
        cacheService.saveCandles(candles);
        return new CachedValue<>(candles.stream()
                .sorted((left, right) -> right.getTradeDate().compareTo(left.getTradeDate()))
                .limit(30)
                .toList(), false);
    }

    private StockQuoteCache parseQuote(String symbol, String json) {
        JsonNode root = readTree(json);
        StockQuoteCache quote = new StockQuoteCache();
        quote.setSymbol(symbol);
        quote.setCurrentPrice(decimal(root, "c"));
        quote.setOpenPrice(decimal(root, "o"));
        quote.setHighPrice(decimal(root, "h"));
        quote.setLowPrice(decimal(root, "l"));
        quote.setPreviousClose(decimal(root, "pc"));
        quote.setQuoteTime(epochSeconds(root, "t"));
        quote.setRawJson(json);
        quote.setFetchedAt(LocalDateTime.now());
        return quote;
    }

    private CompanyProfile parseProfile(String symbol, String json) {
        JsonNode root = readTree(json);
        CompanyProfile profile = new CompanyProfile();
        profile.setSymbol(symbol);
        profile.setName(text(root, "name"));
        profile.setCountry(text(root, "country"));
        profile.setCurrency(text(root, "currency"));
        profile.setExchange(text(root, "exchange"));
        profile.setFinnhubIndustry(text(root, "finnhubIndustry"));
        profile.setMarketCap(decimal(root, "marketCapitalization"));
        profile.setShareOutstanding(decimal(root, "shareOutstanding"));
        profile.setLogo(text(root, "logo"));
        profile.setWeburl(text(root, "weburl"));
        profile.setRawJson(json);
        profile.setFetchedAt(LocalDateTime.now());
        return profile;
    }

    private StockMetricSnapshot parseMetric(String symbol, String json) {
        JsonNode root = readTree(json);
        JsonNode metricNode = Optional.ofNullable(root.get("metric")).orElse(root);
        StockMetricSnapshot metric = new StockMetricSnapshot();
        metric.setSymbol(symbol);
        metric.setMetricDate(LocalDate.now());
        metric.setPeNormalizedAnnual(decimal(metricNode, "peNormalizedAnnual"));
        metric.setPbAnnual(decimal(metricNode, "pbAnnual"));
        metric.setRoeTtm(decimal(metricNode, "roeTTM"));
        metric.setEpsTtm(firstDecimal(metricNode, "epsInclExtraItemsTTM", "epsExclExtraItemsTTM"));
        metric.setWeek52High(decimal(metricNode, "52WeekHigh"));
        metric.setWeek52Low(decimal(metricNode, "52WeekLow"));
        metric.setRawJson(json);
        metric.setFetchedAt(LocalDateTime.now());
        return metric;
    }

    private List<CompanyNews> parseNews(String symbol, String json) {
        JsonNode root = readTree(json);
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<CompanyNews> results = new ArrayList<>();
        LocalDateTime fetchedAt = LocalDateTime.now();
        for (int index = 0; index < root.size(); index++) {
            JsonNode item = root.get(index);
            Long newsId = longValue(item, "id");
            if (newsId == null) {
                continue;
            }
            CompanyNews news = new CompanyNews();
            news.setSymbol(symbol);
            news.setNewsId(newsId);
            news.setHeadline(text(item, "headline"));
            news.setSummary(text(item, "summary"));
            news.setUrl(text(item, "url"));
            news.setSource(text(item, "source"));
            news.setPublishedAt(epochSeconds(item, "datetime"));
            news.setRawJson(item.toString());
            news.setFetchedAt(fetchedAt);
            results.add(news);
        }
        return results;
    }

    private List<StockRecommendationTrend> parseRecommendations(String symbol, String json) {
        JsonNode root = readTree(json);
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<StockRecommendationTrend> results = new ArrayList<>();
        LocalDateTime fetchedAt = LocalDateTime.now();
        for (int index = 0; index < root.size(); index++) {
            JsonNode item = root.get(index);
            LocalDate periodDate = localDate(item, "period");
            if (periodDate == null) {
                continue;
            }
            StockRecommendationTrend recommendation = new StockRecommendationTrend();
            recommendation.setSymbol(symbol);
            recommendation.setPeriodDate(periodDate);
            recommendation.setStrongBuy(integer(item, "strongBuy"));
            recommendation.setBuy(integer(item, "buy"));
            recommendation.setHold(integer(item, "hold"));
            recommendation.setSell(integer(item, "sell"));
            recommendation.setStrongSell(integer(item, "strongSell"));
            recommendation.setRawJson(item.toString());
            recommendation.setFetchedAt(fetchedAt);
            results.add(recommendation);
        }
        return results;
    }

    private List<StockEpsSurprise> parseEpsSurprises(String symbol, String json) {
        JsonNode root = readTree(json);
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<StockEpsSurprise> results = new ArrayList<>();
        LocalDateTime fetchedAt = LocalDateTime.now();
        for (int index = 0; index < root.size(); index++) {
            JsonNode item = root.get(index);
            LocalDate periodDate = localDate(item, "period");
            if (periodDate == null) {
                continue;
            }
            StockEpsSurprise surprise = new StockEpsSurprise();
            surprise.setSymbol(symbol);
            surprise.setPeriodDate(periodDate);
            surprise.setFiscalYear(integer(item, "year"));
            surprise.setFiscalQuarter(integer(item, "quarter"));
            surprise.setActual(decimal(item, "actual"));
            surprise.setEstimate(decimal(item, "estimate"));
            surprise.setSurprise(decimal(item, "surprise"));
            surprise.setSurprisePercent(decimal(item, "surprisePercent"));
            surprise.setRawJson(item.toString());
            surprise.setFetchedAt(fetchedAt);
            results.add(surprise);
        }
        return results;
    }

    private List<StockCandleDaily> parseCandles(String symbol, String json) {
        JsonNode root = readTree(json);
        if (root == null || !"ok".equals(text(root, "s"))) {
            return List.of();
        }

        JsonNode timestamps = root.get("t");
        JsonNode opens = root.get("o");
        JsonNode highs = root.get("h");
        JsonNode lows = root.get("l");
        JsonNode closes = root.get("c");
        JsonNode volumes = root.get("v");
        if (!sameArraySize(timestamps, opens, highs, lows, closes)) {
            return List.of();
        }

        List<StockCandleDaily> results = new ArrayList<>();
        for (int index = 0; index < timestamps.size(); index++) {
            StockCandleDaily candle = new StockCandleDaily();
            candle.setSymbol(symbol);
            candle.setTradeDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamps.get(index).longValue()),
                    ZoneId.systemDefault()).toLocalDate());
            candle.setOpenPrice(decimalAt(opens, index));
            candle.setHighPrice(decimalAt(highs, index));
            candle.setLowPrice(decimalAt(lows, index));
            candle.setClosePrice(decimalAt(closes, index));
            candle.setVolume(volumes != null && volumes.isArray() && volumes.size() > index
                    ? volumes.get(index).longValue()
                    : null);
            candle.setSource(PROVIDER);
            if (candle.getOpenPrice() != null && candle.getHighPrice() != null && candle.getLowPrice() != null
                    && candle.getClosePrice() != null) {
                results.add(candle);
            }
        }
        return results;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Finnhub JSON.", ex);
        }
    }

    private void log(String endpoint, String symbol, FinnhubResponse response) {
        cacheService.logApiCall(PROVIDER, endpoint, symbol, response);
    }

    private LocalDateTime latestFetch(LocalDateTime tableFetchedAt, String endpoint, String symbol) {
        LocalDateTime callFetchedAt = cacheService.findLatestSuccessfulCallAt(endpoint, symbol);
        if (tableFetchedAt == null) {
            return callFetchedAt;
        }
        if (callFetchedAt == null) {
            return tableFetchedAt;
        }
        return tableFetchedAt.isAfter(callFetchedAt) ? tableFetchedAt : callFetchedAt;
    }

    private static boolean isFresh(LocalDateTime fetchedAt, int amount, TimeUnit unit) {
        if (fetchedAt == null) {
            return false;
        }
        LocalDateTime threshold = switch (unit) {
            case SECONDS -> LocalDateTime.now().minusSeconds(amount);
            case MINUTES -> LocalDateTime.now().minusMinutes(amount);
            case HOURS -> LocalDateTime.now().minusHours(amount);
        };
        return fetchedAt.isAfter(threshold);
    }

    private static BigDecimal firstDecimal(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            BigDecimal value = decimal(node, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return new BigDecimal(text);
    }

    private static BigDecimal decimalAt(JsonNode node, int index) {
        if (node == null || !node.isArray() || node.size() <= index) {
            return null;
        }
        JsonNode value = node.get(index);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return new BigDecimal(text);
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text : null;
    }

    private static LocalDateTime epochSeconds(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || !value.isNumber() || value.longValue() <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(value.longValue()), ZoneId.systemDefault());
    }

    private static Long longValue(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Long.valueOf(text);
    }

    private static Integer integer(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Integer.valueOf(text);
    }

    private static LocalDate localDate(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private static long epochStart(LocalDate date) {
        return date.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    }

    private static boolean sameArraySize(JsonNode first, JsonNode... others) {
        if (first == null || !first.isArray()) {
            return false;
        }
        for (JsonNode other : others) {
            if (other == null || !other.isArray() || other.size() != first.size()) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeSymbol(String requestedSymbol) {
        if (!StringUtils.hasText(requestedSymbol)) {
            return "AAPL";
        }
        return requestedSymbol.trim().toUpperCase();
    }

    private static String summarize(StockQuoteCache quote, StockMetricSnapshot metric) {
        if (quote == null) {
            return "No quote data is available for this symbol.";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("Current price ").append(quote.getCurrentPrice() == null ? "N/A" : quote.getCurrentPrice());
        if (quote.getPreviousClose() != null && quote.getCurrentPrice() != null) {
            BigDecimal change = quote.getCurrentPrice().subtract(quote.getPreviousClose());
            summary.append(", change vs previous close ").append(change);
        }
        if (metric != null && metric.getPeNormalizedAnnual() != null) {
            summary.append(", PER ").append(metric.getPeNormalizedAnnual());
        }
        if (metric != null && metric.getPbAnnual() != null) {
            summary.append(", PBR ").append(metric.getPbAnnual());
        }
        return summary.toString();
    }

    private enum TimeUnit {
        SECONDS,
        MINUTES,
        HOURS
    }

    private record CachedValue<T>(T value, boolean cacheHit) {
    }
}
