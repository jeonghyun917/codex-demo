package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockQuantOpinionService {

    private static final String EXPECTED_RETURN_V9_MODEL_VERSION = "EXPECTED_RETURN_V9";
    private static final String EXPECTED_RETURN_V8_MODEL_VERSION = "EXPECTED_RETURN_V8";
    private static final String EXPECTED_RETURN_V7_MODEL_VERSION = "EXPECTED_RETURN_V7";
    private static final String EXPECTED_RETURN_V6_MODEL_VERSION = "EXPECTED_RETURN_V6";
    private static final String EXPECTED_RETURN_V5_MODEL_VERSION = "EXPECTED_RETURN_V5";
    private static final String EXPECTED_RETURN_V4_MODEL_VERSION = "EXPECTED_RETURN_V4";
    private static final String EXPECTED_RETURN_V3_MODEL_VERSION = "EXPECTED_RETURN_V3";
    private static final String EXPECTED_RETURN_V2_MODEL_VERSION = "EXPECTED_RETURN_V2";
    private static final int PRIMARY_HORIZON_DAYS = 20;
    private static final int STALE_EXPECTED_RETURN_DAYS = 45;

    private final StockCacheService cacheService;
    private final ObjectProvider<StockBacktestMapper> stockBacktestMapper;

    public StockQuantOpinionService(StockCacheService cacheService,
            ObjectProvider<StockBacktestMapper> stockBacktestMapper) {
        this.cacheService = cacheService;
        this.stockBacktestMapper = stockBacktestMapper;
    }

    public StockQuantOpinionView build(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        StockSignalLatest signal = cacheService.findLatestSignal(normalized);
        StockDataQualityLatest dataQuality = cacheService.findLatestDataQuality(normalized);
        ExpectedRows expected = expectedRows(normalized);
        StockExpectedReturnSnapshot primary = primaryExpectedReturn(expected.rows());
        if (signal == null && primary == null) {
            return null;
        }
        StockRiskSnapshot riskSnapshot = latestRiskSnapshot(normalized);
        StockMarketSnapshot marketSnapshot = latestMarketSnapshot(normalized);

        int signalScore = valueOr(signal == null ? null : signal.getIntegratedScore(), 50);
        int qualityScore = valueOr(dataQuality == null ? null : dataQuality.getQualityScore(), 50);
        double expectedExcess = toDouble(primary == null ? null : primary.getExpectedExcessReturnPct());
        double upside = toDouble(primary == null ? null : primary.getCalibratedUpsideProbabilityPct());
        if (!Double.isFinite(upside)) {
            upside = toDouble(primary == null ? null : primary.getUpsideProbabilityPct());
        }
        double downsideProbability = toDouble(primary == null ? null : primary.getDownsideProbabilityPct());
        double drawdownRisk = toDouble(primary == null ? null : primary.getDrawdownRiskPct());
        int expectedConfidence = valueOr(primary == null ? null : primary.getConfidence(), 0);
        long staleDays = primary == null || primary.getSignalDate() == null
                ? Long.MAX_VALUE
                : ChronoUnit.DAYS.between(primary.getSignalDate(), LocalDate.now());
        StockMacroRegimeSnapshot macroRegime = latestMacroRegime();

        int opinionScore = opinionScore(signalScore, qualityScore, expectedExcess, upside, downsideProbability,
                drawdownRisk, expectedConfidence, staleDays, macroRegime, expected.modelVersion());
        String label = opinionLabel(opinionScore);
        String tone = opinionTone(opinionScore);
        List<StockExpectedReturnFactorContribution> contributionRows =
                factorContributions(normalized, expected.modelVersion());

        List<StockQuantOpinionView.Metric> metrics = metrics(primary, signalScore, qualityScore, staleDays, macroRegime);
        List<StockQuantOpinionView.Factor> factors = factors(signal, contributionRows);
        List<StockQuantOpinionView.HorizonOpinion> horizonOpinions = horizonOpinions(expected.rows());
        StockQuantOpinionView.PortfolioFit portfolioFit = portfolioFit(primary, signalScore, qualityScore,
                riskSnapshot, macroRegime);
        List<String> interpretations = cleanInterpretations(expectedExcess, upside, downsideProbability, drawdownRisk,
                signalScore, qualityScore, expectedConfidence, staleDays, factors, macroRegime);
        List<String> warnings = warnings(primary, dataQuality, expectedConfidence, staleDays, downsideProbability,
                drawdownRisk, factors, expected.modelVersion(), macroRegime, marketSnapshot);
        String summary = cleanSummary(label, expectedExcess, upside, downsideProbability, signalScore, qualityScore,
                expectedConfidence, staleDays, expected.modelVersion(), macroRegime);

        return new StockQuantOpinionView(
                label,
                tone,
                integer(opinionScore) + " / 100",
                summary,
                primary == null || primary.getSignalDate() == null ? "-" : primary.getSignalDate().toString(),
                expected.modelVersion(),
                metrics,
                interpretations,
                horizonOpinions,
                portfolioFit,
                factors,
                warnings);
    }

    private ExpectedRows expectedRows(String symbol) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return new ExpectedRows(EXPECTED_RETURN_V9_MODEL_VERSION, List.of());
        }
        List<StockExpectedReturnSnapshot> v9 =
                mapper.findLatestExpectedReturnSnapshotsBySymbol(symbol, EXPECTED_RETURN_V9_MODEL_VERSION);
        if (v9 != null && !v9.isEmpty()) {
            return new ExpectedRows(EXPECTED_RETURN_V9_MODEL_VERSION, v9);
        }
        List<StockExpectedReturnSnapshot> v8 =
                mapper.findLatestExpectedReturnSnapshotsBySymbol(symbol, EXPECTED_RETURN_V8_MODEL_VERSION);
        if (v8 != null && !v8.isEmpty()) {
            return new ExpectedRows(EXPECTED_RETURN_V8_MODEL_VERSION, v8);
        }
        List<StockExpectedReturnSnapshot> v7 =
                mapper.findLatestExpectedReturnSnapshotsBySymbol(symbol, EXPECTED_RETURN_V7_MODEL_VERSION);
        if (v7 != null && !v7.isEmpty()) {
            return new ExpectedRows(EXPECTED_RETURN_V7_MODEL_VERSION, v7);
        }
        List<StockExpectedReturnSnapshot> v6 =
                mapper.findLatestExpectedReturnSnapshotsBySymbol(symbol, EXPECTED_RETURN_V6_MODEL_VERSION);
        if (v6 != null && !v6.isEmpty()) {
            return new ExpectedRows(EXPECTED_RETURN_V6_MODEL_VERSION, v6);
        }
        List<StockExpectedReturnSnapshot> v5 =
                mapper.findLatestExpectedReturnSnapshotsBySymbol(symbol, EXPECTED_RETURN_V5_MODEL_VERSION);
        if (v5 != null && !v5.isEmpty()) {
            return new ExpectedRows(EXPECTED_RETURN_V5_MODEL_VERSION, v5);
        }
        List<StockExpectedReturnSnapshot> v4 =
                mapper.findLatestExpectedReturnSnapshotsBySymbol(symbol, EXPECTED_RETURN_V4_MODEL_VERSION);
        if (v4 != null && !v4.isEmpty()) {
            return new ExpectedRows(EXPECTED_RETURN_V4_MODEL_VERSION, v4);
        }
        List<StockExpectedReturnSnapshot> v3 =
                mapper.findLatestExpectedReturnSnapshotsBySymbol(symbol, EXPECTED_RETURN_V3_MODEL_VERSION);
        if (v3 != null && !v3.isEmpty()) {
            return new ExpectedRows(EXPECTED_RETURN_V3_MODEL_VERSION, v3);
        }
        List<StockExpectedReturnSnapshot> v2 =
                mapper.findLatestExpectedReturnSnapshotsBySymbol(symbol, EXPECTED_RETURN_V2_MODEL_VERSION);
        return new ExpectedRows(EXPECTED_RETURN_V2_MODEL_VERSION, v2 == null ? List.of() : v2);
    }

    private List<StockExpectedReturnFactorContribution> factorContributions(String symbol, String modelVersion) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || symbol == null || modelVersion == null) {
            return List.of();
        }
        List<StockExpectedReturnFactorContribution> rows =
                mapper.findLatestExpectedReturnFactorContributionsBySymbol(symbol, modelVersion, PRIMARY_HORIZON_DAYS);
        return rows == null ? List.of() : rows;
    }

    private StockMacroRegimeSnapshot latestMacroRegime() {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestMacroRegimeSnapshot("SP500");
    }

    private StockRiskSnapshot latestRiskSnapshot(String symbol) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestRiskSnapshotBySymbol(symbol);
    }

    private StockMarketSnapshot latestMarketSnapshot(String symbol) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestMarketSnapshotBySymbol(symbol);
    }

    private static StockExpectedReturnSnapshot primaryExpectedReturn(List<StockExpectedReturnSnapshot> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.stream()
                .filter(row -> Objects.equals(row.getHorizonDays(), PRIMARY_HORIZON_DAYS))
                .findFirst()
                .orElse(rows.get(0));
    }

    private static int opinionScore(int signalScore, int qualityScore, double expectedExcess, double upside,
            double downsideProbability, double drawdownRisk, int expectedConfidence, long staleDays,
            StockMacroRegimeSnapshot macroRegime, String modelVersion) {
        double score = 50.0d;
        score += Math.max(-18.0d, Math.min(18.0d, (signalScore - 50) * 0.42d));
        score += Math.max(-14.0d, Math.min(16.0d, finiteOrZero(expectedExcess) * 3.0d));
        if (Double.isFinite(upside)) {
            score += Math.max(-12.0d, Math.min(12.0d, (upside - 50.0d) * 0.34d));
        }
        if (Double.isFinite(downsideProbability) && downsideProbability > 35.0d) {
            score -= Math.min(11.0d, (downsideProbability - 35.0d) * 0.35d);
        }
        if (Double.isFinite(drawdownRisk) && drawdownRisk < -8.0d) {
            score -= Math.min(9.0d, Math.abs(drawdownRisk + 8.0d) * 0.35d);
        }
        score += Math.max(-8.0d, Math.min(5.0d, (qualityScore - 70.0d) * 0.16d));
        if (expectedConfidence > 0 && expectedConfidence < 45) {
            score -= 6.0d;
        }
        if (staleDays > STALE_EXPECTED_RETURN_DAYS) {
            score -= Math.min(10.0d, 4.0d + (staleDays - STALE_EXPECTED_RETURN_DAYS) / 20.0d);
        }
        if (macroRegime != null && !isRegimeAwareModel(modelVersion)) {
            if ("RISK_OFF".equals(macroRegime.getRegimeLabel())) {
                score -= 5.0d;
            } else if ("RISK_ON".equals(macroRegime.getRegimeLabel())) {
                score += 2.0d;
            }
        }
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    private static List<StockQuantOpinionView.Metric> metrics(StockExpectedReturnSnapshot primary, int signalScore,
            int qualityScore, long staleDays, StockMacroRegimeSnapshot macroRegime) {
        List<StockQuantOpinionView.Metric> rows = new ArrayList<>();
        rows.add(new StockQuantOpinionView.Metric("Expected 20D excess",
                signedPercent(toDouble(primary == null ? null : primary.getExpectedExcessReturnPct())),
                "Expected return versus benchmark proxy",
                returnTone(toDouble(primary == null ? null : primary.getExpectedExcessReturnPct()))));
        rows.add(new StockQuantOpinionView.Metric("Return range",
                range(primary),
                "10% / 50% / 90% quantiles", "neutral"));
        double upside = toDouble(primary == null ? null : primary.getCalibratedUpsideProbabilityPct());
        if (!Double.isFinite(upside)) {
            upside = toDouble(primary == null ? null : primary.getUpsideProbabilityPct());
        }
        rows.add(new StockQuantOpinionView.Metric("Upside probability",
                metricPercent(upside),
                "Calibrated probability when available", probabilityTone(upside)));
        rows.add(new StockQuantOpinionView.Metric("Downside risk",
                metricPercent(toDouble(primary == null ? null : primary.getDownsideProbabilityPct())),
                "Probability of material downside", downsideTone(toDouble(primary == null ? null : primary.getDownsideProbabilityPct()))));
        rows.add(new StockQuantOpinionView.Metric("Model confidence",
                primary == null || primary.getConfidence() == null ? "-" : primary.getConfidence() + " / 100",
                primary == null ? "Expected return snapshot missing" : "sample "
                        + valueOr(primary.getSampleCount(), 0) + ", sector " + valueOr(primary.getSectorSampleCount(), 0),
                confidenceTone(valueOr(primary == null ? null : primary.getConfidence(), 0))));
        rows.add(new StockQuantOpinionView.Metric("Signal / Quality",
                signalScore + " / " + qualityScore,
                staleDays > STALE_EXPECTED_RETURN_DAYS ? "Expected snapshot " + staleDays + " days old" : "Stored latest signal",
                qualityScore >= 70 ? "positive" : qualityScore >= 50 ? "neutral" : "caution"));
        rows.add(new StockQuantOpinionView.Metric("Market regime",
                macroRegime == null ? "-" : macroRegimeLabel(macroRegime) + " · " + macroRegime.getMacroScore() + " / 100",
                macroRegime == null || macroRegime.getSnapshotDate() == null ? "macro snapshot missing"
                        : "S&P 500 regime " + macroRegime.getSnapshotDate(),
                macroRegimeTone(macroRegime)));
        return rows;
    }

    private static List<StockQuantOpinionView.HorizonOpinion> horizonOpinions(
            List<StockExpectedReturnSnapshot> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .sorted(Comparator.comparingInt(row -> valueOr(row.getHorizonDays(), Integer.MAX_VALUE)))
                .map(StockQuantOpinionService::horizonOpinion)
                .toList();
    }

    private static StockQuantOpinionView.HorizonOpinion horizonOpinion(StockExpectedReturnSnapshot row) {
        double expectedExcess = toDouble(row.getExpectedExcessReturnPct());
        double upside = toDouble(row.getCalibratedUpsideProbabilityPct());
        if (!Double.isFinite(upside)) {
            upside = toDouble(row.getUpsideProbabilityPct());
        }
        int confidence = valueOr(row.getConfidence(), 0);
        String label;
        String tone;
        if (Double.isFinite(expectedExcess) && expectedExcess >= 0.35d
                && Double.isFinite(upside) && upside >= 52.0d && confidence >= 45) {
            label = "우호";
            tone = "positive";
        } else if ((Double.isFinite(expectedExcess) && expectedExcess <= -0.35d)
                || (Double.isFinite(upside) && upside <= 47.0d)) {
            label = "주의";
            tone = "negative";
        } else {
            label = "중립";
            tone = "neutral";
        }
        return new StockQuantOpinionView.HorizonOpinion(
                row.getHorizonDays() == null ? "-" : row.getHorizonDays() + "D",
                label,
                signedPercent(expectedExcess),
                metricPercent(upside),
                confidence <= 0 ? "-" : confidence + " / 100",
                tone);
    }

    private static StockQuantOpinionView.PortfolioFit portfolioFit(StockExpectedReturnSnapshot primary,
            int signalScore, int qualityScore, StockRiskSnapshot riskSnapshot, StockMacroRegimeSnapshot macroRegime) {
        double expectedExcess = toDouble(primary == null ? null : primary.getExpectedExcessReturnPct());
        double upside = toDouble(primary == null ? null : primary.getCalibratedUpsideProbabilityPct());
        if (!Double.isFinite(upside)) {
            upside = toDouble(primary == null ? null : primary.getUpsideProbabilityPct());
        }
        double beta = toDouble(riskSnapshot == null ? null : riskSnapshot.getBeta());
        double volatility = toDouble(riskSnapshot == null ? null : riskSnapshot.getVolatilityPct());
        double liquidity = toDouble(riskSnapshot == null ? null : riskSnapshot.getAvgDollarVolume());

        double score = 50.0d;
        score += Math.max(-16.0d, Math.min(18.0d, finiteOrZero(expectedExcess) * 3.8d));
        if (Double.isFinite(upside)) {
            score += Math.max(-10.0d, Math.min(10.0d, (upside - 50.0d) * 0.28d));
        }
        score += Math.max(-8.0d, Math.min(8.0d, (signalScore - 50.0d) * 0.15d));
        score += Math.max(-6.0d, Math.min(6.0d, (qualityScore - 60.0d) * 0.12d));
        if (Double.isFinite(beta) && beta > 1.25d) {
            score -= Math.min(10.0d, (beta - 1.25d) * 18.0d);
        }
        if (Double.isFinite(volatility) && volatility > 34.0d) {
            score -= Math.min(10.0d, (volatility - 34.0d) * 0.35d);
        }
        if (Double.isFinite(liquidity) && liquidity < 20_000_000.0d) {
            score -= 8.0d;
        }
        if (macroRegime != null && "RISK_OFF".equals(macroRegime.getRegimeLabel())
                && Double.isFinite(beta) && beta > 1.05d) {
            score -= 6.0d;
        }
        int fitScore = Math.max(0, Math.min(100, (int) Math.round(score)));
        String label = fitScore >= 65 ? "편입 우호" : fitScore >= 45 ? "제한 비중 후보" : "편입 주의";
        String tone = fitScore >= 65 ? "positive" : fitScore >= 45 ? "neutral" : "caution";
        String summary = switch (tone) {
            case "positive" -> "기대수익과 위험 조건이 함께 양호해 포트폴리오 후보로 볼 수 있습니다.";
            case "neutral" -> "방향성은 있으나 위험, 신뢰도, 또는 유동성 조건 때문에 제한 비중 검토가 맞습니다.";
            default -> "현재 저장된 기대수익 대비 위험 부담이 커서 단독 편입은 보수적으로 봐야 합니다.";
        };
        List<StockQuantOpinionView.Metric> metrics = List.of(
                new StockQuantOpinionView.Metric("Fit score", fitScore + " / 100", "expected return + risk", tone),
                new StockQuantOpinionView.Metric("Beta", decimalOrDash(beta), "126D market beta proxy", betaTone(beta)),
                new StockQuantOpinionView.Metric("Volatility", metricPercent(volatility), "126D trailing volatility",
                        volatilityTone(volatility)),
                new StockQuantOpinionView.Metric("Liquidity", dollarCompact(liquidity), "63D average dollar volume",
                        liquidityTone(liquidity)));
        return new StockQuantOpinionView.PortfolioFit(label, tone, summary, metrics);
    }

    private static List<StockQuantOpinionView.Factor> factors(StockSignalLatest signal,
            List<StockExpectedReturnFactorContribution> contributions) {
        Map<String, StockExpectedReturnFactorContribution> byFactor = contributions == null
                ? Map.of()
                : contributions.stream()
                        .collect(Collectors.toMap(StockExpectedReturnFactorContribution::getFactor,
                                row -> row, (left, right) -> left, LinkedHashMap::new));
        if (!byFactor.isEmpty()) {
            List<FactorDraft> drafts = byFactor.values().stream()
                    .map(row -> factorFromContribution(row, signal))
                    .toList();
            return normalizeFactors(drafts);
        }
        return signalFactors(signal);
    }

    private static FactorDraft factorFromContribution(
            StockExpectedReturnFactorContribution contribution, StockSignalLatest signal) {
        Integer score = factorScore(signal, contribution.getFactor());
        double value = toDouble(contribution.getContributionPct());
        return new FactorDraft(
                contribution.getFactor(),
                score == null ? exposureLabel(contribution) : score + " / 100",
                signedPercent(value),
                returnTone(value),
                value);
    }

    private static String exposureLabel(StockExpectedReturnFactorContribution contribution) {
        double exposure = toDouble(contribution.getExposureScore());
        return Double.isFinite(exposure) ? String.format(Locale.US, "exposure %.2f", exposure) : "-";
    }

    private static List<StockQuantOpinionView.Factor> signalFactors(StockSignalLatest signal) {
        if (signal == null) {
            return List.of();
        }
        List<FactorDraft> drafts = new ArrayList<>();
        addSignalFactor(drafts, "Valuation", signal.getValuationScore());
        addSignalFactor(drafts, "Quality", signal.getQualityScore());
        addSignalFactor(drafts, "Growth", signal.getGrowthScore());
        addSignalFactor(drafts, "Stability", signal.getStabilityScore());
        addSignalFactor(drafts, "Earnings", signal.getEarningsScore());
        addSignalFactor(drafts, "Analyst", signal.getAnalystScore());
        addSignalFactor(drafts, "News", signal.getNewsScore());
        addSignalFactor(drafts, "Momentum", signal.getMomentumScore());
        addSignalFactor(drafts, "Risk", signal.getRiskScore());
        addSignalFactor(drafts, "Institution", signal.getInstitutionScore());
        return normalizeFactors(drafts);
    }

    private static void addSignalFactor(List<FactorDraft> rows, String label, Integer score) {
        if (score == null) {
            return;
        }
        int contribution = score - 50;
        rows.add(new FactorDraft(label, score + " / 100",
                contribution >= 0 ? "+" + contribution : String.valueOf(contribution),
                factorTone(score),
                contribution));
    }

    private static List<StockQuantOpinionView.Factor> normalizeFactors(List<FactorDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        double maxAbs = drafts.stream()
                .mapToDouble(factor -> Math.abs(factor.numericValue()))
                .filter(Double::isFinite)
                .max()
                .orElse(0.0d);
        return drafts.stream()
                .sorted(Comparator.comparingDouble((FactorDraft factor) ->
                        Math.abs(factor.numericValue())).reversed())
                .map(factor -> factor.toView(maxAbs))
                .toList();
    }

    private static Integer factorScore(StockSignalLatest signal, String factor) {
        if (signal == null || factor == null) {
            return null;
        }
        return switch (factor) {
            case "Valuation" -> signal.getValuationScore();
            case "Quality" -> signal.getQualityScore();
            case "Growth" -> signal.getGrowthScore();
            case "Stability" -> signal.getStabilityScore();
            case "Earnings" -> signal.getEarningsScore();
            case "Analyst" -> signal.getAnalystScore();
            case "News" -> signal.getNewsScore();
            case "Momentum" -> signal.getMomentumScore();
            case "Risk" -> signal.getRiskScore();
            case "Institution" -> signal.getInstitutionScore();
            default -> null;
        };
    }

    private static List<String> warnings(StockExpectedReturnSnapshot primary, StockDataQualityLatest quality,
            int expectedConfidence, long staleDays, double downsideProbability, double drawdownRisk,
            List<StockQuantOpinionView.Factor> factors, String modelVersion, StockMacroRegimeSnapshot macroRegime,
            StockMarketSnapshot marketSnapshot) {
        List<String> rows = new ArrayList<>();
        if (primary == null) {
            rows.add("Expected return snapshot is missing. Opinion falls back to Signal and data quality.");
        } else if (staleDays > STALE_EXPECTED_RETURN_DAYS) {
            rows.add("Expected return snapshot is " + staleDays + " days old. Treat the opinion as stale.");
        }
        if (!hasFactorContributionModel(modelVersion)) {
            rows.add("Factor-level contribution is missing, so v2 expected-return data is used.");
        } else if (!isRegimeAwareModel(modelVersion)) {
            rows.add("Regime-aware prediction is missing, so v3 factor data is used.");
        }
        if (expectedConfidence > 0 && expectedConfidence < 45) {
            rows.add("Model confidence is low. Use this as a conservative ranking signal, not a forecast.");
        }
        if (quality != null && quality.getQualityScore() != null && quality.getQualityScore() < 60) {
            rows.add("Data quality is weak: " + quality.getQualityScore() + " / 100.");
        }
        if (Double.isFinite(downsideProbability) && downsideProbability >= 45.0d) {
            rows.add("Downside probability is elevated.");
        }
        if (Double.isFinite(drawdownRisk) && drawdownRisk <= -10.0d) {
            rows.add("10% return quantile is weak at " + signedPercent(drawdownRisk) + ".");
        }
        if (macroRegime != null && "RISK_OFF".equals(macroRegime.getRegimeLabel())) {
            rows.add("Broad market regime is Risk-off, so the opinion is adjusted conservatively.");
        }
        if (marketSnapshot == null) {
            rows.add("Market snapshot is missing. PIT provenance for market cap and shares could not be checked.");
        } else if (usesProxyMarketData(marketSnapshot)) {
            rows.add("Market snapshot uses proxy/current-source shares or market cap (sharesSource="
                    + blankToDefault(marketSnapshot.getSharesSource(), "-")
                    + ", marketCapSource=" + blankToDefault(marketSnapshot.getMarketCapSource(), "-")
                    + "). Treat expected return confidence conservatively.");
        }
        factors.stream()
                .filter(factor -> "negative".equals(factor.tone()))
                .limit(2)
                .map(factor -> factor.label() + " is a negative contributor (" + factor.contribution() + ").")
                .forEach(rows::add);
        if (rows.isEmpty()) {
            rows.add("No major warning from the current stored data.");
        }
        return rows;
    }

    private static List<String> cleanInterpretations(double expectedExcess, double upside, double downsideProbability,
            double drawdownRisk, int signalScore, int qualityScore, int expectedConfidence, long staleDays,
            List<StockQuantOpinionView.Factor> factors, StockMacroRegimeSnapshot macroRegime) {
        List<String> rows = new ArrayList<>();
        if (Double.isFinite(expectedExcess) && expectedExcess > 0.35d && signalScore < 50) {
            rows.add("20D 기대 초과수익은 플러스지만 Signal이 약합니다. 구조적 매력보다 단기 반등 또는 상대 강도 신호일 수 있습니다.");
        }
        if (Double.isFinite(expectedExcess) && expectedExcess < -0.35d && signalScore >= 65) {
            rows.add("Signal은 강하지만 최신 기대수익 모델은 20D 초과수익을 낮게 봅니다. 좋은 기업이어도 단기 진입은 보수적으로 해석합니다.");
        }
        if (Double.isFinite(upside) && upside >= 52.0d && expectedConfidence > 0 && expectedConfidence < 55) {
            rows.add("상승 확률은 50%를 넘지만 모델 신뢰도가 높지 않습니다. 방향성보다 순위 참고용으로 보는 편이 안전합니다.");
        }
        if (Double.isFinite(downsideProbability) && downsideProbability >= 40.0d
                && Double.isFinite(expectedExcess) && expectedExcess > 0.0d) {
            rows.add("기대 초과수익은 플러스지만 downside risk도 높습니다. 편입하더라도 비중 제한이 필요합니다.");
        }
        if (Double.isFinite(drawdownRisk) && drawdownRisk <= -10.0d) {
            rows.add("하위 10% 수익률 추정이 약합니다. 상승 의견이 있어도 tail risk를 먼저 확인해야 합니다.");
        }
        if (qualityScore < 60 && Double.isFinite(expectedExcess) && expectedExcess > 0.0d) {
            rows.add("데이터 품질이 낮아 긍정 기대수익의 신뢰도가 제한됩니다. freshness와 coverage를 먼저 확인합니다.");
        }
        if (macroRegime != null && "RISK_OFF".equals(macroRegime.getRegimeLabel())
                && Double.isFinite(expectedExcess) && expectedExcess > 0.0d) {
            rows.add("시장 국면은 Risk-off입니다. 종목 alpha가 좋아도 beta와 drawdown 방어력이 중요합니다.");
        }
        factors.stream()
                .filter(factor -> "positive".equals(factor.tone()))
                .limit(1)
                .map(factor -> "가장 강한 긍정 근거는 " + factor.label() + "입니다. 기여도는 "
                        + factor.contribution() + "로 표시됩니다.")
                .forEach(rows::add);
        factors.stream()
                .filter(factor -> "negative".equals(factor.tone()))
                .limit(1)
                .map(factor -> "가장 큰 반대 근거는 " + factor.label() + "입니다. 기여도 "
                        + factor.contribution() + "가 의견 점수를 제한합니다.")
                .forEach(rows::add);
        if (staleDays > STALE_EXPECTED_RETURN_DAYS) {
            rows.add("Expected Return snapshot이 오래되어 현재 가격 변화가 충분히 반영되지 않았을 수 있습니다.");
        }
        if (rows.isEmpty()) {
            rows.add("Signal, 기대수익, 확률, 리스크가 크게 충돌하지 않습니다. 현재 의견은 저장된 모델 입력과 대체로 일관됩니다.");
        }
        return rows.stream().limit(6).toList();
    }

    private static String cleanSummary(String label, double expectedExcess, double upside, double downsideProbability,
            int signalScore, int qualityScore, int expectedConfidence, long staleDays, String modelVersion,
            StockMacroRegimeSnapshot macroRegime) {
        return label + " 의견입니다. 20D 기대 초과수익 "
                + signedPercent(expectedExcess)
                + ", 보정 상승확률 " + metricPercent(upside)
                + ", 하락확률 " + metricPercent(downsideProbability)
                + ", Signal " + signalScore
                + ", 데이터 품질 " + qualityScore
                + ", 모델 " + modelVersion
                + ", 신뢰도 " + expectedConfidence + " / 100"
                + (macroRegime == null ? "" : ", 시장 국면 " + macroRegimeLabel(macroRegime))
                + (staleDays > STALE_EXPECTED_RETURN_DAYS ? ". 스냅샷이 오래되어 보수적으로 봅니다." : ".");
    }

    private static List<String> interpretations(double expectedExcess, double upside, double downsideProbability,
            double drawdownRisk, int signalScore, int qualityScore, int expectedConfidence, long staleDays,
            List<StockQuantOpinionView.Factor> factors, StockMacroRegimeSnapshot macroRegime) {
        List<String> rows = new ArrayList<>();
        if (Double.isFinite(expectedExcess) && expectedExcess > 0.35d && signalScore < 50) {
            rows.add("20D 기대 초과수익은 플러스지만 Signal이 약합니다. 구조적 매력보다 단기 반등/상대 강도 신호일 수 있습니다.");
        }
        if (Double.isFinite(expectedExcess) && expectedExcess < -0.35d && signalScore >= 65) {
            rows.add("Signal은 강하지만 최신 Expected Return 모델은 단기 초과수익을 낮게 봅니다. 좋은 기업이어도 단기 진입 타이밍은 보수적으로 해석합니다.");
        }
        if (Double.isFinite(upside) && upside >= 52.0d && expectedConfidence > 0 && expectedConfidence < 55) {
            rows.add("상승 확률은 50%를 넘지만 모델 신뢰도가 높지 않습니다. 방향성보다 순위 참고용으로 보는 게 맞습니다.");
        }
        if (Double.isFinite(downsideProbability) && downsideProbability >= 40.0d
                && Double.isFinite(expectedExcess) && expectedExcess > 0.0d) {
            rows.add("기대 초과수익은 플러스지만 downside risk도 높습니다. 포지션 크기를 키우기보다 위험 제한이 필요합니다.");
        }
        if (Double.isFinite(drawdownRisk) && drawdownRisk <= -10.0d) {
            rows.add("하위 10% 수익률 추정이 약합니다. 상승 의견이 있어도 tail risk를 먼저 확인해야 합니다.");
        }
        if (qualityScore < 60 && Double.isFinite(expectedExcess) && expectedExcess > 0.0d) {
            rows.add("데이터 품질이 낮아 긍정 기대수익의 신뢰도가 제한됩니다. 원천 데이터 freshness와 coverage를 먼저 확인합니다.");
        }
        if (macroRegime != null && "RISK_OFF".equals(macroRegime.getRegimeLabel())
                && Double.isFinite(expectedExcess) && expectedExcess > 0.0d) {
            rows.add("시장 국면은 Risk-off입니다. 종목 alpha가 좋아도 시장 beta와 drawdown 방어력이 중요합니다.");
        }
        factors.stream()
                .filter(factor -> "positive".equals(factor.tone()))
                .limit(1)
                .map(factor -> "가장 강한 긍정 근거는 " + factor.label() + "입니다. 기여도 " + factor.contribution()
                        + "로 표시됩니다.")
                .forEach(rows::add);
        factors.stream()
                .filter(factor -> "negative".equals(factor.tone()))
                .limit(1)
                .map(factor -> "가장 큰 반대 근거는 " + factor.label() + "입니다. 기여도 " + factor.contribution()
                        + "가 opinion 점수를 제한합니다.")
                .forEach(rows::add);
        if (staleDays > STALE_EXPECTED_RETURN_DAYS) {
            rows.add("Expected Return snapshot이 오래되어 현재 가격/뉴스 변화가 충분히 반영되지 않았을 수 있습니다.");
        }
        if (rows.isEmpty()) {
            rows.add("Signal, 기대수익, 확률, 리스크가 크게 충돌하지 않습니다. 현재 opinion은 저장된 모델 입력과 대체로 일관됩니다.");
        }
        return rows.stream().limit(6).toList();
    }

    private static boolean hasFactorContributionModel(String modelVersion) {
        return EXPECTED_RETURN_V9_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V8_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V7_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V6_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V5_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V4_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V3_MODEL_VERSION.equals(modelVersion);
    }

    private static boolean isRegimeAwareModel(String modelVersion) {
        return EXPECTED_RETURN_V9_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V8_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V7_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V6_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V5_MODEL_VERSION.equals(modelVersion)
                || EXPECTED_RETURN_V4_MODEL_VERSION.equals(modelVersion);
    }

    private static boolean usesProxyMarketData(StockMarketSnapshot snapshot) {
        return isProxySource(snapshot.getSharesSource()) || isProxySource(snapshot.getMarketCapSource());
    }

    private static boolean isProxySource(String source) {
        if (source == null || source.isBlank()) {
            return true;
        }
        String normalized = source.toUpperCase(Locale.ROOT);
        return normalized.contains("PROXY")
                || normalized.contains("CURRENT")
                || normalized.contains("FALLBACK")
                || normalized.contains("FINNHUB")
                || normalized.contains("TOSS")
                || normalized.contains("MISSING");
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String summary(String label, double expectedExcess, double upside, double downsideProbability,
            int signalScore, int qualityScore, int expectedConfidence, long staleDays, String modelVersion,
            StockMacroRegimeSnapshot macroRegime) {
        return label + " opinion. Expected 20D excess return "
                + signedPercent(expectedExcess)
                + ", calibrated upside probability " + metricPercent(upside)
                + ", downside probability " + metricPercent(downsideProbability)
                + ", Signal " + signalScore
                + ", data quality " + qualityScore
                + ", model " + modelVersion
                + ", confidence " + expectedConfidence + " / 100"
                + (macroRegime == null ? "" : ", regime " + macroRegimeLabel(macroRegime))
                + (staleDays > STALE_EXPECTED_RETURN_DAYS ? ". Snapshot is stale." : ".");
    }

    private static String macroRegimeLabel(StockMacroRegimeSnapshot macroRegime) {
        if (macroRegime == null || macroRegime.getRegimeLabel() == null) {
            return "-";
        }
        return switch (macroRegime.getRegimeLabel()) {
            case "RISK_ON" -> "Risk-on";
            case "RISK_OFF" -> "Risk-off";
            default -> "Neutral";
        };
    }

    private static String macroRegimeTone(StockMacroRegimeSnapshot macroRegime) {
        if (macroRegime == null || macroRegime.getRegimeLabel() == null) {
            return "neutral";
        }
        return switch (macroRegime.getRegimeLabel()) {
            case "RISK_ON" -> "positive";
            case "RISK_OFF" -> "negative";
            default -> "neutral";
        };
    }

    private static String opinionLabel(int score) {
        if (score >= 65) {
            return "Attractive";
        }
        if (score >= 45) {
            return "Neutral";
        }
        return "Caution";
    }

    private static String opinionTone(int score) {
        if (score >= 65) {
            return "positive";
        }
        if (score >= 45) {
            return "neutral";
        }
        return "caution";
    }

    private static String factorTone(int score) {
        if (score >= 65) {
            return "positive";
        }
        if (score <= 40) {
            return "negative";
        }
        return "neutral";
    }

    private static String returnTone(double value) {
        if (!Double.isFinite(value)) {
            return "neutral";
        }
        if (value > 0) {
            return "positive";
        }
        if (value < 0) {
            return "negative";
        }
        return "neutral";
    }

    private static String probabilityTone(double value) {
        if (!Double.isFinite(value)) {
            return "neutral";
        }
        if (value >= 55.0d) {
            return "positive";
        }
        if (value <= 45.0d) {
            return "negative";
        }
        return "neutral";
    }

    private static String downsideTone(double value) {
        if (!Double.isFinite(value)) {
            return "neutral";
        }
        if (value >= 45.0d) {
            return "negative";
        }
        if (value <= 25.0d) {
            return "positive";
        }
        return "neutral";
    }

    private static String confidenceTone(int value) {
        if (value >= 65) {
            return "positive";
        }
        if (value >= 45) {
            return "neutral";
        }
        return "caution";
    }

    private static String range(StockExpectedReturnSnapshot row) {
        if (row == null) {
            return "-";
        }
        return signedPercent(toDouble(row.getReturnP10Pct())) + " / "
                + signedPercent(toDouble(row.getReturnP50Pct())) + " / "
                + signedPercent(toDouble(row.getReturnP90Pct()));
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0d;
    }

    private static int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String integer(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String signedPercent(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%+.2f%%", value) : "-";
    }

    private static String metricPercent(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f%%", value) : "-";
    }

    private static String decimalOrDash(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : "-";
    }

    private static String dollarCompact(double value) {
        if (!Double.isFinite(value)) {
            return "-";
        }
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000.0d) {
            return String.format(Locale.US, "$%.1fB", value / 1_000_000_000.0d);
        }
        if (abs >= 1_000_000.0d) {
            return String.format(Locale.US, "$%.1fM", value / 1_000_000.0d);
        }
        return String.format(Locale.US, "$%,.0f", value);
    }

    private static String betaTone(double value) {
        if (!Double.isFinite(value)) {
            return "neutral";
        }
        if (value <= 0.95d) {
            return "positive";
        }
        if (value >= 1.30d) {
            return "caution";
        }
        return "neutral";
    }

    private static String volatilityTone(double value) {
        if (!Double.isFinite(value)) {
            return "neutral";
        }
        if (value <= 22.0d) {
            return "positive";
        }
        if (value >= 36.0d) {
            return "caution";
        }
        return "neutral";
    }

    private static String liquidityTone(double value) {
        if (!Double.isFinite(value)) {
            return "neutral";
        }
        if (value >= 100_000_000.0d) {
            return "positive";
        }
        if (value < 20_000_000.0d) {
            return "caution";
        }
        return "neutral";
    }

    private record FactorDraft(String label, String score, String contribution, String tone, double numericValue) {

        private StockQuantOpinionView.Factor toView(double maxAbs) {
            double width = 0.0d;
            if (Double.isFinite(maxAbs) && maxAbs > 0.0d && Double.isFinite(numericValue)) {
                width = Math.max(8.0d, Math.min(100.0d, Math.abs(numericValue) / maxAbs * 100.0d));
            }
            String direction = numericValue < 0.0d ? "negative" : numericValue > 0.0d ? "positive" : "neutral";
            return new StockQuantOpinionView.Factor(
                    label,
                    score,
                    contribution,
                    tone,
                    String.format(Locale.US, "--bar-width: %.1f%%;", width),
                    direction);
        }
    }

    private record ExpectedRows(String modelVersion, List<StockExpectedReturnSnapshot> rows) {
    }
}
