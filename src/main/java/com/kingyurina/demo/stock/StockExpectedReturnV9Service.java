package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockExpectedReturnV9Service {

    public static final String MODEL_VERSION = "EXPECTED_RETURN_V9";
    private static final String SOURCE_MODEL = "EXPECTED_RETURN_V8";
    private static final String SOURCE_HISTORICAL = "PROVENANCE_STABILIZED_EXPECTED_RETURN_V9";
    private static final String SOURCE_LIVE = "LIVE_PROVENANCE_STABILIZED_EXPECTED_RETURN_V9";
    private static final String SOURCE_FACTOR = "PROVENANCE_STABILIZED_EXPECTED_RETURN_V9";
    private static final String SOURCE_CALIBRATION = "PROVENANCE_STABILIZED_V9_VALIDATION";
    private static final int MIN_CALIBRATION_BUCKET_ROWS = 50;

    private final ObjectProvider<StockBacktestMapper> stockBacktestMapper;

    public StockExpectedReturnV9Service(ObjectProvider<StockBacktestMapper> stockBacktestMapper) {
        this.stockBacktestMapper = stockBacktestMapper;
    }

    public StockPortfolioBacktestService.ExpectedReturnRefreshResult refreshHistorical(String indexCode,
            int resultLimit, int dateLimit) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return empty();
        }
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusYears(8);
        List<StockExpectedReturnSnapshot> sourceSnapshots = sourceSnapshots(mapper, indexCode, fromDate, toDate);
        if (sourceSnapshots.isEmpty()) {
            return empty();
        }
        List<LocalDate> targetDates = sourceSnapshots.stream()
                .map(StockExpectedReturnSnapshot::getSignalDate)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(Math.max(1, dateLimit))
                .sorted()
                .toList();
        if (targetDates.isEmpty()) {
            return empty();
        }
        LocalDate targetFrom = targetDates.get(0);
        LocalDate targetTo = targetDates.get(targetDates.size() - 1);
        MarketLookup marketLookup = MarketLookup.from(
                mapper.findMarketSnapshots(indexCode, targetFrom.minusDays(30), targetTo));
        Map<LocalDate, List<StockExpectedReturnSnapshot>> byDate = sourceSnapshots.stream()
                .filter(snapshot -> targetDates.contains(snapshot.getSignalDate()))
                .collect(Collectors.groupingBy(StockExpectedReturnSnapshot::getSignalDate,
                        LinkedHashMap::new, Collectors.toList()));
        List<StockBacktestResult> results = mapper.findResults(indexCode, Math.max(1, resultLimit)).stream()
                .filter(row -> row.getSignalDate() != null && row.getSymbol() != null && row.getHorizonDays() != null)
                .toList();
        Map<ExpectedResultKey, StockBacktestResult> resultsByKey = results.stream()
                .collect(Collectors.toMap(row -> new ExpectedResultKey(row.getSignalDate(), row.getSymbol(),
                        row.getHorizonDays()), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<ExpectedBenchmarkKey, Double> benchmarkByKey = results.stream()
                .collect(Collectors.groupingBy(row -> new ExpectedBenchmarkKey(row.getSignalDate(), row.getHorizonDays()),
                        LinkedHashMap::new, Collectors.averagingDouble(StockExpectedReturnV9Service::returnPct)));

        int savedRows = 0;
        int processedDates = 0;
        int trainedRows = sourceSnapshots.stream()
                .map(StockExpectedReturnSnapshot::getSampleCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        List<PredictionEvaluation> evaluations = new ArrayList<>();
        for (LocalDate targetDate : targetDates) {
            List<StockExpectedReturnSnapshot> rows = byDate.getOrDefault(targetDate, List.of());
            if (rows.isEmpty()) {
                continue;
            }
            mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, MODEL_VERSION);
            mapper.copyExpectedReturnFactorContributions(indexCode, targetDate, SOURCE_MODEL, MODEL_VERSION, SOURCE_FACTOR);
            boolean processedAny = false;
            for (StockExpectedReturnSnapshot source : rows) {
                StockMarketSnapshot market = marketLookup.floor(source.getSymbol(), targetDate);
                StockExpectedReturnSnapshot adjusted = provenanceAdjustedSnapshot(source, market, SOURCE_HISTORICAL);
                if (adjusted == null) {
                    continue;
                }
                mapper.upsertExpectedReturnSnapshot(adjusted);
                StockBacktestResult actual = resultsByKey.get(new ExpectedResultKey(source.getSignalDate(),
                        source.getSymbol(), valueOr(source.getHorizonDays(), 0)));
                Double benchmark = benchmarkByKey.get(new ExpectedBenchmarkKey(source.getSignalDate(),
                        valueOr(source.getHorizonDays(), 0)));
                if (actual != null && benchmark != null) {
                    evaluations.add(new PredictionEvaluation(valueOr(source.getHorizonDays(), 0),
                            number(adjusted.getCalibratedUpsideProbabilityPct()),
                            returnPct(actual) - benchmark > 0.0d));
                }
                savedRows++;
                processedAny = true;
            }
            if (processedAny) {
                processedDates++;
            }
        }
        mapper.deleteExpectedReturnCalibrations(indexCode, MODEL_VERSION);
        int calibrationRows = 0;
        for (StockExpectedReturnCalibration calibration : calibrations(indexCode, evaluations)) {
            mapper.upsertExpectedReturnCalibration(calibration);
            calibrationRows++;
        }
        return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(processedDates, savedRows, trainedRows,
                0, calibrationRows);
    }

    public StockPortfolioBacktestService.ExpectedReturnRefreshResult refreshLatest(String indexCode) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return empty();
        }
        LocalDate targetDate = LocalDate.now();
        List<StockExpectedReturnSnapshot> sourceSnapshots = sourceSnapshots(mapper, indexCode, targetDate, targetDate);
        if (sourceSnapshots.isEmpty()) {
            return empty();
        }
        MarketLookup marketLookup = MarketLookup.from(
                mapper.findMarketSnapshots(indexCode, targetDate.minusDays(45), targetDate));
        mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, MODEL_VERSION);
        mapper.copyExpectedReturnFactorContributions(indexCode, targetDate, SOURCE_MODEL, MODEL_VERSION, SOURCE_FACTOR);
        int savedRows = 0;
        int trainedRows = 0;
        for (StockExpectedReturnSnapshot source : sourceSnapshots) {
            trainedRows += valueOr(source.getSampleCount(), 0);
            StockMarketSnapshot market = marketLookup.floor(source.getSymbol(), targetDate);
            StockExpectedReturnSnapshot adjusted = provenanceAdjustedSnapshot(source, market, SOURCE_LIVE);
            if (adjusted == null) {
                continue;
            }
            mapper.upsertExpectedReturnSnapshot(adjusted);
            savedRows++;
        }
        return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(savedRows > 0 ? 1 : 0,
                savedRows, trainedRows, 0, 0);
    }

    private static List<StockExpectedReturnSnapshot> sourceSnapshots(StockBacktestMapper mapper, String indexCode,
            LocalDate fromDate, LocalDate toDate) {
        return mapper.findExpectedReturnSnapshots(indexCode, fromDate, toDate).stream()
                .filter(snapshot -> SOURCE_MODEL.equals(snapshot.getModelVersion()))
                .toList();
    }

    private static StockExpectedReturnSnapshot provenanceAdjustedSnapshot(StockExpectedReturnSnapshot source,
            StockMarketSnapshot market, String sourceName) {
        if (market == null || market.getMarketCapUsd() == null || number(market.getMarketCapUsd()) <= 0.0d) {
            return null;
        }
        ProvenanceAdjustment adjustment = provenanceAdjustment(market);
        double expectedReturn = shrink(number(source.getExpectedReturnPct()), adjustment.returnScale());
        double expectedExcess = shrink(number(source.getExpectedExcessReturnPct()), adjustment.returnScale());
        double p10 = shrink(number(source.getReturnP10Pct()), adjustment.tailScale());
        double p50 = shrink(number(source.getReturnP50Pct()), adjustment.returnScale());
        double p90 = shrink(number(source.getReturnP90Pct()), adjustment.tailScale());
        double exP10 = shrink(number(source.getExcessP10Pct()), adjustment.tailScale());
        double exP50 = shrink(number(source.getExcessP50Pct()), adjustment.returnScale());
        double exP90 = shrink(number(source.getExcessP90Pct()), adjustment.tailScale());
        double upside = shrinkProbability(number(source.getUpsideProbabilityPct()), adjustment.probabilityScale());
        double calibratedUpside = shrinkProbability(number(source.getCalibratedUpsideProbabilityPct()),
                adjustment.probabilityScale());
        double downside = shrinkProbability(number(source.getDownsideProbabilityPct()), adjustment.probabilityScale());
        double drawdownRisk = addIfFinite(number(source.getDrawdownRiskPct()), adjustment.drawdownAddPct());
        int confidence = Math.max(0, Math.min(80, valueOr(source.getConfidence(), 0) + adjustment.confidenceShift()));

        StockExpectedReturnSnapshot adjusted = new StockExpectedReturnSnapshot();
        adjusted.setIndexCode(source.getIndexCode());
        adjusted.setSignalDate(source.getSignalDate());
        adjusted.setSymbol(source.getSymbol());
        adjusted.setHorizonDays(source.getHorizonDays());
        adjusted.setExpectedReturnPct(decimalOrNull(expectedReturn));
        adjusted.setExpectedExcessReturnPct(decimalOrNull(expectedExcess));
        adjusted.setReturnP10Pct(decimalOrNull(p10));
        adjusted.setReturnP50Pct(decimalOrNull(p50));
        adjusted.setReturnP90Pct(decimalOrNull(p90));
        adjusted.setExcessP10Pct(decimalOrNull(exP10));
        adjusted.setExcessP50Pct(decimalOrNull(exP50));
        adjusted.setExcessP90Pct(decimalOrNull(exP90));
        adjusted.setUpsideProbabilityPct(decimalOrNull(clamp(upside, 1.0d, 99.0d)));
        adjusted.setCalibratedUpsideProbabilityPct(decimalOrNull(clamp(calibratedUpside, 1.0d, 99.0d)));
        adjusted.setDownsideProbabilityPct(decimalOrNull(clamp(downside, 1.0d, 99.0d)));
        adjusted.setDrawdownRiskPct(decimalOrNull(Math.max(0.0d, drawdownRisk)));
        adjusted.setConfidence(confidence);
        adjusted.setSampleCount(source.getSampleCount());
        adjusted.setSectorSampleCount(source.getSectorSampleCount());
        adjusted.setScoreBucket(source.getScoreBucket());
        adjusted.setCalibrationBucket(probabilityBucket(clamp(calibratedUpside, 1.0d, 99.0d)));
        adjusted.setCalibrationErrorPct(source.getCalibrationErrorPct());
        adjusted.setModelVersion(MODEL_VERSION);
        adjusted.setSource(sourceName + "_" + adjustment.sourceTag());
        return adjusted;
    }

    private static ProvenanceAdjustment provenanceAdjustment(StockMarketSnapshot market) {
        String sharesSource = safeUpper(market.getSharesSource());
        String marketCapSource = safeUpper(market.getMarketCapSource());
        if (sharesSource.startsWith("SEC_XBRL")) {
            return new ProvenanceAdjustment(1.0d, 1.0d, 1.0d, 0, 0.0d, "SEC_PIT");
        }
        if (sharesSource.contains("ALIAS") || marketCapSource.contains("ALIAS")) {
            return new ProvenanceAdjustment(0.88d, 0.82d, 0.86d, -8, 0.35d, "ALIAS_SHARES");
        }
        if (sharesSource.contains("CURRENT") || marketCapSource.contains("CURRENT")) {
            return new ProvenanceAdjustment(0.92d, 0.88d, 0.90d, -6, 0.25d, "CURRENT_PROXY_SHARES");
        }
        return new ProvenanceAdjustment(0.78d, 0.70d, 0.74d, -14, 0.55d, "WEAK_PROVENANCE");
    }

    private static List<StockExpectedReturnCalibration> calibrations(String indexCode,
            List<PredictionEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return List.of();
        }
        List<StockExpectedReturnCalibration> rows = new ArrayList<>();
        Map<Integer, Map<Integer, List<PredictionEvaluation>>> grouped = evaluations.stream()
                .filter(row -> Double.isFinite(row.predictedUpsidePct()))
                .collect(Collectors.groupingBy(PredictionEvaluation::horizonDays,
                        LinkedHashMap::new,
                        Collectors.groupingBy(row -> probabilityBucket(row.predictedUpsidePct()),
                                LinkedHashMap::new, Collectors.toList())));
        for (Map.Entry<Integer, Map<Integer, List<PredictionEvaluation>>> horizonEntry : grouped.entrySet()) {
            for (Map.Entry<Integer, List<PredictionEvaluation>> bucketEntry : horizonEntry.getValue().entrySet()) {
                List<PredictionEvaluation> bucketRows = bucketEntry.getValue();
                if (bucketRows.size() < MIN_CALIBRATION_BUCKET_ROWS) {
                    continue;
                }
                double predicted = bucketRows.stream()
                        .mapToDouble(PredictionEvaluation::predictedUpsidePct)
                        .average()
                        .orElse(Double.NaN);
                double actual = bucketRows.stream()
                        .mapToDouble(row -> row.actualUpside() ? 100.0d : 0.0d)
                        .average()
                        .orElse(Double.NaN);
                double brier = bucketRows.stream()
                        .mapToDouble(row -> {
                            double p = clamp(row.predictedUpsidePct(), 0.0d, 100.0d) / 100.0d;
                            double y = row.actualUpside() ? 1.0d : 0.0d;
                            double error = p - y;
                            return error * error;
                        })
                        .average()
                        .orElse(Double.NaN);
                StockExpectedReturnCalibration calibration = new StockExpectedReturnCalibration();
                calibration.setIndexCode(indexCode);
                calibration.setModelVersion(MODEL_VERSION);
                calibration.setHorizonDays(horizonEntry.getKey());
                calibration.setProbabilityBucket(bucketEntry.getKey());
                calibration.setSampleCount(bucketRows.size());
                calibration.setAveragePredictedUpsidePct(decimalOrNull(predicted));
                calibration.setActualUpsideRatePct(decimalOrNull(actual));
                calibration.setCalibrationErrorPct(decimalOrNull(actual - predicted));
                calibration.setBrierScore(decimalOrNull(brier));
                calibration.setSource(SOURCE_CALIBRATION);
                rows.add(calibration);
            }
        }
        return rows;
    }

    private static StockPortfolioBacktestService.ExpectedReturnRefreshResult empty() {
        return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
    }

    private static double shrink(double value, double scale) {
        return Double.isFinite(value) ? value * scale : value;
    }

    private static double shrinkProbability(double value, double scale) {
        return Double.isFinite(value) ? 50.0d + (value - 50.0d) * scale : value;
    }

    private static double addIfFinite(double value, double addend) {
        return Double.isFinite(value) ? value + addend : value;
    }

    private static int probabilityBucket(double value) {
        if (!Double.isFinite(value)) {
            return -1;
        }
        return (int) Math.max(0, Math.min(90, Math.floor(value / 10.0d) * 10));
    }

    private static double returnPct(StockBacktestResult result) {
        return result.getForwardReturnPct() == null ? 0.0d : result.getForwardReturnPct().doubleValue();
    }

    private static double number(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    private static int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static BigDecimal decimalOrNull(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value) : null;
    }

    private static String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private record ExpectedResultKey(LocalDate signalDate, String symbol, int horizonDays) {
    }

    private record ExpectedBenchmarkKey(LocalDate signalDate, int horizonDays) {
    }

    private record PredictionEvaluation(int horizonDays, double predictedUpsidePct, boolean actualUpside) {
    }

    private record ProvenanceAdjustment(double returnScale, double tailScale, double probabilityScale,
            int confidenceShift, double drawdownAddPct, String sourceTag) {
    }

    private static final class MarketLookup {
        private final Map<String, NavigableMap<LocalDate, StockMarketSnapshot>> bySymbol;

        private MarketLookup(Map<String, NavigableMap<LocalDate, StockMarketSnapshot>> bySymbol) {
            this.bySymbol = bySymbol;
        }

        private static MarketLookup from(List<StockMarketSnapshot> rows) {
            Map<String, NavigableMap<LocalDate, StockMarketSnapshot>> mapped = new LinkedHashMap<>();
            if (rows == null) {
                return new MarketLookup(mapped);
            }
            for (StockMarketSnapshot row : rows) {
                if (row.getSymbol() == null || row.getSnapshotDate() == null) {
                    continue;
                }
                mapped.computeIfAbsent(row.getSymbol(), ignored -> new TreeMap<>())
                        .put(row.getSnapshotDate(), row);
            }
            return new MarketLookup(mapped);
        }

        private StockMarketSnapshot floor(String symbol, LocalDate date) {
            NavigableMap<LocalDate, StockMarketSnapshot> rows = bySymbol.get(symbol);
            if (rows == null || date == null) {
                return null;
            }
            Map.Entry<LocalDate, StockMarketSnapshot> entry = rows.floorEntry(date);
            return entry == null ? null : entry.getValue();
        }
    }
}
