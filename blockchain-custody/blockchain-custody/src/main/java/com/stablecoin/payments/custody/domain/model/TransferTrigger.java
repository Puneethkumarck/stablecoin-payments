package com.stablecoin.payments.custody.domain.model;

public enum TransferTrigger {
    SELECT_CHAIN,
    START_SIGNING,
    SUBMIT,
    CONFIRM_SUBMISSION,
    START_CONFIRMING,
    CONFIRM,
    RESUBMIT,
    FAIL
}
