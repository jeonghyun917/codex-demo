package com.kingyurina.demo.stock;

import java.util.List;

public record StockHeatmapView(
        String indexCode,
        String title,
        String description,
        List<MenuItem> menus,
        List<Sector> sectors) {

    public record MenuItem(String code, String label, boolean active) {
    }

    public record Sector(String name, String layoutStyle, List<Industry> industries) {
    }

    public record Industry(String name, String layoutStyle, String colorClass, List<Tile> tiles) {
    }

    public record Tile(
            String symbol,
            String name,
            String sector,
            String industry,
            String change,
            String currentPrice,
            String previousClose,
            String openPrice,
            String dayRange,
            String marketCap,
            String per,
            String pbr,
            String roe,
            String layoutStyle,
            String colorClass,
            String href) {
    }
}
