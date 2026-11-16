package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SecFinancialCoherenceTest {
    @Test
    void rebuildingPersistedHistoryRetainsEachOriginalIssuerCik() throws Exception {
        var old = fact("Revenues", "USD", "2024-01-01", "2024-12-31", "1000");
        var successor = fact("Revenues", "USD", "2024-01-01", "2024-12-31", "1200");
        successor.setCik("456"); successor.setAccessionNumber("successor-accession");
        var rows = standardize(List.of(old, successor));
        assertEquals(java.util.Set.of("123", "456"), rows.stream().map(SecFinancialStandard::getCik).collect(java.util.stream.Collectors.toSet()));
    }
    @Test
    void mixedCurrenciesCannotProduceOneRowOfMislabelledIncomeAndAssets() throws Exception {
        var rows = standardize(List.of(
                fact("Revenues", "CNY", "2024-01-01", "2024-12-31", "1000"),
                fact("NetIncomeLoss", "USD", "2024-01-01", "2024-12-31", "100"),
                fact("Assets", "USD", null, "2024-12-31", "500"),
                fact("StockholdersEquity", "CNY", null, "2024-12-31", "400")));
        assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals("CNY", row.getCurrency());
        assertNull(row.getNetIncome()); assertNull(row.getAssets());
        assertEquals(new BigDecimal("400"), row.getEquity());
    }

    @Test
    void coherentCurrencyAlternativeWinsInsteadOfBlanketDroppingThatField() throws Exception {
        var facts = new ArrayList<>(List.of(
                fact("Revenues", "CNY", "2024-01-01", "2024-12-31", "1000"),
                fact("NetIncomeLoss", "USD", "2024-01-01", "2024-12-31", "100"),
                fact("NetIncomeLossAvailableToCommonStockholdersBasic", "CNY", "2024-01-01", "2024-12-31", "80"),
                fact("Assets", "CNY", null, "2024-12-31", "500"),
                fact("Assets", "USD", null, "2024-12-31", "70")));
        var row = standardize(facts).getFirst();
        assertEquals(new BigDecimal("80"), row.getNetIncome());
        assertEquals(new BigDecimal("500"), row.getAssets());
        Collections.reverse(facts);
        var reversed = standardize(facts).getFirst();
        assertEquals(row.getNetIncome(), reversed.getNetIncome());
        assertEquals(row.getAssets(), reversed.getAssets());
    }

    @Test
    void annualRevenueCannotLendItsDurationToYtdIncomeCashFlowOrEps() throws Exception {
        var row = standardize(List.of(
                fact("Revenues", "USD", "2024-01-01", "2024-12-31", "1000"),
                fact("NetIncomeLoss", "USD", "2024-04-01", "2024-12-31", "100"),
                fact("NetCashProvidedByUsedInOperatingActivities", "USD", "2024-07-01", "2024-12-31", "80"),
                fact("EarningsPerShareDiluted", "USD/shares", "2024-10-01", "2024-12-31", "2"))).getFirst();
        assertEquals(366, row.getPeriodDays());
        assertNull(row.getNetIncome()); assertNull(row.getOperatingCashFlow()); assertNull(row.getEpsDiluted());
    }

    @Test
    void sameFrequencyButDifferentExactStartCannotBorrowReferencePeriod() throws Exception {
        var row = standardize(List.of(
                fact("Revenues", "USD", "2024-01-01", "2024-12-31", "1000"),
                fact("NetIncomeLoss", "USD", "2024-01-02", "2024-12-31", "100"),
                fact("NetIncomeLossAvailableToCommonStockholdersBasic", "USD", "2024-01-01", "2024-12-31", "80"))).getFirst();
        assertEquals(new BigDecimal("80"), row.getNetIncome());
    }

    @Test
    void validAnnualAlternateIsNotDisplacedByPreferredConceptWithNineMonthDuration() throws Exception {
        var row = standardize(List.of(
                fact("Revenues", "USD", "2024-04-01", "2024-12-31", "900"),
                fact("RevenueFromContractWithCustomerExcludingAssessedTax", "USD", "2024-01-01", "2024-12-31", "1200"),
                fact("NetIncomeLoss", "USD", "2024-01-01", "2024-12-31", "100"))).getFirst();
        assertEquals(new BigDecimal("1200"), row.getRevenue());
        assertEquals(366, row.getPeriodDays());
    }

    @Test
    void quarterRevenueCannotLendItsDurationToCumulativeEarnings() throws Exception {
        var revenue = fact("Revenues", "USD", "2025-04-01", "2025-06-30", "300");
        var income = fact("NetIncomeLoss", "USD", "2025-01-01", "2025-06-30", "100");
        revenue.setFiscalPeriod("Q2"); income.setFiscalPeriod("Q2");
        var row = standardize(List.of(revenue, income)).getFirst();
        assertEquals(91, row.getPeriodDays()); assertNull(row.getNetIncome());
    }

    @Test
    void coherentDomesticUsdAmountsAndEpsRetainTheirValuesAndExplicitFieldEvidence() throws Exception {
        var row = standardize(List.of(
                fact("Revenues", "USD", "2024-01-01", "2024-12-31", "1000"),
                fact("NetIncomeLoss", "USD", "2024-01-01", "2024-12-31", "100"),
                fact("Assets", "USD", null, "2024-12-31", "500"),
                fact("EarningsPerShareDiluted", "USD/shares", "2024-01-01", "2024-12-31", "2"))).getFirst();
        assertEquals(new BigDecimal("1000"), row.getRevenue());
        assertEquals(new BigDecimal("100"), row.getNetIncome());
        assertEquals(new BigDecimal("500"), row.getAssets());
        assertEquals(new BigDecimal("2"), row.getEpsDiluted()); assertEquals("USD/shares", row.getEpsUnit());
        var raw = new ObjectMapper().readTree(row.getRawJson());
        assertEquals("USD", raw.at("/selectedFieldEvidence/netIncome/unit").asText());
        assertEquals("2024-01-01", raw.at("/selectedFieldEvidence/netIncome/startDate").asText());
        assertEquals("2024-12-31", raw.at("/selectedFieldEvidence/netIncome/endDate").asText());
        assertEquals(366, raw.at("/selectedFieldEvidence/netIncome/periodDays").asInt());
        assertEquals("USD/shares", raw.at("/selectedFieldEvidence/epsDiluted/unit").asText());
        assertTrue(raw.at("/selectedFieldEvidence/assets/startDate").isNull());
    }

    @SuppressWarnings("unchecked")
    private static List<SecFinancialStandard> standardize(List<SecCompanyFact> facts) throws Exception {
        var service = new SecFinancialStandardService(null, null, new ObjectMapper());
        var method = SecFinancialStandardService.class.getDeclaredMethod("standardize", String.class, String.class, List.class);
        method.setAccessible(true);
        return (List<SecFinancialStandard>) method.invoke(service, "TEST", "123", facts);
    }

    private static SecCompanyFact fact(String concept, String unit, String start, String end, String value) {
        var fact = new SecCompanyFact(); fact.setSymbol("TEST"); fact.setCik("123");
        fact.setFiscalYear(LocalDate.parse(end).getYear()); fact.setFiscalPeriod("FY"); fact.setForm("10-K");
        fact.setFiledAt(LocalDate.parse(end).plusDays(30)); fact.setAccessionNumber("test-accession");
        fact.setConcept(concept); fact.setUnit(unit); fact.setValue(new BigDecimal(value));
        fact.setStartDate(start == null ? null : LocalDate.parse(start)); fact.setEndDate(LocalDate.parse(end));
        return fact;
    }
}
