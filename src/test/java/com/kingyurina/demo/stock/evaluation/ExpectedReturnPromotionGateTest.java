package com.kingyurina.demo.stock.evaluation;

import static com.kingyurina.demo.stock.evaluation.ExpectedReturnPromotionResult.Decision.BASELINE_QUALIFIED;
import static com.kingyurina.demo.stock.evaluation.ExpectedReturnPromotionResult.Decision.INSUFFICIENT_DATA;
import static com.kingyurina.demo.stock.evaluation.ExpectedReturnPromotionResult.Decision.PROMOTE;
import static com.kingyurina.demo.stock.evaluation.ExpectedReturnPromotionResult.Decision.REJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExpectedReturnPromotionGateTest {

    private final ExpectedReturnPromotionGate gate =
            new ExpectedReturnPromotionGate(ExpectedReturnPromotionPolicy.sp500Excess20dV1());

    @Test
    void pitViolationAlwaysRejectsCandidate() {
        ExpectedReturnPromotionResult result = gate.evaluateCandidate(
                qualifiedEvidence(0.03d, 3.0d, 0.5d),
                qualifiedEvidence(0.05d, 5.0d, 0.7d).withPitViolations(1));

        assertEquals(REJECT, result.decision());
        assertFalse(result.check("NO_PIT_VIOLATIONS").passed());
    }

    @Test
    void insufficientSamplePrecedesPerformanceJudgment() {
        ExpectedReturnEvaluationEvidence insufficient =
                qualifiedEvidence(0.03d, 3.0d, 0.5d).withCounts(11, 2999);

        ExpectedReturnPromotionResult result = gate.qualifyBaseline(insufficient);

        assertEquals(INSUFFICIENT_DATA, result.decision());
        assertFalse(result.check("MINIMUM_VALID_MONTHS").passed());
        assertFalse(result.check("MINIMUM_ROWS").passed());
    }

    @Test
    void stableV9QualifiesAsBaseline() {
        ExpectedReturnPromotionResult result =
                gate.qualifyBaseline(qualifiedEvidence(0.03d, 3.0d, 0.5d));

        assertEquals(BASELINE_QUALIFIED, result.decision());
        assertTrue(result.checks().stream().allMatch(ExpectedReturnPromotionResult.Check::passed));
    }

    @Test
    void materiallyBetterCandidatePromotes() {
        ExpectedReturnEvaluationEvidence baseline = qualifiedEvidence(0.03d, 3.0d, 0.5d);
        ExpectedReturnEvaluationEvidence candidate = qualifiedEvidence(0.05d, 5.0d, 0.7d)
                .withRelativeQuality(62.0d, 0.19d, 7.0d, -0.01d, 1050.0d);

        ExpectedReturnPromotionResult result = gate.evaluateCandidate(baseline, candidate);

        assertEquals(PROMOTE, result.decision());
        assertTrue(result.checks().stream().allMatch(ExpectedReturnPromotionResult.Check::passed));
    }

    private static ExpectedReturnEvaluationEvidence qualifiedEvidence(
            double rankIc, double annualizedExcess, double sharpe) {
        ExpectedReturnMetrics metrics = new ExpectedReturnMetrics(
                5000, rankIc, 60.0d, 4.0d, 56.0d, 0.20d,
                6.0d, 80.0d, 2.0d, 85.0d);
        ExpectedReturnPortfolioMetrics portfolio = new ExpectedReturnPortfolioMetrics(
                18, 20.0d, 18.0d, 12.0d, annualizedExcess, sharpe,
                0.8d, -10.0d, 60.0d, 1000.0d, 1.0d, -4.0d);
        return new ExpectedReturnEvaluationEvidence(
                metrics, portfolio, 18, 5000, 85.0d, -0.01d, 0);
    }
}

