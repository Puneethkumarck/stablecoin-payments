package com.stablecoin.payments.onramp.domain.model;

public record PaymentRail(PaymentRailType rail, String country, String currency) {

    public PaymentRail {
        if (rail == null) {
            throw new IllegalArgumentException("Rail is required");
        }
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }
}
