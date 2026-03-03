package com.stablecoin.payments.gateway.iam;

import com.jayway.jsonpath.JsonPath;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.OAuthClientEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.OAuthClientJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("business")
@DisplayName("Merchant Onboarding Flow")
class MerchantOnboardingFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthClientJpaRepository oauthClientRepository;

    @Autowired
    private com.stablecoin.payments.gateway.iam.infrastructure.messaging.MerchantEventListener merchantEventListener;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Test
    @DisplayName("should register merchant, activate via Kafka, create OAuth client, and issue token")
    void shouldCompleteOnboardingFlow() throws Exception {
        var externalId = UUID.randomUUID();

        // Step 1: Register merchant via API
        var registerResult = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "externalId": "%s",
                                    "name": "Onboarding Corp",
                                    "country": "US",
                                    "scopes": ["payments:read", "payments:write"]
                                }
                                """.formatted(externalId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Onboarding Corp")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.merchantId", notNullValue()))
                .andReturn();

        var merchantId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.merchantId").toString();

        // Step 2: Verify merchant is retrievable
        mockMvc.perform(get("/v1/merchants/" + merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Onboarding Corp")))
                .andExpect(jsonPath("$.status", is("PENDING")));

        // Step 3: Simulate Kafka merchant.activated event
        merchantEventListener.onMerchantActivated("""
                {"merchantId": "%s", "companyName": "Onboarding Corp", "country": "US", "scopes": ["payments:read", "payments:write"]}
                """.formatted(externalId));

        // Step 4: Verify merchant is now ACTIVE
        mockMvc.perform(get("/v1/merchants/" + merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        // Step 5: Create OAuth client directly in DB (admin operation)
        var clientId = UUID.randomUUID();
        var clientSecret = "test-secret-12345";
        var oauthClient = OAuthClientEntity.builder()
                .clientId(clientId)
                .merchantId(UUID.fromString(merchantId))
                .clientSecretHash(encoder.encode(clientSecret))
                .name("Onboarding Client")
                .scopes(new String[]{"payments:read", "payments:write"})
                .grantTypes(new String[]{"client_credentials"})
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        oauthClientRepository.saveAndFlush(oauthClient);

        // Step 6: Issue token via OAuth2 client_credentials
        var tokenResult = mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "grantType": "client_credentials",
                                    "clientId": "%s",
                                    "clientSecret": "%s",
                                    "scope": "payments:read"
                                }
                                """.formatted(clientId, clientSecret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.scope", is("payments:read")))
                .andReturn();

        var accessToken = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.accessToken").toString();
        assertThat(accessToken).isNotBlank();

        // Step 7: Verify JWKS endpoint is available
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should reject token issuance for pending merchant")
    void shouldRejectTokenForPendingMerchant() throws Exception {
        var externalId = UUID.randomUUID();

        // Register merchant (stays PENDING)
        var registerResult = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "externalId": "%s",
                                    "name": "Pending Corp",
                                    "country": "DE",
                                    "scopes": ["payments:read"]
                                }
                                """.formatted(externalId)))
                .andExpect(status().isCreated())
                .andReturn();

        var merchantId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.merchantId").toString();

        // Create OAuth client for pending merchant
        var clientId = UUID.randomUUID();
        var clientSecret = "test-secret-pending";
        var oauthClient = OAuthClientEntity.builder()
                .clientId(clientId)
                .merchantId(UUID.fromString(merchantId))
                .clientSecretHash(encoder.encode(clientSecret))
                .name("Pending Client")
                .scopes(new String[]{"payments:read"})
                .grantTypes(new String[]{"client_credentials"})
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        oauthClientRepository.saveAndFlush(oauthClient);

        // Token issuance should fail — merchant not active
        mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "grantType": "client_credentials",
                                    "clientId": "%s",
                                    "clientSecret": "%s",
                                    "scope": "payments:read"
                                }
                                """.formatted(clientId, clientSecret)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should reject token with invalid credentials")
    void shouldRejectInvalidCredentials() throws Exception {
        var clientId = UUID.randomUUID();

        mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "grantType": "client_credentials",
                                    "clientId": "%s",
                                    "clientSecret": "wrong-secret"
                                }
                                """.formatted(clientId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 404 for non-existent merchant")
    void shouldReturn404ForNonExistentMerchant() throws Exception {
        mockMvc.perform(get("/v1/merchants/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
