package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.request.TokenRequest;
import com.stablecoin.payments.gateway.iam.api.request.TokenRevokeRequest;
import com.stablecoin.payments.gateway.iam.api.response.TokenResponse;
import com.stablecoin.payments.gateway.iam.application.service.AuthApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;

    @PostMapping("/v1/auth/token")
    public TokenResponse issueToken(@Valid @RequestBody TokenRequest request) {
        log.info("Token request clientId={} grantType={}", request.clientId(), request.grantType());
        return authApplicationService.issueToken(request);
    }

    @PostMapping("/v1/auth/revoke")
    public void revokeToken(@Valid @RequestBody TokenRevokeRequest request) {
        log.info("Revoke token jti={}", request.jti());
        authApplicationService.revokeToken(request.jti());
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String jwks() {
        return authApplicationService.jwksJson();
    }
}
