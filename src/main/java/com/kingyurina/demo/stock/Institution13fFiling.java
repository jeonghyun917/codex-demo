package com.kingyurina.demo.stock;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Institution13fFiling {

    private String accessionNumber;
    private String managerCik;
    private String managerName;
    private String formType;
    private LocalDate reportQuarter;
    private LocalDate reportDate;
    private LocalDate filingDate;
    private String informationTableUrl;
    private String status;
    private String rawJson;
    private LocalDateTime fetchedAt;

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getManagerCik() {
        return managerCik;
    }

    public void setManagerCik(String managerCik) {
        this.managerCik = managerCik;
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public LocalDate getReportQuarter() {
        return reportQuarter;
    }

    public void setReportQuarter(LocalDate reportQuarter) {
        this.reportQuarter = reportQuarter;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public void setReportDate(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public LocalDate getFilingDate() {
        return filingDate;
    }

    public void setFilingDate(LocalDate filingDate) {
        this.filingDate = filingDate;
    }

    public String getInformationTableUrl() {
        return informationTableUrl;
    }

    public void setInformationTableUrl(String informationTableUrl) {
        this.informationTableUrl = informationTableUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
