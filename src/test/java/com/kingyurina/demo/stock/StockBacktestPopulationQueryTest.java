package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StockBacktestPopulationQueryTest {
    @Test
    void historicalTrainingDoesNotSelectOnlySurvivingCurrentConstituents() throws Exception {
        String sql = sql("findResults");
        assertFalse(sql.contains("current_member"));
        assertTrue(sql.contains("stock_index_membership_snapshot"));
        assertTrue(sql.contains("snapshot_date = r.signal_date"));
        assertFalse(sql.contains("p.market_cap"), "A current market cap cannot backfill historical weights");
    }

    @ParameterizedTest
    @ValueSource(strings = {"countResults", "countResultsByHorizon", "findSnapshotsNeedingResults",
            "findCovarianceSnapshotDates", "findCovarianceCandidateSymbols", "upsertResult",
            "findResults", "findResultsForEvaluation"})
    void correctedOutcomePathsNeverReadOrOverwriteLegacyNCloseRows(String statement) throws Exception {
        String sql = sql(statement);
        assertTrue(sql.contains("stock_backtest_result_v2"), statement + " must use the corrected interval contract");
        assertFalse(sql.contains("stock_signal_backtest_result"), statement + " must preserve the legacy table");
    }

    private static String sql(String id) throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/stock/StockBacktestMapper.xml";
        try (var input = StockBacktestPopulationQueryTest.class.getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(StockBacktestMapper.class.getName() + "." + id)
                .getBoundSql(Map.of("indexCode", "SP500", "horizonDays", 20, "limit", 100))
                .getSql().replaceAll("\\s+", " ");
    }
}
