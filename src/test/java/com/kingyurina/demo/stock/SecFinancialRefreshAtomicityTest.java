package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.ObjectMapper;

class SecFinancialRefreshAtomicityTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void failedReplacementRestoresOriginalRowsAtEitherPublicEntrypoint(boolean databaseEntry) {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE standards(symbol VARCHAR(20), evidence VARCHAR(40))");jdbc.update("INSERT INTO standards VALUES('TEST','original')");
        var fact=new SecCompanyFact();fact.setSymbol("TEST");fact.setCik("123");fact.setTaxonomy("us-gaap");fact.setConcept("Revenues");fact.setUnit("USD");fact.setFiscalYear(2024);fact.setFiscalPeriod("FY");fact.setForm("10-K");fact.setFiledAt(LocalDate.parse("2025-02-01"));fact.setStartDate(LocalDate.parse("2024-01-01"));fact.setEndDate(LocalDate.parse("2024-12-31"));fact.setAccessionNumber("actual-fixture");fact.setValue(new BigDecimal("1000"));
        var standards=(SecFinancialStandardMapper)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SecFinancialStandardMapper.class},(p,m,a)->{
            if(m.getName().equals("deleteBySymbol")){jdbc.update("DELETE FROM standards WHERE symbol=?",a[0]);return null;}
            if(m.getName().equals("upsertBatch"))throw new IllegalStateException("simulated persistence failure");
            throw new AssertionError("Unexpected standard operation "+m.getName());
        });
        var facts=(SecCompanyFactMapper)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SecCompanyFactMapper.class},(p,m,a)->{
            if(m.getName().equals("findBySymbol"))return List.of(fact);
            throw new AssertionError("Unexpected raw operation "+m.getName());
        });
        var beans=new StaticListableBeanFactory();beans.addBean("facts",facts);beans.addBean("standards",standards);
        var target=new SecFinancialStandardService(beans.getBeanProvider(SecCompanyFactMapper.class),beans.getBeanProvider(SecFinancialStandardMapper.class),new ObjectMapper());
        var advice=new TransactionInterceptor();advice.setTransactionManager(new DataSourceTransactionManager(ds));advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(advice);var service=(SecFinancialStandardService)factory.getProxy();
        assertThrows(IllegalStateException.class,()->{if(databaseEntry)service.rebuildFromDatabase("TEST");else service.rebuildFromFacts("TEST","123",List.of(fact));});
        assertEquals(List.of("original"),jdbc.queryForList("SELECT evidence FROM standards",String.class));
    }
}
