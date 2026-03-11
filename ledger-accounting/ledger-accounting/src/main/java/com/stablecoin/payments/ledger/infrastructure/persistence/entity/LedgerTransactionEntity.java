package com.stablecoin.payments.ledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, append-only entity — no @Version, no update methods.
 */
@Entity
@Table(name = "ledger_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransactionEntity {

    @Id
    @Column(name = "transaction_id", updatable = false)
    private UUID transactionId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "source_event", length = 100, nullable = false, updatable = false)
    private String sourceEvent;

    @Column(name = "source_event_id", nullable = false, updatable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
