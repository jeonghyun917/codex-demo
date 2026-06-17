package com.kingyurina.demo.stock;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface Institution13fMapper {

    List<Institution13fManager> findActiveManagers(@Param("limit") int limit);

    void upsertFiling(Institution13fFiling filing);

    void updateFilingStatus(@Param("accessionNumber") String accessionNumber,
            @Param("status") String status,
            @Param("informationTableUrl") String informationTableUrl);

    void deleteHoldings(@Param("accessionNumber") String accessionNumber);

    void upsertHoldings(@Param("holdings") List<Institution13fHolding> holdings);

    List<InstitutionNameMapping> findNameMappings();

    void deleteQuarterFlows(@Param("reportQuarter") LocalDate reportQuarter);

    int rebuildQuarter(@Param("reportQuarter") LocalDate reportQuarter);

    StockInstitutionFlow findLatestFlow(@Param("symbol") String symbol);

    List<StockInstitutionFlow> findRecentFlows(@Param("symbol") String symbol, @Param("limit") int limit);

    class InstitutionNameMapping {
        private String symbol;
        private String companyName;
        private String normalizedName;

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getNormalizedName() {
            return normalizedName;
        }

        public void setNormalizedName(String normalizedName) {
            this.normalizedName = normalizedName;
        }
    }
}
