package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class StockMacroVintageBatchDefaultsTest {

    private static final String REQUIRED_MACRO_SERIES =
            "DGS3MO,DGS10,FEDFUNDS,CPIAUCSL,UNRATE,VIXCLS,DTWEXBGS";
    private static final List<String> REQUIRED_MACRO_SERIES_LIST =
            List.of("DGS3MO", "DGS10", "FEDFUNDS", "CPIAUCSL", "UNRATE", "VIXCLS", "DTWEXBGS");

    @Test
    void defaultMacroVintageSeriesIncludesAllInputsUsedByMacroFeatures() throws Exception {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String fredBatchScript = Files.readString(Path.of("run-fred-macro-vintage-batch.cmd"));
        String batchRunner = Files.readString(
                Path.of("src/main/java/com/kingyurina/demo/stock/StockMacroVintageBatchRunner.java"));

        assertTrue(applicationYaml.contains("series-codes: " + REQUIRED_MACRO_SERIES),
                () -> "application.yml macro-vintage default must include " + REQUIRED_MACRO_SERIES);
        assertTrue(fredBatchScript.contains("set \"FRED_SERIES=" + REQUIRED_MACRO_SERIES + "\""),
                () -> "run-fred-macro-vintage-batch.cmd default must include " + REQUIRED_MACRO_SERIES);
        assertTrue(batchRunner.contains("app.batch.macro-vintage.series-codes:" + REQUIRED_MACRO_SERIES),
                () -> "StockMacroVintageBatchRunner @Value fallback must include " + REQUIRED_MACRO_SERIES);
    }

    @Test
    void blankMacroVintageSeriesFallsBackToFullMacroFeatureSeriesSet() throws Exception {
        StockMacroVintageBatchRunner runner = new StockMacroVintageBatchRunner(
                false,
                false,
                false,
                "SP500",
                " ",
                "",
                "",
                5,
                "",
                "",
                null,
                null,
                new ObjectMapper());

        assertEquals(REQUIRED_MACRO_SERIES_LIST, seriesCodesFrom(runner));
    }

    @SuppressWarnings("unchecked")
    private static List<String> seriesCodesFrom(StockMacroVintageBatchRunner runner) throws Exception {
        Field field = StockMacroVintageBatchRunner.class.getDeclaredField("seriesCodes");
        field.setAccessible(true);
        return (List<String>) field.get(runner);
    }
}
