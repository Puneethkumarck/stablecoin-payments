package com.stablecoin.payments.compliance.domain.port;

import com.stablecoin.payments.compliance.domain.model.TravelRulePackage;

public interface TravelRuleProvider {
    String transmit(TravelRulePackage travelRulePackage);
}
