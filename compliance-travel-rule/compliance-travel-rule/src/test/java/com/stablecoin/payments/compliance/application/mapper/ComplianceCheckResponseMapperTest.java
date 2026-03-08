package com.stablecoin.payments.compliance.application.mapper;

import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.KycResultResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.SanctionsResultResponse;
import com.stablecoin.payments.compliance.api.response.CustomerRiskProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

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

        var expected = new ComplianceCheckResponse(
                check.checkId(), check.paymentId(), "PENDING",
                null, null, null, null, null, null, null,
                check.createdAt(), null);

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should map check with KYC and sanctions results")
    void shouldMapCheckWithSubResults() {
        var check = aPendingCheck()
                .startKyc()
                .passKyc(aKycResult(null))
                .sanctionsClear(aSanctionsClearResult(null));

        var response = mapper.toResponse(check);

        var expected = new ComplianceCheckResponse(
                check.checkId(), check.paymentId(), "AML_SCREENING",
                null, null,
                new KycResultResponse("VERIFIED", "VERIFIED", "KYC_TIER_2"),
                new SanctionsResultResponse(false, false, List.of("OFAC", "EU", "UN")),
                null, null, null,
                check.createdAt(), null);

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should map customer risk profile response")
    void shouldMapCustomerProfile() {
        var profile = aRiskProfile();

        var response = mapper.toResponse(profile);

        var expected = new CustomerRiskProfileResponse(
                profile.customerId(), "KYC_TIER_2", profile.kycVerifiedAt(),
                "LOW", 20,
                profile.perTxnLimitUsd(), profile.dailyLimitUsd(),
                profile.monthlyLimitUsd(), profile.lastScoredAt());

        assertThat(response)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }
}
