package com.stablecoin.payments.gateway.iam.domain.exception;

public class UserJwksUnavailableException extends RuntimeException {

    public UserJwksUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
