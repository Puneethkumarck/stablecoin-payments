package com.stablecoin.payments.merchant.onboarding.application.config;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.CorridorEntitlementService;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.MerchantActivationPolicy;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.RiskTierCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public RiskTierCalculator riskTierCalculator() {
        return new RiskTierCalculator();
    }

    @Bean
    public MerchantActivationPolicy merchantActivationPolicy() {
        return new MerchantActivationPolicy();
    }

    @Bean
    public CorridorEntitlementService corridorEntitlementService() {
        return new CorridorEntitlementService();
    }
}
