package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

class OjAlgoPortfolioOptimizerRegressionTest {

    @Test
    void ojAlgoQuadraticSolverProducesFeasibleWeightsNoWorseThanProjectedGradientBaseline() {
        double[] alpha = {0.055, 0.043, 0.035, 0.026};
        double[] cap = {0.55, 0.45, 0.40, 0.35};
        double[][] covariance = {
                {0.040, 0.018, 0.012, 0.006},
                {0.018, 0.035, 0.010, 0.008},
                {0.012, 0.010, 0.028, 0.011},
                {0.006, 0.008, 0.011, 0.022}
        };
        double alphaReward = 0.70;

        double[] projected = projectedGradient(alpha, covariance, cap, alphaReward);
        double[] ojAlgo = solveWithOjAlgo(alpha, covariance, cap, alphaReward);

        assertFeasible(projected, cap);
        assertFeasible(ojAlgo, cap);

        double projectedObjective = objective(projected, alpha, covariance, alphaReward);
        double ojAlgoObjective = objective(ojAlgo, alpha, covariance, alphaReward);
        assertTrue(ojAlgoObjective <= projectedObjective + 0.0001,
                () -> "ojAlgo objective " + ojAlgoObjective
                        + " should not be worse than projected-gradient baseline " + projectedObjective);
    }

    private static double[] solveWithOjAlgo(double[] alpha, double[][] covariance, double[] cap,
            double alphaReward) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        Variable[] variables = new Variable[alpha.length];
        for (int i = 0; i < alpha.length; i++) {
            variables[i] = model.newVariable("asset_" + i)
                    .lower(0.0d)
                    .upper(cap[i])
                    .weight(-alphaReward * alpha[i]);
        }
        Expression budget = model.newExpression("budget").level(1.0d);
        for (Variable variable : variables) {
            budget.set(variable, 1.0d);
        }
        Expression variance = model.newExpression("variance").weight(1.0d);
        for (int i = 0; i < variables.length; i++) {
            for (int j = 0; j < variables.length; j++) {
                variance.set(variables[i], variables[j], covariance[i][j]);
            }
        }

        Optimisation.Result result = model.minimise();
        assertTrue(result.getState().isFeasible(), () -> "ojAlgo state=" + result.getState());
        double[] weights = new double[variables.length];
        for (int i = 0; i < variables.length; i++) {
            weights[i] = result.doubleValue(i);
        }
        return weights;
    }

    private static double[] projectedGradient(double[] alpha, double[][] covariance, double[] cap,
            double alphaReward) {
        double[] weights = projectToBudgetAndCaps(fill(alpha.length, 1.0d / alpha.length), cap);
        double step = 0.45d;
        double best = objective(weights, alpha, covariance, alphaReward);
        for (int iteration = 0; iteration < 600; iteration++) {
            double[] gradient = gradient(weights, alpha, covariance, alphaReward);
            double[] candidate = new double[weights.length];
            for (int i = 0; i < weights.length; i++) {
                candidate[i] = weights[i] - step * gradient[i];
            }
            candidate = projectToBudgetAndCaps(candidate, cap);
            double candidateObjective = objective(candidate, alpha, covariance, alphaReward);
            if (candidateObjective < best) {
                weights = candidate;
                best = candidateObjective;
                step = Math.min(0.60d, step * 1.03d);
            } else {
                step *= 0.60d;
                if (step < 0.000001d) {
                    break;
                }
            }
        }
        return weights;
    }

    private static double[] gradient(double[] weights, double[] alpha, double[][] covariance, double alphaReward) {
        double[] gradient = new double[weights.length];
        for (int i = 0; i < weights.length; i++) {
            double covarianceGradient = 0.0d;
            for (int j = 0; j < weights.length; j++) {
                covarianceGradient += 2.0d * covariance[i][j] * weights[j];
            }
            gradient[i] = covarianceGradient - alphaReward * alpha[i];
        }
        return gradient;
    }

    private static double[] projectToBudgetAndCaps(double[] raw, double[] cap) {
        double[] weights = Arrays.copyOf(raw, raw.length);
        for (int i = 0; i < weights.length; i++) {
            weights[i] = clamp(weights[i], 0.0d, cap[i]);
        }
        for (int iteration = 0; iteration < 80; iteration++) {
            double total = sum(weights);
            double gap = 1.0d - total;
            if (Math.abs(gap) < 0.0000001d) {
                break;
            }
            int adjustable = 0;
            for (int i = 0; i < weights.length; i++) {
                if ((gap > 0 && weights[i] < cap[i] - 0.0000001d)
                        || (gap < 0 && weights[i] > 0.0000001d)) {
                    adjustable++;
                }
            }
            if (adjustable == 0) {
                break;
            }
            double shift = gap / adjustable;
            for (int i = 0; i < weights.length; i++) {
                if ((gap > 0 && weights[i] < cap[i] - 0.0000001d)
                        || (gap < 0 && weights[i] > 0.0000001d)) {
                    weights[i] = clamp(weights[i] + shift, 0.0d, cap[i]);
                }
            }
        }
        return weights;
    }

    private static void assertFeasible(double[] weights, double[] cap) {
        assertTrue(Math.abs(sum(weights) - 1.0d) < 0.0001, () -> "sum=" + sum(weights));
        for (int i = 0; i < weights.length; i++) {
            int index = i;
            assertTrue(weights[i] >= -0.0001, () -> "negative weight at " + index);
            assertTrue(weights[i] <= cap[i] + 0.0001, () -> "cap breach at " + index);
        }
    }

    private static double objective(double[] weights, double[] alpha, double[][] covariance, double alphaReward) {
        double variance = 0.0d;
        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights.length; j++) {
                variance += weights[i] * weights[j] * covariance[i][j];
            }
        }
        double expectedAlpha = 0.0d;
        for (int i = 0; i < weights.length; i++) {
            expectedAlpha += weights[i] * alpha[i];
        }
        return variance - alphaReward * expectedAlpha;
    }

    private static double[] fill(int size, double value) {
        double[] values = new double[size];
        Arrays.fill(values, value);
        return values;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static double sum(double[] values) {
        return Arrays.stream(values).sum();
    }
}
