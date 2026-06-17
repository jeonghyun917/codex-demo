package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

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
    private static final double RISK_SECTOR_CAP = 0.30d;
    private static final double RISK_POSITION_CAP_DEFAULT = 0.08d;
    private static final double RISK_POSITION_CAP_TOP10 = 0.12d;

    private final ObjectProvider<StockBacktestMapper> stockBacktestMapper;
    private final StockBacktestService stockBacktestService;

    public StockPortfolioBacktestService(ObjectProvider<StockBacktestMapper> stockBacktestMapper,
            StockBacktestService stockBacktestService) {
        this.stockBacktestMapper = stockBacktestMapper;
        this.stockBacktestService = stockBacktestService;
    }

    public StockPortfolioBacktestView build(String indexCode) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return empty(indexCode);
        }

        stockBacktestService.refreshCompletedResults(5_000);

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

        List<StrategyRun> runs = strategyConfigs().stream()
                .map(config -> runStrategy(config, results, riskLookup))
                .filter(run -> !run.periods().isEmpty())
                .toList();
        if (runs.isEmpty()) {
            return empty(indexCode);
        }

        List<StrategySummary> summaries = runs.stream()
                .map(StockPortfolioBacktestService::summarize)
                .sorted(Comparator
                        .comparingDouble(StrategySummary::sharpeSort).reversed()
                        .thenComparing(StrategySummary::excessReturnSort, Comparator.reverseOrder()))
                .toList();
        StrategyRun selectedRun = selectedRun(runs, summaries);

        return new StockPortfolioBacktestView(
                indexCode,
                "S&P 500 market-cap weighted proxy",
                "Dynamic transaction cost: base " + percentUnsigned(BASE_TRADE_COST_PCT)
                        + " plus liquidity, volatility, and beta impact",
                cards(summaries, results),
                strategyRows(summaries),
                riskModelRows(summaries, riskLookup),
                riskImpactRows(summaries),
                recentPeriods(selectedRun),
                sectorExposures(selectedRun),
                latestPositions(selectedRun),
                notes(results));
    }

    private static List<StrategyConfig> strategyConfigs() {
        List<StrategyConfig> configs = new ArrayList<>();
        for (int horizon : HORIZONS) {
            for (int topCount : TOP_COUNTS) {
                for (Weighting weighting : Weighting.values()) {
                    for (RiskMode riskMode : RiskMode.values()) {
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
        return RiskLookup.from(candles);
    }

    private static StrategyRun runStrategy(StrategyConfig config, List<StockBacktestResult> allResults,
            RiskLookup riskLookup) {
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

            List<StockBacktestResult> selected = selectPositions(config, entry.getKey(), universe, riskLookup);
            Map<String, Double> weights = weights(selected, config.weighting());
            if (config.riskMode().controlled()) {
                weights = applyRiskWeightCaps(selected, weights, config);
            }
            if (weights.isEmpty()) {
                continue;
            }

            double turnover = oneWayTurnover(previousWeights, weights);
            double grossReturn = weightedReturn(selected, weights);
            double benchmarkReturn = benchmarkReturn(universe);
            Map<String, Double> sectorWeights = sectorWeights(selected, weights);
            Map<String, RiskStats> riskStatsBySymbol = riskStatsBySymbol(selected, riskLookup, entry.getKey());
            double transactionCost = transactionCostPct(previousWeights, weights, riskStatsBySymbol);
            double netReturn = grossReturn - transactionCost;
            double portfolioBeta = weightedRiskMetric(selected, weights, riskLookup, entry.getKey(), RiskField.BETA);
            double portfolioVolatility = weightedRiskMetric(selected, weights, riskLookup, entry.getKey(),
                    RiskField.VOLATILITY);
            double portfolioLiquidity = weightedRiskMetric(selected, weights, riskLookup, entry.getKey(),
                    RiskField.LIQUIDITY);

            periods.add(new PortfolioPeriod(entry.getKey(), selected, weights, riskStatsBySymbol, sectorWeights,
                    grossReturn, netReturn, benchmarkReturn, turnover, transactionCost,
                    portfolioBeta, portfolioVolatility, portfolioLiquidity));
            previousWeights = weights;
        }

        return new StrategyRun(config, periods);
    }

    private static List<StockBacktestResult> selectPositions(StrategyConfig config, LocalDate signalDate,
            List<StockBacktestResult> universe, RiskLookup riskLookup) {
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
                        .comparing((StockBacktestResult result) -> riskAdjustedScore(result, signalDate, riskLookup),
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

    private static double riskAdjustedScore(StockBacktestResult result, LocalDate signalDate, RiskLookup riskLookup) {
        double adjusted = score(result);
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

    private static Map<String, RiskStats> riskStatsBySymbol(List<StockBacktestResult> selected,
            RiskLookup riskLookup, LocalDate signalDate) {
        return selected.stream()
                .collect(Collectors.toMap(StockBacktestResult::getSymbol,
                        result -> riskLookup.stats(result.getSymbol(), signalDate),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static StrategySummary summarize(StrategyRun run) {
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
        double maxSectorSum = 0;
        double maxPositionSum = 0;
        MetricAccumulator betaStats = new MetricAccumulator();
        MetricAccumulator volatilityStats = new MetricAccumulator();
        MetricAccumulator liquidityStats = new MetricAccumulator();

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
            maxSectorSum += period.sectorWeights().values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            maxPositionSum += period.weights().values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            betaStats.add(period.portfolioBeta());
            volatilityStats.add(period.portfolioVolatilityPct());
            liquidityStats.add(period.portfolioLiquidity());
        }

        double cumulativeReturn = (equity - 1.0d) * 100.0d;
        double benchmarkReturn = (benchmarkEquity - 1.0d) * 100.0d;
        double excessReturn = cumulativeReturn - benchmarkReturn;
        double years = periods.size() * run.config().horizonDays() / 252.0d;
        double annualReturn = years > 0 ? (Math.pow(equity, 1.0d / years) - 1.0d) * 100.0d : 0;
        double volatility = annualizedVolatility(periods, run.config().horizonDays());
        Double sharpe = volatility <= 0 ? null : annualReturn / volatility;

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
                maxSectorSum / periods.size(),
                maxPositionSum / periods.size(),
                betaStats.average(),
                volatilityStats.average(),
                liquidityStats.average(),
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
        StrategyConfig preferred = new StrategyConfig(20, 20, Weighting.SIGNAL, RiskMode.RISK_CONTROLLED);
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
            RiskLookup riskLookup) {
        StrategySummary bestRisk = summaries.stream()
                .filter(summary -> summary.config().riskMode().controlled())
                .findFirst()
                .orElse(summaries.get(0));
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
                new StockPortfolioBacktestView.RiskModelRow("Avg beta",
                        metricDecimal(bestRisk.averageBeta()),
                        "Trailing 126D beta versus an equal-weight universe return proxy.",
                        betaTone(bestRisk.averageBeta())),
                new StockPortfolioBacktestView.RiskModelRow("Avg trail vol",
                        metricPercent(bestRisk.averagePortfolioVolatility()),
                        "Annualized trailing 126D volatility from stock_candle_daily.",
                        volatilityTone(bestRisk.averagePortfolioVolatility())),
                new StockPortfolioBacktestView.RiskModelRow("Avg liquidity",
                        dollarVolume(bestRisk.averageLiquidity()),
                        "Trailing 63D average dollar volume. Candle symbols loaded: "
                                + integer(riskLookup.symbolCount()),
                        "neutral"));
    }

    private static List<StockPortfolioBacktestView.MetricCard> cards(List<StrategySummary> summaries,
            List<StockBacktestResult> results) {
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
                        best.sharpe() == null ? "-" : decimal(best.sharpe()), "annualized, rf=0",
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
            rows.add(new StockPortfolioBacktestView.PositionRow(
                    integer(rank++),
                    result.getSymbol(),
                    fallback(result.getName()),
                    fallback(result.getSector()),
                    integer(score(result)),
                    percentUnsigned(latest.weights().getOrDefault(result.getSymbol(), 0.0d) * 100.0d),
                    percent(returnPct(result)),
                    metricDecimal(latest.riskStats(result.getSymbol()).beta()),
                    metricPercent(latest.riskStats(result.getSymbol()).volatilityPct()),
                    dollarVolume(latest.riskStats(result.getSymbol()).avgDollarVolume()),
                    returnTone(returnPct(result))));
        }
        return rows;
    }

    private static List<String> notes(List<StockBacktestResult> results) {
        LocalDate minDate = results.stream().map(StockBacktestResult::getSignalDate).min(LocalDate::compareTo).orElse(null);
        LocalDate maxDate = results.stream().map(StockBacktestResult::getSignalDate).max(LocalDate::compareTo).orElse(null);
        return List.of(
                "v1 composes stored stock_signal_backtest_result rows into portfolio-level performance.",
                "Risk Model v1 adds a risk-controlled candidate with sector caps, position caps, trailing volatility, dollar-volume liquidity, and beta.",
                "Portfolio Backtest v2 uses a dynamic transaction-cost model and compares Base vs Risk-controlled Sharpe, MDD, beta, sector concentration, cost, and excess return.",
                "Rebalance dates are historical signal snapshot dates. Weekly validation can reuse this engine after weekly snapshot seeding.",
                "Benchmark is a same-date S&P 500 universe market-cap weighted proxy. Point-in-time market cap snapshots remain a future improvement.",
                "Beta uses an equal-weight universe return proxy because a point-in-time S&P 500 benchmark series is not stored yet.",
                "Validation window: " + date(minDate) + " ~ " + date(maxDate));
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
                List.of("Portfolio Backtest requires completed stock_signal_backtest_result rows."));
    }

    private static int score(StockBacktestResult result) {
        return result.getIntegratedScore() == null ? 50 : result.getIntegratedScore();
    }

    private static double returnPct(StockBacktestResult result) {
        return result.getForwardReturnPct() == null ? 0 : result.getForwardReturnPct().doubleValue();
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
        BASE("Base", false),
        RISK_CONTROLLED("Risk controlled", true);

        private final String label;
        private final boolean controlled;

        RiskMode(String label, boolean controlled) {
            this.label = label;
            this.controlled = controlled;
        }

        private String label() {
            return label;
        }

        private boolean controlled() {
            return controlled;
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
            Map<String, Double> sectorWeights,
            double grossReturnPct,
            double netReturnPct,
            double benchmarkReturnPct,
            double turnover,
            double transactionCostPct,
            double portfolioBeta,
            double portfolioVolatilityPct,
            double portfolioLiquidity) {

        private RiskStats riskStats(String symbol) {
            return riskStatsBySymbol.getOrDefault(symbol, RiskStats.missing());
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
            double averageMaxSectorWeight,
            double averageMaxPositionWeight,
            double averageBeta,
            double averagePortfolioVolatility,
            double averageLiquidity,
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

    private record PeriodWithEquity(PortfolioPeriod period, double equity, double benchmarkEquity) {
    }

    private static final class RiskLookup {
        private final Map<String, List<DailyReturn>> returnsBySymbol;
        private final Map<LocalDate, Double> benchmarkReturns;
        private final Map<RiskKey, RiskStats> cache = new HashMap<>();

        private RiskLookup(Map<String, List<DailyReturn>> returnsBySymbol, Map<LocalDate, Double> benchmarkReturns) {
            this.returnsBySymbol = returnsBySymbol;
            this.benchmarkReturns = benchmarkReturns;
        }

        private static RiskLookup empty() {
            return new RiskLookup(Map.of(), Map.of());
        }

        private static RiskLookup from(List<StockCandleDaily> candles) {
            if (candles == null || candles.isEmpty()) {
                return empty();
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
            return new RiskLookup(returnsBySymbol, benchmarkReturns);
        }

        private RiskStats stats(String symbol, LocalDate signalDate) {
            if (symbol == null || signalDate == null) {
                return RiskStats.missing();
            }
            RiskKey key = new RiskKey(symbol, signalDate);
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

    private record RiskStats(double beta, double volatilityPct, double avgDollarVolume, int observations) {
        private static RiskStats missing() {
            return new RiskStats(Double.NaN, Double.NaN, Double.NaN, 0);
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
}
