package com.stablecoin.payments.compliance.domain.port;

import com.stablecoin.payments.compliance.domain.model.KycResult;

import java.util.UUID;

public interface KycProvider {
    KycResult verify(UUID senderId, UUID recipientId);
}
