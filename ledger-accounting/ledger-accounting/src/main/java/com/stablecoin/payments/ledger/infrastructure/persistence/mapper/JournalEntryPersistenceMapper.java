package com.stablecoin.payments.ledger.infrastructure.persistence.mapper;

import com.stablecoin.payments.ledger.domain.model.EntryType;
import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.JournalEntryEntity;
import org.mapstruct.Mapper;

@Mapper
public interface JournalEntryPersistenceMapper {

    default JournalEntryEntity toEntity(JournalEntry entry) {
        if (entry == null) return null;
        return JournalEntryEntity.builder()
                .entryId(entry.entryId())
                .transactionId(entry.transactionId())
                .paymentId(entry.paymentId())
                .correlationId(entry.correlationId())
                .sequenceNo(entry.sequenceNo())
                .entryType(entry.entryType().name())
                .accountCode(entry.accountCode())
                .amount(entry.amount())
                .currency(entry.currency())
                .balanceAfter(entry.balanceAfter())
                .accountVersion(entry.accountVersion())
                .sourceEvent(entry.sourceEvent())
                .sourceEventId(entry.sourceEventId())
                .createdAt(entry.createdAt())
                .build();
    }

    default JournalEntry toDomain(JournalEntryEntity entity) {
        if (entity == null) return null;
        return new JournalEntry(
                entity.getEntryId(),
                entity.getTransactionId(),
                entity.getPaymentId(),
                entity.getCorrelationId(),
                entity.getSequenceNo(),
                EntryType.valueOf(entity.getEntryType()),
                entity.getAccountCode(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getBalanceAfter(),
                entity.getAccountVersion(),
                entity.getSourceEvent(),
                entity.getSourceEventId(),
                entity.getCreatedAt()
        );
    }
}
