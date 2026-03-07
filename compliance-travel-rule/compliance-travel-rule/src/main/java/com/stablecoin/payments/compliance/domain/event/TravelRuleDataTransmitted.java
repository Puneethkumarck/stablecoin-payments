package com.stablecoin.payments.compliance.domain.event;

import java.time.Instant;
import java.util.UUID;

public record TravelRuleDataTransmitted(
        UUID checkId,
        UUID paymentId,
        UUID packageId,
        String protocol,
        String protocolRef,
        Instant transmittedAt
) {
    public static final String TOPIC = "compliance.travel-rule.transmitted";
}
