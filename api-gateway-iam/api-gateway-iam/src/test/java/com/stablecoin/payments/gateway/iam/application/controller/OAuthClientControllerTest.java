package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.request.CreateOAuthClientRequest;
import com.stablecoin.payments.gateway.iam.api.response.OAuthClientResponse;
import com.stablecoin.payments.gateway.iam.application.controller.mapper.GatewayRequestResponseMapper;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotActiveException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.service.OAuthClientCommandHandler;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthClientController")
class OAuthClientControllerTest {

    @Mock
    private OAuthClientCommandHandler oauthClientCommandHandler;

    @Mock
    private GatewayRequestResponseMapper mapper;

    @InjectMocks
    private OAuthClientController controller;

    @Test
    @DisplayName("createOAuthClient should return response with raw secret")
    void shouldCreateOAuthClient() {
        var merchantId = UUID.randomUUID();
        var clientId = UUID.randomUUID();
        var response = new OAuthClientResponse(
                clientId, "raw-secret-hex", merchantId, "My Client",
                List.of("payments:read"), List.of("client_credentials"), Instant.now());
        given(oauthClientCommandHandler.create(eq(merchantId), any(), any(), any()))
                .willReturn(new OAuthClientCommandHandler.CreateOAuthClientResult(null, "raw-secret-hex"));
        given(mapper.toOAuthClientResponse(any())).willReturn(response);

        var request = new CreateOAuthClientRequest(
                "My Client", List.of("payments:read"), List.of("client_credentials"));

        var result = controller.createOAuthClient(merchantId, request);

        assertThat(result.clientId()).isEqualTo(clientId);
        assertThat(result.clientSecret()).isEqualTo("raw-secret-hex");
        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.name()).isEqualTo("My Client");
    }

    @Test
    @DisplayName("createOAuthClient should throw when merchant not found")
    void shouldThrowWhenMerchantNotFound() {
        var merchantId = UUID.randomUUID();
        given(oauthClientCommandHandler.create(eq(merchantId), any(), any(), any()))
                .willThrow(MerchantNotFoundException.byId(merchantId));

        var request = new CreateOAuthClientRequest("Client", null, null);

        assertThatThrownBy(() -> controller.createOAuthClient(merchantId, request))
                .isInstanceOf(MerchantNotFoundException.class);
    }

    @Test
    @DisplayName("createOAuthClient should throw when merchant not active")
    void shouldThrowWhenMerchantNotActive() {
        var merchantId = UUID.randomUUID();
        given(oauthClientCommandHandler.create(eq(merchantId), any(), any(), any()))
                .willThrow(MerchantNotActiveException.of(merchantId));

        var request = new CreateOAuthClientRequest("Client", null, null);

        assertThatThrownBy(() -> controller.createOAuthClient(merchantId, request))
                .isInstanceOf(MerchantNotActiveException.class);
    }
}
