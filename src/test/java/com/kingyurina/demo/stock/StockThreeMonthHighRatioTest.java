package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class StockThreeMonthHighRatioTest {

    @Test
    void formatsCurrentPriceAsPercentOfThreeMonthHigh() {
        StockThreeMonthHighRatio ratio = StockThreeMonthHighRatio.from(
                new BigDecimal("325.04"), new BigDecimal("334.99"));

        assertEquals("97.03%", ratio.display());
        assertEquals(new BigDecimal("97.03"), ratio.value());
        assertEquals("near-high", ratio.tone());
    }

    @Test
    void keepsValuesAboveOneHundredPercent() {
        StockThreeMonthHighRatio ratio = StockThreeMonthHighRatio.from(
                new BigDecimal("101"), new BigDecimal("100"));

        assertEquals("101.00%", ratio.display());
        assertEquals(new BigDecimal("101.00"), ratio.value());
        assertEquals("near-high", ratio.tone());
    }

    @Test
    void appliesToneBoundaries() {
        assertEquals("near-high", ratio("95.00").tone());
        assertEquals("mid-range", ratio("94.99").tone());
        assertEquals("mid-range", ratio("85.00").tone());
        assertEquals("extended", ratio("84.99").tone());
    }

    @Test
    void returnsUnavailableForMissingOrNonPositiveInputs() {
        assertUnavailable(StockThreeMonthHighRatio.from(null, BigDecimal.TEN));
        assertUnavailable(StockThreeMonthHighRatio.from(BigDecimal.TEN, null));
        assertUnavailable(StockThreeMonthHighRatio.from(BigDecimal.ZERO, BigDecimal.TEN));
        assertUnavailable(StockThreeMonthHighRatio.from(BigDecimal.TEN, BigDecimal.ZERO));
        assertUnavailable(StockThreeMonthHighRatio.from(new BigDecimal("-1"), BigDecimal.TEN));
    }

    @Test
    void retainsFactorScoresBeforeThreeMonthHighRatioProjection() {
        assertEquals(List.of(
                "valuationScore", "qualityScore", "growthScore", "stabilityScore", "earningsScore",
                "analystScore", "newsScore", "momentumScore", "riskScore",
                "threeMonthHighRatio", "threeMonthHighRatioValue", "threeMonthHighRatioTone"),
                Arrays.stream(StockMarketView.Row.class.getRecordComponents())
                        .skip(27)
                        .map(component -> component.getName())
                        .toList());
    }

    private static StockThreeMonthHighRatio ratio(String percent) {
        return StockThreeMonthHighRatio.from(new BigDecimal(percent), new BigDecimal("100"));
    }

    private static void assertUnavailable(StockThreeMonthHighRatio ratio) {
        assertEquals("-", ratio.display());
        assertNull(ratio.value());
        assertEquals("neutral", ratio.tone());
    }
}
