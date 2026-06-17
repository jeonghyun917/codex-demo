package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class StockDataQualityService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final ObjectProvider<StockDataQualityMapper> dataQualityMapper;
    private final StockCacheService cacheService;
    private final ObjectMapper objectMapper;

    public StockDataQualityService(ObjectProvider<StockDataQualityMapper> dataQualityMapper,
            StockCacheService cacheService, ObjectMapper objectMapper) {
        this.dataQualityMapper = dataQualityMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    public StockDataQualityLatest assessAndSave(StockDashboard dashboard) {
        StockDataQualityLatest quality = assess(dashboard);
        StockDataQualityMapper mapper = dataQualityMapper.getIfAvailable();
        if (mapper != null) {
            mapper.upsert(quality);
        }
        return quality;
    }

    public StockDataQualityLatest findLatest(String symbol) {
        StockDataQualityMapper mapper = dataQualityMapper.getIfAvailable();
        if (mapper == null || symbol == null || symbol.isBlank()) {
            return null;
        }
        return mapper.findBySymbol(symbol.trim().toUpperCase(Locale.ROOT));
    }

    public BigDecimal clean(StockDataQualityLatest quality, String field, BigDecimal value) {
        return isExcluded(quality, field) ? null : value;
    }

    public boolean isExcluded(StockDataQualityLatest quality, String field) {
        if (quality == null || field == null || field.isBlank()) {
            return false;
        }
        JsonNode root = readTree(quality.getExcludedFieldsJson());
        if (root == null || !root.isArray()) {
            return false;
        }
        for (int index = 0; index < root.size(); index++) {
            if (field.equalsIgnoreCase(root.get(index).asText())) {
                return true;
            }
        }
        return false;
    }

    public String metricNote(StockDataQualityLatest quality, String field, String fallback) {
        return isExcluded(quality, field) ? "품질 룰로 분석 제외" : fallback;
    }

    public List<String> issueMessages(StockDataQualityLatest quality, int limit) {
        JsonNode root = readTree(quality == null ? null : quality.getIssuesJson());
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<String> messages = new ArrayList<>();
        for (int index = 0; index < root.size() && messages.size() < limit; index++) {
            JsonNode message = root.get(index).get("message");
            if (message != null && !message.isNull() && !message.asText().isBlank()) {
                messages.add(message.asText());
            }
        }
        return messages;
    }

    private StockDataQualityLatest assess(StockDashboard dashboard) {
        StockDashboard safeDashboard = dashboard == null
                ? new StockDashboard("", false, false, false, false, false, false, false, false, false,
                        "No dashboard.", null, null, null, List.of(), List.of(), List.of(), List.of(), null)
                : dashboard;
        String symbol = safeDashboard.symbol() == null ? "" : safeDashboard.symbol().trim().toUpperCase(Locale.ROOT);
        CompanyProfile profile = safeDashboard.profile();
        StockQuoteCache quote = safeDashboard.quote();
        StockMetricSnapshot metric = safeDashboard.metric();
        JsonNode metricNode = readMetricNode(metric);
        SecFinancialStandard annual = cacheService.findLatestSecAnnualFinancial(symbol);
        SecFinancialStandard quarter = cacheService.findLatestSecQuarterFinancial(symbol);
        StockInstitutionFlow institutionFlow = cacheService.findLatestInstitutionFlow(symbol);

        List<Map<String, String>> issues = new ArrayList<>();
        Set<String> excludedFields = new LinkedHashSet<>();
        Set<String> staleSources = new LinkedHashSet<>();

        int coverageScore = coverageScore(safeDashboard, annual, quarter, institutionFlow);
        freshnessChecks(profile, quote, metric, safeDashboard.candles(), annual, quarter, safeDashboard.news(),
                staleSources, issues);
        outlierChecks(quote, metric, metricNode, excludedFields, issues);
        consistencyChecks(profile, quote, metric, metricNode, safeDashboard.candles(), annual, excludedFields, issues);

        int freshnessScore = clamp(100 - staleSources.size() * 15);
        int outlierScore = clamp(100 - excludedFields.size() * 25);
        int consistencyIssueCount = (int) issues.stream()
                .filter(issue -> "consistency".equals(issue.get("type")))
                .count();
        int consistencyScore = clamp(100 - consistencyIssueCount * 18);
        int qualityScore = Math.round(coverageScore * 0.35f
                + freshnessScore * 0.20f
                + outlierScore * 0.25f
                + consistencyScore * 0.20f);
        if (excludedFields.size() >= 4) {
            qualityScore = Math.min(qualityScore, 60);
        } else if (excludedFields.size() >= 2) {
            qualityScore = Math.min(qualityScore, 74);
        } else if (excludedFields.size() == 1) {
            qualityScore = Math.min(qualityScore, 89);
        }

        StockDataQualityLatest quality = new StockDataQualityLatest();
        quality.setSymbol(symbol);
        quality.setCalculatedAt(LocalDateTime.now());
        quality.setQualityScore(clamp(qualityScore));
        quality.setQualityLabel(label(qualityScore));
        quality.setTone(tone(qualityScore));
        quality.setCoverageScore(coverageScore);
        quality.setFreshnessScore(freshnessScore);
        quality.setOutlierScore(outlierScore);
        quality.setConsistencyScore(consistencyScore);
        quality.setIssueCount(issues.size());
        quality.setExcludedMetricCount(excludedFields.size());
        quality.setStaleSourcesJson(writeJson(new ArrayList<>(staleSources)));
        quality.setExcludedFieldsJson(writeJson(new ArrayList<>(excludedFields)));
        quality.setIssuesJson(writeJson(issues));
        quality.setRawJson(writeJson(rawPayload(safeDashboard, annual, quarter, quality, staleSources, excludedFields)));
        return quality;
    }

    private int coverageScore(StockDashboard dashboard, SecFinancialStandard annual, SecFinancialStandard quarter,
            StockInstitutionFlow institutionFlow) {
        int covered = 0;
        covered += dashboard.profile() == null ? 0 : 1;
        covered += dashboard.quote() == null ? 0 : 1;
        covered += dashboard.metric() == null ? 0 : 1;
        covered += annual == null ? 0 : 1;
        covered += quarter == null ? 0 : 1;
        covered += dashboard.candles() == null || dashboard.candles().size() < 20 ? 0 : 1;
        covered += dashboard.news() == null || dashboard.news().isEmpty() ? 0 : 1;
        covered += dashboard.recommendations() == null || dashboard.recommendations().isEmpty() ? 0 : 1;
        covered += dashboard.epsSurprises() == null || dashboard.epsSurprises().isEmpty() ? 0 : 1;
        covered += institutionFlow == null ? 0 : 1;
        return Math.round(covered * 100.0f / 10.0f);
    }

    private void freshnessChecks(CompanyProfile profile, StockQuoteCache quote, StockMetricSnapshot metric,
            List<StockCandleDaily> candles, SecFinancialStandard annual, SecFinancialStandard quarter,
            List<CompanyNews> news, Set<String> staleSources, List<Map<String, String>> issues) {
        LocalDateTime now = LocalDateTime.now();
        if (profile == null) {
            stale(staleSources, issues, "profile", "company_profile 데이터가 없습니다.");
        } else if (olderThan(profile.getFetchedAt(), now, 45)) {
            stale(staleSources, issues, "profile", "company_profile 수집 시각이 45일보다 오래됐습니다.");
        }
        if (quote == null) {
            stale(staleSources, issues, "quote", "quote 캐시가 없습니다.");
        } else if (olderThan(quote.getFetchedAt(), now, 3)) {
            stale(staleSources, issues, "quote", "quote 캐시가 3일보다 오래됐습니다.");
        }
        if (metric == null) {
            stale(staleSources, issues, "metric", "basic financial metric 데이터가 없습니다.");
        } else if (olderThan(metric.getFetchedAt(), now, 14)) {
            stale(staleSources, issues, "metric", "basic financial metric 수집 시각이 14일보다 오래됐습니다.");
        }

        LocalDate latestCandle = candles == null ? null : candles.stream()
                .map(StockCandleDaily::getTradeDate)
                .filter(value -> value != null)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latestCandle == null) {
            stale(staleSources, issues, "candle", "일봉 데이터가 없습니다.");
        } else if (ChronoUnit.DAYS.between(latestCandle, LocalDate.now()) > 7) {
            stale(staleSources, issues, "candle", "최신 일봉이 7일보다 오래됐습니다.");
        }

        if (annual == null) {
            stale(staleSources, issues, "secAnnual", "SEC 연간 표준 재무 데이터가 없습니다.");
        } else if (annual.getFiledAt() == null || ChronoUnit.DAYS.between(annual.getFiledAt(), LocalDate.now()) > 540) {
            stale(staleSources, issues, "secAnnual", "SEC 연간 재무 제출일이 540일보다 오래됐습니다.");
        }
        if (quarter == null) {
            stale(staleSources, issues, "secQuarter", "SEC 분기 표준 재무 데이터가 없습니다.");
        } else if (quarter.getFiledAt() == null
                || ChronoUnit.DAYS.between(quarter.getFiledAt(), LocalDate.now()) > 210) {
            stale(staleSources, issues, "secQuarter", "SEC 분기 재무 제출일이 210일보다 오래됐습니다.");
        }

        LocalDateTime latestNews = news == null ? null : news.stream()
                .map(CompanyNews::getPublishedAt)
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (latestNews != null && olderThan(latestNews, now, 45)) {
            stale(staleSources, issues, "news", "최근 뉴스가 45일보다 오래됐습니다.");
        }
    }

    private void outlierChecks(StockQuoteCache quote, StockMetricSnapshot metric, JsonNode metricNode,
            Set<String> excludedFields, List<Map<String, String>> issues) {
        if (quote != null) {
            excludeIf(quote.getCurrentPrice() != null && quote.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0,
                    "currentPrice", "quote 현재가가 0 이하라 가격 기반 계산에서 제외합니다.",
                    quote.getCurrentPrice(), excludedFields, issues);
            excludeIf(quote.getPreviousClose() != null && quote.getPreviousClose().compareTo(BigDecimal.ZERO) <= 0,
                    "previousClose", "전일 종가가 0 이하라 등락률 계산에서 제외합니다.",
                    quote.getPreviousClose(), excludedFields, issues);
            boolean ohlcBroken = quote.getHighPrice() != null && quote.getLowPrice() != null
                    && quote.getHighPrice().compareTo(quote.getLowPrice()) < 0;
            excludeIf(ohlcBroken, "quoteRange", "quote 고가가 저가보다 낮아 가격 범위 계산에서 제외합니다.",
                    null, excludedFields, issues);
        }
        if (metric == null && metricNode == null) {
            return;
        }
        BigDecimal pe = metric == null ? null : metric.getPeNormalizedAnnual();
        BigDecimal pb = metric == null ? null : metric.getPbAnnual();
        BigDecimal roe = metric == null ? null : metric.getRoeTtm();
        BigDecimal eps = metric == null ? null : metric.getEpsTtm();
        BigDecimal high = metric == null ? metricDecimal(metricNode, "52WeekHigh") : metric.getWeek52High();
        BigDecimal low = metric == null ? metricDecimal(metricNode, "52WeekLow") : metric.getWeek52Low();
        BigDecimal ps = metricDecimal(metricNode, "psTTM");
        BigDecimal netMargin = metricDecimal(metricNode, "netProfitMarginTTM");
        BigDecimal currentRatio = metricDecimal(metricNode, "currentRatioQuarterly");
        BigDecimal debtToEquity = metricDecimal(metricNode, "totalDebt/totalEquityQuarterly");

        excludeIf(pe != null && (lte(pe, "0") || gte(pe, "300")), "peNormalizedAnnual",
                "PER가 0 이하 또는 300배 이상이라 밸류에이션 계산에서 제외합니다.", pe, excludedFields, issues);
        excludeIf(pb != null && (lte(pb, "0") || gte(pb, "100")), "pbAnnual",
                "PBR이 0 이하 또는 100배 이상이라 밸류에이션 계산에서 제외합니다.", pb, excludedFields, issues);
        excludeIf(ps != null && (lte(ps, "0") || gte(ps, "100")), "psTTM",
                "PSR이 0 이하 또는 100배 이상이라 밸류에이션 계산에서 제외합니다.", ps, excludedFields, issues);
        excludeIf(roe != null && roe.abs().compareTo(BigDecimal.valueOf(300)) > 0, "roeTTM",
                "ROE 절대값이 300%를 넘어 수익성 계산에서 제외합니다.", roe, excludedFields, issues);
        excludeIf(eps != null && eps.abs().compareTo(BigDecimal.valueOf(1000)) > 0, "epsTTM",
                "EPS 절대값이 1000 USD를 넘어 수익성 계산에서 제외합니다.", eps, excludedFields, issues);
        excludeIf(netMargin != null && netMargin.abs().compareTo(BigDecimal.valueOf(100)) > 0,
                "netProfitMarginTTM", "순이익률 절대값이 100%를 넘어 수익성 계산에서 제외합니다.",
                netMargin, excludedFields, issues);
        excludeIf(currentRatio != null && (lte(currentRatio, "0") || gte(currentRatio, "20")),
                "currentRatioQuarterly", "유동비율이 0 이하 또는 20배 이상이라 안정성 계산에서 제외합니다.",
                currentRatio, excludedFields, issues);
        excludeIf(debtToEquity != null && debtToEquity.abs().compareTo(BigDecimal.valueOf(1000)) > 0,
                "totalDebt/totalEquityQuarterly", "부채/자본 비율 절대값이 1000%를 넘어 안정성 계산에서 제외합니다.",
                debtToEquity, excludedFields, issues);
        boolean rangeBroken = high != null && low != null
                && (high.compareTo(BigDecimal.ZERO) <= 0 || low.compareTo(BigDecimal.ZERO) <= 0
                        || high.compareTo(low) <= 0);
        if (rangeBroken) {
            excludedFields.add("52WeekHigh");
            excludedFields.add("52WeekLow");
            issues.add(issue("outlier", "52WeekHigh/52WeekLow",
                    "52주 고가/저가 범위가 깨져 모멘텀 계산에서 제외합니다.", high + "/" + low));
        }
    }

    private void consistencyChecks(CompanyProfile profile, StockQuoteCache quote, StockMetricSnapshot metric,
            JsonNode metricNode, List<StockCandleDaily> candles, SecFinancialStandard annual,
            Set<String> excludedFields, List<Map<String, String>> issues) {
        if (profile != null && quote != null && profile.getMarketCap() != null
                && profile.getShareOutstanding() != null && quote.getCurrentPrice() != null
                && profile.getMarketCap().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal impliedMarketCap = quote.getCurrentPrice().multiply(profile.getShareOutstanding());
            BigDecimal diff = relativeDiff(impliedMarketCap, profile.getMarketCap());
            if (diff != null && diff.compareTo(BigDecimal.valueOf(60)) > 0) {
                issues.add(issue("consistency", "marketCap",
                        "profile 시가총액과 quote 현재가 x 발행주식수 차이가 60%를 넘습니다.",
                        formatPercent(diff)));
            }
        }

        BigDecimal latestClose = candles == null ? null : candles.stream()
                .filter(candle -> candle.getTradeDate() != null && candle.getClosePrice() != null)
                .max((left, right) -> left.getTradeDate().compareTo(right.getTradeDate()))
                .map(StockCandleDaily::getClosePrice)
                .orElse(null);
        if (quote != null && quote.getCurrentPrice() != null && latestClose != null
                && quote.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = relativeDiff(quote.getCurrentPrice(), latestClose);
            if (diff != null && diff.compareTo(BigDecimal.valueOf(30)) > 0) {
                issues.add(issue("consistency", "currentPrice",
                        "quote 현재가와 최신 일봉 종가 차이가 30%를 넘습니다.", formatPercent(diff)));
            }
        }

        BigDecimal high = metric == null ? metricDecimal(metricNode, "52WeekHigh") : metric.getWeek52High();
        BigDecimal low = metric == null ? metricDecimal(metricNode, "52WeekLow") : metric.getWeek52Low();
        if (quote != null && quote.getCurrentPrice() != null && high != null && low != null) {
            boolean outside = quote.getCurrentPrice().compareTo(high.multiply(BigDecimal.valueOf(1.10))) > 0
                    || quote.getCurrentPrice().compareTo(low.multiply(BigDecimal.valueOf(0.90))) < 0;
            if (outside) {
                issues.add(issue("consistency", "52WeekHigh/52WeekLow",
                        "현재가가 52주 범위와 10% 이상 어긋납니다.", quote.getCurrentPrice().toPlainString()));
            }
        }

        BigDecimal finnhubRoe = metric == null ? null : metric.getRoeTtm();
        BigDecimal secRoe = secRoe(annual);
        if (finnhubRoe != null && secRoe != null && finnhubRoe.subtract(secRoe).abs().compareTo(BigDecimal.valueOf(100)) > 0) {
            issues.add(issue("consistency", "roeTTM",
                    "Finnhub ROE와 SEC 기반 ROE 차이가 100%p를 넘습니다.", formatPercent(finnhubRoe.subtract(secRoe).abs())));
            if (finnhubRoe.abs().compareTo(BigDecimal.valueOf(300)) > 0) {
                excludedFields.add("roeTTM");
            }
        }

        BigDecimal finnhubPs = metricDecimal(metricNode, "psTTM");
        BigDecimal secPs = secPs(profile, annual);
        if (finnhubPs != null && secPs != null && finnhubPs.compareTo(BigDecimal.ZERO) > 0
                && secPs.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = finnhubPs.divide(secPs, 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(BigDecimal.valueOf(3)) > 0 || ratio.compareTo(new BigDecimal("0.33")) < 0) {
                issues.add(issue("consistency", "psTTM",
                        "Finnhub PSR과 SEC 기반 PSR 차이가 3배 이상입니다.", ratio.toPlainString() + "x"));
            }
        }
    }

    private void stale(Set<String> staleSources, List<Map<String, String>> issues, String source, String message) {
        staleSources.add(source);
        issues.add(issue("freshness", source, message, null));
    }

    private void excludeIf(boolean condition, String field, String message, BigDecimal value,
            Set<String> excludedFields, List<Map<String, String>> issues) {
        if (!condition) {
            return;
        }
        excludedFields.add(field);
        issues.add(issue("outlier", field, message, value == null ? null : value.toPlainString()));
    }

    private Map<String, String> issue(String type, String field, String message, String value) {
        Map<String, String> issue = new LinkedHashMap<>();
        issue.put("type", type);
        issue.put("field", field);
        issue.put("message", message);
        if (value != null) {
            issue.put("value", value);
        }
        return issue;
    }

    private Map<String, Object> rawPayload(StockDashboard dashboard, SecFinancialStandard annual,
            SecFinancialStandard quarter, StockDataQualityLatest quality, Set<String> staleSources,
            Set<String> excludedFields) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("symbol", dashboard.symbol());
        raw.put("qualityScore", quality.getQualityScore());
        raw.put("coverageScore", quality.getCoverageScore());
        raw.put("freshnessScore", quality.getFreshnessScore());
        raw.put("outlierScore", quality.getOutlierScore());
        raw.put("consistencyScore", quality.getConsistencyScore());
        raw.put("staleSources", staleSources);
        raw.put("excludedFields", excludedFields);
        raw.put("hasProfile", dashboard.profile() != null);
        raw.put("hasQuote", dashboard.quote() != null);
        raw.put("hasMetric", dashboard.metric() != null);
        raw.put("candleCount", dashboard.candles() == null ? 0 : dashboard.candles().size());
        raw.put("newsCount", dashboard.news() == null ? 0 : dashboard.news().size());
        raw.put("recommendationCount", dashboard.recommendations() == null ? 0 : dashboard.recommendations().size());
        raw.put("epsSurpriseCount", dashboard.epsSurprises() == null ? 0 : dashboard.epsSurprises().size());
        raw.put("secAnnualFiledAt", annual == null ? null : annual.getFiledAt());
        raw.put("secQuarterFiledAt", quarter == null ? null : quarter.getFiledAt());
        return raw;
    }

    private JsonNode readMetricNode(StockMetricSnapshot metric) {
        JsonNode root = readTree(metric == null ? null : metric.getRawJson());
        return root == null ? null : root.get("metric");
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static BigDecimal metricDecimal(JsonNode metricNode, String fieldName) {
        JsonNode value = metricNode == null ? null : metricNode.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean olderThan(LocalDateTime value, LocalDateTime now, long days) {
        return value == null || ChronoUnit.DAYS.between(value, now) > days;
    }

    private static BigDecimal secRoe(SecFinancialStandard annual) {
        if (annual == null || annual.getNetIncome() == null || annual.getEquity() == null
                || annual.getEquity().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return annual.getNetIncome().multiply(ONE_HUNDRED)
                .divide(annual.getEquity(), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal secPs(CompanyProfile profile, SecFinancialStandard annual) {
        if (profile == null || profile.getMarketCap() == null || annual == null
                || annual.getRevenue() == null || annual.getRevenue().compareTo(BigDecimal.ZERO) <= 0
                || annual.getCurrency() != null && !"USD".equalsIgnoreCase(annual.getCurrency())) {
            return null;
        }
        return profile.getMarketCap().multiply(BigDecimal.valueOf(1_000_000L))
                .divide(annual.getRevenue(), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal relativeDiff(BigDecimal left, BigDecimal right) {
        if (left == null || right == null || right.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return left.subtract(right).abs().multiply(ONE_HUNDRED)
                .divide(right.abs(), 4, RoundingMode.HALF_UP);
    }

    private static boolean gte(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) >= 0;
    }

    private static boolean lte(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) <= 0;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String formatPercent(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static String label(int score) {
        if (score >= 85) {
            return "데이터 품질 높음";
        }
        if (score >= 70) {
            return "데이터 품질 보통";
        }
        if (score >= 50) {
            return "데이터 품질 주의";
        }
        return "데이터 품질 낮음";
    }

    private static String tone(int score) {
        if (score >= 85) {
            return "positive";
        }
        if (score >= 70) {
            return "neutral";
        }
        if (score >= 50) {
            return "caution";
        }
        return "negative";
    }
}
