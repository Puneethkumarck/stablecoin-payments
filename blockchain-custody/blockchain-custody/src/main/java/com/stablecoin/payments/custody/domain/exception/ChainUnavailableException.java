package com.stablecoin.payments.custody.domain.exception;

/**
 * Thrown when no healthy blockchain chain is available for a transfer.
 */
public class ChainUnavailableException extends RuntimeException {

    public static final String ERROR_CODE = "BC-1002";

    public ChainUnavailableException(String message) {
        super(message);
    }
}
