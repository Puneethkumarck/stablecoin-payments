package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.domain.exception.InvalidClientCredentialsException;
import com.stablecoin.payments.gateway.iam.domain.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    @Test
    @DisplayName("issueToken should return token response")
    void shouldIssueToken() {
        var clientId = UUID.randomUUID();
        var result = new AuthService.TokenResult("jwt-token", UUID.randomUUID(), 3600, List.of("payments:read"));
        given(authService.issueToken(eq(clientId), eq("secret"), any())).willReturn(result);

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
        var clientId = UUID.randomUUID();
        given(authService.issueToken(eq(clientId), any(), any()))
                .willThrow(InvalidClientCredentialsException.clientNotFound());

        var request = new com.stablecoin.payments.gateway.iam.api.request.TokenRequest(
                "client_credentials", clientId, "wrong", null);

        assertThatThrownBy(() -> controller.issueToken(request))
                .isInstanceOf(InvalidClientCredentialsException.class);
    }

    @Test
    @DisplayName("revokeToken should delegate to service")
    void shouldRevokeToken() {
        var jti = UUID.randomUUID();
        var request = new com.stablecoin.payments.gateway.iam.api.request.TokenRevokeRequest(jti);

        controller.revokeToken(request);

        then(authService).should().revokeToken(jti);
    }

    @Test
    @DisplayName("jwks should return JWKS JSON")
    void shouldReturnJwks() {
        given(authService.jwksJson()).willReturn("{\"keys\":[]}");

        var result = controller.jwks();

        assertThat(result).isEqualTo("{\"keys\":[]}");
    }
}
