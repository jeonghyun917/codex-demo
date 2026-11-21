package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record StockThreeMonthHighRatio(String display, BigDecimal value, String tone) {

    public static StockThreeMonthHighRatio from(BigDecimal currentPrice, BigDecimal threeMonthHigh) {
        if (currentPrice == null || threeMonthHigh == null
                || currentPrice.signum() <= 0 || threeMonthHigh.signum() <= 0) {
            return new StockThreeMonthHighRatio("-", null, "neutral");
        }

        BigDecimal ratio = currentPrice
                .multiply(BigDecimal.valueOf(100))
                .divide(threeMonthHigh, 2, RoundingMode.HALF_UP);
        String tone = ratio.compareTo(BigDecimal.valueOf(95)) >= 0
                ? "near-high"
                : ratio.compareTo(BigDecimal.valueOf(85)) >= 0 ? "mid-range" : "extended";
        return new StockThreeMonthHighRatio(ratio.toPlainString() + "%", ratio, tone);
    }
}
