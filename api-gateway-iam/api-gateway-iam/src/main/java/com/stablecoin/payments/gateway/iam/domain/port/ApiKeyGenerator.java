package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment;

public interface ApiKeyGenerator {

    GeneratedApiKey generate(ApiKeyEnvironment environment);

    record GeneratedApiKey(String rawKey, String prefix) {}
}
