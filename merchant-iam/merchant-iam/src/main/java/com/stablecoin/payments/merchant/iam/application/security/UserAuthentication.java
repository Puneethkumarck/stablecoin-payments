package com.stablecoin.payments.merchant.iam.application.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/**
 * Spring Security principal for S13 authenticated users.
 * Extracted from JWT claims after signature verification.
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
        super(permissions.stream().map(p -> new SimpleGrantedAuthority("PERM_" + p)).toList());
        this.userId = userId;
        this.merchantId = merchantId;
        this.roleId = roleId;
        this.role = role;
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
