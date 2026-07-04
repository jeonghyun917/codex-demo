package com.kingyurina.demo.stock;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockQuantModelHealthService {

    private static final DecimalFormat COUNT_FORMAT = new DecimalFormat("#,##0");

    private final ObjectProvider<StockBacktestMapper> backtestMapper;

    public StockQuantModelHealthService(ObjectProvider<StockBacktestMapper> backtestMapper) {
        this.backtestMapper = backtestMapper;
    }

    public StockQuantModelHealthView build(String requestedIndex) {
        String indexCode = normalizeIndexCode(requestedIndex);
        StockBacktestMapper mapper = backtestMapper.getIfAvailable();
        List<StockQuantModelHealthMetric> metrics = mapper == null
                ? List.of()
                : mapper.findQuantModelHealth(indexCode);
        if (metrics.isEmpty()) {
            return new StockQuantModelHealthView(
                    "Model Health",
                    "DB 기준 Quant 모델 상태를 읽을 수 없습니다.",
                    List.of(new StockQuantModelHealthView.Card("상태", "blocked",
                            "mariadb profile 또는 mapper 확인 필요", "negative")),
                    List.of(),
                    List.of(),
                    List.of(new StockQuantModelHealthView.Alert("DB 연결 확인 필요",
                            "Quant 모델 상태를 읽을 수 없어 운영 판단이 제한됩니다.", "negative")));
        }

        List<StockQuantModelHealthView.Row> rows = metrics.stream()
                .map(this::toRow)
                .toList();
        List<StockQuantModelHealthView.OperationRow> operations = mapper.findQuantOperationHealth().stream()
                .map(this::toOperationRow)
                .toList();
        List<StockPitQualityMetric> pitQuality = mapper.findPitQuality(indexCode);

        long healthy = rows.stream().filter(row -> "positive".equals(row.tone())).count();
        long warning = rows.stream().filter(row -> "warning".equals(row.tone())).count();
        long negative = rows.stream().filter(row -> "negative".equals(row.tone())).count();
        long operationWarning = operations.stream().filter(row -> "warning".equals(row.tone())).count();
        long operationNegative = operations.stream().filter(row -> "negative".equals(row.tone())).count();

        List<StockQuantModelHealthView.Card> cards = new ArrayList<>();
        cards.add(new StockQuantModelHealthView.Card("정상 레이어", healthy + "개", "fresh/usable", "positive"));
        cards.add(new StockQuantModelHealthView.Card("주의 레이어", warning + "개", "stale/fallback/proxy",
                warning > 0 ? "warning" : "positive"));
        cards.add(new StockQuantModelHealthView.Card("누락 레이어", negative + "개", "missing data",
                negative > 0 ? "negative" : "positive"));
        cards.add(new StockQuantModelHealthView.Card("운영 경고", (operationWarning + operationNegative) + "개",
                "batch/api", operationNegative > 0 ? "negative" : operationWarning > 0 ? "warning" : "positive"));
        pitQuality.stream()
                .filter(metric -> metric.getScore() != null)
                .forEach(metric -> cards.add(new StockQuantModelHealthView.Card(metric.getLayer() + " 품질",
                        Math.round(metric.getScore()) + " / 100",
                        pitQualityNote(metric),
                        pitTone(metric.getScore()))));
        cards.add(new StockQuantModelHealthView.Card("검사 대상", metrics.size() + "개", indexTitle(indexCode),
                "neutral"));

        String summary = "Signal, Expected Return, risk, covariance, macro, benchmark, market snapshot, "
                + "point-in-time 입력 데이터를 운영 관점에서 점검합니다.";
        return new StockQuantModelHealthView("Model Health", summary, cards, rows, operations,
                alerts(rows, operations, pitQuality));
    }

    private StockQuantModelHealthView.Row toRow(StockQuantModelHealthMetric metric) {
        HealthStatus status = status(metric);
        return new StockQuantModelHealthView.Row(
                metric.getLayer(),
                status.label(),
                status.tone(),
                formatCount(metric.getRowCount()),
                coverage(metric),
                latest(metric),
                note(metric));
    }

    private StockQuantModelHealthView.OperationRow toOperationRow(StockQuantOperationHealthMetric metric) {
        OperationStatus status = operationStatus(metric);
        return new StockQuantModelHealthView.OperationRow(
                blankToDefault(metric.getOperation(), "-"),
                status.label(),
                status.tone(),
                formatDateTime(metric.getStartedAt()),
                formatDateTime(metric.getFinishedAt()),
                successRate(metric),
                formatCount(metric.getFailCount()),
                operationMessage(metric));
    }

    private List<StockQuantModelHealthView.Alert> alerts(List<StockQuantModelHealthView.Row> rows,
            List<StockQuantModelHealthView.OperationRow> operations, List<StockPitQualityMetric> pitQuality) {
        List<StockQuantModelHealthView.Alert> alerts = new ArrayList<>();
        pitQuality.stream()
                .filter(metric -> metric.getScore() != null && metric.getScore() < 75.0d)
                .forEach(metric -> alerts.add(new StockQuantModelHealthView.Alert(
                        metric.getLayer() + " 품질 제한",
                        pitQualityNote(metric) + " 공식 historical CSV가 들어오기 전까지 예측 검증은 proxy 한계를 가집니다.",
                        pitTone(metric.getScore()))));
        rows.stream()
                .filter(row -> "negative".equals(row.tone()))
                .limit(3)
                .forEach(row -> alerts.add(new StockQuantModelHealthView.Alert(
                        row.layer() + " 누락",
                        row.note(),
                        "negative")));
        rows.stream()
                .filter(row -> "warning".equals(row.tone()))
                .limit(4)
                .forEach(row -> alerts.add(new StockQuantModelHealthView.Alert(
                        row.layer() + " 점검",
                        row.note(),
                        "warning")));
        operations.stream()
                .filter(row -> "negative".equals(row.tone()) || "warning".equals(row.tone()))
                .limit(4)
                .forEach(row -> alerts.add(new StockQuantModelHealthView.Alert(
                        row.operation() + " " + row.status(),
                        row.message(),
                        row.tone())));
        if (alerts.isEmpty()) {
            alerts.add(new StockQuantModelHealthView.Alert("운영 상태 정상",
                    "핵심 Quant 레이어와 최근 배치/API 상태가 현재 기준으로 사용할 수 있습니다.", "positive"));
        }
        return alerts.stream().limit(8).toList();
    }

    private HealthStatus status(StockQuantModelHealthMetric metric) {
        long rows = value(metric.getRowCount());
        String key = key(metric);
        String note = metric.getNote() == null ? "" : metric.getNote().toLowerCase(Locale.ROOT);
        if (rows <= 0) {
            if ("RISK_FREE".equals(key)) {
                return new HealthStatus("fallback", "warning");
            }
            return new HealthStatus("누락", "negative");
        }
        if ("RISK_FREE".equals(key) && note.contains("fallback")) {
            return new HealthStatus("fallback", "warning");
        }
        if (("MEMBERSHIP_PIT".equals(key) || "SHARES_PIT".equals(key))
                && (note.contains("proxy") || note.contains("current") || note.contains("wikipedia"))) {
            return new HealthStatus("주의", "warning");
        }
        if ("UNIVERSE".equals(key) || "SIGNAL_LATEST".equals(key) || "DATA_QUALITY".equals(key)) {
            return new HealthStatus("정상", "positive");
        }

        LocalDate latest = metric.getLatestDate();
        if (latest == null && metric.getLatestCalculatedAt() != null) {
            latest = metric.getLatestCalculatedAt().toLocalDate();
        }
        if (latest == null) {
            return new HealthStatus("확인", "warning");
        }

        long age = ChronoUnit.DAYS.between(latest, LocalDate.now());
        long staleDays = staleDays(key);
        if (age <= staleDays) {
            return new HealthStatus("정상", "positive");
        }
        if (age <= staleDays * 3) {
            return new HealthStatus("지연", "warning");
        }
        return new HealthStatus("오래됨", "warning");
    }

    private OperationStatus operationStatus(StockQuantOperationHealthMetric metric) {
        String status = metric.getStatus() == null ? "" : metric.getStatus().trim().toUpperCase(Locale.ROOT);
        long failures = value(metric.getFailCount());
        if (status.contains("FAIL") || status.contains("ERROR") || status.contains("BLOCK")) {
            return new OperationStatus("실패", "negative");
        }
        if (failures > 0 && (status.contains("WARNING") || status.contains("WARN"))) {
            return new OperationStatus("주의", "warning");
        }
        if (failures > 0 && value(metric.getSuccessCount()) == 0) {
            return new OperationStatus("실패", "negative");
        }
        if (status.contains("RUNNING")) {
            LocalDateTime startedAt = metric.getStartedAt();
            if (startedAt != null && ChronoUnit.HOURS.between(startedAt, LocalDateTime.now()) >= 2) {
                return new OperationStatus("장시간 실행", "warning");
            }
            return new OperationStatus("실행 중", "warning");
        }
        if (status.contains("SUCCESS") || status.contains("COMPLETED") || status.contains("DONE")
                || status.equals("OK")) {
            return new OperationStatus("정상", "positive");
        }
        if (status.isBlank()) {
            return new OperationStatus("기록 없음", "warning");
        }
        return new OperationStatus(metric.getStatus(), failures > 0 ? "warning" : "neutral");
    }

    private static long staleDays(String key) {
        return switch (key) {
            case "MARKET_SNAPSHOT", "BENCHMARK", "MACRO_REGIME" -> 7;
            case "PORTFOLIO_VIEW", "BACKTEST_VIEW", "DASHBOARD_VIEW" -> 1;
            case "RISK_SNAPSHOT" -> 14;
            case "MACRO_VINTAGE" -> 90;
            case "MACRO_FEATURE" -> 14;
            case "EXPECTED_RETURN_V4", "EXPECTED_RETURN_V5", "EXPECTED_RETURN_V6", "EXPECTED_RETURN_V7",
                    "EXPECTED_RETURN_V8", "EXPECTED_RETURN_V9",
                    "FACTOR_EXPOSURE", "FACTOR_CONTRIBUTION", "COVARIANCE" -> 45;
            default -> 30;
        };
    }

    private static String note(StockQuantModelHealthMetric metric) {
        String key = key(metric);
        String base = switch (key) {
            case "UNIVERSE" -> "현재 지수 편입 종목 기준입니다.";
            case "SIGNAL_LATEST" -> "리스트와 상세 화면에서 조회하는 최신 Signal 저장값입니다.";
            case "DATA_QUALITY" -> "커버리지, 최신성, 이상치, 원천 충돌을 반영한 데이터 품질 점수입니다.";
            case "MARKET_SNAPSHOT" -> "가격, 거래량, 주식수, 시가총액을 날짜별로 저장한 point-in-time 입력입니다.";
            case "MEMBERSHIP_PIT" -> "과거 지수 편입 여부 snapshot입니다. proxy 원천은 실제 PIT CSV로 교체해야 합니다.";
            case "SHARES_PIT" -> "과거 주식수 snapshot입니다. current proxy 원천은 실제 historical shares로 교체해야 합니다.";
            case "BENCHMARK" -> "market-cap weighted benchmark proxy입니다.";
            case "RISK_SNAPSHOT" -> "beta, trailing volatility, dollar volume 기반 risk 입력값입니다.";
            case "RISK_FREE" -> "무위험수익률 입력입니다. fallback이면 FRED/CSV 보강이 필요합니다.";
            case "MACRO_REGIME" -> "Risk-on/neutral/risk-off 시장 국면 입력값입니다.";
            case "MACRO_VINTAGE" -> "FRED/ALFRED vintage macro 데이터입니다.";
            case "MACRO_FEATURE" -> "macro vintage에서 파생한 feature snapshot입니다.";
            case "FACTOR_EXPOSURE" -> "섹터/시총 중립 factor exposure snapshot입니다.";
            case "EXPECTED_RETURN_V4" -> "regime-aware factor expected return snapshot입니다.";
            case "EXPECTED_RETURN_V5" -> "regime-aware nonlinear interaction expected return snapshot입니다.";
            case "EXPECTED_RETURN_V6" -> "calibration-stabilized expected return snapshot입니다.";
            case "EXPECTED_RETURN_V7" -> "horizon-decay stabilized expected return snapshot입니다.";
            case "EXPECTED_RETURN_V8" -> "strict macro PIT expected return snapshot입니다.";
            case "EXPECTED_RETURN_V9" -> "market-data provenance까지 반영한 현재 production expected return snapshot입니다.";
            case "FACTOR_CONTRIBUTION" -> "Quant Opinion에서 factor별 기대수익 기여도를 설명하는 입력값입니다.";
            case "CALIBRATION" -> "상승확률 예측값과 실제 적중률을 비교한 calibration bucket입니다.";
            case "COVARIANCE" -> "종목 간 covariance/correlation 기반 optimizer 입력값입니다.";
            case "PORTFOLIO_VIEW" -> "materialized /signals/portfolio payload입니다.";
            case "BACKTEST_VIEW" -> "materialized /signals/backtest payload입니다.";
            case "DASHBOARD_VIEW" -> "materialized /quant payload입니다.";
            default -> metric.getNote() == null || metric.getNote().isBlank() ? "-" : metric.getNote();
        };
        if (metric.getNote() == null || metric.getNote().isBlank()
                || "-".equals(base)
                || base.equals(metric.getNote())) {
            return base;
        }
        return base + " " + metric.getNote();
    }

    private static String pitQualityNote(StockPitQualityMetric metric) {
        long total = value(metric.getTotalRows());
        if (total <= 0) {
            return "rows=0";
        }
        return "official/csv " + pct(value(metric.getOfficialRows()), total)
                + ", SEC " + pct(value(metric.getSecRows()), total)
                + ", Wikipedia " + pct(value(metric.getWikipediaRows()), total)
                + ", proxy " + pct(value(metric.getProxyRows()), total);
    }

    private static String pct(long part, long total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.round(part * 1000.0d / total) / 10.0d + "%";
    }

    private static String pitTone(double score) {
        if (score >= 85.0d) {
            return "positive";
        }
        if (score >= 65.0d) {
            return "warning";
        }
        return "negative";
    }

    private static String coverage(StockQuantModelHealthMetric metric) {
        long symbols = value(metric.getSymbolCount());
        long dates = value(metric.getDateCount());
        if (symbols > 0 && dates > 0) {
            return formatCount(symbols) + " symbols / " + formatCount(dates) + " dates";
        }
        if (symbols > 0) {
            return formatCount(symbols) + " symbols";
        }
        if (dates > 0) {
            return formatCount(dates) + " dates";
        }
        return "-";
    }

    private static String latest(StockQuantModelHealthMetric metric) {
        if (metric.getLatestDate() != null) {
            return metric.getLatestDate().toString();
        }
        if (metric.getLatestCalculatedAt() != null) {
            return metric.getLatestCalculatedAt().toLocalDate().toString();
        }
        return "-";
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : value.toLocalDate() + " " + value.toLocalTime().withNano(0);
    }

    private static String successRate(StockQuantOperationHealthMetric metric) {
        long requested = value(metric.getRequestedCount());
        long success = value(metric.getSuccessCount());
        if (requested <= 0) {
            return "-";
        }
        return Math.round((success * 1000.0) / requested) / 10.0 + "%";
    }

    private static String operationMessage(StockQuantOperationHealthMetric metric) {
        String message = blankToDefault(metric.getMessage(), "-");
        long requested = value(metric.getRequestedCount());
        long success = value(metric.getSuccessCount());
        long fail = value(metric.getFailCount());
        if ("-".equals(message)) {
            return "requested=" + formatCount(requested) + ", success=" + formatCount(success)
                    + ", fail=" + formatCount(fail);
        }
        return message;
    }

    private static String formatCount(Long value) {
        return COUNT_FORMAT.format(value(value));
    }

    private static String formatCount(long value) {
        return COUNT_FORMAT.format(value);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static String key(StockQuantModelHealthMetric metric) {
        return metric.getMetricKey() == null ? "" : metric.getMetricKey();
    }

    private static String normalizeIndexCode(String value) {
        if (value == null || value.isBlank()) {
            return "SP500";
        }
        String normalized = value.trim().toUpperCase().replace("-", "").replace("_", "");
        if ("NASDAQ100".equals(normalized) || "NDX".equals(normalized)) {
            return "NASDAQ100";
        }
        if ("DOW30".equals(normalized) || "DOWJONES30".equals(normalized) || "DJI".equals(normalized)) {
            return "DOW30";
        }
        return "SP500";
    }

    private static String indexTitle(String indexCode) {
        return switch (indexCode) {
            case "NASDAQ100" -> "Nasdaq 100";
            case "DOW30" -> "Dow Jones 30";
            default -> "S&P 500";
        };
    }

    private record HealthStatus(String label, String tone) {
    }

    private record OperationStatus(String label, String tone) {
    }
}
