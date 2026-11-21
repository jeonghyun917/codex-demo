package com.kingyurina.demo.stock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Read-only projection of the original barrier-model decisions, never V9 scores. */
@Service
public class QuantBuyCandidatesService {
    private final PaperLedgerViewService ledger;
    private final Clock clock;

    @Autowired
    public QuantBuyCandidatesService(PaperLedgerViewService ledger) {
        this(ledger, Clock.systemUTC());
    }

    QuantBuyCandidatesService(PaperLedgerViewService ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    public record View(String status, String message, String date, String recordedAt, String nextOpen,
                       String trend, String volatilityCap, int eligibleCount,
                       List<Map<String, Object>> rows, Map<String, Object> evidence,
                       List<String> dates, Map<String, Object> tracking) {
        public View(String status, String message, String date, String recordedAt, String nextOpen,
                    String trend, String volatilityCap, int eligibleCount,
                    List<Map<String, Object>> rows, Map<String, Object> evidence) {
            this(status, message, date, recordedAt, nextOpen, trend, volatilityCap, eligibleCount,
                    rows, evidence, List.of(), Map.of());
        }
    }

    public static View unavailable() {
        return empty("UNAVAILABLE", "저장된 익절 확률 예측을 조회할 수 없습니다.");
    }

    private static View empty(String status, String message) {
        return new View(status, message, "-", "-", "-", "-", "-", 0, List.of(), Map.of());
    }

    public View build(String index) {
        return build(index, null);
    }

    public View build(String index, String requestedDate) {
        if (!"SP500".equals(index)) {
            return empty("UNSUPPORTED", "기존 익절 확률 전략의 대상은 S&P 500입니다.");
        }
        try {
            var payload = ledger.read();
            if (!Boolean.TRUE.equals(payload.get("available"))) return unavailable();
            var days = maps(payload.get("days"));
            if (days.isEmpty()) return unavailable();
            var dates = days.stream().map(d -> LocalDate.parse(string(d, "date")).toString())
                    .distinct().sorted(Comparator.reverseOrder()).toList();
            boolean historicalRequest = requestedDate != null && !requestedDate.isBlank();
            String selectedDate = historicalRequest ? requestedDate : dates.getFirst();
            var day = days.stream().filter(d -> selectedDate.equals(string(d, "date"))).findFirst().orElseThrow();
            @SuppressWarnings("unchecked")
            var tracking = day.get("tracking") instanceof Map<?, ?> t ? (Map<String, Object>) t : Map.<String, Object>of();
            var date = LocalDate.parse(string(day, "date"));
            var recorded = Instant.parse(string(day, "recordedAt"));
            var nextOpen = Instant.parse(string(day, "nextOpen"));
            var now = clock.instant();
            if (!recorded.isBefore(nextOpen) || recorded.isAfter(now)
                    || recorded.isBefore(date.atStartOfDay().toInstant(ZoneOffset.UTC))
                    || nextOpen.isAfter(date.plusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC))) return unavailable();
            var evidence = evidence(payload);
            boolean expired = !now.isBefore(nextOpen);
            if (expired && !historicalRequest) {
                return view("STALE", "새 예측 대기 · 판단일을 선택하면 과거 관심 종목을 볼 수 있습니다.", day, 0, List.of(), evidence, dates, tracking);
            }
            // selected is the persisted original strategy gate; do not recompute with rounded display values.
            var ranked = maps(day.get("predictions")).stream()
                    .sorted(Comparator.comparingInt(r -> ((Number) r.get("rank")).intValue())).toList();
            var symbols = new java.util.HashSet<String>();
            for (var row : ranked) {
                double probability = percent(row.get("probability"));
                if (probability < 0 || probability > 100 || !Double.isFinite(probability)
                        || ((Number) row.get("rank")).intValue() < 1
                        || string(row, "symbol").isBlank() || !symbols.add(string(row, "symbol"))) return unavailable();
            }
            int eligible = (int) ranked.stream().filter(QuantBuyCandidatesService::buyEligible).count();
            var rows = ranked.stream().limit(20).map(r -> enrich(r, evidence, date.toString())).toList();
            return view(expired ? "ARCHIVED" : eligible == 0 ? "NO_CANDIDATES" : "READY",
                    expired ? "과거 판단일 기록 · 현재 매수 신호가 아닙니다."
                            : eligible == 0 ? "매수 조건 통과 0종목 · 관심 종목 관찰 중" : "관심 종목 확률순 · 매수 가능 여부 별도 확인",
                    day, eligible, rows, evidence, dates, tracking);
        } catch (RuntimeException invalidSnapshot) {
            return unavailable();
        }
    }

