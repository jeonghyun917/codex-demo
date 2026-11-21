package com.kingyurina.demo.stock;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads a separately published status snapshot only; never collects data or evaluates models on GET. */
@Service
public class StockForwardValidationStatusService {
    public static final String VERSION = "QUANT_FORWARD_VALIDATION_STATUS_V1";
    private static final DateTimeFormatter KST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'KST'")
            .withZone(ZoneId.of("Asia/Seoul"));
    private final ObjectProvider<StockBacktestMapper> mapperProvider;
    private final ObjectMapper json;

    public StockForwardValidationStatusService(ObjectProvider<StockBacktestMapper> mapperProvider, ObjectMapper json) {
        this.mapperProvider = mapperProvider;
        this.json = json;
    }

    public View build(String index) {
        try {
            if (!Set.of("SP500", "DOW30", "NASDAQ100").contains(index)) return unavailable();
            var mapper = mapperProvider.getIfAvailable();
            if (mapper == null) return unavailable();
            var snapshot = mapper.findLatestDashboardViewSnapshot(index, VERSION);
            if (snapshot == null || !index.equals(snapshot.getIndexCode()) || !VERSION.equals(snapshot.getViewVersion()))
                return unavailable();
            JsonNode root = json.readTree(snapshot.getPayloadJson());
            require(VERSION.equals(root.path("format").asText()) && index.equals(root.path("indexCode").asText()));
            require(root.path("promotionAllowed").isBoolean() && !root.path("promotionAllowed").asBoolean());
            Instant generated = Instant.parse(root.path("generatedAtUtc").asText());
            require(!generated.isAfter(Instant.now().plusSeconds(60)));
            var research = root.path("research");
            require("EXPOSED_TRAINING_NOT_FORWARD".equals(research.path("classification").asText()));
            double baselineRaw = metric(research.path("baselineRawMae"));
            double baselineExcess = metric(research.path("baselineExcessMae"));
            double candidateRaw = metric(research.path("candidateRawMae"));
            double candidateExcess = metric(research.path("candidateExcessMae"));
            boolean improved = candidateRaw < baselineRaw && candidateExcess < baselineExcess;
            require(research.path("improved").isBoolean() && research.path("improved").asBoolean() == improved);
            var forward = root.path("forward");
            int observations = count(forward.path("originalObservations"));
            int scored = count(forward.path("scoredObservations"));
            int cohorts = count(forward.path("cohorts"));
            int matured = count(forward.path("maturedCohorts"));
            int terminal = count(forward.path("entryNonTradable"));
            int missing = count(forward.path("missingEntryClose"));
            require(cohorts > 0 && observations > 0 && matured <= cohorts && scored <= observations
                    && terminal + (long) missing <= observations);
            Instant first = Instant.parse(forward.path("firstEarliestEvaluationUtc").asText());
            String status = root.path("status").asText();
            require((matured == 0 && scored == 0 && "AWAITING_MATURITY".equals(status))
                    || (matured > 0 && scored < observations && "PARTIAL".equals(status))
                    || (matured == cohorts && scored == observations && "MATURED".equals(status)));
            String raw = "미평가", excess = "미평가";
            if (matured > 0) require(!first.isAfter(generated) && !first.isAfter(Instant.now()));
            if (scored == 0) {
                require(forward.has("rawMae") && forward.path("rawMae").isNull()
                        && forward.has("excessMae") && forward.path("excessMae").isNull());
            } else {
                raw = format(metric(forward.path("rawMae")));
                excess = format(metric(forward.path("excessMae")));
            }
            var benchmark = root.path("benchmark");
            require("MARKET_SNAPSHOT_WEIGHTED_PRICE_PROXY".equals(benchmark.path("kind").asText()));
            require(benchmark.path("totalReturn").isBoolean() && !benchmark.path("totalReturn").asBoolean());
            require(root.path("limitations").isArray() && !root.path("limitations").isEmpty());
            var limitations = new ArrayList<String>();
            for (var item : root.path("limitations")) {
                require(item.isTextual() && !item.asText().isBlank());
                limitations.add(item.asText());
            }
            String researchSummary = "노출된 학습 자료 · V9 MAE " + format(baselineRaw) + " / " + format(baselineExcess)
                    + " → 재무 보완 후보 " + format(candidateRaw) + " / " + format(candidateExcess)
                    + (improved ? " · 학습 구간 감소, 전방 검증 아님" : " · 양쪽 오차 개선 실패/악화");
            return new View(true, matured == 0 ? "만기 대기" : "MATURED".equals(status) ? "만기 평가 수록" : "일부 만기 평가",
                    KST.format(generated), researchSummary,
                    "원래 관측 " + observations + "건 · 평가 " + scored + " / " + observations
                    + " · 만기 코호트 " + matured + " / " + cohorts,
                    raw, excess, KST.format(first) + " 이후 종가 확보 시 판정 가능",
                    "진입 불가 " + terminal + "건 · 진입 종가 누락 " + missing + "건 (원래 분모 보존)",
                    "저장 시가총액가중 가격수익 대용 지수 · 공식 배당포함 지수 아님",
                    "운영 V9 유지 · 개선 후보 미승격", List.copyOf(limitations));
        } catch (RuntimeException ex) {
            return unavailable();
        }
    }

    private static int count(JsonNode node) {
        require(node.isIntegralNumber() && node.canConvertToInt() && node.asInt() >= 0);
        return node.asInt();
    }

    private static double metric(JsonNode node) {
        require(node.isNumber() && Double.isFinite(node.asDouble()) && node.asDouble() >= 0);
        return node.asDouble();
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.4f%%p", value); }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid validation status"); }

    public static View unavailable() {
        return new View(false, "전방 상태 미발행", "-", "검증된 연구 상태를 아직 발행하지 않았습니다.",
                "전방 관측 상태 미확인", "미평가", "미평가", "미확인", "진입 상태 미확인",
                "비교지수 기준 미확인", "운영 모델 승격 근거 없음", List.of("누락·검증 실패를 0% 오차로 표시하지 않습니다."));
    }

    public record View(boolean available, String status, String generatedAt, String researchSummary,
            String forwardSummary, String rawMae, String excessMae, String firstEvaluation, String entrySummary,
            String benchmark, String modelStatus, List<String> limitations) { }
}
