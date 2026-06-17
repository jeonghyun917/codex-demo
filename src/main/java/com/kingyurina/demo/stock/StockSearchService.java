package com.kingyurina.demo.stock;

import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockSearchService {

    private static final Map<String, String> KOREAN_ALIASES = Map.ofEntries(
            Map.entry("인텔", "INTC"),
            Map.entry("엔비디아", "NVDA"),
            Map.entry("애플", "AAPL"),
            Map.entry("마이크로소프트", "MSFT"),
            Map.entry("마소", "MSFT"),
            Map.entry("구글", "GOOGL"),
            Map.entry("알파벳", "GOOGL"),
            Map.entry("아마존", "AMZN"),
            Map.entry("테슬라", "TSLA"),
            Map.entry("메타", "META"),
            Map.entry("브로드컴", "AVGO"),
            Map.entry("월마트", "WMT"));

    private final ObjectProvider<StockSymbolMapper> stockSymbolMapper;

    public StockSearchService(ObjectProvider<StockSymbolMapper> stockSymbolMapper) {
        this.stockSymbolMapper = stockSymbolMapper;
    }

    public String resolveSymbol(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return "";
        }

        String alias = KOREAN_ALIASES.get(normalized.toLowerCase(Locale.ROOT));
        if (alias != null) {
            return alias;
        }

        StockSymbolMapper mapper = stockSymbolMapper.getIfAvailable();
        if (mapper != null) {
            String resolved = mapper.findSymbolByQuery(normalized);
            if (resolved != null && !resolved.isBlank()) {
                return resolved.toUpperCase(Locale.ROOT);
            }
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public boolean isDifferentSymbol(String query, String symbol) {
        return query != null && symbol != null && !query.trim().equalsIgnoreCase(symbol);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
