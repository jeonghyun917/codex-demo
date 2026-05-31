package com.kingyurina.demo.stock;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockSignalRefreshService {

    private static final Logger log = LoggerFactory.getLogger(StockSignalRefreshService.class);

    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;
    private final StockSignalService stockSignalService;
    private final StockCacheService stockCacheService;

    public StockSignalRefreshService(ObjectProvider<IndexConstituentMapper> indexConstituentMapper,
            StockSignalService stockSignalService, StockCacheService stockCacheService) {
        this.indexConstituentMapper = indexConstituentMapper;
        this.stockSignalService = stockSignalService;
        this.stockCacheService = stockCacheService;
    }

    public RefreshResult recalculateIndexLatest(String indexCode) {
        IndexConstituentMapper mapper = indexConstituentMapper.getIfAvailable();
        if (mapper == null) {
            return new RefreshResult(0, 0, 0);
        }
        return recalculateSymbols(mapper.findCurrentSymbols(indexCode));
    }

    public RefreshResult recalculateSymbol(String symbol) {
        RefreshResult result = recalculateSymbols(List.of(symbol));
        return result;
    }

    public RefreshResult recalculateSymbols(List<String> symbols) {
        int requested = 0;
        int success = 0;
        int fail = 0;
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            requested++;
            try {
                stockCacheService.saveLatestSignal(stockSignalService.buildLatest(symbol));
                success++;
            } catch (RuntimeException ex) {
                fail++;
                log.warn("Failed to recalculate stock signal for {}", symbol, ex);
            }
        }
        return new RefreshResult(requested, success, fail);
    }

    public record RefreshResult(int requested, int success, int fail) {
    }
}
