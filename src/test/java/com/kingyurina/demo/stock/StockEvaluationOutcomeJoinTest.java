package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Connection-local temporary tables: no persistent data or index changes. */
@EnabledIfEnvironmentVariable(named="QUANT_EVIDENCE_DB_TEST", matches="true")
class StockEvaluationOutcomeJoinTest {
    private static final LocalDate DAY=LocalDate.of(2025,1,2);
    private Connection connection;
    private JdbcTemplate jdbc;
    private String sql;
    private Object[] parameters;

    @BeforeEach void setup() throws Exception {
        connection=DriverManager.getConnection("jdbc:mariadb://127.0.0.1:3306/king_yurina","root","");
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
        for(String table:List.of("stock_index_membership_snapshot","stock_signal_snapshot","stock_backtest_result_v2")){
            String ddl=jdbc.queryForObject("SHOW CREATE TABLE "+table,(rs,n)->rs.getString(2));
            jdbc.execute(ddl.replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE")
                    .replaceAll("(?m)^\\s*CONSTRAINT [^\\n]+\\n","").replaceAll(",\\s*\\)",")"));
        }
        var configuration=new Configuration();
        String resource="mapper/stock/StockBacktestMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)){
            new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        var args=Map.of("indexCode","TEST","horizonDays",20,"fromDate",DAY,"toDate",DAY);
        var bound=configuration.getMappedStatement(StockBacktestMapper.class.getName()+".findResultsForEvaluation").getBoundSql(args);
        sql=bound.getSql();
        parameters=bound.getParameterMappings().stream().map(p->args.get(p.getProperty())).toArray();
    }
    @AfterEach void close() throws Exception {if(connection!=null)connection.close();}

    @Test void outcomeLookupUsesIdentityKeyInsteadOfScanningOtherStocksOnSameDate(){
        member("TARGET");signal(1,"TARGET","v1");outcome(1,"TARGET",2);
        var signals=new ArrayList<Object[]>();var outcomes=new ArrayList<Object[]>();
        for(long id=1000;id<4000;id++){
            signals.add(new Object[]{id,"OTHER"+id,DAY});
            outcomes.add(new Object[]{id,"OTHER"+id,DAY,DAY.plusDays(1),DAY.plusDays(21)});
        }
        jdbc.batchUpdate("INSERT INTO stock_signal_snapshot(id,symbol,signal_date) VALUES(?,?,?)",signals);
        jdbc.batchUpdate("INSERT INTO stock_backtest_result_v2(snapshot_id,symbol,signal_date,horizon_days,entry_trade_date,entry_close,exit_trade_date,exit_close,forward_return_pct) VALUES(?,?,?,20,?,100,?,102,2)",outcomes);
        var rows=jdbc.queryForList(sql,parameters);
        assertEquals(1,rows.size());assertEquals("TARGET",rows.getFirst().get("symbol"));
        var plan=jdbc.queryForList("EXPLAIN "+sql,parameters);
        var resultStep=plan.stream().filter(r->"r".equals(r.get("table"))).findFirst().orElseThrow();
        assertEquals("PRIMARY",resultStep.get("key"),"Each source snapshot must use one keyed outcome lookup, not date fan-out");
    }
    @Test void missingSignalsAndMissingOutcomesRemainSinglePopulationRows(){
        member("A");member("B");member("C");signal(1,"A","v1");outcome(1,"A",2);
        signal(2,"C","v1");signal(3,"C","v2");
        var rows=jdbc.queryForList(sql,parameters);
        assertEquals(List.of("A","B","C"),rows.stream().map(r->r.get("symbol")).toList());
        assertNull(rows.get(1).get("snapshotId"));assertNull(rows.get(2).get("snapshotId"));
        assertEquals("FixtureSector",rows.get(2).get("sector"));
    }
    @Test void unusedSignalVersionDoesNotCreateAnAdditionalNullOutcome(){
        member("A");signal(1,"A","v1");signal(2,"A","v2");outcome(1,"A",2);
        var rows=jdbc.queryForList(sql,parameters);
        assertEquals(1,rows.size());assertEquals(1L,((Number)rows.getFirst().get("snapshotId")).longValue());
        assertEquals(2.0,((Number)rows.getFirst().get("forwardReturnPct")).doubleValue());
    }
    @Test void genuineDuplicateOutcomesRemainVisibleForStrictExclusion(){
        member("A");signal(1,"A","v1");signal(2,"A","v2");outcome(1,"A",2);outcome(2,"A",3);
        var rows=jdbc.queryForList(sql,parameters);
        assertEquals(2,rows.size(),"Do not hide duplicate predictions by choosing an arbitrary signal version");
        assertEquals(List.of(2.0,3.0),rows.stream().map(r->((Number)r.get("forwardReturnPct")).doubleValue()).sorted().toList());
    }
    private void member(String symbol){
        jdbc.update("INSERT INTO stock_index_membership_snapshot(index_code,symbol,snapshot_date,sector,source) VALUES('TEST',?,?,'FixtureSector','COMMUNITY_RECONSTRUCTION')",symbol,DAY);
    }
    private void signal(long id,String symbol,String version){
        jdbc.update("INSERT INTO stock_signal_snapshot(id,symbol,signal_date,signal_version) VALUES(?,?,?,?)",id,symbol,DAY,version);
    }
    private void outcome(long id,String symbol,double value){
        jdbc.update("INSERT INTO stock_backtest_result_v2(snapshot_id,symbol,signal_date,horizon_days,entry_trade_date,entry_close,exit_trade_date,exit_close,forward_return_pct) VALUES(?,?,?,20,?,100,?,100+?,?)",id,symbol,DAY,DAY.plusDays(1),DAY.plusDays(21),value,value);
    }
}
