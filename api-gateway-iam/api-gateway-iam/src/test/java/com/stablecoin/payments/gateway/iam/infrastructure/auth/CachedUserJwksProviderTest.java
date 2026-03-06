package com.stablecoin.payments.gateway.iam.infrastructure.auth;

import com.stablecoin.payments.gateway.iam.domain.exception.UserJwksUnavailableException;
import com.stablecoin.payments.gateway.iam.infrastructure.client.MerchantIamClient;
import com.stablecoin.payments.gateway.iam.infrastructure.config.MerchantIamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachedUserJwksProviderTest {

    private static final String CACHE_KEY = "jwks:merchant-iam";
    private static final String JWKS_JSON = "{\"keys\":[{\"kty\":\"EC\"}]}";

    @Mock
    private MerchantIamClient merchantIamClient;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private CachedUserJwksProvider provider;

    @BeforeEach
    void setUp() {
        var properties = new MerchantIamProperties(
                "http://localhost:8083",
                "https://api.stablebridge.dev",
                "payment-platform",
                24);
        provider = new CachedUserJwksProvider(merchantIamClient, redis, properties);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Nested
    class WhenS13Available {

        @Test
        void shouldFetchAndCacheJwks() {
            when(valueOps.get(CACHE_KEY)).thenReturn(null);
            when(merchantIamClient.fetchJwks()).thenReturn(JWKS_JSON);

            var result = provider.fetchJwks();

            assertThat(result).isEqualTo(JWKS_JSON);
            verify(valueOps).set(CACHE_KEY, JWKS_JSON, Duration.ofHours(24));
        }

        @Test
        void shouldRefreshCacheEvenWhenCachedValueExists() {
            when(valueOps.get(CACHE_KEY)).thenReturn("old-jwks");
            when(merchantIamClient.fetchJwks()).thenReturn(JWKS_JSON);

            var result = provider.fetchJwks();

            assertThat(result).isEqualTo(JWKS_JSON);
            verify(valueOps).set(CACHE_KEY, JWKS_JSON, Duration.ofHours(24));
        }
    }

    @Nested
    class WhenS13Unavailable {

        @Test
        void shouldReturnCachedValueWhenAvailable() {
            when(valueOps.get(CACHE_KEY)).thenReturn(JWKS_JSON);
            when(merchantIamClient.fetchJwks()).thenThrow(new RuntimeException("Connection refused"));

            var result = provider.fetchJwks();

            assertThat(result).isEqualTo(JWKS_JSON);
        }

        @Test
        void shouldThrowWhenNoCachedValue() {
            when(valueOps.get(CACHE_KEY)).thenReturn(null);
            when(merchantIamClient.fetchJwks()).thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> provider.fetchJwks())
                    .isInstanceOf(UserJwksUnavailableException.class)
                    .hasMessageContaining("no cached value available");
        }
    }
}
