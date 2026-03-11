package com.stablecoin.payments.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.CHAIN_TRANSFERRED;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.FIAT_IN;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.FIAT_OUT;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.STABLECOIN_MINTED;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.STABLECOIN_REDEEMED;

/**
 * Per-payment reconciliation record tracking the 5-leg matching flow.
 * Required legs for RECONCILED status: FIAT_IN, STABLECOIN_MINTED, CHAIN_TRANSFERRED,
 * STABLECOIN_REDEEMED, FIAT_OUT. FX_RATE is metadata only.
 */
public record ReconciliationRecord(
        UUID recId,
        UUID paymentId,
        ReconciliationStatus status,
        BigDecimal tolerance,
        Instant reconciledAt,
        List<ReconciliationLeg> legs,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    private static final Set<ReconciliationLegType> REQUIRED_LEGS = Set.of(
            FIAT_IN, STABLECOIN_MINTED, CHAIN_TRANSFERRED, STABLECOIN_REDEEMED, FIAT_OUT
    );

    public ReconciliationRecord {
        Objects.requireNonNull(recId, "recId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(tolerance, "tolerance must not be null");
        Objects.requireNonNull(legs, "legs must not be null");
        legs = List.copyOf(legs);
    }

    /**
     * Creates a new PENDING reconciliation record for a payment.
     */
    public static ReconciliationRecord create(UUID paymentId, BigDecimal tolerance) {
        return new ReconciliationRecord(
                UUID.randomUUID(),
                paymentId,
                ReconciliationStatus.PENDING,
                tolerance,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                0L
        );
    }

    /**
     * Adds a reconciliation leg and updates status based on leg completeness.
     */
    public ReconciliationRecord addLeg(ReconciliationLeg leg) {
        Objects.requireNonNull(leg, "leg must not be null");

        List<ReconciliationLeg> updatedLegs = new ArrayList<>(this.legs);
        updatedLegs.add(leg);

        Set<ReconciliationLegType> presentTypes = updatedLegs.stream()
                .map(ReconciliationLeg::legType)
                .collect(Collectors.toSet());

        ReconciliationStatus newStatus;
        if (presentTypes.containsAll(REQUIRED_LEGS)) {
            newStatus = ReconciliationStatus.PARTIAL;
        } else if (presentTypes.stream().anyMatch(REQUIRED_LEGS::contains)) {
            newStatus = ReconciliationStatus.PARTIAL;
        } else {
            newStatus = this.status;
        }

        return new ReconciliationRecord(
                this.recId,
                this.paymentId,
                newStatus,
                this.tolerance,
                this.reconciledAt,
                updatedLegs,
                this.createdAt,
                Instant.now(),
                this.version
        );
    }

    /**
     * Finalizes reconciliation — checks all legs present and amounts within tolerance.
     * Returns RECONCILED or DISCREPANCY.
     */
    public ReconciliationRecord finalize(BigDecimal discrepancy) {
        Objects.requireNonNull(discrepancy, "discrepancy must not be null");

        Set<ReconciliationLegType> presentTypes = this.legs.stream()
                .map(ReconciliationLeg::legType)
                .collect(Collectors.toSet());

        boolean allLegsPresent = presentTypes.containsAll(REQUIRED_LEGS);
        boolean withinTolerance = discrepancy.abs().compareTo(this.tolerance) <= 0;

        ReconciliationStatus newStatus;
        Instant reconciledTime;

        if (allLegsPresent && withinTolerance) {
            newStatus = ReconciliationStatus.RECONCILED;
            reconciledTime = Instant.now();
        } else {
            newStatus = ReconciliationStatus.DISCREPANCY;
            reconciledTime = null;
        }

        return new ReconciliationRecord(
                this.recId,
                this.paymentId,
                newStatus,
                this.tolerance,
                reconciledTime,
                this.legs,
                this.createdAt,
                Instant.now(),
                this.version
        );
    }

    /**
     * Marks the reconciliation as DISCREPANCY (e.g., due to payment failure).
     */
    public ReconciliationRecord markDiscrepancy() {
        return new ReconciliationRecord(
                this.recId,
                this.paymentId,
                ReconciliationStatus.DISCREPANCY,
                this.tolerance,
                null,
                this.legs,
                this.createdAt,
                Instant.now(),
                this.version
        );
    }

    public boolean hasLeg(ReconciliationLegType legType) {
        return this.legs.stream().anyMatch(l -> l.legType() == legType);
    }

    public boolean hasAllRequiredLegs() {
        Set<ReconciliationLegType> presentTypes = this.legs.stream()
                .map(ReconciliationLeg::legType)
                .collect(Collectors.toSet());
        return presentTypes.containsAll(REQUIRED_LEGS);
    }
}
