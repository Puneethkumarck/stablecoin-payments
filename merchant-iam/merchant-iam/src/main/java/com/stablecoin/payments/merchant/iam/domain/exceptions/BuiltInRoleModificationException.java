package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class BuiltInRoleModificationException extends RuntimeException {

    public BuiltInRoleModificationException(UUID roleId, String roleName) {
        super("Cannot modify built-in role: %s (%s)".formatted(roleName, roleId));
    }
}
