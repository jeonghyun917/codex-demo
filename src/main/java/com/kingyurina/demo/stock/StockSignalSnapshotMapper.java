package com.kingyurina.demo.stock;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockSignalSnapshotMapper {

    StockSignalSnapshot findLatestBySymbol(@Param("symbol") String symbol);

    void upsert(StockSignalSnapshot snapshot);
}
