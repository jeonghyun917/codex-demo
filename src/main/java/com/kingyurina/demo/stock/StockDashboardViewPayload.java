package com.kingyurina.demo.stock;

public record StockDashboardViewPayload(
        String activeIndex,
        StockMarketView market,
        StockMacroRegimeView macroRegime,
        StockQuantModelHealthView modelHealth) {
}
