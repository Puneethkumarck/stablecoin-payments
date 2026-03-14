package com.stablecoin.payments.compliance.infrastructure.config;

import com.stablecoin.payments.compliance.domain.model.AmlResult;
import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.SanctionsResult;
import com.stablecoin.payments.compliance.domain.port.AmlProvider;
import com.stablecoin.payments.compliance.domain.port.KycProvider;
import com.stablecoin.payments.compliance.domain.port.SanctionsProvider;
import com.stablecoin.payments.compliance.domain.port.TravelRuleProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "true")
public class FallbackAdaptersConfig {

    @Bean
    public KycProvider fallbackKycProvider() {
        log.warn("Using fallback KYC provider — all verifications will return VERIFIED");
        return (senderId, recipientId) -> KycResult.builder()
                .kycResultId(UUID.randomUUID())
                .senderKycTier(KycTier.KYC_TIER_2)
                .senderStatus(KycStatus.VERIFIED)
                .recipientStatus(KycStatus.VERIFIED)
                .provider("fallback")
                .providerRef("fallback-" + UUID.randomUUID())
                .checkedAt(Instant.now())
                .build();
    }

    @Bean
    public SanctionsProvider fallbackSanctionsProvider() {
        log.warn("Using fallback sanctions provider — no hits will be returned");
        return (senderId, recipientId) -> SanctionsResult.builder()
                .sanctionsResultId(UUID.randomUUID())
                .senderScreened(true)
                .recipientScreened(true)
                .senderHit(false)
                .recipientHit(false)
                .listsChecked(List.of("OFAC_SDN", "EU_CONSOLIDATED", "UN"))
                .provider("fallback")
                .providerRef("fallback-" + UUID.randomUUID())
                .screenedAt(Instant.now())
                .build();
    }

    @Bean
    public AmlProvider fallbackAmlProvider() {
        log.warn("Using fallback AML provider — no flags will be returned");
        return (senderId, recipientId) -> AmlResult.builder()
                .amlResultId(UUID.randomUUID())
                .flagged(false)
                .flagReasons(List.of())
                .provider("fallback")
                .screenedAt(Instant.now())
                .build();
    }

    @Bean
    public TravelRuleProvider fallbackTravelRuleProvider() {
        log.warn("Using fallback travel rule provider — transmissions will be simulated");
        return travelRulePackage -> "fallback-tr-ref-" + UUID.randomUUID();
    }
}
