package com.kingyurina.demo.stock;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface IndexConstituentMapper {

    LocalDateTime findLatestSeenAt(@Param("indexCode") String indexCode);

    void markAllNotCurrent(@Param("indexCode") String indexCode);

    void upsertCurrent(IndexConstituent constituent);

    void markRemoved(@Param("indexCode") String indexCode);

    int countCurrent(@Param("indexCode") String indexCode);

    int countTotal(@Param("indexCode") String indexCode);

    List<String> findCurrentSymbols(@Param("indexCode") String indexCode);

    List<String> findMemberSymbolsOnDate(@Param("indexCode") String indexCode,
            @Param("snapshotDate") LocalDate snapshotDate);

    List<String> findCurrentSymbolsByIndexCodes(@Param("indexCodes") List<String> indexCodes,
            @Param("limit") int limit);

    default List<StockMarketRow> findMarketRows(String indexCode) {
        return findRawMarketRows(indexCode).stream().map(StockPricePolicy::apply).toList();
    }

    List<StockMarketRow> findRawMarketRows(@Param("indexCode") String indexCode);

    default List<StockMarketRow> findMarketRowsBySymbols(List<String> symbols) {
        return findRawMarketRowsBySymbols(symbols).stream().map(StockPricePolicy::apply).toList();
    }

    List<StockMarketRow> findRawMarketRowsBySymbols(@Param("symbols") List<String> symbols);
}
