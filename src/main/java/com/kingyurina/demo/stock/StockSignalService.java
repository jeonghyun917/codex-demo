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
    private static final String SIGNAL_VERSION = "quant-v2";

    private final StockCacheService cacheService;
    private final StockDataQualityService dataQualityService;
    private final ObjectMapper objectMapper;

    public StockSignalService(StockCacheService cacheService, StockDataQualityService dataQualityService,
            ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.dataQualityService = dataQualityService;
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
        List<StockCandleDaily> candles = cacheService.findRecentCandles(normalized, 80);

        return new StockDashboard(normalized, false, cacheService.databaseEnabled(),
                quote != null, profile != null, metric != null, !news.isEmpty(), !recommendations.isEmpty(),
                !epsSurprises.isEmpty(), !candles.isEmpty(), "Cached Finnhub data loaded.", profile, quote, metric,
                news, recommendations, epsSurprises, candles, null);
    }

    public StockInfoView.StockSignalView build(StockDashboard dashboard) {
        StockMetricSnapshot metric = dashboard.metric();
        JsonNode metricNode = readMetricNode(metric);
        SecFinancialContext secFinancial = secFinancialContext(dashboard.symbol());
        StockDataQualityLatest dataQuality = dataQualityService.assessAndSave(dashboard);
        SignalResult valuation = valuationSignal(dashboard, metricNode, secFinancial, dataQuality);
        SignalResult quality = qualitySignal(metric, metricNode, secFinancial, dataQuality);
        SignalResult growth = growthSignal(secFinancial);
        SignalResult stability = stabilitySignal(metricNode, secFinancial, dataQuality);
        SignalResult earnings = earningsSignal(dashboard.epsSurprises(), secFinancial);
        SignalResult analyst = analystSignal(dashboard.recommendations());
        SignalResult news = newsSignal(dashboard.news());
        SignalResult momentum = momentumSignal(dashboard.quote(), metric, dashboard.candles(), dataQuality);
        SignalResult risk = riskSignal(dashboard.candles(), metric, dashboard.quote(), dataQuality);
        SignalResult institution = institutionSignal(dashboard.symbol());

        List<SignalResult> results = List.of(valuation, quality, growth, stability, earnings, analyst, news,
                momentum, risk, institution);
        int score = applyDataQualityGuardrails(integratedScore(results), dataQuality);
        String label = integratedLabel(score);
        String tone = tone(score);
        String confidence = confidence(results, dataQuality);
        List<StockInfoView.SignalCard> cards = results.stream()
                .map(result -> new StockInfoView.SignalCard(result.title(), result.label(), result.tone(),
                        result.score() + "점", result.detail()))
                .toList();
        List<String> reasons = new ArrayList<>(results.stream()
                .filter(SignalResult::available)
                .sorted((left, right) -> Integer.compare(Math.abs(right.score() - 50), Math.abs(left.score() - 50)))
                .limit(3)
                .map(result -> result.title() + ": " + result.detail())
                .toList());
        dataQualityService.issueMessages(dataQuality, 1).stream()
                .map(message -> "Data Quality: " + message)
                .forEach(reasons::add);

        return new StockInfoView.StockSignalView(label, tone, score + " / 100", confidence,
                label + "입니다. 10개 핵심 Signal과 동일 수집 대상 내 factor ranking을 함께 반영한 Quant Signal v2 점수입니다. 확정 예측은 아닙니다.",
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
        StockDataQualityLatest dataQuality = dataQualityService.findLatest(dashboard.symbol());
        if (dataQuality != null) {
            latest.setDataQualityScore(dataQuality.getQualityScore());
            latest.setDataQualityExcludedMetricCount(dataQuality.getExcludedMetricCount());
            latest.setDataQualityIssueCount(dataQuality.getIssueCount());
        }

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
        } else if ("Growth".equals(title)) {
            latest.setGrowthScore(parseScore(card.score()));
            latest.setGrowthLabel(card.label());
        } else if ("Stability".equals(title)) {
            latest.setStabilityScore(parseScore(card.score()));
            latest.setStabilityLabel(card.label());
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
        } else if ("Risk".equals(title)) {
            latest.setRiskScore(parseScore(card.score()));
            latest.setRiskLabel(card.label());
        } else if ("Institution".equals(title)) {
            latest.setInstitutionScore(parseScore(card.score()));
            latest.setInstitutionLabel(card.label());
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
        StockInstitutionFlow institutionFlow = cacheService.findLatestInstitutionFlow(dashboard.symbol());
        values.put("institution13fQuarter", institutionFlow == null || institutionFlow.getReportQuarter() == null
                ? null
                : institutionFlow.getReportQuarter().toString());
        SecFinancialStandard secAnnual = cacheService.findLatestSecAnnualFinancial(dashboard.symbol());
        values.put("secAnnualFiledAt", secAnnual == null || secAnnual.getFiledAt() == null
                ? null
                : secAnnual.getFiledAt().toString());
        values.put("secAnnualEndDate", secAnnual == null || secAnnual.getEndDate() == null
                ? null
                : secAnnual.getEndDate().toString());
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
                storedCard("Growth", latest.getGrowthLabel(), latest.getGrowthScore()),
                storedCard("Stability", latest.getStabilityLabel(), latest.getStabilityScore()),
                storedCard("Earnings", latest.getEarningsLabel(), latest.getEarningsScore()),
                storedCard("Analyst", latest.getAnalystLabel(), latest.getAnalystScore()),
                storedCard("News", latest.getNewsLabel(), latest.getNewsScore()),
                storedCard("Momentum", latest.getMomentumLabel(), latest.getMomentumScore()),
                storedCard("Risk", latest.getRiskLabel(), latest.getRiskScore()),
                storedCard("Institution", latest.getInstitutionLabel(), latest.getInstitutionScore()));
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

    private SignalResult valuationSignal(StockDashboard dashboard, JsonNode metricNode,
            SecFinancialContext secFinancial, StockDataQualityLatest dataQuality) {
        StockMetricSnapshot metric = dashboard.metric();
        CompanyProfile profile = dashboard.profile();
        BigDecimal pe = dataQualityService.clean(dataQuality, "peNormalizedAnnual",
                metric == null ? null : metric.getPeNormalizedAnnual());
        BigDecimal pb = dataQualityService.clean(dataQuality, "pbAnnual",
                metric == null ? null : metric.getPbAnnual());
        BigDecimal ps = dataQualityService.clean(dataQuality, "psTTM",
                firstNonNull(metricDecimal(metricNode, "psTTM"), secFinancial.ps(profile)));
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

        boolean qualityDefense = gte(dataQualityService.clean(dataQuality, "roeTTM",
                firstNonNull(metric == null ? null : metric.getRoeTtm(), secFinancial.roe())), "20")
                || gte(dataQualityService.clean(dataQuality, "netProfitMarginTTM",
                        firstNonNull(metricDecimal(metricNode, "netProfitMarginTTM"), secFinancial.netMargin())), "15");
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

    private SignalResult qualitySignal(StockMetricSnapshot metric, JsonNode metricNode,
            SecFinancialContext secFinancial, StockDataQualityLatest dataQuality) {
        BigDecimal roe = dataQualityService.clean(dataQuality, "roeTTM",
                firstNonNull(metric == null ? null : metric.getRoeTtm(), secFinancial.roe()));
        BigDecimal eps = dataQualityService.clean(dataQuality, "epsTTM",
                firstNonNull(metric == null ? null : metric.getEpsTtm(), secFinancial.epsDiluted()));
        BigDecimal netMargin = dataQualityService.clean(dataQuality, "netProfitMarginTTM",
                firstNonNull(metricDecimal(metricNode, "netProfitMarginTTM"), secFinancial.netMargin()));
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

    private SignalResult growthSignal(SecFinancialContext secFinancial) {
        BigDecimal annualRevenueGrowth = secFinancial.annualRevenueGrowth();
        BigDecimal annualNetIncomeGrowth = secFinancial.annualNetIncomeGrowth();
        BigDecimal quarterRevenueGrowth = secFinancial.quarterRevenueGrowth();
        BigDecimal quarterNetIncomeGrowth = secFinancial.quarterNetIncomeGrowth();
        boolean available = annualRevenueGrowth != null || annualNetIncomeGrowth != null
                || quarterRevenueGrowth != null || quarterNetIncomeGrowth != null;
        if (!available) {
            return unavailable("Growth", "SEC 성장률 데이터 부족");
        }

        int score = 50;
        score += cappedInt(divideSignalValue(annualRevenueGrowth, 2), -15, 15);
        score += cappedInt(divideSignalValue(annualNetIncomeGrowth, 3), -18, 18);
        score += cappedInt(divideSignalValue(quarterRevenueGrowth, 4), -8, 8);
        score += cappedInt(divideSignalValue(quarterNetIncomeGrowth, 5), -10, 10);
        if (gte(annualRevenueGrowth, "10") && gte(annualNetIncomeGrowth, "10")) {
            score += 5;
        }
        if (lte(annualRevenueGrowth, "-5") && lte(annualNetIncomeGrowth, "-5")) {
            score -= 5;
        }
        score = clamp(score);

        String label = score >= 70 ? "성장 강함" : score >= 55 ? "성장 개선" : score >= 40 ? "성장 보통" : "성장 둔화";
        String detail = "연 매출 성장 " + formatPercent(annualRevenueGrowth)
                + ", 연 순이익 성장 " + formatPercent(annualNetIncomeGrowth)
                + ", 분기 매출 성장 " + formatPercent(quarterRevenueGrowth)
                + ", 분기 순이익 성장 " + formatPercent(quarterNetIncomeGrowth);
        return new SignalResult("Growth", label, tone(score), score, detail, true);
    }

    private SignalResult stabilitySignal(JsonNode metricNode, SecFinancialContext secFinancial,
            StockDataQualityLatest dataQuality) {
        BigDecimal currentRatio = dataQualityService.clean(dataQuality, "currentRatioQuarterly",
                metricDecimal(metricNode, "currentRatioQuarterly"));
        BigDecimal debtToEquity = dataQualityService.clean(dataQuality, "totalDebt/totalEquityQuarterly",
                metricDecimal(metricNode, "totalDebt/totalEquityQuarterly"));
        BigDecimal liabilitiesToAssets = secFinancial.liabilitiesToAssets();
        BigDecimal operatingCashFlowMargin = secFinancial.operatingCashFlowMargin();
        boolean available = currentRatio != null || debtToEquity != null || liabilitiesToAssets != null
                || operatingCashFlowMargin != null;
        if (!available) {
            return unavailable("Stability", "재무 안정성 데이터 부족");
        }

        int score = 50;
        if (currentRatio != null) {
            if (gte(currentRatio, "1.5")) {
                score += 14;
            } else if (lte(currentRatio, "1")) {
                score -= 14;
            }
        }
        if (debtToEquity != null) {
            if (lte(debtToEquity, "0.5")) {
                score += 16;
            } else if (lte(debtToEquity, "1")) {
                score += 8;
            } else if (gte(debtToEquity, "2")) {
                score -= 22;
            } else if (gte(debtToEquity, "1.5")) {
                score -= 12;
            }
        }
        if (liabilitiesToAssets != null) {
            if (lte(liabilitiesToAssets, "50")) {
                score += 10;
            } else if (gte(liabilitiesToAssets, "80")) {
                score -= 18;
            } else if (gte(liabilitiesToAssets, "65")) {
                score -= 9;
            }
        }
        if (operatingCashFlowMargin != null) {
            if (gte(operatingCashFlowMargin, "15")) {
                score += 10;
            } else if (lte(operatingCashFlowMargin, "0")) {
                score -= 16;
            }
        }
        score = clamp(score);

        String label = score >= 70 ? "재무 안정성 강함" : score >= 55 ? "안정성 양호" : score >= 40 ? "안정성 보통" : "재무 안정성 약함";
        String detail = "유동비율 " + formatRatio(currentRatio)
                + ", 부채/자본 " + formatRatio(debtToEquity)
                + ", 부채/자산 " + formatPercent(liabilitiesToAssets)
                + ", 영업현금흐름률 " + formatPercent(operatingCashFlowMargin);
        return new SignalResult("Stability", label, tone(score), score, detail, true);
    }

    private SignalResult earningsSignal(List<StockEpsSurprise> surprises, SecFinancialContext secFinancial) {
        if (surprises == null || surprises.isEmpty()) {
            return secEarningsSignal(secFinancial);
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
        score = clamp(score + secFinancial.quarterGrowthScoreAdjustment());

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
                + formatPercent(average) + secFinancial.quarterSourceSuffix();
        return new SignalResult("Earnings", label, tone(score), score, detail, true);
    }

    private SignalResult secEarningsSignal(SecFinancialContext secFinancial) {
        BigDecimal revenueGrowth = secFinancial.quarterRevenueGrowth();
        BigDecimal netIncomeGrowth = secFinancial.quarterNetIncomeGrowth();
        if (revenueGrowth == null && netIncomeGrowth == null) {
            return unavailable("Earnings", "EPS surprise and SEC quarterly financial data are missing.");
        }

        int score = 50;
        score += cappedInt(revenueGrowth == null ? null : revenueGrowth.divide(BigDecimal.valueOf(4), 2,
                RoundingMode.HALF_UP), -12, 12);
        score += cappedInt(netIncomeGrowth == null ? null : netIncomeGrowth.divide(BigDecimal.valueOf(3), 2,
                RoundingMode.HALF_UP), -18, 18);
        score = clamp(score);

        String label;
        if (score >= 65) {
            label = "SEC 실적 모멘텀 개선";
        } else if (score <= 40) {
            label = "SEC 실적 둔화 리스크";
        } else {
            label = "SEC 실적 중립";
        }
        String detail = "SEC quarter revenue growth " + formatPercent(revenueGrowth)
                + ", net income growth " + formatPercent(netIncomeGrowth)
                + secFinancial.quarterSourceSuffix();
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

    private SignalResult momentumSignal(StockQuoteCache quote, StockMetricSnapshot metric,
            List<StockCandleDaily> candles, StockDataQualityLatest dataQuality) {
        BigDecimal currentPrice = dataQualityService.clean(dataQuality, "currentPrice",
                quote == null ? null : quote.getCurrentPrice());
        BigDecimal previousClose = dataQualityService.clean(dataQuality, "previousClose",
                quote == null ? null : quote.getPreviousClose());
        if (quote == null || currentPrice == null || previousClose == null
                || previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return unavailable("Momentum", "quote 데이터 부족");
        }
        BigDecimal change = currentPrice.subtract(previousClose)
                .multiply(ONE_HUNDRED)
                .divide(previousClose, 4, RoundingMode.HALF_UP);
        BigDecimal high = dataQualityService.clean(dataQuality, "52WeekHigh",
                metric == null ? null : metric.getWeek52High());
        BigDecimal low = dataQualityService.clean(dataQuality, "52WeekLow",
                metric == null ? null : metric.getWeek52Low());
        BigDecimal position = week52Position(currentPrice, high, low);
        CandleMomentum candleMomentum = candleMomentum(candles);
        int score = clamp(50 + cappedInt(change.multiply(BigDecimal.valueOf(4)), -24, 24)
                + (position == null ? 0 : position.compareTo(BigDecimal.valueOf(80)) >= 0 ? 8
                        : position.compareTo(BigDecimal.valueOf(20)) <= 0 ? -8 : 0)
                + candleMomentum.adjustment());
        String label;
        if (candleMomentum.adjustment() >= 10) {
            label = "일봉 추세 강세";
        } else if (candleMomentum.adjustment() <= -10) {
            label = "일봉 추세 약세";
        } else if (change.compareTo(BigDecimal.valueOf(2)) >= 0) {
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
                + candleMomentum.detail();
        return new SignalResult("Momentum", label, tone(score), score, detail, true);
    }

    private SignalResult riskSignal(List<StockCandleDaily> candles, StockMetricSnapshot metric, StockQuoteCache quote,
            StockDataQualityLatest dataQuality) {
        List<StockCandleDaily> sorted = sortedCandlesDesc(candles);
        if (sorted.size() < 20) {
            return unavailable("Risk", "일봉 리스크 데이터 부족");
        }

        int window = Math.min(sorted.size(), 80);
        List<StockCandleDaily> recent = sorted.subList(0, window);
        BigDecimal cleanQuote = dataQualityService.clean(dataQuality, "currentPrice",
                quote == null ? null : quote.getCurrentPrice());
        BigDecimal latestClose = cleanQuote != null
                ? cleanQuote
                : recent.getFirst().getClosePrice();
        BigDecimal highInWindow = recent.stream()
                .map(StockCandleDaily::getHighPrice)
                .filter(value -> value != null)
                .max(BigDecimal::compareTo)
                .orElse(null);
        BigDecimal drawdown = percentDiff(latestClose, highInWindow);
        BigDecimal absoluteDrawdown = drawdown == null ? null : drawdown.abs();
        BigDecimal annualizedVolatility = annualizedVolatility(recent);
        BigDecimal week52Position = metric == null ? null
                : week52Position(latestClose,
                        dataQualityService.clean(dataQuality, "52WeekHigh", metric.getWeek52High()),
                        dataQualityService.clean(dataQuality, "52WeekLow", metric.getWeek52Low()));

        int score = 62;
        if (annualizedVolatility != null) {
            if (gte(annualizedVolatility, "60")) {
                score -= 22;
            } else if (gte(annualizedVolatility, "40")) {
                score -= 12;
            } else if (lte(annualizedVolatility, "25")) {
                score += 10;
            }
        }
        if (absoluteDrawdown != null) {
            if (gte(absoluteDrawdown, "35")) {
                score -= 22;
            } else if (gte(absoluteDrawdown, "20")) {
                score -= 12;
            } else if (lte(absoluteDrawdown, "10")) {
                score += 8;
            }
        }
        if (week52Position != null && lte(week52Position, "20")) {
            score -= 8;
        }
        score = clamp(score);

        String label = score >= 70 ? "리스크 낮음" : score >= 55 ? "리스크 관리 가능" : score >= 40 ? "변동성 주의" : "리스크 높음";
        String detail = window + "일 변동성 " + formatPercent(annualizedVolatility)
                + ", 최근 고점 대비 " + formatPercent(drawdown)
                + ", 52주 위치 " + formatPercent(week52Position);
        return new SignalResult("Risk", label, tone(score), score, detail, true);
    }

    private CandleMomentum candleMomentum(List<StockCandleDaily> candles) {
        if (candles == null || candles.size() < 20) {
            return new CandleMomentum(0, ", 일봉 데이터는 아직 부족");
        }
        List<StockCandleDaily> sorted = sortedCandlesDesc(candles);
        if (sorted.size() < 20) {
            return new CandleMomentum(0, ", 일봉 데이터는 아직 부족");
        }

        BigDecimal latestClose = sorted.getFirst().getClosePrice();
        BigDecimal sma20 = averageClose(sorted.subList(0, 20));
        BigDecimal sma20Gap = percentDiff(latestClose, sma20);
        int adjustment = 0;
        if (gte(sma20Gap, "2")) {
            adjustment += 8;
        } else if (lte(sma20Gap, "-2")) {
            adjustment -= 8;
        }

        BigDecimal trend20 = null;
        if (sorted.size() >= 40) {
            BigDecimal previousSma20 = averageClose(sorted.subList(20, 40));
            trend20 = percentDiff(sma20, previousSma20);
            if (gte(trend20, "1")) {
                adjustment += 6;
            } else if (lte(trend20, "-1")) {
                adjustment -= 6;
            }
        }

        BigDecimal rsi14 = rsi14(sorted);
        if (rsi14 != null) {
            if (gte(rsi14, "75")) {
                adjustment -= 6;
            } else if (lte(rsi14, "30")) {
                adjustment -= 8;
            } else if (gte(rsi14, "45") && lte(rsi14, "65")) {
                adjustment += 4;
            }
        }

        String detail = ", 일봉 " + sorted.size() + "건, SMA20 대비 " + formatPercent(sma20Gap)
                + ", 20일 추세 " + formatPercent(trend20)
                + ", RSI14 " + (rsi14 == null ? "-" : rsi14.setScale(1, RoundingMode.HALF_UP));
        return new CandleMomentum(adjustment, detail);
    }

    private static BigDecimal averageClose(List<StockCandleDaily> candles) {
        return average(candles.stream()
                .map(StockCandleDaily::getClosePrice)
                .filter(value -> value != null)
                .toList());
    }

    private static BigDecimal percentDiff(BigDecimal current, BigDecimal basis) {
        if (current == null || basis == null || basis.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(basis).multiply(ONE_HUNDRED).divide(basis, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal rsi14(List<StockCandleDaily> sortedDesc) {
        if (sortedDesc.size() < 15) {
            return null;
        }
        List<StockCandleDaily> recentAsc = new ArrayList<>(sortedDesc.subList(0, 15));
        recentAsc.sort((left, right) -> left.getTradeDate().compareTo(right.getTradeDate()));
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int index = 1; index < recentAsc.size(); index++) {
            BigDecimal previous = recentAsc.get(index - 1).getClosePrice();
            BigDecimal current = recentAsc.get(index).getClosePrice();
            if (previous == null || current == null) {
                continue;
            }
            BigDecimal change = current.subtract(previous);
            if (change.signum() >= 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }
        if (gains.compareTo(BigDecimal.ZERO) == 0 && losses.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(50);
        }
        if (losses.compareTo(BigDecimal.ZERO) == 0) {
            return ONE_HUNDRED;
        }
        BigDecimal averageGain = gains.divide(BigDecimal.valueOf(14), 8, RoundingMode.HALF_UP);
        BigDecimal averageLoss = losses.divide(BigDecimal.valueOf(14), 8, RoundingMode.HALF_UP);
        BigDecimal relativeStrength = averageGain.divide(averageLoss, 8, RoundingMode.HALF_UP);
        return ONE_HUNDRED.subtract(ONE_HUNDRED.divide(BigDecimal.ONE.add(relativeStrength), 4, RoundingMode.HALF_UP));
    }

    private static List<StockCandleDaily> sortedCandlesDesc(List<StockCandleDaily> candles) {
        if (candles == null) {
            return List.of();
        }
        return candles.stream()
                .filter(candle -> candle.getTradeDate() != null && candle.getClosePrice() != null)
                .sorted((left, right) -> right.getTradeDate().compareTo(left.getTradeDate()))
                .toList();
    }

    private static BigDecimal annualizedVolatility(List<StockCandleDaily> sortedDesc) {
        if (sortedDesc == null || sortedDesc.size() < 20) {
            return null;
        }
        List<StockCandleDaily> recentAsc = new ArrayList<>(sortedDesc);
        recentAsc.sort((left, right) -> left.getTradeDate().compareTo(right.getTradeDate()));
        List<Double> returns = new ArrayList<>();
        for (int index = 1; index < recentAsc.size(); index++) {
            BigDecimal previous = recentAsc.get(index - 1).getClosePrice();
            BigDecimal current = recentAsc.get(index).getClosePrice();
            if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            returns.add(current.subtract(previous)
                    .divide(previous, 8, RoundingMode.HALF_UP)
                    .doubleValue());
        }
        if (returns.size() < 10) {
            return null;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .sum() / returns.size();
        double annualizedPercent = Math.sqrt(variance) * Math.sqrt(252) * 100;
        return BigDecimal.valueOf(annualizedPercent).setScale(2, RoundingMode.HALF_UP);
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

    private SecFinancialContext secFinancialContext(String symbol) {
        SecFinancialStandard annual = cacheService.findLatestSecAnnualFinancial(symbol);
        SecFinancialStandard previousAnnual = annual == null
                ? null
                : cacheService.findPreviousSecAnnualFinancial(symbol, annual.getEndDate());
        SecFinancialStandard quarter = cacheService.findLatestSecQuarterFinancial(symbol);
        SecFinancialStandard previousQuarter = quarter == null
                ? null
                : cacheService.findPreviousSecQuarterFinancial(symbol, quarter.getEndDate());
        return new SecFinancialContext(annual, previousAnnual, quarter, previousQuarter);
    }

    private SignalResult institutionSignal(String symbol) {
        StockInstitutionFlow flow = cacheService.findLatestInstitutionFlow(symbol);
        if (flow == null) {
            return unavailable("Institution", "13F institution flow data is missing.");
        }
        BigDecimal sharesChange = flow.getSharesChangePct();
        BigDecimal valueChange = flow.getValueChangePct();
        boolean available = sharesChange != null || valueChange != null || flow.getHolderCount() != null;
        if (!available) {
            return unavailable("Institution", "13F institution flow is not mapped for this symbol.");
        }

        int score = 50;
        if (sharesChange != null) {
            score += cappedInt(sharesChange.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP), -18, 18);
        }
        if (valueChange != null) {
            score += cappedInt(valueChange.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP), -10, 10);
        }
        if (flow.getHolderCount() != null) {
            if (flow.getHolderCount() >= 10) {
                score += 5;
            } else if (flow.getHolderCount() <= 2) {
                score -= 4;
            }
        }
        score = clamp(score);

        String label = score >= 65 ? "기관 보유 증가" : score >= 48 ? "기관 보유 중립" : "기관 보유 감소";
        String detail = "13F " + (flow.getReportQuarter() == null ? "-" : flow.getReportQuarter())
                + ", shares QoQ " + formatPercent(sharesChange)
                + ", value QoQ " + formatPercent(valueChange)
                + ", holders " + (flow.getHolderCount() == null ? "-" : flow.getHolderCount());
        return new SignalResult("Institution", label, tone(score), score, detail, true);
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
            int currentWeight = result.available() ? signalWeight(result.title()) : missingSignalWeight(result.title());
            total += result.score() * currentWeight;
            weight += currentWeight;
        }
        int score = weight == 0 ? 50 : Math.round((float) total / weight);

        Integer valuation = scoreOf(results, "Valuation");
        Integer quality = scoreOf(results, "Quality");
        Integer growth = scoreOf(results, "Growth");
        Integer stability = scoreOf(results, "Stability");
        Integer earnings = scoreOf(results, "Earnings");
        Integer analyst = scoreOf(results, "Analyst");
        Integer news = scoreOf(results, "News");
        Integer momentum = scoreOf(results, "Momentum");
        Integer risk = scoreOf(results, "Risk");
        Integer institution = scoreOf(results, "Institution");

        if (gte(quality, 75) && gte(earnings, 65)) {
            score += 4;
        }
        if (gte(growth, 65) && gte(quality, 65)) {
            score += 3;
        }
        if (gte(valuation, 65) && gte(quality, 65)) {
            score += 3;
        }
        if (gte(stability, 70) && gte(risk, 60)) {
            score += 2;
        }
        if (gte(analyst, 65) && gte(news, 60)) {
            score += 2;
        }
        if (gte(momentum, 65) && gte(earnings, 60)) {
            score += 2;
        }
        if (gte(institution, 65) && gte(momentum, 55)) {
            score += 1;
        }
        if (lte(quality, 35)) {
            score -= 4;
        }
        if (lte(valuation, 35) && !gte(quality, 70)) {
            score -= 5;
        }
        if (lte(growth, 35)) {
            score -= 4;
        }
        if (lte(stability, 35)) {
            score -= 5;
        }
        if (lte(earnings, 35)) {
            score -= 4;
        }
        if (lte(risk, 35)) {
            score -= 4;
        }
        if (lte(analyst, 35) && lte(news, 45)) {
            score -= 3;
        }
        if (lte(institution, 30) && lte(risk, 45)) {
            score -= 2;
        }

        long available = results.stream().filter(SignalResult::available).count();
        if (available < 7) {
            score = pullTowardNeutral(score, 4);
        }
        if (signalSpread(results) >= 55) {
            score = pullTowardNeutral(score, 3);
        }

        if (lte(quality, 35)) {
            score = Math.min(score, 58);
        }
        if (lte(earnings, 35) && lte(momentum, 45)) {
            score = Math.min(score, 60);
        }
        if (lte(valuation, 30) && !gte(quality, 70)) {
            score = Math.min(score, 62);
        }
        if (lte(stability, 30)) {
            score = Math.min(score, 60);
        }
        if (lte(risk, 30)) {
            score = Math.min(score, 64);
        }
        if (lte(analyst, 30)) {
            score = Math.min(score, 68);
        }
        if (lte(institution, 25)) {
            score = Math.min(score, 72);
        }
        return clamp(score);
    }

    private static int applyDataQualityGuardrails(int score, StockDataQualityLatest dataQuality) {
        if (dataQuality == null) {
            return score;
        }
        Integer qualityScore = dataQuality.getQualityScore();
        Integer excludedCount = dataQuality.getExcludedMetricCount();
        Integer issueCount = dataQuality.getIssueCount();
        if (qualityScore != null && qualityScore < 35) {
            score = Math.min(score, 55);
        } else if (qualityScore != null && qualityScore < 50) {
            score = Math.min(score, 60);
        }
        if (excludedCount != null && excludedCount >= 4) {
            score = pullTowardNeutral(score, 8);
            score = Math.min(score, 62);
        } else if (excludedCount != null && excludedCount >= 2) {
            score = pullTowardNeutral(score, 4);
        }
        if (issueCount != null && issueCount >= 8) {
            score = pullTowardNeutral(score, 5);
        }
        return clamp(score);
    }

    private static int signalWeight(String title) {
        return switch (title) {
            case "Quality" -> 17;
            case "Valuation", "Growth" -> 14;
            case "Earnings" -> 13;
            case "Stability" -> 9;
            case "Momentum", "Risk" -> 8;
            case "Analyst" -> 7;
            case "Institution" -> 6;
            case "News" -> 4;
            default -> 10;
        };
    }

    private static int missingSignalWeight(String title) {
        return Math.max(2, signalWeight(title) / 5);
    }

    private static Integer scoreOf(List<SignalResult> results, String title) {
        return results.stream()
                .filter(result -> title.equals(result.title()) && result.available())
                .map(SignalResult::score)
                .findFirst()
                .orElse(null);
    }

    private static int signalSpread(List<SignalResult> results) {
        List<Integer> scores = results.stream()
                .filter(SignalResult::available)
                .map(SignalResult::score)
                .toList();
        if (scores.size() < 2) {
            return 0;
        }
        int min = scores.stream().mapToInt(Integer::intValue).min().orElse(50);
        int max = scores.stream().mapToInt(Integer::intValue).max().orElse(50);
        return max - min;
    }

    private static int pullTowardNeutral(int score, int amount) {
        if (score > 50) {
            return Math.max(50, score - amount);
        }
        if (score < 50) {
            return Math.min(50, score + amount);
        }
        return score;
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

    private static String confidence(List<SignalResult> results, StockDataQualityLatest dataQuality) {
        if (dataQuality != null && dataQuality.getQualityScore() != null) {
            if (dataQuality.getQualityScore() < 50) {
                return "데이터 품질 낮음";
            }
            if (dataQuality.getQualityScore() < 70) {
                return "데이터 품질 주의";
            }
        }
        long available = results.stream().filter(SignalResult::available).count();
        if (available >= 8) {
            return "데이터 신뢰도 높음";
        }
        if (available >= 6) {
            return "데이터 신뢰도 보통";
        }
        if (available >= 4) {
            return "데이터 신뢰도 낮음";
        }
        return "데이터 신뢰도 매우 낮음";
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

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private static BigDecimal divideSignalValue(BigDecimal value, int divisor) {
        return value == null ? null : value.divide(BigDecimal.valueOf(divisor), 4, RoundingMode.HALF_UP);
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

    private static boolean gte(Integer value, int threshold) {
        return value != null && value >= threshold;
    }

    private static boolean lte(Integer value, int threshold) {
        return value != null && value <= threshold;
    }

    private static int cappedInt(BigDecimal value, int min, int max) {
        if (value == null) {
            return 0;
        }
        return Math.max(min, Math.min(max, value.setScale(0, RoundingMode.HALF_UP).intValue()));
    }

    private static BigDecimal divide(BigDecimal value, int divisor) {
        if (value == null) {
            return null;
        }
        return value.divide(BigDecimal.valueOf(divisor), 4, RoundingMode.HALF_UP);
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

    private record SecFinancialContext(SecFinancialStandard annual,
            SecFinancialStandard previousAnnual,
            SecFinancialStandard quarter,
            SecFinancialStandard previousQuarter) {

        BigDecimal ps(CompanyProfile profile) {
            if (profile == null || profile.getMarketCap() == null || annual == null
                    || annual.getRevenue() == null || annual.getRevenue().compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            if (annual.getCurrency() != null && !"USD".equalsIgnoreCase(annual.getCurrency())) {
                return null;
            }
            BigDecimal marketCapUsd = profile.getMarketCap().multiply(BigDecimal.valueOf(1_000_000L));
            return marketCapUsd.divide(annual.getRevenue(), 4, RoundingMode.HALF_UP);
        }

        BigDecimal roe() {
            if (annual == null || annual.getNetIncome() == null || annual.getEquity() == null
                    || annual.getEquity().compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return annual.getNetIncome().multiply(ONE_HUNDRED)
                    .divide(annual.getEquity(), 4, RoundingMode.HALF_UP);
        }

        BigDecimal netMargin() {
            if (annual == null || annual.getNetIncome() == null || annual.getRevenue() == null
                    || annual.getRevenue().compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return annual.getNetIncome().multiply(ONE_HUNDRED)
                    .divide(annual.getRevenue(), 4, RoundingMode.HALF_UP);
        }

        BigDecimal epsDiluted() {
            return annual == null ? null : annual.getEpsDiluted();
        }

        BigDecimal annualRevenueGrowth() {
            return growth(value(annual, Field.REVENUE), value(previousAnnual, Field.REVENUE));
        }

        BigDecimal annualNetIncomeGrowth() {
            return growth(value(annual, Field.NET_INCOME), value(previousAnnual, Field.NET_INCOME));
        }

        BigDecimal quarterRevenueGrowth() {
            return growth(value(quarter, Field.REVENUE), value(previousQuarter, Field.REVENUE));
        }

        BigDecimal quarterNetIncomeGrowth() {
            return growth(value(quarter, Field.NET_INCOME), value(previousQuarter, Field.NET_INCOME));
        }

        BigDecimal liabilitiesToAssets() {
            if (annual == null || annual.getLiabilities() == null || annual.getAssets() == null
                    || annual.getAssets().compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return annual.getLiabilities().multiply(ONE_HUNDRED)
                    .divide(annual.getAssets(), 4, RoundingMode.HALF_UP);
        }

        BigDecimal operatingCashFlowMargin() {
            if (annual == null || annual.getOperatingCashFlow() == null || annual.getRevenue() == null
                    || annual.getRevenue().compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return annual.getOperatingCashFlow().multiply(ONE_HUNDRED)
                    .divide(annual.getRevenue(), 4, RoundingMode.HALF_UP);
        }

        int quarterGrowthScoreAdjustment() {
            BigDecimal revenueGrowth = quarterRevenueGrowth();
            BigDecimal netIncomeGrowth = quarterNetIncomeGrowth();
            int adjustment = 0;
            adjustment += revenueGrowth == null ? 0 : cappedInt(revenueGrowth.divide(BigDecimal.valueOf(8), 2,
                    RoundingMode.HALF_UP), -6, 6);
            adjustment += netIncomeGrowth == null ? 0 : cappedInt(netIncomeGrowth.divide(BigDecimal.valueOf(10), 2,
                    RoundingMode.HALF_UP), -8, 8);
            return adjustment;
        }

        String annualSourceSuffix() {
            if (annual == null || annual.getFiledAt() == null) {
                return "";
            }
            return ", SEC " + annual.getForm() + " filed " + annual.getFiledAt();
        }

        String quarterSourceSuffix() {
            if (quarter == null || quarter.getFiledAt() == null) {
                return "";
            }
            return ", SEC " + quarter.getFiscalPeriod() + " filed " + quarter.getFiledAt();
        }

        private static BigDecimal growth(BigDecimal current, BigDecimal previous) {
            if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return current.subtract(previous).multiply(ONE_HUNDRED)
                    .divide(previous.abs(), 4, RoundingMode.HALF_UP);
        }

        private static BigDecimal value(SecFinancialStandard row, Field field) {
            if (row == null) {
                return null;
            }
            return switch (field) {
                case REVENUE -> row.getRevenue();
                case NET_INCOME -> row.getNetIncome();
            };
        }

        private enum Field {
            REVENUE,
            NET_INCOME
        }
    }

    private record CandleMomentum(int adjustment, String detail) {
    }

    private record SignalResult(String title, String label, String tone, int score, String detail, boolean available) {
    }
}
