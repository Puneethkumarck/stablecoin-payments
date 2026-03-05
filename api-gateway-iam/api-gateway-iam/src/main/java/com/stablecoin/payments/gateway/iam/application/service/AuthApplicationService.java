package com.stablecoin.payments.gateway.iam.application.service;

import com.stablecoin.payments.gateway.iam.api.request.TokenRequest;
import com.stablecoin.payments.gateway.iam.api.response.TokenResponse;
import com.stablecoin.payments.gateway.iam.domain.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthApplicationService {

    private final AuthService authService;

    public TokenResponse issueToken(TokenRequest request) {
        List<String> scopes = request.scope() != null
                ? Arrays.asList(request.scope().split(" "))
                : List.of();

        var result = authService.issueToken(
                request.clientId(), request.clientSecret(), scopes);

        return new TokenResponse(
                result.accessToken(),
                "Bearer",
                (int) result.expiresIn(),
                String.join(" ", result.scopes()));
    }

    public void revokeToken(UUID jti) {
        authService.revokeToken(jti);
    }

    @Transactional(readOnly = true)
    public String jwksJson() {
        return authService.jwksJson();
    }
}
