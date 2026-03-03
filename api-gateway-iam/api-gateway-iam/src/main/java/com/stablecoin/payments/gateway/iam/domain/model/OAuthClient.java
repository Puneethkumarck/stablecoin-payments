package com.stablecoin.payments.gateway.iam.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class OAuthClient {

    private final UUID clientId;
    private final UUID merchantId;
    private final String clientSecretHash;
    private final String name;
    private final List<String> scopes;
    private final List<String> grantTypes;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public void deactivate() {
        if (!active) {
            throw new IllegalStateException("OAuth client is already deactivated");
        }
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public boolean hasScope(String scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return scopes != null && scopes.contains(scope);
    }
}
