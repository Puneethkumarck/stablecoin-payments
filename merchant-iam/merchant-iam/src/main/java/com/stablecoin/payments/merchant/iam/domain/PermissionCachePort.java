package com.stablecoin.payments.merchant.iam.domain;

import com.stablecoin.payments.merchant.iam.domain.team.model.core.PermissionSet;

import java.util.Optional;
import java.util.UUID;

public interface PermissionCachePort {

    Optional<PermissionSet> getPermissions(UUID userId);

    void putPermissions(UUID userId, PermissionSet permissions);

    void evict(UUID userId);

    void evictAll(UUID merchantId);
}
