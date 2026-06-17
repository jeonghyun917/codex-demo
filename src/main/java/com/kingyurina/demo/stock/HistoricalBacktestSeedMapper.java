package com.kingyurina.demo.stock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface HistoricalBacktestSeedMapper {

    LocalDate findLatestCandleDate();

    LocalDate findTradeDateOnOrBefore(@Param("targetDate") LocalDate targetDate);

    List<StockCandleDaily> findCandlesOnOrBefore(@Param("symbol") String symbol,
            @Param("signalDate") LocalDate signalDate, @Param("limit") int limit);

    SecFinancialStandard findLatestAnnualAsOf(@Param("symbol") String symbol,
            @Param("asOfDate") LocalDate asOfDate);

    SecFinancialStandard findPreviousAnnualAsOf(@Param("symbol") String symbol,
            @Param("endDate") LocalDate endDate, @Param("asOfDate") LocalDate asOfDate);

    SecFinancialStandard findLatestQuarterAsOf(@Param("symbol") String symbol,
            @Param("asOfDate") LocalDate asOfDate);

    SecFinancialStandard findPreviousQuarterAsOf(@Param("symbol") String symbol,
            @Param("endDate") LocalDate endDate, @Param("asOfDate") LocalDate asOfDate);

    StockRecommendationTrend findRecommendationAsOf(@Param("symbol") String symbol,
            @Param("asOfDate") LocalDate asOfDate);

    StockInstitutionFlow findInstitutionFlowAsOf(@Param("symbol") String symbol,
            @Param("availableQuarterCutoff") LocalDate availableQuarterCutoff);

    HistoricalNewsStats findNewsStatsAsOf(@Param("symbol") String symbol,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime);
}
