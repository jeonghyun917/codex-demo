package com.kingyurina.demo.etf;

import java.util.List;

public record EtfMarketView(
        String title,
        String subtitle,
        String totalFunds,
        String cachedQuotes,
        String advancers,
        String decliners,
        String averageExpense,
        String averageChange,
        List<Card> cards,
        List<Row> rows,
        List<Row> topMovers,
        List<CategoryRow> categories) {

    public record Card(String label, String value, String note, boolean positive) {
    }

    public record Row(String rank, String symbol, String name, String issuer, String category, String strategy,
            String assetClass, String price, String change, String aum, String expenseRatio, String dividendYield,
            String volume, String signalScore, String signalTone, boolean positive, Integer signalValue) {
    }

    public record CategoryRow(String category, String count, String averageExpense, String averageChange) {
    }
}
