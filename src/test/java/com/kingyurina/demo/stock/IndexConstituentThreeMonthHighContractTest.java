package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class IndexConstituentThreeMonthHighContractTest {

    @Test
    void marketQueriesProjectOneLatestDateAnchoredThreeMonthHigh() throws IOException {
        String xml = resource("mapper/stock/IndexConstituentMapper.xml");
        String marketRows = selectBody(xml, "findMarketRows");
        String marketRowsBySymbols = selectBody(xml, "findMarketRowsBySymbols");

        assertTrue(xml.contains("MAX(trade_date) AS latest_trade_date"));
        assertTrue(xml.contains("DATE_SUB(date_window.latest_trade_date, INTERVAL 3 MONTH)"));
        assertTrue(xml.contains("AND date_window.latest_trade_date"));
        assertFalse(xml.contains(") window"));
        assertTrue(xml.contains("MAX(c.high_price) AS three_month_high"));
        assertTrue(xml.contains("GROUP BY c.symbol"));
        assertTrue(xml.contains("highs.three_month_high AS threeMonthHigh"));
        assertEquals(2, occurrences(xml, "highs.three_month_high AS threeMonthHigh"));
        assertTrue(xml.contains("<sql id=\"threeMonthHighBySymbol\">"));
        assertEquals(1, occurrences(marketRows, "<include refid=\"threeMonthHighBySymbol\"/>"));
        assertEquals(1, occurrences(marketRowsBySymbols, "<include refid=\"threeMonthHighBySymbol\"/>"));
        assertTrue(marketRows.contains("ON highs.symbol = i.symbol"));
        assertTrue(marketRowsBySymbols.contains("ON highs.symbol = p.symbol"));
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

    private static String selectBody(String xml, String id) {
        String openingTag = "<select id=\"" + id + "\"";
        int start = xml.indexOf(openingTag);
        int end = xml.indexOf("</select>", start);

        assertTrue(start >= 0, () -> "Missing select " + id);
        assertTrue(end >= 0, () -> "Missing closing select tag for " + id);
        return xml.substring(start, end);
    }
}
