package com.stablecoin.payments.custody.domain.exception;

/**
 * Thrown when a wallet has insufficient available balance for a transfer.
 */
public class InsufficientBalanceException extends RuntimeException {

    public static final String ERROR_CODE = "BC-1001";

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
