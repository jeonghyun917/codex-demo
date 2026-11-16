package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.ObjectMapper;

class StockReconciledBenchmarkTest {
    @Test void recalculatesReturnsFromCorrectedPricesWithoutRebuildingShareOrMembershipHistory() {
        LocalDate start=LocalDate.of(2025,1,2), end=start.plusDays(1);
        var snapshots=new ArrayList<StockMarketSnapshot>();
        for(int i=0;i<30;i++) for(int day=0;day<2;day++) {
            var row=new StockMarketSnapshot(); row.setIndexCode("SP500"); row.setSymbol("S"+i);
            row.setSnapshotDate(start.plusDays(day)); row.setClosePrice(BigDecimal.valueOf(day==0?10:11));
            row.setMarketCapUsd(BigDecimal.valueOf(100)); row.setCurrency("USD"); snapshots.add(row);
        }
        var saved=new ArrayList<StockBenchmarkReturn>();
        var mapper=(StockBacktestMapper)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{StockBacktestMapper.class},(p,m,args)->{
            if(m.getName().equals("findMarketSnapshots")) {
                assertEquals(List.of("SP500",start,end),List.of(args)); return snapshots;
            }
            if(m.getName().equals("upsertBenchmarkReturn")) { saved.add((StockBenchmarkReturn)args[0]); return null; }
            throw new AssertionError("Unexpected unrelated write/read: "+m.getName());
        });
        var beans=new StaticListableBeanFactory(); beans.addBean("mapper",mapper);
        var service=new StockPortfolioBacktestService(beans.getBeanProvider(StockBacktestMapper.class),new ObjectMapper(),0);
        assertEquals(1,service.rebuildBenchmarkReturns("SP500",start,end));
        assertEquals(1,saved.size()); assertEquals(end,saved.getFirst().getTradeDate());
        assertEquals(10,saved.getFirst().getReturnPct().doubleValue(),0.000001);
        assertEquals(1100,saved.getFirst().getBenchmarkLevel().doubleValue(),0.000001);
        assertEquals(30,saved.getFirst().getCoverageCount());
    }
}
