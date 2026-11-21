package com.kingyurina.demo.stock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaperLedgerViewService {
    private final ObjectProvider<StockBacktestMapper> mapper;
    private final ObjectMapper json;

    public PaperLedgerViewService(ObjectProvider<StockBacktestMapper> mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> read() {
        try {
            var repository = mapper.getIfAvailable();
            var snapshot = repository == null ? null : repository.findLatestDashboardViewSnapshot("SP500", "SP500_PAPER_WEB_V1");
            if (snapshot != null) {
                Map<String, Object> payload = json.readValue(snapshot.getPayloadJson(), Map.class);
                if ("SP500_PAPER_WEB_V1".equals(payload.get("version"))
                        && payload.get("days") instanceof List<?> && payload.get("accounts") instanceof List<?>) {
                    payload.put("available", true);
                    return payload;
                }
            }
        } catch (Exception ignored) {
            // Do not fall back to filesystem or external requests on page GETs.
        }
        var empty = new LinkedHashMap<String, Object>();
        empty.put("available", false);
        empty.put("status", "조회 데이터 없음");
        empty.put("message", "원장 조회용 데이터가 없거나 읽을 수 없습니다.");
        empty.put("days", List.of());
        empty.put("accounts", List.of());
        empty.put("capturedDays", 0);
        empty.put("latest", "-");
        empty.put("createdAt", "-");
        empty.put("syncedAt", "-");
        return empty;
    }
}
