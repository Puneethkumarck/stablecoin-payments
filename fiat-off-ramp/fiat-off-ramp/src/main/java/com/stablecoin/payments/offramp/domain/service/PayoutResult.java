package com.stablecoin.payments.offramp.domain.service;

import com.stablecoin.payments.offramp.domain.model.PayoutOrder;

/**
 * Wrapper returned by {@link PayoutCommandHandler#initiatePayout}
 * so the controller can distinguish 202 ACCEPTED (new) vs 200 OK (idempotent replay).
 */
public record PayoutResult(PayoutOrder order, boolean created) {}
