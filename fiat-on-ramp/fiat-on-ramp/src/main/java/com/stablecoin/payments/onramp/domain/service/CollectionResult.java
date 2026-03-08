package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.model.CollectionOrder;

/**
 * Wrapper returned by {@link CollectionCommandHandler#initiateCollection}
 * so the controller can distinguish 201 CREATED vs 200 OK (idempotent replay).
 */
public record CollectionResult(CollectionOrder order, boolean created) {}
