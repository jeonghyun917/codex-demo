package com.kingyurina.demo.stock;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockMetricMapper {

    StockMetricSnapshot findLatestBySymbol(String symbol);

    void upsert(StockMetricSnapshot metric);
}
