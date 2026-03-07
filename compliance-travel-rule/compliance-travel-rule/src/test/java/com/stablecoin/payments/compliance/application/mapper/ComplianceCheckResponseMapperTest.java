package com.stablecoin.payments.compliance.application.mapper;

import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aKycResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aPendingCheck;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aSanctionsClearResult;
import static com.stablecoin.payments.compliance.fixtures.CustomerRiskProfileFixtures.aRiskProfile;
import static org.assertj.core.api.Assertions.assertThat;

class ComplianceCheckResponseMapperTest {

    private final ComplianceCheckResponseMapper mapper = new ComplianceCheckResponseMapper();

    @Test
    @DisplayName("should map pending check with no sub-results")
    void shouldMapPendingCheck() {
        var check = aPendingCheck();

        var response = mapper.toResponse(check);

        assertThat(response.checkId()).isEqualTo(check.checkId());
        assertThat(response.paymentId()).isEqualTo(check.paymentId());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.overallResult()).isNull();
        assertThat(response.riskScore()).isNull();
        assertThat(response.kycResult()).isNull();
        assertThat(response.sanctionsResult()).isNull();
        assertThat(response.travelRule()).isNull();
    }

    @Test
    @DisplayName("should map check with KYC and sanctions results")
    void shouldMapCheckWithSubResults() {
        var check = aPendingCheck()
                .startKyc()
                .passKyc(aKycResult(null))
                .sanctionsClear(aSanctionsClearResult(null));

        var response = mapper.toResponse(check);

        assertThat(response.status()).isEqualTo("AML_SCREENING");
        assertThat(response.kycResult()).isNotNull();
        assertThat(response.kycResult().senderStatus()).isEqualTo("VERIFIED");
        assertThat(response.sanctionsResult()).isNotNull();
        assertThat(response.sanctionsResult().senderHit()).isFalse();
    }

    @Test
    @DisplayName("should map customer risk profile response")
    void shouldMapCustomerProfile() {
        var profile = aRiskProfile();

        var response = mapper.toResponse(profile);

        assertThat(response.customerId()).isEqualTo(profile.customerId());
        assertThat(response.kycTier()).isEqualTo("KYC_TIER_2");
        assertThat(response.riskBand()).isEqualTo("LOW");
        assertThat(response.riskScore()).isEqualTo(20);
    }
}
