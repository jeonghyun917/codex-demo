package com.kingyurina.demo.stock;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface SecCompanyMapper {

    SecCompany findBySymbol(@Param("symbol") String symbol);

    void upsert(SecCompany company);

    List<String> findSymbolsWithStaleFacts(@Param("symbols") List<String> symbols,
            @Param("cacheHours") int cacheHours,
            @Param("limit") int limit);
}
