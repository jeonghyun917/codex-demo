package com.kingyurina.demo.stock;

import java.util.List;

public record StockDashboardViewPayload(
        String activeIndex,
        StockMarketView market,
        StockMacroRegimeView macroRegime,
        StockQuantModelHealthView modelHealth,
        List<StockApiDataSourceView> dataSources) {
}
