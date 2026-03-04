package com.stablecoin.payments.merchant.iam.domain;

import com.stablecoin.payments.merchant.iam.domain.team.model.core.PermissionSet;

import java.util.Optional;
import java.util.UUID;

public interface PermissionCacheProvider {

    Optional<PermissionSet> getPermissions(UUID merchantId, UUID userId);

    void putPermissions(UUID merchantId, UUID userId, PermissionSet permissions);

    void evict(UUID merchantId, UUID userId);

    void evictAll(UUID merchantId);
}
