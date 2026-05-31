package com.kingyurina.demo.stock;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface CompanyProfileMapper {

    CompanyProfile findBySymbol(String symbol);

    List<StockPeerComparison> findPeersByIndustry(@Param("industry") String industry, @Param("limit") int limit);

    void upsert(CompanyProfile profile);
}
