package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Institution13fHolding {

    private String accessionNumber;
    private String managerCik;
    private String managerName;
    private LocalDate reportQuarter;
    private String issuerName;
    private String titleOfClass;
    private String cusip;
    private BigDecimal valueUsdThousands;
    private BigDecimal shares;
    private String shareType;
    private String putCall;
    private String investmentDiscretion;
    private String otherManager;
    private BigDecimal votingSole;
    private BigDecimal votingShared;
    private BigDecimal votingNone;
    private String symbol;
    private String mappedBy;
    private String rawXml;
    private LocalDateTime fetchedAt;

    public String getAccessionNumber() { return accessionNumber; }
    public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }
    public String getManagerCik() { return managerCik; }
    public void setManagerCik(String managerCik) { this.managerCik = managerCik; }
    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }
    public LocalDate getReportQuarter() { return reportQuarter; }
    public void setReportQuarter(LocalDate reportQuarter) { this.reportQuarter = reportQuarter; }
    public String getIssuerName() { return issuerName; }
    public void setIssuerName(String issuerName) { this.issuerName = issuerName; }
    public String getTitleOfClass() { return titleOfClass; }
    public void setTitleOfClass(String titleOfClass) { this.titleOfClass = titleOfClass; }
    public String getCusip() { return cusip; }
    public void setCusip(String cusip) { this.cusip = cusip; }
    public BigDecimal getValueUsdThousands() { return valueUsdThousands; }
    public void setValueUsdThousands(BigDecimal valueUsdThousands) { this.valueUsdThousands = valueUsdThousands; }
    public BigDecimal getShares() { return shares; }
    public void setShares(BigDecimal shares) { this.shares = shares; }
    public String getShareType() { return shareType; }
    public void setShareType(String shareType) { this.shareType = shareType; }
    public String getPutCall() { return putCall; }
    public void setPutCall(String putCall) { this.putCall = putCall; }
    public String getInvestmentDiscretion() { return investmentDiscretion; }
    public void setInvestmentDiscretion(String investmentDiscretion) { this.investmentDiscretion = investmentDiscretion; }
    public String getOtherManager() { return otherManager; }
    public void setOtherManager(String otherManager) { this.otherManager = otherManager; }
    public BigDecimal getVotingSole() { return votingSole; }
    public void setVotingSole(BigDecimal votingSole) { this.votingSole = votingSole; }
    public BigDecimal getVotingShared() { return votingShared; }
    public void setVotingShared(BigDecimal votingShared) { this.votingShared = votingShared; }
    public BigDecimal getVotingNone() { return votingNone; }
    public void setVotingNone(BigDecimal votingNone) { this.votingNone = votingNone; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getMappedBy() { return mappedBy; }
    public void setMappedBy(String mappedBy) { this.mappedBy = mappedBy; }
    public String getRawXml() { return rawXml; }
    public void setRawXml(String rawXml) { this.rawXml = rawXml; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
