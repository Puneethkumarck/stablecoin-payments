package com.stablecoin.payments.fx.infrastructure.cache;

import com.stablecoin.payments.fx.domain.model.CorridorRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisRateCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisRateCacheAdapter cache;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        cache = new RedisRateCacheAdapter(redisTemplate, 5);
    }

    @Nested
    @DisplayName("put")
    class Put {

        @Test
        @DisplayName("should store rate in Redis with TTL")
        void storesWithTtl() {
            var rate = CorridorRate.builder()
                    .fromCurrency("USD").toCurrency("EUR")
                    .rate(new BigDecimal("0.92")).spreadBps(30).feeBps(30)
                    .provider("refinitiv").ageMs(100)
                    .build();

            cache.put("USD", "EUR", rate);

            var keyCaptor = ArgumentCaptor.forClass(String.class);
            var jsonCaptor = ArgumentCaptor.forClass(String.class);
            var ttlCaptor = ArgumentCaptor.forClass(Duration.class);

            then(valueOps).should().set(keyCaptor.capture(), jsonCaptor.capture(), ttlCaptor.capture());

            assertThat(keyCaptor.getValue()).isEqualTo("fx:rate:USD:EUR");
            assertThat(jsonCaptor.getValue()).contains("\"fromCurrency\":\"USD\"");
            assertThat(jsonCaptor.getValue()).contains("\"rate\":0.92");
            assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(5));
        }
    }

    @Nested
    @DisplayName("get")
    class Get {

        @Test
        @DisplayName("should return empty on cache miss")
        void cacheMiss() {
            given(valueOps.get("fx:rate:USD:EUR")).willReturn(null);

            var result = cache.get("USD", "EUR");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return rate on cache hit with valid age")
        void cacheHit() {
            long nowMs = Instant.now().toEpochMilli();
            var json = """
                {"fromCurrency":"USD","toCurrency":"EUR","rate":0.92,"spreadBps":30,"feeBps":30,"provider":"refinitiv","cachedAtMs":%d}
                """.formatted(nowMs);
            given(valueOps.get("fx:rate:USD:EUR")).willReturn(json);

            var result = cache.get("USD", "EUR");

            assertThat(result).isPresent();
            var rate = result.get();
            assertThat(rate.fromCurrency()).isEqualTo("USD");
            assertThat(rate.toCurrency()).isEqualTo("EUR");
            assertThat(rate.rate()).isEqualByComparingTo("0.92");
            assertThat(rate.provider()).isEqualTo("refinitiv");
            assertThat(rate.ageMs()).isLessThanOrEqualTo(5000);
        }

        @Test
        @DisplayName("should return empty and delete stale entry")
        void staleEntry() {
            long oldMs = Instant.now().toEpochMilli() - 6000;
            var json = """
                {"fromCurrency":"USD","toCurrency":"EUR","rate":0.92,"spreadBps":30,"feeBps":30,"provider":"refinitiv","cachedAtMs":%d}
                """.formatted(oldMs);
            given(valueOps.get("fx:rate:USD:EUR")).willReturn(json);

            var result = cache.get("USD", "EUR");

            assertThat(result).isEmpty();
            then(redisTemplate).should().delete("fx:rate:USD:EUR");
        }

        @Test
        @DisplayName("should return empty on deserialization error")
        void deserializationError() {
            given(valueOps.get("fx:rate:USD:EUR")).willReturn("invalid-json");

            var result = cache.get("USD", "EUR");

            assertThat(result).isEmpty();
        }
    }
}
