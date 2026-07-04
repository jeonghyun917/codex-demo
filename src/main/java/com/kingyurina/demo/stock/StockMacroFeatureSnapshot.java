package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockMacroFeatureSnapshot {

    private String indexCode;
    private LocalDate snapshotDate;
    private BigDecimal shortRatePct;
    private BigDecimal longRatePct;
    private BigDecimal yieldSpreadPct;
    private BigDecimal fedFundsPct;
    private BigDecimal cpiYoyPct;
    private BigDecimal cpiMomPct;
    private BigDecimal unemploymentRatePct;
    private BigDecimal unemploymentChange3mPct;
    private BigDecimal vixLevel;
    private BigDecimal vixChange20dPct;
    private BigDecimal dollarIndex;
    private BigDecimal dollarChange20dPct;
    private Integer macroTightnessScore;
    private Integer macroGrowthStressScore;
    private Integer macroRiskScore;
    private Integer macroFeatureScore;
    private String source;
    private LocalDateTime calculatedAt;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public BigDecimal getShortRatePct() {
        return shortRatePct;
    }

    public void setShortRatePct(BigDecimal shortRatePct) {
        this.shortRatePct = shortRatePct;
    }

    public BigDecimal getLongRatePct() {
        return longRatePct;
    }

    public void setLongRatePct(BigDecimal longRatePct) {
        this.longRatePct = longRatePct;
    }

    public BigDecimal getYieldSpreadPct() {
        return yieldSpreadPct;
    }

    public void setYieldSpreadPct(BigDecimal yieldSpreadPct) {
        this.yieldSpreadPct = yieldSpreadPct;
    }

    public BigDecimal getFedFundsPct() {
        return fedFundsPct;
    }

    public void setFedFundsPct(BigDecimal fedFundsPct) {
        this.fedFundsPct = fedFundsPct;
    }

    public BigDecimal getCpiYoyPct() {
        return cpiYoyPct;
    }

    public void setCpiYoyPct(BigDecimal cpiYoyPct) {
        this.cpiYoyPct = cpiYoyPct;
    }

    public BigDecimal getCpiMomPct() {
        return cpiMomPct;
    }

    public void setCpiMomPct(BigDecimal cpiMomPct) {
        this.cpiMomPct = cpiMomPct;
    }

    public BigDecimal getUnemploymentRatePct() {
        return unemploymentRatePct;
    }

    public void setUnemploymentRatePct(BigDecimal unemploymentRatePct) {
        this.unemploymentRatePct = unemploymentRatePct;
    }

    public BigDecimal getUnemploymentChange3mPct() {
        return unemploymentChange3mPct;
    }

    public void setUnemploymentChange3mPct(BigDecimal unemploymentChange3mPct) {
        this.unemploymentChange3mPct = unemploymentChange3mPct;
    }

    public BigDecimal getVixLevel() {
        return vixLevel;
    }

    public void setVixLevel(BigDecimal vixLevel) {
        this.vixLevel = vixLevel;
    }

    public BigDecimal getVixChange20dPct() {
        return vixChange20dPct;
    }

    public void setVixChange20dPct(BigDecimal vixChange20dPct) {
        this.vixChange20dPct = vixChange20dPct;
    }

    public BigDecimal getDollarIndex() {
        return dollarIndex;
    }

    public void setDollarIndex(BigDecimal dollarIndex) {
        this.dollarIndex = dollarIndex;
    }

    public BigDecimal getDollarChange20dPct() {
        return dollarChange20dPct;
    }

    public void setDollarChange20dPct(BigDecimal dollarChange20dPct) {
        this.dollarChange20dPct = dollarChange20dPct;
    }

    public Integer getMacroTightnessScore() {
        return macroTightnessScore;
    }

    public void setMacroTightnessScore(Integer macroTightnessScore) {
        this.macroTightnessScore = macroTightnessScore;
    }

    public Integer getMacroGrowthStressScore() {
        return macroGrowthStressScore;
    }

    public void setMacroGrowthStressScore(Integer macroGrowthStressScore) {
        this.macroGrowthStressScore = macroGrowthStressScore;
    }

    public Integer getMacroRiskScore() {
        return macroRiskScore;
    }

    public void setMacroRiskScore(Integer macroRiskScore) {
        this.macroRiskScore = macroRiskScore;
    }

    public Integer getMacroFeatureScore() {
        return macroFeatureScore;
    }

    public void setMacroFeatureScore(Integer macroFeatureScore) {
        this.macroFeatureScore = macroFeatureScore;
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
