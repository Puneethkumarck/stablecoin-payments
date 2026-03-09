package com.stablecoin.payments.custody.domain.exception;

/**
 * Thrown when a wallet is not found.
 */
public class WalletNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "BC-1005";

    public WalletNotFoundException(String message) {
        super(message);
    }
}
