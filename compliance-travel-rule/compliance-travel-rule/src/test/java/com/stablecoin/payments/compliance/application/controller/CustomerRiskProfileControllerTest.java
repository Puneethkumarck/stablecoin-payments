package com.stablecoin.payments.compliance.application.controller;

import com.stablecoin.payments.compliance.api.response.CustomerRiskProfileResponse;
import com.stablecoin.payments.compliance.application.service.ComplianceCheckApplicationService;
import com.stablecoin.payments.compliance.domain.exception.CustomerNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerRiskProfileController")
class CustomerRiskProfileControllerTest {

    @Mock
    private ComplianceCheckApplicationService applicationService;

    @InjectMocks
    private CustomerRiskProfileController controller;

    @Test
    @DisplayName("should delegate to application service and return risk profile")
    void shouldGetRiskProfile() {
        // given
        var customerId = UUID.randomUUID();
        var now = Instant.now();
        var expectedResponse = new CustomerRiskProfileResponse(
                customerId, "KYC_TIER_2", now, "LOW", 20,
                new BigDecimal("10000.00"), new BigDecimal("50000.00"),
                new BigDecimal("500000.00"), now);

        given(applicationService.getCustomerRiskProfile(customerId)).willReturn(expectedResponse);

        // when
        var result = controller.getRiskProfile(customerId);

        // then
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("should propagate CustomerNotFoundException")
    void shouldPropagateOnNotFound() {
        // given
        var customerId = UUID.randomUUID();
        given(applicationService.getCustomerRiskProfile(customerId))
                .willThrow(new CustomerNotFoundException(customerId));

        // when/then
        assertThatThrownBy(() -> controller.getRiskProfile(customerId))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining(customerId.toString());
    }
}
