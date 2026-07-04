package com.kingyurina.demo.stock;

import java.util.List;

public record StockQuantOpinionView(
        String label,
        String tone,
        String score,
        String summary,
        String snapshotDate,
        String modelVersion,
        List<Metric> metrics,
        List<String> interpretations,
        List<HorizonOpinion> horizonOpinions,
        PortfolioFit portfolioFit,
        List<Factor> factors,
        List<String> warnings) {

    public record Metric(String label, String value, String note, String tone) {
    }

    public record HorizonOpinion(String horizon, String label, String expectedExcess,
            String upsideProbability, String confidence, String tone) {
    }

    public record PortfolioFit(String label, String tone, String summary, List<Metric> metrics) {
    }

    public record Factor(String label, String score, String contribution, String tone, String barStyle,
            String direction) {
    }
}
