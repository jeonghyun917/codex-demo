package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockBacktestService {

    private static final List<Integer> HORIZONS = List.of(5, 20, 60);
    private static final List<Integer> PROMOTION_HORIZONS = List.of(20, 60);
    private static final int VIEW_RESULT_LIMIT = 100_000;

    private final ObjectProvider<StockBacktestMapper> stockBacktestMapper;
    private final ObjectProvider<StockSignalWeightProfileMapper> weightProfileMapper;

    public StockBacktestService(ObjectProvider<StockBacktestMapper> stockBacktestMapper,
            ObjectProvider<StockSignalWeightProfileMapper> weightProfileMapper) {
        this.stockBacktestMapper = stockBacktestMapper;
        this.weightProfileMapper = weightProfileMapper;
    }

    public StockBacktestView build(String indexCode) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return empty(indexCode);
        }

        refreshCompletedResults(5_000);

        List<StockBacktestCoverage> coverageRows = mapper.findCoverage(indexCode);
        StockBacktestView.CoverageSummary coverage = coverageSummary(coverageRows);
        StockBacktestView.ResultSummary result = resultSummary(mapper, indexCode);
        List<StockBacktestResult> results = mapper.findResults(indexCode, VIEW_RESULT_LIMIT);
        List<FactorMetric> factorMetrics = factorMetrics(results);
        List<YearStabilityMetric> yearStability = yearStabilityMetrics(results);
        List<WeightProfileDefinition> weightProfiles = weightProfiles(results, factorMetrics, yearStability);

        return new StockBacktestView(
                indexCode,
                coverage,
                result,
                cards(coverage, result, results),
                weakCoverageRows(coverageRows),
                scoreBuckets(results),
                deciles(results),
                factors(results),
                factorDiagnostics(factorMetrics),
                sectorNeutralDiagnostics(results),
                yearStabilityDiagnostics(yearStability),
                weightCandidates(factorMetrics, yearStability),
                promotionGate(results, weightProfiles),
                profileDiagnostics(factorMetrics, yearStability, weightProfiles),
                factorCorrelations(results),
                profileComparisons(results, weightProfiles),
                walkForwardProfiles(results),
                weightProfileItems(weightProfiles),
                sectors(results));
    }

    public int refreshCompletedResults(int limit) {
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return 0;
        }

        int created = 0;
        for (StockSignalSnapshot snapshot : mapper.findSnapshotsNeedingResults(limit)) {
            if (snapshot.getId() == null || snapshot.getSymbol() == null || snapshot.getSignalDate() == null) {
                continue;
            }
            for (int horizon : HORIZONS) {
                List<StockCandleDaily> futureCandles = mapper.findFutureCandles(
                        snapshot.getSymbol(), snapshot.getSignalDate(), horizon);
                if (futureCandles.size() < horizon) {
                    continue;
                }
                StockCandleDaily entry = futureCandles.getFirst();
                StockCandleDaily exit = futureCandles.get(horizon - 1);
                if (!hasValidClose(entry) || !hasValidClose(exit)) {
                    continue;
                }
                mapper.upsertResult(toBacktestResult(snapshot, horizon, entry, exit,
                        mapper.findSectorForSymbol(snapshot.getSymbol())));
                created++;
            }
        }
        return created;
    }

    private static StockBacktestResult toBacktestResult(StockSignalSnapshot snapshot, int horizon,
            StockCandleDaily entry, StockCandleDaily exit, String sector) {
        BigDecimal forwardReturn = exit.getClosePrice()
                .subtract(entry.getClosePrice())
                .multiply(BigDecimal.valueOf(100))
                .divide(entry.getClosePrice(), 6, RoundingMode.HALF_UP);

        StockBacktestResult result = new StockBacktestResult();
        result.setSnapshotId(snapshot.getId());
        result.setSymbol(snapshot.getSymbol());
        result.setSignalDate(snapshot.getSignalDate());
        result.setHorizonDays(horizon);
        result.setEntryTradeDate(entry.getTradeDate());
        result.setEntryClose(entry.getClosePrice());
        result.setExitTradeDate(exit.getTradeDate());
        result.setExitClose(exit.getClosePrice());
        result.setForwardReturnPct(forwardReturn);
        result.setIntegratedScore(snapshot.getIntegratedScore());
        result.setValuationScore(snapshot.getValuationScore());
        result.setQualityScore(snapshot.getQualityScore());
        result.setGrowthScore(snapshot.getGrowthScore());
        result.setStabilityScore(snapshot.getStabilityScore());
        result.setEarningsScore(snapshot.getEarningsScore());
        result.setAnalystScore(snapshot.getAnalystScore());
        result.setNewsScore(snapshot.getNewsScore());
        result.setMomentumScore(snapshot.getMomentumScore());
        result.setRiskScore(snapshot.getRiskScore());
        result.setInstitutionScore(snapshot.getInstitutionScore());
        result.setDataQualityScore(snapshot.getDataQualityScore());
        result.setSector(sector == null || sector.isBlank() ? "-" : sector);
        return result;
    }

    private static boolean hasValidClose(StockCandleDaily candle) {
        return candle != null && candle.getClosePrice() != null && candle.getClosePrice().signum() > 0;
    }

    private static StockBacktestView.CoverageSummary coverageSummary(List<StockBacktestCoverage> rows) {
        int total = rows.size();
        long withCandles = rows.stream().filter(row -> candleCount(row) > 0).count();
        long missing = total - withCandles;
        long enough5 = rows.stream().filter(row -> candleCount(row) >= 6).count();
        long enough20 = rows.stream().filter(row -> candleCount(row) >= 21).count();
        long enough60 = rows.stream().filter(row -> candleCount(row) >= 61).count();
        double averageYears = rows.stream()
                .mapToDouble(StockBacktestService::coverageYears)
                .filter(value -> value > 0)
                .average()
                .orElse(0);
        double maxYears = rows.stream()
                .mapToDouble(StockBacktestService::coverageYears)
                .max()
                .orElse(0);

        return new StockBacktestView.CoverageSummary(
                integer(total),
                integer(withCandles),
                integer(missing),
                integer(enough5),
                integer(enough20),
                integer(enough60),
                years(averageYears),
                years(maxYears));
    }

    private static StockBacktestView.ResultSummary resultSummary(StockBacktestMapper mapper, String indexCode) {
        int resultCount = mapper.countResults(indexCode);
        return new StockBacktestView.ResultSummary(
                integer(mapper.countSnapshots(indexCode)),
                integer(resultCount),
                integer(mapper.countResultsByHorizon(indexCode, 5)),
                integer(mapper.countResultsByHorizon(indexCode, 20)),
                integer(mapper.countResultsByHorizon(indexCode, 60)),
                resultCount > 0);
    }

    private static List<StockBacktestView.MetricCard> cards(StockBacktestView.CoverageSummary coverage,
            StockBacktestView.ResultSummary result, List<StockBacktestResult> results) {
        Double hitRate20d = hitRate(byHorizon(results, 20));
        Double ic20d = informationCoefficient(byHorizon(results, 20));
        return List.of(
                new StockBacktestView.MetricCard("Candle coverage", coverage.withCandles() + " / "
                        + coverage.totalSymbols(), coverage.missingCandles() + " missing", "neutral"),
                new StockBacktestView.MetricCard("60D testable", coverage.enough60d(),
                        "Needs at least 61 daily candles", "neutral"),
                new StockBacktestView.MetricCard("Signal snapshots", result.snapshotCount(),
                        "Stored signal-date rows", "neutral"),
                new StockBacktestView.MetricCard("Backtest results", result.resultCount(),
                        "5D/20D/60D completed rows", result.hasResults() ? "positive" : "caution"),
                new StockBacktestView.MetricCard("20D hit rate", hitRate20d == null ? "-" : percent(hitRate20d),
                        "forward return > 0", toneNullable(hitRate20d, 50.0d)),
                new StockBacktestView.MetricCard("20D IC", ic20d == null ? "-" : decimal(ic20d),
                        "score vs forward return", toneNullable(ic20d, 0.0d)));
    }

    private static List<StockBacktestView.CoverageRow> weakCoverageRows(List<StockBacktestCoverage> rows) {
        LocalDate today = LocalDate.now();
        return rows.stream()
                .filter(row -> candleCount(row) < 61 || staleDays(row, today) > 14)
                .limit(80)
                .map(row -> new StockBacktestView.CoverageRow(
                        fallback(row.getSymbol()),
                        fallback(row.getName()),
                        fallback(row.getSector()),
                        integer(candleCount(row)),
                        date(row.getFirstTradeDate()),
                        date(row.getLastTradeDate()),
                        years(coverageYears(row)),
                        coverageStatus(row, today)))
                .toList();
    }

    private static List<StockBacktestView.BucketRow> scoreBuckets(List<StockBacktestResult> results) {
        List<Bucket> buckets = List.of(
                new Bucket("0-39", result -> score(result) <= 39),
                new Bucket("40-54", result -> score(result) >= 40 && score(result) <= 54),
                new Bucket("55-69", result -> score(result) >= 55 && score(result) <= 69),
                new Bucket("70-100", result -> score(result) >= 70));
        List<StockBacktestView.BucketRow> rows = new ArrayList<>();
        for (int horizon : HORIZONS) {
            List<StockBacktestResult> horizonResults = byHorizon(results, horizon);
            for (Bucket bucket : buckets) {
                List<StockBacktestResult> members = horizonResults.stream()
                        .filter(bucket.predicate())
                        .toList();
                if (!members.isEmpty()) {
                    double average = averageReturn(members);
                    rows.add(new StockBacktestView.BucketRow(
                            horizonLabel(horizon),
                            bucket.label(),
                            integer(members.size()),
                            percent(average),
                            tone(average)));
                }
            }
        }
        return rows;
    }

    private static List<StockBacktestView.DecileRow> deciles(List<StockBacktestResult> results) {
        List<StockBacktestView.DecileRow> rows = new ArrayList<>();
        for (int horizon : HORIZONS) {
            List<StockBacktestResult> horizonResults = byHorizon(results, horizon).stream()
                    .filter(result -> result.getIntegratedScore() != null)
                    .sorted(Comparator.comparing(StockBacktestResult::getIntegratedScore).reversed())
                    .toList();
            if (horizonResults.size() < 10) {
                continue;
            }
            int decileSize = Math.max(1, (int) Math.ceil(horizonResults.size() * 0.1));
            List<StockBacktestResult> top = horizonResults.subList(0, decileSize);
            List<StockBacktestResult> bottom = horizonResults.subList(horizonResults.size() - decileSize,
                    horizonResults.size());
            double topAverage = averageReturn(top);
            double bottomAverage = averageReturn(bottom);
            double spread = topAverage - bottomAverage;
            rows.add(new StockBacktestView.DecileRow(horizonLabel(horizon), "Top 10%", integer(top.size()),
                    percent(topAverage), percent(spread), tone(spread)));
            rows.add(new StockBacktestView.DecileRow(horizonLabel(horizon), "Bottom 10%", integer(bottom.size()),
                    percent(bottomAverage), percent(spread), tone(spread)));
        }
        return rows;
    }

    private static List<StockBacktestView.FactorRow> factors(List<StockBacktestResult> results) {
        List<StockBacktestView.FactorRow> rows = new ArrayList<>();
        for (int horizon : HORIZONS) {
            List<StockBacktestResult> horizonResults = byHorizon(results, horizon);
            for (Factor factor : factorDefinitions()) {
                List<StockBacktestResult> high = horizonResults.stream()
                        .filter(result -> factor.score(result) != null && factor.score(result) >= 70)
                        .toList();
                List<StockBacktestResult> low = horizonResults.stream()
                        .filter(result -> factor.score(result) != null && factor.score(result) <= 40)
                        .toList();
                if (high.isEmpty() && low.isEmpty()) {
                    continue;
                }
                double highAverage = averageReturn(high);
                double lowAverage = averageReturn(low);
                Double spread = high.isEmpty() || low.isEmpty() ? null : highAverage - lowAverage;
                rows.add(new StockBacktestView.FactorRow(
                        horizonLabel(horizon),
                        factor.label(),
                        integer(high.size()),
                        high.isEmpty() ? "-" : percent(highAverage),
                        integer(low.size()),
                        low.isEmpty() ? "-" : percent(lowAverage),
                        spread == null ? "-" : percent(spread),
                        spread == null ? "neutral" : tone(spread)));
            }
        }
        return rows;
    }

    private static List<StockBacktestView.FactorDiagnosticRow> factorDiagnostics(List<FactorMetric> metrics) {
        return metrics.stream()
                .map(metric -> new StockBacktestView.FactorDiagnosticRow(
                        horizonLabel(metric.horizon()),
                        metric.factor(),
                        integer(metric.sampleCount()),
                        percent(metric.averageReturn()),
                        metric.hitRate() == null ? "-" : percent(metric.hitRate()),
                        metric.ic() == null ? "-" : decimal(metric.ic()),
                        integer(metric.highCount()),
                        metric.highCount() == 0 ? "-" : percent(metric.highReturn()),
                        integer(metric.lowCount()),
                        metric.lowCount() == 0 ? "-" : percent(metric.lowReturn()),
                        metric.spread() == null ? "-" : percent(metric.spread()),
                        diagnosticTone(metric.spread(), metric.ic())))
                .toList();
    }

    private static List<FactorMetric> factorMetrics(List<StockBacktestResult> results) {
        List<FactorMetric> rows = new ArrayList<>();
        for (int horizon : HORIZONS) {
            List<StockBacktestResult> horizonResults = byHorizon(results, horizon);
            for (Factor factor : factorDefinitions()) {
                List<StockBacktestResult> sample = horizonResults.stream()
                        .filter(result -> factor.score(result) != null)
                        .filter(result -> result.getForwardReturnPct() != null)
                        .toList();
                if (sample.isEmpty()) {
                    continue;
                }
                List<StockBacktestResult> high = sample.stream()
                        .filter(result -> factor.score(result) >= 70)
                        .toList();
                List<StockBacktestResult> low = sample.stream()
                        .filter(result -> factor.score(result) <= 40)
                        .toList();
                double highAverage = averageReturn(high);
                double lowAverage = averageReturn(low);
                Double spread = high.isEmpty() || low.isEmpty() ? null : highAverage - lowAverage;
                rows.add(new FactorMetric(
                        horizon,
                        factor.label(),
                        sample.size(),
                        averageReturn(sample),
                        hitRate(sample),
                        informationCoefficient(sample, factor::score),
                        high.size(),
                        highAverage,
                        low.size(),
                        lowAverage,
                        spread));
            }
        }
        return rows;
    }

    private static List<StockBacktestView.SectorNeutralRow> sectorNeutralDiagnostics(
            List<StockBacktestResult> results) {
        List<StockBacktestView.SectorNeutralRow> rows = new ArrayList<>();
        for (int horizon : HORIZONS) {
            List<StockBacktestResult> horizonResults = byHorizon(results, horizon);
            for (Factor factor : factorDefinitions()) {
                List<SectorSpread> spreads = horizonResults.stream()
                        .filter(result -> factor.score(result) != null)
                        .filter(result -> result.getForwardReturnPct() != null)
                        .collect(Collectors.groupingBy(
                                result -> fallback(result.getSector()),
                                LinkedHashMap::new,
                                Collectors.toList()))
                        .entrySet()
                        .stream()
                        .map(entry -> sectorSpread(entry.getKey(), entry.getValue(), factor))
                        .filter(Objects::nonNull)
                        .toList();
                if (spreads.isEmpty()) {
                    continue;
                }
                double averageSpread = spreads.stream()
                        .mapToDouble(SectorSpread::spread)
                        .average()
                        .orElse(0);
                long positive = spreads.stream().filter(spread -> spread.spread() > 0).count();
                long negative = spreads.stream().filter(spread -> spread.spread() < 0).count();
                SectorSpread best = spreads.stream()
                        .max(Comparator.comparingDouble(SectorSpread::spread))
                        .orElse(null);
                SectorSpread worst = spreads.stream()
                        .min(Comparator.comparingDouble(SectorSpread::spread))
                        .orElse(null);
                rows.add(new StockBacktestView.SectorNeutralRow(
                        horizonLabel(horizon),
                        factor.label(),
                        integer(spreads.size()),
                        integer(positive),
                        integer(negative),
                        percent(averageSpread),
                        best == null ? "-" : best.sector() + " " + percent(best.spread()),
                        worst == null ? "-" : worst.sector() + " " + percent(worst.spread()),
                        tone(averageSpread)));
            }
        }
        return rows;
    }

    private static SectorSpread sectorSpread(String sector, List<StockBacktestResult> results, Factor factor) {
        List<StockBacktestResult> high = results.stream()
                .filter(result -> factor.score(result) >= 70)
                .toList();
        List<StockBacktestResult> low = results.stream()
                .filter(result -> factor.score(result) <= 40)
                .toList();
        if (high.size() < 5 || low.size() < 5) {
            return null;
        }
        return new SectorSpread(sector, high.size(), low.size(), averageReturn(high) - averageReturn(low));
    }

    private static List<YearStabilityMetric> yearStabilityMetrics(List<StockBacktestResult> results) {
        List<YearStabilityMetric> rows = new ArrayList<>();
        for (int horizon : HORIZONS) {
            List<StockBacktestResult> horizonResults = byHorizon(results, horizon);
            for (Factor factor : factorDefinitions()) {
                List<Double> spreads = horizonResults.stream()
                        .filter(result -> result.getSignalDate() != null)
                        .filter(result -> factor.score(result) != null)
                        .filter(result -> result.getForwardReturnPct() != null)
                        .collect(Collectors.groupingBy(
                                result -> result.getSignalDate().getYear(),
                                LinkedHashMap::new,
                                Collectors.toList()))
                        .values()
                        .stream()
                        .map(yearResults -> yearSpread(yearResults, factor))
                        .filter(Objects::nonNull)
                        .toList();
                if (spreads.isEmpty()) {
                    continue;
                }
                double average = spreads.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double min = spreads.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                double max = spreads.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                long positiveYears = spreads.stream().filter(spread -> spread > 0).count();
                rows.add(new YearStabilityMetric(horizon, factor.label(), spreads.size(), positiveYears,
                        average, min, max));
            }
        }
        return rows;
    }

    private static Double yearSpread(List<StockBacktestResult> results, Factor factor) {
        List<StockBacktestResult> high = results.stream()
                .filter(result -> factor.score(result) >= 70)
                .toList();
        List<StockBacktestResult> low = results.stream()
                .filter(result -> factor.score(result) <= 40)
                .toList();
        if (high.size() < 10 || low.size() < 10) {
            return null;
        }
        return averageReturn(high) - averageReturn(low);
    }

    private static List<StockBacktestView.YearStabilityRow> yearStabilityDiagnostics(
            List<YearStabilityMetric> metrics) {
        return metrics.stream()
                .map(metric -> new StockBacktestView.YearStabilityRow(
                        horizonLabel(metric.horizon()),
                        metric.factor(),
                        integer(metric.yearCount()),
                        integer(metric.positiveYears()),
                        percent(metric.averageSpread()),
                        percent(metric.minSpread()),
                        percent(metric.maxSpread()),
                        stabilityTone(metric)))
                .toList();
    }

    private static List<StockBacktestView.WeightCandidateRow> weightCandidates(
            List<FactorMetric> factorMetrics,
            List<YearStabilityMetric> yearMetrics) {
        List<StockBacktestView.WeightCandidateRow> rows = new ArrayList<>();
        for (Factor factor : factorDefinitions()) {
            FactorMetric metric20d = metric(factorMetrics, factor.label(), 20);
            FactorMetric metric60d = metric(factorMetrics, factor.label(), 60);
            YearStabilityMetric stable20d = yearMetric(yearMetrics, factor.label(), 20);
            if (metric20d == null) {
                continue;
            }
            double spread20d = metric20d.spread() == null ? 0 : metric20d.spread();
            double spread60d = metric60d == null || metric60d.spread() == null ? 0 : metric60d.spread();
            double ic20d = metric20d.ic() == null ? 0 : metric20d.ic();
            double yearRatio = stable20d == null || stable20d.yearCount() == 0
                    ? 0.5d
                    : stable20d.positiveYears() * 1.0d / stable20d.yearCount();
            int confidence = candidateConfidence(spread20d, spread60d, ic20d, yearRatio);
            CandidateAction action = candidateActionV2(spread20d, spread60d, ic20d, yearRatio, confidence);
            String evidence = "20D spread " + percent(spread20d)
                    + ", 20D IC " + decimal(ic20d)
                    + ", 60D spread " + percent(spread60d)
                    + ", stable years " + percent(yearRatio * 100.0d);
            rows.add(new StockBacktestView.WeightCandidateRow(
                    factor.label(),
                    action.label(),
                    integer(confidence) + " / 100",
                    evidence,
                    action.tone()));
        }
        return rows;
    }

    private static List<StockBacktestView.ProfileDiagnosticRow> profileDiagnostics(
            List<FactorMetric> factorMetrics,
            List<YearStabilityMetric> yearMetrics,
            List<WeightProfileDefinition> profiles) {
        WeightProfileDefinition defaultProfile = profiles.stream()
                .filter(profile -> "DEFAULT".equals(profile.code()))
                .findFirst()
                .orElseGet(StockBacktestService::defaultWeightProfile);
        List<WeightProfileDefinition> candidateProfiles = profiles.stream()
                .filter(profile -> !"DEFAULT".equals(profile.code()))
                .toList();

        List<StockBacktestView.ProfileDiagnosticRow> rows = new ArrayList<>();
        for (WeightProfileDefinition candidateProfile : candidateProfiles) {
            for (Factor factor : factorDefinitions()) {
                String label = factor.label();
                FactorMetric metric20d = metric(factorMetrics, label, 20);
                FactorMetric metric60d = metric(factorMetrics, label, 60);
                YearStabilityMetric stable20d = yearMetric(yearMetrics, label, 20);
                double spread20d = metric20d == null || metric20d.spread() == null ? 0 : metric20d.spread();
                double spread60d = metric60d == null || metric60d.spread() == null ? 0 : metric60d.spread();
                double ic20d = metric20d == null || metric20d.ic() == null ? 0 : metric20d.ic();
                double stableRatio = stable20d == null || stable20d.yearCount() == 0
                        ? 0.5d
                        : stable20d.positiveYears() * 1.0d / stable20d.yearCount();
                double defaultWeight = defaultProfile.weight(label);
                double candidateWeight = candidateProfile.weight(label);
                ProfileDiagnostic diagnostic = profileDiagnostic(
                        candidateWeight - defaultWeight,
                        spread20d,
                        spread60d,
                        ic20d,
                        stableRatio);
                rows.add(new StockBacktestView.ProfileDiagnosticRow(
                        candidateProfile.code(),
                        label,
                        weightPercent(defaultWeight),
                        weightPercent(candidateWeight),
                        signedWeightDelta(candidateWeight - defaultWeight),
                        percent(spread20d),
                        percent(spread60d),
                        decimal(ic20d),
                        percent(stableRatio * 100.0d),
                        diagnostic.label(),
                        diagnostic.tone()));
            }
        }
        return rows;
    }

    private static ProfileDiagnostic profileDiagnostic(double weightDelta, double spread20d, double spread60d,
            double ic20d, double stableRatio) {
        if (weightDelta > 2.0d && (spread60d < 0 || stableRatio < 0.5d || ic20d < 0)) {
            return new ProfileDiagnostic("상향 과다 검토", "negative");
        }
        if (weightDelta < -2.0d && (spread60d > 0.5d || stableRatio >= 0.67d)) {
            return new ProfileDiagnostic("하향 재검토", "caution");
        }
        if (Math.abs(weightDelta) > 6.0d) {
            return new ProfileDiagnostic("변경폭 큼", "caution");
        }
        if (weightDelta > 0.4d) {
            return new ProfileDiagnostic("소폭 상향", "positive");
        }
        if (weightDelta < -0.4d) {
            return new ProfileDiagnostic("소폭 하향", "neutral");
        }
        return new ProfileDiagnostic("유지", "neutral");
    }
    private static List<StockBacktestView.FactorCorrelationRow> factorCorrelations(
            List<StockBacktestResult> results) {
        List<StockBacktestResult> sample = byHorizon(results, 20);
        if (sample.size() < 100) {
            sample = results;
        }
        List<Factor> factors = factorDefinitions();
        List<FactorCorrelation> rows = new ArrayList<>();
        for (int leftIndex = 0; leftIndex < factors.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < factors.size(); rightIndex++) {
                FactorCorrelation correlation = factorCorrelation(sample, factors.get(leftIndex), factors.get(rightIndex));
                if (correlation.sampleCount() >= 100 && Math.abs(correlation.correlation()) >= 0.45d) {
                    rows.add(correlation);
                }
            }
        }
        return rows.stream()
                .sorted(Comparator.comparing((FactorCorrelation row) -> Math.abs(row.correlation())).reversed())
                .limit(14)
                .map(row -> {
                    CorrelationRisk risk = correlationRisk(row.correlation());
                    return new StockBacktestView.FactorCorrelationRow(
                            row.leftFactor(),
                            row.rightFactor(),
                            integer(row.sampleCount()),
                            decimal(row.correlation()),
                            risk.label(),
                            risk.note(),
                            risk.tone());
                })
                .toList();
    }

    private static FactorCorrelation factorCorrelation(List<StockBacktestResult> results, Factor left, Factor right) {
        List<FactorPairScore> pairs = results.stream()
                .map(result -> new FactorPairScore(left.score(result), right.score(result)))
                .filter(pair -> pair.leftScore() != null && pair.rightScore() != null)
                .toList();
        if (pairs.size() < 10) {
            return new FactorCorrelation(left.label(), right.label(), pairs.size(), 0.0d);
        }
        double leftAverage = pairs.stream().mapToDouble(FactorPairScore::leftScore).average().orElse(0);
        double rightAverage = pairs.stream().mapToDouble(FactorPairScore::rightScore).average().orElse(0);
        double covariance = 0;
        double leftVariance = 0;
        double rightVariance = 0;
        for (FactorPairScore pair : pairs) {
            double leftDelta = pair.leftScore() - leftAverage;
            double rightDelta = pair.rightScore() - rightAverage;
            covariance += leftDelta * rightDelta;
            leftVariance += leftDelta * leftDelta;
            rightVariance += rightDelta * rightDelta;
        }
        double correlation = leftVariance == 0 || rightVariance == 0
                ? 0.0d
                : covariance / Math.sqrt(leftVariance * rightVariance);
        return new FactorCorrelation(left.label(), right.label(), pairs.size(), correlation);
    }

    private static CorrelationRisk correlationRisk(double correlation) {
        if (correlation >= 0.80d) {
            return new CorrelationRisk("중복 높음", "동시에 상향하면 같은 스타일 베팅이 과중될 수 있습니다.", "negative");
        }
        if (correlation >= 0.60d) {
            return new CorrelationRisk("중복 가능", "가중치를 함께 올리기 전 중복 노출을 확인해야 합니다.", "caution");
        }
        if (correlation <= -0.60d) {
            return new CorrelationRisk("충돌 가능", "한쪽 factor 강화가 다른 factor 신호를 상쇄할 수 있습니다.", "caution");
        }
        return new CorrelationRisk("관찰", "현재는 직접 조정 대상보다 모니터링 대상입니다.", "neutral");
    }
    private static FactorMetric metric(List<FactorMetric> metrics, String factor, int horizon) {
        return metrics.stream()
                .filter(metric -> metric.horizon() == horizon)
                .filter(metric -> Objects.equals(metric.factor(), factor))
                .findFirst()
                .orElse(null);
    }

    private static YearStabilityMetric yearMetric(List<YearStabilityMetric> metrics, String factor, int horizon) {
        return metrics.stream()
                .filter(metric -> metric.horizon() == horizon)
                .filter(metric -> Objects.equals(metric.factor(), factor))
                .findFirst()
                .orElse(null);
    }

    private List<WeightProfileDefinition> weightProfiles(List<StockBacktestResult> results, List<FactorMetric> factorMetrics,
            List<YearStabilityMetric> yearMetrics) {
        WeightProfileDefinition defaultProfile = defaultWeightProfile();
        WeightProfileDefinition backtestProfile = backtestWeightProfile(factorMetrics, yearMetrics);
        WeightProfileDefinition orthogonalProfile = backtestV2WeightProfile(results, factorMetrics, yearMetrics);
        StockSignalWeightProfileMapper mapper = weightProfileMapper.getIfAvailable();
        if (mapper == null) {
            return List.of(defaultProfile, backtestProfile, orthogonalProfile);
        }
        try {
            persistWeightProfile(mapper, defaultProfile);
            persistWeightProfile(mapper, backtestProfile);
            persistWeightProfile(mapper, orthogonalProfile);
            List<WeightProfileDefinition> loaded = loadWeightProfiles(mapper, List.of(
                    defaultProfile, backtestProfile, orthogonalProfile));
            return loaded.isEmpty() ? List.of(defaultProfile, backtestProfile, orthogonalProfile) : loaded;
        } catch (RuntimeException ex) {
            return List.of(defaultProfile, backtestProfile, orthogonalProfile);
        }
    }

    private static WeightProfileDefinition defaultWeightProfile() {
        return new WeightProfileDefinition(
                "DEFAULT",
                "Default Quant Signal",
                "Current production reference profile. Profile comparison uses stored integrated Signal values.",
                true,
                "MANUAL_BASELINE",
                defaultWeights(),
                defaultReasons());
    }

    private static WeightProfileDefinition backtestWeightProfile(List<FactorMetric> factorMetrics,
            List<YearStabilityMetric> yearMetrics) {
        Map<String, Double> rawWeights = new LinkedHashMap<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        Map<String, Double> defaults = defaultWeights();
        for (Factor factor : factorDefinitions()) {
            String label = factor.label();
            FactorMetric metric20d = metric(factorMetrics, label, 20);
            FactorMetric metric60d = metric(factorMetrics, label, 60);
            YearStabilityMetric stable20d = yearMetric(yearMetrics, label, 20);
            double spread20d = metric20d == null || metric20d.spread() == null ? 0 : metric20d.spread();
            double spread60d = metric60d == null || metric60d.spread() == null ? 0 : metric60d.spread();
            double ic20d = metric20d == null || metric20d.ic() == null ? 0 : metric20d.ic();
            double yearRatio = stable20d == null || stable20d.yearCount() == 0
                    ? 0.5d
                    : stable20d.positiveYears() * 1.0d / stable20d.yearCount();
            int confidence = candidateConfidence(spread20d, spread60d, ic20d, yearRatio);
            CandidateAction action = candidateActionV2(spread20d, spread60d, ic20d, yearRatio, confidence);
            double multiplier = switch (action.tone()) {
                case "positive" -> 1.12d;
                case "negative" -> 0.82d;
                default -> 1.0d;
            };
            if (spread20d > 1.20d && spread60d > 1.00d && ic20d > 0.03d && yearRatio >= 0.67d) {
                multiplier += 0.04d;
            }
            if (spread20d < -1.20d && ic20d < -0.05d && yearRatio <= 0.34d) {
                multiplier -= 0.04d;
            }
            double defaultWeight = defaults.getOrDefault(label, 0.0d);
            rawWeights.put(label, Math.max(1.0d, defaultWeight * clamp(multiplier, 0.78d, 1.18d)));
            reasons.put(label, action.label() + " | 20D spread " + percent(spread20d)
                    + ", 20D IC " + decimal(ic20d)
                    + ", 60D spread " + percent(spread60d)
                    + ", stable years " + percent(yearRatio * 100.0d));
        }
        return new WeightProfileDefinition(
                "BACKTEST_V1",
                "Backtest v1 Candidate",
                "Diagnostic candidate generated from Factor Performance Diagnostics. Not applied to live Signal.",
                false,
                "BACKTEST_DIAGNOSTIC",
                normalizeWeights(rawWeights),
                reasons);
    }

    private static WeightProfileDefinition backtestV2WeightProfile(List<StockBacktestResult> results,
            List<FactorMetric> factorMetrics,
            List<YearStabilityMetric> yearMetrics) {
        WeightProfileDefinition base = backtestWeightProfile(factorMetrics, yearMetrics);
        Map<String, Double> weights = new LinkedHashMap<>(base.weights());
        Map<String, String> reasons = new LinkedHashMap<>(base.reasons());

        applySectorBreadthPenalty(results, factorMetrics, weights, reasons);
        applyCorrelationPenalty(results, factorMetrics, yearMetrics, weights, reasons);

        return new WeightProfileDefinition(
                "BACKTEST_V2",
                "Backtest v2 Orthogonal Candidate",
                "Sector-neutral scoring candidate with factor correlation penalties. Not applied to live Signal.",
                false,
                "BACKTEST_ORTHOGONAL",
                normalizeWeights(weights),
                reasons);
    }

    private static void applySectorBreadthPenalty(List<StockBacktestResult> results,
            List<FactorMetric> factorMetrics,
            Map<String, Double> weights,
            Map<String, String> reasons) {
        List<StockBacktestResult> sample = byHorizon(results, 20).stream()
                .filter(result -> result.getForwardReturnPct() != null)
                .toList();
        if (sample.size() < 200) {
            return;
        }
        for (Factor factor : factorDefinitions()) {
            List<SectorSpread> spreads = sample.stream()
                    .filter(result -> factor.score(result) != null)
                    .collect(Collectors.groupingBy(result -> fallback(result.getSector()), LinkedHashMap::new,
                            Collectors.toList()))
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().size() >= 20)
                    .map(entry -> sectorSpread(entry.getKey(), entry.getValue(), factor))
                    .filter(Objects::nonNull)
                    .filter(spread -> spread.highCount() >= 3 && spread.lowCount() >= 3)
                    .toList();
            if (spreads.size() < 4) {
                continue;
            }
            long positiveSectors = spreads.stream()
                    .filter(spread -> spread.spread() > 0)
                    .count();
            double positiveRatio = positiveSectors * 1.0d / spreads.size();
            double averageSpread = spreads.stream().mapToDouble(SectorSpread::spread).average().orElse(0.0d);
            String label = factor.label();
            double current = weights.getOrDefault(label, 0.0d);
            if (positiveRatio < 0.45d || averageSpread < -0.20d) {
                weights.put(label, Math.max(1.0d, current * 0.90d));
                appendReason(reasons, label, "sector breadth penalty "
                        + integer(positiveSectors) + "/" + integer(spreads.size())
                        + ", avg spread " + percent(averageSpread));
            } else if (positiveRatio >= 0.67d && averageSpread > 0.20d) {
                weights.put(label, current * 1.03d);
                appendReason(reasons, label, "sector breadth support "
                        + integer(positiveSectors) + "/" + integer(spreads.size())
                        + ", avg spread " + percent(averageSpread));
            }
        }
    }

    private static void applyCorrelationPenalty(List<StockBacktestResult> results,
            List<FactorMetric> factorMetrics,
            List<YearStabilityMetric> yearMetrics,
            Map<String, Double> weights,
            Map<String, String> reasons) {
        List<StockBacktestResult> sample = byHorizon(results, 20);
        if (sample.size() < 200) {
            sample = results;
        }
        Map<String, Double> evidence = factorEvidenceScores(factorMetrics, yearMetrics);
        List<Factor> factors = factorDefinitions();
        for (int leftIndex = 0; leftIndex < factors.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < factors.size(); rightIndex++) {
                FactorCorrelation correlation = factorCorrelation(sample, factors.get(leftIndex), factors.get(rightIndex));
                if (correlation.sampleCount() < 100 || Math.abs(correlation.correlation()) < 0.60d) {
                    continue;
                }
                String left = correlation.leftFactor();
                String right = correlation.rightFactor();
                String weaker = evidence.getOrDefault(left, 0.0d) <= evidence.getOrDefault(right, 0.0d)
                        ? left
                        : right;
                double current = weights.getOrDefault(weaker, 0.0d);
                if (current <= 0) {
                    continue;
                }
                double penalty = correlation.correlation() >= 0.80d ? 0.84d
                        : correlation.correlation() >= 0.60d ? 0.92d
                        : 0.95d;
                weights.put(weaker, Math.max(1.0d, current * penalty));
                appendReason(reasons, weaker, "correlation penalty vs "
                        + (Objects.equals(weaker, left) ? right : left)
                        + " r=" + decimal(correlation.correlation()));
            }
        }
    }

    private static Map<String, Double> factorEvidenceScores(List<FactorMetric> factorMetrics,
            List<YearStabilityMetric> yearMetrics) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Factor factor : factorDefinitions()) {
            String label = factor.label();
            FactorMetric metric20d = metric(factorMetrics, label, 20);
            FactorMetric metric60d = metric(factorMetrics, label, 60);
            YearStabilityMetric stable20d = yearMetric(yearMetrics, label, 20);
            double spread20d = metric20d == null || metric20d.spread() == null ? 0 : metric20d.spread();
            double spread60d = metric60d == null || metric60d.spread() == null ? 0 : metric60d.spread();
            double ic20d = metric20d == null || metric20d.ic() == null ? 0 : metric20d.ic();
            double yearRatio = stable20d == null || stable20d.yearCount() == 0
                    ? 0.5d
                    : stable20d.positiveYears() * 1.0d / stable20d.yearCount();
            scores.put(label, (double) candidateConfidence(spread20d, spread60d, ic20d, yearRatio));
        }
        return scores;
    }

    private static void appendReason(Map<String, String> reasons, String factor, String addition) {
        String current = reasons.getOrDefault(factor, "-");
        reasons.put(factor, current == null || current.isBlank() || "-".equals(current)
                ? addition
                : current + " | " + addition);
    }

    private static Map<String, Double> defaultWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("Valuation", 14.0d);
        weights.put("Quality", 17.0d);
        weights.put("Growth", 14.0d);
        weights.put("Stability", 9.0d);
        weights.put("Earnings", 13.0d);
        weights.put("Analyst", 7.0d);
        weights.put("Momentum", 8.0d);
        weights.put("Risk", 8.0d);
        weights.put("Institution", 6.0d);
        weights.put("News", 4.0d);
        return weights;
    }

    private static Map<String, String> defaultReasons() {
        Map<String, String> reasons = new LinkedHashMap<>();
        for (String factor : defaultWeights().keySet()) {
            reasons.put(factor, "Current default factor ranking weight.");
        }
        return reasons;
    }

    private static Map<String, Double> normalizeWeights(Map<String, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).filter(value -> value > 0).sum();
        Map<String, Double> normalized = new LinkedHashMap<>();
        for (Factor factor : factorDefinitions()) {
            double value = weights.getOrDefault(factor.label(), 0.0d);
            normalized.put(factor.label(), total <= 0 ? 0 : value * 100.0d / total);
        }
        return normalized;
    }

    private static void persistWeightProfile(StockSignalWeightProfileMapper mapper, WeightProfileDefinition profile) {
        StockSignalWeightProfile entity = new StockSignalWeightProfile();
        entity.setCode(profile.code());
        entity.setName(profile.name());
        entity.setDescription(profile.description());
        entity.setActive(profile.active());
        entity.setSource(profile.source());
        mapper.upsertProfile(entity);
        int sortOrder = 10;
        for (Factor factor : factorDefinitions()) {
            StockSignalWeightProfileItem item = new StockSignalWeightProfileItem();
            item.setProfileCode(profile.code());
            item.setFactor(factor.label());
            item.setWeight(BigDecimal.valueOf(profile.weight(factor.label())).setScale(4, RoundingMode.HALF_UP));
            item.setEnabled(true);
            item.setReason(profile.reason(factor.label()));
            item.setSortOrder(sortOrder);
            mapper.upsertItem(item);
            sortOrder += 10;
        }
    }

    private static List<WeightProfileDefinition> loadWeightProfiles(StockSignalWeightProfileMapper mapper,
            List<WeightProfileDefinition> fallbackProfiles) {
        Map<String, WeightProfileDefinition> fallback = fallbackProfiles.stream()
                .collect(Collectors.toMap(WeightProfileDefinition::code, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        List<WeightProfileDefinition> profiles = new ArrayList<>();
        for (StockSignalWeightProfile profile : mapper.findProfiles()) {
            List<StockSignalWeightProfileItem> items = mapper.findItemsByProfile(profile.getCode());
            Map<String, Double> weights = new LinkedHashMap<>();
            Map<String, String> reasons = new LinkedHashMap<>();
            for (StockSignalWeightProfileItem item : items) {
                if (Boolean.FALSE.equals(item.getEnabled()) || item.getWeight() == null) {
                    continue;
                }
                weights.put(item.getFactor(), item.getWeight().doubleValue());
                reasons.put(item.getFactor(), fallback(item.getReason()));
            }
            WeightProfileDefinition fallbackProfile = fallback.get(profile.getCode());
            if (weights.isEmpty() && fallbackProfile != null) {
                weights.putAll(fallbackProfile.weights());
                reasons.putAll(fallbackProfile.reasons());
            }
            profiles.add(new WeightProfileDefinition(
                    profile.getCode(),
                    fallback(profile.getName()),
                    fallback(profile.getDescription()),
                    Boolean.TRUE.equals(profile.getActive()),
                    fallback(profile.getSource()),
                    normalizeWeights(weights),
                    reasons));
        }
        return profiles;
    }

    private static List<StockBacktestView.ProfileComparisonRow> profileComparisons(
            List<StockBacktestResult> results,
            List<WeightProfileDefinition> profiles) {
        List<StockBacktestView.ProfileComparisonRow> rows = new ArrayList<>();
        for (WeightProfileDefinition profile : profiles) {
            for (int horizon : HORIZONS) {
                List<StockBacktestResult> horizonResults = byHorizon(results, horizon).stream()
                        .filter(result -> result.getForwardReturnPct() != null)
                        .toList();
                if (horizonResults.isEmpty()) {
                    continue;
                }
                List<ScoredForwardReturn> scored = scoredForwardReturns(horizonResults, profile);
                ProfileSpread spread = profileSpread(scored);
                Double ic = informationCoefficientScored(scored);
                rows.add(new StockBacktestView.ProfileComparisonRow(
                        profile.code(),
                        profile.active() ? "active" : "inactive",
                        horizonLabel(horizon),
                        integer(horizonResults.size()),
                        percent(averageReturn(horizonResults)),
                        percent(hitRate(horizonResults)),
                        ic == null ? "-" : decimal(ic),
                        spread.topReturn() == null ? "-" : percent(spread.topReturn()),
                        spread.bottomReturn() == null ? "-" : percent(spread.bottomReturn()),
                        spread.spread() == null ? "-" : percent(spread.spread()),
                        spread.spread() == null ? "neutral" : tone(spread.spread())));
            }
        }
        return rows;
    }

    private static List<StockBacktestView.PromotionGateRow> promotionGate(
            List<StockBacktestResult> results,
            List<WeightProfileDefinition> profiles) {
        WeightProfileDefinition defaultProfile = profiles.stream()
                .filter(profile -> "DEFAULT".equals(profile.code()))
                .findFirst()
                .orElseGet(StockBacktestService::defaultWeightProfile);
        List<WeightProfileDefinition> candidateProfiles = profiles.stream()
                .filter(profile -> !"DEFAULT".equals(profile.code()))
                .toList();

        List<StockBacktestView.PromotionGateRow> rows = new ArrayList<>();
        List<StockBacktestResult> promotionSample = promotionSample(results);
        for (WeightProfileDefinition candidateProfile : candidateProfiles) {
            StockBacktestView.PromotionGateRow profileGate = promotionGateRow(
                    "Profile A/B",
                    candidateProfile.code(),
                    evaluateProfile(promotionSample, defaultProfile),
                    evaluateProfile(promotionSample, candidateProfile));
            if (profileGate != null) {
                rows.add(profileGate);
            }
        }

        rows.addAll(walkForwardPromotionGates(results, defaultProfile));
        return rows;
    }

    private static List<StockBacktestResult> promotionSample(List<StockBacktestResult> results) {
        return results.stream()
                .filter(result -> result.getHorizonDays() != null)
                .filter(result -> PROMOTION_HORIZONS.contains(result.getHorizonDays()))
                .filter(result -> result.getForwardReturnPct() != null)
                .toList();
    }

    private static List<StockBacktestView.PromotionGateRow> walkForwardPromotionGates(
            List<StockBacktestResult> results,
            WeightProfileDefinition defaultProfile) {
        List<Integer> years = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .map(LocalDate::getYear)
                .distinct()
                .sorted()
                .toList();
        if (years.size() < 2) {
            return List.of();
        }

        List<ScoredForwardReturn> defaultV1Scores = new ArrayList<>();
        List<ScoredForwardReturn> candidateV1Scores = new ArrayList<>();
        List<ScoredForwardReturn> defaultV2Scores = new ArrayList<>();
        List<ScoredForwardReturn> candidateV2Scores = new ArrayList<>();
        for (int testYear : years) {
            List<StockBacktestResult> trainResults = results.stream()
                    .filter(result -> result.getSignalDate() != null)
                    .filter(result -> result.getSignalDate().getYear() < testYear)
                    .filter(result -> result.getForwardReturnPct() != null)
                    .toList();
            if (trainResults.size() < 100) {
                continue;
            }

            List<StockBacktestResult> testResults = promotionSample(results).stream()
                    .filter(result -> result.getSignalDate() != null)
                    .filter(result -> result.getSignalDate().getYear() == testYear)
                    .toList();
            if (testResults.size() < 40) {
                continue;
            }

            WeightProfileDefinition trainedV1Profile = walkForwardWeightProfile(trainResults, false);
            WeightProfileDefinition trainedV2Profile = walkForwardWeightProfile(trainResults, true);
            defaultV1Scores.addAll(scoredForwardReturns(testResults, defaultProfile));
            candidateV1Scores.addAll(scoredForwardReturns(testResults, trainedV1Profile));
            defaultV2Scores.addAll(scoredForwardReturns(testResults, defaultProfile));
            candidateV2Scores.addAll(scoredForwardReturns(testResults, trainedV2Profile));
        }
        List<StockBacktestView.PromotionGateRow> rows = new ArrayList<>();
        StockBacktestView.PromotionGateRow v1 = promotionGateRow(
                "Walk-forward",
                "WALK_FORWARD_V1",
                evaluateScored(defaultV1Scores),
                evaluateScored(candidateV1Scores));
        if (v1 != null) {
            rows.add(v1);
        }
        StockBacktestView.PromotionGateRow v2 = promotionGateRow(
                "Walk-forward",
                "WALK_FORWARD_V2",
                evaluateScored(defaultV2Scores),
                evaluateScored(candidateV2Scores));
        if (v2 != null) {
            rows.add(v2);
        }
        return rows;
    }

    private static StockBacktestView.PromotionGateRow promotionGateRow(
            String scope,
            String profile,
            ProfileEvaluation baseline,
            ProfileEvaluation candidate) {
        if (baseline.sampleCount() == 0 && candidate.sampleCount() == 0) {
            return null;
        }

        Double spreadDelta = delta(candidate.spread(), baseline.spread());
        Double icDelta = delta(candidate.ic(), baseline.ic());
        Double topHitDelta = delta(candidate.topHitRate(), baseline.topHitRate());
        PromotionDecision decision = promotionDecision(candidate, spreadDelta, icDelta, topHitDelta);
        String evidence = "spread " + signedPercent(spreadDelta)
                + ", IC " + signedDecimal(icDelta)
                + ", top hit " + signedPercent(topHitDelta);
        return new StockBacktestView.PromotionGateRow(
                scope,
                profile,
                decision.label(),
                integer(candidate.sampleCount()),
                signedPercent(baseline.spread()),
                signedPercent(candidate.spread()),
                signedPercent(spreadDelta),
                signedDecimal(baseline.ic()),
                signedDecimal(candidate.ic()),
                signedDecimal(icDelta),
                signedPercent(topHitDelta),
                evidence,
                decision.tone());
    }

    private static PromotionDecision promotionDecision(
            ProfileEvaluation candidate,
            Double spreadDelta,
            Double icDelta,
            Double topHitDelta) {
        if (candidate.sampleCount() < 500 || candidate.spread() == null || candidate.ic() == null) {
            return new PromotionDecision("보류", "neutral");
        }
        double resolvedSpreadDelta = spreadDelta == null ? 0 : spreadDelta;
        double resolvedIcDelta = icDelta == null ? 0 : icDelta;
        double resolvedTopHitDelta = topHitDelta == null ? 0 : topHitDelta;
        if (candidate.spread() > 0.20d
                && candidate.ic() >= 0.0d
                && resolvedSpreadDelta >= 0.30d
                && resolvedIcDelta >= 0.005d
                && resolvedTopHitDelta >= 0.0d) {
            return new PromotionDecision("승격 후보", "positive");
        }
        if (candidate.spread() < -0.20d || candidate.ic() < -0.015d || resolvedSpreadDelta <= -0.30d) {
            return new PromotionDecision("탈락", "negative");
        }
        return new PromotionDecision("보류", "neutral");
    }
    private static ProfileEvaluation evaluateProfile(
            List<StockBacktestResult> results,
            Function<StockBacktestResult, Integer> scoreReader) {
        return evaluateScored(results.stream()
                .map(result -> new ScoredForwardReturn(scoreReader.apply(result), result.getForwardReturnPct()))
                .toList());
    }

    private static ProfileEvaluation evaluateProfile(
            List<StockBacktestResult> results,
            WeightProfileDefinition profile) {
        return evaluateScored(scoredForwardReturns(results, profile));
    }

    private static List<ScoredForwardReturn> scoredForwardReturns(
            List<StockBacktestResult> results,
            WeightProfileDefinition profile) {
        ProfileScoreContext context = profileScoreContext(results, profile);
        return results.stream()
                .map(result -> new ScoredForwardReturn(
                        scoreForProfile(result, profile, context),
                        result.getForwardReturnPct()))
                .filter(row -> row.score() != null && row.forwardReturnPct() != null)
                .toList();
    }

    private static ProfileEvaluation evaluateScored(List<ScoredForwardReturn> rows) {
        List<ScoredForwardReturn> usable = rows.stream()
                .filter(row -> row.score() != null && row.forwardReturnPct() != null)
                .sorted(Comparator.comparing(ScoredForwardReturn::score).reversed())
                .toList();
        if (usable.size() < 10) {
            return new ProfileEvaluation(usable.size(), null, null, null);
        }
        int decileSize = Math.max(1, (int) Math.ceil(usable.size() * 0.1));
        List<ScoredForwardReturn> top = usable.subList(0, decileSize);
        List<ScoredForwardReturn> bottom = usable.subList(usable.size() - decileSize, usable.size());
        double spread = averageScoredReturn(top) - averageScoredReturn(bottom);
        return new ProfileEvaluation(
                usable.size(),
                spread,
                informationCoefficientScored(usable),
                hitRateScored(top));
    }

    private static double averageScoredReturn(List<ScoredForwardReturn> rows) {
        return rows.stream()
                .map(ScoredForwardReturn::forwardReturnPct)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);
    }

    private static Double hitRateScored(List<ScoredForwardReturn> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        long wins = rows.stream()
                .map(ScoredForwardReturn::forwardReturnPct)
                .filter(value -> value != null && value.signum() > 0)
                .count();
        return wins * 100.0d / rows.size();
    }

    private static Double informationCoefficientScored(List<ScoredForwardReturn> rows) {
        List<ScoredForwardReturn> usable = rows.stream()
                .filter(row -> row.score() != null && row.forwardReturnPct() != null)
                .toList();
        if (usable.size() < 10) {
            return null;
        }
        double averageScore = usable.stream().mapToDouble(ScoredForwardReturn::score).average().orElse(0);
        double averageReturn = averageScoredReturn(usable);
        double covariance = 0;
        double scoreVariance = 0;
        double returnVariance = 0;
        for (ScoredForwardReturn row : usable) {
            double scoreDelta = row.score() - averageScore;
            double returnDelta = row.forwardReturnPct().doubleValue() - averageReturn;
            covariance += scoreDelta * returnDelta;
            scoreVariance += scoreDelta * scoreDelta;
            returnVariance += returnDelta * returnDelta;
        }
        if (scoreVariance == 0 || returnVariance == 0) {
            return null;
        }
        return covariance / Math.sqrt(scoreVariance * returnVariance);
    }

    private static Double delta(Double candidate, Double baseline) {
        return candidate == null || baseline == null ? null : candidate - baseline;
    }

    private static String signedPercent(Double value) {
        return value == null ? "-" : percent(value);
    }

    private static String signedDecimal(Double value) {
        return value == null ? "-" : decimal(value);
    }

    private static List<StockBacktestView.WalkForwardProfileRow> walkForwardProfiles(
            List<StockBacktestResult> results) {
        List<Integer> years = results.stream()
                .map(StockBacktestResult::getSignalDate)
                .filter(Objects::nonNull)
                .map(LocalDate::getYear)
                .distinct()
                .sorted()
                .toList();
        if (years.size() < 2) {
            return List.of();
        }

        WeightProfileDefinition defaultProfile = defaultWeightProfile();
        List<StockBacktestView.WalkForwardProfileRow> rows = new ArrayList<>();
        for (int testYear : years) {
            List<StockBacktestResult> trainResults = results.stream()
                    .filter(result -> result.getSignalDate() != null)
                    .filter(result -> result.getSignalDate().getYear() < testYear)
                    .filter(result -> result.getForwardReturnPct() != null)
                    .toList();
            if (trainResults.size() < 100) {
                continue;
            }
            WeightProfileDefinition trainedV1Profile = walkForwardWeightProfile(trainResults, false);
            WeightProfileDefinition trainedV2Profile = walkForwardWeightProfile(trainResults, true);
            String trainWindow = trainWindow(years, testYear);
            String testWindow = Integer.toString(testYear);

            for (int horizon : HORIZONS) {
                List<StockBacktestResult> trainHorizon = byHorizon(trainResults, horizon).stream()
                        .filter(result -> result.getForwardReturnPct() != null)
                        .toList();
                List<StockBacktestResult> testHorizon = byHorizon(results, horizon).stream()
                        .filter(result -> result.getSignalDate() != null)
                        .filter(result -> result.getSignalDate().getYear() == testYear)
                        .filter(result -> result.getForwardReturnPct() != null)
                        .toList();
                if (trainHorizon.size() < 50 || testHorizon.size() < 20) {
                    continue;
                }
                StockBacktestView.WalkForwardProfileRow defaultRow = walkForwardProfileRow(
                        trainWindow, testWindow, defaultProfile, horizon, testHorizon);
                if (defaultRow != null) {
                    rows.add(defaultRow);
                }
                StockBacktestView.WalkForwardProfileRow trainedRow = walkForwardProfileRow(
                        trainWindow, testWindow, trainedV1Profile, horizon, testHorizon);
                if (trainedRow != null) {
                    rows.add(trainedRow);
                }
                StockBacktestView.WalkForwardProfileRow trainedV2Row = walkForwardProfileRow(
                        trainWindow, testWindow, trainedV2Profile, horizon, testHorizon);
                if (trainedV2Row != null) {
                    rows.add(trainedV2Row);
                }
            }
        }
        return rows;
    }

    private static WeightProfileDefinition walkForwardWeightProfile(List<StockBacktestResult> trainResults,
            boolean orthogonalized) {
        WeightProfileDefinition trained = orthogonalized
                ? backtestV2WeightProfile(trainResults, factorMetrics(trainResults), yearStabilityMetrics(trainResults))
                : backtestWeightProfile(factorMetrics(trainResults), yearStabilityMetrics(trainResults));
        return new WeightProfileDefinition(
                orthogonalized ? "WALK_FORWARD_V2" : "WALK_FORWARD_V1",
                orthogonalized ? "Walk-forward v2 Orthogonal Candidate" : "Walk-forward v1 Candidate",
                orthogonalized
                        ? "Generated from train years with sector/size neutralization and correlation penalties."
                        : "Generated only from train years before the validation year.",
                false,
                "WALK_FORWARD",
                trained.weights(),
                trained.reasons());
    }

    private static String trainWindow(List<Integer> years, int testYear) {
        List<Integer> trainYears = years.stream()
                .filter(year -> year < testYear)
                .toList();
        if (trainYears.isEmpty()) {
            return "-";
        }
        Integer first = trainYears.getFirst();
        Integer last = trainYears.getLast();
        return Objects.equals(first, last) ? first.toString() : first + "-" + last;
    }

    private static StockBacktestView.WalkForwardProfileRow walkForwardProfileRow(
            String trainWindow,
            String testWindow,
            WeightProfileDefinition profile,
            int horizon,
            List<StockBacktestResult> testResults) {
        List<ScoredForwardReturn> scored = scoredForwardReturns(testResults, profile);
        if (scored.isEmpty()) {
            return null;
        }
        ProfileSpread spread = profileSpread(scored);
        Double ic = informationCoefficientScored(scored);
        return new StockBacktestView.WalkForwardProfileRow(
                trainWindow,
                testWindow,
                profile.code(),
                horizonLabel(horizon),
                integer(scored.size()),
                percent(averageScoredReturn(scored)),
                percent(hitRateScored(scored)),
                ic == null ? "-" : decimal(ic),
                spread.topReturn() == null ? "-" : percent(spread.topReturn()),
                spread.bottomReturn() == null ? "-" : percent(spread.bottomReturn()),
                spread.spread() == null ? "-" : percent(spread.spread()),
                spread.spread() == null ? "neutral" : tone(spread.spread()));
    }

    private static ProfileSpread profileSpread(List<ScoredForwardReturn> rows) {
        List<ScoredForwardReturn> scored = rows.stream()
                .filter(row -> row.score() != null && row.forwardReturnPct() != null)
                .sorted(Comparator.comparing(ScoredForwardReturn::score).reversed())
                .toList();
        if (scored.size() < 10) {
            return new ProfileSpread(null, null, null);
        }
        int decileSize = Math.max(1, (int) Math.ceil(scored.size() * 0.1));
        List<ScoredForwardReturn> top = scored.subList(0, decileSize);
        List<ScoredForwardReturn> bottom = scored.subList(scored.size() - decileSize, scored.size());
        double topReturn = averageScoredReturn(top);
        double bottomReturn = averageScoredReturn(bottom);
        return new ProfileSpread(topReturn, bottomReturn, topReturn - bottomReturn);
    }

    private static List<StockBacktestView.WeightProfileItemRow> weightProfileItems(
            List<WeightProfileDefinition> profiles) {
        Map<String, Double> defaults = defaultWeights();
        List<StockBacktestView.WeightProfileItemRow> rows = new ArrayList<>();
        for (WeightProfileDefinition profile : profiles) {
            for (Factor factor : factorDefinitions()) {
                String label = factor.label();
                double weight = profile.weight(label);
                double defaultWeight = defaults.getOrDefault(label, 0.0d);
                WeightState state = weightState(profile, weight - defaultWeight);
                rows.add(new StockBacktestView.WeightProfileItemRow(
                        profile.code(),
                        label,
                        weightPercent(weight),
                        state.label(),
                        profile.reason(label),
                        state.tone()));
            }
        }
        return rows;
    }

    private static WeightState weightState(WeightProfileDefinition profile, double delta) {
        if ("DEFAULT".equals(profile.code())) {
            return new WeightState("baseline", "neutral");
        }
        if (delta > 0.4d) {
            return new WeightState("상향", "positive");
        }
        if (delta < -0.4d) {
            return new WeightState("하향", "negative");
        }
        return new WeightState("유지", "neutral");
    }
    private static Integer scoreForProfile(StockBacktestResult result, WeightProfileDefinition profile) {
        return scoreForProfile(result, profile, ProfileScoreContext.empty());
    }

    private static Integer scoreForProfile(StockBacktestResult result, WeightProfileDefinition profile,
            ProfileScoreContext context) {
        if ("DEFAULT".equals(profile.code())) {
            return result.getIntegratedScore();
        }
        Integer rawScore = rawScoreForProfile(result, profile);
        if (rawScore == null) {
            return null;
        }
        if (!context.enabled()) {
            return applyBacktestGuardrails(rawScore, result);
        }
        ScoreStats stats = context.stats(result);
        if (stats == null || stats.count() < 12 || stats.stdDev() < 0.0001d) {
            return applyBacktestGuardrails(rawScore, result);
        }
        double zScore = (rawScore - stats.average()) / stats.stdDev();
        int neutralScore = (int) Math.round(clamp(50.0d + zScore * 12.0d, 0.0d, 100.0d));
        int blendedScore = (int) Math.round(neutralScore * 0.70d + rawScore * 0.30d);
        return applyBacktestGuardrails(blendedScore, result);
    }

    private static Integer rawScoreForProfile(StockBacktestResult result, WeightProfileDefinition profile) {
        double weightedScore = 0;
        double totalWeight = 0;
        for (Factor factor : factorDefinitions()) {
            Integer score = factor.score(result);
            double weight = profile.weight(factor.label());
            if (score == null || weight <= 0) {
                continue;
            }
            weightedScore += score * weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            return null;
        }
        return (int) Math.round(weightedScore / totalWeight);
    }

    private static ProfileScoreContext profileScoreContext(List<StockBacktestResult> results,
            WeightProfileDefinition profile) {
        if (!profile.orthogonalized()) {
            return ProfileScoreContext.empty();
        }
        List<RawProfileScore> rawScores = results.stream()
                .map(result -> new RawProfileScore(result, rawScoreForProfile(result, profile)))
                .filter(row -> row.score() != null)
                .toList();
        if (rawScores.size() < 100) {
            return ProfileScoreContext.empty();
        }
        Map<String, ScoreStats> sectorSizeStats = rawScores.stream()
                .collect(Collectors.groupingBy(row -> neutralGroup(row.result()), LinkedHashMap::new,
                        Collectors.mapping(RawProfileScore::score, Collectors.toList())))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> scoreStats(entry.getValue()),
                        (left, right) -> left, LinkedHashMap::new));
        Map<String, ScoreStats> sectorStats = rawScores.stream()
                .collect(Collectors.groupingBy(row -> fallback(row.result().getSector()), LinkedHashMap::new,
                        Collectors.mapping(RawProfileScore::score, Collectors.toList())))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> scoreStats(entry.getValue()),
                        (left, right) -> left, LinkedHashMap::new));
        return new ProfileScoreContext(true, sectorSizeStats, sectorStats);
    }

    private static String neutralGroup(StockBacktestResult result) {
        return fallback(result.getSector()) + "|" + marketCapBucket(result.getMarketCap());
    }

    private static String marketCapBucket(BigDecimal marketCap) {
        if (marketCap == null || marketCap.signum() <= 0) {
            return "UNKNOWN";
        }
        double value = marketCap.doubleValue();
        if (value >= 1_000_000.0d) {
            return "MEGA";
        }
        if (value >= 200_000.0d) {
            return "LARGE";
        }
        if (value >= 50_000.0d) {
            return "MID";
        }
        return "SMALL";
    }

    private static ScoreStats scoreStats(List<Integer> scores) {
        if (scores.isEmpty()) {
            return new ScoreStats(0, 0, 0);
        }
        double average = scores.stream().mapToDouble(Integer::doubleValue).average().orElse(0);
        double variance = 0;
        for (Integer score : scores) {
            double delta = score - average;
            variance += delta * delta;
        }
        double stdDev = Math.sqrt(variance / scores.size());
        return new ScoreStats(scores.size(), average, stdDev);
    }

    private static int applyBacktestGuardrails(int score, StockBacktestResult result) {
        if (lte(result.getQualityScore(), 35)) {
            score = Math.min(score, 58);
        }
        if (lte(result.getEarningsScore(), 35) && lte(result.getMomentumScore(), 45)) {
            score = Math.min(score, 60);
        }
        if (lte(result.getValuationScore(), 30) && !gte(result.getQualityScore(), 70)) {
            score = Math.min(score, 62);
        }
        if (lte(result.getStabilityScore(), 30)) {
            score = Math.min(score, 60);
        }
        if (lte(result.getRiskScore(), 30)) {
            score = Math.min(score, 64);
        }
        if (lte(result.getAnalystScore(), 30)) {
            score = Math.min(score, 68);
        }
        if (lte(result.getInstitutionScore(), 25)) {
            score = Math.min(score, 72);
        }
        if (result.getDataQualityScore() != null && result.getDataQualityScore() < 35) {
            score = Math.min(score, 55);
        } else if (result.getDataQualityScore() != null && result.getDataQualityScore() < 50) {
            score = Math.min(score, 60);
        }
        return Math.max(0, Math.min(100, score));
    }

    private static List<StockBacktestView.SectorRow> sectors(List<StockBacktestResult> results) {
        return results.stream()
                .filter(result -> result.getHorizonDays() != null)
                .collect(Collectors.groupingBy(
                        result -> horizonLabel(result.getHorizonDays()) + "|" + fallback(result.getSector()),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", 2);
                    double average = averageReturn(entry.getValue());
                    return new StockBacktestView.SectorRow(parts[0], parts.length > 1 ? parts[1] : "-",
                            integer(entry.getValue().size()), percent(average), tone(average));
                })
                .sorted(Comparator
                        .comparing(StockBacktestView.SectorRow::horizon)
                        .thenComparing(row -> numeric(row.count()), Comparator.reverseOrder()))
                .limit(30)
                .toList();
    }

    private static List<StockBacktestResult> byHorizon(List<StockBacktestResult> results, int horizon) {
        return results.stream()
                .filter(result -> Objects.equals(result.getHorizonDays(), horizon))
                .toList();
    }

    private static double averageReturn(List<StockBacktestResult> results) {
        return results.stream()
                .map(StockBacktestResult::getForwardReturnPct)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);
    }

    private static int score(StockBacktestResult result) {
        return result.getIntegratedScore() == null ? 50 : result.getIntegratedScore();
    }

    private static int candleCount(StockBacktestCoverage row) {
        return row.getCandleCount() == null ? 0 : row.getCandleCount();
    }

    private static double coverageYears(StockBacktestCoverage row) {
        if (row.getFirstTradeDate() == null || row.getLastTradeDate() == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(row.getFirstTradeDate(), row.getLastTradeDate());
        return Math.max(0, days / 365.25d);
    }

    private static long staleDays(StockBacktestCoverage row, LocalDate today) {
        if (row.getLastTradeDate() == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(row.getLastTradeDate(), today);
    }

    private static String coverageStatus(StockBacktestCoverage row, LocalDate today) {
        int count = candleCount(row);
        if (count == 0) {
            return "missing";
        }
        if (count < 21) {
            return "too short for 20D";
        }
        if (count < 61) {
            return "too short for 60D";
        }
        long staleDays = staleDays(row, today);
        if (staleDays > 14) {
            return "stale " + staleDays + "D";
        }
        return "ok";
    }

    private static StockBacktestView empty(String indexCode) {
        StockBacktestView.CoverageSummary coverage = new StockBacktestView.CoverageSummary(
                "0", "0", "0", "0", "0", "0", "0.0Y", "0.0Y");
        StockBacktestView.ResultSummary result = new StockBacktestView.ResultSummary(
                "0", "0", "0", "0", "0", false);
        return new StockBacktestView(indexCode, coverage, result, cards(coverage, result, List.of()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static Double hitRate(List<StockBacktestResult> results) {
        if (results.isEmpty()) {
            return null;
        }
        long wins = results.stream()
                .map(StockBacktestResult::getForwardReturnPct)
                .filter(value -> value != null && value.signum() > 0)
                .count();
        return wins * 100.0d / results.size();
    }

    private static Double informationCoefficient(List<StockBacktestResult> results) {
        return informationCoefficient(results, StockBacktestResult::getIntegratedScore);
    }

    private static Double informationCoefficient(List<StockBacktestResult> results,
            Function<StockBacktestResult, Integer> scoreReader) {
        List<StockBacktestResult> usable = results.stream()
                .filter(result -> scoreReader.apply(result) != null && result.getForwardReturnPct() != null)
                .toList();
        if (usable.size() < 10) {
            return null;
        }
        double averageScore = usable.stream().mapToDouble(result -> scoreReader.apply(result)).average().orElse(0);
        double averageReturn = usable.stream()
                .map(StockBacktestResult::getForwardReturnPct)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);
        double covariance = 0;
        double scoreVariance = 0;
        double returnVariance = 0;
        for (StockBacktestResult result : usable) {
            double scoreDelta = scoreReader.apply(result) - averageScore;
            double returnDelta = result.getForwardReturnPct().doubleValue() - averageReturn;
            covariance += scoreDelta * returnDelta;
            scoreVariance += scoreDelta * scoreDelta;
            returnVariance += returnDelta * returnDelta;
        }
        if (scoreVariance == 0 || returnVariance == 0) {
            return null;
        }
        return covariance / Math.sqrt(scoreVariance * returnVariance);
    }

    private static List<Factor> factorDefinitions() {
        return List.of(
                new Factor("Valuation", StockBacktestResult::getValuationScore),
                new Factor("Quality", StockBacktestResult::getQualityScore),
                new Factor("Growth", StockBacktestResult::getGrowthScore),
                new Factor("Stability", StockBacktestResult::getStabilityScore),
                new Factor("Earnings", StockBacktestResult::getEarningsScore),
                new Factor("Analyst", StockBacktestResult::getAnalystScore),
                new Factor("News", StockBacktestResult::getNewsScore),
                new Factor("Momentum", StockBacktestResult::getMomentumScore),
                new Factor("Risk", StockBacktestResult::getRiskScore),
                new Factor("Institution", StockBacktestResult::getInstitutionScore));
    }

    private static String diagnosticTone(Double spread, Double ic) {
        double resolvedSpread = spread == null ? 0 : spread;
        double resolvedIc = ic == null ? 0 : ic;
        if (resolvedSpread > 0.20d && resolvedIc >= 0.0d) {
            return "positive";
        }
        if (resolvedSpread < -0.20d || resolvedIc < -0.01d) {
            return "negative";
        }
        return "neutral";
    }

    private static String stabilityTone(YearStabilityMetric metric) {
        if (metric.yearCount() == 0) {
            return "neutral";
        }
        double positiveRatio = metric.positiveYears() * 1.0d / metric.yearCount();
        if (positiveRatio >= 0.67d && metric.averageSpread() > 0) {
            return "positive";
        }
        if (positiveRatio <= 0.34d || metric.averageSpread() < -0.20d) {
            return "negative";
        }
        return "neutral";
    }

    private static int candidateConfidence(double spread20d, double spread60d, double ic20d, double yearRatio) {
        double score = 50.0d
                + clamp(spread20d * 5.0d, -20.0d, 20.0d)
                + clamp(spread60d * 2.5d, -12.0d, 12.0d)
                + clamp(ic20d * 500.0d, -15.0d, 15.0d)
                + clamp((yearRatio - 0.5d) * 30.0d, -10.0d, 10.0d);
        return (int) Math.round(clamp(score, 0.0d, 100.0d));
    }

    private static CandidateAction candidateActionV2(double spread20d, double spread60d, double ic20d,
            double yearRatio, int confidence) {
        if (confidence >= 70
                && spread20d > 0.50d
                && spread60d > 0.50d
                && ic20d > 0.01d
                && yearRatio >= 0.67d) {
            return new CandidateAction("상향 후보", "positive");
        }
        if (confidence <= 35
                || (spread20d < -0.80d && ic20d < -0.03d)
                || (ic20d < -0.05d && yearRatio <= 0.34d)) {
            return new CandidateAction("강등/반전 검토", "negative");
        }
        return new CandidateAction("유지/관찰", "neutral");
    }

    private static CandidateAction candidateAction(double spread20d, double spread60d, double ic20d,
            double yearRatio, int confidence) {
        if (confidence >= 65 && spread20d > 0.30d && spread60d >= 0 && ic20d >= 0 && yearRatio >= 0.5d) {
            return new CandidateAction("상향 후보", "positive");
        }
        if (confidence <= 40 || spread20d < -0.30d || ic20d < -0.02d) {
            return new CandidateAction("강등/반전 검토", "negative");
        }
        return new CandidateAction("유지/관찰", "neutral");
    }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String integer(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String percent(double value) {
        return String.format(Locale.US, "%+.2f%%", value);
    }

    private static String years(double value) {
        return String.format(Locale.US, "%.1fY", value);
    }

    private static String decimal(double value) {
        return String.format(Locale.US, "%+.3f", value);
    }

    private static String weightPercent(double value) {
        return String.format(Locale.US, "%.1f%%", value);
    }

    private static String signedWeightDelta(double value) {
        return String.format(Locale.US, "%+.1f%sp", value, "%");
    }

    private static String date(LocalDate value) {
        return value == null ? "-" : value.toString();
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String tone(double value) {
        if (value > 0.05d) {
            return "positive";
        }
        if (value < -0.05d) {
            return "negative";
        }
        return "neutral";
    }

    private static String toneNullable(Double value, double neutralPoint) {
        if (value == null) {
            return "caution";
        }
        if (value > neutralPoint) {
            return "positive";
        }
        if (value < neutralPoint) {
            return "negative";
        }
        return "neutral";
    }

    private static String horizonLabel(int horizon) {
        return horizon + "D";
    }

    private static int numeric(String value) {
        return Integer.parseInt(value.replace(",", ""));
    }

    private static boolean gte(Integer value, int threshold) {
        return value != null && value >= threshold;
    }

    private static boolean lte(Integer value, int threshold) {
        return value != null && value <= threshold;
    }

    private record Bucket(String label, Predicate<StockBacktestResult> predicate) {
    }

    private record Factor(String label, Function<StockBacktestResult, Integer> reader) {
        private Integer score(StockBacktestResult result) {
            return reader.apply(result);
        }
    }

    private record FactorMetric(
            int horizon,
            String factor,
            int sampleCount,
            double averageReturn,
            Double hitRate,
            Double ic,
            int highCount,
            double highReturn,
            int lowCount,
            double lowReturn,
            Double spread) {
    }

    private record SectorSpread(String sector, int highCount, int lowCount, double spread) {
    }

    private record YearStabilityMetric(
            int horizon,
            String factor,
            int yearCount,
            long positiveYears,
            double averageSpread,
            double minSpread,
            double maxSpread) {
    }

    private record CandidateAction(String label, String tone) {
    }

    private record ProfileDiagnostic(String label, String tone) {
    }

    private record FactorPairScore(Integer leftScore, Integer rightScore) {
    }

    private record FactorCorrelation(String leftFactor, String rightFactor, int sampleCount, double correlation) {
    }

    private record CorrelationRisk(String label, String note, String tone) {
    }

    private record PromotionDecision(String label, String tone) {
    }

    private record ProfileEvaluation(int sampleCount, Double spread, Double ic, Double topHitRate) {
    }

    private record WeightProfileDefinition(
            String code,
            String name,
            String description,
            boolean active,
            String source,
            Map<String, Double> weights,
            Map<String, String> reasons) {

        private double weight(String factor) {
            return weights.getOrDefault(factor, 0.0d);
        }

        private String reason(String factor) {
            return reasons.getOrDefault(factor, "-");
        }

        private boolean orthogonalized() {
            return code != null && code.endsWith("_V2");
        }
    }

    private record ProfileSpread(Double topReturn, Double bottomReturn, Double spread) {
    }

    private record RawProfileScore(StockBacktestResult result, Integer score) {
    }

    private record ScoreStats(int count, double average, double stdDev) {
    }

    private record ProfileScoreContext(
            boolean enabled,
            Map<String, ScoreStats> sectorSizeStats,
            Map<String, ScoreStats> sectorStats) {

        private static ProfileScoreContext empty() {
            return new ProfileScoreContext(false, Map.of(), Map.of());
        }

        private ScoreStats stats(StockBacktestResult result) {
            ScoreStats sectorSize = sectorSizeStats.get(neutralGroup(result));
            if (sectorSize != null && sectorSize.count() >= 12 && sectorSize.stdDev() > 0) {
                return sectorSize;
            }
            return sectorStats.get(fallback(result.getSector()));
        }
    }

    private record ScoredForwardReturn(Integer score, BigDecimal forwardReturnPct) {
    }

    private record WeightState(String label, String tone) {
    }
}

