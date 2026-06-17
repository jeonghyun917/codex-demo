package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockInstitutionFlow {

    private String symbol;
    private LocalDate reportQuarter;
    private Integer holderCount;
    private BigDecimal totalValueUsdThousands;
    private BigDecimal totalShares;
    private BigDecimal previousTotalValueUsdThousands;
    private BigDecimal previousTotalShares;
    private BigDecimal valueChangePct;
    private BigDecimal sharesChangePct;
    private BigDecimal netSharesChange;
    private String topManagerName;
    private BigDecimal topManagerValueUsdThousands;
    private Integer sourceFilingCount;
    private Integer mappedHoldingCount;
    private LocalDateTime fetchedAt;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public LocalDate getReportQuarter() { return reportQuarter; }
    public void setReportQuarter(LocalDate reportQuarter) { this.reportQuarter = reportQuarter; }
    public Integer getHolderCount() { return holderCount; }
    public void setHolderCount(Integer holderCount) { this.holderCount = holderCount; }
    public BigDecimal getTotalValueUsdThousands() { return totalValueUsdThousands; }
    public void setTotalValueUsdThousands(BigDecimal totalValueUsdThousands) { this.totalValueUsdThousands = totalValueUsdThousands; }
    public BigDecimal getTotalShares() { return totalShares; }
    public void setTotalShares(BigDecimal totalShares) { this.totalShares = totalShares; }
    public BigDecimal getPreviousTotalValueUsdThousands() { return previousTotalValueUsdThousands; }
    public void setPreviousTotalValueUsdThousands(BigDecimal previousTotalValueUsdThousands) { this.previousTotalValueUsdThousands = previousTotalValueUsdThousands; }
    public BigDecimal getPreviousTotalShares() { return previousTotalShares; }
    public void setPreviousTotalShares(BigDecimal previousTotalShares) { this.previousTotalShares = previousTotalShares; }
    public BigDecimal getValueChangePct() { return valueChangePct; }
    public void setValueChangePct(BigDecimal valueChangePct) { this.valueChangePct = valueChangePct; }
    public BigDecimal getSharesChangePct() { return sharesChangePct; }
    public void setSharesChangePct(BigDecimal sharesChangePct) { this.sharesChangePct = sharesChangePct; }
    public BigDecimal getNetSharesChange() { return netSharesChange; }
    public void setNetSharesChange(BigDecimal netSharesChange) { this.netSharesChange = netSharesChange; }
    public String getTopManagerName() { return topManagerName; }
    public void setTopManagerName(String topManagerName) { this.topManagerName = topManagerName; }
    public BigDecimal getTopManagerValueUsdThousands() { return topManagerValueUsdThousands; }
    public void setTopManagerValueUsdThousands(BigDecimal topManagerValueUsdThousands) { this.topManagerValueUsdThousands = topManagerValueUsdThousands; }
    public Integer getSourceFilingCount() { return sourceFilingCount; }
    public void setSourceFilingCount(Integer sourceFilingCount) { this.sourceFilingCount = sourceFilingCount; }
    public Integer getMappedHoldingCount() { return mappedHoldingCount; }
    public void setMappedHoldingCount(Integer mappedHoldingCount) { this.mappedHoldingCount = mappedHoldingCount; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
