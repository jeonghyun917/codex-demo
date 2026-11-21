package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FinancialSignalInputTest {
    @Test
    void pddCurrencyChangeIsNotAnnualGrowthInEitherConsumer() throws Exception {
        var current = row("FY", "2024-12-31", 366, "USD");
        var previous = row("FY", "2023-12-31", 365, "CNY");
        assertNull(contextValue("annualRevenueGrowth", current, previous, null, null));
        assertNull(contextValue("annualNetIncomeGrowth", current, previous, null, null));
        assertEquals(50, historicalScore("growthScore", current, previous));
    }

    @Test
    void missingAnnualAndQuarterDoNotTurnMultiplePeriodsIntoGrowth() throws Exception {
        var annual = row("FY", "2024-12-31", 366, "USD");
        var oldAnnual = row("FY", "2022-12-31", 365, "USD");
        var quarter = row("Q3", "2025-09-30", 92, "USD");
        var oldQuarter = row("Q1", "2025-03-31", 90, "USD");
        assertNull(contextValue("annualRevenueGrowth", annual, oldAnnual, null, null));
        assertNull(contextValue("quarterRevenueGrowth", null, null, quarter, oldQuarter));
        assertEquals(50, historicalScore("growthScore", annual, oldAnnual));
        assertEquals(50, historicalScore("earningsScore", quarter, oldQuarter));
    }

    @Test
    void matchedCurrencyAndAdjacentPeriodsRetainTheirObservedGrowth() throws Exception {
        var annual = row("FY", "2024-12-31", 366, "CNY");
        var previous = row("FY", "2023-12-31", 365, "CNY");
        annual.setRevenue(new BigDecimal("120")); previous.setRevenue(new BigDecimal("100"));
        assertEquals(new BigDecimal("20.0000"), contextValue("annualRevenueGrowth", annual, previous, null, null));
        assertTrue(historicalScore("growthScore", annual, previous) > 50);
        var quarter = row("Q2", "2025-06-30", 91, "USD");
        var priorQuarter = row("Q1", "2025-03-31", 90, "USD");
        quarter.setRevenue(new BigDecimal("120")); priorQuarter.setRevenue(new BigDecimal("100"));
        assertEquals(new BigDecimal("20.0000"), contextValue("quarterRevenueGrowth", null, null, quarter, priorQuarter));
    }

    @Test
    void historicalPeRequiresKnownUsdQuoteShareUnitButRetainsDomesticAndNormalizedEps() throws Exception {
        var annual = row("FY", "2024-12-31", 366, "USD");
        annual.setEpsDiluted(BigDecimal.TEN); annual.setEpsUnit("CNY/shares");
        assertEquals(50, valuationScore(annual));
        annual.setEpsUnit("USD/shares"); annual.setForm("20-F");
        assertEquals(50, valuationScore(annual), "Ordinary-share EPS is not proven ADS EPS");
        annual.setEpsUnit("USD/quoted-security");
        assertEquals(78, valuationScore(annual), "Explicitly normalized quoted unit remains usable");
        annual.setEpsUnit("USD/shares"); annual.setForm("10-K");
        assertEquals(78, valuationScore(annual), "Valid domestic USD/share EPS must not be blanket removed");
        annual.setEpsUnit(null);
        assertEquals(50, valuationScore(annual));
    }

    @Test
    void invalidDurationAndUnknownCurrencyAreNotComparableEvenWithFyOrQuarterLabels() throws Exception {
        var annual = row("FY", "2024-12-31", 273, "USD");
        var previous = row("FY", "2023-12-31", 365, "USD");
        assertNull(contextValue("annualRevenueGrowth", annual, previous, null, null));
        annual = row("FY", "2024-12-31", 366, null);
        assertNull(contextValue("annualRevenueGrowth", annual, previous, null, null));
        var quarter = row("Q2", "2025-06-30", 181, "USD");
        var priorQuarter = row("Q1", "2025-03-31", 90, "USD");
        assertNull(contextValue("quarterRevenueGrowth", null, null, quarter, priorQuarter));
    }

    private static Object contextValue(String method, SecFinancialStandard annual, SecFinancialStandard previous,
            SecFinancialStandard quarter, SecFinancialStandard previousQuarter) throws Exception {
        Class<?> type = Class.forName(StockSignalService.class.getName() + "$SecFinancialContext");
        var constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Object context = constructor.newInstance(annual, previous, quarter, previousQuarter);
        Method accessor = type.getDeclaredMethod(method); accessor.setAccessible(true);
        return accessor.invoke(context);
    }

    private static int historicalScore(String name, SecFinancialStandard current, SecFinancialStandard previous)
            throws Exception {
        Method method = HistoricalBacktestSeedService.class.getDeclaredMethod(name,
                SecFinancialStandard.class, SecFinancialStandard.class);
        method.setAccessible(true);
        return score(method.invoke(null, current, previous));
    }

    private static int valuationScore(SecFinancialStandard annual) throws Exception {
        Method method = HistoricalBacktestSeedService.class.getDeclaredMethod("valuationScore",
                BigDecimal.class, SecFinancialStandard.class);
        method.setAccessible(true);
        return score(method.invoke(null, BigDecimal.valueOf(100), annual));
    }

    private static int score(Object signal) throws Exception {
        Method method = signal.getClass().getDeclaredMethod("score"); method.setAccessible(true);
        return (int) method.invoke(signal);
    }

    private static SecFinancialStandard row(String period, String end, int days, String currency) {
        var row = new SecFinancialStandard(); row.setFiscalPeriod(period); row.setEndDate(LocalDate.parse(end));
        row.setStartDate(row.getEndDate().minusDays(days - 1)); row.setPeriodDays(days); row.setCurrency(currency);
        row.setRevenue(BigDecimal.valueOf(100)); row.setNetIncome(BigDecimal.valueOf(10)); row.setForm("10-K");
        return row;
    }
}
