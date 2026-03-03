package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(UUID roleId) {
        super("Role not found: " + roleId);
    }

    public RoleNotFoundException(UUID merchantId, String roleName) {
        super("Role not found: merchant=%s name=%s".formatted(merchantId, roleName));
    }
}
