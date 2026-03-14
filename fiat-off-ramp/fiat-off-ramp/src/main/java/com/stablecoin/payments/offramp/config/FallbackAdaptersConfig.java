package com.stablecoin.payments.offramp.config;

import com.stablecoin.payments.offramp.domain.port.PayoutPartnerGateway;
import com.stablecoin.payments.offramp.domain.port.PayoutResult;
import com.stablecoin.payments.offramp.domain.port.RedemptionGateway;
import com.stablecoin.payments.offramp.domain.port.RedemptionResult;
import com.stablecoin.payments.offramp.domain.port.WebhookSignatureValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

/**
 * Provides fallback (dev/test) implementations of outbound ports.
 * Activated only when {@code app.fallback-adapters.enabled=true}.
 * Real adapters are registered by provider-specific configurations
 * under {@code infrastructure/provider/<name>/}.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "true")
public class FallbackAdaptersConfig {

    private static final BigDecimal DEV_FEE_MULTIPLIER = new BigDecimal("0.92");

    @Bean
    public RedemptionGateway fallbackRedemptionGateway(Clock clock) {
        return request -> {
            log.warn("[FALLBACK-REDEMPTION] Using dev redemption gateway payoutId={} amount={}",
                    request.payoutId(), request.amount());
            return new RedemptionResult(
                    "dev-redeem-" + UUID.randomUUID(),
                    request.amount().multiply(DEV_FEE_MULTIPLIER),
                    "EUR",
                    clock.instant()
            );
        };
    }

    @Bean
    public PayoutPartnerGateway fallbackPayoutPartnerGateway() {
        return request -> {
            log.warn("[FALLBACK-PAYOUT] Using dev payout gateway payoutId={} amount={} {}",
                    request.payoutId(), request.fiatAmount(), request.currency());
            return new PayoutResult(
                    "dev-payout-" + UUID.randomUUID(),
                    "PROCESSING",
                    null
            );
        };
    }

    @Bean
    public WebhookSignatureValidator fallbackWebhookSignatureValidator() {
        return (payload, signature) -> {
            log.warn("[FALLBACK-WEBHOOK] Using dev webhook validator — accepting all signatures");
            return true;
        };
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
