package com.stablecoin.payments.offramp.config;

import com.stablecoin.payments.offramp.domain.port.RedemptionGateway;
import com.stablecoin.payments.offramp.domain.port.RedemptionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Provides fallback (dev/test) implementations of outbound ports
 * via {@code @ConditionalOnMissingBean}. Real adapters are registered
 * by provider-specific configurations under
 * {@code infrastructure/provider/<name>/}.
 */
@Slf4j
@Configuration
public class FallbackAdaptersConfig {

    @Bean
    @ConditionalOnMissingBean
    public RedemptionGateway fallbackRedemptionGateway() {
        return request -> {
            log.warn("[FALLBACK-REDEMPTION] Using dev redemption gateway payoutId={} amount={}",
                    request.payoutId(), request.amount());
            return new RedemptionResult(
                    "dev-redeem-" + UUID.randomUUID(),
                    request.amount().multiply(new BigDecimal("0.92")),
                    "EUR",
                    Instant.now()
            );
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
