package com.stablecoin.payments.gateway.iam.application.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAuthenticationTest {

    @Test
    void shouldCreateWithAllFields() {
        var userId = UUID.randomUUID();
        var merchantId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var permissions = List.of("payments:read", "team:manage");

        var auth = new UserAuthentication(userId, merchantId, roleId,
                "ADMIN", permissions, true);

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
    void shouldMapPermissionsToAuthorities() {
        var auth = new UserAuthentication(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "VIEWER", List.of("payments:read"), false);

        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("PERM_payments:read");
    }

    @Test
    void shouldRejectNullUserId() {
        assertThatThrownBy(() -> new UserAuthentication(null, UUID.randomUUID(),
                UUID.randomUUID(), "ADMIN", List.of(), false))
                .isInstanceOf(NullPointerException.class);
    }
}
