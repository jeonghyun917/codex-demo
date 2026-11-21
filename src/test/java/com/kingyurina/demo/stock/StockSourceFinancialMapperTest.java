package com.kingyurina.demo.stock;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

class StockSourceFinancialMapperTest {
    @Test void realMapperRetainsPublicationAndUtcAdmissionSeparatelyAndScopesVersion() throws Exception {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var c=source.getConnection();var s=c.createStatement()){
            s.execute("CREATE TABLE stock_source_financial_document(pack_version VARCHAR(80),identity_sha256 CHAR(64),symbol VARCHAR(20),publication_date DATE,admitted_at_utc TIMESTAMP,payload_json CLOB)");
            s.execute("INSERT INTO stock_source_financial_document VALUES('"+StockSourceFinancialService.VERSION+"','"+"a".repeat(64)+"','AMGN','2014-01-28','2026-11-05 12:01:02','{}')");
        }
        var config=new Configuration(new Environment("source-test",new JdbcTransactionFactory(),source));config.setMapUnderscoreToCamelCase(true);
        String name="mapper/stock/StockSourceFinancialMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(name)){new XMLMapperBuilder(input,config,name,config.getSqlFragments()).parse();}
        try(var session=new SqlSessionFactoryBuilder().build(config).openSession()){
            var mapper=session.getMapper(StockSourceFinancialMapper.class);var rows=mapper.findBySymbol("AMGN",StockSourceFinancialService.VERSION);
            assertEquals(1,rows.size());assertEquals(LocalDate.parse("2014-01-28"),rows.getFirst().publicationDate());
            assertEquals(LocalDateTime.parse("2026-11-05T12:01:02"),rows.getFirst().admittedAtUtc());
            assertTrue(mapper.findBySymbol("AMGN","other-version").isEmpty());assertTrue(mapper.findBySymbol("GS",StockSourceFinancialService.VERSION).isEmpty());
        }
    }
}
