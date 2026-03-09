package com.stablecoin.payments.custody.infrastructure.provider.dev;

public class DevCustodyException extends RuntimeException {

    public DevCustodyException(String message) {
        super(message);
    }

    public DevCustodyException(String message, Throwable cause) {
        super(message, cause);
    }
}
