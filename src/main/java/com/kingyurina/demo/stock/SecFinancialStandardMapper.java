package com.kingyurina.demo.stock;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface SecFinancialStandardMapper {

    int countBySymbol(@Param("symbol") String symbol);

    List<String> findSymbolsMissingStandard(@Param("symbols") List<String> symbols,
            @Param("limit") int limit);

    SecFinancialStandard findLatestAnnual(@Param("symbol") String symbol);

    SecFinancialStandard findPreviousAnnual(@Param("symbol") String symbol,
            @Param("endDate") java.time.LocalDate endDate);

    SecFinancialStandard findLatestQuarter(@Param("symbol") String symbol);

    SecFinancialStandard findPreviousQuarter(@Param("symbol") String symbol,
            @Param("endDate") java.time.LocalDate endDate);

    void deleteBySymbol(@Param("symbol") String symbol);

    void upsertBatch(@Param("rows") List<SecFinancialStandard> rows);
}
