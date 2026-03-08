package com.stablecoin.payments.onramp.domain.model;

public record BankAccount(
        String accountNumberHash,
        String bankCode,
        AccountType accountType,
        String country
) {

    public BankAccount {
        if (accountNumberHash == null || accountNumberHash.isBlank()) {
            throw new IllegalArgumentException("Account number hash is required");
        }
        if (bankCode == null || bankCode.isBlank()) {
            throw new IllegalArgumentException("Bank code is required");
        }
        if (accountType == null) {
            throw new IllegalArgumentException("Account type is required");
        }
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country is required");
        }
    }
}
