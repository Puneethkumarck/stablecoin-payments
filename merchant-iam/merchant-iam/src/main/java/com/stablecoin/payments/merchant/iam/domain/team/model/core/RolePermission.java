package com.stablecoin.payments.merchant.iam.domain.team.model.core;

import java.time.Instant;

public record RolePermission(Permission permission, Instant grantedAt) {

    public static RolePermission of(Permission permission) {
        return new RolePermission(permission, Instant.now());
    }

    public static RolePermission of(Permission permission, Instant grantedAt) {
        return new RolePermission(permission, grantedAt);
    }
}
