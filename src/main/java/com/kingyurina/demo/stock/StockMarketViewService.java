package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockMarketViewService {

    private static final BigDecimal USD_KRW_RATE = BigDecimal.valueOf(1365);
    private static final DecimalFormat ONE_DECIMAL_FORMAT = new DecimalFormat("#,##0.0");
    private static final DecimalFormat WON_FORMAT = new DecimalFormat("#,##0");

    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;

    public StockMarketViewService(ObjectProvider<IndexConstituentMapper> indexConstituentMapper) {
        this.indexConstituentMapper = indexConstituentMapper;
    }

    public StockMarketView sp500() {
        IndexConstituentMapper mapper = indexConstituentMapper.getIfAvailable();
        List<StockMarketRow> rows = mapper == null ? List.of() : mapper.findMarketRows("SP500");
        int cachedQuotes = (int) rows.stream().filter(row -> row.getCurrentPrice() != null).count();
        int advancers = (int) rows.stream().filter(row -> changePercent(row) != null
                && changePercent(row).compareTo(BigDecimal.ZERO) >= 0).count();
        int decliners = (int) rows.stream().filter(row -> changePercent(row) != null
                && changePercent(row).compareTo(BigDecimal.ZERO) < 0).count();
        int signalCount = (int) rows.stream().map(StockMarketRow::getSignalScore).filter(score -> score != null).count();
        BigDecimal averageSignalProbability = rows.stream()
                .map(StockMarketRow::getSignalScore)
                .filter(score -> score != null)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (signalCount > 0) {
            averageSignalProbability = averageSignalProbability.divide(BigDecimal.valueOf(signalCount), 2,
                    RoundingMode.HALF_UP);
        }
        int strongBuySignals = (int) rows.stream()
                .map(StockMarketRow::getSignalScore)
                .filter(score -> score != null && score >= 70)
                .count();
        BigDecimal aggregateMarketCap = rows.stream()
                .map(StockMarketRow::getMarketCap)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageChange = rows.stream()
                .map(StockMarketViewService::changePercent)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int changeCount = (int) rows.stream().map(StockMarketViewService::changePercent).filter(value -> value != null).count();
        if (changeCount > 0) {
            averageChange = averageChange.divide(BigDecimal.valueOf(changeCount), 4, RoundingMode.HALF_UP);
        }

        List<StockMarketView.Row> viewRows = new ArrayList<>();
        int rank = 1;
        for (StockMarketRow row : rows) {
            viewRows.add(toViewRow(row, rank++, true));
        }

        List<StockMarketView.Row> topMovers = rows.stream()
                .filter(row -> changePercent(row) != null)
                .sorted(Comparator.comparing((StockMarketRow row) -> changePercent(row).abs()).reversed())
                .limit(8)
                .map(row -> toViewRow(row, 0, false))
                .toList();

        return new StockMarketView(
                "S&P 500",
                "미국 대형주 500개 구성종목의 캐시된 현재가, 시총, 밸류에이션 요약입니다.",
                String.valueOf(rows.size()),
                String.valueOf(cachedQuotes),
                String.valueOf(advancers),
                String.valueOf(decliners),
                formatMarketCapKrw(aggregateMarketCap),
                formatPercent(averageChange),
                indexCards(rows.size(), cachedQuotes, advancers, decliners, averageSignalProbability, signalCount,
                        strongBuySignals, averageChange),
                viewRows,
                topMovers,
                sectorRows(rows));
    }

    private static List<StockMarketView.IndexCard> indexCards(int total, int cachedQuotes, int advancers, int decliners,
            BigDecimal averageSignalProbability, int signalCount, int strongBuySignals, BigDecimal averageChange) {
        return List.of(
                new StockMarketView.IndexCard("구성종목", String.valueOf(total), cachedQuotes + " quote cached", true),
                new StockMarketView.IndexCard("상승", String.valueOf(advancers), "하락 " + decliners, advancers >= decliners),
                new StockMarketView.IndexCard("Signal 평균", formatPercent(signalCount > 0 ? averageSignalProbability : null),
                        signalCount + "개 Signal 기준", signalCount > 0
                                && averageSignalProbability.compareTo(BigDecimal.valueOf(50)) >= 0),
                new StockMarketView.IndexCard("강한 Signal", strongBuySignals + "개", "Signal 70%+", strongBuySignals > 0),
                new StockMarketView.IndexCard("평균 등락률", formatPercent(averageChange), "전일종가 대비",
                        averageChange != null && averageChange.compareTo(BigDecimal.ZERO) >= 0));
    }

    private static List<StockMarketView.SectorRow> sectorRows(List<StockMarketRow> rows) {
        Map<String, List<StockMarketRow>> bySector = new LinkedHashMap<>();
        for (StockMarketRow row : rows) {
            String sector = row.getSector() == null ? "Unknown" : row.getSector();
            bySector.computeIfAbsent(sector, ignored -> new ArrayList<>()).add(row);
        }
        return bySector.entrySet().stream()
                .map(entry -> {
                    BigDecimal marketCap = entry.getValue().stream()
                            .map(StockMarketRow::getMarketCap)
                            .filter(value -> value != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal change = entry.getValue().stream()
                            .map(StockMarketViewService::changePercent)
                            .filter(value -> value != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long count = entry.getValue().stream().map(StockMarketViewService::changePercent)
                            .filter(value -> value != null).count();
                    if (count > 0) {
                        change = change.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
                    }
                    return new SectorSummary(entry.getKey(), String.valueOf(entry.getValue().size()),
                            marketCap, formatMarketCapKrw(marketCap), formatPercent(change));
                })
                .sorted(Comparator.comparing(SectorSummary::marketCap).reversed())
                .map(summary -> new StockMarketView.SectorRow(summary.sector(), summary.count(),
                        summary.formattedMarketCap(), summary.averageChange()))
                .toList();
    }

    private StockMarketView.Row toViewRow(StockMarketRow row, int rank, boolean includeSignal) {
        BigDecimal change = changePercent(row);
        return new StockMarketView.Row(
                rank <= 0 ? "-" : String.valueOf(rank),
                row.getSymbol(),
                row.getName() == null ? row.getSymbol() : row.getName(),
                blankToNull(row.getLogo()),
                logoInitial(row.getSymbol()),
                row.getSector() == null ? "-" : row.getSector(),
                formatPriceKrw(row.getCurrentPrice()),
                formatPercent(change),
                formatMarketCapKrw(row.getMarketCap()),
                formatInstitutionFlow(row),
                formatRatio(clean(row, "peNormalizedAnnual", row.getPeNormalizedAnnual()), "배"),
                formatRatio(clean(row, "pbAnnual", row.getPbAnnual()), "배"),
                formatPercent(clean(row, "roeTTM", row.getRoeTtm())),
                includeSignal ? formatSignalScore(row.getSignalScore()) : "-",
                includeSignal && row.getSignalConfidence() != null ? row.getSignalConfidence() : "-",
                includeSignal && row.getSignalTone() != null ? row.getSignalTone() : "neutral",
                change != null && change.compareTo(BigDecimal.ZERO) >= 0,
                row.getSignalScore(),
                includeSignal ? formatSignalScore(row.getDataQualityScore()) : "-",
                includeSignal && row.getDataQualityTone() != null ? row.getDataQualityTone() : "neutral",
                row.getDataQualityScore(),
                row.getValuationScore(),
                row.getQualityScore(),
                row.getGrowthScore(),
                row.getStabilityScore(),
                row.getEarningsScore(),
                row.getAnalystScore(),
                row.getNewsScore(),
                row.getMomentumScore(),
                row.getRiskScore());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String logoInitial(String symbol) {
        return symbol == null || symbol.isBlank() ? "?" : symbol.substring(0, 1).toUpperCase();
    }

    private static BigDecimal changePercent(StockMarketRow row) {
        if (row.getCurrentPrice() == null || row.getPreviousClose() == null
                || row.getPreviousClose().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return row.getCurrentPrice()
                .subtract(row.getPreviousClose())
                .multiply(BigDecimal.valueOf(100))
                .divide(row.getPreviousClose(), 4, RoundingMode.HALF_UP);
    }

    private static String formatMarketCapKrw(BigDecimal marketCapMillionUsd) {
        if (marketCapMillionUsd == null) {
            return "-";
        }
        BigDecimal trillionKrw = marketCapMillionUsd
                .multiply(USD_KRW_RATE)
                .divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP);
        return ONE_DECIMAL_FORMAT.format(trillionKrw) + " 조원";
    }

    private static String formatPriceKrw(BigDecimal usdPrice) {
        if (usdPrice == null) {
            return "-";
        }
        BigDecimal krw = usdPrice.multiply(USD_KRW_RATE).setScale(0, RoundingMode.HALF_UP);
        return WON_FORMAT.format(krw) + "원";
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

    private static String formatSignalScore(Integer score) {
        return score == null ? "-" : score + " / 100";
    }

    private static String formatInstitutionFlow(StockMarketRow row) {
        if (row.getInstitutionSharesChangePct() == null) {
            return "-";
        }
        String holders = row.getInstitutionHolderCount() == null ? "" : " · " + row.getInstitutionHolderCount() + "곳";
        return formatPercent(row.getInstitutionSharesChangePct()) + holders;
    }

    private static BigDecimal clean(StockMarketRow row, String field, BigDecimal value) {
        return isExcluded(row, field) ? null : value;
    }

    private static boolean isExcluded(StockMarketRow row, String field) {
        String json = row.getDataQualityExcludedFieldsJson();
        return json != null && field != null && json.toLowerCase().contains(("\"" + field + "\"").toLowerCase());
    }

    private record SectorSummary(String sector, String count, BigDecimal marketCap, String formattedMarketCap,
            String averageChange) {
    }
}
