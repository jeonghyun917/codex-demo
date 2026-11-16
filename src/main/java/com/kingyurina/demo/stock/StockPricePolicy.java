package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Selects a complete dated tuple, never a price-by-price COALESCE or inferred split ratio. */
public final class StockPricePolicy {
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private StockPricePolicy() { }

    public static StockDashboard normalize(StockDashboard dashboard) {
        // Already-selected close tuples must not be reinterpreted as raw provider quotes.
        StockQuoteCache quote = dashboard.quote();
        if (quote != null && quote.getPriceKind() != null) { return dashboard; }
        return new StockDashboard(dashboard.symbol(), dashboard.apiConfigured(), dashboard.databaseEnabled(),
                dashboard.quoteCacheHit(), dashboard.profileCacheHit(), dashboard.metricCacheHit(),
                dashboard.newsCacheHit(), dashboard.recommendationCacheHit(), dashboard.epsSurpriseCacheHit(),
                dashboard.candleCacheHit(), dashboard.message(), dashboard.profile(), select(quote, dashboard.candles()),
                dashboard.metric(), dashboard.news(), dashboard.recommendations(), dashboard.epsSurprises(),
                dashboard.candles(), dashboard.summary());
    }

    public static StockMarketRow apply(StockMarketRow row) {
        StockQuoteCache raw = new StockQuoteCache();
        raw.setSymbol(row.getSymbol()); raw.setCurrentPrice(row.getCurrentPrice());
        raw.setPreviousClose(row.getPreviousClose()); raw.setOpenPrice(row.getOpenPrice());
        raw.setHighPrice(row.getHighPrice()); raw.setLowPrice(row.getLowPrice()); raw.setQuoteTime(row.getQuoteTime());
        List<StockCandleDaily> candles = new java.util.ArrayList<>();
        if (row.getCandleDate() != null) {
            StockCandleDaily last = new StockCandleDaily();
            last.setSymbol(row.getSymbol()); last.setTradeDate(row.getCandleDate()); last.setSource(row.getCandleSource());
            last.setClosePrice(row.getCandleClose()); last.setOpenPrice(row.getCandleOpen());
            last.setHighPrice(row.getCandleHigh()); last.setLowPrice(row.getCandleLow()); candles.add(last);
        }
        if (row.getPreviousCandleDate() != null) {
            StockCandleDaily prior = new StockCandleDaily();
            prior.setTradeDate(row.getPreviousCandleDate()); prior.setSource(row.getPreviousCandleSource());
            prior.setClosePrice(row.getCandlePreviousClose()); candles.add(prior);
        }
        StockQuoteCache selected = select(raw, candles);
        row.setCurrentPrice(selected == null ? null : selected.getCurrentPrice());
        row.setPreviousClose(selected == null ? null : selected.getPreviousClose());
        row.setOpenPrice(selected == null ? null : selected.getOpenPrice());
        row.setHighPrice(selected == null ? null : selected.getHighPrice());
        row.setLowPrice(selected == null ? null : selected.getLowPrice());
        row.setPriceAsOf(selected == null ? null : selected.getPriceAsOf());
        row.setPriceSource(selected == null ? null : selected.getPriceSource());
        row.setPriceKind(selected == null ? null : selected.getPriceKind());
        row.setPriceBasis(selected == null ? null : selected.getPriceBasis());
        if (selected == null || !"CLOSE".equals(selected.getPriceKind()) || !Integer.valueOf(1).equals(row.getCandleSourceCount())) {
            row.setThreeMonthHigh(null);
        }
        return row;
    }

