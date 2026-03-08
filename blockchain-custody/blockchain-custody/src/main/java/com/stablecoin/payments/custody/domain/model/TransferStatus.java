package com.stablecoin.payments.custody.domain.model;

public enum TransferStatus {
    PENDING,
    CHAIN_SELECTED,
    SIGNING,
    SUBMITTED,
    RESUBMITTING,
    CONFIRMING,
    CONFIRMED,
    FAILED
}
