package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record Address(
        String street,
        String city,
        String state,
        String postalCode,
        String country
) {}
