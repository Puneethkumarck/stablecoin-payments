package com.stablecoin.payments.orchestrator.application.controller;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorCodes {
    public static final String VALIDATION_ERROR = "PO-0001";
    public static final String IDEMPOTENT_REPLAY = "PO-1001";
    public static final String CORRIDOR_BLOCKED = "PO-1002";
    public static final String AMOUNT_BELOW_MINIMUM = "PO-1003";
    public static final String AMOUNT_ABOVE_MAXIMUM = "PO-1004";
    public static final String SENDER_KYC_INCOMPLETE = "PO-1005";
    public static final String UNSUPPORTED_CURRENCY = "PO-1006";
    public static final String PAYMENT_NOT_FOUND = "PO-2001";
    public static final String PAYMENT_NOT_CANCELLABLE = "PO-2002";
    public static final String INVALID_STATE = "PO-3001";
    public static final String INTERNAL_ERROR = "PO-9999";
}
