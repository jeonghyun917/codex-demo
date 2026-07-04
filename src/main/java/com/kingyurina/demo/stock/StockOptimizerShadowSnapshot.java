package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockOptimizerShadowSnapshot {

    private String indexCode;
    private LocalDate signalDate;
    private Integer horizonDays;
    private Integer topCount;
    private String weighting;
    private String baselineOptimizer;
    private String candidateOptimizer;
    private String solverStatus;
    private Boolean usable;
    private String message;
    private BigDecimal baselineObjective;
    private BigDecimal candidateObjective;
    private BigDecimal objectiveGap;
    private BigDecimal weightDistancePct;
    private BigDecimal baselineNetReturnPct;
    private BigDecimal candidateNetReturnPct;
    private BigDecimal benchmarkReturnPct;
    private BigDecimal candidateTurnoverPct;
    private BigDecimal candidateTransactionCostPct;
    private BigDecimal candidateBeta;
    private BigDecimal candidateVolatilityPct;
    private BigDecimal candidateLiquidity;
    private BigDecimal candidateMaxSectorWeightPct;
    private BigDecimal candidateMaxPositionWeightPct;
    private BigDecimal candidateActiveSectorDeviationPct;
    private BigDecimal candidateInvestedWeightPct;
    private Boolean betaBreach;
    private Boolean volatilityBreach;
    private Boolean sectorBreach;
    private Boolean positionBreach;
    private Boolean turnoverBreach;
    private Boolean objectiveBreach;
    private Boolean weightDistanceBreach;
    private Integer constraintBreachCount;
    private String source;
    private LocalDateTime calculatedAt;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public LocalDate getSignalDate() {
        return signalDate;
    }

    public void setSignalDate(LocalDate signalDate) {
        this.signalDate = signalDate;
    }

    public Integer getHorizonDays() {
        return horizonDays;
    }

    public void setHorizonDays(Integer horizonDays) {
        this.horizonDays = horizonDays;
    }

    public Integer getTopCount() {
        return topCount;
    }

    public void setTopCount(Integer topCount) {
        this.topCount = topCount;
    }

    public String getWeighting() {
        return weighting;
    }

    public void setWeighting(String weighting) {
        this.weighting = weighting;
    }

    public String getBaselineOptimizer() {
        return baselineOptimizer;
    }

    public void setBaselineOptimizer(String baselineOptimizer) {
        this.baselineOptimizer = baselineOptimizer;
    }

    public String getCandidateOptimizer() {
        return candidateOptimizer;
    }

    public void setCandidateOptimizer(String candidateOptimizer) {
        this.candidateOptimizer = candidateOptimizer;
    }

    public String getSolverStatus() {
        return solverStatus;
    }

    public void setSolverStatus(String solverStatus) {
        this.solverStatus = solverStatus;
    }

    public Boolean getUsable() {
        return usable;
    }

    public void setUsable(Boolean usable) {
        this.usable = usable;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public BigDecimal getBaselineObjective() {
        return baselineObjective;
    }

    public void setBaselineObjective(BigDecimal baselineObjective) {
        this.baselineObjective = baselineObjective;
    }

    public BigDecimal getCandidateObjective() {
        return candidateObjective;
    }

    public void setCandidateObjective(BigDecimal candidateObjective) {
        this.candidateObjective = candidateObjective;
    }

    public BigDecimal getObjectiveGap() {
        return objectiveGap;
    }

    public void setObjectiveGap(BigDecimal objectiveGap) {
        this.objectiveGap = objectiveGap;
    }

    public BigDecimal getWeightDistancePct() {
        return weightDistancePct;
    }

    public void setWeightDistancePct(BigDecimal weightDistancePct) {
        this.weightDistancePct = weightDistancePct;
    }

    public BigDecimal getBaselineNetReturnPct() {
        return baselineNetReturnPct;
    }

    public void setBaselineNetReturnPct(BigDecimal baselineNetReturnPct) {
        this.baselineNetReturnPct = baselineNetReturnPct;
    }

    public BigDecimal getCandidateNetReturnPct() {
        return candidateNetReturnPct;
    }

    public void setCandidateNetReturnPct(BigDecimal candidateNetReturnPct) {
        this.candidateNetReturnPct = candidateNetReturnPct;
    }

    public BigDecimal getBenchmarkReturnPct() {
        return benchmarkReturnPct;
    }

    public void setBenchmarkReturnPct(BigDecimal benchmarkReturnPct) {
        this.benchmarkReturnPct = benchmarkReturnPct;
    }

    public BigDecimal getCandidateTurnoverPct() {
        return candidateTurnoverPct;
    }

    public void setCandidateTurnoverPct(BigDecimal candidateTurnoverPct) {
        this.candidateTurnoverPct = candidateTurnoverPct;
    }

    public BigDecimal getCandidateTransactionCostPct() {
        return candidateTransactionCostPct;
    }

    public void setCandidateTransactionCostPct(BigDecimal candidateTransactionCostPct) {
        this.candidateTransactionCostPct = candidateTransactionCostPct;
    }

    public BigDecimal getCandidateBeta() {
        return candidateBeta;
    }

    public void setCandidateBeta(BigDecimal candidateBeta) {
        this.candidateBeta = candidateBeta;
    }

    public BigDecimal getCandidateVolatilityPct() {
        return candidateVolatilityPct;
    }

    public void setCandidateVolatilityPct(BigDecimal candidateVolatilityPct) {
        this.candidateVolatilityPct = candidateVolatilityPct;
    }

    public BigDecimal getCandidateLiquidity() {
        return candidateLiquidity;
    }

    public void setCandidateLiquidity(BigDecimal candidateLiquidity) {
        this.candidateLiquidity = candidateLiquidity;
    }

    public BigDecimal getCandidateMaxSectorWeightPct() {
        return candidateMaxSectorWeightPct;
    }

    public void setCandidateMaxSectorWeightPct(BigDecimal candidateMaxSectorWeightPct) {
        this.candidateMaxSectorWeightPct = candidateMaxSectorWeightPct;
    }

    public BigDecimal getCandidateMaxPositionWeightPct() {
        return candidateMaxPositionWeightPct;
    }

    public void setCandidateMaxPositionWeightPct(BigDecimal candidateMaxPositionWeightPct) {
        this.candidateMaxPositionWeightPct = candidateMaxPositionWeightPct;
    }

    public BigDecimal getCandidateActiveSectorDeviationPct() {
        return candidateActiveSectorDeviationPct;
    }

    public void setCandidateActiveSectorDeviationPct(BigDecimal candidateActiveSectorDeviationPct) {
        this.candidateActiveSectorDeviationPct = candidateActiveSectorDeviationPct;
    }

    public BigDecimal getCandidateInvestedWeightPct() {
        return candidateInvestedWeightPct;
    }

    public void setCandidateInvestedWeightPct(BigDecimal candidateInvestedWeightPct) {
        this.candidateInvestedWeightPct = candidateInvestedWeightPct;
    }

    public Boolean getBetaBreach() {
        return betaBreach;
    }

    public void setBetaBreach(Boolean betaBreach) {
        this.betaBreach = betaBreach;
    }

    public Boolean getVolatilityBreach() {
        return volatilityBreach;
    }

    public void setVolatilityBreach(Boolean volatilityBreach) {
        this.volatilityBreach = volatilityBreach;
    }

    public Boolean getSectorBreach() {
        return sectorBreach;
    }

    public void setSectorBreach(Boolean sectorBreach) {
        this.sectorBreach = sectorBreach;
    }

    public Boolean getPositionBreach() {
        return positionBreach;
    }

    public void setPositionBreach(Boolean positionBreach) {
        this.positionBreach = positionBreach;
    }

    public Boolean getTurnoverBreach() {
        return turnoverBreach;
    }

    public void setTurnoverBreach(Boolean turnoverBreach) {
        this.turnoverBreach = turnoverBreach;
    }

    public Boolean getObjectiveBreach() {
        return objectiveBreach;
    }

    public void setObjectiveBreach(Boolean objectiveBreach) {
        this.objectiveBreach = objectiveBreach;
    }

    public Boolean getWeightDistanceBreach() {
        return weightDistanceBreach;
    }

    public void setWeightDistanceBreach(Boolean weightDistanceBreach) {
        this.weightDistanceBreach = weightDistanceBreach;
    }

    public Integer getConstraintBreachCount() {
        return constraintBreachCount;
    }

    public void setConstraintBreachCount(Integer constraintBreachCount) {
        this.constraintBreachCount = constraintBreachCount;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}
