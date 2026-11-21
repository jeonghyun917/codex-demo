package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.PreparedStatement;
import java.time.LocalDate;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class EvaluationWindowPersistenceTest {
    @Test
    void unavailableFinancialMetricsBindAsSqlNullNotNonFiniteDoubles() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/stock/StockBacktestMapper.xml";
        try (var stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var metrics = new ExpectedReturnMetrics(100, Double.NaN, 0, Double.NaN, 0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
        var portfolio = new ExpectedReturnPortfolioEvaluator(20).evaluate(java.util.List.of(), 10);
        var window = new ExpectedReturnEvaluationWindowResult(1L, "2024-01", LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31), "MODEL", "ELIGIBLE", 1000, 100, metrics, portfolio);
        var statement = configuration.getMappedStatement("com.kingyurina.demo.stock.StockBacktestMapper.insertExpectedReturnEvaluationWindow");
        var sql = statement.getBoundSql(window);
        PreparedStatement jdbc = mock(PreparedStatement.class);
        doAnswer(invocation -> {
            assertTrue(Double.isFinite(invocation.getArgument(1, Double.class)), "JDBC must not receive NaN/Infinity");
            return null;
        }).when(jdbc).setDouble(anyInt(), anyDouble());
        new DefaultParameterHandler(statement, window, sql).setParameters(jdbc);
        verify(jdbc, atLeastOnce()).setNull(anyInt(), anyInt());
    }
}
