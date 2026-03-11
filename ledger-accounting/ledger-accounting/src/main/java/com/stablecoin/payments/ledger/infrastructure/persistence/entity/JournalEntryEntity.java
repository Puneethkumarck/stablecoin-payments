package com.stablecoin.payments.ledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, append-only entity — no @Version, no update methods.
 * Partitioned by created_at (composite PK: entry_id + created_at).
 */
@Entity
@Table(name = "journal_entries")
@IdClass(JournalEntryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntryEntity {

    @Id
    @Column(name = "entry_id", updatable = false)
    private UUID entryId;

    @Id
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Column(name = "entry_type", length = 6, nullable = false, updatable = false)
    private String entryType;

    @Column(name = "account_code", length = 10, nullable = false, updatable = false)
    private String accountCode;

    @Column(name = "amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "currency", length = 10, nullable = false, updatable = false)
    private String currency;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal balanceAfter;

    @Column(name = "account_version", nullable = false, updatable = false)
    private long accountVersion;

    @Column(name = "source_event", length = 100, nullable = false, updatable = false)
    private String sourceEvent;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;
}
