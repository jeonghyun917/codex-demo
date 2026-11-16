package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.ObjectMapper;

class SecEdgarHistoryRetentionTest {
    private static String source() {
        var values=new ArrayList<Map<String,Object>>();
        values.add(Map.of("start","2015-01-01","end","2015-12-31","val",3.82,"accn","old-2016","fy",2015,"fp","FY","form","10-K","filed","2016-02-01"));
        for(int i=0;i<90;i++)values.add(Map.of("start","2024-01-01","end","2024-12-31","val",2,"accn","new-"+i,"fy",2024,"fp","FY","form","10-K","filed","2025-02-01"));
        return new ObjectMapper().writeValueAsString(Map.of("facts",Map.of("us-gaap",Map.of("EarningsPerShareDiluted",Map.of("units",Map.of("USD/shares",values))))));
    }
    @SuppressWarnings("unchecked")
    private static List<SecCompanyFact> parse(int cap)throws Exception {
        var properties=new SecEdgarProperties("https://unused.invalid","https://unused.invalid","test",168,cap,0,false);
        var service=new SecEdgarService(properties,null,null,new ObjectMapper(),null,null,null);
        Method method=SecEdgarService.class.getDeclaredMethod("parseFacts",String.class,String.class,String.class);method.setAccessible(true);
        return (List<SecCompanyFact>)method.invoke(service,"TEST","123",source());
    }
    @Test void completeHistoryKeepsOldEpsBeyondRepeatedComparatives()throws Exception {
        var facts=parse(0);assertEquals(91,facts.size());
        assertEquals("3.82",facts.stream().filter(f->f.getAccessionNumber().equals("old-2016")).findFirst().orElseThrow().getValue().toPlainString());
    }
    @Test void explicitPositiveCapRemainsAnOptInLossyLimit()throws Exception {assertEquals(80,parse(80).size());}
}
