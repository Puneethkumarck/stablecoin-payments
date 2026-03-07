package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record OriginatorInfo(
        UUID customerId,
        String fullName,
        String accountNumber,
        Address address,
        String nationalId,
        String dateOfBirth
) {}
