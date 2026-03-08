package com.stablecoin.payments.onramp.api;

import java.time.Instant;
import java.util.UUID;

public record CollectionResponse(
        UUID collectionId,
        UUID paymentId,
        String status,
        String paymentRail,
        String psp,
        String pspReference,
        Instant estimatedSettlementAt,
        Instant createdAt,
        Instant expiresAt
) {}
