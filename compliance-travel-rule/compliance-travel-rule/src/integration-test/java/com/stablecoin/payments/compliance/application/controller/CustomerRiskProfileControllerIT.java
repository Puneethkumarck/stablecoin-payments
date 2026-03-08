package com.stablecoin.payments.compliance.application.controller;

import com.stablecoin.payments.compliance.AbstractIntegrationTest;
import com.stablecoin.payments.compliance.domain.model.CustomerRiskProfile;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.RiskBand;
import com.stablecoin.payments.compliance.domain.port.CustomerRiskProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CustomerRiskProfileController IT")
class CustomerRiskProfileControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRiskProfileRepository profileRepository;

    @Test
    @DisplayName("should return 200 OK with risk profile for existing customer")
    void shouldReturn200ForExistingCustomer() throws Exception {
        var customerId = UUID.randomUUID();
        var now = Instant.now();
        var profile = CustomerRiskProfile.builder()
                .customerId(customerId)
                .kycTier(KycTier.KYC_TIER_2)
                .kycVerifiedAt(now)
                .riskBand(RiskBand.LOW)
                .riskScore(20)
                .perTxnLimitUsd(new BigDecimal("10000.00"))
                .dailyLimitUsd(new BigDecimal("50000.00"))
                .monthlyLimitUsd(new BigDecimal("500000.00"))
                .lastScoredAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        profileRepository.save(profile);

        mockMvc.perform(get("/v1/customers/{customerId}/risk-profile", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(customerId.toString())))
                .andExpect(jsonPath("$.kycTier", is("KYC_TIER_2")))
                .andExpect(jsonPath("$.riskBand", is("LOW")))
                .andExpect(jsonPath("$.riskScore", is(20)))
                .andExpect(jsonPath("$.perTxnLimitUsd", notNullValue()))
                .andExpect(jsonPath("$.dailyLimitUsd", notNullValue()))
                .andExpect(jsonPath("$.monthlyLimitUsd", notNullValue()));
    }

    @Test
    @DisplayName("should return 404 Not Found for non-existing customer")
    void shouldReturn404ForNonExistingCustomer() throws Exception {
        var customerId = UUID.randomUUID();

        mockMvc.perform(get("/v1/customers/{customerId}/risk-profile", customerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CO-2001")))
                .andExpect(jsonPath("$.status", is("Not Found")))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("should return 400 Bad Request for invalid UUID format")
    void shouldReturn400ForInvalidUuid() throws Exception {
        mockMvc.perform(get("/v1/customers/{customerId}/risk-profile", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
