package com.stablecoin.payments.ledger.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.ledger.reconciliation")
public record ReconciliationConfig(
        BigDecimal tolerance,
        long retryIntervalMs,
        boolean retryEnabled,
        boolean auditArchiveEnabled
) {

    public ReconciliationConfig {
        if (tolerance == null) {
            tolerance = new BigDecimal("0.01");
        }
        if (retryIntervalMs <= 0) {
            retryIntervalMs = 600_000;
        }
    }
}
