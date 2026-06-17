package com.kingyurina.demo.etf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class EtfMarketViewService {

    private static final BigDecimal USD_KRW_RATE = BigDecimal.valueOf(1365);
    private static final DecimalFormat WON_FORMAT = new DecimalFormat("#,##0");
    private static final DecimalFormat ONE_DECIMAL_FORMAT = new DecimalFormat("#,##0.0");

    private final ObjectProvider<EtfMapper> etfMapper;
    private final EtfSignalService signalService;

    public EtfMarketViewService(ObjectProvider<EtfMapper> etfMapper, EtfSignalService signalService) {
        this.etfMapper = etfMapper;
        this.signalService = signalService;
    }

    public EtfMarketView market() {
        EtfMapper mapper = etfMapper.getIfAvailable();
        List<EtfMarketRow> rows = mapper == null ? List.of() : mapper.findMarketRows();
        int cachedQuotes = (int) rows.stream().filter(row -> row.getCurrentPrice() != null).count();
        int advancers = (int) rows.stream().filter(row -> positive(row)).count();
        int decliners = (int) rows.stream().filter(row -> changePercent(row) != null && !positive(row)).count();
        BigDecimal averageExpense = average(rows.stream().map(EtfMarketRow::getExpenseRatio).toList());
        BigDecimal averageChange = average(rows.stream().map(EtfMarketViewService::changePercent).toList());

        List<EtfMarketView.Row> viewRows = new ArrayList<>();
        int rank = 1;
        for (EtfMarketRow row : rows) {
            List<EtfCandleDaily> candles = mapper == null ? List.of() : mapper.findRecentCandles(row.getSymbol(), 80);
            viewRows.add(toViewRow(row, candles, rank++));
        }

        List<EtfMarketView.Row> topMovers = rows.stream()
                .filter(row -> changePercent(row) != null)
                .sorted(Comparator.comparing((EtfMarketRow row) -> changePercent(row).abs()).reversed())
                .limit(8)
                .map(row -> toViewRow(row, mapper == null ? List.of() : mapper.findRecentCandles(row.getSymbol(), 80), 0))
                .toList();

        return new EtfMarketView(
                "ETF",
                "주요 ETF의 가격, 비용, 유동성, 추세를 ETF 전용 Signal로 요약합니다.",
                String.valueOf(rows.size()),
                String.valueOf(cachedQuotes),
                String.valueOf(advancers),
                String.valueOf(decliners),
                formatPercent(averageExpense),
                formatPercent(averageChange),
                cards(rows.size(), cachedQuotes, advancers, decliners, averageExpense, averageChange),
                viewRows,
                topMovers,
                categories(rows));
    }

    public EtfDetailView detail(String symbol) {
        String normalized = normalize(symbol);
        EtfMapper mapper = etfMapper.getIfAvailable();
        EtfMarketRow row = mapper == null ? null : mapper.findMarketRowBySymbol(normalized);
        if (row == null) {
            row = missing(normalized);
        }
        List<EtfCandleDaily> candles = mapper == null ? List.of() : mapper.findRecentCandles(normalized, 260);
        EtfSignal signal = signalService.build(row, candles);
        return new EtfDetailView(
                row.getSymbol(),
                row.getName(),
                row.getIssuer(),
                row.getCategory(),
                row.getStrategy(),
                row.getAssetClass(),
                row.getBenchmark(),
                row.getDescription(),
                row.getWebsite(),
                row.getCurrency(),
                formatPriceKrw(row.getCurrentPrice()),
                formatPercent(changePercent(row)),
                formatMoneyUsd(row.getCurrentPrice()),
                formatVolume(row.getVolume()),
                formatAum(row.getAumMillion()),
                formatPercent(row.getExpenseRatio()),
                formatPercent(row.getDividendYield()),
                row.getHoldingsCount() == null ? "-" : row.getHoldingsCount() + "개",
                row.getInceptionDate() == null ? "-" : row.getInceptionDate().toString(),
                row.getLatestTradeDate() == null ? "일봉 데이터 없음" : row.getLatestTradeDate() + " 기준 Yahoo chart",
                signal,
                infoRows(row),
                chartRows(candles));
    }

    private EtfMarketView.Row toViewRow(EtfMarketRow row, List<EtfCandleDaily> candles, int rank) {
        BigDecimal change = changePercent(row);
        EtfSignal signal = signalService.build(row, candles);
        return new EtfMarketView.Row(
                rank <= 0 ? "-" : String.valueOf(rank),
                row.getSymbol(),
                row.getName(),
                value(row.getIssuer()),
                value(row.getCategory()),
                value(row.getStrategy()),
                value(row.getAssetClass()),
                formatPriceKrw(row.getCurrentPrice()),
                formatPercent(change),
                formatAum(row.getAumMillion()),
                formatPercent(row.getExpenseRatio()),
                formatPercent(row.getDividendYield()),
                formatVolume(row.getVolume()),
                signal.score() + " / 100",
                signal.tone(),
                change != null && change.compareTo(BigDecimal.ZERO) >= 0,
                signal.score());
    }

    private static List<EtfMarketView.Card> cards(int total, int cachedQuotes, int advancers, int decliners,
            BigDecimal averageExpense, BigDecimal averageChange) {
        return List.of(
                new EtfMarketView.Card("ETF 수", String.valueOf(total), cachedQuotes + " quote cached", true),
                new EtfMarketView.Card("상승", String.valueOf(advancers), "하락 " + decliners, advancers >= decliners),
                new EtfMarketView.Card("평균 비용", formatPercent(averageExpense), "manual profile 기준", true),
                new EtfMarketView.Card("평균 등락률", formatPercent(averageChange), "전일종가 대비",
                        averageChange != null && averageChange.compareTo(BigDecimal.ZERO) >= 0));
    }

    private static List<EtfMarketView.CategoryRow> categories(List<EtfMarketRow> rows) {
        Map<String, List<EtfMarketRow>> byCategory = new LinkedHashMap<>();
        for (EtfMarketRow row : rows) {
            byCategory.computeIfAbsent(value(row.getCategory()), ignored -> new ArrayList<>()).add(row);
        }
        return byCategory.entrySet().stream()
                .map(entry -> new EtfMarketView.CategoryRow(
                        entry.getKey(),
                        String.valueOf(entry.getValue().size()),
                        formatPercent(average(entry.getValue().stream().map(EtfMarketRow::getExpenseRatio).toList())),
                        formatPercent(average(entry.getValue().stream().map(EtfMarketViewService::changePercent).toList()))))
                .toList();
    }

    private static List<EtfDetailView.InfoRow> infoRows(EtfMarketRow row) {
        return List.of(
                new EtfDetailView.InfoRow("운용사", value(row.getIssuer())),
                new EtfDetailView.InfoRow("카테고리", value(row.getCategory())),
                new EtfDetailView.InfoRow("전략", value(row.getStrategy())),
                new EtfDetailView.InfoRow("자산군", value(row.getAssetClass())),
                new EtfDetailView.InfoRow("지역", value(row.getRegion())),
                new EtfDetailView.InfoRow("벤치마크", value(row.getBenchmark())),
                new EtfDetailView.InfoRow("총보수", formatPercent(row.getExpenseRatio())),
                new EtfDetailView.InfoRow("보유 종목", row.getHoldingsCount() == null ? "-" : row.getHoldingsCount() + "개"));
    }

    private static List<EtfDetailView.ChartPoint> chartRows(List<EtfCandleDaily> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        List<EtfCandleDaily> ordered = candles.stream()
                .sorted(Comparator.comparing(EtfCandleDaily::getTradeDate))
                .toList();
        BigDecimal min = ordered.stream().map(EtfCandleDaily::getClosePrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = ordered.stream().map(EtfCandleDaily::getClosePrice).max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        BigDecimal range = max.subtract(min);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            range = BigDecimal.ONE;
        }
        int step = Math.max(1, ordered.size() / 36);
        List<EtfDetailView.ChartPoint> points = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index += step) {
            EtfCandleDaily candle = ordered.get(index);
            BigDecimal height = candle.getClosePrice().subtract(min).multiply(BigDecimal.valueOf(72))
                    .divide(range, 2, RoundingMode.HALF_UP).add(BigDecimal.valueOf(12));
            points.add(new EtfDetailView.ChartPoint(candle.getTradeDate().toString(),
                    formatMoneyUsd(candle.getClosePrice()), height.toPlainString()));
        }
        return points;
    }

    private static EtfMarketRow missing(String symbol) {
        EtfMarketRow row = new EtfMarketRow();
        row.setSymbol(symbol);
        row.setName(symbol);
        row.setCurrency("USD");
        row.setDescription("등록되지 않은 ETF입니다. ETF profile seed에 추가하면 리스트와 상세 화면에 표시됩니다.");
        return row;
    }

    private static BigDecimal changePercent(EtfMarketRow row) {
        if (row.getCurrentPrice() == null || row.getPreviousClose() == null
                || row.getPreviousClose().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return row.getCurrentPrice().subtract(row.getPreviousClose()).multiply(BigDecimal.valueOf(100))
                .divide(row.getPreviousClose(), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> valid = values.stream().filter(value -> value != null).toList();
        if (valid.isEmpty()) {
            return null;
        }
        return valid.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valid.size()), 4, RoundingMode.HALF_UP);
    }

    private static boolean positive(EtfMarketRow row) {
        BigDecimal change = changePercent(row);
        return change != null && change.compareTo(BigDecimal.ZERO) >= 0;
    }

    private static String formatPriceKrw(BigDecimal usdPrice) {
        if (usdPrice == null) {
            return "-";
        }
        BigDecimal krw = usdPrice.multiply(USD_KRW_RATE).setScale(0, RoundingMode.HALF_UP);
        return WON_FORMAT.format(krw) + "원";
    }

    private static String formatMoneyUsd(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return "$" + value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String formatAum(BigDecimal aumMillion) {
        if (aumMillion == null) {
            return "-";
        }
        BigDecimal billion = aumMillion.divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
        return ONE_DECIMAL_FORMAT.format(billion) + "B USD";
    }

    private static String formatVolume(Long volume) {
        return volume == null ? "-" : String.format("%,d", volume);
    }

    private static String formatPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
