package com.stablecoin.payments.gateway.iam.domain.port;

public interface ApiKeyHasher {

    String hash(String rawKey);
}
