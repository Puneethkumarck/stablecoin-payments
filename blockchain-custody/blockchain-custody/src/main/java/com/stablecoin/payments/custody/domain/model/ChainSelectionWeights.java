package com.stablecoin.payments.custody.domain.model;

import lombok.Builder;

/**
 * Immutable weights for the multi-criteria chain selection scoring model.
 * <p>
 * All weights must be non-negative and sum to approximately 1.0 (±0.001 tolerance).
 */
@Builder(toBuilder = true)
public record ChainSelectionWeights(
        double costWeight,
        double speedWeight,
        double reliabilityWeight
) {

    public static final double DEFAULT_COST_WEIGHT = 0.4;
    public static final double DEFAULT_SPEED_WEIGHT = 0.35;
    public static final double DEFAULT_RELIABILITY_WEIGHT = 0.25;

    private static final double SUM_TOLERANCE = 0.001;

    public ChainSelectionWeights {
        if (costWeight < 0) {
            throw new IllegalArgumentException("costWeight must be non-negative");
        }
        if (speedWeight < 0) {
            throw new IllegalArgumentException("speedWeight must be non-negative");
        }
        if (reliabilityWeight < 0) {
            throw new IllegalArgumentException("reliabilityWeight must be non-negative");
        }
        double sum = costWeight + speedWeight + reliabilityWeight;
        if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "Weights must sum to ~1.0, but got %s".formatted(sum));
        }
    }

    /**
     * Returns the default weights (cost=0.4, speed=0.35, reliability=0.25).
     */
    public static ChainSelectionWeights defaults() {
        return new ChainSelectionWeights(DEFAULT_COST_WEIGHT, DEFAULT_SPEED_WEIGHT, DEFAULT_RELIABILITY_WEIGHT);
    }
}
