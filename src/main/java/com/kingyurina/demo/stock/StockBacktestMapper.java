package com.kingyurina.demo.stock;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockBacktestMapper {

    List<StockBacktestCoverage> findCoverage(@Param("indexCode") String indexCode);

    int countSnapshots(@Param("indexCode") String indexCode);

    int countResults(@Param("indexCode") String indexCode);

    int countResultsByHorizon(@Param("indexCode") String indexCode, @Param("horizonDays") int horizonDays);

    List<StockSignalSnapshot> findSnapshotsNeedingResults(@Param("limit") int limit);

    List<StockCandleDaily> findFutureCandles(@Param("symbol") String symbol,
            @Param("signalDate") LocalDate signalDate, @Param("limit") int limit);

    List<StockCandleDaily> findRiskCandles(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    String findSectorForSymbol(@Param("symbol") String symbol);

    void upsertResult(StockBacktestResult result);

    List<StockBacktestResult> findResults(@Param("indexCode") String indexCode, @Param("limit") int limit);
}
