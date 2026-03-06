package com.stablecoin.payments.gateway.iam;

import com.jayway.jsonpath.JsonPath;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.OAuthClientEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.AccessTokenJpaRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.OAuthClientJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("business")
@DisplayName("Merchant-Scoped Authorization Flow")
@TestPropertySource(properties = "app.security.enabled=true")
class MerchantScopedAuthorizationFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthClientJpaRepository oauthClientRepository;

    @Autowired
    private AccessTokenJpaRepository accessTokenRepository;

    @Autowired
    private com.stablecoin.payments.gateway.iam.infrastructure.messaging.MerchantEventListener merchantEventListener;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    private UUID merchantAId;
    private UUID merchantBId;
    private String merchantAToken;
    private String merchantBToken;
    private UUID merchantAJti;
    private UUID merchantBJti;

    @BeforeEach
    void setUpMerchants() throws Exception {
        var merchantA = provisionMerchant("Merchant A Corp");
        merchantAId = merchantA.merchantId;
        merchantAToken = merchantA.accessToken;
        merchantAJti = merchantA.jti;

        var merchantB = provisionMerchant("Merchant B Corp");
        merchantBId = merchantB.merchantId;
        merchantBToken = merchantB.accessToken;
        merchantBJti = merchantB.jti;
    }

    @Nested
    @DisplayName("Cross-merchant access denial")
    class CrossMerchantAccessDenial {

        @Test
        @DisplayName("merchant A cannot fetch merchant B's details")
        void shouldDenyGetMerchantForOtherMerchant() throws Exception {
            mockMvc.perform(get("/v1/merchants/" + merchantBId)
                            .header("Authorization", "Bearer " + merchantAToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GW-2003"));
        }

        @Test
        @DisplayName("merchant A cannot create API key for merchant B")
        void shouldDenyCreateApiKeyForOtherMerchant() throws Exception {
            mockMvc.perform(post("/v1/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + merchantAToken)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {
                                        "merchantId": "%s",
                                        "name": "Stolen Key",
                                        "environment": "LIVE",
                                        "scopes": ["payments:read"]
                                    }
                                    """.formatted(merchantBId)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GW-2003"));
        }

        @Test
        @DisplayName("merchant A cannot create OAuth client for merchant B")
        void shouldDenyCreateOAuthClientForOtherMerchant() throws Exception {
            mockMvc.perform(post("/v1/merchants/" + merchantBId + "/oauth-clients")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + merchantAToken)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {
                                        "name": "Stolen Client",
                                        "scopes": ["payments:read"],
                                        "grantTypes": ["client_credentials"]
                                    }
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GW-2003"));
        }

        @Test
        @DisplayName("merchant A cannot list OAuth clients for merchant B")
        void shouldDenyListOAuthClientsForOtherMerchant() throws Exception {
            mockMvc.perform(get("/v1/merchants/" + merchantBId + "/oauth-clients")
                            .header("Authorization", "Bearer " + merchantAToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GW-2003"));
        }

        @Test
        @DisplayName("merchant A cannot revoke merchant B's API key")
        void shouldDenyRevokeApiKeyForOtherMerchant() throws Exception {
            // Create an API key for merchant B
            var createResult = mockMvc.perform(post("/v1/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + merchantBToken)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {
                                        "merchantId": "%s",
                                        "name": "B's Key",
                                        "environment": "LIVE",
                                        "scopes": ["payments:read"]
                                    }
                                    """.formatted(merchantBId)))
                    .andExpect(status().isCreated())
                    .andReturn();

            var keyId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.keyId").toString();

            // Merchant A attempts to revoke merchant B's key
            mockMvc.perform(delete("/v1/api-keys/" + keyId)
                            .header("Authorization", "Bearer " + merchantAToken)
                            .header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GW-2003"));
        }

        @Test
        @DisplayName("merchant A cannot revoke merchant B's token")
        void shouldDenyRevokeTokenForOtherMerchant() throws Exception {
            mockMvc.perform(post("/v1/auth/revoke")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + merchantAToken)
                            .content("""
                                    {"jti": "%s"}
                                    """.formatted(merchantBJti)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GW-2003"));
        }
    }

    @Nested
    @DisplayName("Unauthenticated access denial")
    class UnauthenticatedAccessDenial {

        @Test
        @DisplayName("unauthenticated caller cannot reach /v1/auth/revoke")
        void shouldDenyUnauthenticatedRevoke() throws Exception {
            mockMvc.perform(post("/v1/auth/revoke")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jti": "%s"}
                                    """.formatted(UUID.randomUUID())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated caller cannot access merchant endpoints")
        void shouldDenyUnauthenticatedMerchantAccess() throws Exception {
            mockMvc.perform(get("/v1/merchants/" + merchantAId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated caller cannot create API keys")
        void shouldDenyUnauthenticatedApiKeyCreation() throws Exception {
            mockMvc.perform(post("/v1/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content("""
                                    {
                                        "merchantId": "%s",
                                        "name": "Unauthenticated Key",
                                        "environment": "LIVE",
                                        "scopes": ["payments:read"]
                                    }
                                    """.formatted(merchantAId)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Same-merchant access allowed")
    class SameMerchantAccess {

        @Test
        @DisplayName("merchant A can access own resources")
        void shouldAllowOwnMerchantAccess() throws Exception {
            mockMvc.perform(get("/v1/merchants/" + merchantAId)
                            .header("Authorization", "Bearer " + merchantAToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.merchantId").value(merchantAId.toString()));
        }

        @Test
        @DisplayName("merchant A can revoke own token")
        void shouldAllowRevokeOwnToken() throws Exception {
            mockMvc.perform(post("/v1/auth/revoke")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + merchantAToken)
                            .content("""
                                    {"jti": "%s"}
                                    """.formatted(merchantAJti)))
                    .andExpect(status().isOk());
        }
    }

    private ProvisionedMerchant provisionMerchant(String name) throws Exception {
        var externalId = UUID.randomUUID();

        // Activate merchant via Kafka event (auto-registers + auto-provisions OAuth client)
        merchantEventListener.onMerchantActivated("""
                {"merchantId": "%s", "companyName": "%s", "country": "US", "scopes": ["payments:read", "payments:write"]}
                """.formatted(externalId, name));

        // Find the auto-provisioned OAuth client
        var autoClient = oauthClientRepository.findAll().stream()
                .filter(c -> c.getName().equals(name + " Default Client"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Auto-provisioned OAuth client not found for " + name));

        var merchantId = autoClient.getMerchantId();

        // Create a known OAuth client with a known secret (auto-provisioned secret is hashed and unknown)
        var clientId = UUID.randomUUID();
        var clientSecret = "secret-" + UUID.randomUUID();
        var oauthClient = OAuthClientEntity.builder()
                .clientId(clientId)
                .merchantId(merchantId)
                .clientSecretHash(encoder.encode(clientSecret))
                .name(name + " test client")
                .scopes(new String[]{"payments:read", "payments:write"})
                .grantTypes(new String[]{"client_credentials"})
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        oauthClientRepository.saveAndFlush(oauthClient);

        // Issue a token via OAuth2 client_credentials
        var tokenResult = mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "grantType": "client_credentials",
                                    "clientId": "%s",
                                    "clientSecret": "%s",
                                    "scope": "payments:read payments:write"
                                }
                                """.formatted(clientId, clientSecret)))
                .andExpect(status().isOk())
                .andReturn();

        var accessToken = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.accessToken").toString();

        // Get the JTI from the access_tokens table (most recently issued for this merchant)
        var jti = accessTokenRepository.findAll().stream()
                .filter(t -> t.getMerchantId().equals(merchantId) && !t.isRevoked())
                .map(t -> t.getJti())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Access token not found for " + name));

        return new ProvisionedMerchant(merchantId, accessToken, jti);
    }

    private record ProvisionedMerchant(UUID merchantId, String accessToken, UUID jti) {}
}
