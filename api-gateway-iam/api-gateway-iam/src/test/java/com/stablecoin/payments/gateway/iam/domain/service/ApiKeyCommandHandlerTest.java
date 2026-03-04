package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.event.ApiKeyRevokedEvent;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyExpiredException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyRevokedException;
import com.stablecoin.payments.gateway.iam.domain.exception.IpNotAllowedException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotActiveException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ScopeExceededException;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKey;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment;
import com.stablecoin.payments.gateway.iam.domain.model.KybStatus;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyGenerator;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyHasher;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import com.stablecoin.payments.gateway.iam.domain.port.EventPublisher;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
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
class ApiKeyCommandHandlerTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private ApiKeyGenerator apiKeyGenerator;
    @Mock private ApiKeyHasher apiKeyHasher;
    @Mock private EventPublisher<Object> eventPublisher;

    private ApiKeyCommandHandler apiKeyCommandHandler;

    private static final UUID MERCHANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        apiKeyCommandHandler = new ApiKeyCommandHandler(apiKeyRepository, merchantRepository,
                apiKeyGenerator, apiKeyHasher, eventPublisher);
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
    @DisplayName("create()")
    class Create {

        @Test
        void shouldCreateApiKey() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));
            given(apiKeyGenerator.generate(ApiKeyEnvironment.LIVE))
                    .willReturn(new ApiKeyGenerator.GeneratedApiKey("pk_live_abc123", "pk_live_"));
            given(apiKeyHasher.hash("pk_live_abc123")).willReturn("sha256hash");
            given(apiKeyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expected = ApiKey.builder()
                    .merchantId(MERCHANT_ID)
                    .keyHash("sha256hash")
                    .keyPrefix("pk_live_")
                    .name("My Key")
                    .environment(ApiKeyEnvironment.LIVE)
                    .scopes(List.of("payments:read"))
                    .allowedIps(List.of("10.0.0.1"))
                    .active(true)
                    .version(0L)
                    .build();

            apiKeyCommandHandler.create(MERCHANT_ID, "My Key", ApiKeyEnvironment.LIVE,
                    List.of("payments:read"), List.of("10.0.0.1"), null);

            then(apiKeyRepository).should().save(eqIgnoring(expected, "keyId"));
        }

        @Test
        void shouldUseAllMerchantScopesWhenNoneRequested() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));
            given(apiKeyGenerator.generate(ApiKeyEnvironment.LIVE))
                    .willReturn(new ApiKeyGenerator.GeneratedApiKey("pk_live_abc123", "pk_live_"));
            given(apiKeyHasher.hash("pk_live_abc123")).willReturn("sha256hash");
            given(apiKeyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expected = ApiKey.builder()
                    .merchantId(MERCHANT_ID)
                    .keyHash("sha256hash")
                    .keyPrefix("pk_live_")
                    .name("My Key")
                    .environment(ApiKeyEnvironment.LIVE)
                    .scopes(List.of("payments:read", "payments:write"))
                    .allowedIps(List.of())
                    .active(true)
                    .version(0L)
                    .build();

            apiKeyCommandHandler.create(MERCHANT_ID, "My Key", ApiKeyEnvironment.LIVE,
                    null, null, null);

            then(apiKeyRepository).should().save(eqIgnoring(expected, "keyId"));
        }

        @Test
        void shouldThrowWhenMerchantNotFound() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> apiKeyCommandHandler.create(MERCHANT_ID, "Key",
                    ApiKeyEnvironment.LIVE, null, null, null))
                    .isInstanceOf(MerchantNotFoundException.class);
        }

        @Test
        void shouldThrowWhenMerchantNotActive() {
            var inactiveMerchant = Merchant.builder()
                    .merchantId(MERCHANT_ID).externalId(UUID.randomUUID()).name("Test")
                    .country("US").scopes(List.of()).corridors(List.of())
                    .status(MerchantStatus.PENDING).kybStatus(KybStatus.PENDING)
                    .rateLimitTier(RateLimitTier.STARTER).createdAt(Instant.now())
                    .updatedAt(Instant.now()).version(0L).build();
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(inactiveMerchant));

            assertThatThrownBy(() -> apiKeyCommandHandler.create(MERCHANT_ID, "Key",
                    ApiKeyEnvironment.LIVE, null, null, null))
                    .isInstanceOf(MerchantNotActiveException.class);
        }

        @Test
        void shouldThrowWhenScopesExceedMerchant() {
            given(merchantRepository.findById(MERCHANT_ID)).willReturn(Optional.of(activeMerchant()));

            assertThatThrownBy(() -> apiKeyCommandHandler.create(MERCHANT_ID, "Key",
                    ApiKeyEnvironment.LIVE, List.of("admin:write"), null, null))
                    .isInstanceOf(ScopeExceededException.class);
        }
    }

    @Nested
    @DisplayName("revoke()")
    class Revoke {

        @Test
        void shouldRevokeAndPublishEvent() {
            var keyId = UUID.randomUUID();
            var apiKey = ApiKey.builder()
                    .keyId(keyId).merchantId(MERCHANT_ID)
                    .keyHash("hash").keyPrefix("pk_live_")
                    .name("Key").environment(ApiKeyEnvironment.LIVE)
                    .scopes(List.of()).allowedIps(List.of())
                    .active(true).createdAt(Instant.now())
                    .updatedAt(Instant.now()).version(0L).build();
            given(apiKeyRepository.findById(keyId)).willReturn(Optional.of(apiKey));
            given(apiKeyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expectedKey = apiKey.toBuilder()
                    .active(false)
                    .build();

            var expectedEvent = new ApiKeyRevokedEvent(keyId, MERCHANT_ID, "pk_live_", null);

            apiKeyCommandHandler.revoke(keyId);

            then(apiKeyRepository).should().save(eqIgnoringTimestamps(expectedKey));
            then(eventPublisher).should().publish(eqIgnoringTimestamps(expectedEvent));
        }

        @Test
        void shouldThrowWhenKeyNotFound() {
            var keyId = UUID.randomUUID();
            given(apiKeyRepository.findById(keyId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> apiKeyCommandHandler.revoke(keyId))
                    .isInstanceOf(ApiKeyNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        void shouldReturnKeyWhenValid() {
            var apiKey = ApiKey.builder()
                    .keyId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .keyHash("sha256hash").keyPrefix("pk_live_")
                    .name("Key").environment(ApiKeyEnvironment.LIVE)
                    .scopes(List.of("payments:read")).allowedIps(List.of())
                    .active(true).expiresAt(Instant.now().plusSeconds(3600))
                    .createdAt(Instant.now()).updatedAt(Instant.now()).version(0L).build();
            given(apiKeyHasher.hash("pk_live_raw")).willReturn("sha256hash");
            given(apiKeyRepository.findByKeyHash("sha256hash")).willReturn(Optional.of(apiKey));

            apiKeyCommandHandler.validate("pk_live_raw", "10.0.0.1");

            then(apiKeyRepository).should().findByKeyHash("sha256hash");
        }

        @Test
        void shouldThrowWhenKeyNotFound() {
            given(apiKeyHasher.hash("pk_live_raw")).willReturn("sha256hash");
            given(apiKeyRepository.findByKeyHash("sha256hash")).willReturn(Optional.empty());

            assertThatThrownBy(() -> apiKeyCommandHandler.validate("pk_live_raw", "10.0.0.1"))
                    .isInstanceOf(ApiKeyNotFoundException.class);
        }

        @Test
        void shouldThrowWhenKeyRevoked() {
            var apiKey = ApiKey.builder()
                    .keyId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .keyHash("sha256hash").keyPrefix("pk_live_")
                    .name("Key").environment(ApiKeyEnvironment.LIVE)
                    .scopes(List.of()).allowedIps(List.of())
                    .active(false).createdAt(Instant.now())
                    .updatedAt(Instant.now()).version(0L).build();
            given(apiKeyHasher.hash("pk_live_raw")).willReturn("sha256hash");
            given(apiKeyRepository.findByKeyHash("sha256hash")).willReturn(Optional.of(apiKey));

            assertThatThrownBy(() -> apiKeyCommandHandler.validate("pk_live_raw", "10.0.0.1"))
                    .isInstanceOf(ApiKeyRevokedException.class);
        }

        @Test
        void shouldThrowWhenKeyExpired() {
            var apiKey = ApiKey.builder()
                    .keyId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .keyHash("sha256hash").keyPrefix("pk_live_")
                    .name("Key").environment(ApiKeyEnvironment.LIVE)
                    .scopes(List.of()).allowedIps(List.of())
                    .active(true).expiresAt(Instant.now().minusSeconds(3600))
                    .createdAt(Instant.now()).updatedAt(Instant.now()).version(0L).build();
            given(apiKeyHasher.hash("pk_live_raw")).willReturn("sha256hash");
            given(apiKeyRepository.findByKeyHash("sha256hash")).willReturn(Optional.of(apiKey));

            assertThatThrownBy(() -> apiKeyCommandHandler.validate("pk_live_raw", "10.0.0.1"))
                    .isInstanceOf(ApiKeyExpiredException.class);
        }

        @Test
        void shouldThrowWhenIpNotAllowed() {
            var apiKey = ApiKey.builder()
                    .keyId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .keyHash("sha256hash").keyPrefix("pk_live_")
                    .name("Key").environment(ApiKeyEnvironment.LIVE)
                    .scopes(List.of()).allowedIps(List.of("10.0.0.1"))
                    .active(true).expiresAt(Instant.now().plusSeconds(3600))
                    .createdAt(Instant.now()).updatedAt(Instant.now()).version(0L).build();
            given(apiKeyHasher.hash("pk_live_raw")).willReturn("sha256hash");
            given(apiKeyRepository.findByKeyHash("sha256hash")).willReturn(Optional.of(apiKey));

            assertThatThrownBy(() -> apiKeyCommandHandler.validate("pk_live_raw", "192.168.1.1"))
                    .isInstanceOf(IpNotAllowedException.class);
        }
    }
}
