package com.stablecoin.payments.ledger.domain.port;

import com.stablecoin.payments.ledger.domain.model.JournalEntry;

import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository {

    JournalEntry save(JournalEntry entry);

    List<JournalEntry> saveAll(List<JournalEntry> entries);

    List<JournalEntry> findByPaymentId(UUID paymentId);

    List<JournalEntry> findByTransactionId(UUID transactionId);

    List<JournalEntry> findByAccountCodeAndCurrency(String accountCode, String currency);

    List<JournalEntry> findByAccountCodeAndCurrency(String accountCode, String currency, int offset, int limit);

    long countByAccountCodeAndCurrency(String accountCode, String currency);

    int countByPaymentId(UUID paymentId);
}
