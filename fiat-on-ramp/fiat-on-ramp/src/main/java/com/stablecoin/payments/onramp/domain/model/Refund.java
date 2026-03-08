package com.stablecoin.payments.onramp.domain.model;

import lombok.AccessLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.onramp.domain.model.RefundStatus.COMPLETED;
import static com.stablecoin.payments.onramp.domain.model.RefundStatus.FAILED;
import static com.stablecoin.payments.onramp.domain.model.RefundStatus.PENDING;
import static com.stablecoin.payments.onramp.domain.model.RefundStatus.PROCESSING;

/**
 * Child entity representing a refund for a collected payment.
 * <p>
 * Tracks the refund lifecycle: {@code PENDING -> PROCESSING -> COMPLETED/FAILED}.
 * Immutable record — all state transitions return new instances via {@code toBuilder()}.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record Refund(
        UUID refundId,
        UUID collectionId,
        UUID paymentId,
        Money refundAmount,
        String reason,
        RefundStatus status,
        String pspRefundRef,
        Instant initiatedAt,
        Instant completedAt,
        String failureReason
) {

    // -- Factory Method ---------------------------------------------------

    /**
     * Creates a new refund in PENDING state.
     */
    public static Refund initiate(UUID collectionId, UUID paymentId,
                                  Money refundAmount, String reason) {
        if (collectionId == null) {
            throw new IllegalArgumentException("collectionId is required");
        }
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId is required");
        }
        if (refundAmount == null) {
            throw new IllegalArgumentException("refundAmount is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }

        return Refund.builder()
                .refundId(UUID.randomUUID())
                .collectionId(collectionId)
                .paymentId(paymentId)
                .refundAmount(refundAmount)
                .reason(reason)
                .status(PENDING)
                .initiatedAt(Instant.now())
                .build();
    }

    // -- State Transition Methods -----------------------------------------

    /**
     * Starts processing the refund. Transitions PENDING -> PROCESSING.
     */
    public Refund startProcessing() {
        if (status != PENDING) {
            throw new IllegalStateException(
                    "Refund %s cannot start processing from state %s".formatted(refundId, status));
        }
        return toBuilder()
                .status(PROCESSING)
                .build();
    }

    /**
     * Completes the refund. Transitions PROCESSING -> COMPLETED.
     */
    public Refund complete(String pspRefundRef) {
        if (status != PROCESSING) {
            throw new IllegalStateException(
                    "Refund %s cannot complete from state %s".formatted(refundId, status));
        }
        if (pspRefundRef == null || pspRefundRef.isBlank()) {
            throw new IllegalArgumentException("PSP refund reference is required");
        }
        return toBuilder()
                .status(COMPLETED)
                .pspRefundRef(pspRefundRef)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Fails the refund. Transitions PROCESSING -> FAILED.
     */
    public Refund fail(String failureReason) {
        if (status != PROCESSING) {
            throw new IllegalStateException(
                    "Refund %s cannot fail from state %s".formatted(refundId, status));
        }
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("Failure reason is required");
        }
        return toBuilder()
                .status(FAILED)
                .failureReason(failureReason)
                .build();
    }
}
