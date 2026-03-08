package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import java.util.UUID;

/**
 * Result DTO from the compliance check activity.
 * <p>
 * Contains the compliance check outcome and any screening reference
 * for audit trail purposes.
 */
public record ComplianceResult(
        UUID checkId,
        ComplianceStatus status,
        String failureReason
) {

    public enum ComplianceStatus {
        PASSED,
        FAILED,
        SANCTIONS_HIT
    }

    // Note: convenience methods like isPassed()/passed() are intentionally omitted
    // to avoid Jackson serialization issues with Temporal SDK's internal serializer.
    // Use status() == ComplianceStatus.PASSED directly in workflow code.
}
