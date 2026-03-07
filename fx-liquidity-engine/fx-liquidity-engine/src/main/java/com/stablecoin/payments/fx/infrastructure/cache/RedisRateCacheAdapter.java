package com.stablecoin.payments.fx.infrastructure.cache;

import com.stablecoin.payments.fx.domain.model.CorridorRate;
import com.stablecoin.payments.fx.domain.port.RateCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.fx.cache.provider", havingValue = "redis")
public class RedisRateCacheAdapter implements RateCache {

    private static final String KEY_PREFIX = "fx:rate:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Duration ttl;

    public RedisRateCacheAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${app.fx.cache-ttl-seconds:5}") int ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = JsonMapper.builder().build();
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public void put(String fromCurrency, String toCurrency, CorridorRate rate) {
        try {
            var key = buildKey(fromCurrency, toCurrency);
            var entry = new CachedRate(
                    rate.fromCurrency(), rate.toCurrency(), rate.rate(),
                    rate.spreadBps(), rate.feeBps(), rate.provider(),
                    Instant.now().toEpochMilli()
            );
            var json = jsonMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(key, json, ttl);
            log.debug("[REDIS-CACHE] Cached rate {}:{} ttl={}s", fromCurrency, toCurrency, ttl.getSeconds());
        } catch (Exception ex) {
            log.warn("[REDIS-CACHE] Failed to cache rate {}:{}", fromCurrency, toCurrency, ex);
        }
    }

    @Override
    public Optional<CorridorRate> get(String fromCurrency, String toCurrency) {
        try {
            var key = buildKey(fromCurrency, toCurrency);
            var json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                log.debug("[REDIS-CACHE] Cache miss {}:{}", fromCurrency, toCurrency);
                return Optional.empty();
            }

            var cached = jsonMapper.readValue(json, CachedRate.class);
            int ageMs = (int) (Instant.now().toEpochMilli() - cached.cachedAtMs());
            if (ageMs > 5000) {
                log.debug("[REDIS-CACHE] Stale rate {}:{} ageMs={}", fromCurrency, toCurrency, ageMs);
                redisTemplate.delete(key);
                return Optional.empty();
            }

            var rate = CorridorRate.builder()
                    .fromCurrency(cached.fromCurrency())
                    .toCurrency(cached.toCurrency())
                    .rate(cached.rate())
                    .spreadBps(cached.spreadBps())
                    .feeBps(cached.feeBps())
                    .provider(cached.provider())
                    .ageMs(ageMs)
                    .build();

            log.debug("[REDIS-CACHE] Cache hit {}:{} ageMs={}", fromCurrency, toCurrency, ageMs);
            return Optional.of(rate);
        } catch (Exception ex) {
            log.warn("[REDIS-CACHE] Failed to read cache {}:{}", fromCurrency, toCurrency, ex);
            return Optional.empty();
        }
    }

    private String buildKey(String fromCurrency, String toCurrency) {
        return KEY_PREFIX + fromCurrency + ":" + toCurrency;
    }

    record CachedRate(
            String fromCurrency,
            String toCurrency,
            BigDecimal rate,
            int spreadBps,
            int feeBps,
            String provider,
            long cachedAtMs
    ) {}
}
