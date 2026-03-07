package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record BeneficiaryInfo(
        UUID customerId,
        String fullName,
        String accountNumber,
        Address address
) {}
