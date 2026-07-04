package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockMacroFeatureService {

    private static final String SOURCE = "FRED_MACRO_FEATURE_V2_PIT_STRICT";
    private static final String DGS3MO = "DGS3MO";
    private static final String DGS10 = "DGS10";
    private static final String FEDFUNDS = "FEDFUNDS";
    private static final String CPIAUCSL = "CPIAUCSL";
    private static final String UNRATE = "UNRATE";
    private static final String VIXCLS = "VIXCLS";
    private static final String DTWEXBGS = "DTWEXBGS";

    private final ObjectProvider<StockBacktestMapper> stockBacktestMapper;

    public StockMacroFeatureService(ObjectProvider<StockBacktestMapper> stockBacktestMapper) {
        this.stockBacktestMapper = stockBacktestMapper;
    }

    public int refreshMacroFeatures(String indexCode, int dateLimit) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return 0;
        }
        String normalizedIndexCode = normalizeIndexCode(indexCode);
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusYears(8);
        List<LocalDate> targetDates = benchmarkDates(mapper, normalizedIndexCode, fromDate, toDate, dateLimit);
        if (targetDates.isEmpty() && !"SP500".equals(normalizedIndexCode)) {
            targetDates = benchmarkDates(mapper, "SP500", fromDate, toDate, dateLimit);
        }
        if (targetDates.isEmpty()) {
            return 0;
        }
        LocalDate targetFrom = targetDates.get(0);
        LocalDate targetTo = targetDates.get(targetDates.size() - 1);
        Map<String, List<StockMacroVintageSnapshot>> rowsBySeries =
                mapper.findMacroVintageSnapshots(normalizedIndexCode, targetFrom.minusMonths(18), targetTo).stream()
                        .filter(row -> row.getSeriesCode() != null && row.getObservationDate() != null)
                        .collect(Collectors.groupingBy(row -> row.getSeriesCode().toUpperCase(Locale.ROOT),
                                LinkedHashMap::new, Collectors.toList()));
        if (rowsBySeries.isEmpty() && !"SP500".equals(normalizedIndexCode)) {
            rowsBySeries = mapper.findMacroVintageSnapshots("SP500", targetFrom.minusMonths(18), targetTo).stream()
                    .filter(row -> row.getSeriesCode() != null && row.getObservationDate() != null)
                    .collect(Collectors.groupingBy(row -> row.getSeriesCode().toUpperCase(Locale.ROOT),
                            LinkedHashMap::new, Collectors.toList()));
        }
        if (rowsBySeries.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (LocalDate targetDate : targetDates) {
            StockMacroFeatureSnapshot snapshot = featureSnapshot(normalizedIndexCode, targetDate, rowsBySeries);
            if (snapshot != null) {
                mapper.upsertMacroFeatureSnapshot(snapshot);
                saved++;
            }
        }
        return saved;
    }

    private static List<LocalDate> benchmarkDates(StockBacktestMapper mapper, String indexCode, LocalDate fromDate,
            LocalDate toDate, int dateLimit) {
        return mapper.findBenchmarkReturns(indexCode, fromDate, toDate).stream()
                .map(StockBenchmarkReturn::getTradeDate)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(Math.max(1, dateLimit))
                .sorted()
                .toList();
    }

    public int refreshLatestMacroFeature(String indexCode) {
        return refreshMacroFeatures(indexCode, 3);
    }

    private static StockMacroFeatureSnapshot featureSnapshot(String indexCode, LocalDate targetDate,
            Map<String, List<StockMacroVintageSnapshot>> rowsBySeries) {
        SeriesPoint shortRate = valueAsOf(rowsBySeries.get(DGS3MO), targetDate);
        SeriesPoint longRate = valueAsOf(rowsBySeries.get(DGS10), targetDate);
        SeriesPoint fedFunds = valueAsOf(rowsBySeries.get(FEDFUNDS), targetDate);
        SeriesPoint cpi = valueAsOf(rowsBySeries.get(CPIAUCSL), targetDate);
        SeriesPoint unemployment = valueAsOf(rowsBySeries.get(UNRATE), targetDate);
        SeriesPoint vix = valueAsOf(rowsBySeries.get(VIXCLS), targetDate);
        SeriesPoint dollar = valueAsOf(rowsBySeries.get(DTWEXBGS), targetDate);

        double shortRatePct = value(shortRate);
        double longRatePct = value(longRate);
        double spread = finite(shortRatePct) && finite(longRatePct) ? longRatePct - shortRatePct : Double.NaN;
        double fedFundsPct = value(fedFunds);
        double cpiYoy = growthPct(value(cpi),
                valueAtOrBefore(rowsBySeries.get(CPIAUCSL), observationDate(cpi, targetDate).minusMonths(12), targetDate));
        double cpiMom = growthPct(value(cpi),
                valueAtOrBefore(rowsBySeries.get(CPIAUCSL), observationDate(cpi, targetDate).minusMonths(1), targetDate));
        double unemploymentRate = value(unemployment);
        double unemploymentChange3m = finite(unemploymentRate)
                ? unemploymentRate - valueAtOrBefore(rowsBySeries.get(UNRATE),
                        observationDate(unemployment, targetDate).minusMonths(3), targetDate)
                : Double.NaN;
        double vixLevel = value(vix);
        double vixChange20d = growthPct(vixLevel, valueAtOrBefore(rowsBySeries.get(VIXCLS),
                targetDate.minusDays(30), targetDate));
        double dollarIndex = value(dollar);
        double dollarChange20d = growthPct(dollarIndex, valueAtOrBefore(rowsBySeries.get(DTWEXBGS),
                targetDate.minusDays(30), targetDate));

        int tightness = macroTightnessScore(shortRatePct, longRatePct, spread, fedFundsPct);
        int growthStress = macroGrowthStressScore(cpiYoy, cpiMom, unemploymentRate, unemploymentChange3m);
        int risk = macroRiskScore(vixLevel, vixChange20d, dollarChange20d, spread);
        int featureScore = clampInt(Math.round((float) (tightness * 0.35d + growthStress * 0.30d + risk * 0.35d)));

        StockMacroFeatureSnapshot snapshot = new StockMacroFeatureSnapshot();
        snapshot.setIndexCode(indexCode);
        snapshot.setSnapshotDate(targetDate);
        snapshot.setShortRatePct(decimalOrNull(shortRatePct));
        snapshot.setLongRatePct(decimalOrNull(longRatePct));
        snapshot.setYieldSpreadPct(decimalOrNull(spread));
        snapshot.setFedFundsPct(decimalOrNull(fedFundsPct));
        snapshot.setCpiYoyPct(decimalOrNull(cpiYoy));
        snapshot.setCpiMomPct(decimalOrNull(cpiMom));
        snapshot.setUnemploymentRatePct(decimalOrNull(unemploymentRate));
        snapshot.setUnemploymentChange3mPct(decimalOrNull(unemploymentChange3m));
        snapshot.setVixLevel(decimalOrNull(vixLevel));
        snapshot.setVixChange20dPct(decimalOrNull(vixChange20d));
        snapshot.setDollarIndex(decimalOrNull(dollarIndex));
        snapshot.setDollarChange20dPct(decimalOrNull(dollarChange20d));
        snapshot.setMacroTightnessScore(tightness);
        snapshot.setMacroGrowthStressScore(growthStress);
        snapshot.setMacroRiskScore(risk);
        snapshot.setMacroFeatureScore(featureScore);
        snapshot.setSource(SOURCE);
        return snapshot;
    }

    private static int macroTightnessScore(double shortRate, double longRate, double spread, double fedFunds) {
        double score = 56.0d;
        if (finite(shortRate)) {
            score -= Math.max(0.0d, shortRate - 2.5d) * 5.5d;
        }
        if (finite(fedFunds)) {
            score -= Math.max(0.0d, fedFunds - 2.5d) * 3.5d;
        }
        if (finite(spread)) {
            score += Math.min(2.0d, Math.max(-2.0d, spread)) * 7.0d;
        }
        if (finite(longRate) && finite(shortRate) && longRate < shortRate) {
            score -= 8.0d;
        }
        return clampInt(Math.round((float) score));
    }

    private static int macroGrowthStressScore(double cpiYoy, double cpiMom, double unemploymentRate,
            double unemploymentChange3m) {
        double score = 58.0d;
        if (finite(cpiYoy)) {
            score -= Math.abs(cpiYoy - 2.2d) * 2.4d;
            score -= Math.max(0.0d, cpiYoy - 4.0d) * 3.0d;
        }
        if (finite(cpiMom)) {
            score -= Math.max(0.0d, cpiMom - 0.35d) * 12.0d;
        }
        if (finite(unemploymentRate)) {
            score -= Math.max(0.0d, unemploymentRate - 5.0d) * 4.0d;
        }
        if (finite(unemploymentChange3m)) {
            score -= Math.max(0.0d, unemploymentChange3m) * 18.0d;
            score += Math.max(0.0d, -unemploymentChange3m) * 4.0d;
        }
        return clampInt(Math.round((float) score));
    }

    private static int macroRiskScore(double vix, double vixChange20d, double dollarChange20d, double spread) {
        double score = 58.0d;
        if (finite(vix)) {
            score -= Math.max(0.0d, vix - 18.0d) * 1.15d;
            score += Math.max(0.0d, 16.0d - vix) * 0.45d;
        }
        if (finite(vixChange20d)) {
            score -= Math.max(0.0d, vixChange20d) * 0.12d;
        }
        if (finite(dollarChange20d)) {
            score -= Math.max(0.0d, dollarChange20d) * 1.1d;
        }
        if (finite(spread) && spread < -0.4d) {
            score -= 4.0d;
        }
        return clampInt(Math.round((float) score));
    }

    private static SeriesPoint valueAsOf(List<StockMacroVintageSnapshot> rows, LocalDate targetDate) {
        if (rows == null || rows.isEmpty() || targetDate == null) {
            return null;
        }
        return rows.stream()
                .filter(row -> row.getValue() != null
                        && row.getObservationDate() != null
                        && !row.getObservationDate().isAfter(targetDate)
                        && activeAsOf(row, targetDate))
                .max(Comparator
                        .comparing(StockMacroVintageSnapshot::getObservationDate)
                        .thenComparing(row -> row.getRealtimeStart() == null ? LocalDate.MIN : row.getRealtimeStart()))
                .map(row -> new SeriesPoint(row.getObservationDate(), row.getValue().doubleValue()))
                .orElse(null);
    }

    private static double valueAtOrBefore(List<StockMacroVintageSnapshot> rows, LocalDate observationCutoff,
            LocalDate targetDate) {
        if (rows == null || rows.isEmpty() || observationCutoff == null || targetDate == null) {
            return Double.NaN;
        }
        NavigableMap<LocalDate, SeriesPoint> points = new TreeMap<>();
        rows.stream()
                .filter(row -> row.getValue() != null
                        && row.getObservationDate() != null
                        && !row.getObservationDate().isAfter(observationCutoff)
                        && activeAsOf(row, targetDate))
                .sorted(Comparator
                        .comparing(StockMacroVintageSnapshot::getObservationDate)
                        .thenComparing(row -> row.getRealtimeStart() == null ? LocalDate.MIN : row.getRealtimeStart()))
                .forEach(row -> points.put(row.getObservationDate(),
                        new SeriesPoint(row.getObservationDate(), row.getValue().doubleValue())));
        Map.Entry<LocalDate, SeriesPoint> entry = points.floorEntry(observationCutoff);
        return entry == null ? Double.NaN : entry.getValue().value();
    }

    private static double growthPct(double current, double previous) {
        if (!finite(current) || !finite(previous) || previous == 0.0d) {
            return Double.NaN;
        }
        return (current / previous - 1.0d) * 100.0d;
    }

    private static boolean activeAsOf(StockMacroVintageSnapshot row, LocalDate targetDate) {
        if (targetDate == null) {
            return false;
        }
        LocalDate realtimeStart = row.getRealtimeStart();
        LocalDate realtimeEnd = row.getRealtimeEnd();
        return (realtimeStart == null || !realtimeStart.isAfter(targetDate))
                && (realtimeEnd == null || !realtimeEnd.isBefore(targetDate));
    }

    private static LocalDate observationDate(SeriesPoint point, LocalDate fallback) {
        return point == null || point.observationDate() == null ? fallback : point.observationDate();
    }

    private static double value(SeriesPoint point) {
        return point == null ? Double.NaN : point.value();
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static int clampInt(float value) {
        if (!Float.isFinite(value)) {
            return 50;
        }
        return Math.max(0, Math.min(100, Math.round(value)));
    }

    private static BigDecimal decimalOrNull(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value) : null;
    }

    private static String normalizeIndexCode(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        if ("NASDAQ100".equals(value) || "NDX".equals(value)) {
            return "NASDAQ100";
        }
        if ("DOW30".equals(value) || "DJI".equals(value)) {
            return "DOW30";
        }
        return "SP500";
    }

    private record SeriesPoint(LocalDate observationDate, double value) {
    }
}
