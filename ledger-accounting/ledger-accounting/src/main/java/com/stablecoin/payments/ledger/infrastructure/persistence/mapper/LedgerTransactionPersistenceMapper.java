package com.stablecoin.payments.ledger.infrastructure.persistence.mapper;

import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.LedgerTransactionEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface LedgerTransactionPersistenceMapper {

    default LedgerTransactionEntity toEntity(LedgerTransaction transaction) {
        if (transaction == null) return null;
        return LedgerTransactionEntity.builder()
                .transactionId(transaction.transactionId())
                .paymentId(transaction.paymentId())
                .correlationId(transaction.correlationId())
                .sourceEvent(transaction.sourceEvent())
                .sourceEventId(transaction.sourceEventId())
                .description(transaction.description())
                .createdAt(transaction.createdAt())
                .build();
    }

    default LedgerTransaction toDomain(LedgerTransactionEntity entity, List<JournalEntry> entries) {
        if (entity == null) return null;
        return new LedgerTransaction(
                entity.getTransactionId(),
                entity.getPaymentId(),
                entity.getCorrelationId(),
                entity.getSourceEvent(),
                entity.getSourceEventId(),
                entity.getDescription(),
                entries,
                entity.getCreatedAt()
        );
    }
}
