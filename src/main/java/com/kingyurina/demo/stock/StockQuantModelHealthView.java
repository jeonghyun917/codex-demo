package com.kingyurina.demo.stock;

import java.util.List;

public record StockQuantModelHealthView(
        String title,
        String summary,
        List<Card> cards,
        List<Row> rows,
        List<OperationRow> operations,
        List<Alert> alerts) {

    public record Card(String label, String value, String note, String tone) {
    }

    public record Row(String layer, String status, String tone, String rows, String coverage, String latest,
            String note) {
    }

    public record OperationRow(String operation, String status, String tone, String startedAt, String finishedAt,
            String successRate, String failures, String message) {
    }

    public record Alert(String title, String detail, String tone) {
    }
}
