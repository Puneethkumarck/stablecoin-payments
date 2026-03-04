package com.stablecoin.payments.gateway.iam;

import com.jayway.jsonpath.JsonPath;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.ApiKeyJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("business")
@DisplayName("API Key Lifecycle Flow")
class ApiKeyLifecycleFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyJpaRepository apiKeyRepository;

    @Autowired
    private com.stablecoin.payments.gateway.iam.infrastructure.messaging.MerchantEventListener merchantEventListener;

    @Test
    @DisplayName("should create API key for active merchant, then revoke it")
    void shouldCreateAndRevokeApiKey() throws Exception {
        var externalId = UUID.randomUUID();

        // Step 1: Register and activate merchant
        var registerResult = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                    "externalId": "%s",
                                    "name": "Key Test Corp",
                                    "country": "US",
                                    "scopes": ["payments:read", "payments:write"]
                                }
                                """.formatted(externalId)))
                .andExpect(status().isCreated())
                .andReturn();

        var merchantId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.merchantId").toString();

        // Activate via Kafka event
        merchantEventListener.onMerchantActivated("""
                {"merchantId": "%s", "companyName": "Key Test Corp", "country": "US", "scopes": ["payments:read", "payments:write"]}
                """.formatted(externalId));

        // Step 2: Create API key
        var createKeyResult = mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                    "merchantId": "%s",
                                    "name": "Production Key",
                                    "environment": "LIVE",
                                    "scopes": ["payments:read"],
                                    "expiresInSeconds": 86400
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawKey", notNullValue()))
                .andExpect(jsonPath("$.keyId", notNullValue()))
                .andExpect(jsonPath("$.name", is("Production Key")))
                .andExpect(jsonPath("$.environment", is("LIVE")))
                .andReturn();

        var keyId = JsonPath.read(createKeyResult.getResponse().getContentAsString(), "$.keyId").toString();
        var rawKey = JsonPath.read(createKeyResult.getResponse().getContentAsString(), "$.rawKey").toString();

        // Verify key exists in DB
        assertThat(rawKey).startsWith("pk_live_");
        var savedKey = apiKeyRepository.findById(UUID.fromString(keyId));
        assertThat(savedKey).isPresent();
        assertThat(savedKey.get().isActive()).isTrue();

        // Step 3: Revoke API key
        mockMvc.perform(delete("/v1/api-keys/" + keyId)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());

        // Step 4: Verify key is revoked in DB
        var revokedKey = apiKeyRepository.findById(UUID.fromString(keyId));
        assertThat(revokedKey).isPresent();
        assertThat(revokedKey.get().isActive()).isFalse();
        assertThat(revokedKey.get().getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("should reject API key creation for pending merchant")
    void shouldRejectKeyForPendingMerchant() throws Exception {
        var externalId = UUID.randomUUID();

        // Register merchant (stays PENDING)
        var registerResult = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                    "externalId": "%s",
                                    "name": "Pending Key Corp",
                                    "country": "DE",
                                    "scopes": ["payments:read"]
                                }
                                """.formatted(externalId)))
                .andExpect(status().isCreated())
                .andReturn();

        var merchantId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.merchantId").toString();

        // API key creation should fail — merchant not active
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                    "merchantId": "%s",
                                    "name": "Blocked Key",
                                    "environment": "LIVE",
                                    "scopes": ["payments:read"]
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should create multiple API keys with different environments")
    void shouldCreateMultipleKeys() throws Exception {
        var externalId = UUID.randomUUID();

        // Register and activate merchant
        var registerResult = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                    "externalId": "%s",
                                    "name": "Multi Key Corp",
                                    "country": "US",
                                    "scopes": ["payments:read", "payments:write"]
                                }
                                """.formatted(externalId)))
                .andExpect(status().isCreated())
                .andReturn();

        var merchantId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.merchantId").toString();

        merchantEventListener.onMerchantActivated("""
                {"merchantId": "%s", "companyName": "Multi Key Corp", "country": "US", "scopes": ["payments:read", "payments:write"]}
                """.formatted(externalId));

        // Create LIVE key
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                    "merchantId": "%s",
                                    "name": "Live Key",
                                    "environment": "LIVE",
                                    "scopes": ["payments:read"]
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.environment", is("LIVE")));

        // Create TEST key
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                    "merchantId": "%s",
                                    "name": "Test Key",
                                    "environment": "TEST",
                                    "scopes": ["payments:read", "payments:write"]
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.environment", is("TEST")));

        // Verify both keys in DB
        var activeKeys = apiKeyRepository.findByMerchantIdAndActiveTrue(UUID.fromString(merchantId));
        assertThat(activeKeys).hasSize(2);
    }

    @Test
    @DisplayName("should return 404 when revoking non-existent key")
    void shouldReturn404ForNonExistentKey() throws Exception {
        mockMvc.perform(delete("/v1/api-keys/" + UUID.randomUUID())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }
}
