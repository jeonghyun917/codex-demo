package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Offline research only: known-as-of observations are NOT fabricated realtime intervals. */
public final class StockMacroAsOfResearchFeatures {
    public record Observation(String seriesCode, LocalDate observationDate, LocalDate asOfDate,
            BigDecimal value, String sourceId) {}
    private static final Set<String> SERIES = Set.of("DGS3MO", "DGS10", "FEDFUNDS", "CPIAUCSL", "UNRATE", "VIXCLS", "DTWEXBGS");

    private StockMacroAsOfResearchFeatures() {}

    public static StockMacroFeatureSnapshot build(String index, LocalDate decision, List<Observation> observations) {
        if (index == null || !index.matches("(SP500|NASDAQ100)_MACRO_FREE_20261018") || decision == null
                || observations == null || observations.isEmpty())
            throw new IllegalArgumentException("Explicit macro research namespace, decision and observations required");
        LocalDate asOf = decision.minusDays(1);
        var series = new HashMap<String, NavigableMap<LocalDate, Double>>();
        for (var row : observations) {
            if (row == null || !SERIES.contains(row.seriesCode()) || !asOf.equals(row.asOfDate())
                    || row.observationDate() == null || row.observationDate().isAfter(asOf)
                    || row.value() == null || !Double.isFinite(row.value().doubleValue())
                    || !("alfred-" + row.seriesCode() + "-" + asOf).equals(row.sourceId()))
                throw new IllegalArgumentException("Wrong as-of/source, future or invalid macro observation");
            if (series.computeIfAbsent(row.seriesCode(), key -> new TreeMap<>())
                    .putIfAbsent(row.observationDate(), row.value().doubleValue()) != null)
                throw new IllegalArgumentException("Duplicate as-of macro observation");
        }
        if (!series.keySet().equals(SERIES)) throw new IllegalArgumentException("All seven source series are required");

        double shortRate = latest(series, "DGS3MO"), longRate = latest(series, "DGS10");
        double spread = longRate - shortRate, fedFunds = latest(series, "FEDFUNDS");
        double cpiYoy = growth(latest(series, "CPIAUCSL"), before(series, "CPIAUCSL",
                series.get("CPIAUCSL").lastKey().minusMonths(12)));
        double cpiMom = growth(latest(series, "CPIAUCSL"), before(series, "CPIAUCSL",
                series.get("CPIAUCSL").lastKey().minusMonths(1)));
        double unemployment = latest(series, "UNRATE");
        double unemploymentChange = unemployment - before(series, "UNRATE", series.get("UNRATE").lastKey().minusMonths(3));
        double vix = latest(series, "VIXCLS"), dollar = latest(series, "DTWEXBGS");
        // Retain the existing model's 30-calendar-day comparison proxy; no new tuning.
        double vixChange = growth(vix, before(series, "VIXCLS", decision.minusDays(30)));
        double dollarChange = growth(dollar, before(series, "DTWEXBGS", decision.minusDays(30)));
        int tightness = StockMacroFeatureService.macroTightnessScore(shortRate, longRate, spread, fedFunds);
        int growthStress = StockMacroFeatureService.macroGrowthStressScore(cpiYoy, cpiMom, unemployment, unemploymentChange);
        int risk = StockMacroFeatureService.macroRiskScore(vix, vixChange, dollarChange, spread);

        var result = new StockMacroFeatureSnapshot();
        result.setIndexCode(index); result.setSnapshotDate(decision);
        result.setShortRatePct(decimal(shortRate)); result.setLongRatePct(decimal(longRate));
        result.setYieldSpreadPct(decimal(spread)); result.setFedFundsPct(decimal(fedFunds));
        result.setCpiYoyPct(decimal(cpiYoy)); result.setCpiMomPct(decimal(cpiMom));
        result.setUnemploymentRatePct(decimal(unemployment)); result.setUnemploymentChange3mPct(decimal(unemploymentChange));
        result.setVixLevel(decimal(vix)); result.setVixChange20dPct(decimal(vixChange));
        result.setDollarIndex(decimal(dollar)); result.setDollarChange20dPct(decimal(dollarChange));
        result.setMacroTightnessScore(tightness); result.setMacroGrowthStressScore(growthStress); result.setMacroRiskScore(risk);
        result.setMacroFeatureScore(Math.max(0, Math.min(100, Math.round((float) (tightness * .35 + growthStress * .30 + risk * .35)))));
        result.setSource("ALFRED_ASOF_RESEARCH_PREVDAY");
        return result;
    }
    private static double latest(Map<String, NavigableMap<LocalDate, Double>> series, String code) {
        return series.get(code).lastEntry().getValue();
    }
    private static double before(Map<String, NavigableMap<LocalDate, Double>> series, String code, LocalDate date) {
        var entry = series.get(code).floorEntry(date);
        return entry == null ? Double.NaN : entry.getValue();
    }
    private static double growth(double value, double prior) {
        return !Double.isFinite(prior) || prior == 0 ? Double.NaN : (value / prior - 1) * 100;
    }
    private static BigDecimal decimal(double value) { return Double.isFinite(value) ? BigDecimal.valueOf(value) : null; }
}
