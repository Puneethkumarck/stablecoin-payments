package com.stablecoin.payments.orchestrator.application.controller;

import com.stablecoin.payments.orchestrator.domain.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a payment in the API layer.
 */
public record PaymentResponse(
        UUID paymentId,
        String idempotencyKey,
        UUID correlationId,
        String state,
        UUID senderId,
        UUID recipientId,
        BigDecimal sourceAmount,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal targetAmount,
        String sourceCountry,
        String targetCountry,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {

    /**
     * Maps a domain {@link Payment} aggregate to the API response DTO.
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.paymentId(),
                payment.idempotencyKey(),
                payment.correlationId(),
                payment.state().name(),
                payment.senderId(),
                payment.recipientId(),
                payment.sourceAmount().amount(),
                payment.sourceCurrency(),
                payment.targetCurrency(),
                payment.targetAmount() != null ? payment.targetAmount().amount() : null,
                payment.corridor().sourceCountry(),
                payment.corridor().targetCountry(),
                payment.failureReason(),
                payment.createdAt(),
                payment.updatedAt(),
                payment.expiresAt()
        );
    }
}
