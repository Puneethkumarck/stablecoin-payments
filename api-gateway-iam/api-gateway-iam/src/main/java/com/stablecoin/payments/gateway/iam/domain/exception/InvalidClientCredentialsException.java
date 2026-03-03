package com.stablecoin.payments.gateway.iam.domain.exception;

public class InvalidClientCredentialsException extends RuntimeException {

    private InvalidClientCredentialsException(String message) {
        super(message);
    }

    public static InvalidClientCredentialsException invalidSecret() {
        return new InvalidClientCredentialsException("Invalid client credentials");
    }

    public static InvalidClientCredentialsException clientNotFound() {
        return new InvalidClientCredentialsException("Client not found or inactive");
    }
}
