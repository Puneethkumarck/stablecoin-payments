package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record VaspInfo(
        String vaspId,
        String name,
        String country,
        String did
) {}
