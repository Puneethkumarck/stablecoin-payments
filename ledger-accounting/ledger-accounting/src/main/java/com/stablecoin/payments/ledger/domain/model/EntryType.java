package com.stablecoin.payments.ledger.domain.model;

public enum EntryType {
    DEBIT,
    CREDIT;

    public EntryType opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
