package com.stablecoin.payments.onramp.domain.exception;

/**
 * Thrown when a collection order cannot be found by the given identifier.
 */
public class CollectionOrderNotFoundException extends RuntimeException {

    public CollectionOrderNotFoundException(String identifier) {
        super("Collection order not found: " + identifier);
    }
}
