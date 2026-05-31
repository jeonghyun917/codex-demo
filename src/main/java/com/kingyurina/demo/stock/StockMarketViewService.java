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
                indexCards(rows.size(), cachedQuotes, advancers, decliners, aggregateMarketCap, averageChange),
                viewRows,
                topMovers,
                sectorRows(rows));
    }

    private static List<StockMarketView.IndexCard> indexCards(int total, int cachedQuotes, int advancers, int decliners,
            BigDecimal marketCap, BigDecimal averageChange) {
        return List.of(
                new StockMarketView.IndexCard("구성종목", String.valueOf(total), cachedQuotes + " quote cached", true),
                new StockMarketView.IndexCard("상승", String.valueOf(advancers), "하락 " + decliners, advancers >= decliners),
                new StockMarketView.IndexCard("합산 시가총액", formatMarketCapKrw(marketCap), "company_profile 기준", true),
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
                    return new StockMarketView.SectorRow(entry.getKey(), String.valueOf(entry.getValue().size()),
                            formatMarketCapKrw(marketCap), formatPercent(change));
                })
                .sorted(Comparator.comparing(StockMarketView.SectorRow::marketCap).reversed())
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
                formatRatio(row.getPeNormalizedAnnual(), "배"),
                formatRatio(row.getPbAnnual(), "배"),
                formatPercent(row.getRoeTtm()),
                includeSignal ? formatSignalScore(row.getSignalScore()) : "-",
                includeSignal && row.getSignalConfidence() != null ? row.getSignalConfidence() : "-",
                includeSignal && row.getSignalTone() != null ? row.getSignalTone() : "neutral",
                change != null && change.compareTo(BigDecimal.ZERO) >= 0);
    }

    private static MarketSignal marketSignal(StockMarketRow row, BigDecimal change) {
        List<Integer> scores = new ArrayList<>();
        scores.add(valuationScore(row));
        scores.add(qualityScore(row));
        scores.add(earningsScore(row));
        scores.add(analystScore(row));
        scores.add(newsScore(row));
        scores.add(momentumScore(change));

        List<Integer> availableScores = scores.stream().filter(score -> score != null).toList();
        if (availableScores.isEmpty()) {
            return new MarketSignal("-", "매우 낮음", "neutral");
        }
        int score = Math.round((float) availableScores.stream().mapToInt(Integer::intValue).sum()
                / availableScores.size());
        String confidence = availableScores.size() >= 5 ? "보통"
                : availableScores.size() >= 3 ? "낮음" : "매우 낮음";
        return new MarketSignal(String.valueOf(score), confidence, signalTone(score));
    }

    private static Integer valuationScore(StockMarketRow row) {
        if (row.getPeNormalizedAnnual() == null && row.getPbAnnual() == null) {
            return null;
        }
        int score = 55;
        BigDecimal pe = row.getPeNormalizedAnnual();
        BigDecimal pb = row.getPbAnnual();
        if (pe != null) {
            if (lte(pe, "20")) {
                score += 14;
            } else if (gte(pe, "80")) {
                score -= 24;
            } else if (gte(pe, "50")) {
                score -= 16;
            } else if (gte(pe, "35")) {
                score -= 8;
            }
        }
        if (pb != null) {
            if (lte(pb, "5")) {
                score += 8;
            } else if (gte(pb, "20")) {
                score -= 16;
            } else if (gte(pb, "10")) {
                score -= 8;
            }
        }
        return clamp(score);
    }

    private static Integer qualityScore(StockMarketRow row) {
        if (row.getRoeTtm() == null) {
            return null;
        }
        int score = 50;
        BigDecimal roe = row.getRoeTtm();
        if (gte(roe, "50")) {
            score += 30;
        } else if (gte(roe, "25")) {
            score += 22;
        } else if (gte(roe, "12")) {
            score += 12;
        } else if (lte(roe, "0")) {
            score -= 25;
        } else if (lte(roe, "8")) {
            score -= 10;
        }
        return clamp(score);
    }

    private static Integer earningsScore(StockMarketRow row) {
        BigDecimal surprise = row.getLatestSurprisePercent();
        if (surprise == null) {
            return null;
        }
        return clamp(50 + cappedInt(surprise, -25, 25));
    }

    private static Integer analystScore(StockMarketRow row) {
        int bullish = count(row.getAnalystBullish());
        int neutral = count(row.getAnalystNeutral());
        int bearish = count(row.getAnalystBearish());
        int total = bullish + neutral + bearish;
        if (total == 0) {
            return null;
        }
        return clamp(50 + ((bullish - bearish) * 50 / total));
    }

    private static Integer newsScore(StockMarketRow row) {
        Integer count = row.getRecentNewsCount();
        if (count == null) {
            return null;
        }
        return clamp(50 + Math.min(count, 10));
    }

    private static Integer momentumScore(BigDecimal change) {
        if (change == null) {
            return null;
        }
        return clamp(50 + cappedInt(change.multiply(BigDecimal.valueOf(4)), -24, 24));
    }

    private static String signalTone(int score) {
        if (score >= 67) {
            return "positive";
        }
        if (score >= 52) {
            return "neutral";
        }
        if (score >= 40) {
            return "caution";
        }
        return "negative";
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean gte(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) >= 0;
    }

    private static boolean lte(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) <= 0;
    }

    private static int cappedInt(BigDecimal value, int min, int max) {
        if (value == null) {
            return 0;
        }
        return Math.max(min, Math.min(max, value.setScale(0, RoundingMode.HALF_UP).intValue()));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
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

    private static String formatMoney(BigDecimal value, String suffix) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + suffix;
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

    private record MarketSignal(String scoreLabel, String confidence, String tone) {
    }
}
