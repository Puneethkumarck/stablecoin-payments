package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class RoleInUseException extends RuntimeException {

    public RoleInUseException(UUID roleId, long activeUserCount) {
        super("Cannot delete role %s: %d active users assigned".formatted(roleId, activeUserCount));
    }
}
