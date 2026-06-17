package com.kingyurina.demo.stock;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface SecCompanyFactMapper {

    int countBySymbol(@Param("symbol") String symbol);

    List<SecCompanyFact> findBySymbol(@Param("symbol") String symbol);

    void upsertBatch(@Param("facts") List<SecCompanyFact> facts);
}
