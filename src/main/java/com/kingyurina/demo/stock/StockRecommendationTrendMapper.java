package com.kingyurina.demo.stock;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockRecommendationTrendMapper {

    LocalDateTime findLatestFetchedAt(@Param("symbol") String symbol);

    List<StockRecommendationTrend> findRecentBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);

    void upsertBatch(@Param("items") List<StockRecommendationTrend> items);
}
