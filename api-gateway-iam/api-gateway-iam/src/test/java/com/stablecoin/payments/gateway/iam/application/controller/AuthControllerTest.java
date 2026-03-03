package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.response.TokenResponse;
import com.stablecoin.payments.gateway.iam.application.service.AuthApplicationService;
import com.stablecoin.payments.gateway.iam.domain.exception.InvalidClientCredentialsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock
    private AuthApplicationService authApplicationService;

    @InjectMocks
    private AuthController controller;

    @Test
    @DisplayName("issueToken should return token response")
    void shouldIssueToken() {
        var clientId = UUID.randomUUID();
        var tokenResponse = new TokenResponse("jwt-token", "Bearer", 3600, "payments:read");
        given(authApplicationService.issueToken(any())).willReturn(tokenResponse);

        var request = new com.stablecoin.payments.gateway.iam.api.request.TokenRequest(
                "client_credentials", clientId, "secret", "payments:read");
        var response = controller.issueToken(request);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.scope()).isEqualTo("payments:read");
    }

    @Test
    @DisplayName("issueToken should propagate invalid credentials")
    void shouldPropagateInvalidCredentials() {
        given(authApplicationService.issueToken(any()))
                .willThrow(InvalidClientCredentialsException.clientNotFound());

        var request = new com.stablecoin.payments.gateway.iam.api.request.TokenRequest(
                "client_credentials", UUID.randomUUID(), "wrong", null);

        assertThatThrownBy(() -> controller.issueToken(request))
                .isInstanceOf(InvalidClientCredentialsException.class);
    }

    @Test
    @DisplayName("revokeToken should delegate to service")
    void shouldRevokeToken() {
        var jti = UUID.randomUUID();
        var request = new com.stablecoin.payments.gateway.iam.api.request.TokenRevokeRequest(jti);

        controller.revokeToken(request);

        then(authApplicationService).should().revokeToken(jti);
    }

    @Test
    @DisplayName("jwks should return JWKS JSON")
    void shouldReturnJwks() {
        given(authApplicationService.jwksJson()).willReturn("{\"keys\":[]}");

        var result = controller.jwks();

        assertThat(result).isEqualTo("{\"keys\":[]}");
    }
}
