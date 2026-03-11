package com.stablecoin.payments.ledger.infrastructure.persistence.adapter;

import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.mapper.JournalEntryPersistenceMapper;
import com.stablecoin.payments.ledger.infrastructure.persistence.mapper.LedgerTransactionPersistenceMapper;
import com.stablecoin.payments.ledger.infrastructure.persistence.repository.JournalEntryJpaRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.repository.LedgerTransactionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LedgerTransactionPersistenceAdapter implements LedgerTransactionRepository {

    private final LedgerTransactionJpaRepository transactionJpa;
    private final JournalEntryJpaRepository entryJpa;
    private final LedgerTransactionPersistenceMapper transactionMapper;
    private final JournalEntryPersistenceMapper entryMapper;

    @Override
    public LedgerTransaction save(LedgerTransaction transaction) {
        var savedEntity = transactionJpa.save(transactionMapper.toEntity(transaction));
        var entryEntities = transaction.entries().stream()
                .map(entryMapper::toEntity)
                .toList();
        var savedEntries = entryJpa.saveAll(entryEntities);
        var domainEntries = savedEntries.stream()
                .map(entryMapper::toDomain)
                .toList();
        return transactionMapper.toDomain(savedEntity, domainEntries);
    }

    @Override
    public Optional<LedgerTransaction> findById(UUID transactionId) {
        return transactionJpa.findById(transactionId)
                .map(entity -> {
                    List<JournalEntry> entries = entryJpa
                            .findByTransactionIdOrderBySequenceNoAsc(transactionId)
                            .stream()
                            .map(entryMapper::toDomain)
                            .toList();
                    return transactionMapper.toDomain(entity, entries);
                });
    }

    @Override
    public List<LedgerTransaction> findByPaymentId(UUID paymentId) {
        return transactionJpa.findByPaymentIdOrderByCreatedAtAsc(paymentId).stream()
                .map(entity -> {
                    List<JournalEntry> entries = entryJpa
                            .findByTransactionIdOrderBySequenceNoAsc(entity.getTransactionId())
                            .stream()
                            .map(entryMapper::toDomain)
                            .toList();
                    return transactionMapper.toDomain(entity, entries);
                })
                .toList();
    }

    @Override
    public boolean existsBySourceEventId(UUID sourceEventId) {
        return transactionJpa.existsBySourceEventId(sourceEventId);
    }
}
