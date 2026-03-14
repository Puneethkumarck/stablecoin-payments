package com.stablecoin.payments.ledger.infrastructure.config;

import com.stablecoin.payments.ledger.domain.event.ReconciliationCompletedDomainEvent;
import com.stablecoin.payments.ledger.domain.event.ReconciliationDiscrepancyDomainEvent;
import com.stablecoin.payments.ledger.domain.port.LedgerEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "true")
public class FallbackAdaptersConfig {

    @Bean
    public LedgerEventPublisher ledgerEventPublisher() {
        return new LedgerEventPublisher() {
            @Override
            public void publishReconciliationCompleted(ReconciliationCompletedDomainEvent event) {
                log.debug("[FALLBACK] No-op reconciliation completed paymentId={}", event.paymentId());
            }

            @Override
            public void publishReconciliationDiscrepancy(ReconciliationDiscrepancyDomainEvent event) {
                log.debug("[FALLBACK] No-op reconciliation discrepancy paymentId={}", event.paymentId());
            }
        };
    }
}
