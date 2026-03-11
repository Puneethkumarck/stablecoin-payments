package com.stablecoin.payments.ledger.infrastructure.persistence.repository;

import com.stablecoin.payments.ledger.infrastructure.persistence.entity.JournalEntryEntity;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.JournalEntryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalEntryJpaRepository extends JpaRepository<JournalEntryEntity, JournalEntryId> {

    List<JournalEntryEntity> findByPaymentIdOrderBySequenceNoAsc(UUID paymentId);

    List<JournalEntryEntity> findByTransactionIdOrderBySequenceNoAsc(UUID transactionId);

    List<JournalEntryEntity> findByAccountCodeAndCurrencyOrderByCreatedAtDesc(String accountCode, String currency);

    int countByPaymentId(UUID paymentId);
}
