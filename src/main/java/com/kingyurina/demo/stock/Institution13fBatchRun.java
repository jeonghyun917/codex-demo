package com.kingyurina.demo.stock;

import java.time.LocalDateTime;

public class Institution13fBatchRun {

    private Long id;
    private String jobName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
    private int requestedCount;
    private int successCount;
    private int failCount;
    private int holdingCount;
    private int aggregateRows;
    private String message;

    public Institution13fBatchRun() {
    }

    public Institution13fBatchRun(String jobName, String status) {
        this.jobName = jobName;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRequestedCount() { return requestedCount; }
    public void setRequestedCount(int requestedCount) { this.requestedCount = requestedCount; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
    public int getHoldingCount() { return holdingCount; }
    public void setHoldingCount(int holdingCount) { this.holdingCount = holdingCount; }
    public int getAggregateRows() { return aggregateRows; }
    public void setAggregateRows(int aggregateRows) { this.aggregateRows = aggregateRows; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
