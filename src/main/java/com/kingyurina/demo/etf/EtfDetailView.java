package com.kingyurina.demo.etf;

import java.util.List;

public record EtfDetailView(
        String symbol,
        String name,
        String issuer,
        String category,
        String strategy,
        String assetClass,
        String benchmark,
        String description,
        String website,
        String currency,
        String priceKrw,
        String change,
        String priceUsd,
        String volume,
        String aum,
        String expenseRatio,
        String dividendYield,
        String holdingsCount,
        String inceptionDate,
        String priceSourceLabel,
        EtfSignal signal,
        List<InfoRow> infoRows,
        List<ChartPoint> chartPoints) {

    public record InfoRow(String label, String value) {
    }

    public record ChartPoint(String label, String value, String height) {
    }
}
