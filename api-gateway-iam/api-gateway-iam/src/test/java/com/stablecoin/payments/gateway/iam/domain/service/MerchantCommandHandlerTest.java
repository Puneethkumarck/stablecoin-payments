package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.event.OAuthClientProvisionedEvent;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.model.Corridor;
import com.stablecoin.payments.gateway.iam.domain.model.KybStatus;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus;
import com.stablecoin.payments.gateway.iam.domain.model.OAuthClient;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.port.AccessTokenRepository;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import com.stablecoin.payments.gateway.iam.domain.port.EventPublisher;
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

import static com.stablecoin.payments.gateway.iam.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.gateway.iam.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MerchantCommandHandlerTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private OAuthClientRepository oauthClientRepository;
    @Mock private AccessTokenRepository accessTokenRepository;
    @Mock private OAuthClientCommandHandler oauthClientCommandHandler;
    @Mock private EventPublisher<Object> eventPublisher;

    private MerchantCommandHandler merchantCommandHandler;

    @BeforeEach
    void setUp() {
        merchantCommandHandler = new MerchantCommandHandler(merchantRepository, apiKeyRepository,
                oauthClientRepository, accessTokenRepository, oauthClientCommandHandler, eventPublisher);
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

            var expected = Merchant.builder()
                    .externalId(externalId)
                    .name("Test Corp")
                    .country("US")
                    .scopes(List.of("payments:read"))
                    .corridors(List.of(new Corridor("US", "DE")))
                    .status(MerchantStatus.PENDING)
                    .kybStatus(KybStatus.PENDING)
                    .rateLimitTier(RateLimitTier.STARTER)
                    .version(0L)
                    .build();

            merchantCommandHandler.register(externalId, "Test Corp", "US",
                    List.of("payments:read"), List.of(new Corridor("US", "DE")));

            then(merchantRepository).should().save(eqIgnoring(expected, "merchantId"));
        }

        @Test
        void shouldHandleNullScopesAndCorridors() {
            given(merchantRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expected = Merchant.builder()
                    .name("Test Corp")
                    .country("US")
                    .scopes(List.of())
                    .corridors(List.of())
                    .status(MerchantStatus.PENDING)
                    .kybStatus(KybStatus.PENDING)
                    .rateLimitTier(RateLimitTier.STARTER)
                    .version(0L)
                    .build();

            merchantCommandHandler.register(UUID.randomUUID(), "Test Corp", "US", null, null);

            then(merchantRepository).should().save(eqIgnoring(expected, "merchantId", "externalId"));
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

            var expected = merchant.toBuilder()
                    .status(MerchantStatus.ACTIVE)
                    .kybStatus(KybStatus.VERIFIED)
                    .build();

            merchantCommandHandler.activate(externalId);

            then(merchantRepository).should().save(eqIgnoringTimestamps(expected));
        }

        @Test
        void shouldThrowWhenMerchantNotFound() {
            var externalId = UUID.randomUUID();
            given(merchantRepository.findByExternalId(externalId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> merchantCommandHandler.activate(externalId))
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

            var expected = merchant.toBuilder()
                    .status(MerchantStatus.SUSPENDED)
                    .build();

            merchantCommandHandler.suspend(externalId);

            then(merchantRepository).should().save(eqIgnoringTimestamps(expected));
            then(apiKeyRepository).should().deactivateAllByMerchantId(merchant.getMerchantId());
            then(oauthClientRepository).should().deactivateAllByMerchantId(merchant.getMerchantId());
            then(accessTokenRepository).should().revokeAllByMerchantId(merchant.getMerchantId());
        }

        @Test
        void shouldThrowWhenMerchantNotFound() {
            var externalId = UUID.randomUUID();
            given(merchantRepository.findByExternalId(externalId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> merchantCommandHandler.suspend(externalId))
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

            var expected = merchant.toBuilder()
                    .status(MerchantStatus.CLOSED)
                    .build();

            merchantCommandHandler.close(externalId);

            then(merchantRepository).should().save(eqIgnoringTimestamps(expected));
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

            merchantCommandHandler.findById(merchantId);

            then(merchantRepository).should().findById(merchantId);
        }

        @Test
        void shouldThrowWhenNotFound() {
            var merchantId = UUID.randomUUID();
            given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> merchantCommandHandler.findById(merchantId))
                    .isInstanceOf(MerchantNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("activateAndProvisionOAuthClient()")
    class ActivateAndProvisionOAuthClient {

        @Test
        void shouldActivateAndProvisionDefaultClient() {
            var externalId = UUID.randomUUID();
            var merchantId = UUID.randomUUID();
            var clientId = UUID.randomUUID();
            var merchant = Merchant.builder()
                    .merchantId(merchantId)
                    .externalId(externalId)
                    .name("Test Corp")
                    .country("US")
                    .scopes(List.of("payments:read"))
                    .corridors(List.of())
                    .status(MerchantStatus.PENDING)
                    .kybStatus(KybStatus.PENDING)
                    .rateLimitTier(RateLimitTier.STARTER)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(0L)
                    .build();
            given(merchantRepository.findByExternalId(externalId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var oauthClient = OAuthClient.builder()
                    .clientId(clientId)
                    .merchantId(merchantId)
                    .clientSecretHash("hash")
                    .name("Acme Corp Default Client")
                    .scopes(List.of("payments:read"))
                    .grantTypes(List.of("client_credentials"))
                    .active(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(0L)
                    .build();
            given(oauthClientCommandHandler.create(any(), any(), any(), any()))
                    .willReturn(new OAuthClientCommandHandler.CreateOAuthClientResult(oauthClient, "raw-secret"));

            merchantCommandHandler.activateAndProvisionOAuthClient(
                    externalId, "Acme Corp", "US", List.of("payments:read"));

            then(oauthClientCommandHandler).should().create(
                    merchantId, "Acme Corp Default Client",
                    List.of("payments:read"), List.of("client_credentials"));
            then(eventPublisher).should().publish(any(OAuthClientProvisionedEvent.class));
        }

        @Test
        void shouldUseMerchantScopesWhenNoneProvided() {
            var externalId = UUID.randomUUID();
            var merchantId = UUID.randomUUID();
            var clientId = UUID.randomUUID();
            var merchant = Merchant.builder()
                    .merchantId(merchantId)
                    .externalId(externalId)
                    .name("Test Corp")
                    .country("US")
                    .scopes(List.of("payments:read", "payments:write"))
                    .corridors(List.of())
                    .status(MerchantStatus.PENDING)
                    .kybStatus(KybStatus.PENDING)
                    .rateLimitTier(RateLimitTier.STARTER)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(0L)
                    .build();
            given(merchantRepository.findByExternalId(externalId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var oauthClient = OAuthClient.builder()
                    .clientId(clientId)
                    .merchantId(merchantId)
                    .clientSecretHash("hash")
                    .name("Test Corp Default Client")
                    .scopes(List.of("payments:read", "payments:write"))
                    .grantTypes(List.of("client_credentials"))
                    .active(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(0L)
                    .build();
            given(oauthClientCommandHandler.create(any(), any(), any(), any()))
                    .willReturn(new OAuthClientCommandHandler.CreateOAuthClientResult(oauthClient, "raw-secret"));

            merchantCommandHandler.activateAndProvisionOAuthClient(
                    externalId, "Test Corp", "US", List.of());

            then(oauthClientCommandHandler).should().create(
                    merchantId, "Test Corp Default Client",
                    List.of("payments:read", "payments:write"), List.of("client_credentials"));
        }
    }
}
