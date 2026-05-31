package com.kingyurina.demo.stock;

import java.util.List;

public record StockMarketView(
        String title,
        String subtitle,
        String totalMembers,
        String cachedQuotes,
        String advancers,
        String decliners,
        String aggregateMarketCap,
        String averageChange,
        List<IndexCard> indexCards,
        List<Row> rows,
        List<Row> topMovers,
        List<SectorRow> sectors) {

    public record IndexCard(String label, String value, String change, boolean positive) {
    }

    public record Row(String rank, String symbol, String name, String logoUrl, String logoInitial, String sector,
            String price, String change, String marketCap, String per, String pbr, String roe, String signalScore,
            String signalConfidence, String signalTone, boolean positive) {
    }

    public record SectorRow(String sector, String count, String marketCap, String averageChange) {
    }
}
