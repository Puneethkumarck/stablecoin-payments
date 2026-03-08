package com.stablecoin.payments.fx.domain.exception;

import java.util.UUID;

public class QuoteExpiredException extends RuntimeException {

    private QuoteExpiredException(String message) {
        super(message);
    }

    public static QuoteExpiredException withId(UUID quoteId) {
        return new QuoteExpiredException("Quote has expired: " + quoteId);
    }
}
