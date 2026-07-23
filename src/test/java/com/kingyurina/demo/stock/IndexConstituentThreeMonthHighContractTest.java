package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class IndexConstituentThreeMonthHighContractTest {

    @Test
    void marketQueriesProjectOneLatestDateAnchoredThreeMonthHigh() throws IOException {
        String xml = resource("mapper/stock/IndexConstituentMapper.xml");

        assertTrue(xml.contains("MAX(trade_date) AS latest_trade_date"));
        assertTrue(xml.contains("DATE_SUB(window.latest_trade_date, INTERVAL 3 MONTH)"));
        assertTrue(xml.contains("MAX(c.high_price) AS three_month_high"));
        assertTrue(xml.contains("highs.three_month_high AS threeMonthHigh"));
        assertEquals(2, occurrences(xml, "highs.three_month_high AS threeMonthHigh"));
        assertEquals(2, occurrences(xml, "<include refid=\"threeMonthHighBySymbol\"/>"));
        assertTrue(xml.contains("<sql id=\"threeMonthHighBySymbol\">"));
    }

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }
}
