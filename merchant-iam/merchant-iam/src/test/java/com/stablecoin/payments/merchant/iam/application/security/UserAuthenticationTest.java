package com.stablecoin.payments.merchant.iam.application.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuthenticationTest {

    @Test
    void shouldExposeAllFieldsCorrectly() {
        var userId = UUID.randomUUID();
        var merchantId = UUID.randomUUID();
        var roleId = UUID.randomUUID();

        var auth = new UserAuthentication(userId, merchantId, roleId,
                "ADMIN", List.of("payments:read", "team:manage"), true);

        assertThat(auth.userId()).isEqualTo(userId);
        assertThat(auth.merchantId()).isEqualTo(merchantId);
        assertThat(auth.roleId()).isEqualTo(roleId);
        assertThat(auth.role()).isEqualTo("ADMIN");
        assertThat(auth.permissions()).containsExactly("payments:read", "team:manage");
        assertThat(auth.mfaVerified()).isTrue();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
        assertThat(auth.getCredentials()).isNull();
    }

    @Test
    void shouldGrantPermissionAuthorities() {
        var auth = new UserAuthentication(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "VIEWER", List.of("payments:read"), false);

        assertThat(auth.getAuthorities()).hasSize(1);
        assertThat(auth.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("PERM_payments:read");
    }

    @Test
    void shouldHandleEmptyPermissions() {
        var auth = new UserAuthentication(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "VIEWER", List.of(), false);

        assertThat(auth.permissions()).isEmpty();
        assertThat(auth.getAuthorities()).isEmpty();
    }
}
