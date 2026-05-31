package com.kingyurina.demo.stock;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockQuoteMapper {

    StockQuoteCache findBySymbol(String symbol);

    void upsert(StockQuoteCache quote);
}
