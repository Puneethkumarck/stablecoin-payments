package com.stablecoin.payments.compliance.application.controller;

import com.stablecoin.payments.compliance.api.request.InitiateComplianceCheckRequest;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.KycResultResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.RiskScoreResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.SanctionsResultResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.TravelRuleResponse;
import com.stablecoin.payments.compliance.application.service.ComplianceCheckApplicationService;
import com.stablecoin.payments.compliance.domain.exception.CheckNotFoundException;
import com.stablecoin.payments.compliance.domain.exception.DuplicatePaymentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ComplianceCheckController")
class ComplianceCheckControllerTest {

    @Mock
    private ComplianceCheckApplicationService applicationService;

    @InjectMocks
    private ComplianceCheckController controller;

    @Nested
    @DisplayName("POST /v1/compliance/check")
    class InitiateCheck {

        @Test
        @DisplayName("should delegate to application service and return response")
        void shouldInitiateCheck() {
            // given
            var request = new InitiateComplianceCheckRequest(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new BigDecimal("1000.00"), "USD", "US", "DE", "EUR");

            var checkId = UUID.randomUUID();
            var now = Instant.now();
            var expectedResponse = new ComplianceCheckResponse(
                    checkId, request.paymentId(), "PASSED", "PASSED",
                    new RiskScoreResponse(18, "LOW", List.of("ESTABLISHED_CUSTOMER")),
                    new KycResultResponse("VERIFIED", "VERIFIED", "KYC_TIER_2"),
                    new SanctionsResultResponse(false, false, List.of("OFAC", "EU", "UN")),
                    new TravelRuleResponse("IVMS101", "TRANSMITTED"),
                    null, null, now, now);

            given(applicationService.initiateCheck(request)).willReturn(expectedResponse);

            // when
            var result = controller.initiateCheck(request);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("should propagate DuplicatePaymentException")
        void shouldPropagateOnDuplicate() {
            // given
            var paymentId = UUID.randomUUID();
            var request = new InitiateComplianceCheckRequest(
                    paymentId, UUID.randomUUID(), UUID.randomUUID(),
                    new BigDecimal("1000.00"), "USD", "US", "DE", "EUR");

            given(applicationService.initiateCheck(request))
                    .willThrow(new DuplicatePaymentException(paymentId));

            // when/then
            assertThatThrownBy(() -> controller.initiateCheck(request))
                    .isInstanceOf(DuplicatePaymentException.class)
                    .hasMessageContaining(paymentId.toString());
        }
    }

    @Nested
    @DisplayName("GET /v1/compliance/checks/{checkId}")
    class GetCheck {

        @Test
        @DisplayName("should delegate to application service and return response")
        void shouldGetCheck() {
            // given
            var checkId = UUID.randomUUID();
            var paymentId = UUID.randomUUID();
            var now = Instant.now();
            var expectedResponse = new ComplianceCheckResponse(
                    checkId, paymentId, "PENDING", null,
                    null, null, null, null,
                    null, null, now, null);

            given(applicationService.getCheck(checkId)).willReturn(expectedResponse);

            // when
            var result = controller.getCheck(checkId);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("should propagate CheckNotFoundException")
        void shouldPropagateOnNotFound() {
            // given
            var checkId = UUID.randomUUID();
            given(applicationService.getCheck(checkId))
                    .willThrow(new CheckNotFoundException(checkId));

            // when/then
            assertThatThrownBy(() -> controller.getCheck(checkId))
                    .isInstanceOf(CheckNotFoundException.class)
                    .hasMessageContaining(checkId.toString());
        }
    }
}
