package com.stablecoin.payments.compliance.domain.port;

import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;

import java.util.Optional;
import java.util.UUID;

public interface ComplianceCheckRepository {
    ComplianceCheck save(ComplianceCheck check);
    Optional<ComplianceCheck> findById(UUID checkId);
    Optional<ComplianceCheck> findByPaymentId(UUID paymentId);
}
