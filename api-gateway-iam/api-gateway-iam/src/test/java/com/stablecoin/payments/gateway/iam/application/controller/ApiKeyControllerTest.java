package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKey;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyController")
class ApiKeyControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    @InjectMocks
    private ApiKeyController controller;

    @Test
    @DisplayName("createApiKey should return key with raw key")
    void shouldCreateApiKey() {
        var merchantId = UUID.randomUUID();
        var apiKey = anApiKey(merchantId);
        var result = new ApiKeyService.CreateApiKeyResult(apiKey, "pk_live_raw123");
        given(apiKeyService.create(eq(merchantId), any(), any(), any(), any(), any())).willReturn(result);

        var request = new com.stablecoin.payments.gateway.iam.api.request.CreateApiKeyRequest(
                merchantId, "My Key", "LIVE", List.of("payments:read"), null, null);

        var response = controller.createApiKey(request);

        assertThat(response.rawKey()).isEqualTo("pk_live_raw123");
        assertThat(response.keyId()).isEqualTo(apiKey.getKeyId());
        assertThat(response.name()).isEqualTo("My Key");
    }

    @Test
    @DisplayName("createApiKey should throw when merchant not found")
    void shouldThrowWhenMerchantNotFound() {
        var merchantId = UUID.randomUUID();
        given(apiKeyService.create(eq(merchantId), any(), any(), any(), any(), any()))
                .willThrow(MerchantNotFoundException.byId(merchantId));

        var request = new com.stablecoin.payments.gateway.iam.api.request.CreateApiKeyRequest(
                merchantId, "My Key", "LIVE", null, null, null);

        assertThatThrownBy(() -> controller.createApiKey(request))
                .isInstanceOf(MerchantNotFoundException.class);
    }

    @Test
    @DisplayName("revokeApiKey should delegate to service")
    void shouldRevokeApiKey() {
        var keyId = UUID.randomUUID();

        controller.revokeApiKey(keyId);

        then(apiKeyService).should().revoke(keyId);
    }

    private ApiKey anApiKey(UUID merchantId) {
        return ApiKey.builder()
                .keyId(UUID.randomUUID())
                .merchantId(merchantId)
                .keyHash("sha256_hash")
                .keyPrefix("pk_live_abc")
                .name("My Key")
                .environment(ApiKeyEnvironment.LIVE)
                .scopes(List.of("payments:read"))
                .allowedIps(List.of())
                .active(true)
                .expiresAt(Instant.now().plusSeconds(86400))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }
}
