package com.stablecoin.payments.orchestrator.domain.model;

public enum PaymentTrigger {
    START_COMPLIANCE,
    COMPLIANCE_PASSED,
    LOCK_FX,
    FX_LOCKED,
    START_FIAT_COLLECTION,
    FIAT_COLLECTED,
    SUBMIT_ON_CHAIN,
    CONFIRM_ON_CHAIN,
    INITIATE_OFF_RAMP,
    SETTLE,
    COMPLETE,
    FAIL,
    START_COMPENSATION
}
