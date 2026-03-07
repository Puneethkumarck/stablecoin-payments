package com.stablecoin.payments.compliance.domain.port;

import com.stablecoin.payments.compliance.domain.model.AmlResult;

import java.util.UUID;

public interface AmlProvider {
    AmlResult analyze(UUID senderId, UUID recipientId);
}
