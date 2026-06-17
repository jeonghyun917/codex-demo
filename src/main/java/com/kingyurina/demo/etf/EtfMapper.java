package com.kingyurina.demo.etf;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface EtfMapper {

    List<EtfMarketRow> findMarketRows();

    EtfMarketRow findMarketRowBySymbol(@Param("symbol") String symbol);

    List<String> findActiveSymbols(@Param("limit") int limit);

    List<EtfCandleDaily> findRecentCandles(@Param("symbol") String symbol, @Param("limit") int limit);

    void upsertQuote(EtfQuoteCache quote);

    void upsertCandles(@Param("items") List<EtfCandleDaily> items);
}
