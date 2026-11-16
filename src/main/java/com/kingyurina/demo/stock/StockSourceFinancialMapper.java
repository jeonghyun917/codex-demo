package com.kingyurina.demo.stock;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockSourceFinancialMapper {
    List<StockSourceFinancialDocument> findBySymbol(@Param("symbol") String symbol,@Param("version") String version);
}
