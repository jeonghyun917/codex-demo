package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class StockMacroAsOfResearchFeaturesTest {
    private static final LocalDate DECISION = LocalDate.of(2024, 3, 1);
    private static final String INDEX = "SP500_MACRO_FREE_20261018";

    @Test void knownPreviousDayValuesProduceHandCalculatedFeaturesAndScores() {
        var result = StockMacroAsOfResearchFeatures.build(INDEX, DECISION, fixture());
        assertEquals(-1, result.getYieldSpreadPct().doubleValue(), 1e-10);
        assertEquals(10, result.getCpiYoyPct().doubleValue(), 1e-10);
        assertEquals(10, result.getCpiMomPct().doubleValue(), 1e-10);
        assertEquals(0.3, result.getUnemploymentChange3mPct().doubleValue(), 1e-10);
        assertEquals(25, result.getVixChange20dPct().doubleValue(), 1e-10);
        assertEquals(10, result.getDollarChange20dPct().doubleValue(), 1e-10);
        assertEquals(18, result.getMacroTightnessScore());
        assertEquals(0, result.getMacroGrowthStressScore());
        assertEquals(38, result.getMacroRiskScore());
        assertEquals(20, result.getMacroFeatureScore());
        assertFalse(result.getSource().contains("STRICT"));
        assertTrue(result.getSource().contains("ASOF_RESEARCH"));
        assertNull(result.getCalculatedAt(), "DB must record actual insertion time, not a historical availability timestamp");
    }

    @Test void cannotUseCurrentOrAnotherDecisionVintage() {
        var rows = fixture();
        rows.add(row("CPIAUCSL", "2024-02-01", DECISION, "1000"));
        assertThrows(IllegalArgumentException.class, () -> StockMacroAsOfResearchFeatures.build(INDEX, DECISION, rows));
    }

    @Test void rejectsFutureObservationDuplicateAndWrongSource() {
        var future = fixture(); future.add(row("CPIAUCSL", "2024-03-01", DECISION.minusDays(1), "999"));
        assertThrows(IllegalArgumentException.class, () -> StockMacroAsOfResearchFeatures.build(INDEX, DECISION, future));
        var duplicate = fixture(); duplicate.add(duplicate.getFirst());
        assertThrows(IllegalArgumentException.class, () -> StockMacroAsOfResearchFeatures.build(INDEX, DECISION, duplicate));
        var wrong = fixture();
        wrong.set(0, new StockMacroAsOfResearchFeatures.Observation("DGS3MO", DECISION.minusDays(2),
                DECISION.minusDays(1), BigDecimal.ONE, "latest-series"));
        assertThrows(IllegalArgumentException.class, () -> StockMacroAsOfResearchFeatures.build(INDEX, DECISION, wrong));
    }

    @Test void refusesOperatingNamespacesAndIncompleteSeries() {
        assertThrows(IllegalArgumentException.class, () -> StockMacroAsOfResearchFeatures.build("SP500", DECISION, fixture()));
        var rows = fixture(); rows.removeIf(r -> r.seriesCode().equals("VIXCLS"));
        assertThrows(IllegalArgumentException.class, () -> StockMacroAsOfResearchFeatures.build(INDEX, DECISION, rows));
    }

    @Test void missingPriorObservationsStayUnknownRatherThanBecomingZeroGrowth() {
        var rows = fixture(); rows.removeIf(r -> r.seriesCode().equals("CPIAUCSL") && r.observationDate().getYear() == 2023);
        var result = StockMacroAsOfResearchFeatures.build(INDEX, DECISION, rows);
        assertNull(result.getCpiYoyPct());
        assertNotNull(result.getCpiMomPct());
    }

    private static ArrayList<StockMacroAsOfResearchFeatures.Observation> fixture() {
        var rows = new ArrayList<StockMacroAsOfResearchFeatures.Observation>();
        String[][] values = {{"DGS3MO","2024-02-28","5"},{"DGS10","2024-02-28","4"},
                {"FEDFUNDS","2024-02-01","5.25"},{"CPIAUCSL","2024-02-01","110"},
                {"CPIAUCSL","2024-01-01","100"},{"CPIAUCSL","2023-02-01","100"},
                {"UNRATE","2024-02-01","4.1"},{"UNRATE","2023-11-01","3.8"},
                {"VIXCLS","2024-02-28","20"},{"VIXCLS","2024-01-31","16"},
                {"DTWEXBGS","2024-02-28","110"},{"DTWEXBGS","2024-01-31","100"}};
        for (var value : values) rows.add(row(value[0], value[1], DECISION.minusDays(1), value[2]));
        return rows;
    }
    private static StockMacroAsOfResearchFeatures.Observation row(String series, String day, LocalDate asOf, String value) {
        return new StockMacroAsOfResearchFeatures.Observation(series, LocalDate.parse(day), asOf,
                new BigDecimal(value), "alfred-" + series + "-" + asOf);
    }
}
