package com.stablecoin.payments.offramp.domain.model;

public record MobileMoneyAccount(
        MobileMoneyProvider provider,
        String phoneNumber,
        String country
) {

    public MobileMoneyAccount {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country is required");
        }
    }
}
