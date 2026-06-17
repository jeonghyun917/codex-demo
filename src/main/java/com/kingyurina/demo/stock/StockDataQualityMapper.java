package com.kingyurina.demo.stock;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockDataQualityMapper {

    StockDataQualityLatest findBySymbol(@Param("symbol") String symbol);

    void upsert(StockDataQualityLatest quality);
}
