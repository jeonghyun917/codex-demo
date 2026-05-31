package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockHeatmapViewService {

    private static final BigDecimal USD_KRW_RATE = BigDecimal.valueOf(1365);

    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;

    public StockHeatmapViewService(ObjectProvider<IndexConstituentMapper> indexConstituentMapper) {
        this.indexConstituentMapper = indexConstituentMapper;
    }

    public StockHeatmapView build(String requestedIndex) {
        String indexCode = normalize(requestedIndex);
        List<StockMarketRow> rows = rows(indexCode);
        return new StockHeatmapView(indexCode, title(indexCode), description(indexCode), menus(indexCode), sectors(rows));
    }

    private List<StockMarketRow> rows(String indexCode) {
        IndexConstituentMapper mapper = indexConstituentMapper.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.findMarketRows(indexCode);
    }

    private static List<StockHeatmapView.Sector> sectors(List<StockMarketRow> rows) {
        Map<String, List<StockMarketRow>> bySector = groupRows(rows, StockHeatmapViewService::sector);
        Map<String, String> sectorStyles = layoutStyles(bySector);

        List<StockHeatmapView.Sector> sectors = new ArrayList<>();
        for (Map.Entry<String, List<StockMarketRow>> entry : bySector.entrySet()) {
            sectors.add(new StockHeatmapView.Sector(
                    entry.getKey(),
                    sectorStyles.getOrDefault(entry.getKey(), fullSizeStyle()),
                    industries(entry.getValue())));
        }
        return sectors;
    }

    private static List<StockHeatmapView.Industry> industries(List<StockMarketRow> rows) {
        Map<String, List<StockMarketRow>> byIndustry = groupRows(rows, StockHeatmapViewService::industry);
        Map<String, String> industryStyles = readableGroupLayoutStyles(byIndustry);

        List<StockHeatmapView.Industry> industries = new ArrayList<>();
        for (Map.Entry<String, List<StockMarketRow>> entry : byIndustry.entrySet()) {
            BigDecimal weightedChange = weightedChangePercent(entry.getValue());
            industries.add(new StockHeatmapView.Industry(
                    entry.getKey(),
                    industryStyles.getOrDefault(entry.getKey(), fullSizeStyle()),
                    colorClass(weightedChange),
                    tiles(entry.getValue())));
        }
        return industries;
    }

    private static Map<String, List<StockMarketRow>> groupRows(List<StockMarketRow> rows,
            Function<StockMarketRow, String> classifier) {
        Map<String, List<StockMarketRow>> grouped = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparing(StockMarketRow::getMarketCap,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(row -> grouped.computeIfAbsent(classifier.apply(row), ignored -> new ArrayList<>()).add(row));
        return grouped;
    }

    private static List<StockHeatmapView.Tile> tiles(List<StockMarketRow> rows) {
        Map<String, String> tileStyles = tileLayoutStyles(rows);
        List<StockHeatmapView.Tile> tiles = new ArrayList<>();
        for (StockMarketRow row : rows) {
            BigDecimal change = changePercent(row);
            tiles.add(new StockHeatmapView.Tile(
                    row.getSymbol(),
                    row.getName() == null ? row.getSymbol() : row.getName(),
                    value(row.getSector()),
                    value(row.getIndustry()),
                    formatPercent(change),
                    formatPriceKrw(row.getCurrentPrice()),
                    formatPriceKrw(row.getPreviousClose()),
                    formatPriceKrw(row.getOpenPrice()),
                    formatPriceRangeKrw(row.getLowPrice(), row.getHighPrice()),
                    formatMarketCapKrw(row.getMarketCap()),
                    formatRatio(row.getPeNormalizedAnnual(), "배"),
                    formatRatio(row.getPbAnnual(), "배"),
                    formatPercent(row.getRoeTtm()),
                    tileStyles.getOrDefault(row.getSymbol(), fullSizeStyle()),
                    colorClass(change),
                    "/stocks/" + row.getSymbol()));
        }
        return tiles;
    }

    private static List<StockHeatmapView.MenuItem> menus(String active) {
        return List.of(
                new StockHeatmapView.MenuItem("SP500", "S&P 500", "SP500".equals(active)),
                new StockHeatmapView.MenuItem("NASDAQ100", "Nasdaq 100", "NASDAQ100".equals(active)),
                new StockHeatmapView.MenuItem("DOW30", "Dow Jones 30", "DOW30".equals(active)));
    }

    private static String normalize(String value) {
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

    private static String title(String indexCode) {
        return switch (indexCode) {
            case "NASDAQ100" -> "Nasdaq 100 Heatmap";
            case "DOW30" -> "Dow Jones 30 Heatmap";
            default -> "S&P 500 Heatmap";
        };
    }

    private static String description(String indexCode) {
        return switch (indexCode) {
            case "NASDAQ100" -> "Nasdaq 100 종목을 섹터와 산업으로 묶었습니다. 크기는 시가총액, 색상은 전일 대비 등락률입니다.";
            case "DOW30" -> "Dow Jones 30 종목을 섹터와 산업으로 묶었습니다. 크기는 시가총액, 색상은 전일 대비 등락률입니다.";
            default -> "S&P 500 구성종목을 섹터와 산업으로 묶었습니다. 크기는 시가총액, 색상은 전일 대비 등락률입니다.";
        };
    }

    private static String sector(StockMarketRow row) {
        if (row.getSector() != null && !row.getSector().isBlank()) {
            return row.getSector().toUpperCase(Locale.ROOT);
        }
        return "UNKNOWN";
    }

    private static String industry(StockMarketRow row) {
        if (row.getIndustry() != null && !row.getIndustry().isBlank()) {
            return row.getIndustry().toUpperCase(Locale.ROOT);
        }
        return "OTHER";
    }

    private static Map<String, String> layoutStyles(Map<String, List<StockMarketRow>> groupedRows) {
        return layoutStyles(groupedRows, BigDecimal.ZERO);
    }

    private static Map<String, String> readableGroupLayoutStyles(Map<String, List<StockMarketRow>> groupedRows) {
        if (groupedRows.size() <= 1) {
            return layoutStyles(groupedRows);
        }
        BigDecimal total = groupedRows.values().stream()
                .map(StockHeatmapViewService::totalMarketCap)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double minimumShare = Math.min(0.09, 0.70 / groupedRows.size());
        BigDecimal minimumWeight = total.multiply(BigDecimal.valueOf(minimumShare));
        return layoutStyles(groupedRows, minimumWeight);
    }

    private static Map<String, String> layoutStyles(Map<String, List<StockMarketRow>> groupedRows,
            BigDecimal minimumWeight) {
        List<WeightedItem> items = groupedRows.entrySet().stream()
                .map(entry -> new WeightedItem(entry.getKey(), totalMarketCap(entry.getValue())
                        .max(minimumWeight)
                        .doubleValue()))
                .toList();
        Map<String, Rect> rects = new HashMap<>();
        layout(items, 0, 0, 100, 100, rects);

        Map<String, String> styles = new HashMap<>();
        rects.forEach((key, rect) -> styles.put(key, style(rect)));
        return styles;
    }

    private static BigDecimal totalMarketCap(List<StockMarketRow> rows) {
        BigDecimal total = rows.stream()
                .map(StockMarketRow::getMarketCap)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.compareTo(BigDecimal.ZERO) > 0 ? total : BigDecimal.ONE;
    }

    private static Map<String, String> tileLayoutStyles(List<StockMarketRow> rows) {
        List<WeightedItem> items = rows.stream()
                .map(row -> new WeightedItem(row.getSymbol(), safeWeight(row.getMarketCap())))
                .toList();
        Map<String, Rect> rects = new HashMap<>();
        layout(items, 0, 0, 100, 100, rects);

        Map<String, String> styles = new HashMap<>();
        rects.forEach((symbol, rect) -> styles.put(symbol, style(rect)));
        return styles;
    }

    private static void layout(List<WeightedItem> items, double x, double y, double width, double height,
            Map<String, Rect> rects) {
        if (items.isEmpty()) {
            return;
        }
        if (items.size() == 1) {
            rects.put(items.get(0).key(), new Rect(x, y, width, height));
            return;
        }

        double total = totalWeight(items);
        int split = splitIndex(items, total);
        List<WeightedItem> first = items.subList(0, split);
        List<WeightedItem> second = items.subList(split, items.size());
        double firstRatio = totalWeight(first) / total;

        if (width >= height) {
            double firstWidth = width * firstRatio;
            layout(first, x, y, firstWidth, height, rects);
            layout(second, x + firstWidth, y, width - firstWidth, height, rects);
            return;
        }

        double firstHeight = height * firstRatio;
        layout(first, x, y, width, firstHeight, rects);
        layout(second, x, y + firstHeight, width, height - firstHeight, rects);
    }

    private static int splitIndex(List<WeightedItem> items, double total) {
        double target = total / 2.0;
        double running = 0;
        double bestDiff = Double.MAX_VALUE;
        int split = 1;
        for (int i = 0; i < items.size() - 1; i++) {
            running += items.get(i).weight();
            double diff = Math.abs(target - running);
            if (diff <= bestDiff) {
                bestDiff = diff;
                split = i + 1;
            }
        }
        return split;
    }

    private static double totalWeight(List<WeightedItem> items) {
        return items.stream().mapToDouble(WeightedItem::weight).sum();
    }

    private static double safeWeight(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return 1.0;
        }
        return value.doubleValue();
    }

    private static BigDecimal weightedChangePercent(List<StockMarketRow> rows) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (StockMarketRow row : rows) {
            BigDecimal change = changePercent(row);
            if (change == null) {
                continue;
            }
            BigDecimal weight = row.getMarketCap() == null || row.getMarketCap().compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ONE
                    : row.getMarketCap();
            weightedSum = weightedSum.add(change.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return weightedSum.divide(totalWeight, 4, RoundingMode.HALF_UP);
    }

    private static String style(Rect rect) {
        return String.format(Locale.US,
                "left:%.4f%%;top:%.4f%%;width:%.4f%%;height:%.4f%%;",
                rect.x(),
                rect.y(),
                rect.width(),
                rect.height());
    }

    private static String fullSizeStyle() {
        return "left:0%;top:0%;width:100%;height:100%;";
    }

    private static BigDecimal changePercent(StockMarketRow row) {
        if (row.getCurrentPrice() == null || row.getPreviousClose() == null
                || row.getPreviousClose().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return row.getCurrentPrice().subtract(row.getPreviousClose())
                .multiply(BigDecimal.valueOf(100))
                .divide(row.getPreviousClose(), 4, RoundingMode.HALF_UP);
    }

    private static String formatPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static String formatPriceKrw(BigDecimal usdPrice) {
        if (usdPrice == null) {
            return "-";
        }
        BigDecimal krw = usdPrice.multiply(USD_KRW_RATE).setScale(0, RoundingMode.HALF_UP);
        return new DecimalFormat("#,##0").format(krw) + "원";
    }

    private static String formatPriceRangeKrw(BigDecimal lowPrice, BigDecimal highPrice) {
        if (lowPrice == null && highPrice == null) {
            return "-";
        }
        return formatPriceKrw(lowPrice) + " - " + formatPriceKrw(highPrice);
    }

    private static String formatMarketCapKrw(BigDecimal marketCapMillionUsd) {
        if (marketCapMillionUsd == null) {
            return "-";
        }
        BigDecimal trillionKrw = marketCapMillionUsd
                .multiply(USD_KRW_RATE)
                .divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP);
        return new DecimalFormat("#,##0.0").format(trillionKrw) + "조원";
    }

    private static String formatRatio(BigDecimal value, String suffix) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix;
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String colorClass(BigDecimal change) {
        if (change == null) {
            return "flat";
        }
        int compare = change.compareTo(BigDecimal.ZERO);
        BigDecimal abs = change.abs();
        if (compare > 0 && abs.compareTo(BigDecimal.valueOf(3)) >= 0) {
            return "up-3";
        }
        if (compare > 0 && abs.compareTo(BigDecimal.ONE) >= 0) {
            return "up-2";
        }
        if (compare > 0) {
            return "up-1";
        }
        if (compare < 0 && abs.compareTo(BigDecimal.valueOf(3)) >= 0) {
            return "down-3";
        }
        if (compare < 0 && abs.compareTo(BigDecimal.ONE) >= 0) {
            return "down-2";
        }
        if (compare < 0) {
            return "down-1";
        }
        return "flat";
    }

    private record WeightedItem(String key, double weight) {
    }

    private record Rect(double x, double y, double width, double height) {
    }
}
