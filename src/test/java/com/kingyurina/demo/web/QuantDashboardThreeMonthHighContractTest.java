package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class QuantDashboardThreeMonthHighContractTest {

    @Test
    void quantTableReplacesFourFactorColumnsWithSortableThreeMonthHighRatio() throws IOException {
        String template = resource("templates/dashboard.html");

        assertFalse(template.contains("data-sort=\"valuation\""));
        assertFalse(template.contains("data-sort=\"growth\""));
        assertFalse(template.contains("data-sort=\"momentum\""));
        assertFalse(template.contains("data-sort=\"risk\""));
        assertFalse(template.contains("row.valuationScore"));
        assertFalse(template.contains("row.growthScore"));
        assertFalse(template.contains("row.momentumScore"));
        assertFalse(template.contains("row.riskScore"));
        assertTrue(template.contains("data-sort=\"threeMonthHighRatio\""));
        assertTrue(template.contains("3M 고점비율"));
        assertTrue(template.contains("row.threeMonthHighRatio"));
        assertTrue(template.contains("row.threeMonthHighRatioTone"));
        assertTrue(template.contains("threeMonthHighRatio: 10"));
    }

    @Test
    void quantTableDefinesElevenColumnsAndRatioTones() throws IOException {
        String css = resource("static/css/main.css");

        assertTrue(css.contains(".three-month-high-ratio"));
        assertTrue(css.contains(".three-month-high-ratio.near-high"));
        assertTrue(css.contains(".three-month-high-ratio.mid-range"));
        assertTrue(css.contains(".three-month-high-ratio.extended"));
        assertTrue(css.contains("min-width: 1080px"));
    }

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
