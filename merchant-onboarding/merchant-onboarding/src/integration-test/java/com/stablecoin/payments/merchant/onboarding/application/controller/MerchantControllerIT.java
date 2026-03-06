package com.stablecoin.payments.merchant.onboarding.application.controller;

import com.stablecoin.payments.merchant.onboarding.AbstractIntegrationTest;
import com.stablecoin.payments.merchant.onboarding.fixtures.MerchantEntityFixtures;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.MerchantJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MerchantController IT")
class MerchantControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantJpaRepository merchantJpa;

    private static final String BASE_PATH = "/api/v1/merchants";

    @BeforeEach
    void cleanUp() {
        merchantJpa.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/v1/merchants")
    class Apply {

        @Test
        @DisplayName("should create merchant and return 201")
        @WithMockUser(authorities = "merchant:write")
        void shouldCreateMerchant() throws Exception {
            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {
                                        "legalName": "Test Corp",
                                        "tradingName": "TestCo",
                                        "registrationNumber": "REG-IT-001",
                                        "registrationCountry": "GB",
                                        "entityType": "PRIVATE_LIMITED",
                                        "websiteUrl": "https://testcorp.com",
                                        "primaryCurrency": "USD",
                                        "primaryContactEmail": "john@testcorp.com",
                                        "primaryContactName": "John Doe",
                                        "registeredAddress": {
                                            "streetLine1": "1 Test Street",
                                            "city": "London",
                                            "postcode": "EC1A 1BB",
                                            "country": "GB"
                                        },
                                        "beneficialOwners": [{
                                            "fullName": "John Doe",
                                            "dateOfBirth": "1985-01-15",
                                            "nationality": "GB",
                                            "ownershipPct": 100.00,
                                            "isPoliticallyExposed": false
                                        }],
                                        "requestedCorridors": ["GB->US"]
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.merchantId", notNullValue()))
                    .andExpect(jsonPath("$.status", is("APPLIED")))
                    .andExpect(jsonPath("$.kybStatus", is("NOT_STARTED")))
                    .andExpect(jsonPath("$.legalName", is("Test Corp")));
        }

        @Test
        @DisplayName("should return 400 when required fields missing")
        @WithMockUser(authorities = "merchant:write")
        void shouldReturn400WhenFieldsMissing() throws Exception {
            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {
                                        "legalName": "",
                                        "registrationCountry": "GB"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 409 when duplicate registration")
        @WithMockUser(authorities = "merchant:write")
        void shouldReturn409WhenDuplicate() throws Exception {
            var existing = MerchantEntityFixtures.anAppliedMerchantEntity();
            existing.setRegistrationNumber("REG-DUP-001");
            merchantJpa.save(existing);

            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {
                                        "legalName": "Duplicate Corp",
                                        "tradingName": "DupCo",
                                        "registrationNumber": "REG-DUP-001",
                                        "registrationCountry": "GB",
                                        "entityType": "PRIVATE_LIMITED",
                                        "websiteUrl": "https://dup.com",
                                        "primaryCurrency": "USD",
                                        "primaryContactEmail": "jane@dup.com",
                                        "primaryContactName": "Jane Doe",
                                        "registeredAddress": {
                                            "streetLine1": "1 Street",
                                            "city": "London",
                                            "postcode": "EC1A 1BB",
                                            "country": "GB"
                                        },
                                        "beneficialOwners": [{
                                            "fullName": "Jane Doe",
                                            "dateOfBirth": "1985-01-15",
                                            "nationality": "GB",
                                            "ownershipPct": 100.00,
                                            "isPoliticallyExposed": false
                                        }],
                                        "requestedCorridors": ["GB->US"]
                                    }
                                    """))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/merchants/{merchantId}")
    class FindById {

        @Test
        @DisplayName("should return merchant details")
        @WithMockUser(authorities = "merchant:read")
        void shouldReturnMerchant() throws Exception {
            var entity = MerchantEntityFixtures.anActiveMerchantEntity();
            merchantJpa.save(entity);

            mockMvc.perform(get(BASE_PATH + "/" + entity.getMerchantId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.merchantId", is(entity.getMerchantId().toString())))
                    .andExpect(jsonPath("$.legalName", is("Active Corp Ltd")))
                    .andExpect(jsonPath("$.status", is("ACTIVE")));
        }

        @Test
        @DisplayName("should return 404 for non-existent merchant")
        @WithMockUser(authorities = "merchant:read")
        void shouldReturn404WhenNotFound() throws Exception {
            mockMvc.perform(get(BASE_PATH + "/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/merchants/{merchantId}/suspend")
    class Suspend {

        @Test
        @DisplayName("should suspend active merchant")
        @WithMockUser(authorities = "admin")
        void shouldSuspendMerchant() throws Exception {
            var entity = MerchantEntityFixtures.anActiveMerchantEntity();
            merchantJpa.save(entity);

            mockMvc.perform(post(BASE_PATH + "/" + entity.getMerchantId() + "/suspend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {"reason": "compliance review"}
                                    """))
                    .andExpect(status().isAccepted());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/merchants/{merchantId}/close")
    class Close {

        @Test
        @DisplayName("should close active merchant")
        @WithMockUser(authorities = "admin")
        void shouldCloseMerchant() throws Exception {
            var entity = MerchantEntityFixtures.anActiveMerchantEntity();
            merchantJpa.save(entity);

            mockMvc.perform(post(BASE_PATH + "/" + entity.getMerchantId() + "/close")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {"reason": "business closure"}
                                    """))
                    .andExpect(status().isAccepted());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/merchants/{merchantId}/rate-limit-tier")
    class UpdateRateLimitTier {

        @Test
        @DisplayName("should upgrade rate limit tier")
        @WithMockUser(authorities = "admin")
        void shouldUpgradeRateLimitTier() throws Exception {
            var entity = MerchantEntityFixtures.anActiveMerchantEntity();
            merchantJpa.save(entity);

            mockMvc.perform(patch(BASE_PATH + "/" + entity.getMerchantId() + "/rate-limit-tier")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {"newTier": "ENTERPRISE", "updatedBy": "00000000-0000-0000-0000-000000000001"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rateLimitTier", is("ENTERPRISE")));
        }
    }
}
