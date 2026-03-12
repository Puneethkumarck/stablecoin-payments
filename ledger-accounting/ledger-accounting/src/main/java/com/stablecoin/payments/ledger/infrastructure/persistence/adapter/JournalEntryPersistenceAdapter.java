package com.stablecoin.payments.ledger.infrastructure.persistence.adapter;

import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.domain.port.JournalEntryRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.mapper.JournalEntryPersistenceMapper;
import com.stablecoin.payments.ledger.infrastructure.persistence.repository.JournalEntryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JournalEntryPersistenceAdapter implements JournalEntryRepository {

    private final JournalEntryJpaRepository jpa;
    private final JournalEntryPersistenceMapper mapper;

    @Override
    public JournalEntry save(JournalEntry entry) {
        return mapper.toDomain(jpa.save(mapper.toEntity(entry)));
    }

    @Override
    public List<JournalEntry> saveAll(List<JournalEntry> entries) {
        var entities = entries.stream()
                .map(mapper::toEntity)
                .toList();
        return jpa.saveAll(entities).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<JournalEntry> findByPaymentId(UUID paymentId) {
        return jpa.findByPaymentIdOrderBySequenceNoAsc(paymentId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<JournalEntry> findByTransactionId(UUID transactionId) {
        return jpa.findByTransactionIdOrderBySequenceNoAsc(transactionId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<JournalEntry> findByAccountCodeAndCurrency(String accountCode, String currency) {
        return jpa.findByAccountCodeAndCurrencyOrderByCreatedAtDesc(accountCode, currency).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<JournalEntry> findByAccountCodeAndCurrency(String accountCode, String currency,
                                                            int offset, int limit) {
        int page = offset / Math.max(limit, 1);
        return jpa.findByAccountCodeAndCurrencyOrderByCreatedAtDesc(
                        accountCode, currency, PageRequest.of(page, limit))
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByAccountCodeAndCurrency(String accountCode, String currency) {
        return jpa.countByAccountCodeAndCurrency(accountCode, currency);
    }

    @Override
    public int countByPaymentId(UUID paymentId) {
        return jpa.countByPaymentId(paymentId);
    }
}
