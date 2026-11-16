package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Fail-closed comparisons; no implicit FX or ordinary-share/ADS conversion. */
final class FinancialInputPolicy {
    private FinancialInputPolicy() { }

    static boolean comparable(SecFinancialStandard current, SecFinancialStandard previous, boolean annual) {
        if (!validPeriod(current, annual) || !validPeriod(previous, annual)) {
            return false;
        }
        String currency = normalized(current.getCurrency());
        if (!currency.matches("[A-Z]{3}") || !currency.equals(normalized(previous.getCurrency()))) {
            return false;
        }
        long gap = ChronoUnit.DAYS.between(previous.getEndDate(), current.getEndDate());
        if (gap < (annual ? 330 : 70) || gap > (annual ? 380 : 110)) {
            return false;
        }
        return annual || quarterNumber(current) == quarterNumber(previous) % 4 + 1;
    }

    static boolean validPeriod(SecFinancialStandard row, boolean annual) {
        if (row == null || row.getEndDate() == null
                || !(annual ? "FY".equals(row.getFiscalPeriod()) : quarterNumber(row) > 0)) {
            return false;
        }
        Long actualDays = row.getStartDate() == null ? null
                : ChronoUnit.DAYS.between(row.getStartDate(), row.getEndDate()) + 1;
        long days = row.getPeriodDays() == null ? actualDays == null ? 0 : actualDays : row.getPeriodDays();
        return days >= (annual ? 330 : 70) && days <= (annual ? 380 : 110)
                && (actualDays == null || actualDays == days);
    }

    static BigDecimal usdQuotedEps(SecFinancialStandard annual) {
        if (!validPeriod(annual, true)) {
            return null;
        }
        String unit = normalized(annual.getEpsUnit());
        boolean quoted = "USD/QUOTED-SECURITY".equals(unit);
        boolean domesticShare = "USD/SHARES".equals(unit)
                && ("10-K".equals(annual.getForm()) || "10-K/A".equals(annual.getForm()));
        // Foreign-filer ordinary-share EPS needs explicit quoted-security normalization upstream.
        return quoted || domesticShare ? annual.getEpsDiluted() : null;
    }

    private static int quarterNumber(SecFinancialStandard row) {
        if (row == null || row.getFiscalPeriod() == null || !row.getFiscalPeriod().matches("Q[1-4]")) {
            return 0;
        }
        return row.getFiscalPeriod().charAt(1) - '0';
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
