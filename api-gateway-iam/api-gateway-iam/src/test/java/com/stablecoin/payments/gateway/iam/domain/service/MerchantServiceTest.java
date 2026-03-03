package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.model.Corridor;
import com.stablecoin.payments.gateway.iam.domain.model.KybStatus;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.port.AccessTokenRepository;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.OAuthClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private OAuthClientRepository oauthClientRepository;
    @Mock private AccessTokenRepository accessTokenRepository;

    private MerchantService merchantService;

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(merchantRepository, apiKeyRepository,
                oauthClientRepository, accessTokenRepository);
    }

    private static Merchant pendingMerchant(UUID externalId) {
        return Merchant.builder()
                .merchantId(UUID.randomUUID())
                .externalId(externalId)
                .name("Test Corp")
                .country("US")
                .scopes(List.of("payments:read"))
                .corridors(List.of(new Corridor("US", "DE")))
                .status(MerchantStatus.PENDING)
                .kybStatus(KybStatus.PENDING)
                .rateLimitTier(RateLimitTier.STARTER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    private static Merchant activeMerchant(UUID externalId) {
        return Merchant.builder()
                .merchantId(UUID.randomUUID())
                .externalId(externalId)
                .name("Test Corp")
                .country("US")
                .scopes(List.of("payments:read"))
                .corridors(List.of())
                .status(MerchantStatus.ACTIVE)
                .kybStatus(KybStatus.VERIFIED)
                .rateLimitTier(RateLimitTier.STARTER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        void shouldRegisterNewMerchant() {
            var externalId = UUID.randomUUID();
            given(merchantRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var result = merchantService.register(externalId, "Test Corp", "US",
                    List.of("payments:read"), List.of(new Corridor("US", "DE")));

            assertThat(result.getStatus()).isEqualTo(MerchantStatus.PENDING);
            assertThat(result.getKybStatus()).isEqualTo(KybStatus.PENDING);
            assertThat(result.getExternalId()).isEqualTo(externalId);
            then(merchantRepository).should().save(any());
        }

        @Test
        void shouldHandleNullScopesAndCorridors() {
            given(merchantRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var result = merchantService.register(UUID.randomUUID(), "Test Corp", "US", null, null);

            assertThat(result.getScopes()).isEmpty();
            assertThat(result.getCorridors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("activate()")
    class Activate {

        @Test
        void shouldActivatePendingMerchant() {
            var externalId = UUID.randomUUID();
            var merchant = pendingMerchant(externalId);
            given(merchantRepository.findByExternalId(externalId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var result = merchantService.activate(externalId);

            assertThat(result.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
            assertThat(result.getKybStatus()).isEqualTo(KybStatus.VERIFIED);
        }

        @Test
        void shouldThrowWhenMerchantNotFound() {
            var externalId = UUID.randomUUID();
            given(merchantRepository.findByExternalId(externalId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.activate(externalId))
                    .isInstanceOf(MerchantNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("suspend()")
    class Suspend {

        @Test
        void shouldSuspendAndDeactivateAll() {
            var externalId = UUID.randomUUID();
            var merchant = activeMerchant(externalId);
            given(merchantRepository.findByExternalId(externalId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var result = merchantService.suspend(externalId);

            assertThat(result.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
            then(apiKeyRepository).should().deactivateAllByMerchantId(merchant.getMerchantId());
            then(oauthClientRepository).should().deactivateAllByMerchantId(merchant.getMerchantId());
            then(accessTokenRepository).should().revokeAllByMerchantId(merchant.getMerchantId());
        }

        @Test
        void shouldThrowWhenMerchantNotFound() {
            var externalId = UUID.randomUUID();
            given(merchantRepository.findByExternalId(externalId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.suspend(externalId))
                    .isInstanceOf(MerchantNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("close()")
    class Close {

        @Test
        void shouldCloseAndDeactivateAll() {
            var externalId = UUID.randomUUID();
            var merchant = activeMerchant(externalId);
            given(merchantRepository.findByExternalId(externalId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var result = merchantService.close(externalId);

            assertThat(result.getStatus()).isEqualTo(MerchantStatus.CLOSED);
            then(apiKeyRepository).should().deactivateAllByMerchantId(merchant.getMerchantId());
            then(oauthClientRepository).should().deactivateAllByMerchantId(merchant.getMerchantId());
            then(accessTokenRepository).should().revokeAllByMerchantId(merchant.getMerchantId());
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        void shouldReturnMerchantWhenFound() {
            var merchantId = UUID.randomUUID();
            var merchant = pendingMerchant(UUID.randomUUID());
            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

            var result = merchantService.findById(merchantId);

            assertThat(result).isEqualTo(merchant);
        }

        @Test
        void shouldThrowWhenNotFound() {
            var merchantId = UUID.randomUUID();
            given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.findById(merchantId))
                    .isInstanceOf(MerchantNotFoundException.class);
        }
    }
}
