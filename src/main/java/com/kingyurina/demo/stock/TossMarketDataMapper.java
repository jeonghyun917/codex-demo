package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface TossMarketDataMapper {

    void insertBatchRun(TossBatchRun run);

    void finishBatchRun(TossBatchRun run);

    void insertPriceSnapshot(@Param("symbol") String symbol,
            @Param("priceTime") LocalDateTime priceTime,
            @Param("lastPrice") BigDecimal lastPrice,
            @Param("currency") String currency,
            @Param("rawJson") String rawJson);

    void upsertStockInfo(@Param("symbol") String symbol,
            @Param("name") String name,
            @Param("englishName") String englishName,
            @Param("isinCode") String isinCode,
            @Param("market") String market,
            @Param("securityType") String securityType,
            @Param("commonShare") Boolean commonShare,
            @Param("status") String status,
            @Param("currency") String currency,
            @Param("listDate") LocalDate listDate,
            @Param("delistDate") LocalDate delistDate,
            @Param("sharesOutstanding") BigDecimal sharesOutstanding,
            @Param("leverageFactor") BigDecimal leverageFactor,
            @Param("rawJson") String rawJson);

    void deleteWarnings(@Param("symbol") String symbol);

    void upsertWarning(@Param("symbol") String symbol,
            @Param("warningType") String warningType,
            @Param("exchange") String exchange,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("rawJson") String rawJson);

    void upsertCandle(@Param("symbol") String symbol,
            @Param("intervalCode") String intervalCode,
            @Param("candleTime") LocalDateTime candleTime,
            @Param("tradeDate") LocalDate tradeDate,
            @Param("openPrice") BigDecimal openPrice,
            @Param("highPrice") BigDecimal highPrice,
            @Param("lowPrice") BigDecimal lowPrice,
            @Param("closePrice") BigDecimal closePrice,
            @Param("volume") BigDecimal volume,
            @Param("currency") String currency,
            @Param("adjusted") boolean adjusted,
            @Param("rawJson") String rawJson);

    void insertOrderbookSnapshot(@Param("symbol") String symbol,
            @Param("orderbookTime") LocalDateTime orderbookTime,
            @Param("currency") String currency,
            @Param("bestAskPrice") BigDecimal bestAskPrice,
            @Param("bestAskVolume") BigDecimal bestAskVolume,
            @Param("bestBidPrice") BigDecimal bestBidPrice,
            @Param("bestBidVolume") BigDecimal bestBidVolume,
            @Param("spread") BigDecimal spread,
            @Param("spreadBps") BigDecimal spreadBps,
            @Param("rawJson") String rawJson);

    void upsertTradePrint(@Param("symbol") String symbol,
            @Param("tradeTime") LocalDateTime tradeTime,
            @Param("price") BigDecimal price,
            @Param("volume") BigDecimal volume,
            @Param("currency") String currency,
            @Param("rawJson") String rawJson);

    void insertPriceLimitSnapshot(@Param("symbol") String symbol,
            @Param("limitTime") LocalDateTime limitTime,
            @Param("upperLimitPrice") BigDecimal upperLimitPrice,
            @Param("lowerLimitPrice") BigDecimal lowerLimitPrice,
            @Param("currency") String currency,
            @Param("rawJson") String rawJson);

    void insertExchangeRateSnapshot(@Param("baseCurrency") String baseCurrency,
            @Param("quoteCurrency") String quoteCurrency,
            @Param("rate") BigDecimal rate,
            @Param("midRate") BigDecimal midRate,
            @Param("basisPoint") BigDecimal basisPoint,
            @Param("rateChangeType") String rateChangeType,
            @Param("validFrom") LocalDateTime validFrom,
            @Param("validUntil") LocalDateTime validUntil,
            @Param("rawJson") String rawJson);

    void insertCalendarSnapshot(@Param("marketCountry") String marketCountry,
            @Param("queryDate") LocalDate queryDate,
            @Param("todayDate") LocalDate todayDate,
            @Param("todayOpen") Boolean todayOpen,
            @Param("previousBusinessDate") LocalDate previousBusinessDate,
            @Param("nextBusinessDate") LocalDate nextBusinessDate,
            @Param("rawJson") String rawJson);
}
