package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class StockInfoViewService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yy.MM");

    private final StockCacheService cacheService;
    private final StockDataQualityService dataQualityService;
    private final ObjectMapper objectMapper;

    public StockInfoViewService(StockCacheService cacheService, StockDataQualityService dataQualityService,
            ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.dataQualityService = dataQualityService;
        this.objectMapper = objectMapper;
    }

    public StockInfoView build(StockDashboard dashboard) {
        CompanyProfile profile = dashboard.profile();
        StockQuoteCache quote = dashboard.quote();
        StockMetricSnapshot metric = dashboard.metric();
        JsonNode root = readMetric(metric);
        JsonNode metricNode = root == null ? null : root.get("metric");
        StockDataQualityLatest dataQuality = dataQualityService.assessAndSave(dashboard);

        List<StockInfoView.SeriesPoint> revenueSeries = quarterlySeries(root, "salesPerShare", "netMargin",
                profile == null ? null : profile.getShareOutstanding(), "조정 순이익률");
        List<StockInfoView.SeriesPoint> marginSeries = quarterlySeries(root, "operatingMargin", "netMargin", null,
                "순이익률");
        List<StockInfoView.SeriesPoint> stabilitySeries = quarterlySeries(root, "totalDebtToEquity", "currentRatio",
                null, "유동비율");
        List<StockInfoView.SeriesPoint> epsSeries = quarterlySeries(root, "eps", "roeTTM", null, "ROE");
        List<StockInfoView.SeriesPoint> dividendSeries = annualSeries(root, "payoutRatio", "dividendPerShareAnnual",
                metricNode, "주당배당");

        return new StockInfoView(
                majorRows(profile, metricNode, dataQuality),
                investmentCards(metric, metricNode, dataQuality),
                institutionCards(dashboard.symbol()),
                financeCards(metricNode, dataQuality),
                revenueSeries,
                stabilitySeries,
                revenueSeries,
                epsSeries,
                incomeRows(root, profile == null ? null : profile.getShareOutstanding()),
                dividendCards(metricNode),
                dividendSeries,
                peerRows(dashboard.symbol(), profile),
                profile == null || profile.getFinnhubIndustry() == null ? "동종 업계" : profile.getFinnhubIndustry(),
                formatMarketCap(profile == null ? null : profile.getMarketCap()),
                formatMoney(metricDecimal(metricNode, "enterpriseValue"), "백만 USD"),
                formatNumber(profile == null ? null : profile.getShareOutstanding(), "백만주"),
                metric == null || metric.getMetricDate() == null ? "Finnhub basic financials"
                        : metric.getMetricDate() + " 기준 Finnhub basic financials",
                dataQualityCards(dataQuality),
                dataQualityService.issueMessages(dataQuality, 6));
    }

    private List<StockInfoView.InfoRow> majorRows(CompanyProfile profile, JsonNode metricNode,
            StockDataQualityLatest dataQuality) {
        List<StockInfoView.InfoRow> rows = new ArrayList<>();
        rows.add(new StockInfoView.InfoRow("국가", profile == null ? "-" : value(profile.getCountry())));
        rows.add(new StockInfoView.InfoRow("거래소", profile == null ? "-" : value(profile.getExchange())));
        rows.add(new StockInfoView.InfoRow("산업", profile == null ? "-" : value(profile.getFinnhubIndustry())));
        rows.add(new StockInfoView.InfoRow("시가총액", formatMarketCap(profile == null ? null : profile.getMarketCap())));
        rows.add(new StockInfoView.InfoRow("기업가치", formatMoney(metricDecimal(metricNode, "enterpriseValue"), "백만 USD")));
        rows.add(new StockInfoView.InfoRow("발행주식수", formatNumber(profile == null ? null : profile.getShareOutstanding(), "백만주")));
        rows.add(new StockInfoView.InfoRow("52주 최고",
                formatMoney(dataQualityService.clean(dataQuality, "52WeekHigh", metricDecimal(metricNode, "52WeekHigh")), "USD")));
        rows.add(new StockInfoView.InfoRow("52주 최저",
                formatMoney(dataQualityService.clean(dataQuality, "52WeekLow", metricDecimal(metricNode, "52WeekLow")), "USD")));
        return rows;
    }

    private List<StockInfoView.MetricCard> investmentCards(StockMetricSnapshot metric, JsonNode metricNode,
            StockDataQualityLatest dataQuality) {
        return List.of(
                new StockInfoView.MetricCard("PER",
                        formatRatio(dataQualityService.clean(dataQuality, "peNormalizedAnnual",
                                metric == null ? null : metric.getPeNormalizedAnnual()), "배"),
                        dataQualityService.metricNote(dataQuality, "peNormalizedAnnual", "peNormalizedAnnual")),
                new StockInfoView.MetricCard("PBR",
                        formatRatio(dataQualityService.clean(dataQuality, "pbAnnual",
                                metric == null ? null : metric.getPbAnnual()), "배"),
                        dataQualityService.metricNote(dataQuality, "pbAnnual", "pbAnnual")),
                new StockInfoView.MetricCard("PSR",
                        formatRatio(dataQualityService.clean(dataQuality, "psTTM", metricDecimal(metricNode, "psTTM")), "배"),
                        dataQualityService.metricNote(dataQuality, "psTTM", "psTTM")),
                new StockInfoView.MetricCard("EPS",
                        formatMoney(dataQualityService.clean(dataQuality, "epsTTM",
                                metric == null ? null : metric.getEpsTtm()), "USD"),
                        dataQualityService.metricNote(dataQuality, "epsTTM", "epsTTM")),
                new StockInfoView.MetricCard("ROE",
                        formatPercent(dataQualityService.clean(dataQuality, "roeTTM",
                                metric == null ? null : metric.getRoeTtm())),
                        dataQualityService.metricNote(dataQuality, "roeTTM", "roeTTM")),
                new StockInfoView.MetricCard("순이익률",
                        formatPercent(dataQualityService.clean(dataQuality, "netProfitMarginTTM",
                                metricDecimal(metricNode, "netProfitMarginTTM"))),
                        dataQualityService.metricNote(dataQuality, "netProfitMarginTTM", "netProfitMarginTTM")));
    }

    private List<StockInfoView.MetricCard> institutionCards(String symbol) {
        StockInstitutionFlow flow = cacheService.findLatestInstitutionFlow(symbol);
        if (flow == null) {
            return List.of(
                    new StockInfoView.MetricCard("기관 보유 증감", "-", "13F 데이터 없음"),
                    new StockInfoView.MetricCard("기관 보유액", "-", "13F 데이터 없음"),
                    new StockInfoView.MetricCard("주요 보유기관", "-", "13F 데이터 없음"));
        }
        String quarter = flow.getReportQuarter() == null ? "13F" : flow.getReportQuarter().toString();
        return List.of(
                new StockInfoView.MetricCard("기관 보유 증감", formatPercent(flow.getSharesChangePct()), quarter + " shares QoQ"),
                new StockInfoView.MetricCard("기관 보유액", formatMoney(flow.getTotalValueUsdThousands(), "천 USD"),
                        "기관 " + (flow.getHolderCount() == null ? "-" : flow.getHolderCount()) + "곳"),
                new StockInfoView.MetricCard("주요 보유기관", value(flow.getTopManagerName()),
                        formatMoney(flow.getTopManagerValueUsdThousands(), "천 USD")));
    }

    private List<StockInfoView.MetricCard> financeCards(JsonNode metricNode, StockDataQualityLatest dataQuality) {
        return List.of(
                new StockInfoView.MetricCard("부채비율",
                        formatPercent(dataQualityService.clean(dataQuality, "totalDebt/totalEquityQuarterly",
                                metricDecimal(metricNode, "totalDebt/totalEquityQuarterly"))),
                        dataQualityService.metricNote(dataQuality, "totalDebt/totalEquityQuarterly",
                                "totalDebt/totalEquityQuarterly")),
                new StockInfoView.MetricCard("유동비율",
                        formatPercent(dataQualityService.clean(dataQuality, "currentRatioQuarterly",
                                metricDecimal(metricNode, "currentRatioQuarterly"))),
                        dataQualityService.metricNote(dataQuality, "currentRatioQuarterly", "currentRatioQuarterly")),
                new StockInfoView.MetricCard("자기자본가치", formatMoney(metricDecimal(metricNode, "bookValuePerShareQuarterly"), "USD/주"),
                        "bookValuePerShareQuarterly"));
    }

    private List<StockInfoView.MetricCard> dataQualityCards(StockDataQualityLatest quality) {
        if (quality == null) {
            return List.of();
        }
        return List.of(
                new StockInfoView.MetricCard("품질 점수", formatScore(quality.getQualityScore()), value(quality.getQualityLabel())),
                new StockInfoView.MetricCard("커버리지", formatScore(quality.getCoverageScore()), "원천 데이터 보유율"),
                new StockInfoView.MetricCard("최신성", formatScore(quality.getFreshnessScore()), "오래된 데이터 감점"),
                new StockInfoView.MetricCard("제외 필드",
                        (quality.getExcludedMetricCount() == null ? "-" : quality.getExcludedMetricCount()) + "개",
                        "품질 룰로 분석 제외"));
    }

    private List<StockInfoView.MetricCard> dividendCards(JsonNode metricNode) {
        return List.of(
                new StockInfoView.MetricCard("예상 연 배당", formatMoney(metricDecimal(metricNode, "dividendIndicatedAnnual"), "USD"),
                        "dividendIndicatedAnnual"),
                new StockInfoView.MetricCard("배당수익률", formatPercent(metricDecimal(metricNode, "dividendYieldIndicatedAnnual")),
                        "dividendYieldIndicatedAnnual"),
                new StockInfoView.MetricCard("배당성향", formatPercent(metricDecimal(metricNode, "payoutRatioTTM")),
                        "payoutRatioTTM"),
                new StockInfoView.MetricCard("5년 배당성장률", formatPercent(metricDecimal(metricNode, "dividendGrowthRate5Y")),
                        "dividendGrowthRate5Y"));
    }

    private List<StockInfoView.MetricRow> incomeRows(JsonNode root, BigDecimal shareOutstanding) {
        List<MetricPoint> sales = points(root, "quarterly", "salesPerShare", 9);
        List<MetricPoint> netMargin = points(root, "quarterly", "netMargin", 9);
        List<MetricPoint> operatingMargin = points(root, "quarterly", "operatingMargin", 9);
        List<String> labels = sales.stream().map(MetricPoint::label).toList();
        List<String> revenueValues = new ArrayList<>();
        List<String> netIncomeValues = new ArrayList<>();
        List<String> operatingMarginValues = new ArrayList<>();
        for (int i = 0; i < sales.size(); i++) {
            BigDecimal revenue = shareOutstanding == null ? null : sales.get(i).value().multiply(shareOutstanding);
            revenueValues.add(formatMoney(revenue, "백만 USD"));
            BigDecimal margin = i < netMargin.size() ? netMargin.get(i).value() : null;
            netIncomeValues.add(formatMoney(revenue == null || margin == null ? null
                    : revenue.multiply(margin).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP), "백만 USD"));
            operatingMarginValues.add(i < operatingMargin.size() ? formatPercent(operatingMargin.get(i).value()) : "-");
        }
        List<StockInfoView.MetricRow> rows = new ArrayList<>();
        rows.add(new StockInfoView.MetricRow("항목", labels));
        rows.add(new StockInfoView.MetricRow("추정 매출", revenueValues));
        rows.add(new StockInfoView.MetricRow("추정 순이익", netIncomeValues));
        rows.add(new StockInfoView.MetricRow("영업이익률", operatingMarginValues));
        return rows;
    }

    private List<StockInfoView.PeerRow> peerRows(String symbol, CompanyProfile profile) {
        if (profile == null || profile.getFinnhubIndustry() == null) {
            return List.of();
        }
        List<StockPeerComparison> peers = cacheService.findPeersByIndustry(profile.getFinnhubIndustry(), 8);
        List<StockInfoView.PeerRow> rows = new ArrayList<>();
        int rank = 1;
        for (StockPeerComparison peer : peers) {
            StockDataQualityLatest peerQuality = dataQualityService.findLatest(peer.getSymbol());
            rows.add(new StockInfoView.PeerRow(String.valueOf(rank++), peer.getSymbol(),
                    peer.getName() == null ? peer.getSymbol() : peer.getName(),
                    formatRatio(dataQualityService.clean(peerQuality, "peNormalizedAnnual",
                            peer.getPeNormalizedAnnual()), "배"),
                    formatMarketCap(peer.getMarketCap()),
                    formatMoney(peer.getCurrentPrice(), "USD"),
                    formatRatio(dataQualityService.clean(peerQuality, "pbAnnual", peer.getPbAnnual()), "배"),
                    formatPercent(dataQualityService.clean(peerQuality, "roeTTM", peer.getRoeTtm())),
                    peer.getSymbol() != null && peer.getSymbol().equalsIgnoreCase(symbol)));
        }
        return rows;
    }

    private List<StockInfoView.SeriesPoint> quarterlySeries(JsonNode root, String primaryKey, String secondaryKey,
            BigDecimal multiplier, String secondaryLabel) {
        List<MetricPoint> primary = points(root, "quarterly", primaryKey, 11);
        List<MetricPoint> secondary = points(root, "quarterly", secondaryKey, 11);
        BigDecimal maxPrimary = primary.stream()
                .map(point -> multiplier == null ? point.value().abs() : point.value().multiply(multiplier).abs())
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
        BigDecimal maxSecondary = secondary.stream().map(point -> point.value().abs()).max(Comparator.naturalOrder())
                .orElse(BigDecimal.ONE);

        List<StockInfoView.SeriesPoint> series = new ArrayList<>();
        for (int index = 0; index < primary.size(); index++) {
            MetricPoint primaryPoint = primary.get(index);
            BigDecimal primaryValue = multiplier == null ? primaryPoint.value() : primaryPoint.value().multiply(multiplier);
            BigDecimal secondaryValue = index < secondary.size() ? secondary.get(index).value() : null;
            series.add(new StockInfoView.SeriesPoint(primaryPoint.label(),
                    multiplier == null ? formatPercent(primaryValue) : formatMoney(primaryValue, "백만 USD"),
                    secondaryValue == null ? secondaryLabel + " -" : secondaryLabel + " " + formatPercent(secondaryValue),
                    height(primaryValue, maxPrimary), height(secondaryValue, maxSecondary)));
        }
        return series;
    }

    private List<StockInfoView.SeriesPoint> annualSeries(JsonNode root, String primaryKey, String fallbackMetricKey,
            JsonNode metricNode, String secondaryLabel) {
        List<MetricPoint> points = points(root, "annual", primaryKey, 8);
        BigDecimal fallback = metricDecimal(metricNode, fallbackMetricKey);
        BigDecimal max = points.stream().map(point -> point.value().abs()).max(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
        List<StockInfoView.SeriesPoint> series = new ArrayList<>();
        for (MetricPoint point : points) {
            series.add(new StockInfoView.SeriesPoint(point.label(), formatPercent(point.value()),
                    fallback == null ? secondaryLabel + " -" : secondaryLabel + " " + formatMoney(fallback, "USD"),
                    height(point.value(), max), fallback == null ? 0 : 45));
        }
        return series;
    }

    private List<MetricPoint> points(JsonNode root, String section, String key, int limit) {
        JsonNode values = root == null ? null : root.get("series");
        values = values == null ? null : values.get(section);
        values = values == null ? null : values.get(key);
        if (values == null || !values.isArray()) {
            return List.of();
        }
        List<MetricPoint> points = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            JsonNode item = values.get(index);
            BigDecimal value = decimal(item, "v");
            String period = text(item, "period");
            if (value != null && period != null) {
                points.add(new MetricPoint(formatPeriod(period), value));
            }
        }
        points.sort(Comparator.comparing(MetricPoint::label));
        if (points.size() <= limit) {
            return points;
        }
        return points.subList(points.size() - limit, points.size());
    }

    private JsonNode readMetric(StockMetricSnapshot metric) {
        if (metric == null || metric.getRawJson() == null) {
            return null;
        }
        try {
            return objectMapper.readTree(metric.getRawJson());
        } catch (Exception ex) {
            return null;
        }
    }

    private static BigDecimal metricDecimal(JsonNode metricNode, String fieldName) {
        return decimal(metricNode, fieldName);
    }

    private static BigDecimal decimal(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static int height(BigDecimal value, BigDecimal max) {
        if (value == null || max == null || max.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        BigDecimal percent = value.abs().multiply(BigDecimal.valueOf(100)).divide(max, 0, RoundingMode.HALF_UP);
        return Math.max(8, Math.min(100, percent.intValue()));
    }

    private static String formatPeriod(String period) {
        try {
            LocalDate date = LocalDate.parse(period);
            return date.format(PERIOD_FORMAT);
        } catch (Exception ex) {
            return period;
        }
    }

    private static String formatMarketCap(BigDecimal value) {
        return formatMoney(value, "백만 USD");
    }

    private static String formatMoney(BigDecimal value, String suffix) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + suffix;
    }

    private static String formatNumber(BigDecimal value, String suffix) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + suffix;
    }

    private static String formatRatio(BigDecimal value, String suffix) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix;
    }

    private static String formatPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static String formatScore(Integer score) {
        return score == null ? "-" : score + " / 100";
    }

    private static String value(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private record MetricPoint(String label, BigDecimal value) {
    }
}
