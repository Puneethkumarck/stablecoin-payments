package com.stablecoin.payments.merchant.iam.domain.team.model;

import com.stablecoin.payments.merchant.iam.domain.team.model.core.Permission;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record Role(
        UUID roleId,
        UUID merchantId,
        String roleName,
        String description,
        boolean builtin,
        boolean active,
        List<Permission> permissions,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public Role deactivate() {
        return toBuilder()
                .active(false)
                .updatedAt(Instant.now())
                .build();
    }

    public Role updatePermissions(List<Permission> newPermissions) {
        return toBuilder()
                .permissions(List.copyOf(newPermissions))
                .updatedAt(Instant.now())
                .build();
    }
}