    public static StockQuoteCache select(StockQuoteCache quote, List<StockCandleDaily> candles) {
        List<StockCandleDaily> sorted = candles == null ? List.of() : candles.stream()
                .filter(Objects::nonNull).filter(c -> c.getTradeDate() != null && positive(c.getClosePrice()))
                .sorted(Comparator.comparing(StockCandleDaily::getTradeDate).reversed()).toList();
        StockCandleDaily latest = sorted.isEmpty() ? null : sorted.getFirst();
        LocalDateTime closeTime = latest == null ? null : latest.getTradeDate().atTime(16, 0)
                .atZone(MARKET_ZONE).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        if (usableQuote(quote)
                && (closeTime == null || quote.getQuoteTime().isAfter(closeTime))) {
            StockQuoteCache selected = copy(quote);
            selected.setPreviousClose(positive(quote.getPreviousClose()) ? quote.getPreviousClose() : null);
            selected.setPriceAsOf(quote.getQuoteTime());
            selected.setPriceSource("FINNHUB");
            selected.setPriceKind("QUOTE");
            selected.setPriceBasis("PROVIDER_QUOTE_UNVERIFIED_ADJUSTMENT");
            return selected;
        }
        if (latest == null) { return null; }
        StockQuoteCache selected = new StockQuoteCache();
        selected.setSymbol(latest.getSymbol());
        selected.setCurrentPrice(latest.getClosePrice());
        selected.setOpenPrice(latest.getOpenPrice());
        selected.setHighPrice(latest.getHighPrice());
        selected.setLowPrice(latest.getLowPrice());
        if (sorted.size() > 1 && latest.getSource() != null && Objects.equals(latest.getSource(), sorted.get(1).getSource())) {
            selected.setPreviousClose(sorted.get(1).getClosePrice());
        }
        selected.setQuoteTime(closeTime);
        selected.setFetchedAt(latest.getUpdatedAt());
        selected.setPriceAsOf(closeTime);
        selected.setPriceSource(latest.getSource());
        selected.setPriceKind("CLOSE");
        selected.setPriceBasis("CANONICAL_PROVIDER_OHLC_UNVERIFIED_ADJUSTMENT");
        return selected;
    }

    public static BigDecimal threeMonthHigh(StockQuoteCache selected, List<StockCandleDaily> candles) {
        // Quote/candle adjustment metadata is absent; do not combine the two price bases.
        if (selected == null || !"CLOSE".equals(selected.getPriceKind())) { return null; }
        var latestDate = selected.getPriceAsOf().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(MARKET_ZONE).toLocalDate();
        List<StockCandleDaily> recent = candles.stream()
                .filter(c -> c.getTradeDate() != null && !c.getTradeDate().isAfter(latestDate)
                        && !c.getTradeDate().isBefore(latestDate.minusMonths(3))).toList();
        if (recent.stream().anyMatch(c -> !Objects.equals(selected.getPriceSource(), c.getSource()))) { return null; }
        return recent.stream().map(StockCandleDaily::getHighPrice).filter(StockPricePolicy::positive)
                .max(BigDecimal::compareTo).orElse(null);
    }

    private static StockQuoteCache copy(StockQuoteCache quote) {
        StockQuoteCache copy = new StockQuoteCache();
        copy.setSymbol(quote.getSymbol()); copy.setCurrentPrice(quote.getCurrentPrice());
        copy.setPreviousClose(quote.getPreviousClose()); copy.setOpenPrice(quote.getOpenPrice());
        copy.setHighPrice(quote.getHighPrice()); copy.setLowPrice(quote.getLowPrice());
        copy.setQuoteTime(quote.getQuoteTime()); copy.setFetchedAt(quote.getFetchedAt()); copy.setRawJson(quote.getRawJson());
        return copy;
    }

    static boolean usableQuote(StockQuoteCache quote) {
        return quote != null && positive(quote.getCurrentPrice()) && quote.getQuoteTime() != null
                && !quote.getQuoteTime().isAfter(LocalDateTime.now().plusMinutes(5))
                && (quote.getOpenPrice() == null || positive(quote.getOpenPrice()))
                && (quote.getHighPrice() == null || positive(quote.getHighPrice()))
                && (quote.getLowPrice() == null || positive(quote.getLowPrice()))
                && (quote.getHighPrice() == null || quote.getLowPrice() == null
                        || quote.getHighPrice().compareTo(quote.getLowPrice()) >= 0);
    }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
}
