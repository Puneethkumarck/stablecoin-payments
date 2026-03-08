package com.stablecoin.payments.onramp.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CollectionFailedEvent(
        UUID collectionId,
        UUID paymentId,
        UUID correlationId,
        String reason,
        String errorCode,
        Instant failedAt
) {

    public static final String TOPIC = "fiat.collection.failed";
}
