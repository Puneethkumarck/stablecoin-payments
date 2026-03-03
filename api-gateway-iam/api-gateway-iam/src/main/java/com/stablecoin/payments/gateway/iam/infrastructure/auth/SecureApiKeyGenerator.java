package com.stablecoin.payments.gateway.iam.infrastructure.auth;

import com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class SecureApiKeyGenerator implements ApiKeyGenerator {

    private static final int KEY_BYTES = 32;
    private static final int PREFIX_DISPLAY_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

    @Override
    public GeneratedApiKey generate(ApiKeyEnvironment environment) {
        var bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        var hex = HexFormat.of().formatHex(bytes);

        var prefix = environment == ApiKeyEnvironment.LIVE ? "pk_live_" : "pk_test_";
        var rawKey = prefix + hex;
        var displayPrefix = rawKey.substring(0, prefix.length() + PREFIX_DISPLAY_LENGTH);

        return new GeneratedApiKey(rawKey, displayPrefix);
    }
}
