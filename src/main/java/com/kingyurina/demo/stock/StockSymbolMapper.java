package com.kingyurina.demo.stock;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockSymbolMapper {

    List<String> findActiveSymbols(@Param("limit") int limit);

    String findSymbolByQuery(@Param("query") String query);

    void upsertSymbol(@Param("symbol") String symbol, @Param("name") String name,
            @Param("exchange") String exchange, @Param("currency") String currency,
            @Param("collectTier") String collectTier);

    void markCollected(@Param("symbol") String symbol);
}
