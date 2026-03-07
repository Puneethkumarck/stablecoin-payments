package com.stablecoin.payments.compliance.domain.model;

public enum ComplianceCheckStatus {
    PENDING,
    KYC_IN_PROGRESS,
    SANCTIONS_SCREENING,
    AML_SCREENING,
    RISK_SCORING,
    TRAVEL_RULE_PACKAGING,
    PASSED,
    FAILED,
    SANCTIONS_HIT,
    MANUAL_REVIEW
}
