package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ExpectedReturnEvaluationRun {
    private Long id;
    private String contractVersion;
    private String indexCode;
    private String baselineModelVersion;
    private String candidateModelVersion;
    private LocalDate asOfDate;
    private String status;
    private String decision;
    private Integer validMonths;
    private Integer targetRowCount;
    private Integer validRowCount;
    private Double coveragePct;
    private Double rankIc;
    private Double brierScore;
    private Double annualizedExcessReturnPct;
    private String checksJson;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContractVersion() { return contractVersion; }
    public void setContractVersion(String value) { contractVersion = value; }
    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String value) { indexCode = value; }
    public String getBaselineModelVersion() { return baselineModelVersion; }
    public void setBaselineModelVersion(String value) { baselineModelVersion = value; }
    public String getCandidateModelVersion() { return candidateModelVersion; }
    public void setCandidateModelVersion(String value) { candidateModelVersion = value; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate value) { asOfDate = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getDecision() { return decision; }
    public void setDecision(String value) { decision = value; }
    public Integer getValidMonths() { return validMonths; }
    public void setValidMonths(Integer value) { validMonths = value; }
    public Integer getTargetRowCount() { return targetRowCount; }
    public void setTargetRowCount(Integer value) { targetRowCount = value; }
    public Integer getValidRowCount() { return validRowCount; }
    public void setValidRowCount(Integer value) { validRowCount = value; }
    public Double getCoveragePct() { return coveragePct; }
    public void setCoveragePct(Double value) { coveragePct = value; }
    public Double getRankIc() { return rankIc; }
    public void setRankIc(Double value) { rankIc = value; }
    public Double getBrierScore() { return brierScore; }
    public void setBrierScore(Double value) { brierScore = value; }
    public Double getAnnualizedExcessReturnPct() { return annualizedExcessReturnPct; }
    public void setAnnualizedExcessReturnPct(Double value) { annualizedExcessReturnPct = value; }
    public String getChecksJson() { return checksJson; }
    public void setChecksJson(String value) { checksJson = value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { errorCode = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime value) { finishedAt = value; }
}

