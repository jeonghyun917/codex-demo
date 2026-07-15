package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class WalkForwardWindowGeneratorTest {

    @Test
    void contractFixesSp500TwentyDayRules() {
        ExpectedReturnPredictionContract contract = ExpectedReturnPredictionContract.sp500Excess20dV1();

        assertEquals("SP500_EXCESS_20D_V1", contract.version());
        assertEquals("SP500", contract.indexCode());
        assertEquals("EXPECTED_RETURN_V9", contract.baselineModel());
        assertEquals(20, contract.horizonTradingDays());
        assertEquals(3, contract.minimumTrainingYears());
        assertEquals(1000, contract.minimumTrainingRows());
        assertEquals(100, contract.minimumTestRows());
        assertEquals(12, contract.minimumValidMonths());
        assertEquals(70.0d, contract.minimumCoveragePct());
    }

    @Test
    void generatorKeepsSignalDatesTogetherAndEmbargoesUnfinishedLabels() {
        ExpectedReturnPredictionContract contract = ExpectedReturnPredictionContract.sp500Excess20dV1();
        List<WalkForwardWindow> windows = new WalkForwardWindowGenerator(contract).generate(rowsAcrossMonths(50, 100));

        assertFalse(windows.isEmpty());
        assertTrue(windows.stream().anyMatch(window -> window.status() == WalkForwardWindow.Status.ELIGIBLE));
        for (WalkForwardWindow window : windows) {
            assertTrue(window.trainRows().stream()
                    .allMatch(row -> row.labelEndDate().isBefore(window.testFrom())));
            Set<LocalDate> trainDates = signalDates(window.trainRows());
            Set<LocalDate> testDates = signalDates(window.testRows());
            assertTrue(trainDates.stream().noneMatch(testDates::contains));
            assertTrue(window.testRows().stream()
                    .allMatch(row -> !row.signalDate().isBefore(window.testFrom())
                            && !row.signalDate().isAfter(window.testTo())));
        }
    }

    @Test
    void undersizedMonthIsKeptAndMarkedInsufficient() {
        ExpectedReturnPredictionContract contract = ExpectedReturnPredictionContract.sp500Excess20dV1();
        List<ExpectedReturnEvaluationRow> rows = rowsAcrossMonths(40, 30);

        List<WalkForwardWindow> windows = new WalkForwardWindowGenerator(contract).generate(rows);

        assertFalse(windows.isEmpty());
        assertTrue(windows.stream().allMatch(window ->
                window.status() == WalkForwardWindow.Status.INSUFFICIENT_DATA));
    }

    private static Set<LocalDate> signalDates(List<ExpectedReturnEvaluationRow> rows) {
        Set<LocalDate> dates = new HashSet<>();
        rows.forEach(row -> dates.add(row.signalDate()));
        return dates;
    }

    private static List<ExpectedReturnEvaluationRow> rowsAcrossMonths(int months, int rowsPerMonth) {
        List<ExpectedReturnEvaluationRow> rows = new ArrayList<>();
        LocalDate firstMonth = LocalDate.of(2020, 1, 1);
        for (int month = 0; month < months; month++) {
            LocalDate signalDate = firstMonth.plusMonths(month).withDayOfMonth(5);
            LocalDate labelEndDate = signalDate.plusDays(28);
            for (int index = 0; index < rowsPerMonth; index++) {
                rows.add(new ExpectedReturnEvaluationRow(
                        "SP500_EXCESS_20D_V1",
                        "SP500",
                        "EXPECTED_RETURN_V9",
                        signalDate,
                        LocalDateTime.of(signalDate, java.time.LocalTime.NOON),
                        labelEndDate,
                        "S" + index,
                        index / 100.0d,
                        index / 110.0d,
                        55.0d,
                        -5.0d,
                        8.0d,
                        2.0d,
                        1.0d,
                        "Technology",
                        "NEUTRAL"));
            }
        }
        return rows;
    }
}

