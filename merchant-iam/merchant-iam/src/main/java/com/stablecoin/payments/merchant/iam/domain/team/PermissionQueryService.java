package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.PermissionCacheProvider;
import com.stablecoin.payments.merchant.iam.domain.exceptions.UserNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.team.model.Role;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.Permission;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.PermissionSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Domain service for permission resolution.
 * Checks Redis cache first; falls back to DB on cache miss.
 * Called by S10 API Gateway via {@code GET /v1/auth/permissions/check}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionQueryService {

    private final MerchantUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionCacheProvider permissionCacheProvider;

    public record PermissionCheckResult(boolean allowed, String roleName, String via) {}

    @Transactional(readOnly = true)
    public PermissionCheckResult check(UUID userId, UUID merchantId, String permissionString) {
        var required = Permission.parse(permissionString);
        var permissions = resolvePermissions(merchantId, userId);

        var allowed = permissions.has(required);

        var roleName = userRepository.findById(userId)
                .flatMap(u -> roleRepository.findById(u.roleId()))
                .map(Role::roleName)
                .orElse("UNKNOWN");

        var via = permissions.permissions().stream()
                .filter(p -> p.implies(required))
                .map(Permission::toString)
                .findFirst()
                .orElse(null);

        log.debug("Permission check userId={} permission={} allowed={}", userId, permissionString, allowed);
        return new PermissionCheckResult(allowed, roleName, via);
    }

    private PermissionSet resolvePermissions(UUID merchantId, UUID userId) {
        return permissionCacheProvider.getPermissions(merchantId, userId)
                .orElseGet(() -> loadAndCache(merchantId, userId));
    }

    private PermissionSet loadAndCache(UUID merchantId, UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));
        var role = roleRepository.findById(user.roleId()).orElse(null);
        var permissions = role == null
                ? PermissionSet.empty()
                : PermissionSet.of(role.permissions());
        permissionCacheProvider.putPermissions(merchantId, userId, permissions);
        return permissions;
    }
}
