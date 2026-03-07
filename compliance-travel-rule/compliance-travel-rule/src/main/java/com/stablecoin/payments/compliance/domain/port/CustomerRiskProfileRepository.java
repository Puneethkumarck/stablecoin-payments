package com.stablecoin.payments.compliance.domain.port;

import com.stablecoin.payments.compliance.domain.model.CustomerRiskProfile;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRiskProfileRepository {
    CustomerRiskProfile save(CustomerRiskProfile profile);
    Optional<CustomerRiskProfile> findByCustomerId(UUID customerId);
}
