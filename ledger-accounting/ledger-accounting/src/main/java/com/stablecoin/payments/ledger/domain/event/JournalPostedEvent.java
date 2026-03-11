package com.stablecoin.payments.ledger.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal domain event raised when a ledger transaction with journal entries is posted.
 */
public record JournalPostedEvent(
        UUID transactionId,
        UUID paymentId,
        UUID correlationId,
        String sourceEvent,
        int entryCount,
        Instant postedAt
) {
}
