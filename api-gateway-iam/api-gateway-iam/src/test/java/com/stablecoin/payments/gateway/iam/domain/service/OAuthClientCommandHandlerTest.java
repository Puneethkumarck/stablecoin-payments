package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotActiveException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ScopeExceededException;
import com.stablecoin.payments.gateway.iam.domain.model.KybStatus;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus;
import com.stablecoin.payments.gateway.iam.domain.model.OAuthClient;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretGenerator;
import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretHasher;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OAuthClientCommandHandlerTest {

    @Mock private OAuthClientRepository oauthClientRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private ClientSecretGenerator clientSecretGenerator;
    @Mock private ClientSecretHasher clientSecretHasher;

    private OAuthClientCommandHandler commandHandler;

    private static final UUID MERCHANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        commandHandler = new OAuthClientCommandHandler(oauthClientRepository, merchantRepository,
                clientSecretGenerator, clientSecretHasher);
    }

    private static Merchant activeMerchant() {
        return Merchant.builder()
                .merchantId(MERCHANT_ID)
                .externalId(UUID.randomUUID())
                .name("Test Corp")
                .country("US")
                .scopes(List.of("payments:read", "payments:write"))
                .corridors(List.of())
                .status(MerchantStatus.ACTIVE)
                .kybStatus(KybStatus.VERIFIED)
                .rateLimitTier(RateLimitTier.STARTER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    private static Merchant pendingMerchant() {
        return Merchant.builder()
                .merchantId(MERCHANT_ID)
                .externalId(UUID.randomUUID())
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
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create OAuth client with generated secret")
        void shouldCreateOAuthClient() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));
            given(clientSecretGenerator.generate()).willReturn("raw-secret-hex");
            given(clientSecretHasher.hash("raw-secret-hex")).willReturn("$2a$12$hashed");
            given(oauthClientRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expected = OAuthClient.builder()
                    .merchantId(MERCHANT_ID)
                    .clientSecretHash("$2a$12$hashed")
                    .name("My Client")
                    .scopes(List.of("payments:read"))
                    .grantTypes(List.of("client_credentials"))
                    .active(true)
                    .version(0L)
                    .build();

            commandHandler.create(MERCHANT_ID, "My Client",
                    List.of("payments:read"), List.of("client_credentials"));

            then(oauthClientRepository).should().save(eqIgnoring(expected, "clientId"));
        }

        @Test
        @DisplayName("should use merchant scopes when no scopes provided")
        void shouldUseMerchantScopesAsDefault() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));
            given(clientSecretGenerator.generate()).willReturn("secret");
            given(clientSecretHasher.hash("secret")).willReturn("hash");
            given(oauthClientRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expected = OAuthClient.builder()
                    .merchantId(MERCHANT_ID)
                    .clientSecretHash("hash")
                    .name("Client")
                    .scopes(List.of("payments:read", "payments:write"))
                    .grantTypes(List.of("client_credentials"))
                    .active(true)
                    .version(0L)
                    .build();

            commandHandler.create(MERCHANT_ID, "Client", List.of(), List.of());

            then(oauthClientRepository).should().save(eqIgnoring(expected, "clientId"));
        }

        @Test
        @DisplayName("should default to client_credentials grant type")
        void shouldDefaultToClientCredentials() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));
            given(clientSecretGenerator.generate()).willReturn("secret");
            given(clientSecretHasher.hash("secret")).willReturn("hash");
            given(oauthClientRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expected = OAuthClient.builder()
                    .merchantId(MERCHANT_ID)
                    .clientSecretHash("hash")
                    .name("Client")
                    .scopes(List.of("payments:read", "payments:write"))
                    .grantTypes(List.of("client_credentials"))
                    .active(true)
                    .version(0L)
                    .build();

            commandHandler.create(MERCHANT_ID, "Client", null, null);

            then(oauthClientRepository).should().save(eqIgnoring(expected, "clientId"));
        }

        @Test
        @DisplayName("should throw when merchant not found")
        void shouldThrowWhenMerchantNotFound() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> commandHandler.create(MERCHANT_ID, "Client", List.of(), List.of()))
                    .isInstanceOf(MerchantNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when merchant is not active")
        void shouldThrowWhenMerchantNotActive() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(pendingMerchant()));

            assertThatThrownBy(() -> commandHandler.create(MERCHANT_ID, "Client", List.of(), List.of()))
                    .isInstanceOf(MerchantNotActiveException.class);
        }

        @Test
        @DisplayName("should throw when requested scopes exceed merchant scopes")
        void shouldThrowWhenScopesExceeded() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));

            assertThatThrownBy(() -> commandHandler.create(MERCHANT_ID, "Client",
                    List.of("payments:read", "admin:full"), List.of()))
                    .isInstanceOf(ScopeExceededException.class);
        }
    }
}
