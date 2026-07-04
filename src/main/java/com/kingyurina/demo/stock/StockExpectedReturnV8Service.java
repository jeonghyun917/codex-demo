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
public class StockExpectedReturnV8Service {

    public static final String MODEL_VERSION = "EXPECTED_RETURN_V8";
    private static final String SOURCE_HISTORICAL = "FRED_MACRO_FEATURE_ADJUSTED_EXPECTED_RETURN_V8";
    private static final String SOURCE_LIVE = "LIVE_FRED_MACRO_FEATURE_ADJUSTED_EXPECTED_RETURN_V8";
    private static final String SOURCE_FACTOR = "FRED_MACRO_FEATURE_ADJUSTED_EXPECTED_RETURN_V8";
    private static final String SOURCE_CALIBRATION = "FRED_MACRO_FEATURE_V8_VALIDATION";
    private static final String SOURCE_MODEL = "EXPECTED_RETURN_V7";
    private static final int MIN_CALIBRATION_BUCKET_ROWS = 50;

    private final ObjectProvider<StockBacktestMapper> stockBacktestMapper;

    public StockExpectedReturnV8Service(ObjectProvider<StockBacktestMapper> stockBacktestMapper) {
        this.stockBacktestMapper = stockBacktestMapper;
    }

    public StockPortfolioBacktestService.ExpectedReturnRefreshResult refreshHistorical(String indexCode,
            int resultLimit, int dateLimit) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusYears(8);
        List<StockExpectedReturnSnapshot> sourceSnapshots =
                sourceSnapshots(mapper, indexCode, fromDate, toDate, SOURCE_MODEL);
        if (sourceSnapshots.isEmpty()) {
            return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
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
            return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        LocalDate targetFrom = targetDates.get(0);
        LocalDate targetTo = targetDates.get(targetDates.size() - 1);
        NavigableMap<LocalDate, StockMacroFeatureSnapshot> macroLookup =
                macroLookup(mapper.findMacroFeatureSnapshots(indexCode, targetFrom.minusDays(45), targetTo));
        Map<LocalDate, List<StockExpectedReturnSnapshot>> snapshotsByDate = sourceSnapshots.stream()
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
                        LinkedHashMap::new, Collectors.averagingDouble(StockExpectedReturnV8Service::returnPct)));

        int savedRows = 0;
        int processedDates = 0;
        int trainedRows = sourceSnapshots.stream()
                .map(StockExpectedReturnSnapshot::getSampleCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        List<PredictionEvaluation> evaluations = new ArrayList<>();
        for (LocalDate targetDate : targetDates) {
            List<StockExpectedReturnSnapshot> targetSnapshots = snapshotsByDate.getOrDefault(targetDate, List.of());
            if (targetSnapshots.isEmpty()) {
                continue;
            }
            mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, MODEL_VERSION);
            mapper.copyExpectedReturnFactorContributions(indexCode, targetDate, SOURCE_MODEL, MODEL_VERSION, SOURCE_FACTOR);
            boolean processedAny = false;
            StockMacroFeatureSnapshot macro = floorMacro(macroLookup, targetDate);
            for (StockExpectedReturnSnapshot sourceSnapshot : targetSnapshots) {
                StockExpectedReturnSnapshot adjusted = macroAdjustedSnapshot(sourceSnapshot, macro, SOURCE_HISTORICAL);
                mapper.upsertExpectedReturnSnapshot(adjusted);
                StockBacktestResult actual = resultsByKey.get(new ExpectedResultKey(sourceSnapshot.getSignalDate(),
                        sourceSnapshot.getSymbol(), valueOr(sourceSnapshot.getHorizonDays(), 0)));
                Double benchmark = benchmarkByKey.get(new ExpectedBenchmarkKey(sourceSnapshot.getSignalDate(),
                        valueOr(sourceSnapshot.getHorizonDays(), 0)));
                if (actual != null && benchmark != null) {
                    evaluations.add(new PredictionEvaluation(valueOr(sourceSnapshot.getHorizonDays(), 0),
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
            return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        LocalDate targetDate = LocalDate.now();
        List<StockExpectedReturnSnapshot> sourceSnapshots =
                sourceSnapshots(mapper, indexCode, targetDate, targetDate, SOURCE_MODEL);
        if (sourceSnapshots.isEmpty()) {
            return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0, 0, 0);
        }
        StockMacroFeatureSnapshot macro = mapper.findLatestMacroFeatureSnapshot(indexCode);
        mapper.deleteExpectedReturnFactorContributions(indexCode, targetDate, MODEL_VERSION);
        mapper.copyExpectedReturnFactorContributions(indexCode, targetDate, SOURCE_MODEL, MODEL_VERSION, SOURCE_FACTOR);
        int savedRows = 0;
        int trainedRows = 0;
        for (StockExpectedReturnSnapshot sourceSnapshot : sourceSnapshots) {
            trainedRows += valueOr(sourceSnapshot.getSampleCount(), 0);
            mapper.upsertExpectedReturnSnapshot(macroAdjustedSnapshot(sourceSnapshot, macro, SOURCE_LIVE));
            savedRows++;
        }
        return new StockPortfolioBacktestService.ExpectedReturnRefreshResult(savedRows > 0 ? 1 : 0,
                savedRows, trainedRows, 0, 0);
    }

    private static List<StockExpectedReturnSnapshot> sourceSnapshots(StockBacktestMapper mapper, String indexCode,
            LocalDate fromDate, LocalDate toDate, String modelVersion) {
        return mapper.findExpectedReturnSnapshots(indexCode, fromDate, toDate).stream()
                .filter(snapshot -> modelVersion.equals(snapshot.getModelVersion()))
                .toList();
    }

    private static StockExpectedReturnSnapshot macroAdjustedSnapshot(StockExpectedReturnSnapshot source,
            StockMacroFeatureSnapshot macro, String sourceName) {
        int horizonDays = valueOr(source.getHorizonDays(), 20);
        double expectedReturn = number(source.getExpectedReturnPct());
        double expectedExcess = number(source.getExpectedExcessReturnPct());
        double p10 = number(source.getReturnP10Pct());
        double p50 = number(source.getReturnP50Pct());
        double p90 = number(source.getReturnP90Pct());
        double exP10 = number(source.getExcessP10Pct());
        double exP50 = number(source.getExcessP50Pct());
        double exP90 = number(source.getExcessP90Pct());
        double upside = number(source.getUpsideProbabilityPct());
        double calibratedUpside = number(source.getCalibratedUpsideProbabilityPct());
        double downside = number(source.getDownsideProbabilityPct());
        double drawdownRisk = number(source.getDrawdownRiskPct());
        int confidence = valueOr(source.getConfidence(), 0);

        MacroAdjustment adjustment = macroAdjustment(macro, horizonDays);
        expectedReturn = addIfFinite(expectedReturn, adjustment.expectedReturnShiftPct());
        expectedExcess = addIfFinite(expectedExcess, adjustment.expectedReturnShiftPct());
        p10 = addIfFinite(p10, adjustment.returnP10ShiftPct());
        p50 = addIfFinite(p50, adjustment.expectedReturnShiftPct());
        p90 = addIfFinite(p90, adjustment.returnP90ShiftPct());
        exP10 = addIfFinite(exP10, adjustment.returnP10ShiftPct());
        exP50 = addIfFinite(exP50, adjustment.expectedReturnShiftPct());
        exP90 = addIfFinite(exP90, adjustment.returnP90ShiftPct());
        upside = addIfFinite(upside, adjustment.probabilityShiftPct());
        calibratedUpside = addIfFinite(calibratedUpside, adjustment.probabilityShiftPct());
        downside = addIfFinite(downside, -adjustment.probabilityShiftPct() * 0.65d + adjustment.downsideShiftPct());
        drawdownRisk = addIfFinite(drawdownRisk, adjustment.drawdownShiftPct());
        confidence = Math.max(0, Math.min(78, confidence + adjustment.confidenceShift()));

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
        adjusted.setSource(sourceName);
        return adjusted;
    }

    private static MacroAdjustment macroAdjustment(StockMacroFeatureSnapshot macro, int horizonDays) {
        if (macro == null || macro.getMacroFeatureScore() == null) {
            return new MacroAdjustment(0.0d, -0.20d, 0.10d, 0.0d, 0.35d, -4);
        }
        double featureScore = macro.getMacroFeatureScore();
        double tightness = valueOr(macro.getMacroTightnessScore(), 50);
        double growthStress = valueOr(macro.getMacroGrowthStressScore(), 50);
        double riskScore = valueOr(macro.getMacroRiskScore(), 50);
        double yieldSpread = number(macro.getYieldSpreadPct());
        double vix = number(macro.getVixLevel());
        double dollarChange = number(macro.getDollarChange20dPct());
        double stress = (50.0d - featureScore) / 50.0d;
        stress += Math.max(0.0d, 45.0d - tightness) / 90.0d;
        stress += Math.max(0.0d, 45.0d - growthStress) / 100.0d;
        stress += Math.max(0.0d, 45.0d - riskScore) / 100.0d;
        if (Double.isFinite(yieldSpread) && yieldSpread < -0.4d) {
            stress += Math.min(0.18d, Math.abs(yieldSpread) * 0.06d);
        }
        if (Double.isFinite(vix) && vix > 25.0d) {
            stress += Math.min(0.22d, (vix - 25.0d) * 0.012d);
        }
        if (Double.isFinite(dollarChange) && dollarChange > 1.5d) {
            stress += Math.min(0.12d, dollarChange * 0.015d);
        }
        double support = Math.max(0.0d, featureScore - 55.0d) / 70.0d;
        double horizonScale = horizonDays <= 5 ? 0.35d : horizonDays <= 20 ? 0.80d : 1.35d;
        double shift = clamp((support - Math.max(0.0d, stress)) * horizonScale, -1.80d, 1.20d);
        double p10Shift = shift - Math.max(0.0d, stress) * horizonScale * 0.35d;
        double p90Shift = shift + support * horizonScale * 0.25d;
        double probabilityShift = clamp(shift * 3.3d, -7.5d, 5.0d);
        double downsideShift = clamp(Math.max(0.0d, stress) * horizonScale * 3.0d - support * 1.2d,
                -2.0d, 8.0d);
        double drawdownShift = clamp(Math.max(0.0d, stress) * horizonScale * 1.1d - support * 0.35d,
                -0.6d, 2.5d);
        int confidenceShift = featureScore >= 45 && featureScore <= 65 ? 1 : -2;
        if (stress > 0.55d) {
            confidenceShift -= 2;
        }
        return new MacroAdjustment(shift, p10Shift, p90Shift, probabilityShift, downsideShift + drawdownShift,
                confidenceShift);
    }

    private static NavigableMap<LocalDate, StockMacroFeatureSnapshot> macroLookup(
            List<StockMacroFeatureSnapshot> snapshots) {
        NavigableMap<LocalDate, StockMacroFeatureSnapshot> lookup = new TreeMap<>();
        if (snapshots == null) {
            return lookup;
        }
        snapshots.stream()
                .filter(snapshot -> snapshot.getSnapshotDate() != null)
                .forEach(snapshot -> lookup.put(snapshot.getSnapshotDate(), snapshot));
        return lookup;
    }

    private static StockMacroFeatureSnapshot floorMacro(NavigableMap<LocalDate, StockMacroFeatureSnapshot> lookup,
            LocalDate targetDate) {
        if (lookup == null || targetDate == null) {
            return null;
        }
        Map.Entry<LocalDate, StockMacroFeatureSnapshot> entry = lookup.floorEntry(targetDate);
        return entry == null ? null : entry.getValue();
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

    private record ExpectedResultKey(LocalDate signalDate, String symbol, int horizonDays) {
    }

    private record ExpectedBenchmarkKey(LocalDate signalDate, int horizonDays) {
    }

    private record PredictionEvaluation(int horizonDays, double predictedUpsidePct, boolean actualUpside) {
    }

    private record MacroAdjustment(double expectedReturnShiftPct, double returnP10ShiftPct, double returnP90ShiftPct,
            double probabilityShiftPct, double downsideShiftPct, int confidenceShift) {

        private double drawdownShiftPct() {
            return downsideShiftPct * 0.35d;
        }
    }
}
