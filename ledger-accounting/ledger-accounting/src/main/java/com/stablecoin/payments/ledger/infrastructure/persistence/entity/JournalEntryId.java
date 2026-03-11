package com.stablecoin.payments.ledger.infrastructure.persistence.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class JournalEntryId implements Serializable {

    private UUID entryId;
    private Instant createdAt;

    public JournalEntryId() {
    }

    public JournalEntryId(UUID entryId, Instant createdAt) {
        this.entryId = entryId;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JournalEntryId that = (JournalEntryId) o;
        return Objects.equals(entryId, that.entryId) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryId, createdAt);
    }
}
