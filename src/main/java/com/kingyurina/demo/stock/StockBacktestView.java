package com.kingyurina.demo.stock;

import java.util.List;

public record StockBacktestView(
        String indexCode,
        CoverageSummary coverage,
        ResultSummary result,
        List<MetricCard> cards,
        List<CoverageRow> weakCoverageRows,
        List<BucketRow> scoreBuckets,
        List<DecileRow> deciles,
        List<FactorRow> factors,
        List<FactorDiagnosticRow> factorDiagnostics,
        List<SectorNeutralRow> sectorNeutralDiagnostics,
        List<YearStabilityRow> yearStabilityDiagnostics,
        List<WeightCandidateRow> weightCandidates,
        List<PromotionGateRow> promotionGate,
        List<ProfileDiagnosticRow> profileDiagnostics,
        List<FactorCorrelationRow> factorCorrelations,
        List<ProfileComparisonRow> profileComparisons,
        List<WalkForwardProfileRow> walkForwardProfiles,
        List<WeightProfileItemRow> weightProfileItems,
        List<SectorRow> sectors) {

    public record CoverageSummary(
            String totalSymbols,
            String withCandles,
            String missingCandles,
            String enough5d,
            String enough20d,
            String enough60d,
            String averageYears,
            String maxYears) {
    }

    public record ResultSummary(
            String snapshotCount,
            String resultCount,
            String completed5d,
            String completed20d,
            String completed60d,
            boolean hasResults) {
    }

    public record MetricCard(String label, String value, String note, String tone) {
    }

    public record CoverageRow(
            String symbol,
            String name,
            String sector,
            String candleCount,
            String firstTradeDate,
            String lastTradeDate,
            String years,
            String status) {
    }

    public record BucketRow(String horizon, String bucket, String count, String averageReturn, String tone) {
    }

    public record DecileRow(String horizon, String group, String count, String averageReturn, String spread, String tone) {
    }

    public record FactorRow(
            String horizon,
            String factor,
            String highCount,
            String highReturn,
            String lowCount,
            String lowReturn,
            String spread,
            String tone) {
    }

    public record SectorRow(String horizon, String sector, String count, String averageReturn, String tone) {
    }

    public record FactorDiagnosticRow(
            String horizon,
            String factor,
            String sampleCount,
            String averageReturn,
            String hitRate,
            String ic,
            String highCount,
            String highReturn,
            String lowCount,
            String lowReturn,
            String spread,
            String tone) {
    }

    public record SectorNeutralRow(
            String horizon,
            String factor,
            String sectorCount,
            String positiveSectors,
            String negativeSectors,
            String averageSpread,
            String bestSector,
            String worstSector,
            String tone) {
    }

    public record YearStabilityRow(
            String horizon,
            String factor,
            String yearCount,
            String positiveYears,
            String averageSpread,
            String minSpread,
            String maxSpread,
            String tone) {
    }

    public record WeightCandidateRow(String factor, String action, String score, String evidence, String tone) {
    }

    public record PromotionGateRow(
            String scope,
            String profile,
            String decision,
            String sampleCount,
            String defaultSpread,
            String candidateSpread,
            String spreadDelta,
            String defaultIc,
            String candidateIc,
            String icDelta,
            String topHitDelta,
            String evidence,
            String tone) {
    }

    public record ProfileDiagnosticRow(
            String profile,
            String factor,
            String defaultWeight,
            String candidateWeight,
            String delta,
            String spread20d,
            String spread60d,
            String ic20d,
            String stableYears,
            String diagnosis,
            String tone) {
    }

    public record FactorCorrelationRow(
            String leftFactor,
            String rightFactor,
            String sampleCount,
            String correlation,
            String risk,
            String note,
            String tone) {
    }

    public record ProfileComparisonRow(
            String profile,
            String status,
            String horizon,
            String sampleCount,
            String averageReturn,
            String hitRate,
            String ic,
            String topReturn,
            String bottomReturn,
            String spread,
            String tone) {
    }

    public record WalkForwardProfileRow(
            String trainWindow,
            String testWindow,
            String profile,
            String horizon,
            String sampleCount,
            String averageReturn,
            String hitRate,
            String ic,
            String topReturn,
            String bottomReturn,
            String spread,
            String tone) {
    }

    public record WeightProfileItemRow(
            String profile,
            String factor,
            String weight,
            String state,
            String reason,
            String tone) {
    }
}
