package com.stablecoin.payments.onramp.domain.exception;

import java.util.UUID;

/**
 * Thrown when a collection order cannot be found by the given identifier.
 */
public class CollectionOrderNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "OR-1001";

    public CollectionOrderNotFoundException(String identifier) {
        super("Collection order not found: " + identifier);
    }

    public CollectionOrderNotFoundException(UUID collectionId) {
        super("Collection order not found: " + collectionId);
    }

    public String errorCode() {
        return ERROR_CODE;
    }
}
