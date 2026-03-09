package com.stablecoin.payments.custody.domain.exception;

/**
 * Thrown when the custody engine fails to sign or submit a transaction.
 */
public class CustodySigningException extends RuntimeException {

    public static final String ERROR_CODE = "BC-1004";

    public CustodySigningException(String message) {
        super(message);
    }

    public CustodySigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
