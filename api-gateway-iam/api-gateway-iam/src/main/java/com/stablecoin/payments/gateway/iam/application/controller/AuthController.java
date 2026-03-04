package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.request.TokenRequest;
import com.stablecoin.payments.gateway.iam.api.request.TokenRevokeRequest;
import com.stablecoin.payments.gateway.iam.api.response.TokenResponse;
import com.stablecoin.payments.gateway.iam.application.controller.mapper.GatewayRequestResponseMapper;
import com.stablecoin.payments.gateway.iam.domain.service.AuthCommandHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthCommandHandler authCommandHandler;
    private final GatewayRequestResponseMapper mapper;

    @PostMapping("/v1/auth/token")
    public TokenResponse issueToken(@Valid @RequestBody TokenRequest request) {
        log.info("Token request clientId={} grantType={}", request.clientId(), request.grantType());

        List<String> scopes = request.scope() != null
                ? Arrays.asList(request.scope().split(" "))
                : List.of();

        var result = authCommandHandler.issueToken(
                request.clientId(), request.clientSecret(), scopes);

        return mapper.toTokenResponse(result);
    }

    @PostMapping("/v1/auth/revoke")
    public void revokeToken(@Valid @RequestBody TokenRevokeRequest request) {
        log.info("Revoke token jti={}", request.jti());
        authCommandHandler.revokeToken(request.jti());
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String jwks() {
        return authCommandHandler.jwksJson();
    }
}
