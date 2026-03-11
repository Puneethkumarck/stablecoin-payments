package com.stablecoin.payments.ledger.domain.port;

import java.math.BigDecimal;

/**
 * Domain port for reconciliation configuration.
 * Implemented by application-layer config (e.g. {@code ReconciliationConfig}).
 */
public interface ReconciliationProperties {

    BigDecimal tolerance();

    long retryIntervalMs();

    boolean retryEnabled();
}
