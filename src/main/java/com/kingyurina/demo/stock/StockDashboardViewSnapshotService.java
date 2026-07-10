package com.kingyurina.demo.stock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class StockDashboardViewSnapshotService {

    private static final String DASHBOARD_VIEW_VERSION = "DASHBOARD_QUANT_AI_DATA_SOURCES_V2";
    private static final Duration DASHBOARD_VIEW_CACHE_TTL = Duration.ofMinutes(10);

    private final ObjectProvider<StockBacktestMapper> stockBacktestMapper;
    private final ObjectMapper objectMapper;
    private final StockMarketViewService stockMarketViewService;
    private final StockMacroRegimeService stockMacroRegimeService;
    private final StockQuantModelHealthService stockQuantModelHealthService;
    private final StockApiDataSourceService stockApiDataSourceService;
    private final Map<String, CachedDashboardView> dashboardViewCache = new ConcurrentHashMap<>();

    public StockDashboardViewSnapshotService(ObjectProvider<StockBacktestMapper> stockBacktestMapper,
            ObjectMapper objectMapper,
            StockMarketViewService stockMarketViewService,
            StockMacroRegimeService stockMacroRegimeService,
            StockQuantModelHealthService stockQuantModelHealthService,
            StockApiDataSourceService stockApiDataSourceService) {
        this.stockBacktestMapper = stockBacktestMapper;
        this.objectMapper = objectMapper;
        this.stockMarketViewService = stockMarketViewService;
        this.stockMacroRegimeService = stockMacroRegimeService;
        this.stockQuantModelHealthService = stockQuantModelHealthService;
        this.stockApiDataSourceService = stockApiDataSourceService;
    }

    public StockDashboardViewPayload build(String indexCode) {
        String effectiveIndexCode = normalizeIndexCode(indexCode);
        CachedDashboardView cached = dashboardViewCache.get(effectiveIndexCode);
        if (cached != null && cached.isFresh()) {
            return cached.payload();
        }
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        StockDashboardViewPayload materialized = readMaterializedDashboardView(mapper, effectiveIndexCode);
        if (materialized != null) {
            dashboardViewCache.put(effectiveIndexCode, new CachedDashboardView(materialized, Instant.now()));
            return materialized;
        }
        StockDashboardViewPayload payload = buildUncached(effectiveIndexCode);
        saveMaterializedDashboardView(mapper, effectiveIndexCode, payload, "ON_DEMAND");
        dashboardViewCache.put(effectiveIndexCode, new CachedDashboardView(payload, Instant.now()));
        return payload;
    }

    public DashboardViewSnapshotRefreshResult refreshDashboardViewSnapshot(String indexCode) {
        String effectiveIndexCode = normalizeIndexCode(indexCode);
        dashboardViewCache.clear();
        StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
        if (mapper == null) {
            return new DashboardViewSnapshotRefreshResult(effectiveIndexCode, false, "mapper unavailable", 0);
        }
        long start = System.currentTimeMillis();
        StockDashboardViewPayload payload = buildUncached(effectiveIndexCode);
        boolean saved = saveMaterializedDashboardView(mapper, effectiveIndexCode, payload, "MATERIALIZED_BATCH");
        if (saved) {
            dashboardViewCache.put(effectiveIndexCode, new CachedDashboardView(payload, Instant.now()));
        }
        return new DashboardViewSnapshotRefreshResult(effectiveIndexCode, saved,
                saved ? "saved" : "serialization failed", System.currentTimeMillis() - start);
    }

    private StockDashboardViewPayload buildUncached(String indexCode) {
        StockMarketView market = stockMarketViewService.build(indexCode);
        StockMacroRegimeView macroRegime = stockMacroRegimeService.latestView(indexCode);
        StockQuantModelHealthView modelHealth = stockQuantModelHealthService.build(indexCode);
        return new StockDashboardViewPayload(
                indexCode,
                market,
                macroRegime,
                modelHealth,
                stockApiDataSourceService.build(indexCode, modelHealth));
    }

    private StockDashboardViewPayload readMaterializedDashboardView(StockBacktestMapper mapper, String indexCode) {
        if (mapper == null) {
            return null;
        }
        try {
            StockDashboardViewSnapshot snapshot =
                    mapper.findLatestDashboardViewSnapshot(indexCode, DASHBOARD_VIEW_VERSION);
            if (snapshot == null || snapshot.getPayloadJson() == null || snapshot.getPayloadJson().isBlank()) {
                return null;
            }
            StockDashboardViewPayload payload = objectMapper.readValue(snapshot.getPayloadJson(), StockDashboardViewPayload.class);
            if (!isCompatible(payload)) {
                return null;
            }
            return payload;
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean isCompatible(StockDashboardViewPayload payload) {
        return payload != null
                && payload.market() != null
                && payload.macroRegime() != null
                && payload.modelHealth() != null
                && payload.modelHealth().alerts() != null
                && payload.modelHealth().operations() != null
                && payload.dataSources() != null
                && hasModelHealthLayer(payload, "Backtest view snapshot");
    }

    private static boolean hasModelHealthLayer(StockDashboardViewPayload payload, String layer) {
        if (payload == null || payload.modelHealth() == null || payload.modelHealth().rows() == null) {
            return false;
        }
        return payload.modelHealth().rows().stream()
                .anyMatch(row -> row != null && layer.equals(row.layer()));
    }

    private boolean saveMaterializedDashboardView(StockBacktestMapper mapper, String indexCode,
            StockDashboardViewPayload payload, String source) {
        if (mapper == null || payload == null) {
            return false;
        }
        try {
            StockDashboardViewSnapshot snapshot = new StockDashboardViewSnapshot();
            snapshot.setIndexCode(indexCode);
            snapshot.setViewVersion(DASHBOARD_VIEW_VERSION);
            snapshot.setGeneratedAt(LocalDateTime.now());
            snapshot.setSource(source);
            snapshot.setPayloadJson(objectMapper.writeValueAsString(payload));
            mapper.upsertDashboardViewSnapshot(snapshot);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String normalizeIndexCode(String value) {
        if (value == null || value.isBlank()) {
            return "SP500";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        if ("NASDAQ100".equals(normalized) || "NDX".equals(normalized)) {
            return "NASDAQ100";
        }
        if ("DOW30".equals(normalized) || "DOWJONES30".equals(normalized) || "DJI".equals(normalized)) {
            return "DOW30";
        }
        return "SP500";
    }

    public record DashboardViewSnapshotRefreshResult(
            String indexCode,
            boolean saved,
            String message,
            long elapsedMillis) {
    }

    private record CachedDashboardView(StockDashboardViewPayload payload, Instant cachedAt) {
        boolean isFresh() {
            return cachedAt != null && Instant.now().minus(DASHBOARD_VIEW_CACHE_TTL).isBefore(cachedAt);
        }
    }
}
