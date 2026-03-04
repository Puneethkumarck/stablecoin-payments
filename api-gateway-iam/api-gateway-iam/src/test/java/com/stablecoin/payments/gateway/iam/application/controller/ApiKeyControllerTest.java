package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.response.ApiKeyResponse;
import com.stablecoin.payments.gateway.iam.application.controller.mapper.GatewayRequestResponseMapper;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyCommandHandler;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyController")
class ApiKeyControllerTest {

    @Mock
    private ApiKeyCommandHandler apiKeyCommandHandler;

    @Mock
    private GatewayRequestResponseMapper mapper;

    @InjectMocks
    private ApiKeyController controller;

    @Test
    @DisplayName("createApiKey should return key with raw key")
    void shouldCreateApiKey() {
        var keyId = UUID.randomUUID();
        var response = new ApiKeyResponse(
                keyId, "pk_live_raw123", "pk_live_abc", "My Key", "LIVE",
                List.of("payments:read"), List.of(), Instant.now().plusSeconds(86400), Instant.now());
        given(apiKeyCommandHandler.create(any(), any(), any(), any(), any(), any()))
                .willReturn(new ApiKeyCommandHandler.CreateApiKeyResult(null, "pk_live_raw123"));
        given(mapper.toApiKeyResponse(any())).willReturn(response);

        var request = new com.stablecoin.payments.gateway.iam.api.request.CreateApiKeyRequest(
                UUID.randomUUID(), "My Key", "LIVE", List.of("payments:read"), null, null);

        var result = controller.createApiKey(request);

        assertThat(result.rawKey()).isEqualTo("pk_live_raw123");
        assertThat(result.keyId()).isEqualTo(keyId);
        assertThat(result.name()).isEqualTo("My Key");
    }

    @Test
    @DisplayName("createApiKey should throw when merchant not found")
    void shouldThrowWhenMerchantNotFound() {
        var merchantId = UUID.randomUUID();
        given(apiKeyCommandHandler.create(any(), any(), any(), any(), any(), any()))
                .willThrow(MerchantNotFoundException.byId(merchantId));

        var request = new com.stablecoin.payments.gateway.iam.api.request.CreateApiKeyRequest(
                merchantId, "My Key", "LIVE", null, null, null);

        assertThatThrownBy(() -> controller.createApiKey(request))
                .isInstanceOf(MerchantNotFoundException.class);
    }

    @Test
    @DisplayName("revokeApiKey should delegate to command handler")
    void shouldRevokeApiKey() {
        var keyId = UUID.randomUUID();

        controller.revokeApiKey(keyId);

        then(apiKeyCommandHandler).should().revoke(keyId);
    }
}
