package com.stablecoin.payments.gateway.iam.infrastructure.auth;

import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class SecureClientSecretGenerator implements ClientSecretGenerator {

    private static final int SECRET_LENGTH_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        var bytes = new byte[SECRET_LENGTH_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
