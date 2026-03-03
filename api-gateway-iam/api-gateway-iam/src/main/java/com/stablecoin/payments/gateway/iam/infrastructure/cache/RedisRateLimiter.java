package com.stablecoin.payments.gateway.iam.infrastructure.cache;

import com.stablecoin.payments.gateway.iam.domain.model.RateLimitPolicy;
import com.stablecoin.payments.gateway.iam.domain.port.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private static final String RATE_LIMIT_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('EXPIRE', key, window)
            end
            return current
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);

    private final StringRedisTemplate redis;

    @Override
    public RateLimitResult check(UUID merchantId, String endpoint, RateLimitPolicy policy) {
        var minuteKey = "ratelimit:" + merchantId + ":" + endpoint + ":min";
        var minuteLimit = policy.tier().requestsPerMinute();

        var currentMinute = executeScript(minuteKey, minuteLimit, 60);

        if (currentMinute > minuteLimit) {
            log.warn("Rate limit exceeded merchantId={} endpoint={} tier={} count={}/{}",
                    merchantId, endpoint, policy.tier(), currentMinute, minuteLimit);
            return new RateLimitResult(false, currentMinute.intValue(), minuteLimit, "1m");
        }

        var dayKey = "ratelimit:" + merchantId + ":" + endpoint + ":day";
        var dayLimit = policy.tier().requestsPerDay();
        var currentDay = executeScript(dayKey, dayLimit, 86400);

        if (currentDay > dayLimit) {
            log.warn("Daily rate limit exceeded merchantId={} endpoint={} tier={} count={}/{}",
                    merchantId, endpoint, policy.tier(), currentDay, dayLimit);
            return new RateLimitResult(false, currentDay.intValue(), dayLimit, "1d");
        }

        return new RateLimitResult(true, currentMinute.intValue(), minuteLimit, "1m");
    }

    private Long executeScript(String key, int limit, int windowSeconds) {
        return redis.execute(SCRIPT, List.of(key), String.valueOf(limit), String.valueOf(windowSeconds));
    }
}
