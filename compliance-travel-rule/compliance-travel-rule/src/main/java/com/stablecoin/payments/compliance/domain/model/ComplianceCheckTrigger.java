package com.stablecoin.payments.compliance.domain.model;

public enum ComplianceCheckTrigger {
    START_KYC,
    KYC_PASSED,
    KYC_FAILED,
    SANCTIONS_CLEAR,
    SANCTIONS_HIT_DETECTED,
    AML_CLEAR,
    AML_FLAGGED,
    RISK_SCORED,
    RISK_CRITICAL,
    TRAVEL_RULE_COMPLETE,
    TRAVEL_RULE_FAILED,
    ESCALATE_MANUAL_REVIEW
}
