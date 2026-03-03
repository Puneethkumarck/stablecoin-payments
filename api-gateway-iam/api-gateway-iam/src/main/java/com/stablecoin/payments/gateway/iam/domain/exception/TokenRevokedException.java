package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.UUID;

public class TokenRevokedException extends RuntimeException {

    private TokenRevokedException(String message) {
        super(message);
    }

    public static TokenRevokedException of(UUID jti) {
        return new TokenRevokedException("Token has been revoked: " + jti);
    }
}
