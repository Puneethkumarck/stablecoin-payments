package com.stablecoin.payments.gateway.iam.application.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring Security principal for S13-authenticated users accessing S10 gateway.
 * Distinct from {@link MerchantAuthentication} which represents merchant/client-level auth.
 */
public class UserAuthentication extends AbstractAuthenticationToken {

    private final UUID userId;
    private final UUID merchantId;
    private final UUID roleId;
    private final String role;
    private final List<String> permissions;
    private final boolean mfaVerified;

    public UserAuthentication(UUID userId, UUID merchantId, UUID roleId,
                              String role, List<String> permissions, boolean mfaVerified) {
        super(Objects.requireNonNull(permissions, "permissions must not be null").stream()
                .map(p -> new SimpleGrantedAuthority("PERM_" + p))
                .toList());
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId must not be null");
        this.roleId = Objects.requireNonNull(roleId, "roleId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.permissions = List.copyOf(permissions);
        this.mfaVerified = mfaVerified;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID merchantId() {
        return merchantId;
    }

    public UUID roleId() {
        return roleId;
    }

    public String role() {
        return role;
    }

    public List<String> permissions() {
        return permissions;
    }

    public boolean mfaVerified() {
        return mfaVerified;
    }
}
