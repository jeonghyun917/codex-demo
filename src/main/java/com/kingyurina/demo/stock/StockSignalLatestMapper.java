package com.kingyurina.demo.stock;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockSignalLatestMapper {

    StockSignalLatest findBySymbol(@Param("symbol") String symbol);

    void upsert(StockSignalLatest signal);
}
