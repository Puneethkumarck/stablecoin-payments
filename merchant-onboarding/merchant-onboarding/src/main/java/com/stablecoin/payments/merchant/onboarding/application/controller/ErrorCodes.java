package com.stablecoin.payments.merchant.onboarding.application.controller;

import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class ErrorCodes {

    // 4xx — client errors
    public static final String BAD_REQUEST_CODE           = moCode(1);
    public static final String MERCHANT_NOT_FOUND_CODE    = moCode(2);
    public static final String MERCHANT_ALREADY_EXISTS_CODE = moCode(3);
    public static final String INVALID_STATE_CODE         = moCode(4);

    // 5xx — server errors
    public static final String INTERNAL_ERROR_CODE        = moCode(50);

    static String moCode(int code) {
        if (code > 9999) {
            throw new IllegalArgumentException("Cannot create error code with more than 4 digits");
        }
        return "MO-%04d".formatted(code);
    }
}
