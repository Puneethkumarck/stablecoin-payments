package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

/**
 * Groups all input data needed for risk scoring.
 * Separates the scoring inputs from the ComplianceCheck aggregate.
 */
@Builder(toBuilder = true)
public record RiskScoringContext(
        ComplianceCheck check,
        CustomerRiskProfile customerProfile,
        int recentTransactionCount
) {
    public RiskScoringContext {
        if (check == null) {
            throw new IllegalArgumentException("ComplianceCheck is required for risk scoring");
        }
        if (recentTransactionCount < 0) {
            throw new IllegalArgumentException("recentTransactionCount must be non-negative");
        }
    }
}
