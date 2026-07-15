package com.kingyurina.demo.stock;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

import com.kingyurina.demo.stock.evaluation.ExpectedReturnEvaluationExclusionCount;
import com.kingyurina.demo.stock.evaluation.ExpectedReturnEvaluationRun;
import com.kingyurina.demo.stock.evaluation.ExpectedReturnEvaluationWindowResult;

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

    List<StockRiskSnapshot> findRiskSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    StockRiskSnapshot findLatestRiskSnapshotBySymbol(@Param("symbol") String symbol);

    int refreshMarketSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    int refreshIndexMembershipSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    int deactivateStaleCurrentMembershipSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    int deleteStaleMarketSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    int refreshSharesOutstandingSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    int refreshSecSharesOutstandingSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<StockMarketSnapshot> findMarketSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<StockBenchmarkReturn> findBenchmarkReturns(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<StockRiskFreeRateSnapshot> findRiskFreeRateSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<StockMacroVintageSnapshot> findMacroVintageSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<StockMacroFeatureSnapshot> findMacroFeatureSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    StockMacroFeatureSnapshot findLatestMacroFeatureSnapshot(@Param("indexCode") String indexCode);

    List<LocalDate> findMacroRegimeSnapshotDates(@Param("indexCode") String indexCode, @Param("limit") int limit);

    List<StockMacroRegimeSnapshot> findMacroRegimeSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    StockMacroRegimeSnapshot findLatestMacroRegimeSnapshot(@Param("indexCode") String indexCode);

    List<LocalDate> findCovarianceSnapshotDates(@Param("indexCode") String indexCode, @Param("limit") int limit);

    List<String> findCovarianceCandidateSymbols(@Param("indexCode") String indexCode,
            @Param("signalDate") LocalDate signalDate, @Param("limit") int limit);

    List<StockCandleDaily> findCandlesForSymbols(@Param("symbols") List<String> symbols,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<StockCovarianceSnapshot> findCovarianceSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<StockExpectedReturnSnapshot> findExpectedReturnSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<StockBacktestResult> findResultsForEvaluation(@Param("indexCode") String indexCode,
            @Param("horizonDays") int horizonDays, @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    List<StockIndexMembershipSnapshot> findIndexMembershipSnapshots(@Param("indexCode") String indexCode,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<StockExpectedReturnCalibration> findExpectedReturnCalibrations(@Param("indexCode") String indexCode,
            @Param("modelVersion") String modelVersion);

    StockPortfolioViewSnapshot findLatestPortfolioViewSnapshot(@Param("indexCode") String indexCode,
            @Param("viewVersion") String viewVersion);

    StockDashboardViewSnapshot findLatestDashboardViewSnapshot(@Param("indexCode") String indexCode,
            @Param("viewVersion") String viewVersion);

    StockBacktestViewSnapshot findLatestBacktestViewSnapshot(@Param("indexCode") String indexCode,
            @Param("viewVersion") String viewVersion);

    List<StockExpectedReturnSnapshot> findLatestExpectedReturnSnapshotsBySymbol(@Param("symbol") String symbol,
            @Param("modelVersion") String modelVersion);

    StockMarketSnapshot findLatestMarketSnapshotBySymbol(@Param("symbol") String symbol);

    List<StockExpectedReturnFactorContribution> findExpectedReturnFactorContributions(
            @Param("indexCode") String indexCode,
            @Param("modelVersion") String modelVersion,
            @Param("horizonDays") int horizonDays,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    List<StockExpectedReturnFactorContribution> findLatestExpectedReturnFactorContributionsBySymbol(
            @Param("symbol") String symbol,
            @Param("modelVersion") String modelVersion,
            @Param("horizonDays") int horizonDays);

    List<StockQuantModelHealthMetric> findQuantModelHealth(@Param("indexCode") String indexCode);

    List<StockPitQualityMetric> findPitQuality(@Param("indexCode") String indexCode);

    List<StockOptimizerShadowSnapshot> findOptimizerShadowSnapshots(@Param("indexCode") String indexCode,
            @Param("candidateOptimizer") String candidateOptimizer,
            @Param("limit") int limit);

    List<StockQuantOperationHealthMetric> findQuantOperationHealth();

    String findSectorForSymbol(@Param("symbol") String symbol);

    void upsertResult(StockBacktestResult result);

    void upsertRiskSnapshot(StockRiskSnapshot snapshot);

    void upsertBenchmarkReturn(StockBenchmarkReturn benchmarkReturn);

    void upsertRiskFreeRateSnapshot(StockRiskFreeRateSnapshot snapshot);

    void upsertIndexMembershipSnapshot(StockIndexMembershipSnapshot snapshot);

    void upsertSharesOutstandingSnapshot(StockSharesOutstandingSnapshot snapshot);

    void upsertMacroVintageSnapshot(StockMacroVintageSnapshot snapshot);

    void upsertMacroFeatureSnapshot(StockMacroFeatureSnapshot snapshot);

    void upsertMacroRegimeSnapshot(StockMacroRegimeSnapshot snapshot);

    void upsertCorrelationSnapshot(StockCovarianceSnapshot snapshot);

    void upsertCovarianceSnapshot(StockCovarianceSnapshot snapshot);

    void upsertExpectedReturnSnapshot(StockExpectedReturnSnapshot snapshot);

    void upsertFactorExposureSnapshot(StockFactorExposureSnapshot snapshot);

    void deleteExpectedReturnFactorContributions(@Param("indexCode") String indexCode,
            @Param("signalDate") LocalDate signalDate,
            @Param("modelVersion") String modelVersion);

    void copyExpectedReturnFactorContributions(@Param("indexCode") String indexCode,
            @Param("signalDate") LocalDate signalDate,
            @Param("sourceModelVersion") String sourceModelVersion,
            @Param("targetModelVersion") String targetModelVersion,
            @Param("source") String source);

    void upsertExpectedReturnFactorContribution(StockExpectedReturnFactorContribution contribution);

    void deleteExpectedReturnCalibrations(@Param("indexCode") String indexCode,
            @Param("modelVersion") String modelVersion);

    void upsertExpectedReturnCalibration(StockExpectedReturnCalibration calibration);

    void upsertPortfolioViewSnapshot(StockPortfolioViewSnapshot snapshot);

    void upsertDashboardViewSnapshot(StockDashboardViewSnapshot snapshot);

    void upsertBacktestViewSnapshot(StockBacktestViewSnapshot snapshot);

    void upsertOptimizerShadowSnapshot(StockOptimizerShadowSnapshot snapshot);

    void insertExpectedReturnEvaluationRun(ExpectedReturnEvaluationRun run);

    int completeExpectedReturnEvaluationRun(ExpectedReturnEvaluationRun run);

    void insertExpectedReturnEvaluationWindow(ExpectedReturnEvaluationWindowResult window);

    void insertExpectedReturnEvaluationExclusionCount(ExpectedReturnEvaluationExclusionCount count);

    ExpectedReturnEvaluationRun findLatestExpectedReturnEvaluationRun(@Param("indexCode") String indexCode,
            @Param("contractVersion") String contractVersion);

    List<StockBacktestResult> findResults(@Param("indexCode") String indexCode, @Param("limit") int limit);

    List<StockBacktestResult> findLatestSignalRows(@Param("indexCode") String indexCode);
}
