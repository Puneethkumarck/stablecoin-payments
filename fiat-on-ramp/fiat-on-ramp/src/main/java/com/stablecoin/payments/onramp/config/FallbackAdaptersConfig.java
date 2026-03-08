package com.stablecoin.payments.onramp.config;

import com.stablecoin.payments.onramp.domain.port.PspGateway;
import com.stablecoin.payments.onramp.domain.port.PspPaymentResult;
import com.stablecoin.payments.onramp.domain.port.PspRefundResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
