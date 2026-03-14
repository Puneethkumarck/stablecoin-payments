package com.stablecoin.payments.onramp.config;

import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import com.stablecoin.payments.onramp.domain.port.PspGateway;
import com.stablecoin.payments.onramp.domain.port.PspPaymentResult;
import com.stablecoin.payments.onramp.domain.port.PspRefundResult;
import com.stablecoin.payments.onramp.domain.port.WebhookSignatureValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public PspGateway fallbackPspGateway() {
        return new PspGateway() {
            @Override
            public PspPaymentResult initiatePayment(
                    com.stablecoin.payments.onramp.domain.port.PspPaymentRequest request) {
                log.warn("[FALLBACK-PSP] Using dev PSP gateway for payment collectionId={}",
                        request.collectionId());
                return new PspPaymentResult("dev-pi-" + UUID.randomUUID(), "succeeded");
            }

            @Override
            public PspRefundResult initiateRefund(
                    com.stablecoin.payments.onramp.domain.port.PspRefundRequest request) {
                log.warn("[FALLBACK-PSP] Using dev PSP gateway for refund collectionId={}",
                        request.collectionId());
                return new PspRefundResult("dev-re-" + UUID.randomUUID(), "succeeded");
            }
        };
    }

    @Bean
    public WebhookSignatureValidator fallbackWebhookSignatureValidator() {
        return (payload, signature) -> {
            log.warn("[FALLBACK-WEBHOOK] Using dev webhook signature validator — always valid");
            return true;
        };
    }

    @Bean
    public CollectionEventPublisher fallbackCollectionEventPublisher() {
        return event -> log.warn("[FALLBACK-EVENT] Using dev event publisher — event={}",
                event.getClass().getSimpleName());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
