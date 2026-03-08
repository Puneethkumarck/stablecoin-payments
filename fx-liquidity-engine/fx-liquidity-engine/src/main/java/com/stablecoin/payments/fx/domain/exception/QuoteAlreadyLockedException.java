package com.stablecoin.payments.fx.domain.exception;

import java.util.UUID;

public class QuoteAlreadyLockedException extends RuntimeException {

    private QuoteAlreadyLockedException(String message) {
        super(message);
    }

    public static QuoteAlreadyLockedException withId(UUID quoteId) {
        return new QuoteAlreadyLockedException("Quote is already locked: " + quoteId);
    }
}
