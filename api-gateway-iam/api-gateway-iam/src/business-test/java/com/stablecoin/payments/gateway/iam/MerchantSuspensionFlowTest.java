package com.stablecoin.payments.gateway.iam;

import com.jayway.jsonpath.JsonPath;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.OAuthClientEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.AccessTokenJpaRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.ApiKeyJpaRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("business")
@DisplayName("Merchant Suspension Flow")
class MerchantSuspensionFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyJpaRepository apiKeyRepository;

    @Autowired
    private OAuthClientJpaRepository oauthClientRepository;

    @Autowired
    private AccessTokenJpaRepository accessTokenRepository;

    @Autowired
    private com.stablecoin.payments.gateway.iam.infrastructure.messaging.MerchantEventListener merchantEventListener;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Test
    @DisplayName("should deactivate all keys and revoke tokens when merchant is suspended")
    void shouldDeactivateAllOnSuspension() throws Exception {
        var externalId = UUID.randomUUID();

        // Step 1: Register and activate merchant
        var registerResult = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "externalId": "%s",
                                    "name": "Suspend Corp",
                                    "country": "US",
                                    "scopes": ["payments:read", "payments:write"]
                                }
                                """.formatted(externalId)))
                .andExpect(status().isCreated())
                .andReturn();

        var merchantId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.merchantId").toString();
        var merchantUuid = UUID.fromString(merchantId);

        merchantEventListener.onMerchantActivated("""
                {"merchantId": "%s", "companyName": "Suspend Corp", "country": "US", "scopes": ["payments:read", "payments:write"]}
                """.formatted(externalId));

        // Step 2: Create API key
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "merchantId": "%s",
                                    "name": "Suspend Test Key",
                                    "environment": "LIVE",
                                    "scopes": ["payments:read"]
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isCreated());

        // Step 3: Create OAuth client and issue token
        var clientId = UUID.randomUUID();
        var clientSecret = "suspend-test-secret";
        var oauthClient = OAuthClientEntity.builder()
                .clientId(clientId)
                .merchantId(merchantUuid)
                .clientSecretHash(encoder.encode(clientSecret))
                .name("Suspend Test Client")
                .scopes(new String[]{"payments:read", "payments:write"})
                .grantTypes(new String[]{"client_credentials"})
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        oauthClientRepository.saveAndFlush(oauthClient);

        // Issue a token
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
                .andExpect(status().isOk());

        // Verify pre-suspension state
        assertThat(apiKeyRepository.findByMerchantIdAndActiveTrue(merchantUuid)).hasSize(1);
        assertThat(oauthClientRepository.findByClientIdAndActiveTrue(clientId)).isPresent();

        // Step 4: Simulate Kafka merchant.suspended event
        merchantEventListener.onMerchantSuspended("""
                {"merchantId": "%s", "reason": "compliance review"}
                """.formatted(externalId));

        // Step 5: Verify merchant is SUSPENDED
        mockMvc.perform(get("/v1/merchants/" + merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUSPENDED")));

        // Step 6: Verify all API keys deactivated
        assertThat(apiKeyRepository.findByMerchantIdAndActiveTrue(merchantUuid)).isEmpty();

        // Step 7: Verify OAuth client deactivated
        assertThat(oauthClientRepository.findByClientIdAndActiveTrue(clientId)).isEmpty();

        // Step 8: Token issuance should now fail
        mockMvc.perform(post("/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "grantType": "client_credentials",
                                    "clientId": "%s",
                                    "clientSecret": "%s"
                                }
                                """.formatted(clientId, clientSecret)))
                .andExpect(status().isUnauthorized());

        // Step 9: API key creation should fail — merchant not active
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "merchantId": "%s",
                                    "name": "Should Fail Key",
                                    "environment": "LIVE",
                                    "scopes": ["payments:read"]
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should deactivate all keys and revoke tokens when merchant is closed")
    void shouldDeactivateAllOnClosure() throws Exception {
        var externalId = UUID.randomUUID();

        // Register and activate merchant
        var registerResult = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "externalId": "%s",
                                    "name": "Close Corp",
                                    "country": "GB",
                                    "scopes": ["payments:read"]
                                }
                                """.formatted(externalId)))
                .andExpect(status().isCreated())
                .andReturn();

        var merchantId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.merchantId").toString();
        var merchantUuid = UUID.fromString(merchantId);

        merchantEventListener.onMerchantActivated("""
                {"merchantId": "%s", "companyName": "Close Corp", "country": "GB", "scopes": ["payments:read"]}
                """.formatted(externalId));

        // Create API key
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "merchantId": "%s",
                                    "name": "Close Test Key",
                                    "environment": "LIVE",
                                    "scopes": ["payments:read"]
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isCreated());

        assertThat(apiKeyRepository.findByMerchantIdAndActiveTrue(merchantUuid)).hasSize(1);

        // Simulate Kafka merchant.closed event
        merchantEventListener.onMerchantClosed("""
                {"merchantId": "%s"}
                """.formatted(externalId));

        // Verify merchant is CLOSED
        mockMvc.perform(get("/v1/merchants/" + merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")));

        // Verify all API keys deactivated
        assertThat(apiKeyRepository.findByMerchantIdAndActiveTrue(merchantUuid)).isEmpty();
    }

    @Test
    @DisplayName("should handle token revocation independently of merchant status")
    void shouldRevokeTokenIndependently() throws Exception {
        var externalId = UUID.randomUUID();

        // Register and activate merchant
        var registerResult = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "externalId": "%s",
                                    "name": "Revoke Token Corp",
                                    "country": "US",
                                    "scopes": ["payments:read"]
                                }
                                """.formatted(externalId)))
                .andExpect(status().isCreated())
                .andReturn();

        var merchantId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.merchantId").toString();
        var merchantUuid = UUID.fromString(merchantId);

        merchantEventListener.onMerchantActivated("""
                {"merchantId": "%s", "companyName": "Revoke Token Corp", "country": "US", "scopes": ["payments:read"]}
                """.formatted(externalId));

        // Create OAuth client and issue token
        var clientId = UUID.randomUUID();
        var clientSecret = "revoke-token-secret";
        var oauthClient = OAuthClientEntity.builder()
                .clientId(clientId)
                .merchantId(merchantUuid)
                .clientSecretHash(encoder.encode(clientSecret))
                .name("Revoke Token Client")
                .scopes(new String[]{"payments:read"})
                .grantTypes(new String[]{"client_credentials"})
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        oauthClientRepository.saveAndFlush(oauthClient);

        // Issue token
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
                .andReturn();

        // Verify token exists in DB (not revoked)
        var tokens = accessTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().isRevoked()).isFalse();
        var jti = tokens.getFirst().getJti();

        // Revoke the token
        mockMvc.perform(post("/v1/auth/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jti": "%s"}
                                """.formatted(jti)))
                .andExpect(status().isOk());

        // Verify token is revoked in DB
        var revokedToken = accessTokenRepository.findById(jti);
        assertThat(revokedToken).isPresent();
        assertThat(revokedToken.get().isRevoked()).isTrue();
        assertThat(revokedToken.get().getRevokedAt()).isNotNull();
    }
}
