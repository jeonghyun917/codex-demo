package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class StockSignalService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final String SIGNAL_VERSION = "v1";

    private final StockCacheService cacheService;
    private final ObjectMapper objectMapper;

    public StockSignalService(StockCacheService cacheService, ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    public StockInfoView.StockSignalView buildStored(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        StockSignalLatest latest = cacheService.findLatestSignal(normalized);
        return latest == null ? null : toView(latest);
    }

    public StockSignalLatest buildLatest(String symbol) {
        StockDashboard dashboard = cachedDashboard(symbol);
        StockInfoView.StockSignalView view = build(dashboard);
        return toLatest(dashboard, view);
    }

    private StockDashboard cachedDashboard(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        CompanyProfile profile = cacheService.findProfile(normalized);
        StockQuoteCache quote = cacheService.findQuote(normalized);
        StockMetricSnapshot metric = cacheService.findLatestMetric(normalized);
        List<CompanyNews> news = cacheService.findRecentNews(normalized, 5);
        List<StockRecommendationTrend> recommendations = cacheService.findRecentRecommendations(normalized, 4);
        List<StockEpsSurprise> epsSurprises = cacheService.findRecentEpsSurprises(normalized, 4);
        List<StockCandleDaily> candles = cacheService.findRecentCandles(normalized, 30);

        return new StockDashboard(normalized, false, cacheService.databaseEnabled(),
                quote != null, profile != null, metric != null, !news.isEmpty(), !recommendations.isEmpty(),
                !epsSurprises.isEmpty(), !candles.isEmpty(), "Cached Finnhub data loaded.", profile, quote, metric,
                news, recommendations, epsSurprises, candles, null);
    }

    public StockInfoView.StockSignalView build(StockDashboard dashboard) {
        StockMetricSnapshot metric = dashboard.metric();
        JsonNode metricNode = readMetricNode(metric);
        SignalResult valuation = valuationSignal(dashboard, metricNode);
        SignalResult quality = qualitySignal(metric, metricNode);
        SignalResult earnings = earningsSignal(dashboard.epsSurprises());
        SignalResult analyst = analystSignal(dashboard.recommendations());
        SignalResult news = newsSignal(dashboard.news());
        SignalResult momentum = momentumSignal(dashboard.quote(), metric, dashboard.candles());

        List<SignalResult> results = List.of(valuation, quality, earnings, analyst, news, momentum);
        int score = integratedScore(results);
        String label = integratedLabel(score);
        String tone = tone(score);
        String confidence = confidence(results);
        List<StockInfoView.SignalCard> cards = results.stream()
                .map(result -> new StockInfoView.SignalCard(result.title(), result.label(), result.tone(),
                        result.score() + "점", result.detail()))
                .toList();
        List<String> reasons = results.stream()
                .filter(SignalResult::available)
                .sorted((left, right) -> Integer.compare(Math.abs(right.score() - 50), Math.abs(left.score() - 50)))
                .limit(3)
                .map(result -> result.title() + ": " + result.detail())
                .toList();

        return new StockInfoView.StockSignalView(label, tone, score + " / 100", confidence,
                label + "입니다. 현재 보유한 캐시 데이터 기준의 투자 판단 보조 신호이며 확정 예측은 아닙니다.",
                cards, reasons);
    }

    public StockInfoView.StockSignalView toView(StockSignalLatest latest) {
        return new StockInfoView.StockSignalView(
                fallback(latest.getIntegratedLabel(), "-"),
                fallback(latest.getTone(), "neutral"),
                formatScore(latest.getIntegratedScore()),
                fallback(latest.getConfidence(), "-"),
                fallback(latest.getSummary(), "Stored stock signal."),
                readCards(latest),
                readReasons(latest.getReasonsJson()));
    }

    private StockSignalLatest toLatest(StockDashboard dashboard, StockInfoView.StockSignalView view) {
        StockSignalLatest latest = new StockSignalLatest();
        latest.setSymbol(dashboard.symbol());
        latest.setCalculatedAt(LocalDateTime.now());
        latest.setSignalVersion(SIGNAL_VERSION);
        latest.setIntegratedScore(parseScore(view.score()));
        latest.setIntegratedLabel(view.integratedLabel());
        latest.setTone(view.tone());
        latest.setConfidence(view.confidence());
        latest.setSummary(view.summary());
        latest.setReasonsJson(writeJson(view.reasons()));
        latest.setCardsJson(writeJson(view.cards()));
        latest.setSourceFreshnessJson(writeJson(sourceFreshness(dashboard)));
        latest.setRawJson(writeJson(Map.of(
                "symbol", dashboard.symbol(),
                "signalVersion", SIGNAL_VERSION,
                "view", view)));

        for (StockInfoView.SignalCard card : view.cards()) {
            applyCard(latest, card);
        }
        return latest;
    }

    private void applyCard(StockSignalLatest latest, StockInfoView.SignalCard card) {
        String title = card.title();
        if ("Valuation".equals(title)) {
            latest.setValuationScore(parseScore(card.score()));
            latest.setValuationLabel(card.label());
        } else if ("Quality".equals(title)) {
            latest.setQualityScore(parseScore(card.score()));
            latest.setQualityLabel(card.label());
        } else if ("Earnings".equals(title)) {
            latest.setEarningsScore(parseScore(card.score()));
            latest.setEarningsLabel(card.label());
        } else if ("Analyst".equals(title)) {
            latest.setAnalystScore(parseScore(card.score()));
            latest.setAnalystLabel(card.label());
        } else if ("News".equals(title)) {
            latest.setNewsScore(parseScore(card.score()));
            latest.setNewsLabel(card.label());
        } else if ("Momentum".equals(title)) {
            latest.setMomentumScore(parseScore(card.score()));
            latest.setMomentumLabel(card.label());
        }
    }

    private Map<String, String> sourceFreshness(StockDashboard dashboard) {
        Map<String, String> values = new LinkedHashMap<>();
        StockQuoteCache quote = dashboard.quote();
        StockMetricSnapshot metric = dashboard.metric();
        values.put("quoteFetchedAt", quote == null || quote.getFetchedAt() == null ? null : quote.getFetchedAt().toString());
        values.put("metricDate", metric == null || metric.getMetricDate() == null ? null : metric.getMetricDate().toString());
        values.put("metricFetchedAt", metric == null || metric.getFetchedAt() == null ? null : metric.getFetchedAt().toString());
        values.put("latestNewsPublishedAt", dashboard.news().stream()
                .map(CompanyNews::getPublishedAt)
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .map(LocalDateTime::toString)
                .orElse(null));
        values.put("recommendationPeriodDate", dashboard.recommendations().stream()
                .map(StockRecommendationTrend::getPeriodDate)
                .filter(value -> value != null)
                .max(java.time.LocalDate::compareTo)
                .map(java.time.LocalDate::toString)
                .orElse(null));
        values.put("epsPeriodDate", dashboard.epsSurprises().stream()
                .map(StockEpsSurprise::getPeriodDate)
                .filter(value -> value != null)
                .max(java.time.LocalDate::compareTo)
                .map(java.time.LocalDate::toString)
                .orElse(null));
        values.put("latestCandleDate", dashboard.candles().stream()
                .map(StockCandleDaily::getTradeDate)
                .filter(value -> value != null)
                .max(java.time.LocalDate::compareTo)
                .map(java.time.LocalDate::toString)
                .orElse(null));
        return values;
    }

    private List<StockInfoView.SignalCard> readCards(StockSignalLatest latest) {
        List<StockInfoView.SignalCard> cards = readCardsJson(latest.getCardsJson());
        if (!cards.isEmpty()) {
            return cards;
        }
        return List.of(
                storedCard("Valuation", latest.getValuationLabel(), latest.getValuationScore()),
                storedCard("Quality", latest.getQualityLabel(), latest.getQualityScore()),
                storedCard("Earnings", latest.getEarningsLabel(), latest.getEarningsScore()),
                storedCard("Analyst", latest.getAnalystLabel(), latest.getAnalystScore()),
                storedCard("News", latest.getNewsLabel(), latest.getNewsScore()),
                storedCard("Momentum", latest.getMomentumLabel(), latest.getMomentumScore()));
    }

    private List<StockInfoView.SignalCard> readCardsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<StockInfoView.SignalCard> cards = new ArrayList<>();
            for (int index = 0; index < root.size(); index++) {
                JsonNode item = root.get(index);
                cards.add(new StockInfoView.SignalCard(text(item, "title"), text(item, "label"),
                        text(item, "tone"), text(item, "score"), text(item, "detail")));
            }
            return cards;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> readReasons(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<String> reasons = new ArrayList<>();
            for (int index = 0; index < root.size(); index++) {
                reasons.add(root.get(index).asText());
            }
            return reasons;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private StockInfoView.SignalCard storedCard(String title, String label, Integer score) {
        return new StockInfoView.SignalCard(title, fallback(label, "-"), tone(score == null ? 50 : score),
                formatPoint(score), "Stored stock_signal_latest value.");
    }

    private SignalResult valuationSignal(StockDashboard dashboard, JsonNode metricNode) {
        StockMetricSnapshot metric = dashboard.metric();
        CompanyProfile profile = dashboard.profile();
        BigDecimal pe = metric == null ? null : metric.getPeNormalizedAnnual();
        BigDecimal pb = metric == null ? null : metric.getPbAnnual();
        BigDecimal ps = metricDecimal(metricNode, "psTTM");
        BigDecimal peerPe = peerAveragePe(dashboard.symbol(), profile);
        boolean available = pe != null || pb != null || ps != null;
        if (!available) {
            return unavailable("Valuation", "밸류 데이터 부족");
        }

        int score = 55;
        if (pe != null) {
            if (peerPe != null && peerPe.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = pe.divide(peerPe, 4, RoundingMode.HALF_UP);
                if (lte(ratio, "0.75")) {
                    score += 18;
                } else if (lte(ratio, "1.00")) {
                    score += 8;
                } else if (gte(ratio, "1.50")) {
                    score -= 22;
                } else if (gte(ratio, "1.20")) {
                    score -= 12;
                }
            } else if (lte(pe, "20")) {
                score += 12;
            } else if (gte(pe, "60")) {
                score -= 22;
            } else if (gte(pe, "40")) {
                score -= 12;
            }
        }
        if (pb != null) {
            if (lte(pb, "5")) {
                score += 5;
            } else if (gte(pb, "20")) {
                score -= 15;
            } else if (gte(pb, "10")) {
                score -= 8;
            }
        }
        if (ps != null) {
            if (lte(ps, "5")) {
                score += 8;
            } else if (gte(ps, "20")) {
                score -= 12;
            } else if (gte(ps, "10")) {
                score -= 6;
            }
        }
        score = clamp(score);

        boolean qualityDefense = gte(metric == null ? null : metric.getRoeTtm(), "20")
                || gte(metricDecimal(metricNode, "netProfitMarginTTM"), "15");
        String label;
        if (score >= 70) {
            label = "저평가";
        } else if (score >= 55) {
            label = "적정";
        } else if (qualityDefense) {
            label = "고평가지만 수익성으로 방어 가능";
        } else {
            label = "고평가";
        }

        String detail = "PER " + formatRatio(pe) + ", PBR " + formatRatio(pb) + ", PSR " + formatRatio(ps)
                + (peerPe == null ? "" : ", 업종 PER 평균 " + formatRatio(peerPe));
        return new SignalResult("Valuation", label, score >= 55 ? "neutral" : "caution", score, detail, true);
    }

    private SignalResult qualitySignal(StockMetricSnapshot metric, JsonNode metricNode) {
        BigDecimal roe = metric == null ? null : metric.getRoeTtm();
        BigDecimal eps = metric == null ? null : metric.getEpsTtm();
        BigDecimal netMargin = metricDecimal(metricNode, "netProfitMarginTTM");
        boolean available = roe != null || eps != null || netMargin != null;
        if (!available) {
            return unavailable("Quality", "수익성 데이터 부족");
        }

        int score = 50;
        if (roe != null) {
            if (gte(roe, "30")) {
                score += 25;
            } else if (gte(roe, "15")) {
                score += 15;
            } else if (lte(roe, "0")) {
                score -= 25;
            } else if (lte(roe, "8")) {
                score -= 10;
            }
        }
        if (netMargin != null) {
            if (gte(netMargin, "25")) {
                score += 20;
            } else if (gte(netMargin, "10")) {
                score += 10;
            } else if (lte(netMargin, "0")) {
                score -= 20;
            }
        }
        if (eps != null) {
            score += eps.compareTo(BigDecimal.ZERO) > 0 ? 10 : -10;
        }
        score = clamp(score);

        String label = score >= 72 ? "수익성 강함" : score >= 50 ? "수익성 보통" : "마진 약함";
        String detail = "ROE " + formatPercent(roe) + ", EPS " + formatMoney(eps) + ", 순이익률 "
                + formatPercent(netMargin);
        return new SignalResult("Quality", label, tone(score), score, detail, true);
    }

    private SignalResult earningsSignal(List<StockEpsSurprise> surprises) {
        if (surprises == null || surprises.isEmpty()) {
            return unavailable("Earnings", "EPS surprise 데이터 부족");
        }
        List<StockEpsSurprise> recent = surprises.stream().limit(4).toList();
        BigDecimal average = average(recent.stream()
                .map(StockEpsSurprise::getSurprisePercent)
                .filter(value -> value != null)
                .toList());
        long positiveCount = recent.stream()
                .map(StockEpsSurprise::getSurprisePercent)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .count();
        StockEpsSurprise latest = surprises.get(0);
        StockEpsSurprise previous = surprises.size() > 1 ? surprises.get(1) : null;
        BigDecimal latestSurprise = latest.getSurprisePercent();
        int score = 50 + (int) positiveCount * 8 + cappedInt(average, -20, 20);
        score = clamp(score);

        String label;
        if (gte(latestSurprise, "5")) {
            label = "실적 기대치 상회 흐름";
        } else if (lte(latestSurprise, "-5")) {
            label = "기대치 하회 리스크";
        } else if (previous != null && latestSurprise != null && previous.getSurprisePercent() != null
                && latestSurprise.compareTo(previous.getSurprisePercent()) > 0) {
            label = "최근 실적 모멘텀 개선";
        } else {
            label = "실적 중립";
        }
        String detail = "최근 " + recent.size() + "개 분기 중 " + positiveCount + "회 상회, 평균 surprise "
                + formatPercent(average);
        return new SignalResult("Earnings", label, tone(score), score, detail, true);
    }

    private SignalResult analystSignal(List<StockRecommendationTrend> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return unavailable("Analyst", "추천 트렌드 데이터 부족");
        }
        StockRecommendationTrend latest = recommendations.get(0);
        StockRecommendationTrend previous = recommendations.size() > 1 ? recommendations.get(1) : null;
        int bullish = count(latest.getStrongBuy()) + count(latest.getBuy());
        int neutral = count(latest.getHold());
        int bearish = count(latest.getSell()) + count(latest.getStrongSell());
        int total = bullish + neutral + bearish;
        if (total == 0) {
            return unavailable("Analyst", "추천 의견 수 부족");
        }
        int score = clamp(50 + ((bullish - bearish) * 50 / total));
        int previousBullish = previous == null ? bullish : count(previous.getStrongBuy()) + count(previous.getBuy());
        int previousBearish = previous == null ? bearish : count(previous.getSell()) + count(previous.getStrongSell());

        String label;
        if (bullish >= total * 0.55) {
            label = previous != null && bullish > previousBullish ? "이전 기간 대비 추천 개선" : "매수 의견 우세";
        } else if (bearish > previousBearish || bearish >= total * 0.25) {
            label = "매도 의견 증가";
        } else {
            label = "중립 우세";
        }
        String detail = "매수 " + bullish + ", 중립 " + neutral + ", 매도 " + bearish;
        return new SignalResult("Analyst", label, tone(score), score, detail, true);
    }

    private SignalResult newsSignal(List<CompanyNews> news) {
        if (news == null || news.isEmpty()) {
            return unavailable("News", "최근 뉴스 데이터 부족");
        }
        int positive = 0;
        int negative = 0;
        for (CompanyNews item : news.stream().limit(20).toList()) {
            String text = ((item.getHeadline() == null ? "" : item.getHeadline()) + " "
                    + (item.getSummary() == null ? "" : item.getSummary())).toLowerCase(Locale.ROOT);
            if (containsAny(text, "beat", "growth", "upgrade", "raise", "strong", "record", "profit",
                    "outperform", "surge", "gain", "ai")) {
                positive++;
            }
            if (containsAny(text, "miss", "downgrade", "cut", "lawsuit", "probe", "risk", "fall", "decline",
                    "weak", "warning", "recall", "antitrust", "selloff", "loss")) {
                negative++;
            }
        }
        int score = clamp(50 + ((positive - negative) * 8) + Math.min(news.size(), 10));
        String label;
        if (negative > positive && negative >= 2) {
            label = "부정/리스크 뉴스 감지";
        } else if (positive > negative && positive >= 2) {
            label = "최근 긍정 뉴스 우세";
        } else if (news.size() >= 8) {
            label = "뉴스 관심도 증가";
        } else {
            label = "뉴스 중립";
        }
        String detail = "최근 뉴스 " + news.size() + "건, 긍정 키워드 " + positive + "건, 리스크 키워드 " + negative + "건";
        return new SignalResult("News", label, tone(score), score, detail, true);
    }

    private SignalResult momentumSignal(StockQuoteCache quote, StockMetricSnapshot metric, List<StockCandleDaily> candles) {
        if (quote == null || quote.getCurrentPrice() == null || quote.getPreviousClose() == null
                || quote.getPreviousClose().compareTo(BigDecimal.ZERO) == 0) {
            return unavailable("Momentum", "quote 데이터 부족");
        }
        BigDecimal change = quote.getCurrentPrice().subtract(quote.getPreviousClose())
                .multiply(ONE_HUNDRED)
                .divide(quote.getPreviousClose(), 4, RoundingMode.HALF_UP);
        BigDecimal high = metric == null ? null : metric.getWeek52High();
        BigDecimal low = metric == null ? null : metric.getWeek52Low();
        BigDecimal position = week52Position(quote.getCurrentPrice(), high, low);
        int score = clamp(50 + cappedInt(change.multiply(BigDecimal.valueOf(4)), -24, 24)
                + (position == null ? 0 : position.compareTo(BigDecimal.valueOf(80)) >= 0 ? 8
                        : position.compareTo(BigDecimal.valueOf(20)) <= 0 ? -8 : 0));
        String label;
        if (change.compareTo(BigDecimal.valueOf(2)) >= 0) {
            label = "단기 강세";
        } else if (change.compareTo(BigDecimal.valueOf(-2)) <= 0) {
            label = "단기 약세";
        } else if (position != null && position.compareTo(BigDecimal.valueOf(80)) >= 0) {
            label = "52주 고점 근접";
        } else if (position != null && position.compareTo(BigDecimal.valueOf(20)) <= 0) {
            label = "52주 저점 근접";
        } else {
            label = "단기 중립";
        }
        String detail = "전일 대비 " + formatPercent(change) + ", 52주 위치 " + formatPercent(position)
                + (candles == null || candles.isEmpty() ? ", 일봉 데이터는 아직 없음" : ", 일봉 " + candles.size() + "건 반영");
        return new SignalResult("Momentum", label, tone(score), score, detail, true);
    }

    private BigDecimal peerAveragePe(String symbol, CompanyProfile profile) {
        if (profile == null || profile.getFinnhubIndustry() == null) {
            return null;
        }
        List<BigDecimal> values = cacheService.findPeersByIndustry(profile.getFinnhubIndustry(), 20).stream()
                .filter(peer -> peer.getSymbol() == null || !peer.getSymbol().equalsIgnoreCase(symbol))
                .map(StockPeerComparison::getPeNormalizedAnnual)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        return average(values);
    }

    private JsonNode readMetricNode(StockMetricSnapshot metric) {
        if (metric == null || metric.getRawJson() == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(metric.getRawJson());
            return root == null ? null : root.get("metric");
        } catch (Exception ex) {
            return null;
        }
    }

    private static SignalResult unavailable(String title, String detail) {
        return new SignalResult(title, "데이터 부족", "neutral", 50, detail, false);
    }

    private static int integratedScore(List<SignalResult> results) {
        int total = 0;
        int weight = 0;
        for (SignalResult result : results) {
            int currentWeight = result.available() ? 2 : 1;
            total += result.score() * currentWeight;
            weight += currentWeight;
        }
        return weight == 0 ? 50 : Math.round((float) total / weight);
    }

    private static String integratedLabel(int score) {
        if (score >= 70) {
            return "상향 우세";
        }
        if (score >= 55) {
            return "중립 우세";
        }
        if (score >= 40) {
            return "주의";
        }
        return "하향 리스크";
    }

    private static String confidence(List<SignalResult> results) {
        long available = results.stream().filter(SignalResult::available).count();
        if (available >= 5) {
            return "신뢰도 보통";
        }
        if (available >= 3) {
            return "신뢰도 낮음";
        }
        return "신뢰도 매우 낮음";
    }

    private static String tone(int score) {
        if (score >= 67) {
            return "positive";
        }
        if (score >= 52) {
            return "neutral";
        }
        if (score >= 40) {
            return "caution";
        }
        return "negative";
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

    private static BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal week52Position(BigDecimal price, BigDecimal high, BigDecimal low) {
        if (price == null || high == null || low == null || high.compareTo(low) <= 0) {
            return null;
        }
        return price.subtract(low).multiply(ONE_HUNDRED).divide(high.subtract(low), 2, RoundingMode.HALF_UP);
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean gte(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) >= 0;
    }

    private static boolean lte(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) <= 0;
    }

    private static int cappedInt(BigDecimal value, int min, int max) {
        if (value == null) {
            return 0;
        }
        return Math.max(min, Math.min(max, value.setScale(0, RoundingMode.HALF_UP).intValue()));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String formatRatio(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "배";
    }

    private static String formatPercent(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static String formatMoney(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " USD";
    }

    private static String formatScore(Integer score) {
        return score == null ? "-" : score + " / 100";
    }

    private static String formatPoint(Integer score) {
        return score == null ? "-" : score + "점";
    }

    private static Integer parseScore(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        boolean started = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isDigit(current) || (!started && current == '-')) {
                digits.append(current);
                started = true;
            } else if (started) {
                break;
            }
        }
        if (digits.isEmpty() || "-".contentEquals(digits)) {
            return null;
        }
        try {
            return Integer.valueOf(digits.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private record SignalResult(String title, String label, String tone, int score, String detail, boolean available) {
    }
}
