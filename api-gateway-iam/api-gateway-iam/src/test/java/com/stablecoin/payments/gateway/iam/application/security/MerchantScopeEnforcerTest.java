package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantAccessDeniedException;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKey;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantScopeEnforcer")
class MerchantScopeEnforcerTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private MerchantScopeEnforcer enforcer;

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
    @DisplayName("hasAccessToApiKey")
    class HasAccessToApiKey {

        @Test
        @DisplayName("should return true when principal owns the API key")
        void shouldReturnTrueWhenOwner() {
            var merchantId = UUID.randomUUID();
            var keyId = UUID.randomUUID();
            setMerchantAuth(merchantId);
            given(apiKeyRepository.findById(keyId)).willReturn(
                    Optional.of(ApiKey.builder().keyId(keyId).merchantId(merchantId).build()));

            assertThat(enforcer.hasAccessToApiKey(keyId)).isTrue();
        }

        @Test
        @DisplayName("should throw when principal does not own the API key")
        void shouldThrowWhenNotOwner() {
            var keyId = UUID.randomUUID();
            setMerchantAuth(UUID.randomUUID());
            given(apiKeyRepository.findById(keyId)).willReturn(
                    Optional.of(ApiKey.builder().keyId(keyId).merchantId(UUID.randomUUID()).build()));

            assertThatThrownBy(() -> enforcer.hasAccessToApiKey(keyId))
                    .isInstanceOf(MerchantAccessDeniedException.class);
        }

        @Test
        @DisplayName("should throw when API key not found")
        void shouldThrowWhenKeyNotFound() {
            var keyId = UUID.randomUUID();
            setMerchantAuth(UUID.randomUUID());
            given(apiKeyRepository.findById(keyId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enforcer.hasAccessToApiKey(keyId))
                    .isInstanceOf(ApiKeyNotFoundException.class);
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