    private static View view(String status, String message, Map<String, Object> day, int count,
                             List<Map<String, Object>> rows, Map<String, Object> evidence,
                             List<String> dates, Map<String, Object> tracking) {
        return new View(status, message, string(day, "date"), string(day, "recordedAt"),
                string(day, "nextOpen"), string(day, "trend"), string(day, "volatilityCap"), count, rows, evidence, dates, tracking);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static String string(Map<String, Object> map, String key) {
        return map.get(key) == null ? "-" : map.get(key).toString();
    }

    private static double percent(Object value) {
        String text = String.valueOf(value);
        if (!text.endsWith("%")) throw new IllegalArgumentException("Missing percent unit");
        return Double.parseDouble(text.substring(0, text.length() - 1));
    }

    private static Map<String, Object> evidence(Map<String, Object> payload) {
        if (!(payload.get("probabilityEvidence") instanceof Map<?, ?> evidence)
                || !Boolean.TRUE.equals(evidence.get("available"))) return Map.of();
        return maps(evidence.get("groups")).stream()
                .filter(g -> "baseline-0.001".equals(g.get("key")))
                .findFirst().orElse(Map.of());
    }

    private static Map<String, Object> enrich(Map<String, Object> row, Map<String, Object> evidence, String date) {
        var result = new LinkedHashMap<>(row);
        result.values().removeIf(java.util.Objects::isNull);
        boolean eligible = buyEligible(row);
        result.put("buyEligible", eligible);
        result.put("buyReason", eligible ? "조건 통과 · 체결 전 후보"
                : row.get("reason") instanceof String reason && !reason.isBlank()
                    ? reason : "제외 사유 기록 없음 · 매수 보류");
        var close = row.get("referenceClose");
        boolean validClose = date.equals(row.get("referenceDate")) && close instanceof Number n
                && Double.isFinite(n.doubleValue()) && n.doubleValue() > 0;
        result.put("referencePrice", validClose
                ? String.format(java.util.Locale.US, "$%,.2f", ((Number) close).doubleValue()) : "미기록");
        // Display-only levels; execution barriers remain based on the next-session fill.
        result.put("referenceTakeProfit", validClose
                ? String.format(java.util.Locale.US, "$%,.2f", ((Number) close).doubleValue() * 1.15) : "-");
        result.put("referenceStopLoss", validClose
                ? String.format(java.util.Locale.US, "$%,.2f", ((Number) close).doubleValue() * 0.95) : "-");
        result.put("companyName", row.get("companyName") instanceof String name ? name : "");
        result.put("tpRate", "-");
        result.put("slRate", "-");
        result.put("meanReturn", "-");
        result.put("sample", "근거 없음");
        double probability = percent(row.get("probability"));
        var bins = maps(evidence.get("rows"));
        // Rounded display probabilities at bin boundaries cannot be safely joined to raw-probability bins.
        if (bins.size() == 10 && probability % 10 != 0) {
            var bin = bins.get((int) (probability / 10));
            if (bin.get("count") instanceof Number count && count.intValue() > 0) {
                for (String key : List.of("tpRate", "slRate", "meanReturn")) result.put(key, string(bin, key));
                result.put("sample", count.intValue() + "건" + (count.intValue() < 30 ? " · 소표본" : ""));
            } else result.put("sample", "표본 없음");
        }
        return Map.copyOf(result);
    }

    private static boolean buyEligible(Map<String, Object> row) {
        return Boolean.TRUE.equals(row.get("selected")) && Boolean.TRUE.equals(row.get("interest"));
    }
}
