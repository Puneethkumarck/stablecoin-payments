package com.stablecoin.payments.compliance.domain.port;

import com.stablecoin.payments.compliance.domain.model.SanctionsResult;

import java.util.UUID;

public interface SanctionsProvider {
    SanctionsResult screen(UUID senderId, UUID recipientId);
}
