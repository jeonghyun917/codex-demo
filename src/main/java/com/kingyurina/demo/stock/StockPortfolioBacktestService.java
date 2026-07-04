package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;
import tools.jackson.databind.ObjectMapper;

@Service
public class StockPortfolioBacktestService {

    private static final int VIEW_RESULT_LIMIT = 100_000;
    private static final double BASE_TRADE_COST_PCT = 0.04d;
    private static final List<Integer> HORIZONS = List.of(20, 60);
    private static final List<Integer> TOP_COUNTS = List.of(10, 20, 50);
    private static final int RISK_LOOKBACK_DAYS = 420;
    private static final int RISK_RETURN_WINDOW = 126;
    private static final int RISK_LIQUIDITY_WINDOW = 63;
    private static final int MIN_RISK_OBSERVATIONS = 40;
    private static final int DEFAULT_COVARIANCE_LOOKBACK_DAYS = 126;
    private static final int MIN_COVARIANCE_OBSERVATIONS = 40;
    private static final String EXPECTED_RETURN_MODEL_VERSION = "EXPECTED_RETURN_V9";
    private static final String EXPECTED_RETURN_V2_MODEL_VERSION = "EXPECTED_RETURN_V2";
    private static final String EXPECTED_RETURN_V3_MODEL_VERSION = "EXPECTED_RETURN_V3";
    private static final String EXPECTED_RETURN_V4_MODEL_VERSION = "EXPECTED_RETURN_V4";
    private static final String EXPECTED_RETURN_V5_MODEL_VERSION = "EXPECTED_RETURN_V5";
    private static final String EXPECTED_RETURN_V6_MODEL_VERSION = "EXPECTED_RETURN_V6";
    private static final String EXPECTED_RETURN_V7_MODEL_VERSION = "EXPECTED_RETURN_V7";
    private static final String EXPECTED_RETURN_HISTORICAL_SOURCE = "PIT_HISTORICAL_SCORE_SECTOR_QUANTILE_CALIBRATION";
    private static final String EXPECTED_RETURN_LIVE_SOURCE = "LIVE_SIGNAL_LATEST_EXPECTED_RETURN";
    private static final String EXPECTED_RETURN_V3_HISTORICAL_SOURCE = "FACTOR_EXPOSURE_WALK_FORWARD_V3";
    private static final String EXPECTED_RETURN_V3_LIVE_SOURCE = "LIVE_FACTOR_EXPOSURE_EXPECTED_RETURN_V3";
    private static final String EXPECTED_RETURN_V4_HISTORICAL_SOURCE = "REGIME_FACTOR_EXPOSURE_WALK_FORWARD_V4";
    private static final String EXPECTED_RETURN_V4_LIVE_SOURCE = "LIVE_REGIME_FACTOR_EXPOSURE_EXPECTED_RETURN_V4";
    private static final String EXPECTED_RETURN_V5_HISTORICAL_SOURCE = "REGIME_NONLINEAR_INTERACTION_WALK_FORWARD_V5";
    private static final String EXPECTED_RETURN_V5_LIVE_SOURCE = "LIVE_REGIME_NONLINEAR_INTERACTION_EXPECTED_RETURN_V5";
    private static final String EXPECTED_RETURN_V6_HISTORICAL_SOURCE = "CALIBRATION_STABILIZED_EXPECTED_RETURN_V6";
    private static final String EXPECTED_RETURN_V6_LIVE_SOURCE = "LIVE_CALIBRATION_STABILIZED_EXPECTED_RETURN_V6";
    private static final String EXPECTED_RETURN_V7_HISTORICAL_SOURCE = "HORIZON_DECAY_STABILIZED_EXPECTED_RETURN_V7";
    private static final String EXPECTED_RETURN_V7_LIVE_SOURCE = "LIVE_HORIZON_DECAY_STABILIZED_EXPECTED_RETURN_V7";
    private static final String FACTOR_EXPOSURE_SOURCE = "SIGNAL_FACTOR_SCORE_V3";
    private static final String DEFAULT_RISK_FREE_SERIES_CODE = "DGS3MO";
    private static final String PORTFOLIO_VIEW_VERSION = "PORTFOLIO_OPTIMIZATION_V5_OJALGO_SHADOW_MATERIALIZED_V3";
    private static final String OJALGO_SHADOW_OPTIMIZER_V1 = "OJALGO_QP_SHADOW_V1";
    private static final String OJALGO_SHADOW_OPTIMIZER_V2 = "OJALGO_QP_SHADOW_V2";
    private static final List<String> OJALGO_SHADOW_OPTIMIZERS = List.of(
            OJALGO_SHADOW_OPTIMIZER_V1,
            OJALGO_SHADOW_OPTIMIZER_V2);
    private static final int MIN_EXPECTED_RETURN_TRAINING_ROWS = 300;
    private static final int MIN_EXPECTED_RETURN_CALIBRATION_BUCKET_ROWS = 200;
    private static final double EXPECTED_RETURN_DOWNSIDE_THRESHOLD_PCT = -5.0d;
    private static final int PRIMARY_HORIZON_DAYS = 20;
    private static final double MIN_FACTOR_EXPOSURE = 0.20d;
    private static final double MIN_INTERACTION_EXPOSURE = 0.12d;
    private static final double RISK_SECTOR_CAP = 0.30d;
    private static final double RISK_POSITION_CAP_DEFAULT = 0.08d;
    private static final double RISK_POSITION_CAP_TOP10 = 0.12d;
    private static final double OPTIMIZER_TARGET_BETA = 0.95d;
    private static final double OPTIMIZER_TARGET_VOLATILITY_PCT = 24.0d;
    private static final double OPTIMIZER_ACTIVE_SECTOR_LIMIT = 0.12d;
    private static final double OPTIMIZER_MIN_POSITION_WEIGHT = 0.005d;
    private static final double OPTIMIZER_V2_CORRELATION_THRESHOLD = 0.58d;
    private static final double OPTIMIZER_V3_SHIFT_STEP = 0.01d;
    private static final int OPTIMIZER_V3_MAX_ITERATIONS = 10;
    private static final int OPTIMIZER_V3_SOURCE_LIMIT = 4;
    private static final int OPTIMIZER_V3_TARGET_LIMIT = 5;
    private static final int OPTIMIZER_V4_MAX_ITERATIONS = 60;
    private static final double OPTIMIZER_V4_INITIAL_STEP = 0.025d;
    private static final int OPTIMIZER_V5_MAX_ITERATIONS = 70;
    private static final double OPTIMIZER_V5_INITIAL_STEP = 0.018d;
    private static final Duration PORTFOLIO_VIEW_CACHE_TTL = Duration.ofMinutes(10);
    private static final List<FactorSpec> FACTOR_SPECS = List.of(
            new FactorSpec("Valuation", StockBacktestResult::getValuationScore),
            new FactorSpec("Quality", StockBacktestResult::getQualityScore),
            new FactorSpec("Growth", StockBacktestResult::getGrowthScore),
            new FactorSpec("Stability", StockBacktestResult::getStabilityScore),
            new FactorSpec("Earnings", StockBacktestResult::getEarningsScore),
            new FactorSpec("Analyst", StockBacktestResult::getAnalystScore),
            new FactorSpec("News", StockBacktestResult::getNewsScore),
            new FactorSpec("Momentum", StockBacktestResult::getMomentumScore),
            new FactorSpec("Risk", StockBacktestResult::getRiskScore),
            new FactorSpec("Institution", StockBacktestResult::getInstitutionScore));
    private static final List<InteractionSpec> INTERACTION_SPECS = List.of(
            new InteractionSpec("QUALITY_X_MOMENTUM", "Quality", "Momentum"),
            new InteractionSpec("VALUE_X_QUALITY", "Valuation", "Quality"),
            new InteractionSpec("GROWTH_X_MOMENTUM", "Growth", "Momentum"),
            new InteractionSpec("RISK_X_MOMENTUM", "Risk", "Momentum"),
            new InteractionSpec("VALUE_X_RISK", "Valuation", "Risk"));

    private final ObjectProvider<StockBacktestMapper> stockBacktestMapper;
    private final ObjectMapper objectMapper;
    private final double annualRiskFreeRatePct;
    private final Map<String, CachedPortfolioBacktestView> portfolioViewCache = new ConcurrentHashMap<>();

    public StockPortfolioBacktestService(ObjectProvider<StockBacktestMapper> stockBacktestMapper,
            ObjectMapper objectMapper,
            @Value("${quant.portfolio.risk-free-rate-pct:0}") double annualRiskFreeRatePct) {
        this.stockBacktestMapper = stockBacktestMapper;
        this.objectMapper = objectMapper;
        this.annualRiskFreeRatePct = annualRiskFreeRatePct;
    }

    public StockPortfolioBacktestView build(String indexCode) {
        String effectiveIndexCode = normalizeIndexCode(indexCode);
        CachedPortfolioBacktestView cached = portfolioViewCache.get(effectiveIndexCode);
        if (cached != null && cached.isFresh()) {
            return cached.view();
        }
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        StockPortfolioBacktestView materialized = readMaterializedPortfolioView(mapper, effectiveIndexCode);
        if (materialized != null) {
            portfolioViewCache.put(effectiveIndexCode, new CachedPortfolioBacktestView(materialized, Instant.now()));
            return materialized;
        }
        StockPortfolioBacktestView view = buildUncached(effectiveIndexCode);
        saveMaterializedPortfolioView(mapper, effectiveIndexCode, view, "ON_DEMAND");
        portfolioViewCache.put(effectiveIndexCode, new CachedPortfolioBacktestView(view, Instant.now()));
        return view;
    }

    public PortfolioViewSnapshotRefreshResult refreshPortfolioViewSnapshot(String indexCode) {
        String effectiveIndexCode = normalizeIndexCode(indexCode);
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return new PortfolioViewSnapshotRefreshResult(effectiveIndexCode, false, "mapper unavailable", 0);
        }
        long start = System.currentTimeMillis();
        StockPortfolioBacktestView view = buildUncached(effectiveIndexCode);
        boolean saved = saveMaterializedPortfolioView(mapper, effectiveIndexCode, view, "MATERIALIZED_BATCH");
        if (saved) {
            portfolioViewCache.put(effectiveIndexCode, new CachedPortfolioBacktestView(view, Instant.now()));
        }
        return new PortfolioViewSnapshotRefreshResult(effectiveIndexCode, saved,
                saved ? "saved" : "serialization failed", System.currentTimeMillis() - start);
    }

    public OptimizerShadowRefreshResult refreshOptimizerShadowSnapshots(String indexCode, int resultLimit,
            int dateLimit) {
        String effectiveIndexCode = normalizeIndexCode(indexCode);
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return new OptimizerShadowRefreshResult(effectiveIndexCode, 0, 0, 0, "mapper unavailable");
        }
        int effectiveLimit = resultLimit <= 0 ? VIEW_RESULT_LIMIT : resultLimit;
        List<StockBacktestResult> results = mapper.findResults(effectiveIndexCode, effectiveLimit)
                .stream()
                .filter(StockPortfolioBacktestService::usable)
                .toList();
        if (results.isEmpty()) {
            return new OptimizerShadowRefreshResult(effectiveIndexCode, 0, 0, 0, "no backtest rows");
        }

        LocalDate minSignalDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate maxSignalDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        RiskLookup riskLookup = riskLookup(mapper, effectiveIndexCode, minSignalDate, maxSignalDate);
        CovarianceLookup covarianceLookup = covarianceLookup(mapper, effectiveIndexCode, minSignalDate, maxSignalDate);
        ExpectedReturnLookup expectedReturnLookup = expectedReturnLookup(mapper, effectiveIndexCode, minSignalDate,
                maxSignalDate);
        RiskFreeRateLookup riskFreeRateLookup = riskFreeRateLookup(mapper, effectiveIndexCode, minSignalDate,
                maxSignalDate, annualRiskFreeRatePct);

        StrategyConfig config = new StrategyConfig(PRIMARY_HORIZON_DAYS, 20, Weighting.SIGNAL,
                RiskMode.OPTIMIZED_V5);
        StrategyRun run = runStrategy(config, results, riskLookup, covarianceLookup, expectedReturnLookup,
                riskFreeRateLookup);
        List<PortfolioPeriod> periods = limitedShadowPeriods(run.periods(), dateLimit);
        int saved = 0;
        int usable = 0;
        Map<String, Double> previousWeights = Map.of();
        for (PortfolioPeriod period : run.periods()) {
            if (!periods.contains(period)) {
                previousWeights = period.weights();
                continue;
            }
            for (String candidateOptimizer : OJALGO_SHADOW_OPTIMIZERS) {
                StockOptimizerShadowSnapshot snapshot = optimizerShadowSnapshot(effectiveIndexCode, config, period,
                        previousWeights, covarianceLookup, candidateOptimizer);
                mapper.upsertOptimizerShadowSnapshot(snapshot);
                saved++;
                if (Boolean.TRUE.equals(snapshot.getUsable())) {
                    usable++;
                }
            }
            previousWeights = period.weights();
        }
        return new OptimizerShadowRefreshResult(effectiveIndexCode, periods.size() * OJALGO_SHADOW_OPTIMIZERS.size(),
                saved, usable,
                "saved " + saved + " ojAlgo shadow rows");
    }

    private StockPortfolioBacktestView buildUncached(String indexCode) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return empty(indexCode);
        }

        List<StockBacktestResult> results = mapper.findResults(indexCode, VIEW_RESULT_LIMIT)
                .stream()
                .filter(StockPortfolioBacktestService::usable)
                .toList();
        if (results.isEmpty()) {
            return empty(indexCode);
        }

        LocalDate minSignalDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate maxSignalDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        RiskLookup riskLookup = riskLookup(mapper, indexCode, minSignalDate, maxSignalDate);
        CovarianceLookup covarianceLookup = covarianceLookup(mapper, indexCode, minSignalDate, maxSignalDate);
        ExpectedReturnLookup expectedReturnLookup = expectedReturnLookup(mapper, indexCode, minSignalDate, maxSignalDate);
        RiskFreeRateLookup riskFreeRateLookup = riskFreeRateLookup(mapper, indexCode, minSignalDate, maxSignalDate,
                annualRiskFreeRatePct);
        ExpectedCalibrationLookup expectedCalibrationLookup =
                ExpectedCalibrationLookup.from(mapper.findExpectedReturnCalibrations(indexCode,
                        EXPECTED_RETURN_MODEL_VERSION));
        List<StockOptimizerShadowSnapshot> optimizerShadowSnapshots = OJALGO_SHADOW_OPTIMIZERS.stream()
                .flatMap(candidate -> mapper.findOptimizerShadowSnapshots(indexCode, candidate, 2_000).stream())
                .toList();

        List<StrategyRun> runs = strategyConfigs().stream()
                .map(config -> runStrategy(config, results, riskLookup, covarianceLookup, expectedReturnLookup,
                        riskFreeRateLookup))
                .filter(run -> !run.periods().isEmpty())
                .toList();
        if (runs.isEmpty()) {
            return empty(indexCode);
        }

        List<StrategySummary> summaries = runs.stream()
                .map(run -> summarize(run, annualRiskFreeRatePct))
                .sorted(Comparator
                        .comparingDouble(StrategySummary::sharpeSort).reversed()
                        .thenComparing(StrategySummary::excessReturnSort, Comparator.reverseOrder()))
                .toList();
        StrategyRun selectedRun = selectedRun(runs, summaries);
        LivePortfolioRecommendation liveRecommendation = liveRecommendation(mapper, indexCode, covarianceLookup);

        return new StockPortfolioBacktestView(
                indexCode,
                "S&P 500 market-cap weighted proxy",
                "Dynamic transaction cost: base " + percentUnsigned(BASE_TRADE_COST_PCT)
                        + " plus liquidity, volatility, and beta impact. Sharpe rf="
                        + percentUnsigned(annualRiskFreeRatePct),
                cards(summaries, results, annualRiskFreeRatePct),
                strategyRows(summaries),
                riskModelRows(summaries, riskLookup, covarianceLookup, expectedReturnLookup,
                        expectedCalibrationLookup, riskFreeRateLookup),
                riskImpactRows(summaries),
                modelHealthAlerts(mapper, indexCode),
                optimizerValidationRows(summaries, selectedRun, covarianceLookup, optimizerShadowSnapshots,
                        annualRiskFreeRatePct),
                optimizerShadowPathRows(optimizerShadowSnapshots, annualRiskFreeRatePct),
                liveRecommendation.cards(),
                liveRecommendation.positions(),
                recentPeriods(selectedRun),
                sectorExposures(selectedRun),
                latestPositions(selectedRun),
                notes(results));
    }

    private static String normalizeIndexCode(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            return "SP500";
        }
        return indexCode.trim().toUpperCase(Locale.ROOT);
    }

    private void clearPortfolioViewCache() {
        portfolioViewCache.clear();
    }

    private StockPortfolioBacktestView readMaterializedPortfolioView(StockBacktestMapper mapper, String indexCode) {
        if (mapper == null) {
            return null;
        }
        try {
            StockPortfolioViewSnapshot snapshot =
                    mapper.findLatestPortfolioViewSnapshot(indexCode, PORTFOLIO_VIEW_VERSION);
            if (snapshot == null || snapshot.getPayloadJson() == null || snapshot.getPayloadJson().isBlank()) {
                return null;
            }
            return objectMapper.readValue(snapshot.getPayloadJson(), StockPortfolioBacktestView.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean saveMaterializedPortfolioView(StockBacktestMapper mapper, String indexCode,
            StockPortfolioBacktestView view, String source) {
        if (mapper == null || view == null) {
            return false;
        }
        try {
            StockPortfolioViewSnapshot snapshot = new StockPortfolioViewSnapshot();
            snapshot.setIndexCode(indexCode);
            snapshot.setViewVersion(PORTFOLIO_VIEW_VERSION);
            snapshot.setGeneratedAt(LocalDateTime.now());
            snapshot.setSource(source);
            snapshot.setPayloadJson(objectMapper.writeValueAsString(view));
            mapper.upsertPortfolioViewSnapshot(snapshot);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static List<StrategyConfig> strategyConfigs() {
        List<StrategyConfig> configs = new ArrayList<>();
        for (int horizon : HORIZONS) {
            for (int topCount : TOP_COUNTS) {
                for (Weighting weighting : Weighting.values()) {
                    for (RiskMode riskMode : RiskMode.values()) {
                        if (riskMode.explicitOptimizer() && weighting != Weighting.SIGNAL) {
                            continue;
                        }
                        if (weighting != Weighting.SIGNAL
                                && (riskMode != RiskMode.BASE || topCount != 20 || horizon != PRIMARY_HORIZON_DAYS)) {
                            continue;
                        }
                        if (weighting == Weighting.SIGNAL && !riskMode.defaultComparison()) {
                            continue;
                        }
                        if (riskMode.quadraticOptimizer()
                                && (topCount != 20 || horizon != PRIMARY_HORIZON_DAYS)) {
                            continue;
                        }
                        configs.add(new StrategyConfig(horizon, topCount, weighting, riskMode));
                    }
                }
            }
        }
        return configs;
    }

    private static RiskLookup riskLookup(StockBacktestMapper mapper, String indexCode,
            LocalDate minSignalDate, LocalDate maxSignalDate) {
        if (minSignalDate == null || maxSignalDate == null) {
            return RiskLookup.empty();
        }
        List<StockCandleDaily> candles = mapper.findRiskCandles(indexCode,
                minSignalDate.minusDays(RISK_LOOKBACK_DAYS), maxSignalDate);
        List<StockRiskSnapshot> snapshots = mapper.findRiskSnapshots(indexCode, minSignalDate, maxSignalDate);
        List<StockBenchmarkReturn> benchmarkReturns = mapper.findBenchmarkReturns(indexCode,
                minSignalDate.minusDays(RISK_LOOKBACK_DAYS), maxSignalDate);
        return RiskLookup.from(candles, snapshots, benchmarkReturns);
    }

    private static CovarianceLookup covarianceLookup(StockBacktestMapper mapper, String indexCode,
            LocalDate minSignalDate, LocalDate maxSignalDate) {
        if (minSignalDate == null || maxSignalDate == null) {
            return CovarianceLookup.empty();
        }
        return CovarianceLookup.from(mapper.findCovarianceSnapshots(indexCode, minSignalDate, maxSignalDate));
    }

    private static ExpectedReturnLookup expectedReturnLookup(StockBacktestMapper mapper, String indexCode,
            LocalDate minSignalDate, LocalDate maxSignalDate) {
        if (minSignalDate == null || maxSignalDate == null) {
            return ExpectedReturnLookup.empty();
        }
        return ExpectedReturnLookup.from(mapper.findExpectedReturnSnapshots(indexCode, minSignalDate, maxSignalDate));
    }

    private static RiskFreeRateLookup riskFreeRateLookup(StockBacktestMapper mapper, String indexCode,
            LocalDate minSignalDate, LocalDate maxSignalDate, double fallbackAnnualRatePct) {
        if (minSignalDate == null || maxSignalDate == null) {
            return RiskFreeRateLookup.empty(fallbackAnnualRatePct);
        }
        return RiskFreeRateLookup.from(mapper.findRiskFreeRateSnapshots(indexCode, minSignalDate.minusDays(10),
                maxSignalDate.plusDays(1)), fallbackAnnualRatePct);
    }

    private static List<PortfolioPeriod> limitedShadowPeriods(List<PortfolioPeriod> periods, int dateLimit) {
        if (periods == null || periods.isEmpty()) {
            return List.of();
        }
        if (dateLimit <= 0 || dateLimit >= periods.size()) {
            return periods;
        }
        return periods.subList(periods.size() - dateLimit, periods.size());
    }

    private static StockOptimizerShadowSnapshot optimizerShadowSnapshot(String indexCode, StrategyConfig config,
            PortfolioPeriod period, Map<String, Double> previousWeights, CovarianceLookup covarianceLookup,
            String candidateOptimizer) {
        Map<String, Double> alphaScores = optimizerAlphaScores(period.positions(), period.riskStatsBySymbol(),
                period.expectedStatsBySymbol(), previousWeights);
        OjAlgoShadowResult shadow = OJALGO_SHADOW_OPTIMIZER_V2.equals(candidateOptimizer)
                ? ojAlgoShadowWeightsV2(period.positions(), alphaScores, period.riskStatsBySymbol(),
                        previousWeights, config, covarianceLookup, period.signalDate(), period.benchmarkSectorWeights())
                : ojAlgoShadowWeights(period.positions(), alphaScores, period.riskStatsBySymbol(),
                        previousWeights, config, covarianceLookup, period.signalDate(), period.benchmarkSectorWeights());

        StockOptimizerShadowSnapshot snapshot = baseOptimizerShadowSnapshot(indexCode, config, period, shadow,
                candidateOptimizer);
        if (!shadow.usable()) {
            snapshot.setUsable(false);
            snapshot.setSolverStatus("UNAVAILABLE");
            snapshot.setMessage(shadow.message());
            snapshot.setBaselineNetReturnPct(decimalOrNull(period.netReturnPct()));
            snapshot.setBenchmarkReturnPct(decimalOrNull(period.benchmarkReturnPct()));
            snapshot.setConstraintBreachCount(1);
            snapshot.setSource(candidateOptimizer);
            return snapshot;
        }

        Map<String, Double> candidateWeights = shadow.weights();
        double baselineObjective = optimizerV5PortfolioObjective(period.positions(), period.weights(), alphaScores,
                period.riskStatsBySymbol(), previousWeights, config, covarianceLookup, period.signalDate(),
                period.benchmarkSectorWeights());
        double candidateObjective = optimizerV5PortfolioObjective(period.positions(), candidateWeights, alphaScores,
                period.riskStatsBySymbol(), previousWeights, config, covarianceLookup, period.signalDate(),
                period.benchmarkSectorWeights());
        double objectiveGap = candidateObjective - baselineObjective;
        double weightDistancePct = oneWayTurnover(period.weights(), candidateWeights) * 100.0d;
        double candidateTurnoverPct = oneWayTurnover(previousWeights, candidateWeights) * 100.0d;
        double candidateTransactionCostPct = transactionCostPct(previousWeights, candidateWeights,
                period.riskStatsBySymbol());
        double candidateGrossReturnPct = weightedReturn(period.positions(), candidateWeights)
                + cashWeight(candidateWeights) * period.riskFreePeriodReturnPct();
        double candidateNetReturnPct = candidateGrossReturnPct - candidateTransactionCostPct;
        double candidateBeta = weightedRiskMetric(candidateWeights, period.riskStatsBySymbol(), RiskField.BETA);
        double candidateVolatilityPct = portfolioCovarianceVolatilityPct(candidateWeights, period.riskStatsBySymbol(),
                covarianceLookup, period.signalDate());
        if (!Double.isFinite(candidateVolatilityPct)) {
            candidateVolatilityPct = weightedRiskMetric(candidateWeights, period.riskStatsBySymbol(),
                    RiskField.VOLATILITY);
        }
        double candidateLiquidity = weightedRiskMetric(candidateWeights, period.riskStatsBySymbol(),
                RiskField.LIQUIDITY);
        Map<String, Double> candidateSectorWeights = sectorWeights(period.positions(), candidateWeights);
        double candidateMaxSectorPct = candidateSectorWeights.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0d) * 100.0d;
        double candidateMaxPositionPct = candidateWeights.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0d) * 100.0d;
        double candidateActiveSectorDeviationPct = activeSectorDeviation(candidateSectorWeights,
                period.benchmarkSectorWeights()) * 100.0d;
        double investedWeightPct = investedWeight(candidateWeights) * 100.0d;
        double positionCapPct = (config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT)
                * 100.0d;
        double turnoverBudgetPct = optimizerTurnoverBudget(config) * 100.0d;

        boolean betaBreach = !Double.isFinite(candidateBeta)
                || Math.abs(candidateBeta - OPTIMIZER_TARGET_BETA) > 0.30d;
        boolean volatilityBreach = !Double.isFinite(candidateVolatilityPct)
                || candidateVolatilityPct > OPTIMIZER_TARGET_VOLATILITY_PCT + 6.0d;
        boolean sectorBreach = candidateMaxSectorPct > RISK_SECTOR_CAP * 100.0d + 6.0d;
        boolean positionBreach = candidateMaxPositionPct > positionCapPct + 2.5d;
        boolean initialPortfolio = previousWeights == null || previousWeights.isEmpty();
        boolean turnoverBreach = !initialPortfolio && candidateTurnoverPct > turnoverBudgetPct + 12.0d;
        boolean objectiveBreach = !Double.isFinite(objectiveGap) || objectiveGap > 0.10d;
        boolean weightDistanceBreach = weightDistancePct > 30.0d;
        int constraintBreaches = boolCount(betaBreach, volatilityBreach, sectorBreach, positionBreach,
                turnoverBreach);

        snapshot.setUsable(true);
        snapshot.setSolverStatus("FEASIBLE");
        snapshot.setMessage(shadow.message());
        snapshot.setBaselineObjective(decimalOrNull(baselineObjective));
        snapshot.setCandidateObjective(decimalOrNull(candidateObjective));
        snapshot.setObjectiveGap(decimalOrNull(objectiveGap));
        snapshot.setWeightDistancePct(decimalOrNull(weightDistancePct));
        snapshot.setBaselineNetReturnPct(decimalOrNull(period.netReturnPct()));
        snapshot.setCandidateNetReturnPct(decimalOrNull(candidateNetReturnPct));
        snapshot.setBenchmarkReturnPct(decimalOrNull(period.benchmarkReturnPct()));
        snapshot.setCandidateTurnoverPct(decimalOrNull(candidateTurnoverPct));
        snapshot.setCandidateTransactionCostPct(decimalOrNull(candidateTransactionCostPct));
        snapshot.setCandidateBeta(decimalOrNull(candidateBeta));
        snapshot.setCandidateVolatilityPct(decimalOrNull(candidateVolatilityPct));
        snapshot.setCandidateLiquidity(decimalOrNull(candidateLiquidity));
        snapshot.setCandidateMaxSectorWeightPct(decimalOrNull(candidateMaxSectorPct));
        snapshot.setCandidateMaxPositionWeightPct(decimalOrNull(candidateMaxPositionPct));
        snapshot.setCandidateActiveSectorDeviationPct(decimalOrNull(candidateActiveSectorDeviationPct));
        snapshot.setCandidateInvestedWeightPct(decimalOrNull(investedWeightPct));
        snapshot.setBetaBreach(betaBreach);
        snapshot.setVolatilityBreach(volatilityBreach);
        snapshot.setSectorBreach(sectorBreach);
        snapshot.setPositionBreach(positionBreach);
        snapshot.setTurnoverBreach(turnoverBreach);
        snapshot.setObjectiveBreach(objectiveBreach);
        snapshot.setWeightDistanceBreach(weightDistanceBreach);
        snapshot.setConstraintBreachCount(constraintBreaches);
        snapshot.setSource(candidateOptimizer);
        return snapshot;
    }

    private static StockOptimizerShadowSnapshot baseOptimizerShadowSnapshot(String indexCode, StrategyConfig config,
            PortfolioPeriod period, OjAlgoShadowResult shadow, String candidateOptimizer) {
        StockOptimizerShadowSnapshot snapshot = new StockOptimizerShadowSnapshot();
        snapshot.setIndexCode(normalizeIndexCode(indexCode));
        snapshot.setSignalDate(period.signalDate());
        snapshot.setHorizonDays(config.horizonDays());
        snapshot.setTopCount(config.topCount());
        snapshot.setWeighting(config.weighting().name());
        snapshot.setBaselineOptimizer(config.riskMode().name());
        snapshot.setCandidateOptimizer(candidateOptimizer);
        snapshot.setSolverStatus(shadow.usable() ? "FEASIBLE" : "UNAVAILABLE");
        snapshot.setUsable(shadow.usable());
        snapshot.setMessage(shadow.message());
        snapshot.setBetaBreach(false);
        snapshot.setVolatilityBreach(false);
        snapshot.setSectorBreach(false);
        snapshot.setPositionBreach(false);
        snapshot.setTurnoverBreach(false);
        snapshot.setObjectiveBreach(false);
        snapshot.setWeightDistanceBreach(false);
        snapshot.setConstraintBreachCount(0);
        snapshot.setSource(candidateOptimizer);
        return snapshot;
    }

    private static int boolCount(boolean... values) {
        int count = 0;
        for (boolean value : values) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    public int refreshRiskSnapshots(String indexCode, int limit) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return 0;
        }
        List<StockBacktestResult> results = mapper.findResults(indexCode, limit)
                .stream()
                .filter(StockPortfolioBacktestService::usable)
                .toList();
        if (results.isEmpty()) {
            return 0;
        }
        LocalDate minSignalDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate maxSignalDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (minSignalDate == null || maxSignalDate == null) {
            return 0;
        }
        RiskLookup lookup = RiskLookup.from(
                mapper.findRiskCandles(indexCode, minSignalDate.minusDays(RISK_LOOKBACK_DAYS), maxSignalDate),
                List.of(),
                mapper.findBenchmarkReturns(indexCode, minSignalDate.minusDays(RISK_LOOKBACK_DAYS), maxSignalDate));
        Set<RiskKey> keys = results.stream()
                .map(result -> new RiskKey(result.getSymbol(), result.getSignalDate()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int saved = 0;
        for (RiskKey key : keys) {
            RiskStats stats = lookup.stats(key.symbol(), key.signalDate());
            if (!stats.hasUsableValue()) {
                continue;
            }
            mapper.upsertRiskSnapshot(toRiskSnapshot(key, stats));
            saved++;
        }
        return saved;
    }

    public int refreshLatestRiskSnapshots(String indexCode, int limit) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return 0;
        }
        String effectiveIndexCode = normalizeIndexCode(indexCode);
        LocalDate targetDate = LocalDate.now();
        List<StockBacktestResult> latestRows = mapper.findLatestSignalRows(effectiveIndexCode).stream()
                .filter(row -> row.getSymbol() != null && row.getIntegratedScore() != null)
                .limit(Math.max(1, limit))
                .toList();
        if (latestRows.isEmpty()) {
            return 0;
        }
        RiskLookup lookup = RiskLookup.from(
                mapper.findRiskCandles(effectiveIndexCode, targetDate.minusDays(RISK_LOOKBACK_DAYS), targetDate),
                List.of(),
                mapper.findBenchmarkReturns(effectiveIndexCode, targetDate.minusDays(RISK_LOOKBACK_DAYS), targetDate));
        int saved = 0;
        for (StockBacktestResult row : latestRows) {
            RiskKey key = new RiskKey(row.getSymbol(), targetDate);
            RiskStats stats = lookup.stats(key.symbol(), key.signalDate());
            if (!stats.hasUsableValue()) {
                continue;
            }
            mapper.upsertRiskSnapshot(toRiskSnapshot(key, stats));
            saved++;
        }
        return saved;
    }

    public MarketSnapshotRefreshResult refreshMarketSnapshotsAndBenchmarks(String indexCode, LocalDate fromDate,
            LocalDate toDate) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            return new MarketSnapshotRefreshResult(0, 0, 0, 0, 0, 0, 0);
        }
        int membershipRows = mapper.refreshIndexMembershipSnapshots(indexCode, fromDate, toDate);
        int removedMembershipRows = mapper.deactivateStaleCurrentMembershipSnapshots(indexCode, fromDate, toDate);
        mapper.deleteStaleMarketSnapshots(indexCode, fromDate, toDate);
        int secSharesRows = mapper.refreshSecSharesOutstandingSnapshots(indexCode, fromDate, toDate);
        int marketRows = mapper.refreshMarketSnapshots(indexCode, fromDate, toDate);
        int proxySharesRows = mapper.refreshSharesOutstandingSnapshots(indexCode, fromDate, toDate);
        List<StockMarketSnapshot> snapshots = mapper.findMarketSnapshots(indexCode, fromDate, toDate);
        List<StockBenchmarkReturn> benchmarkRows = buildBenchmarkReturns(indexCode, snapshots);
        int savedBenchmarkRows = 0;
        for (StockBenchmarkReturn row : benchmarkRows) {
            mapper.upsertBenchmarkReturn(row);
            savedBenchmarkRows++;
        }
        long tossShareRows = snapshots.stream()
                .filter(row -> row.getSharesSource() != null && row.getSharesSource().startsWith("TOSS_CURRENT"))
                .count();
        long fallbackRows = snapshots.stream()
                .filter(row -> row.getMarketCapSource() == null
                        || "FINNHUB_CURRENT_MARKET_CAP".equals(row.getMarketCapSource()))
                .count();
        return new MarketSnapshotRefreshResult(marketRows, snapshots.size(), savedBenchmarkRows,
                (int) tossShareRows, (int) fallbackRows, membershipRows + removedMembershipRows,
                secSharesRows + proxySharesRows);
    }

    public RiskFreeRateRefreshResult refreshRiskFreeRateSnapshots(String indexCode,
            List<StockRiskFreeRateSnapshot> snapshots) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || snapshots == null || snapshots.isEmpty()) {
            return new RiskFreeRateRefreshResult(0, 0, null, null);
        }
        String effectiveIndexCode = normalizeIndexCode(indexCode);
        int saved = 0;
        LocalDate minDate = null;
        LocalDate maxDate = null;
        for (StockRiskFreeRateSnapshot snapshot : snapshots) {
            if (snapshot.getRateDate() == null || snapshot.getAnnualRatePct() == null) {
                continue;
            }
            snapshot.setIndexCode(effectiveIndexCode);
            if (snapshot.getSeriesCode() == null || snapshot.getSeriesCode().isBlank()) {
                snapshot.setSeriesCode(DEFAULT_RISK_FREE_SERIES_CODE);
            }
            if (snapshot.getSource() == null || snapshot.getSource().isBlank()) {
                snapshot.setSource("UNKNOWN");
            }
            mapper.upsertRiskFreeRateSnapshot(snapshot);
            minDate = minDate == null || snapshot.getRateDate().isBefore(minDate) ? snapshot.getRateDate() : minDate;
            maxDate = maxDate == null || snapshot.getRateDate().isAfter(maxDate) ? snapshot.getRateDate() : maxDate;
            saved++;
        }
        return new RiskFreeRateRefreshResult(snapshots.size(), saved, minDate, maxDate);
    }

    public RiskFreeRateRefreshResult refreshFixedRiskFreeRateSnapshots(String indexCode, String seriesCode,
            LocalDate fromDate, LocalDate toDate, double annualRatePct, String source) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || fromDate == null || toDate == null || fromDate.isAfter(toDate)
                || !Double.isFinite(annualRatePct)) {
            return new RiskFreeRateRefreshResult(0, 0, null, null);
        }
        List<StockRiskFreeRateSnapshot> snapshots = mapper.findBenchmarkReturns(normalizeIndexCode(indexCode),
                fromDate, toDate).stream()
                .map(row -> riskFreeRateSnapshot(indexCode, seriesCode, row.getTradeDate(), annualRatePct, source))
                .toList();
        return refreshRiskFreeRateSnapshots(indexCode, snapshots);
    }

    public CovarianceRefreshResult refreshCovarianceSnapshots(String indexCode, int dateLimit, int candidateLimit,
            int lookbackDays) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new CovarianceRefreshResult(0, 0, 0, 0);
        }
        int effectiveDateLimit = Math.max(1, dateLimit);
        int effectiveCandidateLimit = Math.max(2, candidateLimit);
        int effectiveLookbackDays = Math.max(MIN_COVARIANCE_OBSERVATIONS, lookbackDays);

        int savedPairs = 0;
        int processedDates = 0;
        int candidateRows = 0;
        for (LocalDate signalDate : mapper.findCovarianceSnapshotDates(indexCode, effectiveDateLimit)) {
            List<String> symbols = mapper.findCovarianceCandidateSymbols(indexCode, signalDate, effectiveCandidateLimit);
            if (symbols.size() < 2) {
                continue;
            }
            candidateRows += symbols.size();
            List<StockCandleDaily> candles = mapper.findCandlesForSymbols(symbols,
                    signalDate.minusDays(effectiveLookbackDays * 3L), signalDate);
            List<StockCovarianceSnapshot> snapshots = covarianceSnapshots(indexCode, signalDate, symbols, candles,
                    effectiveLookbackDays);
            for (StockCovarianceSnapshot snapshot : snapshots) {
                mapper.upsertCorrelationSnapshot(snapshot);
                mapper.upsertCovarianceSnapshot(snapshot);
                savedPairs++;
            }
            processedDates++;
        }
        return new CovarianceRefreshResult(processedDates, candidateRows, savedPairs, effectiveLookbackDays);
    }

    public CovarianceRefreshResult refreshLatestCovarianceSnapshot(String indexCode, int candidateLimit,
            int lookbackDays) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new CovarianceRefreshResult(0, 0, 0, 0);
        }
        String effectiveIndexCode = normalizeIndexCode(indexCode);
        LocalDate targetDate = LocalDate.now();
        int effectiveCandidateLimit = Math.max(2, candidateLimit);
        int effectiveLookbackDays = Math.max(MIN_COVARIANCE_OBSERVATIONS, lookbackDays);
        List<String> symbols = mapper.findLatestSignalRows(effectiveIndexCode).stream()
                .filter(row -> row.getSymbol() != null && row.getIntegratedScore() != null)
                .sorted(Comparator
                        .comparing((StockBacktestResult row) -> row.getIntegratedScore(), Comparator.reverseOrder())
                        .thenComparing((StockBacktestResult row) -> number(row.getMarketCap()),
                                Comparator.reverseOrder())
                        .thenComparing(StockBacktestResult::getSymbol))
                .map(StockBacktestResult::getSymbol)
                .distinct()
                .limit(effectiveCandidateLimit)
                .toList();
        if (symbols.size() < 2) {
            return new CovarianceRefreshResult(0, symbols.size(), 0, effectiveLookbackDays);
        }
        List<StockCandleDaily> candles = mapper.findCandlesForSymbols(symbols,
                targetDate.minusDays(effectiveLookbackDays * 3L), targetDate);
        List<StockCovarianceSnapshot> snapshots = covarianceSnapshots(effectiveIndexCode, targetDate, symbols, candles,
                effectiveLookbackDays);
        int savedPairs = 0;
        for (StockCovarianceSnapshot snapshot : snapshots) {
            mapper.upsertCorrelationSnapshot(snapshot);
            mapper.upsertCovarianceSnapshot(snapshot);
            savedPairs++;
        }
        return new CovarianceRefreshResult(savedPairs > 0 ? 1 : 0, symbols.size(), savedPairs, effectiveLookbackDays);
    }

    public ExpectedReturnRefreshResult refreshExpectedReturnSnapshots(String indexCode, int resultLimit, int dateLimit,
            int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> results = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        if (results.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }

        List<LocalDate> targetDates = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(Math.max(1, dateLimit))
                .sorted()
                .toList();
        Set<Integer> horizons = results.stream()
                .map(StockBacktestResult::getHorizonDays)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int savedRows = 0;
        int processedDates = 0;
        int trainedRows = 0;
        List<PredictionEvaluation> evaluations = new ArrayList<>();
        int requiredTrainingRows = Math.max(50, minTrainingRows);
        for (LocalDate targetDate : targetDates) {
            boolean processedAny = false;
            for (Integer horizon : horizons) {
                List<StockBacktestResult> targetRows = results.stream()
                        .filter(row -> Objects.equals(row.getSignalDate(), targetDate))
                        .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                        .toList();
                if (targetRows.isEmpty()) {
                    continue;
                }
                List<StockBacktestResult> trainingRows = results.stream()
                        .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                        .filter(row -> row.getSignalDate().isBefore(targetDate))
                        .toList();
                if (trainingRows.size() < requiredTrainingRows) {
                    continue;
                }
                List<TrainingObservation> observations = trainingObservations(trainingRows);
                if (observations.size() < requiredTrainingRows) {
                    continue;
                }
                trainedRows += observations.size();
                double targetBenchmark = targetRows.stream()
                        .mapToDouble(StockPortfolioBacktestService::returnPct)
                        .average()
                        .orElse(0.0d);
                for (StockBacktestResult row : targetRows) {
                    ExpectedReturnStats stats = expectedReturnStats(row, observations);
                    mapper.upsertExpectedReturnSnapshot(toExpectedReturnSnapshot(indexCode, row, stats,
                            EXPECTED_RETURN_HISTORICAL_SOURCE, EXPECTED_RETURN_V2_MODEL_VERSION));
                    double actualExcess = returnPct(row) - targetBenchmark;
                    evaluations.add(new PredictionEvaluation(horizon,
                            stats.calibratedUpsideProbabilityPct(),
                            actualExcess > 0));
                    savedRows++;
                }
                processedAny = true;
            }
            if (processedAny) {
                processedDates++;
            }
        }
        mapper.deleteExpectedReturnCalibrations(indexCode, EXPECTED_RETURN_V2_MODEL_VERSION);
        int calibrationRows = 0;
        for (StockExpectedReturnCalibration calibration : expectedReturnCalibrations(indexCode,
                EXPECTED_RETURN_V2_MODEL_VERSION, evaluations)) {
            mapper.upsertExpectedReturnCalibration(calibration);
            calibrationRows++;
        }
        return new ExpectedReturnRefreshResult(processedDates, savedRows, trainedRows,
                requiredTrainingRows, calibrationRows);
    }

    public ExpectedReturnRefreshResult refreshLatestExpectedReturnSnapshots(String indexCode, int resultLimit,
            int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> trainingSource = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        List<StockBacktestResult> latestRows = mapper.findLatestSignalRows(indexCode).stream()
                .filter(row -> row.getSymbol() != null && row.getIntegratedScore() != null)
                .toList();
        if (trainingSource.isEmpty() || latestRows.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, Math.max(50, minTrainingRows), 0);
        }
        LocalDate targetDate = LocalDate.now();
        Set<Integer> horizons = trainingSource.stream()
                .map(StockBacktestResult::getHorizonDays)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int savedRows = 0;
        int trainedRows = 0;
        int requiredTrainingRows = Math.max(50, minTrainingRows);
        for (Integer horizon : horizons) {
            List<StockBacktestResult> trainingRows = trainingSource.stream()
                    .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                    .filter(row -> row.getSignalDate() != null && row.getSignalDate().isBefore(targetDate))
                    .toList();
            if (trainingRows.size() < requiredTrainingRows) {
                continue;
            }
            List<TrainingObservation> observations = trainingObservations(trainingRows);
            if (observations.size() < requiredTrainingRows) {
                continue;
            }
            trainedRows += observations.size();
            for (StockBacktestResult row : latestRows) {
                StockBacktestResult targetRow = latestExpectedReturnTarget(row, targetDate, horizon);
                ExpectedReturnStats stats = expectedReturnStats(targetRow, observations);
                mapper.upsertExpectedReturnSnapshot(toExpectedReturnSnapshot(indexCode, targetRow, stats,
                        EXPECTED_RETURN_LIVE_SOURCE, EXPECTED_RETURN_V2_MODEL_VERSION));
                savedRows++;
            }
        }
        return new ExpectedReturnRefreshResult(savedRows > 0 ? 1 : 0, savedRows, trainedRows,
                requiredTrainingRows, 0);
    }

    public ExpectedReturnRefreshResult refreshExpectedReturnV3Snapshots(String indexCode, int resultLimit,
            int dateLimit, int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> results = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        if (results.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }

        List<LocalDate> targetDates = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(Math.max(1, dateLimit))
                .sorted()
                .toList();
        Set<Integer> horizons = results.stream()
                .map(StockBacktestResult::getHorizonDays)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int savedRows = 0;
        int processedDates = 0;
        int trainedRows = 0;
        List<PredictionEvaluation> evaluations = new ArrayList<>();
        int requiredTrainingRows = Math.max(50, minTrainingRows);
        for (LocalDate targetDate : targetDates) {
            boolean processedAny = false;
            mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V3_MODEL_VERSION);
            results.stream()
                    .filter(row -> Objects.equals(row.getSignalDate(), targetDate))
                    .collect(Collectors.toMap(StockBacktestResult::getSymbol,
                            Function.identity(), (left, right) -> left, LinkedHashMap::new))
                    .values()
                    .forEach(row -> saveFactorExposureSnapshots(mapper, indexCode, row));
            for (Integer horizon : horizons) {
                List<StockBacktestResult> targetRows = results.stream()
                        .filter(row -> Objects.equals(row.getSignalDate(), targetDate))
                        .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                        .toList();
                if (targetRows.isEmpty()) {
                    continue;
                }
                List<StockBacktestResult> trainingRows = results.stream()
                        .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                        .filter(row -> row.getSignalDate().isBefore(targetDate))
                        .toList();
                if (trainingRows.size() < requiredTrainingRows) {
                    continue;
                }
                List<TrainingObservation> observations = trainingObservations(trainingRows);
                if (observations.size() < requiredTrainingRows) {
                    continue;
                }
                trainedRows += observations.size();
                Map<String, Map<String, FactorCoefficient>> coefficientsBySector =
                        coefficientsBySector(observations, targetRows);
                double targetBenchmark = targetRows.stream()
                        .mapToDouble(StockPortfolioBacktestService::returnPct)
                        .average()
                        .orElse(0.0d);
                for (StockBacktestResult row : targetRows) {
                    ExpectedReturnStats baseStats = expectedReturnStats(row, observations);
                    FactorAdjustedPrediction prediction = factorAdjustedPrediction(row, baseStats,
                            coefficientsBySector.getOrDefault(fallback(row.getSector()), Map.of()));
                    mapper.upsertExpectedReturnSnapshot(toExpectedReturnSnapshot(indexCode, row, prediction.stats(),
                            EXPECTED_RETURN_V3_HISTORICAL_SOURCE, EXPECTED_RETURN_V3_MODEL_VERSION));
                    saveExpectedReturnFactorContributions(mapper, indexCode, row, prediction.contributions(),
                            EXPECTED_RETURN_V3_HISTORICAL_SOURCE, EXPECTED_RETURN_V3_MODEL_VERSION);
                    double actualExcess = returnPct(row) - targetBenchmark;
                    evaluations.add(new PredictionEvaluation(horizon,
                            prediction.stats().calibratedUpsideProbabilityPct(),
                            actualExcess > 0));
                    savedRows++;
                }
                processedAny = true;
            }
            if (processedAny) {
                processedDates++;
            }
        }
        mapper.deleteExpectedReturnCalibrations(indexCode, EXPECTED_RETURN_V3_MODEL_VERSION);
        int calibrationRows = 0;
        for (StockExpectedReturnCalibration calibration : expectedReturnCalibrations(indexCode,
                EXPECTED_RETURN_V3_MODEL_VERSION, evaluations)) {
            mapper.upsertExpectedReturnCalibration(calibration);
            calibrationRows++;
        }
        return new ExpectedReturnRefreshResult(processedDates, savedRows, trainedRows,
                requiredTrainingRows, calibrationRows);
    }

    public ExpectedReturnRefreshResult refreshLatestExpectedReturnV3Snapshots(String indexCode, int resultLimit,
            int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> trainingSource = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        List<StockBacktestResult> latestRows = mapper.findLatestSignalRows(indexCode).stream()
                .filter(row -> row.getSymbol() != null && row.getIntegratedScore() != null)
                .toList();
        int requiredTrainingRows = Math.max(50, minTrainingRows);
        if (trainingSource.isEmpty() || latestRows.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, requiredTrainingRows, 0);
        }
        LocalDate targetDate = LocalDate.now();
        Set<Integer> horizons = trainingSource.stream()
                .map(StockBacktestResult::getHorizonDays)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V3_MODEL_VERSION);
        latestRows.forEach(row -> saveFactorExposureSnapshots(mapper, indexCode, row));

        int savedRows = 0;
        int trainedRows = 0;
        for (Integer horizon : horizons) {
            List<StockBacktestResult> trainingRows = trainingSource.stream()
                    .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                    .filter(row -> row.getSignalDate() != null && row.getSignalDate().isBefore(targetDate))
                    .toList();
            if (trainingRows.size() < requiredTrainingRows) {
                continue;
            }
            List<TrainingObservation> observations = trainingObservations(trainingRows);
            if (observations.size() < requiredTrainingRows) {
                continue;
            }
            trainedRows += observations.size();
            Map<String, Map<String, FactorCoefficient>> coefficientsBySector =
                    coefficientsBySector(observations, latestRows);
            for (StockBacktestResult row : latestRows) {
                StockBacktestResult targetRow = latestExpectedReturnTarget(row, targetDate, horizon);
                ExpectedReturnStats baseStats = expectedReturnStats(targetRow, observations);
                FactorAdjustedPrediction prediction = factorAdjustedPrediction(targetRow, baseStats,
                        coefficientsBySector.getOrDefault(fallback(targetRow.getSector()), Map.of()));
                mapper.upsertExpectedReturnSnapshot(toExpectedReturnSnapshot(indexCode, targetRow, prediction.stats(),
                        EXPECTED_RETURN_V3_LIVE_SOURCE, EXPECTED_RETURN_V3_MODEL_VERSION));
                saveExpectedReturnFactorContributions(mapper, indexCode, targetRow, prediction.contributions(),
                        EXPECTED_RETURN_V3_LIVE_SOURCE, EXPECTED_RETURN_V3_MODEL_VERSION);
                savedRows++;
            }
        }
        return new ExpectedReturnRefreshResult(savedRows > 0 ? 1 : 0, savedRows, trainedRows,
                requiredTrainingRows, 0);
    }

    public ExpectedReturnRefreshResult refreshExpectedReturnV4Snapshots(String indexCode, int resultLimit,
            int dateLimit, int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> results = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        if (results.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }

        List<LocalDate> targetDates = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(Math.max(1, dateLimit))
                .sorted()
                .toList();
        Set<Integer> horizons = results.stream()
                .map(StockBacktestResult::getHorizonDays)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDate minDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate maxDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        MacroRegimeLookup macroLookup = MacroRegimeLookup.from(minDate == null || maxDate == null
                ? List.of()
                : mapper.findMacroRegimeSnapshots(indexCode, minDate.minusDays(10), maxDate.plusDays(1)));

        int savedRows = 0;
        int processedDates = 0;
        int trainedRows = 0;
        List<PredictionEvaluation> evaluations = new ArrayList<>();
        int requiredTrainingRows = Math.max(50, minTrainingRows);
        for (LocalDate targetDate : targetDates) {
            boolean processedAny = false;
            mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V4_MODEL_VERSION);
            results.stream()
                    .filter(row -> Objects.equals(row.getSignalDate(), targetDate))
                    .collect(Collectors.toMap(StockBacktestResult::getSymbol,
                            Function.identity(), (left, right) -> left, LinkedHashMap::new))
                    .values()
                    .forEach(row -> saveFactorExposureSnapshots(mapper, indexCode, row));
            String targetRegime = macroLookup.regimeLabel(targetDate);
            for (Integer horizon : horizons) {
                List<StockBacktestResult> targetRows = results.stream()
                        .filter(row -> Objects.equals(row.getSignalDate(), targetDate))
                        .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                        .toList();
                if (targetRows.isEmpty()) {
                    continue;
                }
                List<StockBacktestResult> trainingRows = results.stream()
                        .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                        .filter(row -> row.getSignalDate().isBefore(targetDate))
                        .toList();
                if (trainingRows.size() < requiredTrainingRows) {
                    continue;
                }
                List<TrainingObservation> observations = trainingObservations(trainingRows, macroLookup);
                if (observations.size() < requiredTrainingRows) {
                    continue;
                }
                trainedRows += observations.size();
                Map<String, Map<String, FactorCoefficient>> coefficientsBySector =
                        coefficientsBySectorAndRegime(observations, targetRows, targetRegime);
                double targetBenchmark = targetRows.stream()
                        .mapToDouble(StockPortfolioBacktestService::returnPct)
                        .average()
                        .orElse(0.0d);
                for (StockBacktestResult row : targetRows) {
                    ExpectedReturnStats baseStats = expectedReturnStats(row, observations);
                    FactorAdjustedPrediction prediction = factorAdjustedPrediction(row, baseStats,
                            coefficientsBySector.getOrDefault(fallback(row.getSector()), Map.of()));
                    mapper.upsertExpectedReturnSnapshot(toExpectedReturnSnapshot(indexCode, row, prediction.stats(),
                            EXPECTED_RETURN_V4_HISTORICAL_SOURCE, EXPECTED_RETURN_V4_MODEL_VERSION));
                    saveExpectedReturnFactorContributions(mapper, indexCode, row, prediction.contributions(),
                            EXPECTED_RETURN_V4_HISTORICAL_SOURCE, EXPECTED_RETURN_V4_MODEL_VERSION);
                    double actualExcess = returnPct(row) - targetBenchmark;
                    evaluations.add(new PredictionEvaluation(horizon,
                            prediction.stats().calibratedUpsideProbabilityPct(),
                            actualExcess > 0));
                    savedRows++;
                }
                processedAny = true;
            }
            if (processedAny) {
                processedDates++;
            }
        }
        mapper.deleteExpectedReturnCalibrations(indexCode, EXPECTED_RETURN_V4_MODEL_VERSION);
        int calibrationRows = 0;
        for (StockExpectedReturnCalibration calibration : expectedReturnCalibrations(indexCode,
                EXPECTED_RETURN_V4_MODEL_VERSION, evaluations)) {
            mapper.upsertExpectedReturnCalibration(calibration);
            calibrationRows++;
        }
        return new ExpectedReturnRefreshResult(processedDates, savedRows, trainedRows,
                requiredTrainingRows, calibrationRows);
    }

    public ExpectedReturnRefreshResult refreshLatestExpectedReturnV4Snapshots(String indexCode, int resultLimit,
            int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> trainingSource = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        List<StockBacktestResult> latestRows = mapper.findLatestSignalRows(indexCode).stream()
                .filter(row -> row.getSymbol() != null && row.getIntegratedScore() != null)
                .toList();
        int requiredTrainingRows = Math.max(50, minTrainingRows);
        if (trainingSource.isEmpty() || latestRows.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, requiredTrainingRows, 0);
        }
        LocalDate targetDate = LocalDate.now();
        Set<Integer> horizons = trainingSource.stream()
                .map(StockBacktestResult::getHorizonDays)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDate minDate = trainingSource.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(targetDate.minusYears(3));
        MacroRegimeLookup macroLookup = MacroRegimeLookup.from(
                mapper.findMacroRegimeSnapshots(indexCode, minDate.minusDays(10), targetDate));
        String targetRegime = macroLookup.regimeLabel(targetDate);

        mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V4_MODEL_VERSION);
        latestRows.forEach(row -> saveFactorExposureSnapshots(mapper, indexCode, row));

        int savedRows = 0;
        int trainedRows = 0;
        for (Integer horizon : horizons) {
            List<StockBacktestResult> trainingRows = trainingSource.stream()
                    .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                    .filter(row -> row.getSignalDate() != null && row.getSignalDate().isBefore(targetDate))
                    .toList();
            if (trainingRows.size() < requiredTrainingRows) {
                continue;
            }
            List<TrainingObservation> observations = trainingObservations(trainingRows, macroLookup);
            if (observations.size() < requiredTrainingRows) {
                continue;
            }
            trainedRows += observations.size();
            Map<String, Map<String, FactorCoefficient>> coefficientsBySector =
                    coefficientsBySectorAndRegime(observations, latestRows, targetRegime);
            for (StockBacktestResult row : latestRows) {
                StockBacktestResult targetRow = latestExpectedReturnTarget(row, targetDate, horizon);
                ExpectedReturnStats baseStats = expectedReturnStats(targetRow, observations);
                FactorAdjustedPrediction prediction = factorAdjustedPrediction(targetRow, baseStats,
                        coefficientsBySector.getOrDefault(fallback(targetRow.getSector()), Map.of()));
                mapper.upsertExpectedReturnSnapshot(toExpectedReturnSnapshot(indexCode, targetRow, prediction.stats(),
                        EXPECTED_RETURN_V4_LIVE_SOURCE, EXPECTED_RETURN_V4_MODEL_VERSION));
                saveExpectedReturnFactorContributions(mapper, indexCode, targetRow, prediction.contributions(),
                        EXPECTED_RETURN_V4_LIVE_SOURCE, EXPECTED_RETURN_V4_MODEL_VERSION);
                savedRows++;
            }
        }
        return new ExpectedReturnRefreshResult(savedRows > 0 ? 1 : 0, savedRows, trainedRows,
                requiredTrainingRows, 0);
    }

    public ExpectedReturnRefreshResult refreshExpectedReturnV5Snapshots(String indexCode, int resultLimit,
            int dateLimit, int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> results = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        if (results.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }

        List<LocalDate> targetDates = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(Math.max(1, dateLimit))
                .sorted()
                .toList();
        Set<Integer> horizons = results.stream()
                .map(StockBacktestResult::getHorizonDays)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDate minDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate maxDate = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        MacroRegimeLookup macroLookup = MacroRegimeLookup.from(minDate == null || maxDate == null
                ? List.of()
                : mapper.findMacroRegimeSnapshots(indexCode, minDate.minusDays(10), maxDate.plusDays(1)));

        int savedRows = 0;
        int processedDates = 0;
        int trainedRows = 0;
        List<PredictionEvaluation> evaluations = new ArrayList<>();
        int requiredTrainingRows = Math.max(50, minTrainingRows);
        for (LocalDate targetDate : targetDates) {
            boolean processedAny = false;
            mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V5_MODEL_VERSION);
            results.stream()
                    .filter(row -> Objects.equals(row.getSignalDate(), targetDate))
                    .collect(Collectors.toMap(StockBacktestResult::getSymbol,
                            Function.identity(), (left, right) -> left, LinkedHashMap::new))
                    .values()
                    .forEach(row -> saveFactorExposureSnapshots(mapper, indexCode, row));
            String targetRegime = macroLookup.regimeLabel(targetDate);
            for (Integer horizon : horizons) {
                List<StockBacktestResult> targetRows = results.stream()
                        .filter(row -> Objects.equals(row.getSignalDate(), targetDate))
                        .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                        .toList();
                if (targetRows.isEmpty()) {
                    continue;
                }
                List<StockBacktestResult> trainingRows = results.stream()
                        .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                        .filter(row -> row.getSignalDate().isBefore(targetDate))
                        .toList();
                if (trainingRows.size() < requiredTrainingRows) {
                    continue;
                }
                List<TrainingObservation> observations = trainingObservations(trainingRows, macroLookup);
                if (observations.size() < requiredTrainingRows) {
                    continue;
                }
                trainedRows += observations.size();
                Map<String, Map<String, FactorCoefficient>> coefficientsBySector =
                        coefficientsBySectorAndRegime(observations, targetRows, targetRegime);
                Map<String, Map<String, FactorCoefficient>> interactionsBySector =
                        interactionCoefficientsBySectorAndRegime(observations, targetRows, targetRegime);
                double targetBenchmark = targetRows.stream()
                        .mapToDouble(StockPortfolioBacktestService::returnPct)
                        .average()
                        .orElse(0.0d);
                for (StockBacktestResult row : targetRows) {
                    ExpectedReturnStats baseStats = expectedReturnStats(row, observations);
                    FactorAdjustedPrediction prediction = nonlinearFactorAdjustedPrediction(row, baseStats,
                            coefficientsBySector.getOrDefault(fallback(row.getSector()), Map.of()),
                            interactionsBySector.getOrDefault(fallback(row.getSector()), Map.of()));
                    mapper.upsertExpectedReturnSnapshot(toExpectedReturnSnapshot(indexCode, row, prediction.stats(),
                            EXPECTED_RETURN_V5_HISTORICAL_SOURCE, EXPECTED_RETURN_V5_MODEL_VERSION));
                    saveExpectedReturnFactorContributions(mapper, indexCode, row, prediction.contributions(),
                            EXPECTED_RETURN_V5_HISTORICAL_SOURCE, EXPECTED_RETURN_V5_MODEL_VERSION);
                    double actualExcess = returnPct(row) - targetBenchmark;
                    evaluations.add(new PredictionEvaluation(horizon,
                            prediction.stats().calibratedUpsideProbabilityPct(),
                            actualExcess > 0));
                    savedRows++;
                }
                processedAny = true;
            }
            if (processedAny) {
                processedDates++;
            }
        }
        mapper.deleteExpectedReturnCalibrations(indexCode, EXPECTED_RETURN_V5_MODEL_VERSION);
        int calibrationRows = 0;
        for (StockExpectedReturnCalibration calibration : expectedReturnCalibrations(indexCode,
                EXPECTED_RETURN_V5_MODEL_VERSION, evaluations)) {
            mapper.upsertExpectedReturnCalibration(calibration);
            calibrationRows++;
        }
        return new ExpectedReturnRefreshResult(processedDates, savedRows, trainedRows,
                requiredTrainingRows, calibrationRows);
    }

    public ExpectedReturnRefreshResult refreshLatestExpectedReturnV5Snapshots(String indexCode, int resultLimit,
            int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> trainingSource = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        List<StockBacktestResult> latestRows = mapper.findLatestSignalRows(indexCode).stream()
                .filter(row -> row.getSymbol() != null && row.getIntegratedScore() != null)
                .toList();
        int requiredTrainingRows = Math.max(50, minTrainingRows);
        if (trainingSource.isEmpty() || latestRows.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, requiredTrainingRows, 0);
        }
        LocalDate targetDate = LocalDate.now();
        Set<Integer> horizons = trainingSource.stream()
                .map(StockBacktestResult::getHorizonDays)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDate minDate = trainingSource.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(targetDate.minusYears(3));
        MacroRegimeLookup macroLookup = MacroRegimeLookup.from(
                mapper.findMacroRegimeSnapshots(indexCode, minDate.minusDays(10), targetDate));
        String targetRegime = macroLookup.regimeLabel(targetDate);

        mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V5_MODEL_VERSION);
        latestRows.forEach(row -> saveFactorExposureSnapshots(mapper, indexCode, row));

        int savedRows = 0;
        int trainedRows = 0;
        for (Integer horizon : horizons) {
            List<StockBacktestResult> trainingRows = trainingSource.stream()
                    .filter(row -> Objects.equals(row.getHorizonDays(), horizon))
                    .filter(row -> row.getSignalDate() != null && row.getSignalDate().isBefore(targetDate))
                    .toList();
            if (trainingRows.size() < requiredTrainingRows) {
                continue;
            }
            List<TrainingObservation> observations = trainingObservations(trainingRows, macroLookup);
            if (observations.size() < requiredTrainingRows) {
                continue;
            }
            trainedRows += observations.size();
            Map<String, Map<String, FactorCoefficient>> coefficientsBySector =
                    coefficientsBySectorAndRegime(observations, latestRows, targetRegime);
            Map<String, Map<String, FactorCoefficient>> interactionsBySector =
                    interactionCoefficientsBySectorAndRegime(observations, latestRows, targetRegime);
            for (StockBacktestResult row : latestRows) {
                StockBacktestResult targetRow = latestExpectedReturnTarget(row, targetDate, horizon);
                ExpectedReturnStats baseStats = expectedReturnStats(targetRow, observations);
                FactorAdjustedPrediction prediction = nonlinearFactorAdjustedPrediction(targetRow, baseStats,
                        coefficientsBySector.getOrDefault(fallback(targetRow.getSector()), Map.of()),
                        interactionsBySector.getOrDefault(fallback(targetRow.getSector()), Map.of()));
                mapper.upsertExpectedReturnSnapshot(toExpectedReturnSnapshot(indexCode, targetRow, prediction.stats(),
                        EXPECTED_RETURN_V5_LIVE_SOURCE, EXPECTED_RETURN_V5_MODEL_VERSION));
                saveExpectedReturnFactorContributions(mapper, indexCode, targetRow, prediction.contributions(),
                        EXPECTED_RETURN_V5_LIVE_SOURCE, EXPECTED_RETURN_V5_MODEL_VERSION);
                savedRows++;
            }
        }
        return new ExpectedReturnRefreshResult(savedRows > 0 ? 1 : 0, savedRows, trainedRows,
                requiredTrainingRows, 0);
    }

    public ExpectedReturnRefreshResult refreshExpectedReturnV6Snapshots(String indexCode, int resultLimit,
            int dateLimit, int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> results = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        if (results.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<LocalDate> targetDates = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(Math.max(1, dateLimit))
                .sorted()
                .toList();
        if (targetDates.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, Math.max(50, minTrainingRows), 0);
        }
        LocalDate fromDate = targetDates.get(0);
        LocalDate toDate = targetDates.get(targetDates.size() - 1);
        List<StockExpectedReturnSnapshot> v5Snapshots = mapper.findExpectedReturnSnapshots(indexCode,
                        fromDate, toDate).stream()
                .filter(snapshot -> EXPECTED_RETURN_V5_MODEL_VERSION.equals(snapshot.getModelVersion()))
                .toList();
        if (v5Snapshots.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, Math.max(50, minTrainingRows), 0);
        }
        Map<LocalDate, List<StockExpectedReturnSnapshot>> snapshotsByDate = v5Snapshots.stream()
                .filter(snapshot -> snapshot.getSignalDate() != null)
                .collect(Collectors.groupingBy(StockExpectedReturnSnapshot::getSignalDate,
                        LinkedHashMap::new, Collectors.toList()));
        Map<ExpectedResultKey, StockBacktestResult> resultsByKey = results.stream()
                .filter(row -> row.getSignalDate() != null && row.getSymbol() != null && row.getHorizonDays() != null)
                .collect(Collectors.toMap(row -> new ExpectedResultKey(row.getSignalDate(), row.getSymbol(),
                        row.getHorizonDays()), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<ExpectedBenchmarkKey, Double> benchmarkByKey = results.stream()
                .filter(row -> row.getSignalDate() != null && row.getHorizonDays() != null)
                .collect(Collectors.groupingBy(row -> new ExpectedBenchmarkKey(row.getSignalDate(), row.getHorizonDays()),
                        LinkedHashMap::new, Collectors.averagingDouble(StockPortfolioBacktestService::returnPct)));
        ExpectedCalibrationLookup calibrationLookup =
                ExpectedCalibrationLookup.from(mapper.findExpectedReturnCalibrations(indexCode,
                        EXPECTED_RETURN_V5_MODEL_VERSION));

        int savedRows = 0;
        int processedDates = 0;
        long trainedRowCount = v5Snapshots.stream()
                .map(StockExpectedReturnSnapshot::getSampleCount)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        int trainedRows = (int) Math.min(Integer.MAX_VALUE, trainedRowCount);
        List<PredictionEvaluation> evaluations = new ArrayList<>();
        for (LocalDate targetDate : targetDates) {
            List<StockExpectedReturnSnapshot> targetSnapshots = snapshotsByDate.getOrDefault(targetDate, List.of());
            if (targetSnapshots.isEmpty()) {
                continue;
            }
            mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V6_MODEL_VERSION);
            mapper.copyExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V5_MODEL_VERSION,
                    EXPECTED_RETURN_V6_MODEL_VERSION, EXPECTED_RETURN_V6_HISTORICAL_SOURCE);
            boolean processedAny = false;
            for (StockExpectedReturnSnapshot sourceSnapshot : targetSnapshots) {
                StockExpectedReturnSnapshot stabilized = stabilizedExpectedReturnSnapshot(sourceSnapshot,
                        calibrationLookup, EXPECTED_RETURN_V6_HISTORICAL_SOURCE);
                mapper.upsertExpectedReturnSnapshot(stabilized);
                StockBacktestResult actual = resultsByKey.get(new ExpectedResultKey(sourceSnapshot.getSignalDate(),
                        sourceSnapshot.getSymbol(), valueOr(sourceSnapshot.getHorizonDays(), 0)));
                Double benchmark = benchmarkByKey.get(new ExpectedBenchmarkKey(sourceSnapshot.getSignalDate(),
                        valueOr(sourceSnapshot.getHorizonDays(), 0)));
                if (actual != null && benchmark != null) {
                    evaluations.add(new PredictionEvaluation(valueOr(sourceSnapshot.getHorizonDays(), 0),
                            number(stabilized.getCalibratedUpsideProbabilityPct()),
                            returnPct(actual) - benchmark > 0.0d));
                }
                savedRows++;
                processedAny = true;
            }
            if (processedAny) {
                processedDates++;
            }
        }
        mapper.deleteExpectedReturnCalibrations(indexCode, EXPECTED_RETURN_V6_MODEL_VERSION);
        int calibrationRows = 0;
        for (StockExpectedReturnCalibration calibration : expectedReturnCalibrations(indexCode,
                EXPECTED_RETURN_V6_MODEL_VERSION, evaluations)) {
            mapper.upsertExpectedReturnCalibration(calibration);
            calibrationRows++;
        }
        return new ExpectedReturnRefreshResult(processedDates, savedRows, trainedRows,
                Math.max(50, minTrainingRows), calibrationRows);
    }

    public ExpectedReturnRefreshResult refreshLatestExpectedReturnV6Snapshots(String indexCode, int resultLimit,
            int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        LocalDate targetDate = LocalDate.now();
        List<StockExpectedReturnSnapshot> v5Snapshots = mapper.findExpectedReturnSnapshots(indexCode,
                        targetDate, targetDate).stream()
                .filter(snapshot -> EXPECTED_RETURN_V5_MODEL_VERSION.equals(snapshot.getModelVersion()))
                .toList();
        if (v5Snapshots.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, Math.max(50, minTrainingRows), 0);
        }
        ExpectedCalibrationLookup calibrationLookup =
                ExpectedCalibrationLookup.from(mapper.findExpectedReturnCalibrations(indexCode,
                        EXPECTED_RETURN_V5_MODEL_VERSION));

        mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V6_MODEL_VERSION);
        mapper.copyExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V5_MODEL_VERSION,
                EXPECTED_RETURN_V6_MODEL_VERSION, EXPECTED_RETURN_V6_LIVE_SOURCE);
        int savedRows = 0;
        int trainedRows = 0;
        for (StockExpectedReturnSnapshot sourceSnapshot : v5Snapshots) {
            trainedRows += valueOr(sourceSnapshot.getSampleCount(), 0);
            StockExpectedReturnSnapshot stabilized = stabilizedExpectedReturnSnapshot(sourceSnapshot,
                    calibrationLookup, EXPECTED_RETURN_V6_LIVE_SOURCE);
            mapper.upsertExpectedReturnSnapshot(stabilized);
            savedRows++;
        }
        return new ExpectedReturnRefreshResult(savedRows > 0 ? 1 : 0, savedRows, trainedRows,
                Math.max(50, minTrainingRows), 0);
    }

    public ExpectedReturnRefreshResult refreshExpectedReturnV7Snapshots(String indexCode, int resultLimit,
            int dateLimit, int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<StockBacktestResult> results = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(StockPortfolioBacktestService::usable)
                .sorted(Comparator
                        .comparing(StockBacktestResult::getSignalDate)
                        .thenComparing(StockBacktestResult::getHorizonDays)
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        if (results.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        List<LocalDate> targetDates = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(Math.max(1, dateLimit))
                .sorted()
                .toList();
        if (targetDates.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, Math.max(50, minTrainingRows), 0);
        }
        LocalDate fromDate = targetDates.get(0);
        LocalDate toDate = targetDates.get(targetDates.size() - 1);
        List<StockExpectedReturnSnapshot> v6Snapshots = mapper.findExpectedReturnSnapshots(indexCode,
                        fromDate, toDate).stream()
                .filter(snapshot -> EXPECTED_RETURN_V6_MODEL_VERSION.equals(snapshot.getModelVersion()))
                .toList();
        if (v6Snapshots.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, Math.max(50, minTrainingRows), 0);
        }
        Map<LocalDate, List<StockExpectedReturnSnapshot>> snapshotsByDate = v6Snapshots.stream()
                .filter(snapshot -> snapshot.getSignalDate() != null)
                .collect(Collectors.groupingBy(StockExpectedReturnSnapshot::getSignalDate,
                        LinkedHashMap::new, Collectors.toList()));
        Map<ExpectedResultKey, StockBacktestResult> resultsByKey = results.stream()
                .filter(row -> row.getSignalDate() != null && row.getSymbol() != null && row.getHorizonDays() != null)
                .collect(Collectors.toMap(row -> new ExpectedResultKey(row.getSignalDate(), row.getSymbol(),
                        row.getHorizonDays()), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<ExpectedBenchmarkKey, Double> benchmarkByKey = results.stream()
                .filter(row -> row.getSignalDate() != null && row.getHorizonDays() != null)
                .collect(Collectors.groupingBy(row -> new ExpectedBenchmarkKey(row.getSignalDate(), row.getHorizonDays()),
                        LinkedHashMap::new, Collectors.averagingDouble(StockPortfolioBacktestService::returnPct)));
        ExpectedCalibrationLookup calibrationLookup =
                ExpectedCalibrationLookup.from(mapper.findExpectedReturnCalibrations(indexCode,
                        EXPECTED_RETURN_V6_MODEL_VERSION));

        int savedRows = 0;
        int processedDates = 0;
        long trainedRowCount = v6Snapshots.stream()
                .map(StockExpectedReturnSnapshot::getSampleCount)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        int trainedRows = (int) Math.min(Integer.MAX_VALUE, trainedRowCount);
        List<PredictionEvaluation> evaluations = new ArrayList<>();
        for (LocalDate targetDate : targetDates) {
            List<StockExpectedReturnSnapshot> targetSnapshots = snapshotsByDate.getOrDefault(targetDate, List.of());
            if (targetSnapshots.isEmpty()) {
                continue;
            }
            mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V7_MODEL_VERSION);
            mapper.copyExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V6_MODEL_VERSION,
                    EXPECTED_RETURN_V7_MODEL_VERSION, EXPECTED_RETURN_V7_HISTORICAL_SOURCE);
            boolean processedAny = false;
            for (StockExpectedReturnSnapshot sourceSnapshot : targetSnapshots) {
                StockExpectedReturnSnapshot stabilized = horizonDecayExpectedReturnSnapshot(sourceSnapshot,
                        calibrationLookup, EXPECTED_RETURN_V7_HISTORICAL_SOURCE);
                mapper.upsertExpectedReturnSnapshot(stabilized);
                StockBacktestResult actual = resultsByKey.get(new ExpectedResultKey(sourceSnapshot.getSignalDate(),
                        sourceSnapshot.getSymbol(), valueOr(sourceSnapshot.getHorizonDays(), 0)));
                Double benchmark = benchmarkByKey.get(new ExpectedBenchmarkKey(sourceSnapshot.getSignalDate(),
                        valueOr(sourceSnapshot.getHorizonDays(), 0)));
                if (actual != null && benchmark != null) {
                    evaluations.add(new PredictionEvaluation(valueOr(sourceSnapshot.getHorizonDays(), 0),
                            number(stabilized.getCalibratedUpsideProbabilityPct()),
                            returnPct(actual) - benchmark > 0.0d));
                }
                savedRows++;
                processedAny = true;
            }
            if (processedAny) {
                processedDates++;
            }
        }
        mapper.deleteExpectedReturnCalibrations(indexCode, EXPECTED_RETURN_V7_MODEL_VERSION);
        int calibrationRows = 0;
        for (StockExpectedReturnCalibration calibration : expectedReturnCalibrations(indexCode,
                EXPECTED_RETURN_V7_MODEL_VERSION, evaluations)) {
            mapper.upsertExpectedReturnCalibration(calibration);
            calibrationRows++;
        }
        return new ExpectedReturnRefreshResult(processedDates, savedRows, trainedRows,
                Math.max(50, minTrainingRows), calibrationRows);
    }

    public ExpectedReturnRefreshResult refreshLatestExpectedReturnV7Snapshots(String indexCode, int resultLimit,
            int minTrainingRows) {
        clearPortfolioViewCache();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null || indexCode == null || indexCode.isBlank()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        LocalDate targetDate = LocalDate.now();
        List<StockExpectedReturnSnapshot> v6Snapshots = mapper.findExpectedReturnSnapshots(indexCode,
                        targetDate, targetDate).stream()
                .filter(snapshot -> EXPECTED_RETURN_V6_MODEL_VERSION.equals(snapshot.getModelVersion()))
                .toList();
        if (v6Snapshots.isEmpty()) {
            return new ExpectedReturnRefreshResult(0, 0, 0, Math.max(50, minTrainingRows), 0);
        }
        ExpectedCalibrationLookup calibrationLookup =
                ExpectedCalibrationLookup.from(mapper.findExpectedReturnCalibrations(indexCode,
                        EXPECTED_RETURN_V6_MODEL_VERSION));

        mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V7_MODEL_VERSION);
        mapper.copyExpectedReturnFactorContributions(indexCode, targetDate, EXPECTED_RETURN_V6_MODEL_VERSION,
                EXPECTED_RETURN_V7_MODEL_VERSION, EXPECTED_RETURN_V7_LIVE_SOURCE);
        int savedRows = 0;
        int trainedRows = 0;
        for (StockExpectedReturnSnapshot sourceSnapshot : v6Snapshots) {
            trainedRows += valueOr(sourceSnapshot.getSampleCount(), 0);
            StockExpectedReturnSnapshot stabilized = horizonDecayExpectedReturnSnapshot(sourceSnapshot,
                    calibrationLookup, EXPECTED_RETURN_V7_LIVE_SOURCE);
            mapper.upsertExpectedReturnSnapshot(stabilized);
            savedRows++;
        }
        return new ExpectedReturnRefreshResult(savedRows > 0 ? 1 : 0, savedRows, trainedRows,
                Math.max(50, minTrainingRows), 0);
    }

    private static List<StockCovarianceSnapshot> covarianceSnapshots(String indexCode, LocalDate signalDate,
            List<String> symbols, List<StockCandleDaily> candles, int lookbackDays) {
        Map<String, List<DailyReturn>> returnsBySymbol = dailyReturnsBySymbol(candles).entrySet().stream()
                .filter(entry -> symbols.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> tail(entry.getValue().stream()
                                .filter(row -> !row.tradeDate().isAfter(signalDate))
                                .toList(), lookbackDays),
                        (left, right) -> left, LinkedHashMap::new));
        List<String> ordered = symbols.stream()
                .filter(symbol -> returnsBySymbol.containsKey(symbol) && returnsBySymbol.get(symbol).size() >= MIN_COVARIANCE_OBSERVATIONS)
                .distinct()
                .sorted()
                .toList();
        List<StockCovarianceSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i; j < ordered.size(); j++) {
                PairCovariance covariance = pairCovariance(returnsBySymbol.get(ordered.get(i)),
                        returnsBySymbol.get(ordered.get(j)));
                if (!covariance.usable()) {
                    continue;
                }
                StockCovarianceSnapshot snapshot = new StockCovarianceSnapshot();
                snapshot.setIndexCode(indexCode);
                snapshot.setSnapshotDate(signalDate);
                snapshot.setSymbolA(ordered.get(i));
                snapshot.setSymbolB(ordered.get(j));
                snapshot.setCorrelation(BigDecimal.valueOf(covariance.correlation()));
                snapshot.setCovariance(BigDecimal.valueOf(covariance.annualizedCovariance()));
                snapshot.setVolatilityAPct(BigDecimal.valueOf(covariance.volatilityAPct()));
                snapshot.setVolatilityBPct(BigDecimal.valueOf(covariance.volatilityBPct()));
                snapshot.setObservations(covariance.observations());
                snapshot.setLookbackDays(lookbackDays);
                snapshot.setSource("TRAILING_DAILY_RETURN");
                snapshots.add(snapshot);
            }
        }
        return snapshots;
    }

    private static Map<String, List<DailyReturn>> dailyReturnsBySymbol(List<StockCandleDaily> candles) {
        if (candles == null || candles.isEmpty()) {
            return Map.of();
        }
        Map<String, List<StockCandleDaily>> candlesBySymbol = candles.stream()
                .filter(candle -> candle.getSymbol() != null
                        && candle.getTradeDate() != null
                        && candle.getClosePrice() != null
                        && candle.getClosePrice().signum() > 0)
                .collect(Collectors.groupingBy(StockCandleDaily::getSymbol, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<DailyReturn>> returns = new LinkedHashMap<>();
        for (Map.Entry<String, List<StockCandleDaily>> entry : candlesBySymbol.entrySet()) {
            List<StockCandleDaily> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(StockCandleDaily::getTradeDate))
                    .toList();
            List<DailyReturn> rows = new ArrayList<>();
            StockCandleDaily previous = null;
            for (StockCandleDaily candle : sorted) {
                if (previous != null
                        && previous.getClosePrice() != null
                        && previous.getClosePrice().signum() > 0
                        && candle.getClosePrice() != null
                        && candle.getClosePrice().signum() > 0) {
                    double close = candle.getClosePrice().doubleValue();
                    double previousClose = previous.getClosePrice().doubleValue();
                    double dollarVolume = close * Math.max(0L, candle.getVolume() == null ? 0L : candle.getVolume());
                    rows.add(new DailyReturn(candle.getTradeDate(), close / previousClose - 1.0d, dollarVolume));
                }
                previous = candle;
            }
            if (!rows.isEmpty()) {
                returns.put(entry.getKey(), rows);
            }
        }
        return returns;
    }

    private static PairCovariance pairCovariance(List<DailyReturn> left, List<DailyReturn> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return PairCovariance.missing();
        }
        Map<LocalDate, Double> rightByDate = right.stream()
                .collect(Collectors.toMap(DailyReturn::tradeDate, DailyReturn::returnValue,
                        (first, second) -> second, LinkedHashMap::new));
        List<Double> a = new ArrayList<>();
        List<Double> b = new ArrayList<>();
        for (DailyReturn row : left) {
            Double rightReturn = rightByDate.get(row.tradeDate());
            if (rightReturn != null && Double.isFinite(row.returnValue()) && Double.isFinite(rightReturn)) {
                a.add(row.returnValue());
                b.add(rightReturn);
            }
        }
        if (a.size() < MIN_COVARIANCE_OBSERVATIONS) {
            return PairCovariance.missing();
        }
        double meanA = a.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanB = b.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double covariance = 0.0d;
        double varianceA = 0.0d;
        double varianceB = 0.0d;
        for (int i = 0; i < a.size(); i++) {
            double deltaA = a.get(i) - meanA;
            double deltaB = b.get(i) - meanB;
            covariance += deltaA * deltaB;
            varianceA += deltaA * deltaA;
            varianceB += deltaB * deltaB;
        }
        covariance /= Math.max(1, a.size() - 1);
        varianceA /= Math.max(1, a.size() - 1);
        varianceB /= Math.max(1, a.size() - 1);
        if (varianceA <= 0 || varianceB <= 0) {
            return PairCovariance.missing();
        }
        double correlation = covariance / Math.sqrt(varianceA * varianceB);
        correlation = Math.max(-1.0d, Math.min(1.0d, correlation));
        return new PairCovariance(correlation, covariance * 252.0d,
                Math.sqrt(varianceA) * Math.sqrt(252.0d) * 100.0d,
                Math.sqrt(varianceB) * Math.sqrt(252.0d) * 100.0d,
                a.size());
    }

    private static List<TrainingObservation> trainingObservations(List<StockBacktestResult> trainingRows) {
        return trainingObservations(trainingRows, MacroRegimeLookup.empty());
    }

    private static List<TrainingObservation> trainingObservations(List<StockBacktestResult> trainingRows,
            MacroRegimeLookup macroLookup) {
        Map<LocalDate, Double> dateBenchmark = trainingRows.stream()
                .filter(row -> row.getSignalDate() != null)
                .collect(Collectors.groupingBy(StockBacktestResult::getSignalDate, LinkedHashMap::new,
                        Collectors.averagingDouble(StockPortfolioBacktestService::returnPct)));
        List<TrainingObservation> observations = new ArrayList<>();
        for (StockBacktestResult row : trainingRows) {
            Double benchmarkReturn = dateBenchmark.get(row.getSignalDate());
            if (benchmarkReturn == null || !Double.isFinite(benchmarkReturn)) {
                continue;
            }
            double forwardReturn = returnPct(row);
            observations.add(new TrainingObservation(
                    score(row),
                    fallback(row.getSector()),
                    macroLookup.regimeLabel(row.getSignalDate()),
                    macroLookup.macroScore(row.getSignalDate()),
                    forwardReturn,
                    forwardReturn - benchmarkReturn,
                    factorExposures(row)));
        }
        return observations;
    }

    private static ExpectedReturnStats expectedReturnStats(StockBacktestResult row,
            List<TrainingObservation> observations) {
        int score = score(row);
        String sector = fallback(row.getSector());
        List<TrainingObservation> global = scoreBand(observations, score, 12, 220);
        if (global.size() < 50) {
            global = scoreBand(observations, score, 22, 260);
        }
        if (global.size() < 50) {
            global = nearestByScore(observations, score, Math.min(300, observations.size()));
        }
        List<TrainingObservation> sectorBand = observations.stream()
                .filter(observation -> Objects.equals(observation.sector(), sector))
                .filter(observation -> Math.abs(observation.score() - score) <= 18)
                .sorted(Comparator.comparingInt(observation -> Math.abs(observation.score() - score)))
                .limit(140)
                .toList();
        List<TrainingObservation> nearest = nearestByScore(observations, score, Math.min(160, observations.size()));

        ObservedStats globalStats = observedStats(global);
        ObservedStats sectorStats = observedStats(sectorBand);
        ObservedStats nearestStats = observedStats(nearest);
        double sectorWeight = sectorStats.count() >= 25 ? 0.25d : 0.0d;
        double nearestWeight = 0.25d;
        double globalWeight = 1.0d - sectorWeight - nearestWeight;

        double expectedReturn = weightedAverage(globalStats.expectedReturnPct(), globalWeight,
                sectorStats.expectedReturnPct(), sectorWeight,
                nearestStats.expectedReturnPct(), nearestWeight);
        double expectedExcess = weightedAverage(globalStats.expectedExcessReturnPct(), globalWeight,
                sectorStats.expectedExcessReturnPct(), sectorWeight,
                nearestStats.expectedExcessReturnPct(), nearestWeight);
        double returnP10 = weightedAverage(globalStats.returnP10Pct(), globalWeight,
                sectorStats.returnP10Pct(), sectorWeight,
                nearestStats.returnP10Pct(), nearestWeight);
        double returnP50 = weightedAverage(globalStats.returnP50Pct(), globalWeight,
                sectorStats.returnP50Pct(), sectorWeight,
                nearestStats.returnP50Pct(), nearestWeight);
        double returnP90 = weightedAverage(globalStats.returnP90Pct(), globalWeight,
                sectorStats.returnP90Pct(), sectorWeight,
                nearestStats.returnP90Pct(), nearestWeight);
        double excessP10 = weightedAverage(globalStats.excessP10Pct(), globalWeight,
                sectorStats.excessP10Pct(), sectorWeight,
                nearestStats.excessP10Pct(), nearestWeight);
        double excessP50 = weightedAverage(globalStats.excessP50Pct(), globalWeight,
                sectorStats.excessP50Pct(), sectorWeight,
                nearestStats.excessP50Pct(), nearestWeight);
        double excessP90 = weightedAverage(globalStats.excessP90Pct(), globalWeight,
                sectorStats.excessP90Pct(), sectorWeight,
                nearestStats.excessP90Pct(), nearestWeight);
        double upsideProbability = weightedAverage(globalStats.upsideProbabilityPct(), globalWeight,
                sectorStats.upsideProbabilityPct(), sectorWeight,
                nearestStats.upsideProbabilityPct(), nearestWeight);
        double downsideProbability = weightedAverage(globalStats.downsideProbabilityPct(), globalWeight,
                sectorStats.downsideProbabilityPct(), sectorWeight,
                nearestStats.downsideProbabilityPct(), nearestWeight);
        double drawdownRisk = weightedAverage(globalStats.drawdownRiskPct(), globalWeight,
                sectorStats.drawdownRiskPct(), sectorWeight,
                nearestStats.drawdownRiskPct(), nearestWeight);
        int confidence = expectedReturnConfidence(globalStats.count(), sectorStats.count(), row);
        double calibratedUpsideProbability = calibratedUpsideProbability(upsideProbability,
                globalStats.upsideProbabilityPct(), confidence);
        return new ExpectedReturnStats(expectedReturn, expectedExcess, upsideProbability,
                calibratedUpsideProbability, downsideProbability, drawdownRisk,
                returnP10, returnP50, returnP90, excessP10, excessP50, excessP90,
                confidence, globalStats.count(), sectorStats.count(), scoreBucket(score),
                probabilityBucket(calibratedUpsideProbability), Double.NaN);
    }

    private static StockExpectedReturnSnapshot toExpectedReturnSnapshot(String indexCode, StockBacktestResult row,
            ExpectedReturnStats stats) {
        return toExpectedReturnSnapshot(indexCode, row, stats, EXPECTED_RETURN_HISTORICAL_SOURCE,
                EXPECTED_RETURN_V2_MODEL_VERSION);
    }

    private static StockExpectedReturnSnapshot toExpectedReturnSnapshot(String indexCode, StockBacktestResult row,
            ExpectedReturnStats stats, String source, String modelVersion) {
        StockExpectedReturnSnapshot snapshot = new StockExpectedReturnSnapshot();
        snapshot.setIndexCode(indexCode);
        snapshot.setSignalDate(row.getSignalDate());
        snapshot.setSymbol(row.getSymbol());
        snapshot.setHorizonDays(row.getHorizonDays());
        snapshot.setExpectedReturnPct(decimalOrNull(stats.expectedReturnPct()));
        snapshot.setExpectedExcessReturnPct(decimalOrNull(stats.expectedExcessReturnPct()));
        snapshot.setReturnP10Pct(decimalOrNull(stats.returnP10Pct()));
        snapshot.setReturnP50Pct(decimalOrNull(stats.returnP50Pct()));
        snapshot.setReturnP90Pct(decimalOrNull(stats.returnP90Pct()));
        snapshot.setExcessP10Pct(decimalOrNull(stats.excessP10Pct()));
        snapshot.setExcessP50Pct(decimalOrNull(stats.excessP50Pct()));
        snapshot.setExcessP90Pct(decimalOrNull(stats.excessP90Pct()));
        snapshot.setUpsideProbabilityPct(decimalOrNull(stats.upsideProbabilityPct()));
        snapshot.setCalibratedUpsideProbabilityPct(decimalOrNull(stats.calibratedUpsideProbabilityPct()));
        snapshot.setDownsideProbabilityPct(decimalOrNull(stats.downsideProbabilityPct()));
        snapshot.setDrawdownRiskPct(decimalOrNull(stats.drawdownRiskPct()));
        snapshot.setConfidence(stats.confidence());
        snapshot.setSampleCount(stats.sampleCount());
        snapshot.setSectorSampleCount(stats.sectorSampleCount());
        snapshot.setScoreBucket(stats.scoreBucket());
        snapshot.setCalibrationBucket(stats.calibrationBucket());
        snapshot.setCalibrationErrorPct(decimalOrNull(stats.calibrationErrorPct()));
        snapshot.setModelVersion(modelVersion);
        snapshot.setSource(source);
        return snapshot;
    }

    private static StockExpectedReturnSnapshot stabilizedExpectedReturnSnapshot(
            StockExpectedReturnSnapshot sourceSnapshot, ExpectedCalibrationLookup calibrationLookup, String source) {
        double calibrationError = calibrationLookup.errorFor(
                valueOr(sourceSnapshot.getHorizonDays(), PRIMARY_HORIZON_DAYS),
                valueOr(sourceSnapshot.getCalibrationBucket(), -1));
        double absCalibrationError = Double.isFinite(calibrationError) ? Math.abs(calibrationError) : 8.0d;
        int confidence = valueOr(sourceSnapshot.getConfidence(), 0);
        int sampleCount = valueOr(sourceSnapshot.getSampleCount(), 0);
        int sectorSampleCount = valueOr(sourceSnapshot.getSectorSampleCount(), 0);
        double shrink = 0.50d + clamp(confidence, 0, 100) / 100.0d * 0.26d;
        shrink -= Math.min(0.18d, absCalibrationError / 100.0d);
        if (sampleCount < 300) {
            shrink -= 0.08d;
        }
        if (sectorSampleCount < 40) {
            shrink -= 0.05d;
        }
        shrink = clamp(shrink, 0.34d, 0.78d);

        double probabilityStrength = 0.45d + clamp(confidence, 0, 100) / 100.0d * 0.30d
                - Math.min(0.14d, absCalibrationError / 120.0d);
        probabilityStrength = clamp(probabilityStrength, 0.32d, 0.78d);

        double expectedReturn = shrinkAroundZero(number(sourceSnapshot.getExpectedReturnPct()), shrink);
        double expectedExcess = shrinkAroundZero(number(sourceSnapshot.getExpectedExcessReturnPct()), shrink);
        double returnP10 = shrinkAroundZero(number(sourceSnapshot.getReturnP10Pct()), shrink);
        double returnP50 = shrinkAroundZero(number(sourceSnapshot.getReturnP50Pct()), shrink);
        double returnP90 = shrinkAroundZero(number(sourceSnapshot.getReturnP90Pct()), shrink);
        double excessP10 = shrinkAroundZero(number(sourceSnapshot.getExcessP10Pct()), shrink);
        double excessP50 = shrinkAroundZero(number(sourceSnapshot.getExcessP50Pct()), shrink);
        double excessP90 = shrinkAroundZero(number(sourceSnapshot.getExcessP90Pct()), shrink);
        double rawUpside = finiteOr(number(sourceSnapshot.getUpsideProbabilityPct()), 50.0d);
        double calibratedUpside = finiteOr(number(sourceSnapshot.getCalibratedUpsideProbabilityPct()), rawUpside);
        if (Double.isFinite(calibrationError)) {
            calibratedUpside += calibrationError * 0.60d;
            rawUpside += calibrationError * 0.35d;
        }
        rawUpside = shrinkProbability(rawUpside, probabilityStrength);
        calibratedUpside = shrinkProbability(calibratedUpside, probabilityStrength);
        double downside = finiteOr(number(sourceSnapshot.getDownsideProbabilityPct()), 35.0d);
        downside = clamp(downside + (50.0d - calibratedUpside) * 0.30d, 1.0d, 99.0d);
        double drawdownRisk = number(sourceSnapshot.getDrawdownRiskPct());
        if (Double.isFinite(drawdownRisk) && confidence < 65) {
            drawdownRisk += (65 - confidence) * 0.035d;
        }
        int adjustedConfidence = confidence;
        adjustedConfidence -= (int) Math.round(Math.min(14.0d, absCalibrationError * 0.70d));
        if (sampleCount < 300) {
            adjustedConfidence -= 4;
        }
        if (sampleCount < 120) {
            adjustedConfidence -= 5;
        }
        if (sectorSampleCount < 40) {
            adjustedConfidence -= 3;
        }
        if (!Double.isFinite(calibrationError)) {
            adjustedConfidence -= 6;
        }
        adjustedConfidence = Math.max(0, Math.min(78, adjustedConfidence));

        StockExpectedReturnSnapshot stabilized = new StockExpectedReturnSnapshot();
        stabilized.setIndexCode(sourceSnapshot.getIndexCode());
        stabilized.setSignalDate(sourceSnapshot.getSignalDate());
        stabilized.setSymbol(sourceSnapshot.getSymbol());
        stabilized.setHorizonDays(sourceSnapshot.getHorizonDays());
        stabilized.setExpectedReturnPct(decimalOrNull(expectedReturn));
        stabilized.setExpectedExcessReturnPct(decimalOrNull(expectedExcess));
        stabilized.setReturnP10Pct(decimalOrNull(returnP10));
        stabilized.setReturnP50Pct(decimalOrNull(returnP50));
        stabilized.setReturnP90Pct(decimalOrNull(returnP90));
        stabilized.setExcessP10Pct(decimalOrNull(excessP10));
        stabilized.setExcessP50Pct(decimalOrNull(excessP50));
        stabilized.setExcessP90Pct(decimalOrNull(excessP90));
        stabilized.setUpsideProbabilityPct(decimalOrNull(rawUpside));
        stabilized.setCalibratedUpsideProbabilityPct(decimalOrNull(calibratedUpside));
        stabilized.setDownsideProbabilityPct(decimalOrNull(downside));
        stabilized.setDrawdownRiskPct(decimalOrNull(drawdownRisk));
        stabilized.setConfidence(adjustedConfidence);
        stabilized.setSampleCount(sourceSnapshot.getSampleCount());
        stabilized.setSectorSampleCount(sourceSnapshot.getSectorSampleCount());
        stabilized.setScoreBucket(sourceSnapshot.getScoreBucket());
        stabilized.setCalibrationBucket(probabilityBucket(calibratedUpside));
        stabilized.setCalibrationErrorPct(decimalOrNull(calibrationError));
        stabilized.setModelVersion(EXPECTED_RETURN_V6_MODEL_VERSION);
        stabilized.setSource(source);
        return stabilized;
    }

    private static StockExpectedReturnSnapshot horizonDecayExpectedReturnSnapshot(
            StockExpectedReturnSnapshot sourceSnapshot, ExpectedCalibrationLookup calibrationLookup, String source) {
        int horizonDays = valueOr(sourceSnapshot.getHorizonDays(), PRIMARY_HORIZON_DAYS);
        double calibrationError = calibrationLookup.errorFor(horizonDays, valueOr(sourceSnapshot.getCalibrationBucket(), -1));
        double absCalibrationError = Double.isFinite(calibrationError) ? Math.abs(calibrationError) : 7.0d;
        int confidence = valueOr(sourceSnapshot.getConfidence(), 0);
        int sampleCount = valueOr(sourceSnapshot.getSampleCount(), 0);
        int sectorSampleCount = valueOr(sourceSnapshot.getSectorSampleCount(), 0);

        double horizonDecay = horizonDecay(horizonDays);
        double evidenceStrength = 0.72d + clamp(confidence, 0, 100) / 100.0d * 0.16d;
        if (sampleCount < 600) {
            evidenceStrength -= 0.05d;
        }
        if (sampleCount < 300) {
            evidenceStrength -= 0.06d;
        }
        if (sectorSampleCount < 60) {
            evidenceStrength -= 0.04d;
        }
        evidenceStrength -= Math.min(0.10d, absCalibrationError / 160.0d);
        double expectedShrink = clamp(horizonDecay * evidenceStrength, 0.42d, 0.82d);
        double rangeShrink = clamp(expectedShrink + 0.08d, 0.50d, 0.88d);

        double expectedReturn = shrinkAroundZero(number(sourceSnapshot.getExpectedReturnPct()), expectedShrink);
        double expectedExcess = shrinkAroundZero(number(sourceSnapshot.getExpectedExcessReturnPct()), expectedShrink);
        double returnP10 = shrinkAroundZero(number(sourceSnapshot.getReturnP10Pct()), rangeShrink);
        double returnP50 = shrinkAroundZero(number(sourceSnapshot.getReturnP50Pct()), expectedShrink);
        double returnP90 = shrinkAroundZero(number(sourceSnapshot.getReturnP90Pct()), rangeShrink);
        double excessP10 = shrinkAroundZero(number(sourceSnapshot.getExcessP10Pct()), rangeShrink);
        double excessP50 = shrinkAroundZero(number(sourceSnapshot.getExcessP50Pct()), expectedShrink);
        double excessP90 = shrinkAroundZero(number(sourceSnapshot.getExcessP90Pct()), rangeShrink);

        double probabilityStrength = 0.56d + clamp(confidence, 0, 100) / 100.0d * 0.20d
                - Math.min(0.12d, absCalibrationError / 130.0d);
        probabilityStrength *= horizonProbabilityMultiplier(horizonDays);
        probabilityStrength = clamp(probabilityStrength, 0.30d, 0.74d);
        double rawUpside = finiteOr(number(sourceSnapshot.getUpsideProbabilityPct()), 50.0d);
        double calibratedUpside = finiteOr(number(sourceSnapshot.getCalibratedUpsideProbabilityPct()), rawUpside);
        if (Double.isFinite(calibrationError)) {
            calibratedUpside += calibrationError * 0.42d;
            rawUpside += calibrationError * 0.24d;
        }
        rawUpside = shrinkProbability(rawUpside, probabilityStrength);
        calibratedUpside = shrinkProbability(calibratedUpside, probabilityStrength);

        double downside = finiteOr(number(sourceSnapshot.getDownsideProbabilityPct()), 35.0d);
        downside = clamp(downside + (50.0d - calibratedUpside) * 0.36d
                + horizonDownsidePenalty(horizonDays, confidence), 1.0d, 99.0d);
        double drawdownRisk = number(sourceSnapshot.getDrawdownRiskPct());
        if (Double.isFinite(drawdownRisk)) {
            drawdownRisk = clamp(drawdownRisk + horizonDownsidePenalty(horizonDays, confidence) * 0.35d,
                    0.0d, 99.0d);
        }

        int adjustedConfidence = confidence;
        adjustedConfidence -= (int) Math.round(Math.min(12.0d, absCalibrationError * 0.60d));
        adjustedConfidence -= horizonConfidencePenalty(horizonDays);
        if (sampleCount < 600) {
            adjustedConfidence -= 3;
        }
        if (sampleCount < 300) {
            adjustedConfidence -= 4;
        }
        if (sectorSampleCount < 60) {
            adjustedConfidence -= 3;
        }
        if (!Double.isFinite(calibrationError)) {
            adjustedConfidence -= 5;
        }
        adjustedConfidence = Math.max(0, Math.min(76, adjustedConfidence));

        StockExpectedReturnSnapshot stabilized = new StockExpectedReturnSnapshot();
        stabilized.setIndexCode(sourceSnapshot.getIndexCode());
        stabilized.setSignalDate(sourceSnapshot.getSignalDate());
        stabilized.setSymbol(sourceSnapshot.getSymbol());
        stabilized.setHorizonDays(sourceSnapshot.getHorizonDays());
        stabilized.setExpectedReturnPct(decimalOrNull(expectedReturn));
        stabilized.setExpectedExcessReturnPct(decimalOrNull(expectedExcess));
        stabilized.setReturnP10Pct(decimalOrNull(returnP10));
        stabilized.setReturnP50Pct(decimalOrNull(returnP50));
        stabilized.setReturnP90Pct(decimalOrNull(returnP90));
        stabilized.setExcessP10Pct(decimalOrNull(excessP10));
        stabilized.setExcessP50Pct(decimalOrNull(excessP50));
        stabilized.setExcessP90Pct(decimalOrNull(excessP90));
        stabilized.setUpsideProbabilityPct(decimalOrNull(rawUpside));
        stabilized.setCalibratedUpsideProbabilityPct(decimalOrNull(calibratedUpside));
        stabilized.setDownsideProbabilityPct(decimalOrNull(downside));
        stabilized.setDrawdownRiskPct(decimalOrNull(drawdownRisk));
        stabilized.setConfidence(adjustedConfidence);
        stabilized.setSampleCount(sourceSnapshot.getSampleCount());
        stabilized.setSectorSampleCount(sourceSnapshot.getSectorSampleCount());
        stabilized.setScoreBucket(sourceSnapshot.getScoreBucket());
        stabilized.setCalibrationBucket(probabilityBucket(calibratedUpside));
        stabilized.setCalibrationErrorPct(decimalOrNull(calibrationError));
        stabilized.setModelVersion(EXPECTED_RETURN_V7_MODEL_VERSION);
        stabilized.setSource(source);
        return stabilized;
    }

    private static double horizonDecay(int horizonDays) {
        if (horizonDays <= 5) {
            return 0.92d;
        }
        if (horizonDays <= 20) {
            return 0.86d;
        }
        return 0.72d;
    }

    private static double horizonProbabilityMultiplier(int horizonDays) {
        if (horizonDays <= 5) {
            return 0.96d;
        }
        if (horizonDays <= 20) {
            return 0.90d;
        }
        return 0.76d;
    }

    private static double horizonDownsidePenalty(int horizonDays, int confidence) {
        double lowConfidencePenalty = Math.max(0, 60 - confidence) * 0.025d;
        if (horizonDays <= 5) {
            return lowConfidencePenalty;
        }
        if (horizonDays <= 20) {
            return 0.35d + lowConfidencePenalty;
        }
        return 0.85d + lowConfidencePenalty;
    }

    private static int horizonConfidencePenalty(int horizonDays) {
        if (horizonDays <= 5) {
            return 1;
        }
        if (horizonDays <= 20) {
            return 2;
        }
        return 5;
    }

    private static double shrinkAroundZero(double value, double shrink) {
        return Double.isFinite(value) ? value * shrink : Double.NaN;
    }

    private static double shrinkProbability(double probability, double strength) {
        if (!Double.isFinite(probability)) {
            return Double.NaN;
        }
        return clamp(50.0d + (probability - 50.0d) * strength, 1.0d, 99.0d);
    }

    private static StockBacktestResult latestExpectedReturnTarget(StockBacktestResult source, LocalDate signalDate,
            int horizonDays) {
        StockBacktestResult target = new StockBacktestResult();
        target.setSymbol(source.getSymbol());
        target.setName(source.getName());
        target.setSignalDate(signalDate);
        target.setHorizonDays(horizonDays);
        target.setIntegratedScore(source.getIntegratedScore());
        target.setValuationScore(source.getValuationScore());
        target.setQualityScore(source.getQualityScore());
        target.setGrowthScore(source.getGrowthScore());
        target.setStabilityScore(source.getStabilityScore());
        target.setEarningsScore(source.getEarningsScore());
        target.setAnalystScore(source.getAnalystScore());
        target.setNewsScore(source.getNewsScore());
        target.setMomentumScore(source.getMomentumScore());
        target.setRiskScore(source.getRiskScore());
        target.setInstitutionScore(source.getInstitutionScore());
        target.setDataQualityScore(source.getDataQualityScore());
        target.setMarketCap(source.getMarketCap());
        target.setSector(source.getSector());
        target.setCalculatedAt(source.getCalculatedAt());
        return target;
    }

    private static void saveFactorExposureSnapshots(StockBacktestMapper mapper, String indexCode,
            StockBacktestResult row) {
        if (mapper == null || row == null || row.getSignalDate() == null || row.getSymbol() == null) {
            return;
        }
        Map<String, Integer> rawScores = rawFactorScores(row);
        Map<String, Double> exposures = factorExposures(row);
        for (FactorSpec factor : FACTOR_SPECS) {
            if (!rawScores.containsKey(factor.name())) {
                continue;
            }
            StockFactorExposureSnapshot snapshot = new StockFactorExposureSnapshot();
            snapshot.setIndexCode(indexCode);
            snapshot.setSignalDate(row.getSignalDate());
            snapshot.setSymbol(row.getSymbol());
            snapshot.setFactor(factor.name());
            snapshot.setRawScore(rawScores.get(factor.name()));
            snapshot.setExposureScore(decimalOrNull(exposures.getOrDefault(factor.name(), Double.NaN)));
            snapshot.setSector(fallback(row.getSector()));
            snapshot.setMarketCap(row.getMarketCap());
            snapshot.setDataQualityScore(row.getDataQualityScore());
            snapshot.setSource(FACTOR_EXPOSURE_SOURCE);
            mapper.upsertFactorExposureSnapshot(snapshot);
        }
    }

    private static void saveExpectedReturnFactorContributions(StockBacktestMapper mapper, String indexCode,
            StockBacktestResult row, List<FactorContribution> contributions, String source, String modelVersion) {
        if (mapper == null || row == null || row.getSignalDate() == null || row.getSymbol() == null
                || row.getHorizonDays() == null || contributions == null || contributions.isEmpty()) {
            return;
        }
        if (!Objects.equals(row.getHorizonDays(), PRIMARY_HORIZON_DAYS)) {
            return;
        }
        for (FactorContribution sourceContribution : contributions) {
            StockExpectedReturnFactorContribution contribution = new StockExpectedReturnFactorContribution();
            contribution.setIndexCode(indexCode);
            contribution.setSignalDate(row.getSignalDate());
            contribution.setSymbol(row.getSymbol());
            contribution.setHorizonDays(row.getHorizonDays());
            contribution.setModelVersion(modelVersion);
            contribution.setFactor(sourceContribution.factor());
            contribution.setExposureScore(decimalOrNull(sourceContribution.exposure()));
            contribution.setCoefficient(decimalOrNull(sourceContribution.coefficient()));
            contribution.setContributionPct(decimalOrNull(sourceContribution.contributionPct()));
            contribution.setSampleCount(sourceContribution.sampleCount());
            contribution.setSource(source);
            mapper.upsertExpectedReturnFactorContribution(contribution);
        }
    }

    private static Map<String, Map<String, FactorCoefficient>> coefficientsBySectorAndRegime(
            List<TrainingObservation> observations, List<StockBacktestResult> targetRows, String targetRegime) {
        if (observations == null || observations.isEmpty() || targetRows == null || targetRows.isEmpty()) {
            return Map.of();
        }
        Set<String> sectors = targetRows.stream()
                .map(row -> fallback(row.getSector()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Map<String, FactorCoefficient>> bySector = new LinkedHashMap<>();
        for (String sector : sectors) {
            bySector.put(sector, factorCoefficients(observations, sector, targetRegime));
        }
        return bySector;
    }

    private static Map<String, Map<String, FactorCoefficient>> interactionCoefficientsBySectorAndRegime(
            List<TrainingObservation> observations, List<StockBacktestResult> targetRows, String targetRegime) {
        if (observations == null || observations.isEmpty() || targetRows == null || targetRows.isEmpty()) {
            return Map.of();
        }
        Set<String> sectors = targetRows.stream()
                .map(row -> fallback(row.getSector()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Map<String, FactorCoefficient>> bySector = new LinkedHashMap<>();
        for (String sector : sectors) {
            bySector.put(sector, interactionCoefficients(observations, sector, targetRegime));
        }
        return bySector;
    }

    private static Map<String, Map<String, FactorCoefficient>> coefficientsBySector(
            List<TrainingObservation> observations, List<StockBacktestResult> targetRows) {
        if (observations == null || observations.isEmpty() || targetRows == null || targetRows.isEmpty()) {
            return Map.of();
        }
        Set<String> sectors = targetRows.stream()
                .map(row -> fallback(row.getSector()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Map<String, FactorCoefficient>> bySector = new LinkedHashMap<>();
        for (String sector : sectors) {
            bySector.put(sector, factorCoefficients(observations, sector));
        }
        return bySector;
    }

    private static Map<String, FactorCoefficient> factorCoefficients(List<TrainingObservation> observations,
            String targetSector) {
        return factorCoefficients(observations, targetSector, null);
    }

    private static Map<String, FactorCoefficient> factorCoefficients(List<TrainingObservation> observations,
            String targetSector, String targetRegime) {
        Map<String, FactorCoefficient> coefficients = new LinkedHashMap<>();
        List<TrainingObservation> sectorObservations = observations.stream()
                .filter(row -> Objects.equals(row.sector(), fallback(targetSector)))
                .toList();
        List<TrainingObservation> regimeObservations = targetRegime == null || targetRegime.isBlank()
                ? List.of()
                : observations.stream()
                        .filter(row -> Objects.equals(row.regimeLabel(), targetRegime))
                        .toList();
        List<TrainingObservation> sectorRegimeObservations = targetRegime == null || targetRegime.isBlank()
                ? List.of()
                : sectorObservations.stream()
                        .filter(row -> Objects.equals(row.regimeLabel(), targetRegime))
                        .toList();
        for (FactorSpec factor : FACTOR_SPECS) {
            FactorCoefficient global = coefficientForFactor(factor.name(), observations);
            FactorCoefficient sector = coefficientForFactor(factor.name(), sectorObservations);
            FactorCoefficient blended = targetRegime == null || targetRegime.isBlank()
                    ? blendCoefficient(global, sector)
                    : blendRegimeCoefficient(global,
                            sector,
                            coefficientForFactor(factor.name(), regimeObservations),
                            coefficientForFactor(factor.name(), sectorRegimeObservations));
            coefficients.put(factor.name(), blended);
        }
        return coefficients;
    }

    private static FactorCoefficient coefficientForFactor(String factor, List<TrainingObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return FactorCoefficient.missing();
        }
        List<Double> high = observations.stream()
                .filter(row -> row.exposures().getOrDefault(factor, Double.NaN) >= MIN_FACTOR_EXPOSURE)
                .map(TrainingObservation::excessReturnPct)
                .filter(Double::isFinite)
                .toList();
        List<Double> low = observations.stream()
                .filter(row -> row.exposures().getOrDefault(factor, Double.NaN) <= -MIN_FACTOR_EXPOSURE)
                .map(TrainingObservation::excessReturnPct)
                .filter(Double::isFinite)
                .toList();
        if (high.size() < 20 || low.size() < 20) {
            return FactorCoefficient.missing();
        }
        double spread = trimmedAverage(high) - trimmedAverage(low);
        double coefficient = clamp(spread / 2.0d, -2.5d, 2.5d);
        return new FactorCoefficient(coefficient, high.size() + low.size());
    }

    private static FactorCoefficient blendCoefficient(FactorCoefficient global, FactorCoefficient sector) {
        if (sector.sampleCount() >= 80 && global.sampleCount() >= 80) {
            return new FactorCoefficient(global.coefficient() * 0.70d + sector.coefficient() * 0.30d,
                    global.sampleCount() + sector.sampleCount());
        }
        return global.sampleCount() > 0 ? global : sector;
    }

    private static FactorCoefficient blendRegimeCoefficient(FactorCoefficient global, FactorCoefficient sector,
            FactorCoefficient regime, FactorCoefficient sectorRegime) {
        double weightedCoefficient = 0.0d;
        double weight = 0.0d;
        int sampleCount = 0;
        if (global.sampleCount() >= 80) {
            weightedCoefficient += global.coefficient() * 0.45d;
            weight += 0.45d;
            sampleCount += global.sampleCount();
        }
        if (sector.sampleCount() >= 80) {
            weightedCoefficient += sector.coefficient() * 0.20d;
            weight += 0.20d;
            sampleCount += sector.sampleCount();
        }
        if (regime.sampleCount() >= 80) {
            weightedCoefficient += regime.coefficient() * 0.25d;
            weight += 0.25d;
            sampleCount += regime.sampleCount();
        }
        if (sectorRegime.sampleCount() >= 45) {
            weightedCoefficient += sectorRegime.coefficient() * 0.10d;
            weight += 0.10d;
            sampleCount += sectorRegime.sampleCount();
        }
        if (weight <= 0.0d) {
            return blendCoefficient(global, sector);
        }
        return new FactorCoefficient(clamp(weightedCoefficient / weight, -2.5d, 2.5d), sampleCount);
    }

    private static Map<String, FactorCoefficient> interactionCoefficients(List<TrainingObservation> observations,
            String targetSector, String targetRegime) {
        Map<String, FactorCoefficient> coefficients = new LinkedHashMap<>();
        List<TrainingObservation> sectorObservations = observations.stream()
                .filter(row -> Objects.equals(row.sector(), fallback(targetSector)))
                .toList();
        List<TrainingObservation> regimeObservations = targetRegime == null || targetRegime.isBlank()
                ? List.of()
                : observations.stream()
                        .filter(row -> Objects.equals(row.regimeLabel(), targetRegime))
                        .toList();
        List<TrainingObservation> sectorRegimeObservations = targetRegime == null || targetRegime.isBlank()
                ? List.of()
                : sectorObservations.stream()
                        .filter(row -> Objects.equals(row.regimeLabel(), targetRegime))
                        .toList();
        for (InteractionSpec interaction : INTERACTION_SPECS) {
            FactorCoefficient global = coefficientForInteraction(interaction, observations);
            FactorCoefficient sector = coefficientForInteraction(interaction, sectorObservations);
            FactorCoefficient blended = targetRegime == null || targetRegime.isBlank()
                    ? blendInteractionCoefficient(global, sector)
                    : blendRegimeInteractionCoefficient(global,
                            sector,
                            coefficientForInteraction(interaction, regimeObservations),
                            coefficientForInteraction(interaction, sectorRegimeObservations));
            coefficients.put(interaction.name(), blended);
        }
        return coefficients;
    }

    private static FactorCoefficient coefficientForInteraction(InteractionSpec interaction,
            List<TrainingObservation> observations) {
        if (interaction == null || observations == null || observations.isEmpty()) {
            return FactorCoefficient.missing();
        }
        List<Double> high = observations.stream()
                .filter(row -> interactionExposure(interaction, row.exposures()) >= MIN_INTERACTION_EXPOSURE)
                .map(TrainingObservation::excessReturnPct)
                .filter(Double::isFinite)
                .toList();
        List<Double> low = observations.stream()
                .filter(row -> interactionExposure(interaction, row.exposures()) <= -MIN_INTERACTION_EXPOSURE)
                .map(TrainingObservation::excessReturnPct)
                .filter(Double::isFinite)
                .toList();
        if (high.size() < 30 || low.size() < 30) {
            return FactorCoefficient.missing();
        }
        double spread = trimmedAverage(high) - trimmedAverage(low);
        double coefficient = clamp(spread / 2.0d, -1.25d, 1.25d);
        return new FactorCoefficient(coefficient, high.size() + low.size());
    }

    private static FactorCoefficient blendInteractionCoefficient(FactorCoefficient global, FactorCoefficient sector) {
        if (sector.sampleCount() >= 100 && global.sampleCount() >= 100) {
            return new FactorCoefficient(global.coefficient() * 0.75d + sector.coefficient() * 0.25d,
                    global.sampleCount() + sector.sampleCount());
        }
        return global.sampleCount() > 0 ? global : sector;
    }

    private static FactorCoefficient blendRegimeInteractionCoefficient(FactorCoefficient global,
            FactorCoefficient sector, FactorCoefficient regime, FactorCoefficient sectorRegime) {
        double weightedCoefficient = 0.0d;
        double weight = 0.0d;
        int sampleCount = 0;
        if (global.sampleCount() >= 100) {
            weightedCoefficient += global.coefficient() * 0.55d;
            weight += 0.55d;
            sampleCount += global.sampleCount();
        }
        if (sector.sampleCount() >= 100) {
            weightedCoefficient += sector.coefficient() * 0.20d;
            weight += 0.20d;
            sampleCount += sector.sampleCount();
        }
        if (regime.sampleCount() >= 100) {
            weightedCoefficient += regime.coefficient() * 0.20d;
            weight += 0.20d;
            sampleCount += regime.sampleCount();
        }
        if (sectorRegime.sampleCount() >= 60) {
            weightedCoefficient += sectorRegime.coefficient() * 0.05d;
            weight += 0.05d;
            sampleCount += sectorRegime.sampleCount();
        }
        if (weight <= 0.0d) {
            return blendInteractionCoefficient(global, sector);
        }
        return new FactorCoefficient(clamp(weightedCoefficient / weight, -1.25d, 1.25d), sampleCount);
    }

    private static FactorAdjustedPrediction factorAdjustedPrediction(StockBacktestResult row,
            ExpectedReturnStats baseStats, Map<String, FactorCoefficient> coefficients) {
        Map<String, Double> exposures = factorExposures(row);
        double singleFactorCap = horizonAdjustmentCap(row.getHorizonDays()) / 3.0d;
        List<FactorContribution> contributions = new ArrayList<>();
        double adjustment = 0.0d;
        int sampleCountSum = 0;
        int coefficientCount = 0;
        for (FactorSpec factor : FACTOR_SPECS) {
            double exposure = exposures.getOrDefault(factor.name(), Double.NaN);
            FactorCoefficient coefficient = coefficients.getOrDefault(factor.name(), FactorCoefficient.missing());
            if (!Double.isFinite(exposure) || coefficient.sampleCount() <= 0) {
                continue;
            }
            double contribution = clamp(exposure * coefficient.coefficient(), -singleFactorCap, singleFactorCap);
            adjustment += contribution;
            sampleCountSum += coefficient.sampleCount();
            coefficientCount++;
            contributions.add(new FactorContribution(factor.name(), exposure, coefficient.coefficient(),
                    contribution, coefficient.sampleCount()));
        }
        adjustment = clamp(adjustment, -horizonAdjustmentCap(row.getHorizonDays()), horizonAdjustmentCap(row.getHorizonDays()));
        int averageCoefficientSamples = coefficientCount == 0 ? 0 : sampleCountSum / coefficientCount;
        ExpectedReturnStats adjusted = adjustExpectedReturnStats(baseStats, adjustment, row, averageCoefficientSamples);
        return new FactorAdjustedPrediction(adjusted, contributions);
    }

    private static FactorAdjustedPrediction nonlinearFactorAdjustedPrediction(StockBacktestResult row,
            ExpectedReturnStats baseStats, Map<String, FactorCoefficient> coefficients,
            Map<String, FactorCoefficient> interactionCoefficients) {
        FactorAdjustedPrediction linear = factorAdjustedPrediction(row, baseStats, coefficients);
        Map<String, Double> exposures = factorExposures(row);
        if (interactionCoefficients == null || interactionCoefficients.isEmpty() || exposures.isEmpty()) {
            return linear;
        }

        double singleInteractionCap = horizonAdjustmentCap(row.getHorizonDays()) / 5.0d;
        double maxInteractionAdjustment = horizonAdjustmentCap(row.getHorizonDays()) / 2.0d;
        List<FactorContribution> contributions = new ArrayList<>(linear.contributions());
        double adjustment = 0.0d;
        int sampleCountSum = 0;
        int coefficientCount = 0;
        for (InteractionSpec interaction : INTERACTION_SPECS) {
            double exposure = interactionExposure(interaction, exposures);
            FactorCoefficient coefficient = interactionCoefficients.getOrDefault(interaction.name(),
                    FactorCoefficient.missing());
            if (!Double.isFinite(exposure) || coefficient.sampleCount() <= 0) {
                continue;
            }
            double contribution = clamp(exposure * coefficient.coefficient(),
                    -singleInteractionCap, singleInteractionCap);
            adjustment += contribution;
            sampleCountSum += coefficient.sampleCount();
            coefficientCount++;
            contributions.add(new FactorContribution(interaction.name(), exposure, coefficient.coefficient(),
                    contribution, coefficient.sampleCount()));
        }
        if (coefficientCount == 0) {
            return linear;
        }
        adjustment = clamp(adjustment, -maxInteractionAdjustment, maxInteractionAdjustment);
        int averageCoefficientSamples = sampleCountSum / coefficientCount;
        ExpectedReturnStats adjusted = adjustExpectedReturnStats(linear.stats(), adjustment, row,
                averageCoefficientSamples);
        adjusted = conservativeNonlinearConfidence(adjusted);
        return new FactorAdjustedPrediction(adjusted, contributions);
    }

    private static ExpectedReturnStats conservativeNonlinearConfidence(ExpectedReturnStats stats) {
        int confidence = Math.max(0, Math.min(68, stats.confidence() - 6));
        return new ExpectedReturnStats(
                stats.expectedReturnPct(),
                stats.expectedExcessReturnPct(),
                stats.upsideProbabilityPct(),
                stats.calibratedUpsideProbabilityPct(),
                stats.downsideProbabilityPct(),
                stats.drawdownRiskPct(),
                stats.returnP10Pct(),
                stats.returnP50Pct(),
                stats.returnP90Pct(),
                stats.excessP10Pct(),
                stats.excessP50Pct(),
                stats.excessP90Pct(),
                confidence,
                stats.sampleCount(),
                stats.sectorSampleCount(),
                stats.scoreBucket(),
                stats.calibrationBucket(),
                stats.calibrationErrorPct());
    }

    private static double interactionExposure(InteractionSpec interaction, Map<String, Double> exposures) {
        if (interaction == null || exposures == null || exposures.isEmpty()) {
            return Double.NaN;
        }
        double left = exposures.getOrDefault(interaction.leftFactor(), Double.NaN);
        double right = exposures.getOrDefault(interaction.rightFactor(), Double.NaN);
        if (!Double.isFinite(left) || !Double.isFinite(right)) {
            return Double.NaN;
        }
        return clamp(left * right, -1.0d, 1.0d);
    }

    private static ExpectedReturnStats adjustExpectedReturnStats(ExpectedReturnStats base, double adjustment,
            StockBacktestResult row, int coefficientSamples) {
        double expectedReturn = clamp(finiteOrZero(base.expectedReturnPct()) + adjustment,
                -horizonExpectedReturnCap(row.getHorizonDays()), horizonExpectedReturnCap(row.getHorizonDays()));
        double expectedExcess = clamp(finiteOrZero(base.expectedExcessReturnPct()) + adjustment,
                -horizonExpectedExcessCap(row.getHorizonDays()), horizonExpectedExcessCap(row.getHorizonDays()));
        double upside = clamp(finiteOr(base.upsideProbabilityPct(), 50.0d) + adjustment * 3.5d, 1.0d, 99.0d);
        double calibratedUpside = clamp(finiteOr(base.calibratedUpsideProbabilityPct(), upside)
                + adjustment * 2.5d, 1.0d, 99.0d);
        double downside = clamp(finiteOr(base.downsideProbabilityPct(), 35.0d) - adjustment * 2.0d, 1.0d, 99.0d);
        double drawdownRisk = finiteOr(base.drawdownRiskPct(), Double.NaN);
        if (Double.isFinite(drawdownRisk)) {
            drawdownRisk += adjustment * 0.70d;
        }
        int confidence = base.confidence();
        if (coefficientSamples >= 500) {
            confidence += 6;
        } else if (coefficientSamples >= 200) {
            confidence += 3;
        }
        if (Math.abs(adjustment) >= 3.5d) {
            confidence -= 4;
        }
        if (row.getDataQualityScore() != null && row.getDataQualityScore() < 70) {
            confidence -= Math.min(8, (70 - row.getDataQualityScore()) / 5);
        }
        confidence = Math.max(0, Math.min(84, confidence));
        return new ExpectedReturnStats(
                expectedReturn,
                expectedExcess,
                upside,
                calibratedUpside,
                downside,
                drawdownRisk,
                shift(base.returnP10Pct(), adjustment),
                shift(base.returnP50Pct(), adjustment),
                shift(base.returnP90Pct(), adjustment),
                shift(base.excessP10Pct(), adjustment),
                shift(base.excessP50Pct(), adjustment),
                shift(base.excessP90Pct(), adjustment),
                confidence,
                base.sampleCount(),
                base.sectorSampleCount(),
                base.scoreBucket(),
                probabilityBucket(calibratedUpside),
                base.calibrationErrorPct());
    }

    private static Map<String, Integer> rawFactorScores(StockBacktestResult row) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (FactorSpec factor : FACTOR_SPECS) {
            Integer score = factor.scoreAccessor().apply(row);
            if (score != null) {
                scores.put(factor.name(), score);
            }
        }
        return scores;
    }

    private static Map<String, Double> factorExposures(StockBacktestResult row) {
        if (row == null) {
            return Map.of();
        }
        double qualityMultiplier = row.getDataQualityScore() == null
                ? 0.85d
                : clamp(row.getDataQualityScore() / 100.0d, 0.35d, 1.0d);
        Map<String, Double> exposures = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : rawFactorScores(row).entrySet()) {
            double exposure = clamp((entry.getValue() - 50.0d) / 50.0d, -1.0d, 1.0d);
            exposures.put(entry.getKey(), exposure * qualityMultiplier);
        }
        return exposures;
    }

    private static double horizonAdjustmentCap(Integer horizonDays) {
        if (horizonDays == null) {
            return 3.0d;
        }
        if (horizonDays <= 5) {
            return 1.5d;
        }
        if (horizonDays <= 20) {
            return 3.0d;
        }
        return 5.0d;
    }

    private static double horizonExpectedExcessCap(Integer horizonDays) {
        if (horizonDays == null) {
            return 8.0d;
        }
        if (horizonDays <= 5) {
            return 4.0d;
        }
        if (horizonDays <= 20) {
            return 8.0d;
        }
        return 12.0d;
    }

    private static double horizonExpectedReturnCap(Integer horizonDays) {
        if (horizonDays == null) {
            return 15.0d;
        }
        if (horizonDays <= 5) {
            return 8.0d;
        }
        if (horizonDays <= 20) {
            return 15.0d;
        }
        return 25.0d;
    }

    private static List<TrainingObservation> scoreBand(List<TrainingObservation> observations, int score,
            int scoreRadius, int limit) {
        return observations.stream()
                .filter(observation -> Math.abs(observation.score() - score) <= scoreRadius)
                .sorted(Comparator.comparingInt(observation -> Math.abs(observation.score() - score)))
                .limit(limit)
                .toList();
    }

    private static List<TrainingObservation> nearestByScore(List<TrainingObservation> observations, int score,
            int limit) {
        return observations.stream()
                .sorted(Comparator.comparingInt(observation -> Math.abs(observation.score() - score)))
                .limit(limit)
                .toList();
    }

    private static ObservedStats observedStats(List<TrainingObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return ObservedStats.missing();
        }
        double expectedReturn = observations.stream()
                .mapToDouble(TrainingObservation::forwardReturnPct)
                .average()
                .orElse(Double.NaN);
        double expectedExcess = observations.stream()
                .mapToDouble(TrainingObservation::excessReturnPct)
                .average()
                .orElse(Double.NaN);
        double[] forwardReturns = observations.stream()
                .mapToDouble(TrainingObservation::forwardReturnPct)
                .sorted()
                .toArray();
        double[] excessReturns = observations.stream()
                .mapToDouble(TrainingObservation::excessReturnPct)
                .sorted()
                .toArray();
        double upsideProbability = observations.stream()
                .filter(observation -> observation.excessReturnPct() > 0)
                .count() * 100.0d / observations.size();
        double downsideProbability = observations.stream()
                .filter(observation -> observation.forwardReturnPct() <= EXPECTED_RETURN_DOWNSIDE_THRESHOLD_PCT)
                .count() * 100.0d / observations.size();
        double drawdownRisk = percentile(forwardReturns, 0.10d);
        return new ObservedStats(expectedReturn, expectedExcess, upsideProbability,
                downsideProbability, drawdownRisk,
                percentile(forwardReturns, 0.10d),
                percentile(forwardReturns, 0.50d),
                percentile(forwardReturns, 0.90d),
                percentile(excessReturns, 0.10d),
                percentile(excessReturns, 0.50d),
                percentile(excessReturns, 0.90d),
                observations.size());
    }

    private static double weightedAverage(double first, double firstWeight, double second, double secondWeight,
            double third, double thirdWeight) {
        double sum = 0;
        double weight = 0;
        if (Double.isFinite(first) && firstWeight > 0) {
            sum += first * firstWeight;
            weight += firstWeight;
        }
        if (Double.isFinite(second) && secondWeight > 0) {
            sum += second * secondWeight;
            weight += secondWeight;
        }
        if (Double.isFinite(third) && thirdWeight > 0) {
            sum += third * thirdWeight;
            weight += thirdWeight;
        }
        return weight <= 0 ? Double.NaN : sum / weight;
    }

    private static double percentile(double[] sortedValues, double percentile) {
        if (sortedValues.length == 0) {
            return Double.NaN;
        }
        double index = Math.max(0, Math.min(sortedValues.length - 1, percentile * (sortedValues.length - 1)));
        int low = (int) Math.floor(index);
        int high = (int) Math.ceil(index);
        if (low == high) {
            return sortedValues[low];
        }
        double fraction = index - low;
        return sortedValues[low] * (1.0d - fraction) + sortedValues[high] * fraction;
    }

    private static int expectedReturnConfidence(int sampleCount, int sectorSampleCount, StockBacktestResult row) {
        int confidence = 20;
        confidence += Math.min(30, sampleCount / 12);
        confidence += Math.min(16, sectorSampleCount / 5);
        if (row.getDataQualityScore() != null) {
            confidence += Math.round((row.getDataQualityScore() - 50) * 0.14f);
        }
        if (row.getIntegratedScore() != null) {
            confidence += Math.min(5, Math.abs(row.getIntegratedScore() - 50) / 8);
        }
        return Math.max(0, Math.min(88, confidence));
    }

    private static double calibratedUpsideProbability(double rawUpsideProbability, double baseHitRate, int confidence) {
        if (!Double.isFinite(rawUpsideProbability)) {
            return Double.NaN;
        }
        double anchor = Double.isFinite(baseHitRate) ? baseHitRate : 50.0d;
        double strength = 0.35d + Math.max(0, Math.min(88, confidence)) / 100.0d * 0.35d;
        return Math.max(1.0d, Math.min(99.0d, anchor + (rawUpsideProbability - anchor) * strength));
    }

    private static int probabilityBucket(double probabilityPct) {
        if (!Double.isFinite(probabilityPct)) {
            return -1;
        }
        return Math.max(0, Math.min(100, (int) Math.floor(probabilityPct / 10.0d) * 10));
    }

    private static int scoreBucket(int score) {
        return Math.max(0, Math.min(100, (score / 10) * 10));
    }

    private static List<StockExpectedReturnCalibration> expectedReturnCalibrations(String indexCode,
            String modelVersion, List<PredictionEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return List.of();
        }
        Map<CalibrationKey, List<PredictionEvaluation>> grouped = evaluations.stream()
                .filter(row -> Double.isFinite(row.predictedUpsidePct()))
                .collect(Collectors.groupingBy(row -> new CalibrationKey(row.horizonDays(),
                        probabilityBucket(row.predictedUpsidePct())), LinkedHashMap::new, Collectors.toList()));
        List<StockExpectedReturnCalibration> rows = new ArrayList<>();
        for (Map.Entry<CalibrationKey, List<PredictionEvaluation>> entry : grouped.entrySet()) {
            List<PredictionEvaluation> bucketRows = entry.getValue();
            if (bucketRows.size() < MIN_EXPECTED_RETURN_CALIBRATION_BUCKET_ROWS) {
                continue;
            }
            double avgPredicted = bucketRows.stream()
                    .mapToDouble(PredictionEvaluation::predictedUpsidePct)
                    .average()
                    .orElse(Double.NaN);
            double actualRate = bucketRows.stream()
                    .filter(PredictionEvaluation::actualUpside)
                    .count() * 100.0d / bucketRows.size();
            double brier = bucketRows.stream()
                    .mapToDouble(row -> {
                        double probability = row.predictedUpsidePct() / 100.0d;
                        double actual = row.actualUpside() ? 1.0d : 0.0d;
                        double error = probability - actual;
                        return error * error;
                    })
                    .average()
                    .orElse(Double.NaN);
            StockExpectedReturnCalibration calibration = new StockExpectedReturnCalibration();
            calibration.setIndexCode(indexCode);
            calibration.setModelVersion(modelVersion);
            calibration.setHorizonDays(entry.getKey().horizonDays());
            calibration.setProbabilityBucket(entry.getKey().probabilityBucket());
            calibration.setSampleCount(bucketRows.size());
            calibration.setAveragePredictedUpsidePct(decimalOrNull(avgPredicted));
            calibration.setActualUpsideRatePct(decimalOrNull(actualRate));
            calibration.setCalibrationErrorPct(decimalOrNull(actualRate - avgPredicted));
            calibration.setBrierScore(decimalOrNull(brier));
            calibration.setSource("HISTORICAL_PREDICTION_VALIDATION");
            rows.add(calibration);
        }
        return rows.stream()
                .sorted(Comparator
                        .comparing(StockExpectedReturnCalibration::getHorizonDays)
                        .thenComparing(StockExpectedReturnCalibration::getProbabilityBucket))
                .toList();
    }

    private static List<StockBenchmarkReturn> buildBenchmarkReturns(String indexCode, List<StockMarketSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        Map<String, List<StockMarketSnapshot>> bySymbol = snapshots.stream()
                .filter(row -> row.getSymbol() != null
                        && row.getSnapshotDate() != null
                        && positive(row.getClosePrice()))
                .collect(Collectors.groupingBy(StockMarketSnapshot::getSymbol, LinkedHashMap::new, Collectors.toList()));

        Map<LocalDate, BenchmarkAccumulator> byDate = new LinkedHashMap<>();
        for (List<StockMarketSnapshot> rows : bySymbol.values()) {
            List<StockMarketSnapshot> sorted = rows.stream()
                    .sorted(Comparator.comparing(StockMarketSnapshot::getSnapshotDate))
                    .toList();
            StockMarketSnapshot previous = null;
            for (StockMarketSnapshot current : sorted) {
                byDate.computeIfAbsent(current.getSnapshotDate(), ignored -> new BenchmarkAccumulator())
                        .addConstituent(current);
                if (previous != null
                        && sameCurrency(previous, current)
                        && positive(previous.getClosePrice())
                        && positive(current.getClosePrice())) {
                    double previousMarketCap = number(previous.getMarketCapUsd());
                    if (previousMarketCap > 0 && Double.isFinite(previousMarketCap)) {
                        double stockReturn = current.getClosePrice().doubleValue()
                                / previous.getClosePrice().doubleValue() - 1.0d;
                        byDate.computeIfAbsent(current.getSnapshotDate(), ignored -> new BenchmarkAccumulator())
                                .addReturn(previousMarketCap, stockReturn);
                    }
                }
                previous = current;
            }
        }

        double level = 1000.0d;
        List<StockBenchmarkReturn> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, BenchmarkAccumulator> entry : byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            BenchmarkAccumulator accumulator = entry.getValue();
            if (accumulator.coverageCount() < 30 || accumulator.totalWeight() <= 0) {
                continue;
            }
            double returnPct = accumulator.weightedReturn() / accumulator.totalWeight() * 100.0d;
            level *= 1.0d + returnPct / 100.0d;
            StockBenchmarkReturn row = new StockBenchmarkReturn();
            row.setIndexCode(indexCode);
            row.setTradeDate(entry.getKey());
            row.setBenchmarkLevel(BigDecimal.valueOf(level));
            row.setReturnPct(BigDecimal.valueOf(returnPct));
            row.setTotalMarketCapUsd(BigDecimal.valueOf(accumulator.totalMarketCapUsd()));
            row.setConstituentCount(accumulator.constituentCount());
            row.setCoverageCount(accumulator.coverageCount());
            row.setSource("MARKET_SNAPSHOT_WEIGHTED");
            rows.add(row);
        }
        return rows;
    }

    private static boolean sameCurrency(StockMarketSnapshot previous, StockMarketSnapshot current) {
        String left = previous.getCurrency() == null ? "USD" : previous.getCurrency();
        String right = current.getCurrency() == null ? "USD" : current.getCurrency();
        return Objects.equals(left, right);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static double number(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    private static int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static StrategyRun runStrategy(StrategyConfig config, List<StockBacktestResult> allResults,
            RiskLookup riskLookup, CovarianceLookup covarianceLookup, ExpectedReturnLookup expectedReturnLookup,
            RiskFreeRateLookup riskFreeRateLookup) {
        Map<LocalDate, List<StockBacktestResult>> byDate = allResults.stream()
                .filter(result -> Objects.equals(result.getHorizonDays(), config.horizonDays()))
                .collect(Collectors.groupingBy(StockBacktestResult::getSignalDate, LinkedHashMap::new,
                        Collectors.toList()));

        List<PortfolioPeriod> periods = new ArrayList<>();
        Map<String, Double> previousWeights = Map.of();

        for (Map.Entry<LocalDate, List<StockBacktestResult>> entry : byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            List<StockBacktestResult> universe = entry.getValue().stream()
                    .filter(StockPortfolioBacktestService::usable)
                    .toList();
            if (universe.size() < config.topCount()) {
                continue;
            }

            List<StockBacktestResult> selected = selectPositions(config, entry.getKey(), universe, riskLookup,
                    expectedReturnLookup);
            Map<String, RiskStats> riskStatsBySymbol = riskStatsBySymbol(selected, riskLookup, entry.getKey());
            Map<String, ExpectedReturnStats> expectedStatsBySymbol = expectedStatsBySymbol(selected,
                    expectedReturnLookup, entry.getKey(), config.horizonDays());
            Map<String, Double> benchmarkSectorWeights = benchmarkSectorWeights(universe);
            Map<String, Double> weights = config.riskMode().optimized()
                    ? optimizedWeights(selected, riskStatsBySymbol, expectedStatsBySymbol, previousWeights, config,
                            covarianceLookup, entry.getKey(), benchmarkSectorWeights)
                    : weights(selected, config.weighting());
            if (config.riskMode().controlled() && !config.riskMode().optimized()) {
                weights = applyRiskWeightCaps(selected, weights, config);
            }
            if (weights.isEmpty()) {
                continue;
            }

            double turnover = oneWayTurnover(previousWeights, weights);
            double riskFreeAnnualRate = riskFreeRateLookup.annualRate(entry.getKey());
            double riskFreePeriodReturn = riskFreeRateLookup.periodReturnPct(entry.getKey(), config.horizonDays());
            double cashReturn = cashWeight(weights) * riskFreePeriodReturn;
            double grossReturn = weightedReturn(selected, weights) + cashReturn;
            double benchmarkReturn = benchmarkReturn(universe);
            Map<String, Double> sectorWeights = sectorWeights(selected, weights);
            double activeSectorDeviation = activeSectorDeviation(sectorWeights, benchmarkSectorWeights);
            double transactionCost = transactionCostPct(previousWeights, weights, riskStatsBySymbol);
            double netReturn = grossReturn - transactionCost;
            double portfolioBeta = weightedRiskMetric(selected, weights, riskLookup, entry.getKey(), RiskField.BETA);
            double portfolioVolatility = weightedRiskMetric(selected, weights, riskLookup, entry.getKey(),
                    RiskField.VOLATILITY);
            double portfolioLiquidity = weightedRiskMetric(selected, weights, riskLookup, entry.getKey(),
                    RiskField.LIQUIDITY);

            periods.add(new PortfolioPeriod(entry.getKey(), selected, weights, riskStatsBySymbol,
                    expectedStatsBySymbol, sectorWeights, benchmarkSectorWeights,
                    grossReturn, netReturn, benchmarkReturn, turnover, transactionCost,
                    riskFreeAnnualRate, riskFreePeriodReturn, cashReturn,
                    portfolioBeta, portfolioVolatility, portfolioLiquidity, activeSectorDeviation));
            previousWeights = weights;
        }

        return new StrategyRun(config, periods);
    }

    private static List<StockBacktestResult> selectPositions(StrategyConfig config, LocalDate signalDate,
            List<StockBacktestResult> universe, RiskLookup riskLookup, ExpectedReturnLookup expectedReturnLookup) {
        Comparator<StockBacktestResult> baseOrder = Comparator
                .comparing(StockPortfolioBacktestService::score, Comparator.reverseOrder())
                .thenComparing(StockBacktestResult::getSymbol);
        if (!config.riskMode().controlled()) {
            return universe.stream()
                    .sorted(baseOrder)
                    .limit(config.topCount())
                    .toList();
        }

        int candidateLimit = Math.min(universe.size(), Math.max(config.topCount() * 4, config.topCount() + 30));
        List<StockBacktestResult> candidates = universe.stream()
                .sorted(Comparator
                        .comparing((StockBacktestResult result) -> riskAdjustedScore(result, signalDate, riskLookup,
                                        expectedReturnLookup, config.horizonDays()),
                                Comparator.reverseOrder())
                        .thenComparing(baseOrder))
                .limit(candidateLimit)
                .toList();

        List<StockBacktestResult> selected = new ArrayList<>();
        Map<String, Integer> sectorCounts = new HashMap<>();
        int sectorNameCap = Math.max(1, (int) Math.ceil(config.topCount() * RISK_SECTOR_CAP));
        for (StockBacktestResult candidate : candidates) {
            String sector = fallback(candidate.getSector());
            if (sectorCounts.getOrDefault(sector, 0) >= sectorNameCap) {
                continue;
            }
            selected.add(candidate);
            sectorCounts.merge(sector, 1, Integer::sum);
            if (selected.size() == config.topCount()) {
                return selected;
            }
        }

        Set<String> selectedSymbols = selected.stream()
                .map(StockBacktestResult::getSymbol)
                .collect(Collectors.toCollection(HashSet::new));
        for (StockBacktestResult candidate : candidates) {
            if (selectedSymbols.add(candidate.getSymbol())) {
                selected.add(candidate);
            }
            if (selected.size() == config.topCount()) {
                return selected;
            }
        }
        return selected;
    }

    private static double riskAdjustedScore(StockBacktestResult result, LocalDate signalDate, RiskLookup riskLookup,
            ExpectedReturnLookup expectedReturnLookup, int horizonDays) {
        double adjusted = score(result);
        ExpectedReturnStats expected = expectedReturnLookup.stats(result.getSymbol(), signalDate, horizonDays);
        if (Double.isFinite(expected.expectedExcessReturnPct())) {
            adjusted += Math.max(-10.0d, Math.min(12.0d, expected.expectedExcessReturnPct() * 2.5d));
        }
        double upsideProbability = displayUpsideProbability(expected);
        if (Double.isFinite(upsideProbability)) {
            adjusted += Math.max(-5.0d, Math.min(5.0d, (upsideProbability - 50.0d) * 0.12d));
        }
        RiskStats stats = riskLookup.stats(result.getSymbol(), signalDate);
        if (Double.isFinite(stats.volatilityPct())) {
            if (stats.volatilityPct() > 45.0d) {
                adjusted -= Math.min(20.0d, (stats.volatilityPct() - 45.0d) * 0.35d);
            } else if (stats.volatilityPct() < 22.0d) {
                adjusted += Math.min(3.0d, (22.0d - stats.volatilityPct()) * 0.10d);
            }
        } else {
            adjusted -= 4.0d;
        }

        if (Double.isFinite(stats.beta())) {
            if (stats.beta() > 1.35d) {
                adjusted -= Math.min(12.0d, (stats.beta() - 1.35d) * 12.0d);
            } else if (stats.beta() < 0.45d) {
                adjusted -= Math.min(4.0d, (0.45d - stats.beta()) * 4.0d);
            }
        }

        if (Double.isFinite(stats.avgDollarVolume())) {
            if (stats.avgDollarVolume() < 5_000_000.0d) {
                adjusted -= 15.0d;
            } else if (stats.avgDollarVolume() < 20_000_000.0d) {
                adjusted -= 7.0d;
            } else if (stats.avgDollarVolume() > 100_000_000.0d) {
                adjusted += 2.0d;
            }
        } else {
            adjusted -= 5.0d;
        }
        return adjusted;
    }

    private static boolean usable(StockBacktestResult result) {
        return result.getSignalDate() != null
                && result.getSymbol() != null
                && result.getHorizonDays() != null
                && result.getForwardReturnPct() != null
                && result.getIntegratedScore() != null;
    }

    private static Map<String, Double> weights(List<StockBacktestResult> selected, Weighting weighting) {
        return switch (weighting) {
            case EQUAL -> equalWeights(selected);
            case SIGNAL -> scoreWeights(selected);
            case MARKET_CAP_ADJUSTED -> marketCapAdjustedWeights(selected);
        };
    }

    private static Map<String, Double> equalWeights(List<StockBacktestResult> selected) {
        if (selected.isEmpty()) {
            return Map.of();
        }
        double weight = 1.0d / selected.size();
        return selected.stream()
                .collect(Collectors.toMap(StockBacktestResult::getSymbol, ignored -> weight,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static Map<String, Double> scoreWeights(List<StockBacktestResult> selected) {
        return normalize(selected, result -> Math.max(1.0d, score(result)));
    }

    private static Map<String, Double> marketCapAdjustedWeights(List<StockBacktestResult> selected) {
        Map<String, Double> weights = normalize(selected, result -> {
            BigDecimal marketCap = result.getMarketCap();
            if (marketCap == null || marketCap.signum() <= 0) {
                return Math.max(1.0d, score(result));
            }
            return Math.sqrt(marketCap.doubleValue()) * Math.max(1.0d, score(result));
        });
        return weights.isEmpty() ? scoreWeights(selected) : weights;
    }

    private static Map<String, Double> optimizedWeights(List<StockBacktestResult> selected,
            Map<String, RiskStats> riskStatsBySymbol, Map<String, ExpectedReturnStats> expectedStatsBySymbol,
            Map<String, Double> previousWeights, StrategyConfig config, CovarianceLookup covarianceLookup,
            LocalDate signalDate, Map<String, Double> benchmarkSectorWeights) {
        Map<String, Double> raw = optimizerAlphaScores(selected, riskStatsBySymbol, expectedStatsBySymbol,
                previousWeights);

        Map<String, Double> weights = normalizeRaw(raw);
        weights = applyRiskWeightCaps(selected, weights, config);
        weights = tiltTowardTargetBeta(selected, weights, riskStatsBySymbol, config);
        if (config.riskMode().covarianceAware()) {
            weights = reduceCorrelationCrowding(selected, weights, riskStatsBySymbol, covarianceLookup, signalDate, config);
        }
        if (config.riskMode().quadraticOptimizer()) {
            weights = quadraticConstraintOptimizedWeights(selected, weights, raw, riskStatsBySymbol, previousWeights,
                    config, covarianceLookup, signalDate, benchmarkSectorWeights);
        } else if (config.riskMode().explicitOptimizer()) {
            weights = explicitConstraintOptimizedWeights(selected, weights, raw, riskStatsBySymbol, previousWeights,
                    config, covarianceLookup, signalDate, benchmarkSectorWeights);
        }
        return applyRiskWeightCaps(selected, weights, config);
    }

    private static Map<String, Double> optimizerAlphaScores(List<StockBacktestResult> selected,
            Map<String, RiskStats> riskStatsBySymbol, Map<String, ExpectedReturnStats> expectedStatsBySymbol,
            Map<String, Double> previousWeights) {
        Map<String, Double> raw = new LinkedHashMap<>();
        for (StockBacktestResult result : selected) {
            RiskStats stats = riskStatsBySymbol.getOrDefault(result.getSymbol(), RiskStats.missing());
            ExpectedReturnStats expected = expectedStatsBySymbol.getOrDefault(result.getSymbol(),
                    ExpectedReturnStats.missing());
            double signalAlpha = Math.max(1.0d, score(result) - 35.0d) * expectedAlphaMultiplier(expected);
            double betaPenalty = betaPenalty(stats.beta());
            double volatilityPenalty = volatilityPenalty(stats.volatilityPct());
            double liquidityBoost = liquidityBoost(stats.avgDollarVolume());
            double costPenalty = 1.0d + effectiveTradeCostPct(stats) * 2.0d;
            double incumbentBoost = previousWeights.containsKey(result.getSymbol())
                    ? 1.0d + Math.min(0.35d, previousWeights.getOrDefault(result.getSymbol(), 0.0d) * 4.0d)
                    : 0.95d;
            double rawWeight = signalAlpha * liquidityBoost * incumbentBoost
                    / Math.max(0.25d, betaPenalty * volatilityPenalty * costPenalty);
            raw.put(result.getSymbol(), rawWeight);
        }
        return raw;
    }

    private static Map<String, Double> explicitConstraintOptimizedWeights(List<StockBacktestResult> selected,
            Map<String, Double> inputWeights, Map<String, Double> alphaScores,
            Map<String, RiskStats> riskStatsBySymbol, Map<String, Double> previousWeights,
            StrategyConfig config, CovarianceLookup covarianceLookup, LocalDate signalDate,
            Map<String, Double> benchmarkSectorWeights) {
        if (selected.isEmpty() || inputWeights.isEmpty()) {
            return inputWeights;
        }
        Map<String, Double> weights = applyTurnoverBudget(
                applyRiskWeightCaps(selected, inputWeights, config), previousWeights, selected, config);
        double bestObjective = portfolioObjective(selected, weights, alphaScores, riskStatsBySymbol,
                previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights);
        for (int iteration = 0; iteration < OPTIMIZER_V3_MAX_ITERATIONS; iteration++) {
            Map<String, Double> bestCandidate = null;
            double candidateObjective = bestObjective;
            List<StockBacktestResult> sourceCandidates = v3SourceCandidates(selected, weights, alphaScores,
                    riskStatsBySymbol, covarianceLookup, signalDate);
            List<StockBacktestResult> targetCandidates = v3TargetCandidates(selected, weights, alphaScores,
                    riskStatsBySymbol, covarianceLookup, signalDate, config);
            if (sourceCandidates.isEmpty() || targetCandidates.isEmpty()) {
                break;
            }
            for (StockBacktestResult source : sourceCandidates) {
                String sourceSymbol = source.getSymbol();
                double sourceWeight = weights.getOrDefault(sourceSymbol, 0.0d);
                if (sourceWeight <= OPTIMIZER_MIN_POSITION_WEIGHT + 0.0001d) {
                    continue;
                }
                for (StockBacktestResult target : targetCandidates) {
                    String targetSymbol = target.getSymbol();
                    if (Objects.equals(sourceSymbol, targetSymbol)) {
                        continue;
                    }
                    double capacity = availableRiskCapacity(target, selected, weights, config);
                    if (capacity <= 0.0001d) {
                        continue;
                    }
                    double shift = Math.min(OPTIMIZER_V3_SHIFT_STEP,
                            Math.min(sourceWeight - OPTIMIZER_MIN_POSITION_WEIGHT, capacity));
                    if (shift <= 0.0001d) {
                        continue;
                    }
                    Map<String, Double> candidate = new LinkedHashMap<>(weights);
                    candidate.merge(sourceSymbol, -shift, Double::sum);
                    candidate.merge(targetSymbol, shift, Double::sum);
                    candidate = applyRiskWeightCaps(selected, candidate, config);
                    candidate = applyTurnoverBudget(candidate, previousWeights, selected, config);
                    double objective = portfolioObjective(selected, candidate, alphaScores, riskStatsBySymbol,
                            previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights);
                    if (objective < candidateObjective - 0.000001d) {
                        candidateObjective = objective;
                        bestCandidate = candidate;
                    }
                }
            }
            if (bestCandidate == null) {
                break;
            }
            weights = bestCandidate;
            bestObjective = candidateObjective;
        }
        return applyRiskWeightCaps(selected, weights, config).entrySet().stream()
                .filter(entry -> entry.getValue() > 0.000001d)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static Map<String, Double> quadraticConstraintOptimizedWeights(List<StockBacktestResult> selected,
            Map<String, Double> inputWeights, Map<String, Double> alphaScores,
            Map<String, RiskStats> riskStatsBySymbol, Map<String, Double> previousWeights,
            StrategyConfig config, CovarianceLookup covarianceLookup, LocalDate signalDate,
            Map<String, Double> benchmarkSectorWeights) {
        if (selected.isEmpty() || inputWeights.isEmpty()) {
            return inputWeights;
        }
        if (config.riskMode() == RiskMode.OPTIMIZED_V5) {
            return quadraticMultiStartOptimizedWeights(selected, inputWeights, alphaScores, riskStatsBySymbol,
                    previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights);
        }
        Map<String, Double> weights = projectOptimizerWeights(selected, inputWeights, previousWeights, config);
        double bestObjective = quadraticPortfolioObjective(selected, weights, alphaScores, riskStatsBySymbol,
                previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights);
        double step = OPTIMIZER_V4_INITIAL_STEP;
        for (int iteration = 0; iteration < OPTIMIZER_V4_MAX_ITERATIONS; iteration++) {
            Map<String, Double> gradient = quadraticObjectiveGradient(selected, weights, alphaScores,
                    riskStatsBySymbol, previousWeights, config, covarianceLookup, signalDate);
            if (gradient.isEmpty()) {
                break;
            }
            Map<String, Double> candidate = new LinkedHashMap<>(weights);
            for (StockBacktestResult result : selected) {
                String symbol = result.getSymbol();
                double current = candidate.getOrDefault(symbol, 0.0d);
                double next = current - step * gradient.getOrDefault(symbol, 0.0d);
                if (next > 0.000001d) {
                    candidate.put(symbol, next);
                } else {
                    candidate.remove(symbol);
                }
            }
            candidate = projectOptimizerWeights(selected, candidate, previousWeights, config);
            double objective = quadraticPortfolioObjective(selected, candidate, alphaScores, riskStatsBySymbol,
                    previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights);
            if (objective < bestObjective - 0.000001d) {
                weights = candidate;
                bestObjective = objective;
                step = Math.min(0.035d, step * 1.04d);
            } else {
                step *= 0.55d;
                if (step < 0.0005d) {
                    break;
                }
            }
        }
        return projectOptimizerWeights(selected, weights, previousWeights, config).entrySet().stream()
                .filter(entry -> entry.getValue() > 0.000001d)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static Map<String, Double> quadraticMultiStartOptimizedWeights(List<StockBacktestResult> selected,
            Map<String, Double> inputWeights, Map<String, Double> alphaScores,
            Map<String, RiskStats> riskStatsBySymbol, Map<String, Double> previousWeights,
            StrategyConfig config, CovarianceLookup covarianceLookup, LocalDate signalDate,
            Map<String, Double> benchmarkSectorWeights) {
        Map<String, Double> bestWeights = Map.of();
        double bestObjective = Double.POSITIVE_INFINITY;
        for (Map<String, Double> seed : optimizerV5Seeds(selected, inputWeights, alphaScores, riskStatsBySymbol,
                previousWeights, config, benchmarkSectorWeights)) {
            Map<String, Double> candidate = quadraticProjectedGradientSearch(selected, seed, alphaScores,
                    riskStatsBySymbol, previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights,
                    OPTIMIZER_V5_MAX_ITERATIONS, OPTIMIZER_V5_INITIAL_STEP, true);
            double objective = optimizerV5PortfolioObjective(selected, candidate, alphaScores, riskStatsBySymbol,
                    previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights);
            if (objective < bestObjective) {
                bestObjective = objective;
                bestWeights = candidate;
            }
        }
        if (bestWeights.isEmpty()) {
            bestWeights = projectOptimizerWeights(selected, inputWeights, previousWeights, config);
        }
        return bestWeights.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.000001d)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static Map<String, Double> quadraticProjectedGradientSearch(List<StockBacktestResult> selected,
            Map<String, Double> seedWeights, Map<String, Double> alphaScores,
            Map<String, RiskStats> riskStatsBySymbol, Map<String, Double> previousWeights,
            StrategyConfig config, CovarianceLookup covarianceLookup, LocalDate signalDate,
            Map<String, Double> benchmarkSectorWeights, int maxIterations, double initialStep,
            boolean strictProjection) {
        Map<String, Double> weights = projectOptimizerWeights(selected, seedWeights, previousWeights, config);
        if (strictProjection) {
            weights = reduceActiveSectorDeviation(selected, weights, previousWeights, config, benchmarkSectorWeights);
        }
        double bestObjective = strictProjection
                ? optimizerV5PortfolioObjective(selected, weights, alphaScores, riskStatsBySymbol, previousWeights,
                        config, covarianceLookup, signalDate, benchmarkSectorWeights)
                : quadraticPortfolioObjective(selected, weights, alphaScores, riskStatsBySymbol, previousWeights,
                        config, covarianceLookup, signalDate, benchmarkSectorWeights);
        double step = initialStep;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Map<String, Double> gradient = quadraticObjectiveGradient(selected, weights, alphaScores,
                    riskStatsBySymbol, previousWeights, config, covarianceLookup, signalDate);
            if (gradient.isEmpty()) {
                break;
            }
            Map<String, Double> candidate = new LinkedHashMap<>(weights);
            for (StockBacktestResult result : selected) {
                String symbol = result.getSymbol();
                double current = candidate.getOrDefault(symbol, 0.0d);
                double next = current - step * gradient.getOrDefault(symbol, 0.0d);
                if (next > 0.000001d) {
                    candidate.put(symbol, next);
                } else {
                    candidate.remove(symbol);
                }
            }
            candidate = projectOptimizerWeights(selected, candidate, previousWeights, config);
            if (strictProjection) {
                candidate = reduceActiveSectorDeviation(selected, candidate, previousWeights, config,
                        benchmarkSectorWeights);
            }
            double objective = strictProjection
                    ? optimizerV5PortfolioObjective(selected, candidate, alphaScores, riskStatsBySymbol,
                            previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights)
                    : quadraticPortfolioObjective(selected, candidate, alphaScores, riskStatsBySymbol,
                            previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights);
            if (objective < bestObjective - 0.000001d) {
                weights = candidate;
                bestObjective = objective;
                step = Math.min(strictProjection ? 0.028d : 0.035d, step * 1.04d);
            } else {
                step *= 0.55d;
                if (step < 0.00035d) {
                    break;
                }
            }
        }
        return projectOptimizerWeights(selected, weights, previousWeights, config);
    }

    private static List<Map<String, Double>> optimizerV5Seeds(List<StockBacktestResult> selected,
            Map<String, Double> inputWeights, Map<String, Double> alphaScores,
            Map<String, RiskStats> riskStatsBySymbol, Map<String, Double> previousWeights,
            StrategyConfig config, Map<String, Double> benchmarkSectorWeights) {
        List<Map<String, Double>> seeds = new ArrayList<>();
        addOptimizerSeed(seeds, inputWeights);
        addOptimizerSeed(seeds, scoreWeights(selected));
        addOptimizerSeed(seeds, lowRiskAlphaSeed(selected, alphaScores, riskStatsBySymbol));
        addOptimizerSeed(seeds, sectorBalancedSeed(selected, alphaScores, riskStatsBySymbol, benchmarkSectorWeights));
        Map<String, Double> previousSelected = previousWeights.entrySet().stream()
                .filter(entry -> selected.stream().anyMatch(result -> Objects.equals(result.getSymbol(), entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
        if (!previousSelected.isEmpty()) {
            addOptimizerSeed(seeds, applyRiskWeightCaps(selected, previousSelected, config));
        }
        return seeds;
    }

    private static void addOptimizerSeed(List<Map<String, Double>> seeds, Map<String, Double> seed) {
        if (seed == null || seed.isEmpty()) {
            return;
        }
        Map<String, Double> normalized = normalizeRaw(seed);
        if (normalized.isEmpty()) {
            return;
        }
        boolean duplicate = seeds.stream().anyMatch(existing -> sameOptimizerSeed(existing, normalized));
        if (!duplicate) {
            seeds.add(normalized);
        }
    }

    private static boolean sameOptimizerSeed(Map<String, Double> left, Map<String, Double> right) {
        Set<String> symbols = new HashSet<>(left.keySet());
        symbols.addAll(right.keySet());
        double distance = 0.0d;
        for (String symbol : symbols) {
            distance += Math.abs(left.getOrDefault(symbol, 0.0d) - right.getOrDefault(symbol, 0.0d));
        }
        return distance < 0.015d;
    }

    private static Map<String, Double> lowRiskAlphaSeed(List<StockBacktestResult> selected,
            Map<String, Double> alphaScores, Map<String, RiskStats> riskStatsBySymbol) {
        Map<String, Double> raw = new LinkedHashMap<>();
        double averageAlpha = Math.max(1.0d, averageAlpha(alphaScores));
        for (StockBacktestResult result : selected) {
            RiskStats stats = riskStatsBySymbol.getOrDefault(result.getSymbol(), RiskStats.missing());
            double alpha = alphaScores.getOrDefault(result.getSymbol(), averageAlpha);
            double volatility = Double.isFinite(stats.volatilityPct()) ? Math.max(8.0d, stats.volatilityPct()) : 30.0d;
            double betaDistance = Double.isFinite(stats.beta()) ? Math.abs(stats.beta() - OPTIMIZER_TARGET_BETA) : 0.45d;
            double liquidity = liquidityBoost(stats.avgDollarVolume());
            raw.put(result.getSymbol(), alpha * liquidity / (volatility * volatility * (1.0d + betaDistance)));
        }
        return normalizeRaw(raw);
    }

    private static Map<String, Double> sectorBalancedSeed(List<StockBacktestResult> selected,
            Map<String, Double> alphaScores, Map<String, RiskStats> riskStatsBySymbol,
            Map<String, Double> benchmarkSectorWeights) {
        Map<String, List<StockBacktestResult>> bySector = selected.stream()
                .collect(Collectors.groupingBy(result -> fallback(result.getSector()), LinkedHashMap::new,
                        Collectors.toList()));
        if (bySector.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> sectorTargets = new LinkedHashMap<>();
        for (String sector : bySector.keySet()) {
            double benchmarkWeight = benchmarkSectorWeights.getOrDefault(sector, 1.0d / bySector.size());
            sectorTargets.put(sector, Math.min(RISK_SECTOR_CAP,
                    Math.max(0.02d, benchmarkWeight + Math.min(0.04d, OPTIMIZER_ACTIVE_SECTOR_LIMIT / 2.0d))));
        }
        double totalTarget = sectorTargets.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalTarget <= 0) {
            return Map.of();
        }
        Map<String, Double> raw = new LinkedHashMap<>();
        double averageAlpha = Math.max(1.0d, averageAlpha(alphaScores));
        for (Map.Entry<String, List<StockBacktestResult>> sector : bySector.entrySet()) {
            double sectorWeight = sectorTargets.getOrDefault(sector.getKey(), 0.0d) / totalTarget;
            Map<String, Double> sectorRaw = new LinkedHashMap<>();
            for (StockBacktestResult result : sector.getValue()) {
                RiskStats stats = riskStatsBySymbol.getOrDefault(result.getSymbol(), RiskStats.missing());
                double alpha = alphaScores.getOrDefault(result.getSymbol(), averageAlpha);
                double riskPenalty = volatilityPenalty(stats.volatilityPct()) * betaPenalty(stats.beta());
                sectorRaw.put(result.getSymbol(), alpha * liquidityBoost(stats.avgDollarVolume())
                        / Math.max(0.35d, riskPenalty));
            }
            Map<String, Double> sectorNormalized = normalizeRaw(sectorRaw);
            for (Map.Entry<String, Double> entry : sectorNormalized.entrySet()) {
                raw.put(entry.getKey(), entry.getValue() * sectorWeight);
            }
        }
        return normalizeRaw(raw);
    }

    private static Map<String, Double> projectOptimizerWeights(List<StockBacktestResult> selected,
            Map<String, Double> inputWeights, Map<String, Double> previousWeights, StrategyConfig config) {
        if (inputWeights.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> cleaned = inputWeights.entrySet().stream()
                .filter(entry -> entry.getValue() != null && Double.isFinite(entry.getValue()) && entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
        if (cleaned.isEmpty()) {
            return Map.of();
        }
        double invested = investedWeight(cleaned);
        if (invested > 1.0d) {
            double scale = 1.0d / invested;
            cleaned.replaceAll((symbol, weight) -> weight * scale);
        }
        Map<String, Double> capped = applyRiskWeightCaps(selected, cleaned, config);
        capped = applyTurnoverBudget(capped, previousWeights, selected, config);
        double cappedInvested = investedWeight(capped);
        if (cappedInvested < 0.80d) {
            double positionCap = config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT;
            redistributeRiskWeight(selected, capped, Math.min(1.0d - cappedInvested, 0.20d), positionCap);
            capped = applyRiskWeightCaps(selected, capped, config);
        }
        return capped;
    }

    private static double quadraticPortfolioObjective(List<StockBacktestResult> selected, Map<String, Double> weights,
            Map<String, Double> alphaScores, Map<String, RiskStats> riskStatsBySymbol,
            Map<String, Double> previousWeights, StrategyConfig config, CovarianceLookup covarianceLookup,
            LocalDate signalDate, Map<String, Double> benchmarkSectorWeights) {
        double base = portfolioObjective(selected, weights, alphaScores, riskStatsBySymbol, previousWeights,
                config, covarianceLookup, signalDate, benchmarkSectorWeights);
        double variance = portfolioCovarianceVariance(weights, riskStatsBySymbol, covarianceLookup, signalDate);
        double crowding = weightedCorrelationCrowding(weights, covarianceLookup, signalDate);
        double variancePenalty = Double.isFinite(variance) ? variance * 4.5d : 0.25d;
        double crowdingPenalty = Double.isFinite(crowding) ? square(Math.max(0.0d, crowding - 0.35d) / 0.20d) : 0.0d;
        return base + variancePenalty + crowdingPenalty * 0.45d;
    }

    private static double optimizerV5PortfolioObjective(List<StockBacktestResult> selected, Map<String, Double> weights,
            Map<String, Double> alphaScores, Map<String, RiskStats> riskStatsBySymbol,
            Map<String, Double> previousWeights, StrategyConfig config, CovarianceLookup covarianceLookup,
            LocalDate signalDate, Map<String, Double> benchmarkSectorWeights) {
        double objective = quadraticPortfolioObjective(selected, weights, alphaScores, riskStatsBySymbol,
                previousWeights, config, covarianceLookup, signalDate, benchmarkSectorWeights);
        double variance = portfolioCovarianceVariance(weights, riskStatsBySymbol, covarianceLookup, signalDate);
        double covarianceVol = Double.isFinite(variance) && variance > 0.0d ? Math.sqrt(variance) * 100.0d : Double.NaN;
        double activeSector = activeSectorDeviation(sectorWeights(selected, weights), benchmarkSectorWeights);
        double turnover = oneWayTurnover(previousWeights, weights);
        double invested = investedWeight(weights);
        double herfindahl = weights.values().stream()
                .filter(Double::isFinite)
                .mapToDouble(weight -> weight * weight)
                .sum();
        double maxSector = sectorWeights(selected, weights).values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0d);
        double constraintPenalty = square(Math.max(0.0d, 0.92d - invested) / 0.08d)
                + square(Math.max(0.0d, turnover - optimizerTurnoverBudget(config)) / 0.08d)
                + square(Math.max(0.0d, activeSector - OPTIMIZER_ACTIVE_SECTOR_LIMIT) / 0.025d)
                + square(Math.max(0.0d, maxSector - RISK_SECTOR_CAP) / 0.025d);
        double covarianceTailPenalty = Double.isFinite(covarianceVol)
                ? square(Math.max(0.0d, covarianceVol - OPTIMIZER_TARGET_VOLATILITY_PCT * 1.10d) / 6.0d)
                : 0.0d;
        double concentrationPenalty = square(Math.max(0.0d, herfindahl - 0.075d) / 0.050d);
        return objective + constraintPenalty * 1.80d + covarianceTailPenalty * 0.75d + concentrationPenalty * 0.35d;
    }

    private static Map<String, Double> quadraticObjectiveGradient(List<StockBacktestResult> selected,
            Map<String, Double> weights, Map<String, Double> alphaScores, Map<String, RiskStats> riskStatsBySymbol,
            Map<String, Double> previousWeights, StrategyConfig config, CovarianceLookup covarianceLookup,
            LocalDate signalDate) {
        double averageAlpha = Math.max(1.0d, averageAlpha(alphaScores));
        double beta = weightedRiskMetric(weights, riskStatsBySymbol, RiskField.BETA);
        double turnover = oneWayTurnover(previousWeights, weights);
        double turnoverBudget = optimizerTurnoverBudget(config);
        double positionCap = config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT;
        Map<String, Double> sectors = sectorWeights(selected, weights);
        Map<String, Double> gradient = new LinkedHashMap<>();
        for (StockBacktestResult result : selected) {
            String symbol = result.getSymbol();
            RiskStats stats = riskStatsBySymbol.getOrDefault(symbol, RiskStats.missing());
            double alpha = alphaScores.getOrDefault(symbol, averageAlpha) / averageAlpha;
            double covarianceGradient = covarianceGradient(symbol, weights, riskStatsBySymbol, covarianceLookup,
                    signalDate);
            double betaGradient = Double.isFinite(beta) && Double.isFinite(stats.beta())
                    ? 2.0d * (beta - OPTIMIZER_TARGET_BETA) * stats.beta() / square(0.22d)
                    : 0.0d;
            double sectorWeight = sectors.getOrDefault(fallback(result.getSector()), 0.0d);
            double sectorGradient = sectorWeight > RISK_SECTOR_CAP
                    ? 2.0d * (sectorWeight - RISK_SECTOR_CAP) / square(0.05d)
                    : 0.0d;
            double position = weights.getOrDefault(symbol, 0.0d);
            double positionGradient = position > positionCap
                    ? 2.0d * (position - positionCap) / square(0.03d)
                    : 0.0d;
            double turnoverGradient = turnover > turnoverBudget
                    ? Math.signum(position - previousWeights.getOrDefault(symbol, 0.0d))
                            * (turnover - turnoverBudget) / square(0.12d)
                    : 0.0d;
            double liquidityGradient = Double.isFinite(stats.avgDollarVolume()) && stats.avgDollarVolume() < 20_000_000.0d
                    ? 0.12d
                    : 0.0d;
            double value = -0.35d * alpha
                    + 4.5d * covarianceGradient
                    + 0.10d * betaGradient
                    + 0.08d * sectorGradient
                    + 0.06d * positionGradient
                    + 0.08d * turnoverGradient
                    + liquidityGradient;
            if (Double.isFinite(value)) {
                gradient.put(symbol, value);
            }
        }
        return gradient;
    }

    private static double covarianceGradient(String symbol, Map<String, Double> weights,
            Map<String, RiskStats> riskStatsBySymbol, CovarianceLookup covarianceLookup, LocalDate signalDate) {
        if (symbol == null || weights.isEmpty()) {
            return 0.0d;
        }
        double sum = 0.0d;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            double covariance = covarianceLookup.covariance(signalDate, symbol, entry.getKey());
            if (!Double.isFinite(covariance)) {
                covariance = fallbackCovariance(symbol, entry.getKey(), riskStatsBySymbol, covarianceLookup,
                        signalDate);
            }
            if (Double.isFinite(covariance)) {
                sum += entry.getValue() * covariance;
            }
        }
        return 2.0d * sum;
    }

    private static List<StockBacktestResult> v3SourceCandidates(List<StockBacktestResult> selected,
            Map<String, Double> weights, Map<String, Double> alphaScores,
            Map<String, RiskStats> riskStatsBySymbol, CovarianceLookup covarianceLookup, LocalDate signalDate) {
        double averageAlpha = averageAlpha(alphaScores);
        return selected.stream()
                .filter(result -> weights.getOrDefault(result.getSymbol(), 0.0d)
                        > OPTIMIZER_MIN_POSITION_WEIGHT + 0.0001d)
                .sorted(Comparator
                        .comparingDouble((StockBacktestResult result) -> v3SourcePressure(result, weights,
                                alphaScores, averageAlpha, riskStatsBySymbol, covarianceLookup, signalDate))
                        .reversed())
                .limit(OPTIMIZER_V3_SOURCE_LIMIT)
                .toList();
    }

    private static List<StockBacktestResult> v3TargetCandidates(List<StockBacktestResult> selected,
            Map<String, Double> weights, Map<String, Double> alphaScores,
            Map<String, RiskStats> riskStatsBySymbol, CovarianceLookup covarianceLookup, LocalDate signalDate,
            StrategyConfig config) {
        return selected.stream()
                .filter(result -> availableRiskCapacity(result, selected, weights, config) > 0.0001d)
                .sorted(Comparator
                        .comparingDouble((StockBacktestResult result) -> v3TargetScore(result, weights,
                                alphaScores, riskStatsBySymbol, covarianceLookup, signalDate))
                        .reversed())
                .limit(OPTIMIZER_V3_TARGET_LIMIT)
                .toList();
    }

    private static double v3SourcePressure(StockBacktestResult result, Map<String, Double> weights,
            Map<String, Double> alphaScores, double averageAlpha, Map<String, RiskStats> riskStatsBySymbol,
            CovarianceLookup covarianceLookup, LocalDate signalDate) {
        RiskStats stats = riskStatsBySymbol.getOrDefault(result.getSymbol(), RiskStats.missing());
        double weight = weights.getOrDefault(result.getSymbol(), 0.0d);
        double betaPressure = Double.isFinite(stats.beta()) ? Math.max(0.0d, stats.beta() - OPTIMIZER_TARGET_BETA) : 0.4d;
        double volPressure = Double.isFinite(stats.volatilityPct())
                ? Math.max(0.0d, stats.volatilityPct() - OPTIMIZER_TARGET_VOLATILITY_PCT) / 30.0d
                : 0.4d;
        double crowding = correlationCrowding(result.getSymbol(), weights, covarianceLookup, signalDate);
        double alpha = alphaScores.getOrDefault(result.getSymbol(), averageAlpha);
        double alphaPressure = averageAlpha <= 0 ? 0.0d : Math.max(0.0d, (averageAlpha - alpha) / averageAlpha);
        return weight * (1.0d + betaPressure + volPressure + crowding + alphaPressure);
    }

    private static double v3TargetScore(StockBacktestResult result, Map<String, Double> weights,
            Map<String, Double> alphaScores, Map<String, RiskStats> riskStatsBySymbol,
            CovarianceLookup covarianceLookup, LocalDate signalDate) {
        RiskStats stats = riskStatsBySymbol.getOrDefault(result.getSymbol(), RiskStats.missing());
        double alpha = alphaScores.getOrDefault(result.getSymbol(), 0.0d);
        double betaPenalty = Double.isFinite(stats.beta()) ? Math.abs(stats.beta() - OPTIMIZER_TARGET_BETA) * 8.0d : 5.0d;
        double volPenalty = Double.isFinite(stats.volatilityPct())
                ? Math.max(0.0d, stats.volatilityPct() - OPTIMIZER_TARGET_VOLATILITY_PCT) * 0.25d
                : 5.0d;
        double crowdingPenalty = correlationCrowding(result.getSymbol(), weights, covarianceLookup, signalDate) * 12.0d;
        double liquidityBonus = Double.isFinite(stats.avgDollarVolume())
                ? Math.min(5.0d, Math.log10(Math.max(1.0d, stats.avgDollarVolume())) - 6.0d)
                : -2.0d;
        return alpha + liquidityBonus - betaPenalty - volPenalty - crowdingPenalty;
    }

    private static double expectedAlphaMultiplier(ExpectedReturnStats expected) {
        if (!Double.isFinite(expected.expectedExcessReturnPct())) {
            return 1.0d;
        }
        double alpha = 1.0d + Math.max(-0.35d, Math.min(0.55d, expected.expectedExcessReturnPct() / 8.0d));
        double upsideProbability = displayUpsideProbability(expected);
        if (Double.isFinite(upsideProbability)) {
            alpha += Math.max(-0.10d, Math.min(0.12d, (upsideProbability - 50.0d) / 250.0d));
        }
        if (Double.isFinite(expected.downsideProbabilityPct()) && expected.downsideProbabilityPct() > 35.0d) {
            alpha -= Math.min(0.15d, (expected.downsideProbabilityPct() - 35.0d) / 120.0d);
        }
        double confidenceMultiplier = 0.70d + Math.min(0.30d, Math.max(0.0d, expected.confidence()) / 100.0d * 0.30d);
        return Math.max(0.45d, Math.min(1.75d, alpha * confidenceMultiplier + (1.0d - confidenceMultiplier)));
    }

    private static double portfolioObjective(List<StockBacktestResult> selected, Map<String, Double> weights,
            Map<String, Double> alphaScores, Map<String, RiskStats> riskStatsBySymbol,
            Map<String, Double> previousWeights, StrategyConfig config, CovarianceLookup covarianceLookup,
            LocalDate signalDate, Map<String, Double> benchmarkSectorWeights) {
        double beta = weightedRiskMetric(weights, riskStatsBySymbol, RiskField.BETA);
        double trailingVol = weightedRiskMetric(weights, riskStatsBySymbol, RiskField.VOLATILITY);
        double covarianceVol = portfolioCovarianceVolatilityPct(weights, riskStatsBySymbol, covarianceLookup, signalDate);
        double portfolioVol = Double.isFinite(covarianceVol) ? covarianceVol : trailingVol;
        double turnover = oneWayTurnover(previousWeights, weights);
        double invested = investedWeight(weights);
        double maxSector = sectorWeights(selected, weights).values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double activeSector = activeSectorDeviation(sectorWeights(selected, weights), benchmarkSectorWeights);
        double maxPosition = weights.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double positionCap = config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT;
        double alpha = weightedAlpha(weights, alphaScores);
        double averageAlpha = averageAlpha(alphaScores);

        double betaPenalty = Double.isFinite(beta) ? square((beta - OPTIMIZER_TARGET_BETA) / 0.22d) : 1.5d;
        double volPenalty = Double.isFinite(portfolioVol)
                ? square(Math.max(0.0d, portfolioVol - OPTIMIZER_TARGET_VOLATILITY_PCT) / 8.0d)
                : 1.0d;
        double turnoverPenalty = square(Math.max(0.0d, turnover - optimizerTurnoverBudget(config)) / 0.12d);
        double sectorPenalty = square(Math.max(0.0d, maxSector - RISK_SECTOR_CAP) / 0.05d);
        double activeSectorPenalty = square(Math.max(0.0d, activeSector - OPTIMIZER_ACTIVE_SECTOR_LIMIT) / 0.04d);
        double positionPenalty = square(Math.max(0.0d, maxPosition - positionCap) / 0.03d);
        double cashPenalty = square(Math.max(0.0d, 0.80d - invested) / 0.20d);
        double covariancePenalty = Double.isFinite(covarianceVol)
                ? square(Math.max(0.0d, covarianceVol - OPTIMIZER_TARGET_VOLATILITY_PCT) / 10.0d)
                : 0.0d;
        double alphaReward = averageAlpha <= 0 ? 0.0d : alpha / averageAlpha;

        return betaPenalty * 1.20d
                + volPenalty * 1.15d
                + covariancePenalty * 0.80d
                + turnoverPenalty * 1.25d
                + sectorPenalty * 2.50d
                + activeSectorPenalty * 1.20d
                + positionPenalty * 2.00d
                + cashPenalty * 0.50d
                - alphaReward * 0.35d;
    }

    private static Map<String, Double> applyTurnoverBudget(Map<String, Double> inputWeights,
            Map<String, Double> previousWeights, List<StockBacktestResult> selected, StrategyConfig config) {
        if (inputWeights.isEmpty() || previousWeights.isEmpty()) {
            return inputWeights;
        }
        double turnover = oneWayTurnover(previousWeights, inputWeights);
        double budget = optimizerTurnoverBudget(config);
        if (turnover <= budget || turnover <= 0) {
            return inputWeights;
        }
        double scale = Math.max(0.0d, Math.min(1.0d, budget / turnover));
        Set<String> selectedSymbols = selected.stream()
                .map(StockBacktestResult::getSymbol)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Double> adjusted = new LinkedHashMap<>();
        for (String symbol : selectedSymbols) {
            double previous = previousWeights.getOrDefault(symbol, 0.0d);
            double target = inputWeights.getOrDefault(symbol, 0.0d);
            double weight = previous + (target - previous) * scale;
            if (weight > 0.000001d) {
                adjusted.put(symbol, weight);
            }
        }
        return applyRiskWeightCaps(selected, adjusted, config);
    }

    private static Map<String, Double> reduceActiveSectorDeviation(List<StockBacktestResult> selected,
            Map<String, Double> inputWeights, Map<String, Double> previousWeights, StrategyConfig config,
            Map<String, Double> benchmarkSectorWeights) {
        if (selected.isEmpty() || inputWeights.isEmpty() || benchmarkSectorWeights.isEmpty()) {
            return inputWeights;
        }
        Map<String, Double> weights = new LinkedHashMap<>(inputWeights);
        for (int iteration = 0; iteration < 20; iteration++) {
            Map<String, Double> sectorWeights = sectorWeights(selected, weights);
            Map<String, Double> currentWeights = Map.copyOf(weights);
            Map<String, Double> currentSectorWeights = Map.copyOf(sectorWeights);
            String overweightSector = sectorWeights.entrySet().stream()
                    .filter(entry -> entry.getValue()
                            > benchmarkSectorWeights.getOrDefault(entry.getKey(), 0.0d)
                                    + OPTIMIZER_ACTIVE_SECTOR_LIMIT)
                    .max(Comparator.comparingDouble(entry ->
                            entry.getValue() - benchmarkSectorWeights.getOrDefault(entry.getKey(), 0.0d)))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (overweightSector == null) {
                break;
            }
            StockBacktestResult source = selected.stream()
                    .filter(result -> Objects.equals(fallback(result.getSector()), overweightSector))
                    .filter(result -> currentWeights.getOrDefault(result.getSymbol(), 0.0d)
                            > OPTIMIZER_MIN_POSITION_WEIGHT)
                    .max(Comparator.comparingDouble(result -> currentWeights.getOrDefault(result.getSymbol(), 0.0d)))
                    .orElse(null);
            StockBacktestResult target = selected.stream()
                    .filter(result -> !Objects.equals(fallback(result.getSector()), overweightSector))
                    .filter(result -> availableRiskCapacity(result, selected, currentWeights, config) > 0.0001d)
                    .min(Comparator
                            .comparingDouble((StockBacktestResult result) -> sectorActiveOverweight(
                                    fallback(result.getSector()), currentSectorWeights, benchmarkSectorWeights))
                            .thenComparing(Comparator.comparingInt(StockPortfolioBacktestService::score).reversed()))
                    .orElse(null);
            if (source == null || target == null) {
                break;
            }
            double sourceWeight = weights.getOrDefault(source.getSymbol(), 0.0d);
            double capacity = availableRiskCapacity(target, selected, weights, config);
            double excessSector = sectorWeights.getOrDefault(overweightSector, 0.0d)
                    - benchmarkSectorWeights.getOrDefault(overweightSector, 0.0d)
                    - OPTIMIZER_ACTIVE_SECTOR_LIMIT;
            double shift = Math.min(0.01d, Math.min(sourceWeight - OPTIMIZER_MIN_POSITION_WEIGHT,
                    Math.min(capacity, excessSector)));
            if (shift <= 0.0001d) {
                break;
            }
            weights.merge(source.getSymbol(), -shift, Double::sum);
            weights.merge(target.getSymbol(), shift, Double::sum);
            weights = applyTurnoverBudget(applyRiskWeightCaps(selected, weights, config), previousWeights, selected,
                    config);
        }
        return weights.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.000001d)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static double sectorActiveOverweight(String sector, Map<String, Double> sectorWeights,
            Map<String, Double> benchmarkSectorWeights) {
        return sectorWeights.getOrDefault(sector, 0.0d) - benchmarkSectorWeights.getOrDefault(sector, 0.0d);
    }

    private static double optimizerTurnoverBudget(StrategyConfig config) {
        if (config.topCount() <= 10) {
            return 0.45d;
        }
        if (config.topCount() <= 20) {
            return 0.35d;
        }
        return 0.28d;
    }

    private static double portfolioCovarianceVolatilityPct(Map<String, Double> weights,
            Map<String, RiskStats> riskStatsBySymbol, CovarianceLookup covarianceLookup, LocalDate signalDate) {
        double variance = portfolioCovarianceVariance(weights, riskStatsBySymbol, covarianceLookup, signalDate);
        return Double.isFinite(variance) && variance > 0.0d ? Math.sqrt(variance) * 100.0d : Double.NaN;
    }

    private static double portfolioCovarianceVariance(Map<String, Double> weights,
            Map<String, RiskStats> riskStatsBySymbol, CovarianceLookup covarianceLookup, LocalDate signalDate) {
        if (weights.isEmpty()) {
            return Double.NaN;
        }
        List<Map.Entry<String, Double>> entries = weights.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .toList();
        if (entries.isEmpty()) {
            return Double.NaN;
        }
        double variance = 0.0d;
        boolean hasAnyCovariance = false;
        for (int i = 0; i < entries.size(); i++) {
            String left = entries.get(i).getKey();
            double leftWeight = entries.get(i).getValue();
            for (int j = i; j < entries.size(); j++) {
                String right = entries.get(j).getKey();
                double rightWeight = entries.get(j).getValue();
                double covariance = covarianceLookup.covariance(signalDate, left, right);
                if (!Double.isFinite(covariance)) {
                    covariance = fallbackCovariance(left, right, riskStatsBySymbol, covarianceLookup, signalDate);
                } else {
                    hasAnyCovariance = true;
                }
                if (!Double.isFinite(covariance)) {
                    continue;
                }
                double contribution = leftWeight * rightWeight * covariance;
                variance += i == j ? contribution : contribution * 2.0d;
            }
        }
        if (variance <= 0 || !hasAnyCovariance) {
            return Double.NaN;
        }
        return variance;
    }

    private static double fallbackCovariance(String left, String right, Map<String, RiskStats> riskStatsBySymbol,
            CovarianceLookup covarianceLookup, LocalDate signalDate) {
        RiskStats leftStats = riskStatsBySymbol.getOrDefault(left, RiskStats.missing());
        RiskStats rightStats = riskStatsBySymbol.getOrDefault(right, RiskStats.missing());
        double leftVol = leftStats.volatilityPct();
        double rightVol = rightStats.volatilityPct();
        if (!Double.isFinite(leftVol) || !Double.isFinite(rightVol)) {
            return Double.NaN;
        }
        if (Objects.equals(left, right)) {
            double vol = leftVol / 100.0d;
            return vol * vol;
        }
        double correlation = covarianceLookup.correlation(signalDate, left, right);
        if (!Double.isFinite(correlation)) {
            correlation = 0.35d;
        }
        return correlation * (leftVol / 100.0d) * (rightVol / 100.0d);
    }

    private static double weightedAlpha(Map<String, Double> weights, Map<String, Double> alphaScores) {
        double weightedAlpha = 0.0d;
        double totalWeight = 0.0d;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            double alpha = alphaScores.getOrDefault(entry.getKey(), Double.NaN);
            if (!Double.isFinite(alpha) || entry.getValue() <= 0) {
                continue;
            }
            weightedAlpha += entry.getValue() * alpha;
            totalWeight += entry.getValue();
        }
        return totalWeight <= 0 ? 0.0d : weightedAlpha / totalWeight;
    }

    private static double averageAlpha(Map<String, Double> alphaScores) {
        return alphaScores.values().stream()
                .filter(Double::isFinite)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0d);
    }

    private static double square(double value) {
        return value * value;
    }

    private static Map<String, Double> reduceCorrelationCrowding(List<StockBacktestResult> selected,
            Map<String, Double> inputWeights, Map<String, RiskStats> riskStatsBySymbol,
            CovarianceLookup covarianceLookup, LocalDate signalDate, StrategyConfig config) {
        if (selected.isEmpty() || inputWeights.isEmpty() || covarianceLookup.isEmpty()) {
            return inputWeights;
        }
        Map<String, Double> weights = new LinkedHashMap<>(inputWeights);
        for (int i = 0; i < 25; i++) {
            StockBacktestResult source = selected.stream()
                    .filter(result -> weights.getOrDefault(result.getSymbol(), 0.0d) > OPTIMIZER_MIN_POSITION_WEIGHT)
                    .max(Comparator.comparingDouble(result -> correlationCrowding(result.getSymbol(), weights,
                            covarianceLookup, signalDate) * weights.getOrDefault(result.getSymbol(), 0.0d)))
                    .orElse(null);
            if (source == null) {
                break;
            }
            double sourceCrowding = correlationCrowding(source.getSymbol(), weights, covarianceLookup, signalDate);
            if (sourceCrowding < OPTIMIZER_V2_CORRELATION_THRESHOLD) {
                break;
            }
            StockBacktestResult target = selected.stream()
                    .filter(result -> !Objects.equals(result.getSymbol(), source.getSymbol()))
                    .filter(result -> availableRiskCapacity(result, selected, weights, config) > 0.0001d)
                    .filter(result -> score(result) >= score(source) - 18)
                    .min(Comparator
                            .comparingDouble((StockBacktestResult result) -> correlationCrowding(result.getSymbol(),
                                    weights, covarianceLookup, signalDate))
                            .thenComparing(Comparator.comparingInt(StockPortfolioBacktestService::score).reversed()))
                    .orElse(null);
            if (target == null) {
                break;
            }
            double targetCrowding = correlationCrowding(target.getSymbol(), weights, covarianceLookup, signalDate);
            if (targetCrowding >= sourceCrowding - 0.08d) {
                break;
            }
            double sourceWeight = weights.getOrDefault(source.getSymbol(), 0.0d);
            double capacity = availableRiskCapacity(target, selected, weights, config);
            double shift = Math.min(0.0075d, Math.min(sourceWeight - OPTIMIZER_MIN_POSITION_WEIGHT, capacity));
            if (shift <= 0.0001d) {
                break;
            }
            weights.merge(source.getSymbol(), -shift, Double::sum);
            weights.merge(target.getSymbol(), shift, Double::sum);
        }
        return weights.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static double correlationCrowding(String symbol, Map<String, Double> weights,
            CovarianceLookup covarianceLookup, LocalDate signalDate) {
        double crowding = 0.0d;
        double coveredWeight = 0.0d;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (Objects.equals(symbol, entry.getKey()) || entry.getValue() <= 0) {
                continue;
            }
            double correlation = covarianceLookup.correlation(signalDate, symbol, entry.getKey());
            if (!Double.isFinite(correlation)) {
                continue;
            }
            crowding += entry.getValue() * Math.max(0.0d, correlation);
            coveredWeight += entry.getValue();
        }
        return coveredWeight <= 0 ? 0 : crowding / coveredWeight;
    }

    private static double betaPenalty(double beta) {
        if (!Double.isFinite(beta)) {
            return 1.20d;
        }
        double distance = Math.abs(beta - OPTIMIZER_TARGET_BETA);
        double highBetaPenalty = Math.max(0.0d, beta - OPTIMIZER_TARGET_BETA) * 0.90d;
        return 1.0d + distance * 0.45d + highBetaPenalty;
    }

    private static double volatilityPenalty(double volatilityPct) {
        if (!Double.isFinite(volatilityPct)) {
            return 1.20d;
        }
        return 1.0d + Math.max(0.0d, volatilityPct - 25.0d) / 70.0d;
    }

    private static double liquidityBoost(double avgDollarVolume) {
        if (!Double.isFinite(avgDollarVolume) || avgDollarVolume <= 0) {
            return 0.70d;
        }
        if (avgDollarVolume >= 1_000_000_000.0d) {
            return 1.18d;
        }
        if (avgDollarVolume >= 100_000_000.0d) {
            return 1.08d;
        }
        if (avgDollarVolume >= 20_000_000.0d) {
            return 0.95d;
        }
        if (avgDollarVolume >= 5_000_000.0d) {
            return 0.78d;
        }
        return 0.58d;
    }

    private static Map<String, Double> normalizeRaw(Map<String, Double> raw) {
        double total = raw.values().stream().filter(value -> value > 0).mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return Map.of();
        }
        return raw.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue() / total,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static Map<String, Double> tiltTowardTargetBeta(List<StockBacktestResult> selected,
            Map<String, Double> inputWeights, Map<String, RiskStats> riskStatsBySymbol, StrategyConfig config) {
        Map<String, Double> weights = new LinkedHashMap<>(inputWeights);
        for (int i = 0; i < 25; i++) {
            double beta = weightedRiskMetric(weights, riskStatsBySymbol, RiskField.BETA);
            if (!Double.isFinite(beta) || beta <= OPTIMIZER_TARGET_BETA + 0.03d) {
                break;
            }
            StockBacktestResult high = selected.stream()
                    .filter(result -> weights.getOrDefault(result.getSymbol(), 0.0d) > OPTIMIZER_MIN_POSITION_WEIGHT)
                    .filter(result -> riskStatsBySymbol.getOrDefault(result.getSymbol(), RiskStats.missing()).beta() > beta)
                    .max(Comparator.comparingDouble(result ->
                            riskStatsBySymbol.getOrDefault(result.getSymbol(), RiskStats.missing()).beta()))
                    .orElse(null);
            StockBacktestResult low = selected.stream()
                    .filter(result -> riskStatsBySymbol.getOrDefault(result.getSymbol(), RiskStats.missing()).beta()
                            < OPTIMIZER_TARGET_BETA)
                    .filter(result -> availableRiskCapacity(result, selected, weights, config) > 0.0001d)
                    .min(Comparator.comparingDouble(result ->
                            riskStatsBySymbol.getOrDefault(result.getSymbol(), RiskStats.missing()).beta()))
                    .orElse(null);
            if (high == null || low == null) {
                break;
            }
            double sourceWeight = weights.getOrDefault(high.getSymbol(), 0.0d);
            double capacity = availableRiskCapacity(low, selected, weights, config);
            double shift = Math.min(0.01d, Math.min(sourceWeight - OPTIMIZER_MIN_POSITION_WEIGHT, capacity));
            if (shift <= 0.0001d) {
                break;
            }
            weights.merge(high.getSymbol(), -shift, Double::sum);
            weights.merge(low.getSymbol(), shift, Double::sum);
        }
        return weights;
    }

    private static double availableRiskCapacity(StockBacktestResult result, List<StockBacktestResult> selected,
            Map<String, Double> weights, StrategyConfig config) {
        double positionCap = config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT;
        double positionCapacity = Math.max(0.0d, positionCap - weights.getOrDefault(result.getSymbol(), 0.0d));
        Map<String, Double> sectors = sectorWeights(selected, weights);
        double sectorCapacity = Math.max(0.0d, RISK_SECTOR_CAP - sectors.getOrDefault(fallback(result.getSector()), 0.0d));
        return Math.min(positionCapacity, sectorCapacity);
    }

    private static Map<String, Double> applyRiskWeightCaps(List<StockBacktestResult> selected,
            Map<String, Double> inputWeights, StrategyConfig config) {
        if (selected.isEmpty() || inputWeights.isEmpty()) {
            return Map.of();
        }
        double positionCap = config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT;
        Map<String, Double> weights = new LinkedHashMap<>(inputWeights);
        for (int i = 0; i < 10; i++) {
            boolean changed = false;
            double freed = 0.0d;

            for (Map.Entry<String, Double> entry : new ArrayList<>(weights.entrySet())) {
                double weight = entry.getValue();
                if (weight > positionCap) {
                    weights.put(entry.getKey(), positionCap);
                    freed += weight - positionCap;
                    changed = true;
                }
            }

            Map<String, Double> sectorWeights = sectorWeights(selected, weights);
            for (Map.Entry<String, Double> sector : sectorWeights.entrySet()) {
                if (sector.getValue() <= RISK_SECTOR_CAP) {
                    continue;
                }
                double scale = RISK_SECTOR_CAP / sector.getValue();
                for (StockBacktestResult result : selected) {
                    if (!Objects.equals(fallback(result.getSector()), sector.getKey())) {
                        continue;
                    }
                    double oldWeight = weights.getOrDefault(result.getSymbol(), 0.0d);
                    double newWeight = oldWeight * scale;
                    weights.put(result.getSymbol(), newWeight);
                    freed += oldWeight - newWeight;
                    changed = true;
                }
            }

            if (freed > 0.0000001d) {
                double redistributed = redistributeRiskWeight(selected, weights, freed, positionCap);
                changed = changed || redistributed > 0.0000001d;
            }
            if (!changed) {
                break;
            }
        }
        return weights.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static double redistributeRiskWeight(List<StockBacktestResult> selected, Map<String, Double> weights,
            double freed, double positionCap) {
        if (freed <= 0) {
            return 0;
        }
        Map<String, Double> sectorWeights = sectorWeights(selected, weights);
        Map<String, Double> capacityBySymbol = new LinkedHashMap<>();
        double totalCapacity = 0.0d;
        for (StockBacktestResult result : selected) {
            String symbol = result.getSymbol();
            String sector = fallback(result.getSector());
            double positionCapacity = Math.max(0.0d, positionCap - weights.getOrDefault(symbol, 0.0d));
            double sectorCapacity = Math.max(0.0d, RISK_SECTOR_CAP - sectorWeights.getOrDefault(sector, 0.0d));
            double capacity = Math.min(positionCapacity, sectorCapacity);
            if (capacity > 0.0000001d) {
                capacityBySymbol.put(symbol, capacity);
                totalCapacity += capacity;
            }
        }
        if (totalCapacity <= 0) {
            return 0;
        }
        double remaining = freed;
        for (Map.Entry<String, Double> entry : capacityBySymbol.entrySet()) {
            double add = Math.min(entry.getValue(), freed * entry.getValue() / totalCapacity);
            weights.merge(entry.getKey(), add, Double::sum);
            remaining -= add;
        }
        return freed - Math.max(0.0d, remaining);
    }

    private static Map<String, Double> normalize(List<StockBacktestResult> selected,
            Function<StockBacktestResult, Double> rawWeightReader) {
        Map<String, Double> raw = new LinkedHashMap<>();
        double total = 0;
        for (StockBacktestResult result : selected) {
            double value = Math.max(0.0d, rawWeightReader.apply(result));
            if (value <= 0) {
                continue;
            }
            raw.put(result.getSymbol(), value);
            total += value;
        }
        if (total <= 0) {
            return Map.of();
        }
        double denominator = total;
        return raw.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue() / denominator,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static double oneWayTurnover(Map<String, Double> previous, Map<String, Double> current) {
        List<String> symbols = new ArrayList<>(previous.keySet());
        for (String symbol : current.keySet()) {
            if (!previous.containsKey(symbol)) {
                symbols.add(symbol);
            }
        }
        double absoluteChange = 0;
        for (String symbol : symbols) {
            absoluteChange += Math.abs(current.getOrDefault(symbol, 0.0d) - previous.getOrDefault(symbol, 0.0d));
        }
        double previousCash = Math.max(0.0d, 1.0d - previous.values().stream().mapToDouble(Double::doubleValue).sum());
        double currentCash = Math.max(0.0d, 1.0d - current.values().stream().mapToDouble(Double::doubleValue).sum());
        return (absoluteChange + Math.abs(currentCash - previousCash)) / 2.0d;
    }

    private static double transactionCostPct(Map<String, Double> previous, Map<String, Double> current,
            Map<String, RiskStats> riskStatsBySymbol) {
        Set<String> symbols = new HashSet<>(previous.keySet());
        symbols.addAll(current.keySet());
        double cost = 0.0d;
        for (String symbol : symbols) {
            double tradedNotional = Math.abs(current.getOrDefault(symbol, 0.0d) - previous.getOrDefault(symbol, 0.0d));
            if (tradedNotional <= 0) {
                continue;
            }
            RiskStats stats = riskStatsBySymbol.getOrDefault(symbol, RiskStats.missing());
            cost += tradedNotional * effectiveTradeCostPct(stats);
        }
        return cost;
    }

    private static double effectiveTradeCostPct(RiskStats stats) {
        double cost = BASE_TRADE_COST_PCT;
        double liquidity = stats.avgDollarVolume();
        if (!Double.isFinite(liquidity)) {
            cost += 0.10d;
        } else if (liquidity < 5_000_000.0d) {
            cost += 0.28d;
        } else if (liquidity < 20_000_000.0d) {
            cost += 0.14d;
        } else if (liquidity < 100_000_000.0d) {
            cost += 0.07d;
        } else if (liquidity < 1_000_000_000.0d) {
            cost += 0.03d;
        } else {
            cost += 0.01d;
        }

        double volatility = stats.volatilityPct();
        if (Double.isFinite(volatility) && volatility > 35.0d) {
            cost += Math.min(0.12d, (volatility - 35.0d) * 0.003d);
        }

        double beta = stats.beta();
        if (Double.isFinite(beta) && beta > 1.4d) {
            cost += Math.min(0.05d, (beta - 1.4d) * 0.04d);
        }
        return cost;
    }

    private static double weightedReturn(List<StockBacktestResult> selected, Map<String, Double> weights) {
        return selected.stream()
                .mapToDouble(result -> weights.getOrDefault(result.getSymbol(), 0.0d) * returnPct(result))
                .sum();
    }

    private static double benchmarkReturn(List<StockBacktestResult> universe) {
        List<StockBacktestResult> withMarketCap = universe.stream()
                .filter(result -> result.getMarketCap() != null && result.getMarketCap().signum() > 0)
                .toList();
        if (withMarketCap.size() >= 50) {
            double totalMarketCap = withMarketCap.stream()
                    .map(StockBacktestResult::getMarketCap)
                    .mapToDouble(BigDecimal::doubleValue)
                    .sum();
            if (totalMarketCap > 0) {
                return withMarketCap.stream()
                        .mapToDouble(result -> result.getMarketCap().doubleValue() / totalMarketCap * returnPct(result))
                        .sum();
            }
        }
        return universe.stream()
                .mapToDouble(StockPortfolioBacktestService::returnPct)
                .average()
                .orElse(0);
    }

    private static Map<String, Double> sectorWeights(List<StockBacktestResult> selected, Map<String, Double> weights) {
        Map<String, Double> sectors = new LinkedHashMap<>();
        for (StockBacktestResult result : selected) {
            sectors.merge(fallback(result.getSector()), weights.getOrDefault(result.getSymbol(), 0.0d), Double::sum);
        }
        return sectors;
    }

    private static Map<String, Double> benchmarkSectorWeights(List<StockBacktestResult> universe) {
        Map<String, Double> sectors = new LinkedHashMap<>();
        double totalMarketCap = universe.stream()
                .map(StockBacktestResult::getMarketCap)
                .filter(value -> value != null && value.signum() > 0)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
        if (totalMarketCap > 0) {
            for (StockBacktestResult result : universe) {
                BigDecimal marketCap = result.getMarketCap();
                if (marketCap == null || marketCap.signum() <= 0) {
                    continue;
                }
                sectors.merge(fallback(result.getSector()), marketCap.doubleValue() / totalMarketCap, Double::sum);
            }
        }
        if (!sectors.isEmpty()) {
            return sectors;
        }
        double equalWeight = universe.isEmpty() ? 0.0d : 1.0d / universe.size();
        for (StockBacktestResult result : universe) {
            sectors.merge(fallback(result.getSector()), equalWeight, Double::sum);
        }
        return sectors;
    }

    private static double activeSectorDeviation(Map<String, Double> portfolioSectorWeights,
            Map<String, Double> benchmarkSectorWeights) {
        Set<String> sectors = new LinkedHashSet<>();
        sectors.addAll(portfolioSectorWeights.keySet());
        sectors.addAll(benchmarkSectorWeights.keySet());
        double max = 0.0d;
        for (String sector : sectors) {
            max = Math.max(max, Math.abs(portfolioSectorWeights.getOrDefault(sector, 0.0d)
                    - benchmarkSectorWeights.getOrDefault(sector, 0.0d)));
        }
        return max;
    }

    private static double weightedRiskMetric(List<StockBacktestResult> selected, Map<String, Double> weights,
            RiskLookup riskLookup, LocalDate signalDate, RiskField field) {
        double weightedValue = 0.0d;
        double coveredWeight = 0.0d;
        for (StockBacktestResult result : selected) {
            double weight = weights.getOrDefault(result.getSymbol(), 0.0d);
            if (weight <= 0) {
                continue;
            }
            RiskStats stats = riskLookup.stats(result.getSymbol(), signalDate);
            double value = switch (field) {
                case BETA -> stats.beta();
                case VOLATILITY -> stats.volatilityPct();
                case LIQUIDITY -> stats.avgDollarVolume();
            };
            if (Double.isFinite(value)) {
                weightedValue += weight * value;
                coveredWeight += weight;
            }
        }
        return coveredWeight <= 0 ? Double.NaN : weightedValue / coveredWeight;
    }

    private static double weightedRiskMetric(Map<String, Double> weights, Map<String, RiskStats> riskStatsBySymbol,
            RiskField field) {
        double weightedValue = 0.0d;
        double coveredWeight = 0.0d;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            RiskStats stats = riskStatsBySymbol.getOrDefault(entry.getKey(), RiskStats.missing());
            double value = switch (field) {
                case BETA -> stats.beta();
                case VOLATILITY -> stats.volatilityPct();
                case LIQUIDITY -> stats.avgDollarVolume();
            };
            if (Double.isFinite(value)) {
                weightedValue += entry.getValue() * value;
                coveredWeight += entry.getValue();
            }
        }
        return coveredWeight <= 0 ? Double.NaN : weightedValue / coveredWeight;
    }

    private static Map<String, RiskStats> riskStatsBySymbol(List<StockBacktestResult> selected,
            RiskLookup riskLookup, LocalDate signalDate) {
        return selected.stream()
                .collect(Collectors.toMap(StockBacktestResult::getSymbol,
                        result -> riskLookup.stats(result.getSymbol(), signalDate),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static Map<String, ExpectedReturnStats> expectedStatsBySymbol(List<StockBacktestResult> selected,
            ExpectedReturnLookup expectedReturnLookup, LocalDate signalDate, int horizonDays) {
        return selected.stream()
                .collect(Collectors.toMap(StockBacktestResult::getSymbol,
                        result -> expectedReturnLookup.stats(result.getSymbol(), signalDate, horizonDays),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static StrategySummary summarize(StrategyRun run, double annualRiskFreeRatePct) {
        List<PortfolioPeriod> periods = run.periods();
        double equity = 1.0d;
        double benchmarkEquity = 1.0d;
        double peak = 1.0d;
        double maxDrawdown = 0;
        double best = -Double.MAX_VALUE;
        double worst = Double.MAX_VALUE;
        int benchmarkBeats = 0;
        double turnoverSum = 0;
        double transactionCostSum = 0;
        double investedWeightSum = 0;
        double maxSectorSum = 0;
        double maxPositionSum = 0;
        double activeSectorSum = 0;
        MetricAccumulator betaStats = new MetricAccumulator();
        MetricAccumulator volatilityStats = new MetricAccumulator();
        MetricAccumulator liquidityStats = new MetricAccumulator();
        MetricAccumulator riskFreeStats = new MetricAccumulator();

        for (PortfolioPeriod period : periods) {
            equity *= 1.0d + period.netReturnPct() / 100.0d;
            benchmarkEquity *= 1.0d + period.benchmarkReturnPct() / 100.0d;
            peak = Math.max(peak, equity);
            maxDrawdown = Math.min(maxDrawdown, (equity / peak - 1.0d) * 100.0d);
            best = Math.max(best, period.netReturnPct());
            worst = Math.min(worst, period.netReturnPct());
            if (period.netReturnPct() > period.benchmarkReturnPct()) {
                benchmarkBeats++;
            }
            turnoverSum += period.turnover();
            transactionCostSum += period.transactionCostPct();
            investedWeightSum += investedWeight(period.weights());
            maxSectorSum += period.sectorWeights().values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            maxPositionSum += period.weights().values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            activeSectorSum += period.activeSectorDeviation();
            betaStats.add(period.portfolioBeta());
            volatilityStats.add(period.portfolioVolatilityPct());
            liquidityStats.add(period.portfolioLiquidity());
            riskFreeStats.add(period.riskFreeAnnualRatePct());
        }

        double cumulativeReturn = (equity - 1.0d) * 100.0d;
        double benchmarkReturn = (benchmarkEquity - 1.0d) * 100.0d;
        double excessReturn = cumulativeReturn - benchmarkReturn;
        double years = periods.size() * run.config().horizonDays() / 252.0d;
        double annualReturn = years > 0 ? (Math.pow(equity, 1.0d / years) - 1.0d) * 100.0d : 0;
        double volatility = annualizedVolatility(periods, run.config().horizonDays());
        double effectiveRiskFreeRate = Double.isFinite(riskFreeStats.average())
                ? riskFreeStats.average()
                : annualRiskFreeRatePct;
        Double sharpe = volatility <= 0 ? null : (annualReturn - effectiveRiskFreeRate) / volatility;

        return new StrategySummary(
                run.config(),
                periods.size(),
                cumulativeReturn,
                benchmarkReturn,
                excessReturn,
                annualReturn,
                volatility,
                sharpe,
                maxDrawdown,
                benchmarkBeats * 100.0d / periods.size(),
                turnoverSum / periods.size(),
                transactionCostSum / periods.size(),
                investedWeightSum / periods.size(),
                maxSectorSum / periods.size(),
                maxPositionSum / periods.size(),
                activeSectorSum / periods.size(),
                betaStats.average(),
                volatilityStats.average(),
                liquidityStats.average(),
                effectiveRiskFreeRate,
                best,
                worst);
    }

    private static double annualizedVolatility(List<PortfolioPeriod> periods, int horizonDays) {
        if (periods.size() < 2) {
            return 0;
        }
        double mean = periods.stream().mapToDouble(period -> period.netReturnPct() / 100.0d).average().orElse(0);
        double variance = 0;
        for (PortfolioPeriod period : periods) {
            double delta = period.netReturnPct() / 100.0d - mean;
            variance += delta * delta;
        }
        double sampleVariance = variance / (periods.size() - 1);
        return Math.sqrt(sampleVariance) * Math.sqrt(252.0d / horizonDays) * 100.0d;
    }

    private static StrategyRun selectedRun(List<StrategyRun> runs, List<StrategySummary> summaries) {
        StrategyConfig preferred = new StrategyConfig(20, 20, Weighting.SIGNAL, RiskMode.OPTIMIZED_V5);
        return runs.stream()
                .filter(run -> run.config().equals(preferred))
                .findFirst()
                .orElseGet(() -> {
                    StrategyConfig bestConfig = summaries.get(0).config();
                    return runs.stream()
                            .filter(run -> run.config().equals(bestConfig))
                            .findFirst()
                            .orElse(runs.get(0));
        });
    }

    private static List<StockPortfolioBacktestView.RiskModelRow> riskModelRows(List<StrategySummary> summaries,
            RiskLookup riskLookup, CovarianceLookup covarianceLookup, ExpectedReturnLookup expectedReturnLookup,
            ExpectedCalibrationLookup expectedCalibrationLookup, RiskFreeRateLookup riskFreeRateLookup) {
        StrategySummary bestRisk = summaries.stream()
                .filter(summary -> summary.config().riskMode().controlled())
                .findFirst()
                .orElse(summaries.get(0));
        double avgCalibrationError = expectedCalibrationLookup.averageAbsoluteErrorPct();
        double avgBrierScore = expectedCalibrationLookup.averageBrierScore();
        String calibrationValue = expectedCalibrationLookup.rowCount() == 0
                ? "-"
                : integer(expectedCalibrationLookup.rowCount()) + " buckets";
        String calibrationRule = expectedCalibrationLookup.rowCount() == 0
                ? EXPECTED_RETURN_MODEL_VERSION + " calibration has not been generated yet."
                : "Avg absolute calibration error " + decimal(avgCalibrationError)
                        + "pt, avg Brier " + decimal(avgBrierScore) + ".";
        String calibrationTone = expectedCalibrationLookup.rowCount() == 0
                ? "caution"
                : avgCalibrationError <= 8.0d ? "positive" : avgCalibrationError <= 14.0d ? "neutral" : "caution";
        return List.of(
                new StockPortfolioBacktestView.RiskModelRow("Risk candidate",
                        bestRisk.config().fullLabel(),
                        "Compared against unconstrained portfolios, not applied to live Signal yet.",
                        "neutral"),
                new StockPortfolioBacktestView.RiskModelRow("Sector cap",
                        percentUnsigned(RISK_SECTOR_CAP * 100.0d),
                        "Risk mode limits selected names and target weights by sector.",
                        "neutral"),
                new StockPortfolioBacktestView.RiskModelRow("Position cap",
                        percentUnsigned(RISK_POSITION_CAP_DEFAULT * 100.0d) + " / "
                                + percentUnsigned(RISK_POSITION_CAP_TOP10 * 100.0d),
                        "Default cap, with a wider cap for Top 10 portfolios.",
                        "neutral"),
                new StockPortfolioBacktestView.RiskModelRow("Cost model",
                        "Dynamic",
                        "Base " + percentUnsigned(BASE_TRADE_COST_PCT)
                                + " plus liquidity, volatility, and beta impact per traded notional.",
                        "neutral"),
                new StockPortfolioBacktestView.RiskModelRow("Risk-free rate",
                        riskFreeRateLookup.displayValue(),
                        "Cash return and Sharpe use point-in-time rate snapshots when available. Snapshot rows: "
                                + integer(riskFreeRateLookup.snapshotCount()) + ".",
                        riskFreeRateLookup.snapshotCount() > 0 && !riskFreeRateLookup.fallbackBacked()
                                ? "positive"
                                : "caution"),
                new StockPortfolioBacktestView.RiskModelRow("Optimizer target",
                        "Beta " + decimal(OPTIMIZER_TARGET_BETA)
                                + " / Vol " + percentUnsigned(OPTIMIZER_TARGET_VOLATILITY_PCT),
                        "Optimized v5 uses multi-start projected-gradient optimization with covariance, beta, volatility, turnover, sector, position, active-sector, concentration, and expected-return constraints.",
                        "neutral"),
                new StockPortfolioBacktestView.RiskModelRow("Active sector limit",
                        percentUnsigned(OPTIMIZER_ACTIVE_SECTOR_LIMIT * 100.0d),
                        "Average active sector deviation of best controlled strategy: "
                                + percentUnsigned(bestRisk.averageActiveSectorDeviation() * 100.0d) + ".",
                        bestRisk.averageActiveSectorDeviation() <= OPTIMIZER_ACTIVE_SECTOR_LIMIT ? "positive" : "caution"),
                new StockPortfolioBacktestView.RiskModelRow("Covariance snapshots",
                        integer(covarianceLookup.snapshotCount()),
                        "Pairwise trailing return correlation rows loaded across "
                                + integer(covarianceLookup.dateCount()) + " signal dates.",
                        covarianceLookup.snapshotCount() > 0 ? "positive" : "caution"),
                new StockPortfolioBacktestView.RiskModelRow("Expected return snapshots",
                        integer(expectedReturnLookup.snapshotCount()),
                        "5D/20D/60D " + EXPECTED_RETURN_MODEL_VERSION
                                + " alpha, quantile range, and calibrated upside rows loaded across "
                                + integer(expectedReturnLookup.dateCount()) + " signal dates.",
                        expectedReturnLookup.snapshotCount() > 0 ? "positive" : "caution"),
                new StockPortfolioBacktestView.RiskModelRow("Probability calibration",
                        calibrationValue,
                        calibrationRule,
                        calibrationTone),
                new StockPortfolioBacktestView.RiskModelRow("Avg beta",
                        metricDecimal(bestRisk.averageBeta()),
                        "Trailing 126D beta versus stored benchmark return series when available.",
                        betaTone(bestRisk.averageBeta())),
                new StockPortfolioBacktestView.RiskModelRow("Avg trail vol",
                        metricPercent(bestRisk.averagePortfolioVolatility()),
                        "Annualized trailing 126D volatility from stock_candle_daily.",
                        volatilityTone(bestRisk.averagePortfolioVolatility())),
                new StockPortfolioBacktestView.RiskModelRow("Avg liquidity",
                        dollarVolume(bestRisk.averageLiquidity()),
                        "Trailing 63D average dollar volume. Risk snapshots loaded: "
                                + integer(riskLookup.snapshotCount()) + ", candle symbols loaded: "
                                + integer(riskLookup.symbolCount()),
                        "neutral"));
    }

    private static List<StockPortfolioBacktestView.MetricCard> cards(List<StrategySummary> summaries,
            List<StockBacktestResult> results, double annualRiskFreeRatePct) {
        StrategySummary best = summaries.get(0);
        StrategySummary bestRisk = summaries.stream()
                .filter(summary -> summary.config().riskMode().controlled())
                .findFirst()
                .orElse(best);
        long rebalanceDates = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long symbols = results.stream()
                .map(StockBacktestResult::getSymbol)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return List.of(
                new StockPortfolioBacktestView.MetricCard("Best strategy", best.config().fullLabel(),
                        best.config().horizonDays() + "D holding", "positive"),
                new StockPortfolioBacktestView.MetricCard("Best risk strategy", bestRisk.config().fullLabel(),
                        "beta " + metricDecimal(bestRisk.averageBeta())
                                + ", vol " + metricPercent(bestRisk.averagePortfolioVolatility()),
                        "neutral"),
                new StockPortfolioBacktestView.MetricCard("Best excess", percent(best.excessReturn()),
                        "vs cap-weight S&P proxy", returnTone(best.excessReturn())),
                new StockPortfolioBacktestView.MetricCard("Best Sharpe",
                        best.sharpe() == null ? "-" : decimal(best.sharpe()),
                        "annualized, rf=" + percentUnsigned(best.averageRiskFreeRate()),
                        best.sharpe() != null && best.sharpe() > 1.0d ? "positive" : "neutral"),
                new StockPortfolioBacktestView.MetricCard("Max drawdown", percent(best.maxDrawdown()),
                        "best strategy path", best.maxDrawdown() < -15 ? "negative" : "neutral"),
                new StockPortfolioBacktestView.MetricCard("Universe", integer(symbols),
                        integer(rebalanceDates) + " historical signal dates", "neutral"));
    }

    private static List<StockPortfolioBacktestView.StrategyRow> strategyRows(List<StrategySummary> summaries) {
        List<StockPortfolioBacktestView.StrategyRow> rows = new ArrayList<>();
        int rank = 1;
        for (StrategySummary summary : summaries) {
            rows.add(new StockPortfolioBacktestView.StrategyRow(
                    integer(rank++),
                    summary.config().label(),
                    summary.config().riskMode().label(),
                    summary.config().horizonDays() + "D",
                    integer(summary.config().topCount()),
                    summary.config().weighting().label(),
                    integer(summary.rebalanceCount()),
                    percent(summary.cumulativeReturn()),
                    percent(summary.benchmarkReturn()),
                    percent(summary.excessReturn()),
                    percent(summary.annualReturn()),
                    percentUnsigned(summary.volatility()),
                    summary.sharpe() == null ? "-" : decimal(summary.sharpe()),
                    percent(summary.maxDrawdown()),
                    percentUnsigned(summary.benchmarkBeatRate()),
                    percentUnsigned(summary.averageTurnover() * 100.0d),
                    percentUnsigned(summary.averageTransactionCost()),
                    percentUnsigned(summary.averageInvestedWeight() * 100.0d),
                    percentUnsigned(summary.averageMaxSectorWeight() * 100.0d),
                    percentUnsigned(summary.averageMaxPositionWeight() * 100.0d),
                    metricDecimal(summary.averageBeta()),
                    metricPercent(summary.averagePortfolioVolatility()),
                    dollarVolume(summary.averageLiquidity()),
                    returnTone(summary.excessReturn())));
        }
        return rows;
    }

    private static List<StockPortfolioBacktestView.RiskImpactRow> riskImpactRows(List<StrategySummary> summaries) {
        Map<StrategyKey, StrategySummary> baseByKey = summaries.stream()
                .filter(summary -> !summary.config().riskMode().controlled())
                .collect(Collectors.toMap(summary -> StrategyKey.from(summary.config()), Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));

        return summaries.stream()
                .filter(summary -> summary.config().riskMode().controlled())
                .map(risk -> riskImpact(baseByKey.get(StrategyKey.from(risk.config())), risk))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingDouble(RiskImpact::sortScore).reversed()
                        .thenComparing(impact -> impact.risk().config().horizonDays())
                        .thenComparing(impact -> impact.risk().config().topCount())
                        .thenComparing(impact -> impact.risk().config().weighting().label()))
                .map(impact -> new StockPortfolioBacktestView.RiskImpactRow(
                        impact.risk().config().label(),
                        impact.risk().config().riskMode().label(),
                        impact.risk().config().horizonDays() + "D",
                        integer(impact.risk().config().topCount()),
                        impact.risk().config().weighting().label(),
                        signedDecimal(impact.sharpeDelta()),
                        signedPercent(impact.drawdownDelta()),
                        signedDecimal(impact.betaDelta()),
                        signedPercent(impact.sectorDelta() * 100.0d),
                        signedPercent(impact.costDelta()),
                        signedPercent(impact.excessDelta()),
                        impact.verdict(),
                        impact.tone()))
                .toList();
    }

    private static List<StockPortfolioBacktestView.OptimizerValidationRow> optimizerShadowSummaryRows(
            List<StockOptimizerShadowSnapshot> snapshots, double annualRiskFreeRatePct) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of(customOptimizerValidationRow("ojAlgo historical shadow", "20D", "20",
                    "Materialized rows", ">= 20", "0", "-", "Run batch", "warning"));
        }
        Map<String, List<StockOptimizerShadowSnapshot>> byCandidate = snapshots.stream()
                .collect(Collectors.groupingBy(
                        row -> fallback(row.getCandidateOptimizer()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<StockPortfolioBacktestView.OptimizerValidationRow> rows = new ArrayList<>();
        for (String candidate : OJALGO_SHADOW_OPTIMIZERS) {
            List<StockOptimizerShadowSnapshot> candidateRows = byCandidate.remove(candidate);
            if (candidateRows != null && !candidateRows.isEmpty()) {
                rows.addAll(optimizerShadowSummaryRows(candidate, candidateRows, annualRiskFreeRatePct));
            }
        }
        byCandidate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> rows.addAll(optimizerShadowSummaryRows(entry.getKey(), entry.getValue(),
                        annualRiskFreeRatePct)));
        return rows;
    }

    private static List<StockPortfolioBacktestView.OptimizerShadowPathRow> optimizerShadowPathRows(
            List<StockOptimizerShadowSnapshot> snapshots, double annualRiskFreeRatePct) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        Map<String, List<StockOptimizerShadowSnapshot>> byCandidate = snapshots.stream()
                .collect(Collectors.groupingBy(
                        row -> fallback(row.getCandidateOptimizer()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<StockPortfolioBacktestView.OptimizerShadowPathRow> rows = new ArrayList<>();
        for (String candidate : OJALGO_SHADOW_OPTIMIZERS) {
            List<StockOptimizerShadowSnapshot> candidateRows = byCandidate.remove(candidate);
            if (candidateRows != null && !candidateRows.isEmpty()) {
                rows.add(optimizerShadowPathRow(candidate, candidateRows, annualRiskFreeRatePct));
            }
        }
        byCandidate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> optimizerShadowPathRow(entry.getKey(), entry.getValue(), annualRiskFreeRatePct))
                .forEach(rows::add);
        return rows;
    }

    private static StockPortfolioBacktestView.OptimizerShadowPathRow optimizerShadowPathRow(String candidateOptimizer,
            List<StockOptimizerShadowSnapshot> snapshots, double annualRiskFreeRatePct) {
        List<StockOptimizerShadowSnapshot> ordered = snapshots.stream()
                .sorted(Comparator.comparing(StockOptimizerShadowSnapshot::getSignalDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int rows = ordered.size();
        long usableRows = ordered.stream().filter(row -> Boolean.TRUE.equals(row.getUsable())).count();
        long hardPassRows = ordered.stream()
                .filter(row -> Boolean.TRUE.equals(row.getUsable()))
                .filter(row -> valueOr(row.getConstraintBreachCount(), 0) == 0)
                .count();
        long betaBreaches = ordered.stream().filter(row -> Boolean.TRUE.equals(row.getBetaBreach())).count();
        long turnoverBreaches = ordered.stream().filter(row -> Boolean.TRUE.equals(row.getTurnoverBreach())).count();
        long objectiveBreaches = ordered.stream().filter(row -> Boolean.TRUE.equals(row.getObjectiveBreach())).count();
        long driftBreaches = ordered.stream().filter(row -> Boolean.TRUE.equals(row.getWeightDistanceBreach())).count();
        double usableRate = rows == 0 ? 0.0d : usableRows * 100.0d / rows;
        double hardPassRate = rows == 0 ? 0.0d : hardPassRows * 100.0d / rows;
        double objectiveBreachRate = rows == 0 ? 0.0d : objectiveBreaches * 100.0d / rows;
        double driftBreachRate = rows == 0 ? 0.0d : driftBreaches * 100.0d / rows;
        double avgObjectiveGap = averageDecimal(ordered, StockOptimizerShadowSnapshot::getObjectiveGap);
        double avgWeightDistance = averageDecimal(ordered, StockOptimizerShadowSnapshot::getWeightDistancePct);
        ShadowPathSummary baseline = shadowPathSummary(ordered, true, annualRiskFreeRatePct);
        ShadowPathSummary candidate = shadowPathSummary(ordered, false, annualRiskFreeRatePct);
        double sharpeDelta = finiteDelta(candidate.sharpe(), baseline.sharpe());
        double drawdownDelta = candidate.maxDrawdownPct() - baseline.maxDrawdownPct();
        double excessDelta = candidate.excessReturnPct() - baseline.excessReturnPct();

        String verdict;
        String tone;
        boolean sampleOk = rows >= 20 && usableRate >= 95.0d;
        boolean constraintOk = hardPassRate >= 80.0d;
        boolean objectiveOk = Double.isFinite(avgObjectiveGap) && avgObjectiveGap <= 5.0d
                && objectiveBreachRate <= 50.0d;
        boolean driftOk = Double.isFinite(avgWeightDistance) && avgWeightDistance <= 20.0d
                && driftBreachRate <= 20.0d;
        boolean pathOk = (!Double.isFinite(sharpeDelta) || sharpeDelta >= -0.05d)
                && (!Double.isFinite(drawdownDelta) || drawdownDelta >= -3.0d)
                && (!Double.isFinite(excessDelta) || excessDelta >= -2.0d);
        if (sampleOk && constraintOk && objectiveOk && driftOk && pathOk) {
            verdict = "V6 candidate";
            tone = "positive";
        } else if (sampleOk && constraintOk && objectiveOk && driftOk) {
            verdict = "Path watch";
            tone = "warning";
        } else if (sampleOk && constraintOk) {
            verdict = "Objective watch";
            tone = "warning";
        } else {
            verdict = "Hold";
            tone = "negative";
        }

        return new StockPortfolioBacktestView.OptimizerShadowPathRow(
                candidateOptimizer,
                integer(rows) + " rows / " + percentUnsigned(usableRate) + " usable",
                percentUnsigned(hardPassRate),
                metricDecimal(avgObjectiveGap) + " / worse " + percentUnsigned(objectiveBreachRate),
                metricPercent(avgWeightDistance) + " / drift " + percentUnsigned(driftBreachRate),
                metricDecimal(sharpeDelta),
                signedPercent(drawdownDelta),
                signedPercent(excessDelta),
                integer((int) betaBreaches),
                integer((int) turnoverBreaches),
                verdict,
                tone);
    }

    private static List<StockPortfolioBacktestView.OptimizerValidationRow> optimizerShadowSummaryRows(
            String candidateOptimizer, List<StockOptimizerShadowSnapshot> snapshots, double annualRiskFreeRatePct) {
        List<StockOptimizerShadowSnapshot> ordered = snapshots.stream()
                .sorted(Comparator.comparing(StockOptimizerShadowSnapshot::getSignalDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int rows = ordered.size();
        long usableRows = ordered.stream()
                .filter(row -> Boolean.TRUE.equals(row.getUsable()))
                .count();
        long passRows = ordered.stream()
                .filter(row -> Boolean.TRUE.equals(row.getUsable()))
                .filter(row -> valueOr(row.getConstraintBreachCount(), 0) == 0)
                .count();
        long objectiveBreachRows = ordered.stream()
                .filter(row -> Boolean.TRUE.equals(row.getObjectiveBreach()))
                .count();
        long weightDistanceBreachRows = ordered.stream()
                .filter(row -> Boolean.TRUE.equals(row.getWeightDistanceBreach()))
                .count();
        double passRate = rows == 0 ? 0.0d : passRows * 100.0d / rows;
        double breachRate = rows == 0 ? 0.0d : (rows - passRows) * 100.0d / rows;
        double objectiveBreachRate = rows == 0 ? 0.0d : objectiveBreachRows * 100.0d / rows;
        double weightDistanceBreachRate = rows == 0 ? 0.0d : weightDistanceBreachRows * 100.0d / rows;
        double usableRate = rows == 0 ? 0.0d : usableRows * 100.0d / rows;
        double avgObjectiveGap = averageDecimal(ordered, StockOptimizerShadowSnapshot::getObjectiveGap);
        double avgWeightDistance = averageDecimal(ordered, StockOptimizerShadowSnapshot::getWeightDistancePct);
        ShadowPathSummary baseline = shadowPathSummary(ordered, true, annualRiskFreeRatePct);
        ShadowPathSummary candidate = shadowPathSummary(ordered, false, annualRiskFreeRatePct);
        double sharpeDelta = finiteDelta(candidate.sharpe(), baseline.sharpe());
        double drawdownDelta = candidate.maxDrawdownPct() - baseline.maxDrawdownPct();
        double excessDelta = candidate.excessReturnPct() - baseline.excessReturnPct();
        String strategy = "ojAlgo materialized shadow / " + candidateOptimizer;

        List<StockPortfolioBacktestView.OptimizerValidationRow> rowsOut = new ArrayList<>();
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "Stored dates",
                "all historical", integer(rows), percentUnsigned(usableRate) + " usable",
                rows >= 20 ? "Tracked" : "Sparse", rows >= 20 ? "positive" : "warning"));
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "Hard constraint pass",
                ">= 80.00%", percentUnsigned(passRate), signedPercent(passRate - 80.0d),
                passRate >= 80.0d ? "Pass" : passRate >= 60.0d ? "Watch" : "Breach",
                passRate >= 80.0d ? "positive" : passRate >= 60.0d ? "warning" : "negative"));
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "Hard constraint breach",
                "<= 20.00%", percentUnsigned(breachRate), signedPercent(breachRate - 20.0d),
                breachRate <= 20.0d ? "Pass" : breachRate <= 40.0d ? "Watch" : "Breach",
                breachRate <= 20.0d ? "positive" : breachRate <= 40.0d ? "warning" : "negative"));
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "Objective worse rate",
                "<= 20.00%", percentUnsigned(objectiveBreachRate), signedPercent(objectiveBreachRate - 20.0d),
                objectiveBreachRate <= 20.0d ? "Pass" : objectiveBreachRate <= 50.0d ? "Watch" : "Worse",
                objectiveBreachRate <= 20.0d ? "positive" : objectiveBreachRate <= 50.0d ? "warning" : "negative"));
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "Weight drift rate",
                "<= 20.00%", percentUnsigned(weightDistanceBreachRate), signedPercent(weightDistanceBreachRate - 20.0d),
                weightDistanceBreachRate <= 20.0d ? "Pass" : weightDistanceBreachRate <= 40.0d ? "Watch" : "Different",
                weightDistanceBreachRate <= 20.0d ? "positive" : weightDistanceBreachRate <= 40.0d ? "warning" : "negative"));
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "Avg objective gap",
                "<= 0.10", metricDecimal(avgObjectiveGap), signedDecimal(avgObjectiveGap - 0.10d),
                avgObjectiveGap <= 0.10d ? "Comparable" : "Worse",
                avgObjectiveGap <= 0.10d ? "positive" : "warning"));
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "Avg weight distance",
                "<= 30.00%", metricPercent(avgWeightDistance), signedPercent(avgWeightDistance - 30.0d),
                avgWeightDistance <= 30.0d ? "Stable" : "Different",
                avgWeightDistance <= 30.0d ? "positive" : "warning"));
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "Sharpe delta",
                ">= 0.00", metricDecimal(sharpeDelta), signedDecimal(sharpeDelta),
                sharpeDelta >= 0.0d ? "Improved" : sharpeDelta >= -0.10d ? "Flat" : "Worse",
                sharpeDelta >= 0.0d ? "positive" : sharpeDelta >= -0.10d ? "neutral" : "warning"));
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "MDD delta",
                ">= 0.00%", signedPercent(drawdownDelta), signedPercent(drawdownDelta),
                drawdownDelta >= 0.0d ? "Lower risk" : drawdownDelta >= -3.0d ? "Flat" : "Worse",
                drawdownDelta >= 0.0d ? "positive" : drawdownDelta >= -3.0d ? "neutral" : "warning"));
        rowsOut.add(customOptimizerValidationRow(strategy, "20D", "20", "Excess delta",
                ">= 0.00%", signedPercent(excessDelta), signedPercent(excessDelta),
                excessDelta >= 0.0d ? "Improved" : excessDelta >= -2.0d ? "Flat" : "Worse",
                excessDelta >= 0.0d ? "positive" : excessDelta >= -2.0d ? "neutral" : "warning"));
        return rowsOut;
    }

    private static double averageDecimal(List<StockOptimizerShadowSnapshot> snapshots,
            Function<StockOptimizerShadowSnapshot, BigDecimal> extractor) {
        return snapshots.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(Double.NaN);
    }

    private static ShadowPathSummary shadowPathSummary(List<StockOptimizerShadowSnapshot> snapshots,
            boolean baseline, double annualRiskFreeRatePct) {
        List<Double> returns = new ArrayList<>();
        List<Double> benchmarkReturns = new ArrayList<>();
        double equity = 1.0d;
        double benchmarkEquity = 1.0d;
        double peak = 1.0d;
        double maxDrawdown = 0.0d;
        for (StockOptimizerShadowSnapshot snapshot : snapshots) {
            if (!baseline && !Boolean.TRUE.equals(snapshot.getUsable())) {
                continue;
            }
            double returnPct = number(baseline ? snapshot.getBaselineNetReturnPct()
                    : snapshot.getCandidateNetReturnPct());
            double benchmarkPct = number(snapshot.getBenchmarkReturnPct());
            if (!Double.isFinite(returnPct)) {
                continue;
            }
            returns.add(returnPct);
            equity *= 1.0d + returnPct / 100.0d;
            peak = Math.max(peak, equity);
            maxDrawdown = Math.min(maxDrawdown, (equity / peak - 1.0d) * 100.0d);
            if (Double.isFinite(benchmarkPct)) {
                benchmarkReturns.add(benchmarkPct);
                benchmarkEquity *= 1.0d + benchmarkPct / 100.0d;
            }
        }
        if (returns.isEmpty()) {
            return ShadowPathSummary.empty();
        }
        double cumulativeReturn = (equity - 1.0d) * 100.0d;
        double benchmarkReturn = (benchmarkEquity - 1.0d) * 100.0d;
        double years = returns.size() * PRIMARY_HORIZON_DAYS / 252.0d;
        double annualReturn = years > 0 ? (Math.pow(equity, 1.0d / years) - 1.0d) * 100.0d : 0.0d;
        double volatility = annualizedReturnVolatility(returns, PRIMARY_HORIZON_DAYS);
        double sharpe = volatility <= 0 ? Double.NaN : (annualReturn - annualRiskFreeRatePct) / volatility;
        return new ShadowPathSummary(cumulativeReturn, benchmarkReturn, cumulativeReturn - benchmarkReturn,
                maxDrawdown, annualReturn, volatility, sharpe);
    }

    private static double annualizedReturnVolatility(List<Double> returnsPct, int horizonDays) {
        if (returnsPct.size() < 2) {
            return 0.0d;
        }
        double mean = returnsPct.stream()
                .mapToDouble(value -> value / 100.0d)
                .average()
                .orElse(0.0d);
        double variance = 0.0d;
        for (double value : returnsPct) {
            double delta = value / 100.0d - mean;
            variance += delta * delta;
        }
        return Math.sqrt(variance / (returnsPct.size() - 1)) * Math.sqrt(252.0d / horizonDays) * 100.0d;
    }

    private static List<StockPortfolioBacktestView.OptimizerValidationRow> optimizerValidationRows(
            List<StrategySummary> summaries, StrategyRun selectedRun, CovarianceLookup covarianceLookup,
            List<StockOptimizerShadowSnapshot> optimizerShadowSnapshots, double annualRiskFreeRatePct) {
        List<StockPortfolioBacktestView.OptimizerValidationRow> rows = new ArrayList<>();
        rows.addAll(optimizerShadowSummaryRows(optimizerShadowSnapshots, annualRiskFreeRatePct));
        summaries.stream()
                .filter(summary -> summary.config().riskMode().quadraticOptimizer())
                .sorted(Comparator
                        .comparingInt((StrategySummary summary) -> summary.config().horizonDays())
                        .thenComparingInt(summary -> summary.config().topCount())
                        .thenComparing(summary -> summary.config().weighting().label()))
                .limit(6)
                .forEach(summary -> {
                    StrategyConfig config = summary.config();
                    String strategy = config.label() + " / " + config.riskMode().label();
                    String horizon = config.horizonDays() + "D";
                    String topCount = integer(config.topCount());
                    double positionCap = config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT;
                    rows.add(optimizerValidationRow(strategy, horizon, topCount, "Beta target",
                            OPTIMIZER_TARGET_BETA, summary.averageBeta(), 0.18d, 0.30d, false));
                    rows.add(optimizerValidationRow(strategy, horizon, topCount, "Volatility target",
                            OPTIMIZER_TARGET_VOLATILITY_PCT, summary.averagePortfolioVolatility(), 3.0d, 6.0d, true));
                    rows.add(optimizerValidationRow(strategy, horizon, topCount, "Sector cap",
                            RISK_SECTOR_CAP * 100.0d, summary.averageMaxSectorWeight() * 100.0d, 1.0d, 6.0d, true));
                    rows.add(optimizerValidationRow(strategy, horizon, topCount, "Position cap",
                            positionCap * 100.0d, summary.averageMaxPositionWeight() * 100.0d, 0.8d, 2.5d, true));
                    rows.add(optimizerValidationRow(strategy, horizon, topCount, "Turnover budget",
                            optimizerTurnoverBudget(config) * 100.0d, summary.averageTurnover() * 100.0d,
                            4.0d, 12.0d, true));
                    rows.add(optimizerValidationRow(strategy, horizon, topCount, "Trade cost",
                            0.20d, summary.averageTransactionCost(), 0.03d, 0.10d, true));
                });
        if (optimizerShadowSnapshots == null || optimizerShadowSnapshots.isEmpty()) {
            rows.addAll(ojAlgoShadowValidationRows(selectedRun, covarianceLookup));
        }
        return rows.stream()
                .sorted(Comparator
                        .comparing((StockPortfolioBacktestView.OptimizerValidationRow row) -> row.tone().equals("negative") ? 0 : row.tone().equals("warning") ? 1 : 2)
                        .thenComparing(StockPortfolioBacktestView.OptimizerValidationRow::strategy)
                        .thenComparing(StockPortfolioBacktestView.OptimizerValidationRow::objective))
                .limit(32)
                .toList();
    }

    private static List<StockPortfolioBacktestView.OptimizerValidationRow> ojAlgoShadowValidationRows(
            StrategyRun selectedRun, CovarianceLookup covarianceLookup) {
        if (selectedRun == null || selectedRun.periods().isEmpty()) {
            return List.of();
        }
        StrategyConfig config = selectedRun.config();
        if (config.riskMode() != RiskMode.OPTIMIZED_V5) {
            return List.of();
        }
        List<PortfolioPeriod> periods = selectedRun.periods();
        PortfolioPeriod latest = periods.get(periods.size() - 1);
        Map<String, Double> previousWeights = periods.size() >= 2
                ? periods.get(periods.size() - 2).weights()
                : Map.of();
        Map<String, Double> alphaScores = optimizerAlphaScores(latest.positions(), latest.riskStatsBySymbol(),
                latest.expectedStatsBySymbol(), previousWeights);
        OjAlgoShadowResult shadow = ojAlgoShadowWeights(latest.positions(), alphaScores, latest.riskStatsBySymbol(),
                previousWeights, config, covarianceLookup, latest.signalDate(), latest.benchmarkSectorWeights());
        String strategy = "Top " + integer(config.topCount()) + " Signal / ojAlgo shadow";
        String horizon = config.horizonDays() + "D";
        String topCount = integer(config.topCount());
        if (!shadow.usable()) {
            return List.of(customOptimizerValidationRow(strategy, horizon, topCount, "QP solve",
                    "feasible", shadow.message(), "-", "Unavailable", "warning"));
        }
        Map<String, Double> v5Weights = latest.weights();
        Map<String, Double> ojAlgoWeights = shadow.weights();
        double v5Objective = optimizerV5PortfolioObjective(latest.positions(), v5Weights, alphaScores,
                latest.riskStatsBySymbol(), previousWeights, config, covarianceLookup, latest.signalDate(),
                latest.benchmarkSectorWeights());
        double ojAlgoObjective = optimizerV5PortfolioObjective(latest.positions(), ojAlgoWeights, alphaScores,
                latest.riskStatsBySymbol(), previousWeights, config, covarianceLookup, latest.signalDate(),
                latest.benchmarkSectorWeights());
        double objectiveGap = ojAlgoObjective - v5Objective;
        double weightDistance = oneWayTurnover(v5Weights, ojAlgoWeights) * 100.0d;
        double ojBeta = weightedRiskMetric(ojAlgoWeights, latest.riskStatsBySymbol(), RiskField.BETA);
        double ojVolatility = portfolioCovarianceVolatilityPct(ojAlgoWeights, latest.riskStatsBySymbol(),
                covarianceLookup, latest.signalDate());
        if (!Double.isFinite(ojVolatility)) {
            ojVolatility = weightedRiskMetric(ojAlgoWeights, latest.riskStatsBySymbol(), RiskField.VOLATILITY);
        }
        double ojMaxSector = sectorWeights(latest.positions(), ojAlgoWeights).values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0d) * 100.0d;
        double ojMaxPosition = ojAlgoWeights.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0d) * 100.0d;
        double ojTurnover = oneWayTurnover(previousWeights, ojAlgoWeights) * 100.0d;
        double positionCap = config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT;

        List<StockPortfolioBacktestView.OptimizerValidationRow> rows = new ArrayList<>();
        rows.add(customOptimizerValidationRow(strategy, horizon, topCount, "QP objective vs v5",
                "v5 " + decimal(v5Objective),
                "ojAlgo " + decimal(ojAlgoObjective),
                signedDecimal(objectiveGap),
                objectiveGap <= 0.02d ? "Comparable" : objectiveGap <= 0.10d ? "Watch" : "Worse",
                objectiveGap <= 0.02d ? "positive" : objectiveGap <= 0.10d ? "warning" : "negative"));
        rows.add(customOptimizerValidationRow(strategy, horizon, topCount, "Weight distance",
                "<= 15.00%", percentUnsigned(weightDistance), signedPercent(weightDistance - 15.0d),
                weightDistance <= 15.0d ? "Stable" : weightDistance <= 30.0d ? "Different" : "Unstable",
                weightDistance <= 15.0d ? "positive" : weightDistance <= 30.0d ? "warning" : "negative"));
        rows.add(optimizerValidationRow(strategy, horizon, topCount, "Beta target",
                OPTIMIZER_TARGET_BETA, ojBeta, 0.18d, 0.30d, false));
        rows.add(optimizerValidationRow(strategy, horizon, topCount, "Volatility target",
                OPTIMIZER_TARGET_VOLATILITY_PCT, ojVolatility, 3.0d, 6.0d, true));
        rows.add(optimizerValidationRow(strategy, horizon, topCount, "Sector cap",
                RISK_SECTOR_CAP * 100.0d, ojMaxSector, 1.0d, 6.0d, true));
        rows.add(optimizerValidationRow(strategy, horizon, topCount, "Position cap",
                positionCap * 100.0d, ojMaxPosition, 0.8d, 2.5d, true));
        rows.add(optimizerValidationRow(strategy, horizon, topCount, "Turnover budget",
                optimizerTurnoverBudget(config) * 100.0d, ojTurnover, 4.0d, 12.0d, true));
        return rows;
    }

    private static StockPortfolioBacktestView.OptimizerValidationRow customOptimizerValidationRow(
            String strategy, String horizon, String topCount, String objective, String target, String observed,
            String gap, String verdict, String tone) {
        return new StockPortfolioBacktestView.OptimizerValidationRow(strategy, horizon, topCount, objective,
                target, observed, gap, verdict, tone);
    }

    private static OjAlgoShadowResult ojAlgoShadowWeights(List<StockBacktestResult> selected,
            Map<String, Double> alphaScores, Map<String, RiskStats> riskStatsBySymbol,
            Map<String, Double> previousWeights, StrategyConfig config, CovarianceLookup covarianceLookup,
            LocalDate signalDate, Map<String, Double> benchmarkSectorWeights) {
        if (selected == null || selected.isEmpty()) {
            return OjAlgoShadowResult.unavailable("empty input");
        }
        try {
            ExpressionsBasedModel model = new ExpressionsBasedModel();
            Map<String, Variable> variables = new LinkedHashMap<>();
            double positionCap = config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT;
            double averageAlpha = Math.max(1.0d, averageAlpha(alphaScores));
            for (StockBacktestResult result : selected) {
                String symbol = result.getSymbol();
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                double normalizedAlpha = Math.max(0.0d, alphaScores.getOrDefault(symbol, averageAlpha) / averageAlpha);
                Variable variable = model.newVariable(symbol)
                        .lower(0.0d)
                        .upper(positionCap)
                        .weight(-0.035d * normalizedAlpha);
                variables.put(symbol, variable);
            }
            if (variables.isEmpty()) {
                return OjAlgoShadowResult.unavailable("no variables");
            }

            Expression budget = model.newExpression("budget").level(1.0d);
            variables.values().forEach(variable -> budget.set(variable, 1.0d));

            Map<String, Expression> sectorConstraints = new LinkedHashMap<>();
            for (StockBacktestResult result : selected) {
                Variable variable = variables.get(result.getSymbol());
                if (variable == null) {
                    continue;
                }
                String sector = fallback(result.getSector());
                sectorConstraints.computeIfAbsent(sector,
                                value -> model.newExpression("sector_" + value.replaceAll("[^A-Za-z0-9]", "_"))
                                        .upper(RISK_SECTOR_CAP))
                        .set(variable, 1.0d);
            }

            Expression variance = model.newExpression("variance").weight(1.0d);
            List<StockBacktestResult> ordered = selected.stream()
                    .filter(result -> variables.containsKey(result.getSymbol()))
                    .toList();
            for (int i = 0; i < ordered.size(); i++) {
                String left = ordered.get(i).getSymbol();
                for (int j = 0; j < ordered.size(); j++) {
                    String right = ordered.get(j).getSymbol();
                    double covariance = covarianceLookup.covariance(signalDate, left, right);
                    if (!Double.isFinite(covariance)) {
                        covariance = fallbackCovariance(left, right, riskStatsBySymbol, covarianceLookup, signalDate);
                    }
                    if (Double.isFinite(covariance)) {
                        variance.set(variables.get(left), variables.get(right), covariance);
                    }
                }
            }

            Optimisation.Result result = model.minimise();
            if (!result.getState().isFeasible()) {
                return OjAlgoShadowResult.unavailable("state=" + result.getState());
            }
            Map<String, Double> weights = new LinkedHashMap<>();
            int index = 0;
            for (String symbol : variables.keySet()) {
                double weight = result.doubleValue(index++);
                if (Double.isFinite(weight) && weight > 0.000001d) {
                    weights.put(symbol, weight);
                }
            }
            weights = projectOptimizerWeights(selected, weights, previousWeights, config);
            weights = reduceActiveSectorDeviation(selected, weights, previousWeights, config, benchmarkSectorWeights);
            weights = applyRiskWeightCaps(selected, weights, config);
            if (weights.isEmpty()) {
                return OjAlgoShadowResult.unavailable("empty solution after projection");
            }
            return new OjAlgoShadowResult(weights, "feasible");
        } catch (RuntimeException ex) {
            return OjAlgoShadowResult.unavailable(ex.getClass().getSimpleName());
        }
    }

    private static OjAlgoShadowResult ojAlgoShadowWeightsV2(List<StockBacktestResult> selected,
            Map<String, Double> alphaScores, Map<String, RiskStats> riskStatsBySymbol,
            Map<String, Double> previousWeights, StrategyConfig config, CovarianceLookup covarianceLookup,
            LocalDate signalDate, Map<String, Double> benchmarkSectorWeights) {
        if (selected == null || selected.isEmpty()) {
            return OjAlgoShadowResult.unavailable("empty input");
        }
        try {
            ExpressionsBasedModel model = new ExpressionsBasedModel();
            Map<String, Variable> variables = new LinkedHashMap<>();
            double positionCap = config.topCount() <= 10 ? RISK_POSITION_CAP_TOP10 : RISK_POSITION_CAP_DEFAULT;
            double averageAlpha = Math.max(1.0d, averageAlpha(alphaScores));
            for (StockBacktestResult result : selected) {
                String symbol = result.getSymbol();
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                double normalizedAlpha = Math.max(0.0d, alphaScores.getOrDefault(symbol, averageAlpha) / averageAlpha);
                Variable variable = model.newVariable(symbol)
                        .lower(0.0d)
                        .upper(positionCap)
                        .weight(-0.050d * normalizedAlpha);
                variables.put(symbol, variable);
            }
            if (variables.isEmpty()) {
                return OjAlgoShadowResult.unavailable("no variables");
            }

            Expression budget = model.newExpression("budget_v2").level(1.0d);
            variables.values().forEach(variable -> budget.set(variable, 1.0d));

            Map<String, Expression> sectorConstraints = new LinkedHashMap<>();
            for (StockBacktestResult result : selected) {
                Variable variable = variables.get(result.getSymbol());
                if (variable == null) {
                    continue;
                }
                String sector = fallback(result.getSector());
                sectorConstraints.computeIfAbsent(sector,
                                value -> model.newExpression("sector_v2_" + safeExpressionName(value))
                                        .upper(RISK_SECTOR_CAP))
                        .set(variable, 1.0d);
            }

            List<StockBacktestResult> ordered = selected.stream()
                    .filter(result -> variables.containsKey(result.getSymbol()))
                    .toList();
            Expression variance = model.newExpression("variance_v2").weight(0.90d);
            for (int i = 0; i < ordered.size(); i++) {
                String left = ordered.get(i).getSymbol();
                for (int j = 0; j < ordered.size(); j++) {
                    String right = ordered.get(j).getSymbol();
                    double covariance = covarianceLookup.covariance(signalDate, left, right);
                    if (!Double.isFinite(covariance)) {
                        covariance = fallbackCovariance(left, right, riskStatsBySymbol, covarianceLookup, signalDate);
                    }
                    if (Double.isFinite(covariance)) {
                        variance.set(variables.get(left), variables.get(right), covariance);
                    }
                }
            }

            addBetaTargetPenalty(model, variables, riskStatsBySymbol, 0.95d);
            addTurnoverPenalty(model, variables, previousWeights, 0.70d);
            addActiveSectorPenalty(model, variables, ordered, benchmarkSectorWeights, 0.65d);
            addConcentrationPenalty(model, variables, 0.18d);

            Optimisation.Result result = model.minimise();
            if (!result.getState().isFeasible()) {
                return OjAlgoShadowResult.unavailable("state=" + result.getState());
            }
            Map<String, Double> weights = new LinkedHashMap<>();
            int index = 0;
            for (String symbol : variables.keySet()) {
                double weight = result.doubleValue(index++);
                if (Double.isFinite(weight) && weight > 0.000001d) {
                    weights.put(symbol, weight);
                }
            }
            weights = projectOptimizerWeights(selected, weights, previousWeights, config);
            weights = reduceActiveSectorDeviation(selected, weights, previousWeights, config, benchmarkSectorWeights);
            weights = applyRiskWeightCaps(selected, weights, config);
            if (weights.isEmpty()) {
                return OjAlgoShadowResult.unavailable("empty solution after projection");
            }
            return new OjAlgoShadowResult(weights, "feasible_v2");
        } catch (RuntimeException ex) {
            return OjAlgoShadowResult.unavailable(ex.getClass().getSimpleName());
        }
    }

    private static void addBetaTargetPenalty(ExpressionsBasedModel model, Map<String, Variable> variables,
            Map<String, RiskStats> riskStatsBySymbol, double weight) {
        Map<String, Double> coefficients = new LinkedHashMap<>();
        for (String symbol : variables.keySet()) {
            double beta = riskStatsBySymbol.getOrDefault(symbol, RiskStats.missing()).beta();
            if (Double.isFinite(beta)) {
                coefficients.put(symbol, beta);
            }
        }
        addSquaredLinearTargetExpression(model, "beta_target_v2", variables, coefficients, OPTIMIZER_TARGET_BETA,
                weight);
    }

    private static void addTurnoverPenalty(ExpressionsBasedModel model, Map<String, Variable> variables,
            Map<String, Double> previousWeights, double weight) {
        if (variables.isEmpty()) {
            return;
        }
        Expression turnover = model.newExpression("turnover_anchor_v2").weight(weight);
        for (Map.Entry<String, Variable> entry : variables.entrySet()) {
            double previous = previousWeights.getOrDefault(entry.getKey(), 0.0d);
            turnover.set(entry.getValue(), -2.0d * previous);
            turnover.set(entry.getValue(), entry.getValue(), 1.0d);
        }
    }

    private static void addActiveSectorPenalty(ExpressionsBasedModel model, Map<String, Variable> variables,
            List<StockBacktestResult> selected, Map<String, Double> benchmarkSectorWeights, double weight) {
        if (variables.isEmpty() || selected.isEmpty()) {
            return;
        }
        Map<String, List<Variable>> variablesBySector = new LinkedHashMap<>();
        for (StockBacktestResult result : selected) {
            Variable variable = variables.get(result.getSymbol());
            if (variable == null) {
                continue;
            }
            variablesBySector.computeIfAbsent(fallback(result.getSector()), ignored -> new ArrayList<>())
                    .add(variable);
        }
        double equalSectorTarget = variablesBySector.isEmpty() ? 0.0d : 1.0d / variablesBySector.size();
        for (Map.Entry<String, List<Variable>> entry : variablesBySector.entrySet()) {
            double target = benchmarkSectorWeights.getOrDefault(entry.getKey(), equalSectorTarget);
            target = Math.min(RISK_SECTOR_CAP, Math.max(0.02d, target));
            Expression sector = model.newExpression("active_sector_v2_" + safeExpressionName(entry.getKey()))
                    .weight(weight);
            for (Variable variable : entry.getValue()) {
                sector.set(variable, -2.0d * target);
                for (Variable other : entry.getValue()) {
                    sector.set(variable, other, 1.0d);
                }
            }
        }
    }

    private static void addConcentrationPenalty(ExpressionsBasedModel model, Map<String, Variable> variables,
            double weight) {
        if (variables.isEmpty()) {
            return;
        }
        Expression concentration = model.newExpression("concentration_v2").weight(weight);
        for (Variable variable : variables.values()) {
            concentration.set(variable, variable, 1.0d);
        }
    }

    private static void addSquaredLinearTargetExpression(ExpressionsBasedModel model, String name,
            Map<String, Variable> variables, Map<String, Double> coefficients, double target, double weight) {
        if (variables.isEmpty() || coefficients.isEmpty()) {
            return;
        }
        Expression expression = model.newExpression(safeExpressionName(name)).weight(weight);
        List<Map.Entry<String, Double>> entries = coefficients.entrySet().stream()
                .filter(entry -> variables.containsKey(entry.getKey()))
                .filter(entry -> Double.isFinite(entry.getValue()))
                .toList();
        for (Map.Entry<String, Double> left : entries) {
            Variable leftVariable = variables.get(left.getKey());
            expression.set(leftVariable, -2.0d * target * left.getValue());
            for (Map.Entry<String, Double> right : entries) {
                expression.set(leftVariable, variables.get(right.getKey()), left.getValue() * right.getValue());
            }
        }
    }

    private static String safeExpressionName(String value) {
        String normalized = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9]", "_");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static StockPortfolioBacktestView.OptimizerValidationRow optimizerValidationRow(String strategy,
            String horizon, String topCount, String objective, double target, double observed, double passGap,
            double watchGap, boolean upperBound) {
        double gap = upperBound ? observed - target : Math.abs(observed - target);
        String verdict;
        String tone;
        if (!Double.isFinite(observed)) {
            verdict = "Missing";
            tone = "warning";
        } else if (gap <= passGap) {
            verdict = "Pass";
            tone = "positive";
        } else if (gap <= watchGap) {
            verdict = "Watch";
            tone = "warning";
        } else {
            verdict = "Breach";
            tone = "negative";
        }
        String gapText = upperBound ? signedPercent(gap) : signedDecimal(observed - target);
        String targetText = upperBound ? "<= " + metricPercent(target) : metricDecimal(target);
        String observedText = upperBound ? metricPercent(observed) : metricDecimal(observed);
        return new StockPortfolioBacktestView.OptimizerValidationRow(strategy, horizon, topCount, objective,
                targetText, observedText, gapText, verdict, tone);
    }

    private static RiskImpact riskImpact(StrategySummary base, StrategySummary risk) {
        if (base == null) {
            return null;
        }
        double baseSharpe = base.sharpe() == null ? 0 : base.sharpe();
        double riskSharpe = risk.sharpe() == null ? 0 : risk.sharpe();
        double sharpeDelta = riskSharpe - baseSharpe;
        double drawdownDelta = risk.maxDrawdown() - base.maxDrawdown();
        double betaDelta = finiteDelta(risk.averageBeta(), base.averageBeta());
        double sectorDelta = risk.averageMaxSectorWeight() - base.averageMaxSectorWeight();
        double costDelta = risk.averageTransactionCost() - base.averageTransactionCost();
        double excessDelta = risk.excessReturn() - base.excessReturn();

        String verdict;
        String tone;
        if (sharpeDelta > 0.05d && drawdownDelta > 0 && betaDelta <= 0 && sectorDelta <= 0) {
            verdict = "Improved";
            tone = "positive";
        } else if (drawdownDelta > 0 && betaDelta <= 0 && sharpeDelta >= -0.05d) {
            verdict = "Lower risk";
            tone = "positive";
        } else if (sharpeDelta < -0.10d && excessDelta < 0) {
            verdict = "Too costly";
            tone = "negative";
        } else {
            verdict = "Mixed";
            tone = "neutral";
        }
        return new RiskImpact(base, risk, sharpeDelta, drawdownDelta, betaDelta, sectorDelta, costDelta,
                excessDelta, verdict, tone);
    }

    private static double finiteDelta(double right, double left) {
        if (!Double.isFinite(right) || !Double.isFinite(left)) {
            return Double.NaN;
        }
        return right - left;
    }

    private static List<StockPortfolioBacktestView.PeriodRow> recentPeriods(StrategyRun run) {
        double equity = 1.0d;
        double benchmarkEquity = 1.0d;
        List<PeriodWithEquity> periods = new ArrayList<>();
        for (PortfolioPeriod period : run.periods()) {
            equity *= 1.0d + period.netReturnPct() / 100.0d;
            benchmarkEquity *= 1.0d + period.benchmarkReturnPct() / 100.0d;
            periods.add(new PeriodWithEquity(period, equity, benchmarkEquity));
        }
        return periods.stream()
                .skip(Math.max(0, periods.size() - 12))
                .map(row -> new StockPortfolioBacktestView.PeriodRow(
                        date(row.period().signalDate()),
                        run.config().fullLabel(),
                        run.config().horizonDays() + "D",
                        integer(row.period().positions().size()),
                        percent(row.period().grossReturnPct()),
                        percent(row.period().netReturnPct()),
                        percent(row.period().benchmarkReturnPct()),
                        percent(row.period().netReturnPct() - row.period().benchmarkReturnPct()),
                        percentUnsigned(row.period().turnover() * 100.0d),
                        percentUnsigned(row.period().transactionCostPct()),
                        percentUnsigned(cashWeight(row.period().weights()) * 100.0d),
                        metricDecimal(row.period().portfolioBeta()),
                        metricPercent(row.period().portfolioVolatilityPct()),
                        dollarVolume(row.period().portfolioLiquidity()),
                        multiple(row.equity()),
                        multiple(row.benchmarkEquity()),
                        returnTone(row.period().netReturnPct() - row.period().benchmarkReturnPct())))
                .toList();
    }

    private static List<StockPortfolioBacktestView.SectorExposureRow> sectorExposures(StrategyRun run) {
        Map<String, SectorStats> stats = new HashMap<>();
        for (PortfolioPeriod period : run.periods()) {
            for (Map.Entry<String, Double> entry : period.sectorWeights().entrySet()) {
                stats.computeIfAbsent(entry.getKey(), ignored -> new SectorStats()).add(entry.getValue());
            }
        }
        return stats.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, SectorStats> entry) -> entry.getValue().average())
                        .reversed())
                .limit(12)
                .map(entry -> new StockPortfolioBacktestView.SectorExposureRow(
                        run.config().fullLabel(),
                        run.config().horizonDays() + "D",
                        entry.getKey(),
                        percentUnsigned(entry.getValue().average() * 100.0d),
                        percentUnsigned(entry.getValue().max() * 100.0d),
                        entry.getValue().max() > 0.35d ? "caution" : "neutral"))
                .toList();
    }

    private static List<StockPortfolioBacktestView.PositionRow> latestPositions(StrategyRun run) {
        if (run.periods().isEmpty()) {
            return List.of();
        }
        PortfolioPeriod latest = run.periods().get(run.periods().size() - 1);
        List<StockBacktestResult> sorted = latest.positions().stream()
                .sorted(Comparator
                        .comparing((StockBacktestResult result) -> latest.weights().getOrDefault(result.getSymbol(), 0.0d))
                        .reversed()
                        .thenComparing(StockBacktestResult::getSymbol))
                .toList();
        List<StockPortfolioBacktestView.PositionRow> rows = new ArrayList<>();
        int rank = 1;
        for (StockBacktestResult result : sorted) {
            ExpectedReturnStats expected = latest.expectedStats(result.getSymbol());
            rows.add(new StockPortfolioBacktestView.PositionRow(
                    integer(rank++),
                    result.getSymbol(),
                    fallback(result.getName()),
                    fallback(result.getSector()),
                    integer(score(result)),
                    percentUnsigned(latest.weights().getOrDefault(result.getSymbol(), 0.0d) * 100.0d),
                    signedPercent(expected.expectedExcessReturnPct()),
                    expectedRange(expected),
                    metricPercent(displayUpsideProbability(expected)),
                    expected.confidence() <= 0 ? "-" : integer(expected.confidence()) + " / 100",
                    percent(returnPct(result)),
                    metricDecimal(latest.riskStats(result.getSymbol()).beta()),
                    metricPercent(latest.riskStats(result.getSymbol()).volatilityPct()),
                    dollarVolume(latest.riskStats(result.getSymbol()).avgDollarVolume()),
                    returnTone(returnPct(result))));
        }
        return rows;
    }

    private LivePortfolioRecommendation liveRecommendation(StockBacktestMapper mapper, String indexCode,
            CovarianceLookup covarianceLookup) {
        LocalDate targetDate = LocalDate.now();
        List<StockBacktestResult> universe = mapper.findLatestSignalRows(indexCode).stream()
                .filter(row -> row.getSymbol() != null && row.getIntegratedScore() != null)
                .toList();
        if (universe.size() < 10) {
            return LivePortfolioRecommendation.empty("Live Signal rows are not available.");
        }

        StrategyConfig config = new StrategyConfig(PRIMARY_HORIZON_DAYS, 20, Weighting.SIGNAL, RiskMode.OPTIMIZED_V5);
        RiskLookup riskLookup = riskLookup(mapper, indexCode, targetDate, targetDate);
        ExpectedReturnLookup expectedReturnLookup = ExpectedReturnLookup.from(
                mapper.findExpectedReturnSnapshots(indexCode, targetDate, targetDate));
        List<StockBacktestResult> selected = selectPositions(config, targetDate, universe, riskLookup,
                expectedReturnLookup);
        if (selected.isEmpty()) {
            return LivePortfolioRecommendation.empty("No live positions passed the optimizer input filters.");
        }

        Map<String, RiskStats> riskStatsBySymbol = riskStatsBySymbol(selected, riskLookup, targetDate);
        Map<String, ExpectedReturnStats> expectedStatsBySymbol = expectedStatsBySymbol(selected, expectedReturnLookup,
                targetDate, config.horizonDays());
        Map<String, Double> weights = optimizedWeights(selected, riskStatsBySymbol, expectedStatsBySymbol, Map.of(),
                config, covarianceLookup, targetDate, benchmarkSectorWeights(universe));
        if (weights.isEmpty()) {
            return LivePortfolioRecommendation.empty("The live optimizer produced no investable weights.");
        }

        double expectedExcess = weightedExpectedExcess(weights, expectedStatsBySymbol);
        double upsideProbability = weightedUpsideProbability(weights, expectedStatsBySymbol);
        double beta = weightedRiskMetric(selected, weights, riskLookup, targetDate, RiskField.BETA);
        double covarianceVolatility = portfolioCovarianceVolatilityPct(weights, riskStatsBySymbol, covarianceLookup,
                targetDate);
        double trailingVolatility = weightedRiskMetric(selected, weights, riskLookup, targetDate, RiskField.VOLATILITY);
        double displayedVolatility = Double.isFinite(covarianceVolatility) ? covarianceVolatility : trailingVolatility;
        double liquidity = weightedRiskMetric(selected, weights, riskLookup, targetDate, RiskField.LIQUIDITY);
        Map<String, Double> liveSectorWeights = sectorWeights(selected, weights);
        Map<String, Double> benchmarkSectorWeights = benchmarkSectorWeights(universe);
        double activeSector = activeSectorDeviation(liveSectorWeights, benchmarkSectorWeights);
        double maxPosition = weights.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0d);
        double maxSector = liveSectorWeights.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0d);
        double correlationCrowding = weightedCorrelationCrowding(weights, covarianceLookup, targetDate);

        List<StockPortfolioBacktestView.MetricCard> cards = List.of(
                new StockPortfolioBacktestView.MetricCard("Live date", date(targetDate),
                        "stock_signal_latest + EXPECTED_RETURN_V9", "neutral"),
                new StockPortfolioBacktestView.MetricCard("Expected alpha", signedPercent(expectedExcess),
                        "20D expected excess return", returnTone(expectedExcess)),
                new StockPortfolioBacktestView.MetricCard("Upside probability", metricPercent(upsideProbability),
                        "calibrated weighted probability", upsideProbability >= 52.0d ? "positive" : "neutral"),
                new StockPortfolioBacktestView.MetricCard("Beta / Vol",
                        metricDecimal(beta) + " / " + metricPercent(displayedVolatility),
                        "target beta " + metricDecimal(OPTIMIZER_TARGET_BETA), beta > 1.15d ? "caution" : "neutral"),
                new StockPortfolioBacktestView.MetricCard("Liquidity", dollarVolume(liquidity),
                        "weighted avg dollar volume", "neutral"),
                new StockPortfolioBacktestView.MetricCard("Active sector", percentUnsigned(activeSector * 100.0d),
                        "benchmark-relative exposure", activeSector > OPTIMIZER_ACTIVE_SECTOR_LIMIT ? "caution" : "positive"),
                new StockPortfolioBacktestView.MetricCard("Max position", percentUnsigned(maxPosition * 100.0d),
                        "cap " + percentUnsigned(RISK_POSITION_CAP_DEFAULT * 100.0d),
                        maxPosition > RISK_POSITION_CAP_DEFAULT ? "caution" : "positive"),
                new StockPortfolioBacktestView.MetricCard("Max sector", percentUnsigned(maxSector * 100.0d),
                        "cap " + percentUnsigned(RISK_SECTOR_CAP * 100.0d),
                        maxSector > RISK_SECTOR_CAP ? "caution" : "positive"),
                new StockPortfolioBacktestView.MetricCard("Corr crowding", metricDecimal(correlationCrowding),
                        "weighted positive pair correlation", correlationCrowding > 0.45d ? "caution" : "neutral"));

        return new LivePortfolioRecommendation(cards,
                livePositionRows(targetDate, selected, weights, riskLookup, expectedStatsBySymbol));
    }

    private static double weightedCorrelationCrowding(Map<String, Double> weights, CovarianceLookup covarianceLookup,
            LocalDate targetDate) {
        if (weights == null || weights.isEmpty() || covarianceLookup == null || covarianceLookup.isEmpty()) {
            return Double.NaN;
        }
        double weighted = 0.0d;
        double total = 0.0d;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            double weight = entry.getValue() == null ? 0.0d : entry.getValue();
            if (weight <= 0.0d) {
                continue;
            }
            double crowding = correlationCrowding(entry.getKey(), weights, covarianceLookup, targetDate);
            if (!Double.isFinite(crowding)) {
                continue;
            }
            weighted += weight * crowding;
            total += weight;
        }
        return total <= 0.0d ? Double.NaN : weighted / total;
    }

    private static List<StockPortfolioBacktestView.PositionRow> livePositionRows(LocalDate targetDate,
            List<StockBacktestResult> selected, Map<String, Double> weights, RiskLookup riskLookup,
            Map<String, ExpectedReturnStats> expectedStatsBySymbol) {
        return selected.stream()
                .sorted(Comparator
                        .comparing((StockBacktestResult result) -> weights.getOrDefault(result.getSymbol(), 0.0d))
                        .reversed()
                        .thenComparing(StockBacktestResult::getSymbol))
                .limit(30)
                .map(new Function<StockBacktestResult, StockPortfolioBacktestView.PositionRow>() {
                    private int rank = 1;

                    @Override
                    public StockPortfolioBacktestView.PositionRow apply(StockBacktestResult result) {
                        ExpectedReturnStats expected = expectedStatsBySymbol.getOrDefault(result.getSymbol(),
                                ExpectedReturnStats.missing());
                        RiskStats risk = riskLookup.stats(result.getSymbol(), targetDate);
                        double expectedExcess = expected.expectedExcessReturnPct();
                        return new StockPortfolioBacktestView.PositionRow(
                                integer(rank++),
                                result.getSymbol(),
                                fallback(result.getName()),
                                fallback(result.getSector()),
                                integer(score(result)),
                                percentUnsigned(weights.getOrDefault(result.getSymbol(), 0.0d) * 100.0d),
                                signedPercent(expectedExcess),
                                expectedRange(expected),
                                metricPercent(displayUpsideProbability(expected)),
                                expected.confidence() <= 0 ? "-" : integer(expected.confidence()) + " / 100",
                                "-",
                                metricDecimal(risk.beta()),
                                metricPercent(risk.volatilityPct()),
                                dollarVolume(risk.avgDollarVolume()),
                                Double.isFinite(expectedExcess) ? returnTone(expectedExcess) : returnTone(score(result) - 50.0d));
                    }
                })
                .toList();
    }

    private static double weightedExpectedExcess(Map<String, Double> weights,
            Map<String, ExpectedReturnStats> expectedStatsBySymbol) {
        double weighted = 0.0d;
        double total = 0.0d;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            ExpectedReturnStats expected = expectedStatsBySymbol.getOrDefault(entry.getKey(), ExpectedReturnStats.missing());
            if (!Double.isFinite(expected.expectedExcessReturnPct()) || entry.getValue() <= 0) {
                continue;
            }
            weighted += entry.getValue() * expected.expectedExcessReturnPct();
            total += entry.getValue();
        }
        return total <= 0 ? Double.NaN : weighted / total;
    }

    private static double weightedUpsideProbability(Map<String, Double> weights,
            Map<String, ExpectedReturnStats> expectedStatsBySymbol) {
        double weighted = 0.0d;
        double total = 0.0d;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            ExpectedReturnStats expected = expectedStatsBySymbol.getOrDefault(entry.getKey(), ExpectedReturnStats.missing());
            double upside = displayUpsideProbability(expected);
            if (!Double.isFinite(upside) || entry.getValue() <= 0) {
                continue;
            }
            weighted += entry.getValue() * upside;
            total += entry.getValue();
        }
        return total <= 0 ? Double.NaN : weighted / total;
    }

    private static List<String> notes(List<StockBacktestResult> results) {
        LocalDate minDate = results.stream().map(StockBacktestResult::getSignalDate).min(LocalDate::compareTo).orElse(null);
        LocalDate maxDate = results.stream().map(StockBacktestResult::getSignalDate).max(LocalDate::compareTo).orElse(null);
        return List.of(
                "v1 composes stored stock_signal_backtest_result rows into portfolio-level performance.",
                "Risk Model v1 adds a risk-controlled candidate with sector caps, position caps, trailing volatility, dollar-volume liquidity, and beta.",
                "Portfolio Backtest v2 uses a dynamic transaction-cost model and compares Base vs Risk-controlled Sharpe, MDD, beta, sector concentration, cost, and excess return.",
                "Expected Return Model v8 adds FRED macro feature adjustment on top of v7 horizon-decay stabilized outputs.",
                "Portfolio Optimization v5 weights Signal alpha with expected return, beta target, volatility target, liquidity, transaction cost, turnover budget, sector/position caps, active-sector control, concentration control, and a multi-start projected-gradient covariance objective.",
                "Rebalance dates are historical signal snapshot dates. Weekly validation can reuse this engine after weekly snapshot seeding.",
                "Benchmark uses stock_market_snapshot market-cap weights when available and falls back only when coverage is missing.",
                "Beta uses stored stock_benchmark_return_series when available, then falls back to an equal-weight universe proxy.",
                "Validation window: " + date(minDate) + " ~ " + date(maxDate));
    }

    private static List<StockPortfolioBacktestView.HealthAlert> modelHealthAlerts(StockBacktestMapper mapper,
            String indexCode) {
        if (mapper == null) {
            return List.of(new StockPortfolioBacktestView.HealthAlert("Model Health", "blocked",
                    "Mapper is not available for model health validation.", "negative"));
        }
        List<StockQuantModelHealthMetric> metrics = mapper.findQuantModelHealth(indexCode);
        if (metrics == null || metrics.isEmpty()) {
            return List.of(new StockPortfolioBacktestView.HealthAlert("Model Health", "blocked",
                    "No model health rows were returned for this universe.", "negative"));
        }
        List<StockPortfolioBacktestView.HealthAlert> alerts = metrics.stream()
                .map(StockPortfolioBacktestService::toModelHealthAlert)
                .filter(Objects::nonNull)
                .limit(6)
                .toList();
        if (alerts.isEmpty()) {
            return List.of(new StockPortfolioBacktestView.HealthAlert("Model Health", "normal",
                    "Core model layers are fresh enough for portfolio review.", "positive"));
        }
        return alerts;
    }

    private static StockPortfolioBacktestView.HealthAlert toModelHealthAlert(StockQuantModelHealthMetric metric) {
        long rows = metric.getRowCount() == null ? 0L : metric.getRowCount();
        String key = metric.getMetricKey() == null ? "" : metric.getMetricKey();
        if (rows <= 0) {
            if ("RISK_FREE".equals(key)) {
                return new StockPortfolioBacktestView.HealthAlert(metric.getLayer(), "fallback",
                        "Risk-free series is missing. Portfolio Sharpe uses configured fallback cash rate.",
                        "warning");
            }
            return new StockPortfolioBacktestView.HealthAlert(metric.getLayer(), "missing",
                    "Required model layer has no stored rows.", "negative");
        }
        if ("UNIVERSE".equals(key) || "SIGNAL_LATEST".equals(key) || "DATA_QUALITY".equals(key)) {
            return null;
        }
        if ("RISK_FREE".equals(key) && metric.getNote() != null
                && metric.getNote().toLowerCase(Locale.ROOT).contains("fallback")) {
            return new StockPortfolioBacktestView.HealthAlert(metric.getLayer(), "fallback",
                    metric.getNote(), "warning");
        }

        LocalDate latest = metric.getLatestDate();
        if (latest == null && metric.getLatestCalculatedAt() != null) {
            latest = metric.getLatestCalculatedAt().toLocalDate();
        }
        if (latest == null) {
            return new StockPortfolioBacktestView.HealthAlert(metric.getLayer(), "check",
                    "Latest timestamp is unavailable; verify this layer before relying on the result.",
                    "warning");
        }

        long age = ChronoUnit.DAYS.between(latest, LocalDate.now());
        long staleDays = modelHealthStaleDays(key);
        if (age <= staleDays) {
            return null;
        }
        String status = age <= staleDays * 3 ? "delayed" : "stale";
        return new StockPortfolioBacktestView.HealthAlert(metric.getLayer(), status,
                "Latest " + latest + ", age " + age + " days. " + metric.getNote(),
                "warning");
    }

    private static long modelHealthStaleDays(String key) {
        return switch (key) {
            case "MARKET_SNAPSHOT", "BENCHMARK", "MACRO_REGIME" -> 7;
            case "RISK_SNAPSHOT" -> 14;
            case "EXPECTED_RETURN_V4", "EXPECTED_RETURN_V5", "EXPECTED_RETURN_V6", "EXPECTED_RETURN_V7",
                    "EXPECTED_RETURN_V9",
                    "FACTOR_EXPOSURE", "FACTOR_CONTRIBUTION", "COVARIANCE" -> 45;
            default -> 30;
        };
    }

    private static StockPortfolioBacktestView empty(String indexCode) {
        return new StockPortfolioBacktestView(
                indexCode,
                "S&P 500 market-cap weighted proxy",
                "Dynamic transaction cost: base " + percentUnsigned(BASE_TRADE_COST_PCT)
                        + " plus liquidity, volatility, and beta impact",
                List.of(new StockPortfolioBacktestView.MetricCard("Portfolio samples", "0",
                        "completed signal backtest rows required", "caution")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Portfolio Backtest requires completed stock_signal_backtest_result rows."));
    }

    private static int score(StockBacktestResult result) {
        return result.getIntegratedScore() == null ? 50 : result.getIntegratedScore();
    }

    private static double returnPct(StockBacktestResult result) {
        return result.getForwardReturnPct() == null ? 0 : result.getForwardReturnPct().doubleValue();
    }

    private static StockRiskSnapshot toRiskSnapshot(RiskKey key, RiskStats stats) {
        StockRiskSnapshot snapshot = new StockRiskSnapshot();
        snapshot.setSymbol(key.symbol());
        snapshot.setSignalDate(key.signalDate());
        snapshot.setBeta(decimalOrNull(stats.beta()));
        snapshot.setVolatilityPct(decimalOrNull(stats.volatilityPct()));
        snapshot.setAvgDollarVolume(decimalOrNull(stats.avgDollarVolume()));
        snapshot.setObservations(stats.observations());
        snapshot.setSource("CANDLE_TRAILING");
        return snapshot;
    }

    private static StockRiskFreeRateSnapshot riskFreeRateSnapshot(String indexCode, String seriesCode,
            LocalDate rateDate, double annualRatePct, String source) {
        StockRiskFreeRateSnapshot snapshot = new StockRiskFreeRateSnapshot();
        snapshot.setIndexCode(normalizeIndexCode(indexCode));
        snapshot.setSeriesCode(seriesCode == null || seriesCode.isBlank() ? DEFAULT_RISK_FREE_SERIES_CODE
                : seriesCode.trim().toUpperCase(Locale.ROOT));
        snapshot.setRateDate(rateDate);
        snapshot.setAnnualRatePct(decimalOrNull(annualRatePct));
        snapshot.setSource(source == null || source.isBlank() ? "CONFIG_FIXED" : source.trim());
        return snapshot;
    }

    private static BigDecimal decimalOrNull(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value) : null;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0d;
    }

    private static double shift(double value, double adjustment) {
        return Double.isFinite(value) ? value + adjustment : Double.NaN;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return Double.NaN;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double trimmedAverage(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = values.stream()
                .filter(Double::isFinite)
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return Double.NaN;
        }
        int trim = sorted.size() >= 20 ? Math.max(1, (int) Math.floor(sorted.size() * 0.10d)) : 0;
        int from = Math.min(trim, sorted.size() - 1);
        int to = Math.max(from + 1, sorted.size() - trim);
        return sorted.subList(from, to).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);
    }

    private static double investedWeight(Map<String, Double> weights) {
        return weights.values().stream()
                .filter(Double::isFinite)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static double cashWeight(Map<String, Double> weights) {
        return Math.max(0.0d, 1.0d - investedWeight(weights));
    }

    private static String returnTone(double value) {
        if (value > 0) {
            return "positive";
        }
        if (value < 0) {
            return "negative";
        }
        return "neutral";
    }

    private static String integer(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String percent(double value) {
        return String.format(Locale.US, "%+.2f%%", value);
    }

    private static String signedPercent(double value) {
        return Double.isFinite(value) ? percent(value) : "-";
    }

    private static String percentUnsigned(double value) {
        return String.format(Locale.US, "%.2f%%", value);
    }

    private static String metricPercent(double value) {
        return Double.isFinite(value) ? percentUnsigned(value) : "-";
    }

    private static String decimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String signedDecimal(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%+.2f", value) : "-";
    }

    private static String metricDecimal(double value) {
        return Double.isFinite(value) ? decimal(value) : "-";
    }

    private static String expectedRange(ExpectedReturnStats expected) {
        if (!Double.isFinite(expected.returnP10Pct())
                || !Double.isFinite(expected.returnP50Pct())
                || !Double.isFinite(expected.returnP90Pct())) {
            return "-";
        }
        return signedPercent(expected.returnP10Pct()) + " / "
                + signedPercent(expected.returnP50Pct()) + " / "
                + signedPercent(expected.returnP90Pct());
    }

    private static double displayUpsideProbability(ExpectedReturnStats expected) {
        return Double.isFinite(expected.calibratedUpsideProbabilityPct())
                ? expected.calibratedUpsideProbabilityPct()
                : expected.upsideProbabilityPct();
    }

    private static String dollarVolume(double value) {
        if (!Double.isFinite(value)) {
            return "-";
        }
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000.0d) {
            return String.format(Locale.US, "$%.1fB", value / 1_000_000_000.0d);
        }
        if (absolute >= 1_000_000.0d) {
            return String.format(Locale.US, "$%.1fM", value / 1_000_000.0d);
        }
        return String.format(Locale.US, "$%,.0f", value);
    }

    private static String multiple(double value) {
        return String.format(Locale.US, "%.2fx", value);
    }

    private static String betaTone(double value) {
        if (!Double.isFinite(value)) {
            return "caution";
        }
        if (value > 1.25d) {
            return "caution";
        }
        return value < 0.75d ? "positive" : "neutral";
    }

    private static String volatilityTone(double value) {
        if (!Double.isFinite(value)) {
            return "caution";
        }
        if (value > 45.0d) {
            return "caution";
        }
        return value < 28.0d ? "positive" : "neutral";
    }

    private static String date(LocalDate value) {
        return value == null ? "-" : value.toString();
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private enum Weighting {
        EQUAL("Equal weight"),
        SIGNAL("Signal weight"),
        MARKET_CAP_ADJUSTED("Market-cap adjusted");

        private final String label;

        Weighting(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private enum RiskMode {
        BASE("Base", false, false, false, false),
        RISK_CONTROLLED("Risk controlled", true, false, false, false),
        OPTIMIZED("Optimized", true, true, false, false, false, false),
        OPTIMIZED_V2("Optimized v2", true, true, true, false, false, false),
        OPTIMIZED_V3("Optimized v3", true, true, true, true, false, false),
        OPTIMIZED_V4("Optimized v4", true, true, true, true, true),
        OPTIMIZED_V5("Optimized v5", true, true, true, true, true);

        private final String label;
        private final boolean controlled;
        private final boolean optimized;
        private final boolean covarianceAware;
        private final boolean explicitOptimizer;
        private final boolean quadraticOptimizer;
        private final boolean defaultComparison;

        RiskMode(String label, boolean controlled, boolean optimized, boolean covarianceAware,
                boolean explicitOptimizer) {
            this(label, controlled, optimized, covarianceAware, explicitOptimizer, false);
        }

        RiskMode(String label, boolean controlled, boolean optimized, boolean covarianceAware,
                boolean explicitOptimizer, boolean quadraticOptimizer) {
            this(label, controlled, optimized, covarianceAware, explicitOptimizer, quadraticOptimizer, true);
        }

        RiskMode(String label, boolean controlled, boolean optimized, boolean covarianceAware,
                boolean explicitOptimizer, boolean quadraticOptimizer, boolean defaultComparison) {
            this.label = label;
            this.controlled = controlled;
            this.optimized = optimized;
            this.covarianceAware = covarianceAware;
            this.explicitOptimizer = explicitOptimizer;
            this.quadraticOptimizer = quadraticOptimizer;
            this.defaultComparison = defaultComparison;
        }

        private String label() {
            return label;
        }

        private boolean controlled() {
            return controlled;
        }

        private boolean optimized() {
            return optimized;
        }

        private boolean covarianceAware() {
            return covarianceAware;
        }

        private boolean explicitOptimizer() {
            return explicitOptimizer;
        }

        private boolean quadraticOptimizer() {
            return quadraticOptimizer;
        }

        private boolean defaultComparison() {
            return defaultComparison;
        }
    }

    private enum RiskField {
        BETA,
        VOLATILITY,
        LIQUIDITY
    }

    private record StrategyConfig(int horizonDays, int topCount, Weighting weighting, RiskMode riskMode) {
        private String label() {
            return "Top " + topCount + " " + weighting.label();
        }

        private String fullLabel() {
            return label() + " / " + riskMode.label();
        }
    }

    private record StrategyKey(int horizonDays, int topCount, Weighting weighting) {
        private static StrategyKey from(StrategyConfig config) {
            return new StrategyKey(config.horizonDays(), config.topCount(), config.weighting());
        }
    }

    private record StrategyRun(StrategyConfig config, List<PortfolioPeriod> periods) {
    }

    private record PortfolioPeriod(
            LocalDate signalDate,
            List<StockBacktestResult> positions,
            Map<String, Double> weights,
            Map<String, RiskStats> riskStatsBySymbol,
            Map<String, ExpectedReturnStats> expectedStatsBySymbol,
            Map<String, Double> sectorWeights,
            Map<String, Double> benchmarkSectorWeights,
            double grossReturnPct,
            double netReturnPct,
            double benchmarkReturnPct,
            double turnover,
            double transactionCostPct,
            double riskFreeAnnualRatePct,
            double riskFreePeriodReturnPct,
            double cashReturnPct,
            double portfolioBeta,
            double portfolioVolatilityPct,
            double portfolioLiquidity,
            double activeSectorDeviation) {

        private RiskStats riskStats(String symbol) {
            return riskStatsBySymbol.getOrDefault(symbol, RiskStats.missing());
        }

        private ExpectedReturnStats expectedStats(String symbol) {
            return expectedStatsBySymbol.getOrDefault(symbol, ExpectedReturnStats.missing());
        }
    }

    private record StrategySummary(
            StrategyConfig config,
            int rebalanceCount,
            double cumulativeReturn,
            double benchmarkReturn,
            double excessReturn,
            double annualReturn,
            double volatility,
            Double sharpe,
            double maxDrawdown,
            double benchmarkBeatRate,
            double averageTurnover,
            double averageTransactionCost,
            double averageInvestedWeight,
            double averageMaxSectorWeight,
            double averageMaxPositionWeight,
            double averageActiveSectorDeviation,
            double averageBeta,
            double averagePortfolioVolatility,
            double averageLiquidity,
            double averageRiskFreeRate,
            double bestPeriodReturn,
            double worstPeriodReturn) {

        private double sharpeSort() {
            return sharpe == null ? -999.0d : sharpe;
        }

        private double excessReturnSort() {
            return excessReturn;
        }
    }

    private record RiskImpact(
            StrategySummary base,
            StrategySummary risk,
            double sharpeDelta,
            double drawdownDelta,
            double betaDelta,
            double sectorDelta,
            double costDelta,
            double excessDelta,
            String verdict,
            String tone) {

        private double sortScore() {
            double score = sharpeDelta * 10.0d + drawdownDelta + excessDelta * 0.05d;
            if (Double.isFinite(betaDelta)) {
                score -= Math.max(0.0d, betaDelta) * 2.0d;
            }
            score -= Math.max(0.0d, sectorDelta * 100.0d) * 0.2d;
            score -= Math.max(0.0d, costDelta) * 2.0d;
            return score;
        }
    }

    private record OjAlgoShadowResult(Map<String, Double> weights, String message) {

        private static OjAlgoShadowResult unavailable(String message) {
            return new OjAlgoShadowResult(Map.of(), message);
        }

        private boolean usable() {
            return weights != null && !weights.isEmpty();
        }
    }

    private record ShadowPathSummary(
            double cumulativeReturnPct,
            double benchmarkReturnPct,
            double excessReturnPct,
            double maxDrawdownPct,
            double annualReturnPct,
            double volatilityPct,
            double sharpe) {

        private static ShadowPathSummary empty() {
            return new ShadowPathSummary(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN);
        }
    }

    private record PeriodWithEquity(PortfolioPeriod period, double equity, double benchmarkEquity) {
    }

    private static final class RiskLookup {
        private final Map<RiskKey, RiskStats> storedByKey;
        private final Map<String, List<DailyReturn>> returnsBySymbol;
        private final Map<LocalDate, Double> benchmarkReturns;
        private final Map<RiskKey, RiskStats> cache = new HashMap<>();

        private RiskLookup(Map<RiskKey, RiskStats> storedByKey, Map<String, List<DailyReturn>> returnsBySymbol,
                Map<LocalDate, Double> benchmarkReturns) {
            this.storedByKey = storedByKey;
            this.returnsBySymbol = returnsBySymbol;
            this.benchmarkReturns = benchmarkReturns;
        }

        private static RiskLookup empty() {
            return new RiskLookup(Map.of(), Map.of(), Map.of());
        }

        private static RiskLookup from(List<StockCandleDaily> candles, List<StockRiskSnapshot> snapshots,
                List<StockBenchmarkReturn> storedBenchmarkReturns) {
            Map<RiskKey, RiskStats> storedByKey = riskSnapshots(snapshots);
            if (candles == null || candles.isEmpty()) {
                return new RiskLookup(storedByKey, Map.of(), Map.of());
            }
            Map<String, List<StockCandleDaily>> candlesBySymbol = candles.stream()
                    .filter(candle -> candle.getSymbol() != null
                            && candle.getTradeDate() != null
                            && candle.getClosePrice() != null
                            && candle.getClosePrice().signum() > 0)
                    .collect(Collectors.groupingBy(StockCandleDaily::getSymbol, LinkedHashMap::new, Collectors.toList()));

            Map<String, List<DailyReturn>> returnsBySymbol = new LinkedHashMap<>();
            Map<LocalDate, DoubleSummaryStatistics> benchmarkStats = new HashMap<>();
            for (Map.Entry<String, List<StockCandleDaily>> entry : candlesBySymbol.entrySet()) {
                List<StockCandleDaily> sorted = entry.getValue().stream()
                        .sorted(Comparator.comparing(StockCandleDaily::getTradeDate))
                        .toList();
                List<DailyReturn> returns = new ArrayList<>();
                StockCandleDaily previous = null;
                for (StockCandleDaily candle : sorted) {
                    if (previous != null
                            && previous.getClosePrice() != null
                            && previous.getClosePrice().signum() > 0
                            && candle.getClosePrice() != null
                            && candle.getClosePrice().signum() > 0) {
                        double close = candle.getClosePrice().doubleValue();
                        double previousClose = previous.getClosePrice().doubleValue();
                        double returnValue = close / previousClose - 1.0d;
                        double dollarVolume = close * Math.max(0L, candle.getVolume() == null ? 0L : candle.getVolume());
                        DailyReturn dailyReturn = new DailyReturn(candle.getTradeDate(), returnValue, dollarVolume);
                        returns.add(dailyReturn);
                        benchmarkStats.computeIfAbsent(candle.getTradeDate(), ignored -> new DoubleSummaryStatistics())
                                .accept(returnValue);
                    }
                    previous = candle;
                }
                if (!returns.isEmpty()) {
                    returnsBySymbol.put(entry.getKey(), returns);
                }
            }

            Map<LocalDate, Double> benchmarkReturns = benchmarkStats.entrySet().stream()
                    .filter(entry -> entry.getValue().getCount() >= 50)
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAverage(),
                            (left, right) -> left, LinkedHashMap::new));
            Map<LocalDate, Double> storedBenchmark = benchmarkReturns(storedBenchmarkReturns);
            if (!storedBenchmark.isEmpty()) {
                benchmarkReturns = storedBenchmark;
            }
            return new RiskLookup(storedByKey, returnsBySymbol, benchmarkReturns);
        }

        private static Map<LocalDate, Double> benchmarkReturns(List<StockBenchmarkReturn> rows) {
            if (rows == null || rows.isEmpty()) {
                return Map.of();
            }
            return rows.stream()
                    .filter(row -> row.getTradeDate() != null && row.getReturnPct() != null)
                    .collect(Collectors.toMap(StockBenchmarkReturn::getTradeDate,
                            row -> row.getReturnPct().doubleValue() / 100.0d,
                            (left, right) -> right, LinkedHashMap::new));
        }

        private static Map<RiskKey, RiskStats> riskSnapshots(List<StockRiskSnapshot> snapshots) {
            if (snapshots == null || snapshots.isEmpty()) {
                return Map.of();
            }
            Map<RiskKey, RiskStats> mapped = new LinkedHashMap<>();
            for (StockRiskSnapshot snapshot : snapshots) {
                if (snapshot.getSymbol() == null || snapshot.getSignalDate() == null) {
                    continue;
                }
                mapped.put(new RiskKey(snapshot.getSymbol(), snapshot.getSignalDate()),
                        new RiskStats(
                                number(snapshot.getBeta()),
                                number(snapshot.getVolatilityPct()),
                                number(snapshot.getAvgDollarVolume()),
                                snapshot.getObservations() == null ? 0 : snapshot.getObservations()));
            }
            return mapped;
        }

        private static double number(BigDecimal value) {
            return value == null ? Double.NaN : value.doubleValue();
        }

        private RiskStats stats(String symbol, LocalDate signalDate) {
            if (symbol == null || signalDate == null) {
                return RiskStats.missing();
            }
            RiskKey key = new RiskKey(symbol, signalDate);
            RiskStats stored = storedByKey.get(key);
            if (stored != null) {
                return stored;
            }
            return cache.computeIfAbsent(key, ignored -> calculate(symbol, signalDate));
        }

        private RiskStats calculate(String symbol, LocalDate signalDate) {
            List<DailyReturn> history = returnsBySymbol.get(symbol);
            if (history == null || history.isEmpty()) {
                return RiskStats.missing();
            }
            List<DailyReturn> trailing = history.stream()
                    .filter(row -> !row.tradeDate().isAfter(signalDate))
                    .toList();
            if (trailing.size() < MIN_RISK_OBSERVATIONS) {
                return RiskStats.missing();
            }
            List<DailyReturn> returnWindow = tail(trailing, RISK_RETURN_WINDOW);
            double volatility = annualizedVolatility(returnWindow);
            double liquidity = tail(trailing, RISK_LIQUIDITY_WINDOW).stream()
                    .mapToDouble(DailyReturn::dollarVolume)
                    .filter(Double::isFinite)
                    .average()
                    .orElse(Double.NaN);
            double beta = beta(returnWindow);
            return new RiskStats(beta, volatility, liquidity, returnWindow.size());
        }

        private double beta(List<DailyReturn> returns) {
            List<Double> benchmark = new ArrayList<>();
            List<Double> asset = new ArrayList<>();
            for (DailyReturn row : returns) {
                Double benchmarkReturn = benchmarkReturns.get(row.tradeDate());
                if (benchmarkReturn != null && Double.isFinite(benchmarkReturn) && Double.isFinite(row.returnValue())) {
                    benchmark.add(benchmarkReturn);
                    asset.add(row.returnValue());
                }
            }
            if (asset.size() < MIN_RISK_OBSERVATIONS) {
                return Double.NaN;
            }
            double benchmarkMean = benchmark.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double assetMean = asset.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double covariance = 0.0d;
            double benchmarkVariance = 0.0d;
            for (int i = 0; i < asset.size(); i++) {
                double benchmarkDelta = benchmark.get(i) - benchmarkMean;
                covariance += benchmarkDelta * (asset.get(i) - assetMean);
                benchmarkVariance += benchmarkDelta * benchmarkDelta;
            }
            return benchmarkVariance <= 0 ? Double.NaN : covariance / benchmarkVariance;
        }

        private int symbolCount() {
            return returnsBySymbol.size();
        }

        private int snapshotCount() {
            return storedByKey.size();
        }
    }

    private static List<DailyReturn> tail(List<DailyReturn> rows, int limit) {
        if (rows.size() <= limit) {
            return rows;
        }
        return rows.subList(rows.size() - limit, rows.size());
    }

    private static double annualizedVolatility(List<DailyReturn> returns) {
        if (returns.size() < 2) {
            return Double.NaN;
        }
        double mean = returns.stream().mapToDouble(DailyReturn::returnValue).average().orElse(0);
        double variance = 0.0d;
        for (DailyReturn row : returns) {
            double delta = row.returnValue() - mean;
            variance += delta * delta;
        }
        return Math.sqrt(variance / (returns.size() - 1)) * Math.sqrt(252.0d) * 100.0d;
    }

    private record DailyReturn(LocalDate tradeDate, double returnValue, double dollarVolume) {
    }

    private record RiskKey(String symbol, LocalDate signalDate) {
    }

    public record MarketSnapshotRefreshResult(
            int insertedOrUpdatedMarketRows,
            int loadedMarketRows,
            int savedBenchmarkRows,
            int tossShareRows,
            int fallbackMarketCapRows,
            int membershipRows,
            int sharesRows) {
    }

    public record CovarianceRefreshResult(
            int processedDates,
            int candidateRows,
            int savedPairs,
            int lookbackDays) {
    }

    public record ExpectedReturnRefreshResult(
            int processedDates,
            int savedRows,
            int trainedRows,
            int minTrainingRows,
            int calibrationRows) {
    }

    public record PortfolioViewSnapshotRefreshResult(
            String indexCode,
            boolean saved,
            String message,
            long elapsedMillis) {
    }

    public record OptimizerShadowRefreshResult(
            String indexCode,
            int requestedRows,
            int savedRows,
            int usableRows,
            String message) {
    }

    private record PairCovariance(
            double correlation,
            double annualizedCovariance,
            double volatilityAPct,
            double volatilityBPct,
            int observations) {

        private static PairCovariance missing() {
            return new PairCovariance(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
        }

        private boolean usable() {
            return observations >= MIN_COVARIANCE_OBSERVATIONS
                    && Double.isFinite(correlation)
                    && Double.isFinite(annualizedCovariance)
                    && Double.isFinite(volatilityAPct)
                    && Double.isFinite(volatilityBPct);
        }
    }

    private record TrainingObservation(
            int score,
            String sector,
            String regimeLabel,
            int macroScore,
            double forwardReturnPct,
            double excessReturnPct,
            Map<String, Double> exposures) {
    }

    private record FactorSpec(
            String name,
            Function<StockBacktestResult, Integer> scoreAccessor) {
    }

    private record InteractionSpec(
            String name,
            String leftFactor,
            String rightFactor) {
    }

    private record FactorCoefficient(
            double coefficient,
            int sampleCount) {

        private static FactorCoefficient missing() {
            return new FactorCoefficient(0.0d, 0);
        }
    }

    private record FactorContribution(
            String factor,
            double exposure,
            double coefficient,
            double contributionPct,
            int sampleCount) {
    }

    private record FactorAdjustedPrediction(
            ExpectedReturnStats stats,
            List<FactorContribution> contributions) {
    }

    private static final class MacroRegimeLookup {
        private final NavigableMap<LocalDate, StockMacroRegimeSnapshot> byDate;

        private MacroRegimeLookup(NavigableMap<LocalDate, StockMacroRegimeSnapshot> byDate) {
            this.byDate = byDate;
        }

        private static MacroRegimeLookup empty() {
            return new MacroRegimeLookup(new TreeMap<>());
        }

        private static MacroRegimeLookup from(List<StockMacroRegimeSnapshot> snapshots) {
            if (snapshots == null || snapshots.isEmpty()) {
                return empty();
            }
            NavigableMap<LocalDate, StockMacroRegimeSnapshot> mapped = new TreeMap<>();
            for (StockMacroRegimeSnapshot snapshot : snapshots) {
                if (snapshot == null || snapshot.getSnapshotDate() == null) {
                    continue;
                }
                mapped.put(snapshot.getSnapshotDate(), snapshot);
            }
            return new MacroRegimeLookup(mapped);
        }

        private String regimeLabel(LocalDate date) {
            StockMacroRegimeSnapshot snapshot = snapshot(date);
            return snapshot == null || snapshot.getRegimeLabel() == null
                    ? "UNKNOWN"
                    : snapshot.getRegimeLabel();
        }

        private int macroScore(LocalDate date) {
            StockMacroRegimeSnapshot snapshot = snapshot(date);
            return snapshot == null || snapshot.getMacroScore() == null ? 50 : snapshot.getMacroScore();
        }

        private StockMacroRegimeSnapshot snapshot(LocalDate date) {
            if (date == null || byDate.isEmpty()) {
                return null;
            }
            Map.Entry<LocalDate, StockMacroRegimeSnapshot> entry = byDate.floorEntry(date);
            return entry == null ? null : entry.getValue();
        }
    }

    private record ObservedStats(
            double expectedReturnPct,
            double expectedExcessReturnPct,
            double upsideProbabilityPct,
            double downsideProbabilityPct,
            double drawdownRiskPct,
            double returnP10Pct,
            double returnP50Pct,
            double returnP90Pct,
            double excessP10Pct,
            double excessP50Pct,
            double excessP90Pct,
            int count) {

        private static ObservedStats missing() {
            return new ObservedStats(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
        }
    }

    private record ExpectedReturnStats(
            double expectedReturnPct,
            double expectedExcessReturnPct,
            double upsideProbabilityPct,
            double calibratedUpsideProbabilityPct,
            double downsideProbabilityPct,
            double drawdownRiskPct,
            double returnP10Pct,
            double returnP50Pct,
            double returnP90Pct,
            double excessP10Pct,
            double excessP50Pct,
            double excessP90Pct,
            int confidence,
            int sampleCount,
            int sectorSampleCount,
            int scoreBucket,
            int calibrationBucket,
            double calibrationErrorPct) {

        private static ExpectedReturnStats missing() {
            return new ExpectedReturnStats(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, 0, 0, 0, 0, -1, Double.NaN);
        }
    }

    private record PredictionEvaluation(int horizonDays, double predictedUpsidePct, boolean actualUpside) {
    }

    private record LivePortfolioRecommendation(
            List<StockPortfolioBacktestView.MetricCard> cards,
            List<StockPortfolioBacktestView.PositionRow> positions) {

        private static LivePortfolioRecommendation empty(String note) {
            return new LivePortfolioRecommendation(
                    List.of(new StockPortfolioBacktestView.MetricCard("Live portfolio", "대기", note, "caution")),
                    List.of());
        }
    }

    private record CalibrationKey(int horizonDays, int probabilityBucket) {
    }

    public record RiskFreeRateRefreshResult(int requestedRows, int savedRows, LocalDate minDate, LocalDate maxDate) {
    }

    private record CachedPortfolioBacktestView(StockPortfolioBacktestView view, Instant cachedAt) {
        private boolean isFresh() {
            if (view == null || cachedAt == null) {
                return false;
            }
            return Duration.between(cachedAt, Instant.now()).compareTo(PORTFOLIO_VIEW_CACHE_TTL) < 0;
        }
    }

    private static final class RiskFreeRateLookup {
        private final NavigableMap<LocalDate, StockRiskFreeRateSnapshot> byDate;
        private final double fallbackAnnualRatePct;

        private RiskFreeRateLookup(NavigableMap<LocalDate, StockRiskFreeRateSnapshot> byDate,
                double fallbackAnnualRatePct) {
            this.byDate = byDate;
            this.fallbackAnnualRatePct = fallbackAnnualRatePct;
        }

        private static RiskFreeRateLookup empty(double fallbackAnnualRatePct) {
            return new RiskFreeRateLookup(new TreeMap<>(), fallbackAnnualRatePct);
        }

        private static RiskFreeRateLookup from(List<StockRiskFreeRateSnapshot> rows, double fallbackAnnualRatePct) {
            if (rows == null || rows.isEmpty()) {
                return empty(fallbackAnnualRatePct);
            }
            NavigableMap<LocalDate, StockRiskFreeRateSnapshot> mapped = new TreeMap<>();
            for (StockRiskFreeRateSnapshot row : rows) {
                if (row.getRateDate() == null || row.getAnnualRatePct() == null) {
                    continue;
                }
                StockRiskFreeRateSnapshot current = mapped.get(row.getRateDate());
                if (current == null || DEFAULT_RISK_FREE_SERIES_CODE.equalsIgnoreCase(row.getSeriesCode())) {
                    mapped.put(row.getRateDate(), row);
                }
            }
            return new RiskFreeRateLookup(mapped, fallbackAnnualRatePct);
        }

        private double annualRate(LocalDate date) {
            if (date != null) {
                Map.Entry<LocalDate, StockRiskFreeRateSnapshot> entry = byDate.floorEntry(date);
                if (entry != null && entry.getValue().getAnnualRatePct() != null) {
                    return entry.getValue().getAnnualRatePct().doubleValue();
                }
            }
            return fallbackAnnualRatePct;
        }

        private double periodReturnPct(LocalDate date, int tradingDays) {
            double annualRate = annualRate(date);
            if (!Double.isFinite(annualRate)) {
                return 0.0d;
            }
            return (Math.pow(1.0d + annualRate / 100.0d, Math.max(0, tradingDays) / 252.0d) - 1.0d) * 100.0d;
        }

        private int snapshotCount() {
            return byDate.size();
        }

        private boolean fallbackBacked() {
            if (byDate.isEmpty()) {
                return true;
            }
            return byDate.values().stream()
                    .allMatch(row -> row.getSource() != null
                            && row.getSource().toUpperCase(Locale.ROOT).startsWith("CONFIG_"));
        }

        private String displayValue() {
            if (byDate.isEmpty()) {
                return percentUnsigned(fallbackAnnualRatePct) + " fallback";
            }
            StockRiskFreeRateSnapshot latest = byDate.lastEntry().getValue();
            if (latest.getSource() != null && latest.getSource().toUpperCase(Locale.ROOT).startsWith("CONFIG_")) {
                return percentUnsigned(latest.getAnnualRatePct() == null ? fallbackAnnualRatePct
                        : latest.getAnnualRatePct().doubleValue()) + " fallback";
            }
            return percentUnsigned(latest.getAnnualRatePct() == null ? fallbackAnnualRatePct
                    : latest.getAnnualRatePct().doubleValue()) + " " + latest.getSeriesCode();
        }
    }

    private record RiskStats(double beta, double volatilityPct, double avgDollarVolume, int observations) {
        private static RiskStats missing() {
            return new RiskStats(Double.NaN, Double.NaN, Double.NaN, 0);
        }

        private boolean hasUsableValue() {
            return observations >= MIN_RISK_OBSERVATIONS
                    && (Double.isFinite(beta) || Double.isFinite(volatilityPct) || Double.isFinite(avgDollarVolume));
        }
    }

    private record CovarianceKey(LocalDate snapshotDate, String symbolA, String symbolB) {
        private static CovarianceKey of(LocalDate snapshotDate, String left, String right) {
            if (left == null || right == null) {
                return new CovarianceKey(snapshotDate, left, right);
            }
            return left.compareTo(right) <= 0
                    ? new CovarianceKey(snapshotDate, left, right)
                    : new CovarianceKey(snapshotDate, right, left);
        }
    }

    private record PairRiskStats(double correlation, double covariance, int observations) {
    }

    private static final class CovarianceLookup {
        private final Map<CovarianceKey, PairRiskStats> byKey;
        private final NavigableMap<LocalDate, LocalDate> availableDates;
        private final int dateCount;

        private CovarianceLookup(Map<CovarianceKey, PairRiskStats> byKey, NavigableMap<LocalDate, LocalDate> availableDates,
                int dateCount) {
            this.byKey = byKey;
            this.availableDates = availableDates;
            this.dateCount = dateCount;
        }

        private static CovarianceLookup empty() {
            return new CovarianceLookup(Map.of(), new TreeMap<>(), 0);
        }

        private static CovarianceLookup from(List<StockCovarianceSnapshot> snapshots) {
            if (snapshots == null || snapshots.isEmpty()) {
                return empty();
            }
            Map<CovarianceKey, PairRiskStats> mapped = new LinkedHashMap<>();
            NavigableMap<LocalDate, LocalDate> dates = new TreeMap<>();
            for (StockCovarianceSnapshot snapshot : snapshots) {
                if (snapshot.getSnapshotDate() == null
                        || snapshot.getSymbolA() == null
                        || snapshot.getSymbolB() == null) {
                    continue;
                }
                double correlation = toDouble(snapshot.getCorrelation());
                double covariance = toDouble(snapshot.getCovariance());
                if (!Double.isFinite(correlation) && !Double.isFinite(covariance)) {
                    continue;
                }
                int observations = snapshot.getObservations() == null ? 0 : snapshot.getObservations();
                mapped.put(CovarianceKey.of(snapshot.getSnapshotDate(), snapshot.getSymbolA(), snapshot.getSymbolB()),
                        new PairRiskStats(correlation, covariance, observations));
                dates.put(snapshot.getSnapshotDate(), snapshot.getSnapshotDate());
            }
            return new CovarianceLookup(mapped, dates, dates.size());
        }

        private static double toDouble(BigDecimal value) {
            return value == null ? Double.NaN : value.doubleValue();
        }

        private double correlation(LocalDate snapshotDate, String symbolA, String symbolB) {
            if (snapshotDate == null || symbolA == null || symbolB == null) {
                return Double.NaN;
            }
            PairRiskStats stats = pairStats(snapshotDate, symbolA, symbolB);
            if (stats == null || stats.observations() < MIN_COVARIANCE_OBSERVATIONS) {
                return Double.NaN;
            }
            return stats.correlation();
        }

        private double covariance(LocalDate snapshotDate, String symbolA, String symbolB) {
            if (snapshotDate == null || symbolA == null || symbolB == null) {
                return Double.NaN;
            }
            PairRiskStats stats = pairStats(snapshotDate, symbolA, symbolB);
            if (stats == null || stats.observations() < MIN_COVARIANCE_OBSERVATIONS) {
                return Double.NaN;
            }
            return stats.covariance();
        }

        private PairRiskStats pairStats(LocalDate snapshotDate, String symbolA, String symbolB) {
            PairRiskStats exact = byKey.get(CovarianceKey.of(snapshotDate, symbolA, symbolB));
            if (exact != null) {
                return exact;
            }
            Map.Entry<LocalDate, LocalDate> floorDate = availableDates.floorEntry(snapshotDate);
            if (floorDate == null || floorDate.getKey().equals(snapshotDate)) {
                return null;
            }
            return byKey.get(CovarianceKey.of(floorDate.getValue(), symbolA, symbolB));
        }

        private boolean isEmpty() {
            return byKey.isEmpty();
        }

        private int snapshotCount() {
            return byKey.size();
        }

        private int dateCount() {
            return dateCount;
        }
    }

    private record ExpectedReturnKey(LocalDate signalDate, String symbol, int horizonDays) {
    }

    private record ExpectedResultKey(LocalDate signalDate, String symbol, int horizonDays) {
    }

    private record ExpectedBenchmarkKey(LocalDate signalDate, int horizonDays) {
    }

    private static final class ExpectedReturnLookup {
        private final Map<ExpectedReturnKey, ExpectedReturnStats> byKey;
        private final int dateCount;

        private ExpectedReturnLookup(Map<ExpectedReturnKey, ExpectedReturnStats> byKey, int dateCount) {
            this.byKey = byKey;
            this.dateCount = dateCount;
        }

        private static ExpectedReturnLookup empty() {
            return new ExpectedReturnLookup(Map.of(), 0);
        }

        private static ExpectedReturnLookup from(List<StockExpectedReturnSnapshot> snapshots) {
            if (snapshots == null || snapshots.isEmpty()) {
                return empty();
            }
            Map<ExpectedReturnKey, ExpectedReturnStats> mapped = new LinkedHashMap<>();
            Set<LocalDate> dates = new HashSet<>();
            for (StockExpectedReturnSnapshot snapshot : snapshots) {
                if (snapshot.getSignalDate() == null || snapshot.getSymbol() == null
                        || snapshot.getHorizonDays() == null) {
                    continue;
                }
                ExpectedReturnStats stats = new ExpectedReturnStats(
                        toDouble(snapshot.getExpectedReturnPct()),
                        toDouble(snapshot.getExpectedExcessReturnPct()),
                        toDouble(snapshot.getUpsideProbabilityPct()),
                        toDouble(snapshot.getCalibratedUpsideProbabilityPct()),
                        toDouble(snapshot.getDownsideProbabilityPct()),
                        toDouble(snapshot.getDrawdownRiskPct()),
                        toDouble(snapshot.getReturnP10Pct()),
                        toDouble(snapshot.getReturnP50Pct()),
                        toDouble(snapshot.getReturnP90Pct()),
                        toDouble(snapshot.getExcessP10Pct()),
                        toDouble(snapshot.getExcessP50Pct()),
                        toDouble(snapshot.getExcessP90Pct()),
                        snapshot.getConfidence() == null ? 0 : snapshot.getConfidence(),
                        snapshot.getSampleCount() == null ? 0 : snapshot.getSampleCount(),
                        snapshot.getSectorSampleCount() == null ? 0 : snapshot.getSectorSampleCount(),
                        snapshot.getScoreBucket() == null ? 0 : snapshot.getScoreBucket(),
                        snapshot.getCalibrationBucket() == null ? -1 : snapshot.getCalibrationBucket(),
                        toDouble(snapshot.getCalibrationErrorPct()));
                mapped.put(new ExpectedReturnKey(snapshot.getSignalDate(), snapshot.getSymbol(),
                        snapshot.getHorizonDays()), stats);
                dates.add(snapshot.getSignalDate());
            }
            return new ExpectedReturnLookup(mapped, dates.size());
        }

        private static double toDouble(BigDecimal value) {
            return value == null ? Double.NaN : value.doubleValue();
        }

        private ExpectedReturnStats stats(String symbol, LocalDate signalDate, int horizonDays) {
            if (symbol == null || signalDate == null) {
                return ExpectedReturnStats.missing();
            }
            return byKey.getOrDefault(new ExpectedReturnKey(signalDate, symbol, horizonDays),
                    ExpectedReturnStats.missing());
        }

        private boolean isEmpty() {
            return byKey.isEmpty();
        }

        private int snapshotCount() {
            return byKey.size();
        }

        private int dateCount() {
            return dateCount;
        }
    }

    private static final class ExpectedCalibrationLookup {
        private final List<StockExpectedReturnCalibration> rows;

        private ExpectedCalibrationLookup(List<StockExpectedReturnCalibration> rows) {
            this.rows = rows;
        }

        private static ExpectedCalibrationLookup empty() {
            return new ExpectedCalibrationLookup(List.of());
        }

        private static ExpectedCalibrationLookup from(List<StockExpectedReturnCalibration> rows) {
            if (rows == null || rows.isEmpty()) {
                return empty();
            }
            return new ExpectedCalibrationLookup(rows.stream()
                    .filter(row -> row.getSampleCount() != null
                            && row.getSampleCount() >= MIN_EXPECTED_RETURN_CALIBRATION_BUCKET_ROWS)
                    .toList());
        }

        private int rowCount() {
            return rows.size();
        }

        private double averageAbsoluteErrorPct() {
            return rows.stream()
                    .mapToDouble(row -> Math.abs(toDouble(row.getCalibrationErrorPct())))
                    .filter(Double::isFinite)
                    .average()
                    .orElse(Double.NaN);
        }

        private double averageBrierScore() {
            return rows.stream()
                    .mapToDouble(row -> toDouble(row.getBrierScore()))
                    .filter(Double::isFinite)
                    .average()
                    .orElse(Double.NaN);
        }

        private double errorFor(int horizonDays, int probabilityBucket) {
            if (rows.isEmpty() || probabilityBucket < 0) {
                return Double.NaN;
            }
            return rows.stream()
                    .filter(row -> row.getHorizonDays() != null && row.getHorizonDays() == horizonDays)
                    .filter(row -> row.getProbabilityBucket() != null
                            && row.getProbabilityBucket() == probabilityBucket)
                    .mapToDouble(row -> toDouble(row.getCalibrationErrorPct()))
                    .filter(Double::isFinite)
                    .findFirst()
                    .orElse(Double.NaN);
        }

        private static double toDouble(BigDecimal value) {
            return value == null ? Double.NaN : value.doubleValue();
        }
    }

    private static final class MetricAccumulator {
        private int count;
        private double sum;

        private void add(double value) {
            if (Double.isFinite(value)) {
                count++;
                sum += value;
            }
        }

        private double average() {
            return count == 0 ? Double.NaN : sum / count;
        }
    }

    private static final class SectorStats {
        private int count;
        private double sum;
        private double max;

        private void add(double value) {
            count++;
            sum += value;
            max = Math.max(max, value);
        }

        private double average() {
            return count == 0 ? 0 : sum / count;
        }

        private double max() {
            return max;
        }
    }

    private static final class BenchmarkAccumulator {
        private int constituentCount;
        private int coverageCount;
        private double totalMarketCapUsd;
        private double totalWeight;
        private double weightedReturn;

        private void addConstituent(StockMarketSnapshot snapshot) {
            constituentCount++;
            double marketCap = number(snapshot.getMarketCapUsd());
            if (Double.isFinite(marketCap) && marketCap > 0) {
                totalMarketCapUsd += marketCap;
            }
        }

        private void addReturn(double weight, double returnValue) {
            if (!Double.isFinite(weight) || weight <= 0 || !Double.isFinite(returnValue)) {
                return;
            }
            coverageCount++;
            totalWeight += weight;
            weightedReturn += weight * returnValue;
        }

        private int constituentCount() {
            return constituentCount;
        }

        private int coverageCount() {
            return coverageCount;
        }

        private double totalMarketCapUsd() {
            return totalMarketCapUsd;
        }

        private double totalWeight() {
            return totalWeight;
        }

        private double weightedReturn() {
            return weightedReturn;
        }
    }
}
