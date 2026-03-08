package com.stablecoin.payments.compliance.application.service;

import com.stablecoin.payments.compliance.api.request.InitiateComplianceCheckRequest;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.compliance.api.response.CustomerRiskProfileResponse;
import com.stablecoin.payments.compliance.application.mapper.ComplianceCheckResponseMapper;
import com.stablecoin.payments.compliance.domain.model.Money;
import com.stablecoin.payments.compliance.domain.service.ComplianceCheckCommandHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aPendingCheck;
import static com.stablecoin.payments.compliance.fixtures.CustomerRiskProfileFixtures.aRiskProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ComplianceCheckApplicationServiceTest {

    @Mock private ComplianceCheckCommandHandler commandHandler;
    @Mock private ComplianceCheckResponseMapper responseMapper;

    @InjectMocks
    private ComplianceCheckApplicationService service;

    @Test
    @DisplayName("initiateCheck should delegate to commandHandler and map response")
    void initiateCheckShouldDelegateAndMap() {
        var request = new InitiateComplianceCheckRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("1000.00"), "USD", "US", "DE", "EUR");
        var domainCheck = aPendingCheck();
        var expectedResponse = new ComplianceCheckResponse(
                domainCheck.checkId(), domainCheck.paymentId(), "PENDING",
                null, null, null, null, null, null, null,
                domainCheck.createdAt(), null);

        given(commandHandler.initiateCheck(
                request.paymentId(), request.senderId(), request.recipientId(),
                new Money(request.amount(), request.currency()),
                request.sourceCountry(), request.targetCountry(), request.targetCurrency()))
                .willReturn(domainCheck);
        given(responseMapper.toResponse(domainCheck)).willReturn(expectedResponse);

        var result = service.initiateCheck(request);

        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("getCheck should delegate to commandHandler and map response")
    void getCheckShouldDelegateAndMap() {
        var checkId = UUID.randomUUID();
        var domainCheck = aPendingCheck();
        var expectedResponse = new ComplianceCheckResponse(
                domainCheck.checkId(), domainCheck.paymentId(), "PENDING",
                null, null, null, null, null, null, null,
                domainCheck.createdAt(), null);

        given(commandHandler.getCheck(checkId)).willReturn(domainCheck);
        given(responseMapper.toResponse(domainCheck)).willReturn(expectedResponse);

        var result = service.getCheck(checkId);

        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expectedResponse);
        then(commandHandler).should().getCheck(checkId);
    }

    @Test
    @DisplayName("getCustomerRiskProfile should delegate to commandHandler and map response")
    void getCustomerRiskProfileShouldDelegateAndMap() {
        var customerId = UUID.randomUUID();
        var profile = aRiskProfile().toBuilder().customerId(customerId).build();
        var expectedResponse = new CustomerRiskProfileResponse(
                customerId, "KYC_TIER_2", profile.kycVerifiedAt(),
                "LOW", 20,
                profile.perTxnLimitUsd(), profile.dailyLimitUsd(),
                profile.monthlyLimitUsd(), profile.lastScoredAt());

        given(commandHandler.getCustomerRiskProfile(customerId)).willReturn(profile);
        given(responseMapper.toResponse(profile)).willReturn(expectedResponse);

        var result = service.getCustomerRiskProfile(customerId);

        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expectedResponse);
        then(commandHandler).should().getCustomerRiskProfile(customerId);
    }
}
