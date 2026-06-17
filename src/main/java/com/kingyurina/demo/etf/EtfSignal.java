package com.kingyurina.demo.etf;

import java.util.List;

public record EtfSignal(
        int score,
        String label,
        String tone,
        String confidence,
        String summary,
        List<Card> cards,
        List<String> reasons) {

    public record Card(String title, String label, int score, String tone, String detail) {
    }
}
