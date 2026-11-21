package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.ObjectMapper;

class SecNativeIfrsIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void nativeAnnualValuesUseExactConceptsOwnerPreferenceAndSourceUnits() throws Exception {
        // Literal values reconciled to GFS 0001709048-25-000024, not US-GAAP aliases.
        var rows = standardize(List.of(
                fact("Revenue", "USD", "2024-01-01", "2024-12-31", "6750000000", "CY2024"),
                fact("ProfitLossFromOperatingActivities", "USD", "2024-01-01", "2024-12-31", "-214000000", "CY2024"),
                fact("ProfitLoss", "USD", "2024-01-01", "2024-12-31", "-262000000", "CY2024"),
                fact("ProfitLossAttributableToOwnersOfParent", "USD", "2024-01-01", "2024-12-31", "-265000000", "CY2024"),
                fact("DilutedEarningsLossPerShare", "USD/shares", "2024-01-01", "2024-12-31", "-0.48", "CY2024"),
                fact("Assets", "USD", null, "2024-12-31", "16799000000", "CY2024Q4I"),
                fact("Liabilities", "USD", null, "2024-12-31", "5975000000", "CY2024Q4I"),
                fact("Equity", "USD", null, "2024-12-31", "10824000000", "CY2024Q4I"),
                fact("EquityAttributableToOwnersOfParent", "USD", null, "2024-12-31", "10776000000", "CY2024Q4I"),
                fact("CashFlowsFromUsedInOperatingActivities", "USD", "2024-01-01", "2024-12-31", "1722000000", "CY2024")));
        assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals(new BigDecimal("6750000000"), row.getRevenue());
        assertEquals(new BigDecimal("-214000000"), row.getOperatingIncome());
        assertEquals(new BigDecimal("-265000000"), row.getNetIncome());
        assertEquals(new BigDecimal("-0.48"), row.getEpsDiluted());
        assertEquals(new BigDecimal("16799000000"), row.getAssets());
        assertEquals(new BigDecimal("5975000000"), row.getLiabilities());
        assertEquals(new BigDecimal("10776000000"), row.getEquity());
        assertEquals(new BigDecimal("1722000000"), row.getOperatingCashFlow());
        assertEquals("USD/shares", row.getEpsUnit());
        assertNull(FinancialInputPolicy.usdQuotedEps(row)); // No inferred ADS/quoted-share conversion.
        assertEquals("ifrs-full", JSON.readTree(row.getRawJson()).at("/selectedFieldEvidence/revenue/taxonomy").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"6-K", "6-K/A"})
    void parserIncludesNativeIfrsInDefaultBoundedModeAndRetainsInstantKey(String form) throws Exception {
        String json = """
                {"facts":{"ifrs-full":{
                  "Revenue":{"units":{"USD":[{"start":"2025-07-01","end":"2025-09-30","val":1688000000,
                    "accn":"0001709048-25-000071","fy":null,"fp":null,"form":"%s","filed":"2025-11-12","frame":"CY2025Q3"}]}},
                  "Assets":{"units":{"USD":[{"end":"2025-09-30","val":16708000000,
                    "accn":"0001709048-25-000071","fy":null,"fp":null,"form":"%s","filed":"2025-11-12","frame":"CY2025Q3I"}]}}
                }}}
                """.formatted(form, form);
        var facts = parse(json);
        assertEquals(2, facts.size());
        assertTrue(facts.stream().allMatch(f -> Integer.valueOf(2025).equals(f.getFiscalYear()) && "Q3".equals(f.getFiscalPeriod())));
        assertTrue(parse(json).stream().allMatch(f -> Integer.valueOf(2025).equals(f.getFiscalYear()) && "Q3".equals(f.getFiscalPeriod())));
        var asset = facts.stream().filter(f -> "Assets".equals(f.getConcept())).findFirst().orElseThrow();
        assertEquals(asset.getEndDate(), asset.getStartDate());
        assertFalse(JSON.readTree(asset.getRawJson()).has("start"));
        assertTrue(JSON.readTree(asset.getRawJson()).path("fy").isNull());
        assertTrue(JSON.readTree(asset.getRawJson()).path("fp").isNull());
        var row = standardize(facts).getFirst();
        assertEquals("Q3", row.getFiscalPeriod()); assertEquals(2025, row.getFiscalYear());
        assertEquals(92, row.getPeriodDays()); assertEquals(new BigDecimal("16708000000"), row.getAssets());
        assertTrue(JSON.readTree(row.getRawJson()).at("/selectedFieldEvidence/revenue/sourceFiscalYear").isNull());
        assertTrue(JSON.readTree(row.getRawJson()).at("/selectedFieldEvidence/revenue/sourceFiscalPeriod").isNull());
    }

    @Test
    void unknownSixKPeriodsRemainArchiveOnlyRatherThanPersistingNullableNaturalKeys() throws Exception {
        String json = """
                {"facts":{"ifrs-full":{"Revenue":{"units":{"USD":[
                  {"start":"2025-01-01","end":"2025-09-30","val":4961000000,
                    "accn":"0001709048-25-000071","fy":null,"fp":null,"form":"6-K","filed":"2025-11-12"},
                  {"start":"2025-07-01","end":"2025-09-30","val":1688000000,
                    "accn":"0001709048-25-000071","fy":null,"fp":null,"form":"6-K","filed":"2025-11-12"}
                ]}}}}}
                """;
        assertTrue(parse(json).isEmpty());
    }

    @Test
    void comparativeSixKQuarterIgnoresReportingFyFpButKeepsOriginalEvidence() throws Exception {
        var quarter = fact("Revenue", "USD", "2024-07-01", "2024-09-30", "1739000000", "CY2024Q3");
        quarter.setForm("6-K"); quarter.setFiscalYear(2025); quarter.setFiscalPeriod("FY");
        quarter.setFiledAt(LocalDate.of(2025, 11, 12));
        var rows = standardize(List.of(quarter)); assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals("Q3", row.getFiscalPeriod()); assertEquals(2024, row.getFiscalYear());
        assertEquals(92, row.getPeriodDays());
        assertEquals("FY", quarter.getFiscalPeriod()); assertEquals(2025, quarter.getFiscalYear());
        assertEquals("CALENDAR_FRAME_EXACT_DURATION_V1", JSON.readTree(row.getRawJson()).path("periodPolicy").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-01-01", "2025-04-01"})
    void halfAndNineMonthCashFlowNeverBorrowQuarterDuration(String start) throws Exception {
        var quarter = quarter("Revenue", "USD", "1688000000");
        var cumulative = fact("CashFlowsFromUsedInOperatingActivities", "USD", start, "2025-09-30", "1357000000", null);
        cumulative.setForm("6-K"); cumulative.setFiscalYear(null); cumulative.setFiscalPeriod(null);
        var rows = standardize(List.of(quarter, cumulative));
        assertEquals(1, rows.size()); assertEquals(92, rows.getFirst().getPeriodDays());
        assertNull(rows.getFirst().getOperatingCashFlow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CY2025Q2", "CY2024Q3", "CY2025Q3I"})
    void unprovenOrConflictingCalendarFrameCannotTurnSixKIntoQuarter(String frame) throws Exception {
        var quarter = quarter("Revenue", "USD", "1688000000"); quarter.setFrame(frame);
        assertTrue(standardize(List.of(quarter)).isEmpty());
    }

    @Test
    void explicitFrameCannotRelabelOffBoundaryShortDurationAsCalendarQuarter() throws Exception {
        var quarter = quarter("Revenue", "USD", "1"); quarter.setStartDate(LocalDate.of(2025, 7, 2));
        assertTrue(standardize(List.of(quarter)).isEmpty());
    }

    @Test
    void unknownTaxonomyAndNearNameConceptAreNotAliases() throws Exception {
        var unknown = fact("Revenues", "USD", "2024-01-01", "2024-12-31", "1", "CY2024");
        unknown.setTaxonomy("issuer-extension");
        var guessed = fact("AdjustedRevenue", "USD", "2024-01-01", "2024-12-31", "2", "CY2024");
        assertTrue(standardize(List.of(unknown, guessed)).isEmpty());
    }

    @Test
    void foreignCurrencyRemainsOriginalAndMixedEpsIsExcluded() throws Exception {
        var revenue = quarter("Revenue", "GBP", "100");
        var eps = quarter("DilutedEarningsLossPerShare", "USD/shares", "1.23");
        var rows = standardize(List.of(revenue, eps)); assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals("GBP", row.getCurrency()); assertEquals(new BigDecimal("100"), row.getRevenue());
        assertNull(row.getEpsDiluted());
    }

    @Test
    void instantOnlySixKCannotAcquireDurationFromMetadata() throws Exception {
        var instant = fact("Assets", "USD", null, "2025-09-30", "16708000000", "CY2025Q3I");
        instant.setForm("6-K"); instant.setFiscalPeriod("Q3"); instant.setFiscalYear(2025);
        assertTrue(standardize(List.of(instant)).isEmpty());
    }

    private static SecCompanyFact quarter(String concept, String unit, String value) {
        var fact = fact(concept, unit, "2025-07-01", "2025-09-30", value, "CY2025Q3");
        fact.setForm("6-K"); fact.setFiscalYear(null); fact.setFiscalPeriod(null);
        return fact;
    }

    private static SecCompanyFact fact(String concept, String unit, String start, String end, String value, String frame) {
        var fact = new SecCompanyFact(); fact.setSymbol("GFS"); fact.setCik("0001709048");
        fact.setTaxonomy("ifrs-full"); fact.setConcept(concept); fact.setUnit(unit); fact.setValue(new BigDecimal(value));
        fact.setStartDate(start == null ? LocalDate.parse(end) : LocalDate.parse(start)); fact.setEndDate(LocalDate.parse(end));
        fact.setFiscalYear(2024); fact.setFiscalPeriod("FY"); fact.setForm("20-F");
        fact.setFiledAt(LocalDate.of(2025, 11, 12)); fact.setAccessionNumber("test-accession"); fact.setFrame(frame);
        fact.setRawJson(start == null ? "{\"end\":\"" + end + "\"}" : "{\"start\":\"" + start + "\",\"end\":\"" + end + "\"}");
        return fact;
    }

    @SuppressWarnings("unchecked")
    private static List<SecCompanyFact> parse(String json) throws Exception {
        var props = new SecEdgarProperties("https://unused.invalid", "https://unused.invalid", "test", 168, 1000, 0, false);
        var service = new SecEdgarService(props, null, null, JSON, null, null, null);
        var method = SecEdgarService.class.getDeclaredMethod("parseFacts", String.class, String.class, String.class);
        method.setAccessible(true);
        return (List<SecCompanyFact>) method.invoke(service, "GFS", "0001709048", json);
    }

    @SuppressWarnings("unchecked")
    private static List<SecFinancialStandard> standardize(List<SecCompanyFact> facts) throws Exception {
        var service = new SecFinancialStandardService(null, null, JSON);
        var method = SecFinancialStandardService.class.getDeclaredMethod("standardize", String.class, String.class, List.class);
        method.setAccessible(true);
        return (List<SecFinancialStandard>) method.invoke(service, "GFS", "0001709048", new ArrayList<>(facts));
    }
}
