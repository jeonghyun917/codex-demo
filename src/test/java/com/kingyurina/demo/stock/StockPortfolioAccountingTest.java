package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockPortfolioAccountingTest {
    @Test
    void legacyShadowRowsWithoutHoldingDatesCannotAdvertiseAnnualizedPerformance() throws Exception {
        var snapshot = new StockOptimizerShadowSnapshot();
        snapshot.setSignalDate(LocalDate.of(2025, 1, 2)); snapshot.setHorizonDays(20);
        snapshot.setBaselineNetReturnPct(BigDecimal.TEN); snapshot.setBenchmarkReturnPct(BigDecimal.ZERO);
        Object summary = call(StockPortfolioBacktestService.class, "shadowPathSummary", null,
                List.of(snapshot), true, 0d);
        assertTrue(Double.isNaN((double) call(summary.getClass(), "annualReturnPct", summary)));
        snapshot.setUsable(true); snapshot.setConstraintBreachCount(0);
        snapshot.setObjectiveGap(BigDecimal.ZERO); snapshot.setWeightDistancePct(BigDecimal.ZERO);
        var row = (StockPortfolioBacktestView.OptimizerShadowPathRow) call(StockPortfolioBacktestService.class,
                "optimizerShadowPathRow", null, "candidate", java.util.Collections.nCopies(20, snapshot), 0d);
        assertNotEquals("V6 candidate", row.verdict(), "Missing capital path must block promotion");
    }

    @Test
    void overlappingHoldingsDoNotRedeployCapitalAndAnnualizationIncludesCalendarGaps() throws Exception {
        List<StockBacktestResult> rows = new ArrayList<>();
        LocalDate first = LocalDate.of(2025, 1, 2), last = LocalDate.of(2026, 1, 2);
        for (LocalDate date : List.of(first, first.plusDays(1), last.minusDays(20))) {
            for (int i = 0; i < 10; i++) {
                var row = StockQuantModelCorrectnessTest.row("S" + i, date, date.plusDays(20), 10);
                row.setMarketCap(BigDecimal.ONE); row.setBenchmarkReturnPct(BigDecimal.ZERO); rows.add(row);
            }
        }
        var constructor = nested("StrategyConfig").getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Object config = constructor.newInstance(20, 10, nested("Weighting").getEnumConstants()[0],
                nested("RiskMode").getEnumConstants()[0]);
        Object run = call(StockPortfolioBacktestService.class, "runStrategy", null, config, rows,
                call(nested("RiskLookup"), "empty", null),
                call(nested("CovarianceLookup"), "empty", null),
                call(nested("ExpectedReturnLookup"), "empty", null),
                call(nested("RiskFreeRateLookup"), "empty", null, 0d));
        List<?> periods = (List<?>) call(run.getClass(), "periods", run);
        assertEquals(2, periods.size(), "January 3 cannot reuse the capital held from January 2 through January 22");
        Object summary = call(StockPortfolioBacktestService.class, "summarize", null, run, 0d);
        double total = (double) call(summary.getClass(), "cumulativeReturn", summary);
        double annual = (double) call(summary.getClass(), "annualReturn", summary);
        assertTrue(total < 21 && total > 19, "Two 10% deployments less explicit round-trip transaction costs");
        assertEquals(Math.pow(1 + total / 100, 365.2425 / 365d) * 100 - 100, annual, 0.000001);
    }

    private static Class<?> nested(String name) throws Exception {
        return Class.forName(StockPortfolioBacktestService.class.getName() + "$" + name);
    }

    private static Object call(Class<?> type, String name, Object target, Object... args) throws Exception {
        Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.getName().equals(name) && m.getParameterCount() == args.length).findFirst().orElseThrow();
        method.setAccessible(true); return method.invoke(target, args);
    }
}
