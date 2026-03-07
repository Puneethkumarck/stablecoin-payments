package com.stablecoin.payments.compliance.application.controller;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorCodes {
    public static final String VALIDATION_ERROR = "CO-0001";
    public static final String CHECK_NOT_FOUND = "CO-1001";
    public static final String DUPLICATE_PAYMENT = "CO-1002";
    public static final String CORRIDOR_NOT_SUPPORTED = "CO-1003";
    public static final String INVALID_STATE = "CO-0004";
    public static final String CUSTOMER_NOT_FOUND = "CO-2001";
    public static final String SANCTIONS_HIT = "CO-3001";
    public static final String KYC_FAILED = "CO-3002";
    public static final String AML_FLAGGED = "CO-3003";
    public static final String TRAVEL_RULE_FAILED = "CO-3004";
    public static final String INTERNAL_ERROR = "CO-9999";
}
