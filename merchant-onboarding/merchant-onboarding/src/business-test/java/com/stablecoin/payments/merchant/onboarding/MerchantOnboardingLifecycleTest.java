package com.stablecoin.payments.merchant.onboarding;

import com.jayway.jsonpath.JsonPath;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.MerchantJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("business")
@DisplayName("Merchant Onboarding Lifecycle")
class MerchantOnboardingLifecycleTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantJpaRepository merchantJpa;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("DELETE FROM onboarding_outbox_record");
        merchantJpa.deleteAll();
    }

    @Test
    @DisplayName("should complete full lifecycle: apply → suspend → close")
    @WithMockUser(authorities = {"merchant:write", "merchant:read", "admin"})
    void shouldCompleteFullLifecycle() throws Exception {
        // Step 1: Apply
        MvcResult applyResult = mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                    "legalName": "Lifecycle Corp",
                                    "tradingName": "LifeCo",
                                    "registrationNumber": "REG-LIFE-001",
                                    "registrationCountry": "GB",
                                    "entityType": "PRIVATE_LIMITED",
                                    "websiteUrl": "https://lifecycle.com",
                                    "primaryCurrency": "USD",
                                    "primaryContactEmail": "test@lifecycle.com",
                                    "primaryContactName": "Test Owner",
                                    "registeredAddress": {
                                        "streetLine1": "1 Life Street",
                                        "city": "London",
                                        "postcode": "EC1A 1BB",
                                        "country": "GB"
                                    },
                                    "beneficialOwners": [{
                                        "fullName": "Test Owner",
                                        "dateOfBirth": "1985-06-15",
                                        "nationality": "GB",
                                        "ownershipPct": 100.00,
                                        "isPoliticallyExposed": false
                                    }],
                                    "requestedCorridors": ["GB->US"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andReturn();

        var merchantId = JsonPath.read(applyResult.getResponse().getContentAsString(), "$.merchantId").toString();

        // Verify DB state after apply
        var appliedMerchant = merchantJpa.findById(java.util.UUID.fromString(merchantId)).orElseThrow();
        assertThat(appliedMerchant.getStatus()).isEqualTo(MerchantStatus.APPLIED);

        // Verify outbox event
        var outboxPayload = jdbc.queryForObject(
                "SELECT payload FROM onboarding_outbox_record LIMIT 1", String.class);
        assertThat(outboxPayload).contains("merchant.applied");

        // Step 2: Get merchant
        mockMvc.perform(get("/api/v1/merchants/" + merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andExpect(jsonPath("$.legalName", is("Lifecycle Corp")));

        // Step 3: Start KYB
        mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/kyb/start")
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted());

        var kybMerchant = merchantJpa.findById(java.util.UUID.fromString(merchantId)).orElseThrow();
        assertThat(kybMerchant.getStatus()).isEqualTo(MerchantStatus.KYB_IN_PROGRESS);

        // Note: Activation requires KYB to pass which needs the KYB provider callback.
        // For this lifecycle test, we verify the apply → kyb start → suspend → close path
        // by manually transitioning the entity to ACTIVE state.
        kybMerchant.setStatus(MerchantStatus.ACTIVE);
        kybMerchant.setKybStatus(com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus.PASSED);
        kybMerchant.setRiskTier(com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RiskTier.LOW);
        kybMerchant.setRateLimitTier(com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RateLimitTier.GROWTH);
        kybMerchant.setActivatedAt(java.time.Instant.now());
        merchantJpa.saveAndFlush(kybMerchant);

        // Step 4: Suspend
        mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"reason": "compliance review"}
                                """))
                .andExpect(status().isAccepted());

        var suspendedMerchant = merchantJpa.findById(java.util.UUID.fromString(merchantId)).orElseThrow();
        assertThat(suspendedMerchant.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);

        // Step 5: Reactivate
        mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/reactivate")
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted());

        var reactivatedMerchant = merchantJpa.findById(java.util.UUID.fromString(merchantId)).orElseThrow();
        assertThat(reactivatedMerchant.getStatus()).isEqualTo(MerchantStatus.ACTIVE);

        // Step 6: Close
        mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"reason": "business shutdown"}
                                """))
                .andExpect(status().isAccepted());

        var closedMerchant = merchantJpa.findById(java.util.UUID.fromString(merchantId)).orElseThrow();
        assertThat(closedMerchant.getStatus()).isEqualTo(MerchantStatus.CLOSED);
        assertThat(closedMerchant.getClosedAt()).isNotNull();

        // Verify outbox events: applied + suspended + reactivated + closed >= 3
        var outboxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM onboarding_outbox_record", Integer.class);
        assertThat(outboxCount).isGreaterThanOrEqualTo(3);
    }
}
