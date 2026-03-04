package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.exception.InvalidClientCredentialsException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotActiveException;
import com.stablecoin.payments.gateway.iam.domain.exception.ScopeExceededException;
import com.stablecoin.payments.gateway.iam.domain.model.AccessToken;
import com.stablecoin.payments.gateway.iam.domain.model.KybStatus;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus;
import com.stablecoin.payments.gateway.iam.domain.model.OAuthClient;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.port.AccessTokenRepository;
import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretHasher;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.OAuthClientRepository;
import com.stablecoin.payments.gateway.iam.domain.port.TokenIssuer;
import com.stablecoin.payments.gateway.iam.domain.port.TokenRevocationCache;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthCommandHandlerTest {

    @Mock private OAuthClientRepository oauthClientRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private AccessTokenRepository accessTokenRepository;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private ClientSecretHasher clientSecretHasher;
    @Mock private TokenRevocationCache tokenRevocationCache;

    private AuthCommandHandler authCommandHandler;

    private static final long TTL = 3600L;

    @BeforeEach
    void setUp() {
        authCommandHandler = new AuthCommandHandler(oauthClientRepository, merchantRepository,
                accessTokenRepository, tokenIssuer, clientSecretHasher, tokenRevocationCache, TTL);
    }

    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();

    private static OAuthClient activeClient() {
        return OAuthClient.builder()
                .clientId(CLIENT_ID)
                .merchantId(MERCHANT_ID)
                .clientSecretHash("$2a$12$hash")
                .name("Test Client")
                .scopes(List.of("payments:read", "payments:write"))
                .grantTypes(List.of("client_credentials"))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
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

    @Nested
    @DisplayName("issueToken()")
    class IssueToken {

        @Test
        void shouldIssueTokenWithClientScopes() {
            given(oauthClientRepository.findActiveById(CLIENT_ID)).willReturn(Optional.of(activeClient()));
            given(clientSecretHasher.matches("secret", "$2a$12$hash")).willReturn(true);
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));
            given(tokenIssuer.issueToken(eq(MERCHANT_ID), eq(CLIENT_ID), anyList())).willReturn("jwt-token");
            given(accessTokenRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expected = AccessToken.builder()
                    .merchantId(MERCHANT_ID)
                    .clientId(CLIENT_ID)
                    .scopes(List.of("payments:read", "payments:write"))
                    .revoked(false)
                    .build();

            authCommandHandler.issueToken(CLIENT_ID, "secret", null);

            then(accessTokenRepository).should().save(eqIgnoring(expected, "jti"));
        }

        @Test
        void shouldIssueTokenWithRequestedScopes() {
            given(oauthClientRepository.findActiveById(CLIENT_ID)).willReturn(Optional.of(activeClient()));
            given(clientSecretHasher.matches("secret", "$2a$12$hash")).willReturn(true);
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));
            given(tokenIssuer.issueToken(eq(MERCHANT_ID), eq(CLIENT_ID), anyList())).willReturn("jwt-token");
            given(accessTokenRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expected = AccessToken.builder()
                    .merchantId(MERCHANT_ID)
                    .clientId(CLIENT_ID)
                    .scopes(List.of("payments:read"))
                    .revoked(false)
                    .build();

            authCommandHandler.issueToken(CLIENT_ID, "secret", List.of("payments:read"));

            then(accessTokenRepository).should().save(eqIgnoring(expected, "jti"));
        }

        @Test
        void shouldThrowWhenClientNotFound() {
            given(oauthClientRepository.findActiveById(CLIENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authCommandHandler.issueToken(CLIENT_ID, "secret", null))
                    .isInstanceOf(InvalidClientCredentialsException.class);
        }

        @Test
        void shouldThrowWhenSecretInvalid() {
            given(oauthClientRepository.findActiveById(CLIENT_ID)).willReturn(Optional.of(activeClient()));
            given(clientSecretHasher.matches("wrong", "$2a$12$hash")).willReturn(false);

            assertThatThrownBy(() -> authCommandHandler.issueToken(CLIENT_ID, "wrong", null))
                    .isInstanceOf(InvalidClientCredentialsException.class);
        }

        @Test
        void shouldThrowWhenMerchantNotActive() {
            var inactiveMerchant = Merchant.builder()
                    .merchantId(MERCHANT_ID).externalId(UUID.randomUUID()).name("Test")
                    .country("US").scopes(List.of()).corridors(List.of())
                    .status(MerchantStatus.SUSPENDED).kybStatus(KybStatus.VERIFIED)
                    .rateLimitTier(RateLimitTier.STARTER).createdAt(Instant.now())
                    .updatedAt(Instant.now()).version(0L).build();
            given(oauthClientRepository.findActiveById(CLIENT_ID)).willReturn(Optional.of(activeClient()));
            given(clientSecretHasher.matches("secret", "$2a$12$hash")).willReturn(true);
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(inactiveMerchant));

            assertThatThrownBy(() -> authCommandHandler.issueToken(CLIENT_ID, "secret", null))
                    .isInstanceOf(MerchantNotActiveException.class);
        }

        @Test
        void shouldThrowWhenScopesExceedClient() {
            given(oauthClientRepository.findActiveById(CLIENT_ID)).willReturn(Optional.of(activeClient()));
            given(clientSecretHasher.matches("secret", "$2a$12$hash")).willReturn(true);
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));

            assertThatThrownBy(() -> authCommandHandler.issueToken(CLIENT_ID, "secret", List.of("admin:write")))
                    .isInstanceOf(ScopeExceededException.class);
        }
    }

    @Nested
    @DisplayName("revokeToken()")
    class RevokeToken {

        @Test
        void shouldRevokeActiveToken() {
            var jti = UUID.randomUUID();
            var token = AccessToken.builder()
                    .jti(jti).merchantId(MERCHANT_ID).clientId(CLIENT_ID)
                    .scopes(List.of("payments:read"))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
                    .revoked(false).build();
            given(accessTokenRepository.findByJti(jti)).willReturn(Optional.of(token));
            given(accessTokenRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expected = token.toBuilder()
                    .revoked(true)
                    .build();

            authCommandHandler.revokeToken(jti);

            then(accessTokenRepository).should().save(eqIgnoring(expected, "revokedAt"));
            then(tokenRevocationCache).should().markRevoked(eq(jti), any());
        }
    }

    @Nested
    @DisplayName("jwksJson()")
    class JwksJson {

        @Test
        void shouldDelegateToTokenIssuer() {
            given(tokenIssuer.jwksJson()).willReturn("{\"keys\":[]}");

            authCommandHandler.jwksJson();

            then(tokenIssuer).should().jwksJson();
        }
    }
}
