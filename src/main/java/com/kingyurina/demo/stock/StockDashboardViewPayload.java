package com.kingyurina.demo.stock;

import java.util.List;

import com.kingyurina.demo.stock.evaluation.ExpectedReturnEvaluationSummaryView;

public record StockDashboardViewPayload(
        String activeIndex,
        StockMarketView market,
        StockMacroRegimeView macroRegime,
        StockQuantModelHealthView modelHealth,
        List<StockApiDataSourceView> dataSources,
        ExpectedReturnEvaluationSummaryView expectedReturnEvaluation) {
}
