package com.kingyurina.demo.stock.evaluation;

import java.util.List;

public record ExpectedReturnPromotionResult(Decision decision, List<Check> checks) {

    public ExpectedReturnPromotionResult {
        checks = List.copyOf(checks);
    }

    public Check check(String name) {
        return checks.stream()
                .filter(check -> check.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown check: " + name));
    }

    public enum Decision {
        BASELINE_QUALIFIED,
        BASELINE_UNSTABLE,
        PROMOTE,
        HOLD,
        REJECT,
        INSUFFICIENT_DATA
    }

    public record Check(String name, String expected, double actual, boolean passed) {
    }
}

