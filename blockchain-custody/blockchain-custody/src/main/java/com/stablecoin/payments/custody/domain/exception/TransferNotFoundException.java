package com.stablecoin.payments.custody.domain.exception;

/**
 * Thrown when a chain transfer is not found.
 */
public class TransferNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "BC-1003";

    public TransferNotFoundException(String message) {
        super(message);
    }
}
