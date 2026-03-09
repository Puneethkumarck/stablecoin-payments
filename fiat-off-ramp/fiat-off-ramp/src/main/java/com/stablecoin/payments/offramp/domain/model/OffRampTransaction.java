package com.stablecoin.payments.offramp.domain.model;

import lombok.AccessLevel;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity recording off-ramp partner transaction events (audit trail).
 * <p>
 * Each interaction with an off-ramp partner (Modulr, CurrencyCloud, Safaricom, etc.)
 * creates a new OffRampTransaction record for auditability.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record OffRampTransaction(
        UUID offRampTxnId,
        UUID payoutId,
        String partnerName,
        String eventType,
        BigDecimal amount,
        String currency,
        String status,
        String rawResponse,
        Instant receivedAt
) {

    /**
     * Creates a new OffRampTransaction.
     */
    public static OffRampTransaction create(UUID payoutId, String partnerName,
                                            String eventType, BigDecimal amount,
                                            String currency, String status,
                                            String rawResponse) {
        if (payoutId == null) {
            throw new IllegalArgumentException("payoutId is required");
        }
        if (partnerName == null || partnerName.isBlank()) {
            throw new IllegalArgumentException("partnerName is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }

        return OffRampTransaction.builder()
                .offRampTxnId(UUID.randomUUID())
                .payoutId(payoutId)
                .partnerName(partnerName)
                .eventType(eventType)
                .amount(amount)
                .currency(currency)
                .status(status)
                .rawResponse(rawResponse != null ? rawResponse : "{}")
                .receivedAt(Instant.now())
                .build();
    }
}
