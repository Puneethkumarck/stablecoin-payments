package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record TravelRulePackage(
        UUID packageId,
        UUID checkId,
        VaspInfo originatorVasp,
        VaspInfo beneficiaryVasp,
        String originatorData,
        String beneficiaryData,
        TravelRuleProtocol protocol,
        TransmissionStatus transmissionStatus,
        Instant transmittedAt,
        String protocolRef
) {}
