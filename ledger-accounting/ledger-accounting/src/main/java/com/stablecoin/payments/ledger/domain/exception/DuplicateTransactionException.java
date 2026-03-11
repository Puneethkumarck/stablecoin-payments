package com.stablecoin.payments.ledger.domain.exception;

import java.util.UUID;

public class DuplicateTransactionException extends RuntimeException {

    public static final String ERROR_CODE = "LD-1004";

    public DuplicateTransactionException(UUID sourceEventId) {
        super("Duplicate transaction for source event: " + sourceEventId);
    }

    public String errorCode() {
        return ERROR_CODE;
    }
}
