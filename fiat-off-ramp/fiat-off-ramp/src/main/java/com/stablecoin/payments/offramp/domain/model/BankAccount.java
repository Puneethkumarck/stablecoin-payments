package com.stablecoin.payments.offramp.domain.model;

public record BankAccount(
        String accountNumber,
        String bankCode,
        AccountType accountType,
        String country
) {

    public BankAccount {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number is required");
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
