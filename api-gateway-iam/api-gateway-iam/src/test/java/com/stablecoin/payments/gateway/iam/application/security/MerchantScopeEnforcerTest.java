package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.exception.MerchantAccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MerchantScopeEnforcer")
class MerchantScopeEnforcerTest {

    private final MerchantScopeEnforcer enforcer = new MerchantScopeEnforcer();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("with MerchantAuthentication")
    class WithMerchantAuth {

        @Test
        @DisplayName("should return true when merchant IDs match")
        void shouldReturnTrueWhenMatch() {
            var merchantId = UUID.randomUUID();
            setMerchantAuth(merchantId);

            assertThat(enforcer.hasAccess(merchantId)).isTrue();
            assertThat(enforcer.authenticatedMerchantId()).isEqualTo(merchantId);
        }

        @Test
        @DisplayName("should throw when merchant IDs differ")
        void shouldThrowWhenMismatch() {
            var ownMerchantId = UUID.randomUUID();
            var otherMerchantId = UUID.randomUUID();
            setMerchantAuth(ownMerchantId);

            assertThatThrownBy(() -> enforcer.hasAccess(otherMerchantId))
                    .isInstanceOf(MerchantAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("with UserAuthentication")
    class WithUserAuth {

        @Test
        @DisplayName("should return true when merchant IDs match")
        void shouldReturnTrueWhenMatch() {
            var merchantId = UUID.randomUUID();
            setUserAuth(merchantId);

            assertThat(enforcer.hasAccess(merchantId)).isTrue();
            assertThat(enforcer.authenticatedMerchantId()).isEqualTo(merchantId);
        }

        @Test
        @DisplayName("should throw when merchant IDs differ")
        void shouldThrowWhenMismatch() {
            var ownMerchantId = UUID.randomUUID();
            var otherMerchantId = UUID.randomUUID();
            setUserAuth(ownMerchantId);

            assertThatThrownBy(() -> enforcer.hasAccess(otherMerchantId))
                    .isInstanceOf(MerchantAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("with null target merchant ID")
    class WithNullTarget {

        @Test
        @DisplayName("should throw when target merchant ID is null")
        void shouldThrowWhenTargetIsNull() {
            var merchantId = UUID.randomUUID();
            setMerchantAuth(merchantId);

            assertThatThrownBy(() -> enforcer.hasAccess(null))
                    .isInstanceOf(MerchantAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("with no authentication")
    class WithNoAuth {

        @Test
        @DisplayName("should throw when no authentication present")
        void shouldThrowWhenNoAuth() {
            assertThatThrownBy(() -> enforcer.authenticatedMerchantId())
                    .isInstanceOf(MerchantAccessDeniedException.class);
        }
    }

    private void setMerchantAuth(UUID merchantId) {
        var auth = new MerchantAuthentication(
                merchantId, UUID.randomUUID(), List.of("payments:read"),
                MerchantAuthentication.AuthMethod.JWT);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setUserAuth(UUID merchantId) {
        var auth = new UserAuthentication(
                UUID.randomUUID(), merchantId, UUID.randomUUID(),
                "ADMIN", List.of("*:*"), true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
