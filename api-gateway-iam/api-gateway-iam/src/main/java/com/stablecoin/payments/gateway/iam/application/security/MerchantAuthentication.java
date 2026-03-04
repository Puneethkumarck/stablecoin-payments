package com.stablecoin.payments.gateway.iam.application.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MerchantAuthentication extends AbstractAuthenticationToken {

    private final UUID merchantId;
    private final UUID clientId;
    private final List<String> scopes;
    private final AuthMethod authMethod;

    public MerchantAuthentication(UUID merchantId, UUID clientId, List<String> scopes,
                                  AuthMethod authMethod) {
        super(Objects.requireNonNull(scopes, "scopes must not be null").stream()
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .toList());
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId must not be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.scopes = List.copyOf(scopes);
        this.authMethod = Objects.requireNonNull(authMethod, "authMethod must not be null");
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return merchantId;
    }

    public UUID merchantId() {
        return merchantId;
    }

    public UUID clientId() {
        return clientId;
    }

    public List<String> scopes() {
        return scopes;
    }

    public AuthMethod authMethod() {
        return authMethod;
    }

    public enum AuthMethod {
        JWT,
        API_KEY
    }
}
