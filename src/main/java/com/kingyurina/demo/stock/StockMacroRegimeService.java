package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockMacroRegimeService {

    private static final List<String> PROXY_SYMBOLS = List.of("^VIX", "VIX", "DX-Y.NYB", "UUP", "TLT", "IEF", "SHY");
    private static final int DEFAULT_LOOKBACK_DAYS = 126;

    private final ObjectProvider<StockBacktestMapper> backtestMapper;

    public StockMacroRegimeService(ObjectProvider<StockBacktestMapper> backtestMapper) {
        this.backtestMapper = backtestMapper;
    }

    public int refreshMacroRegimeSnapshots(String indexCode, int dateLimit, int lookbackDays) {
        StockBacktestMapper mapper = requireMapper();
        int resolvedLookbackDays = Math.max(80, lookbackDays);
        List<LocalDate> dates = mapper.findMacroRegimeSnapshotDates(indexCode, Math.max(1, dateLimit));
        if (dates.isEmpty()) {
            return 0;
        }

        LocalDate minDate = dates.stream().min(Comparator.naturalOrder()).orElseThrow()
                .minusDays(resolvedLookbackDays * 3L);
        LocalDate maxDate = dates.stream().max(Comparator.naturalOrder()).orElseThrow();
        List<StockBenchmarkReturn> benchmarkReturns = mapper.findBenchmarkReturns(indexCode, minDate, maxDate);
        List<StockMarketSnapshot> marketSnapshots = mapper.findMarketSnapshots(indexCode, minDate, maxDate);
        List<StockCandleDaily> proxyCandles = mapper.findCandlesForSymbols(PROXY_SYMBOLS, minDate, maxDate);

        Map<LocalDate, StockBenchmarkReturn> benchmarkByDate = benchmarkReturns.stream()
                .filter(row -> row.getTradeDate() != null)
                .collect(Collectors.toMap(StockBenchmarkReturn::getTradeDate, row -> row, (left, right) -> right,
                        LinkedHashMap::new));
        List<StockBenchmarkReturn> sortedBenchmarkRows = benchmarkReturns.stream()
                .filter(row -> row.getTradeDate() != null && row.getReturnPct() != null)
                .sorted(Comparator.comparing(StockBenchmarkReturn::getTradeDate))
                .toList();
        Map<LocalDate, Double> breadthByDate = breadthByDate(marketSnapshots);
        Map<String, List<StockCandleDaily>> proxyBySymbol = proxyCandles.stream()
                .filter(row -> row.getSymbol() != null && row.getTradeDate() != null && row.getClosePrice() != null)
                .collect(Collectors.groupingBy(StockCandleDaily::getSymbol, LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), rows -> rows.stream()
                                .sorted(Comparator.comparing(StockCandleDaily::getTradeDate))
                                .toList())));

        int saved = 0;
        for (LocalDate date : dates) {
            List<StockBenchmarkReturn> history = sortedBenchmarkRows.stream()
                    .filter(row -> !row.getTradeDate().isAfter(date))
                    .toList();
            if (history.size() < 20) {
                continue;
            }
            StockBenchmarkReturn benchmark = benchmarkByDate.get(date);
            StockMacroRegimeSnapshot snapshot = buildSnapshot(indexCode, date, benchmark, history, breadthByDate,
                    proxyBySymbol);
            mapper.upsertMacroRegimeSnapshot(snapshot);
            saved++;
        }
        return saved;
    }

    public StockMacroRegimeView latestView(String indexCode) {
        StockBacktestMapper mapper = backtestMapper.getIfAvailable();
        if (mapper == null) {
            return emptyView();
        }
        StockMacroRegimeSnapshot snapshot = mapper.findLatestMacroRegimeSnapshot(indexCode);
        if (snapshot == null) {
            return emptyView();
        }
        return toView(snapshot);
    }

    private StockMacroRegimeSnapshot buildSnapshot(String indexCode, LocalDate date, StockBenchmarkReturn benchmark,
            List<StockBenchmarkReturn> history, Map<LocalDate, Double> breadthByDate,
            Map<String, List<StockCandleDaily>> proxyBySymbol) {
        double return20d = compoundReturn(history, 20);
        double return60d = compoundReturn(history, 60);
        double vol20d = annualizedVolatility(history, 20);
        double vol60d = annualizedVolatility(history, 60);
        Double breadth = breadthByDate.get(date);
        BigDecimal vixLevel = latestClose(proxyBySymbol, List.of("^VIX", "VIX"), date).map(StockCandleDaily::getClosePrice)
                .orElse(null);
        Double vixChange20d = proxyReturn(proxyBySymbol, List.of("^VIX", "VIX"), date, 20);
        Double dollarReturn20d = proxyReturn(proxyBySymbol, List.of("DX-Y.NYB", "UUP"), date, 20);
        Double rateReturn20d = proxyReturn(proxyBySymbol, List.of("TLT", "IEF", "SHY"), date, 20);

        int trendScore = trendScore(return20d, return60d);
        int volatilityScore = volatilityScore(vol20d, vol60d, number(vixLevel), vixChange20d);
        int breadthScore = breadth == null ? 50 : clamp((int) Math.round(breadth));
        int liquidityScore = liquidityScore(benchmark);
        int dollarScore = dollarScore(dollarReturn20d);
        int rateScore = rateScore(rateReturn20d);
        int macroScore = clamp((int) Math.round(
                trendScore * 0.30d
                        + volatilityScore * 0.25d
                        + breadthScore * 0.20d
                        + liquidityScore * 0.10d
                        + dollarScore * 0.075d
                        + rateScore * 0.075d));

        StockMacroRegimeSnapshot snapshot = new StockMacroRegimeSnapshot();
        snapshot.setIndexCode(indexCode);
        snapshot.setSnapshotDate(date);
        snapshot.setRegimeLabel(regimeLabel(macroScore));
        snapshot.setMacroScore(macroScore);
        snapshot.setTrendScore(trendScore);
        snapshot.setVolatilityScore(volatilityScore);
        snapshot.setBreadthScore(breadthScore);
        snapshot.setLiquidityScore(liquidityScore);
        snapshot.setDollarScore(dollarScore);
        snapshot.setRateScore(rateScore);
        snapshot.setBenchmarkReturn20dPct(decimal(return20d));
        snapshot.setBenchmarkReturn60dPct(decimal(return60d));
        snapshot.setRealizedVolatility20dPct(decimal(vol20d));
        snapshot.setRealizedVolatility60dPct(decimal(vol60d));
        snapshot.setBreadthAdvancerPct(decimalOrNull(breadth));
        snapshot.setBenchmarkCoverageCount(benchmark == null ? null : benchmark.getCoverageCount());
        snapshot.setVixLevel(vixLevel);
        snapshot.setVixChange20dPct(decimalOrNull(vixChange20d));
        snapshot.setDollarProxyReturn20dPct(decimalOrNull(dollarReturn20d));
        snapshot.setRateProxyReturn20dPct(decimalOrNull(rateReturn20d));
        snapshot.setSource(source(vixLevel, dollarReturn20d, rateReturn20d));
        return snapshot;
    }

    private static Map<LocalDate, Double> breadthByDate(List<StockMarketSnapshot> rows) {
        Map<String, List<StockMarketSnapshot>> bySymbol = rows.stream()
                .filter(row -> row.getSymbol() != null && row.getSnapshotDate() != null && row.getClosePrice() != null)
                .collect(Collectors.groupingBy(StockMarketSnapshot::getSymbol));
        Map<LocalDate, BreadthAccumulator> byDate = new HashMap<>();
        for (List<StockMarketSnapshot> symbolRows : bySymbol.values()) {
            List<StockMarketSnapshot> sorted = symbolRows.stream()
                    .sorted(Comparator.comparing(StockMarketSnapshot::getSnapshotDate))
                    .toList();
            StockMarketSnapshot previous = null;
            for (StockMarketSnapshot current : sorted) {
                if (previous != null && positive(previous.getClosePrice()) && positive(current.getClosePrice())) {
                    byDate.computeIfAbsent(current.getSnapshotDate(), ignored -> new BreadthAccumulator())
                            .add(current.getClosePrice().compareTo(previous.getClosePrice()) >= 0);
                }
                previous = current;
            }
        }
        Map<LocalDate, Double> result = new HashMap<>();
        for (Map.Entry<LocalDate, BreadthAccumulator> entry : byDate.entrySet()) {
            if (entry.getValue().total >= 30) {
                result.put(entry.getKey(), entry.getValue().advancerPct());
            }
        }
        return result;
    }

    private static double compoundReturn(List<StockBenchmarkReturn> rows, int window) {
        List<StockBenchmarkReturn> slice = tail(rows, window);
        if (slice.isEmpty()) {
            return Double.NaN;
        }
        double level = 1.0d;
        for (StockBenchmarkReturn row : slice) {
            level *= 1.0d + number(row.getReturnPct()) / 100.0d;
        }
        return (level - 1.0d) * 100.0d;
    }

    private static double annualizedVolatility(List<StockBenchmarkReturn> rows, int window) {
        List<Double> returns = tail(rows, window).stream()
                .map(StockBenchmarkReturn::getReturnPct)
                .filter(Objects::nonNull)
                .map(BigDecimal::doubleValue)
                .filter(Double::isFinite)
                .toList();
        if (returns.size() < 10) {
            return Double.NaN;
        }
        double average = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value - average, 2.0d))
                .sum() / Math.max(1, returns.size() - 1);
        return Math.sqrt(variance) * Math.sqrt(252.0d);
    }

    private static <T> List<T> tail(List<T> rows, int window) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, rows.size() - window);
        return rows.subList(fromIndex, rows.size());
    }

    private static Optional<StockCandleDaily> latestClose(Map<String, List<StockCandleDaily>> proxyBySymbol,
            List<String> symbols, LocalDate date) {
        for (String symbol : symbols) {
            List<StockCandleDaily> rows = proxyBySymbol.get(symbol);
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            for (int index = rows.size() - 1; index >= 0; index--) {
                StockCandleDaily row = rows.get(index);
                if (!row.getTradeDate().isAfter(date) && positive(row.getClosePrice())) {
                    return Optional.of(row);
                }
            }
        }
        return Optional.empty();
    }

    private static Double proxyReturn(Map<String, List<StockCandleDaily>> proxyBySymbol, List<String> symbols,
            LocalDate date, int lookback) {
        for (String symbol : symbols) {
            List<StockCandleDaily> rows = proxyBySymbol.get(symbol);
            if (rows == null || rows.size() <= lookback) {
                continue;
            }
            List<StockCandleDaily> eligible = rows.stream()
                    .filter(row -> !row.getTradeDate().isAfter(date) && positive(row.getClosePrice()))
                    .toList();
            if (eligible.size() <= lookback) {
                continue;
            }
            BigDecimal current = eligible.get(eligible.size() - 1).getClosePrice();
            BigDecimal previous = eligible.get(eligible.size() - 1 - lookback).getClosePrice();
            if (positive(previous)) {
                return current.divide(previous, 10, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }
        }
        return null;
    }

    private static int trendScore(double return20d, double return60d) {
        double score = 50.0d;
        if (Double.isFinite(return20d)) {
            score += return20d * 1.2d;
        }
        if (Double.isFinite(return60d)) {
            score += return60d * 0.9d;
        }
        return clamp((int) Math.round(score));
    }

    private static int volatilityScore(double vol20d, double vol60d, double vixLevel, Double vixChange20d) {
        double score = 65.0d;
        if (Double.isFinite(vol20d)) {
            score -= Math.max(0.0d, vol20d - 12.0d) * 1.1d;
        }
        if (Double.isFinite(vol60d)) {
            score -= Math.max(0.0d, vol60d - 14.0d) * 0.8d;
        }
        if (Double.isFinite(vixLevel)) {
            if (vixLevel >= 30.0d) {
                score -= 18.0d;
            } else if (vixLevel >= 24.0d) {
                score -= 10.0d;
            } else if (vixLevel <= 16.0d) {
                score += 6.0d;
            }
        }
        if (vixChange20d != null && Double.isFinite(vixChange20d)) {
            score -= Math.max(-3.0d, Math.min(8.0d, vixChange20d / 5.0d));
        }
        return clamp((int) Math.round(score));
    }

    private static int liquidityScore(StockBenchmarkReturn benchmark) {
        if (benchmark == null || benchmark.getCoverageCount() == null || benchmark.getConstituentCount() == null
                || benchmark.getConstituentCount() <= 0) {
            return 50;
        }
        double coveragePct = benchmark.getCoverageCount() * 100.0d / benchmark.getConstituentCount();
        return clamp((int) Math.round(coveragePct));
    }

    private static int dollarScore(Double dollarReturn20d) {
        if (dollarReturn20d == null || !Double.isFinite(dollarReturn20d)) {
            return 50;
        }
        return clamp((int) Math.round(50.0d - dollarReturn20d * 3.0d));
    }

    private static int rateScore(Double rateReturn20d) {
        if (rateReturn20d == null || !Double.isFinite(rateReturn20d)) {
            return 50;
        }
        return clamp((int) Math.round(50.0d + rateReturn20d * 3.0d));
    }

    private static String regimeLabel(int score) {
        if (score >= 67) {
            return "RISK_ON";
        }
        if (score < 45) {
            return "RISK_OFF";
        }
        return "NEUTRAL";
    }

    private static String source(BigDecimal vixLevel, Double dollarReturn20d, Double rateReturn20d) {
        List<String> sources = new ArrayList<>();
        sources.add("BENCHMARK_RETURN_SERIES");
        sources.add("MARKET_SNAPSHOT_BREADTH");
        if (vixLevel != null) {
            sources.add("VIX_PROXY");
        }
        if (dollarReturn20d != null) {
            sources.add("DOLLAR_PROXY");
        }
        if (rateReturn20d != null) {
            sources.add("RATE_PROXY");
        }
        return String.join("+", sources);
    }

    private static StockMacroRegimeView toView(StockMacroRegimeSnapshot snapshot) {
        String tone = switch (snapshot.getRegimeLabel()) {
            case "RISK_ON" -> "positive";
            case "RISK_OFF" -> "negative";
            default -> "neutral";
        };
        String label = switch (snapshot.getRegimeLabel()) {
            case "RISK_ON" -> "Risk-on";
            case "RISK_OFF" -> "Risk-off";
            default -> "Neutral";
        };
        String summary = label + " regime based on benchmark trend, realized volatility, breadth, and available macro proxies.";
        return new StockMacroRegimeView(
                label,
                tone,
                formatScore(snapshot.getMacroScore()),
                snapshot.getSnapshotDate() == null ? "-" : snapshot.getSnapshotDate().toString(),
                summary,
                List.of(
                        new StockMacroRegimeView.Metric("20D benchmark", formatPercent(snapshot.getBenchmarkReturn20dPct()),
                                "market-cap weighted proxy", tone(snapshot.getBenchmarkReturn20dPct())),
                        new StockMacroRegimeView.Metric("60D benchmark", formatPercent(snapshot.getBenchmarkReturn60dPct()),
                                "trend input", tone(snapshot.getBenchmarkReturn60dPct())),
                        new StockMacroRegimeView.Metric("60D volatility",
                                formatPercent(snapshot.getRealizedVolatility60dPct()), "annualized", inverseTone(
                                        snapshot.getRealizedVolatility60dPct())),
                        new StockMacroRegimeView.Metric("Breadth", formatPercent(snapshot.getBreadthAdvancerPct()),
                                "advancers in universe", breadthTone(snapshot.getBreadthAdvancerPct())),
                        new StockMacroRegimeView.Metric("VIX", formatDecimal(snapshot.getVixLevel()), "proxy if available",
                                inverseTone(snapshot.getVixChange20dPct())),
                        new StockMacroRegimeView.Metric("Dollar 20D", formatPercent(snapshot.getDollarProxyReturn20dPct()),
                                "strong dollar is risk headwind", inverseTone(snapshot.getDollarProxyReturn20dPct())),
                        new StockMacroRegimeView.Metric("Rates proxy 20D",
                                formatPercent(snapshot.getRateProxyReturn20dPct()), "bond proxy return", tone(
                                        snapshot.getRateProxyReturn20dPct()))));
    }

    private static StockMacroRegimeView emptyView() {
        return new StockMacroRegimeView(
                "No regime snapshot",
                "neutral",
                "-",
                "-",
                "Run the macro-regime batch after benchmark snapshots are available.",
                List.of());
    }

    private StockBacktestMapper requireMapper() {
        StockBacktestMapper mapper = backtestMapper.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("StockBacktestMapper is not available. Run with the mariadb profile.");
        }
        return mapper;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static double number(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    private static BigDecimal decimal(double value) {
        if (!Double.isFinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimalOrNull(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String formatScore(Integer value) {
        return value == null ? "-" : value + " / 100";
    }

    private static String formatPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String tone(BigDecimal value) {
        if (value == null) {
            return "neutral";
        }
        return value != null && value.signum() >= 0 ? "positive" : "negative";
    }

    private static String inverseTone(BigDecimal value) {
        if (value == null) {
            return "neutral";
        }
        return value != null && value.signum() <= 0 ? "positive" : "negative";
    }

    private static String breadthTone(BigDecimal value) {
        if (value == null) {
            return "neutral";
        }
        return value != null && value.compareTo(BigDecimal.valueOf(50)) >= 0 ? "positive" : "negative";
    }

    private static class BreadthAccumulator {
        private int advancers;
        private int total;

        void add(boolean advancer) {
            if (advancer) {
                advancers++;
            }
            total++;
        }

        double advancerPct() {
            return total == 0 ? Double.NaN : advancers * 100.0d / total;
        }
    }
}
