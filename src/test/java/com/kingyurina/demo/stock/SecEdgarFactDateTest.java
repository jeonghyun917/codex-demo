package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.ObjectMapper;

class SecEdgarFactDateTest {

    private static final String COMPANY_FACTS = """
            {"facts":{"us-gaap":{
              "Assets":{"units":{"USD":[{
                "end":"2024-12-31","val":500,"accn":"0000000123-25-000001",
                "fy":2024,"fp":"FY","form":"10-K","filed":"2025-01-30"
              }]}},
              "Revenues":{"units":{"USD":[{
                "start":"2024-01-01","end":"2024-12-31","val":1000,
                "accn":"0000000123-25-000001","fy":2024,"fp":"FY",
                "form":"10-K","filed":"2025-01-30"
              }]}}
            }}}
            """;

    @Test
    void absentInstantStartKeepsLegacyStoredKeyAndOriginalRawEvidence() throws Exception {
        var asset = parseFacts().stream().filter(fact -> "Assets".equals(fact.getConcept())).findFirst().orElseThrow();

        assertEquals(LocalDate.of(2024, 12, 31), asset.getStartDate());
        assertEquals(LocalDate.of(2024, 12, 31), asset.getEndDate());
        assertTrue(new ObjectMapper().readTree(asset.getRawJson()).path("start").isMissingNode());
    }

    @Test
    void explicitDurationStartAndEndRemainUnchanged() throws Exception {
        var revenue = parseFacts().stream().filter(fact -> "Revenues".equals(fact.getConcept())).findFirst().orElseThrow();

        assertEquals(LocalDate.of(2024, 1, 1), revenue.getStartDate());
        assertEquals(LocalDate.of(2024, 12, 31), revenue.getEndDate());
    }

    @Test
    void parsedInstantFactRetainsItsBalanceSheetValueAfterStandardization() throws Exception {
        var facts = parseFacts();
        var asset = facts.stream().filter(fact -> "Assets".equals(fact.getConcept())).findFirst().orElseThrow();
        var originalRaw = asset.getRawJson();
        var rows = standardize(facts);

        assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals(new BigDecimal("500"), row.getAssets());
        assertEquals(new BigDecimal("1000"), row.getRevenue());
        assertEquals(366, row.getPeriodDays());
        assertTrue(new ObjectMapper().readTree(row.getRawJson()).at("/selectedFieldEvidence/assets/startDate").isNull());
        assertEquals(LocalDate.of(2024, 12, 31), asset.getStartDate());
        assertEquals(originalRaw, asset.getRawJson());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "not-json", "null", "[]", "{}",
            "{\"start\":null,\"end\":\"2024-12-31\"}",
            "{\"start\":\"2024-01-01\",\"end\":\"2024-12-31\"}",
            "{\"start\":\"2024-12-31\",\"end\":\"2024-12-31\"}",
            "{\"end\":\"2023-12-31\"}"
    })
    void normalizedStartCannotBecomeInstantWithoutMatchingOriginalSourceProof(String rawJson) throws Exception {
        var facts = parseFacts();
        var asset = facts.stream().filter(fact -> "Assets".equals(fact.getConcept())).findFirst().orElseThrow();
        asset.setRawJson(rawJson);

        assertNull(standardize(facts).getFirst().getAssets());
    }

    @Test
    void genuineDurationCannotBeSelectedAsInstantEvenWhenRawStartIsMissing() throws Exception {
        var facts = parseFacts();
        var asset = facts.stream().filter(fact -> "Assets".equals(fact.getConcept())).findFirst().orElseThrow();
        asset.setStartDate(LocalDate.of(2024, 1, 1));

        var row = standardize(facts).getFirst();
        assertNull(row.getAssets());
        assertEquals(LocalDate.of(2024, 1, 1), asset.getStartDate());
        assertEquals(new BigDecimal("1000"), row.getRevenue());
        assertEquals(366, row.getPeriodDays());
    }

    @SuppressWarnings("unchecked")
    private static List<SecFinancialStandard> standardize(List<SecCompanyFact> facts) throws Exception {
        var standardizer = new SecFinancialStandardService(null, null, new ObjectMapper());
        var method = SecFinancialStandardService.class.getDeclaredMethod("standardize", String.class, String.class, List.class);
        method.setAccessible(true);
        return (List<SecFinancialStandard>) method.invoke(standardizer, "TEST", "0000000123", facts);
    }

    @SuppressWarnings("unchecked")
    private static List<SecCompanyFact> parseFacts() throws Exception {
        var properties = new SecEdgarProperties("https://unused.invalid", "https://unused.invalid", "test", 168, 80, 0, false);
        var service = new SecEdgarService(properties, null, null, new ObjectMapper(), null, null, null);
        var method = SecEdgarService.class.getDeclaredMethod("parseFacts", String.class, String.class, String.class);
        method.setAccessible(true);
        return (List<SecCompanyFact>) method.invoke(service, "TEST", "0000000123", COMPANY_FACTS);
    }
}
