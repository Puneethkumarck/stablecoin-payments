package com.stablecoin.payments.orchestrator.domain.model;

public enum SagaStepName {
    COMPLIANCE_CHECK,
    FX_LOCK,
    FIAT_COLLECTION,
    ON_CHAIN_TRANSFER,
    OFF_RAMP,
    SETTLEMENT
}
