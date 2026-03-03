package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class InvalidUserStateException extends RuntimeException {

    public InvalidUserStateException(UUID userId, String currentState, String attemptedAction) {
        super("Invalid state for user=%s: cannot %s in state %s".formatted(userId, attemptedAction, currentState));
    }
}
