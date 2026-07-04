package com.kingyurina.demo.stock;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockCandleMapper {

    int countBySymbol(@Param("symbol") String symbol);

    LocalDateTime findLatestUpdatedAt(@Param("symbol") String symbol);

    List<StockCandleDaily> findRecentBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);

    void upsertSourceBatch(@Param("items") List<StockCandleDaily> items);

    void upsertBatch(@Param("items") List<StockCandleDaily> items);
}
