package com.stablecoin.payments.fx.domain.exception;

import java.util.UUID;

public class QuoteNotFoundException extends RuntimeException {

    private QuoteNotFoundException(String message) {
        super(message);
    }

    public static QuoteNotFoundException withId(UUID quoteId) {
        return new QuoteNotFoundException("Quote not found: " + quoteId);
    }
}
