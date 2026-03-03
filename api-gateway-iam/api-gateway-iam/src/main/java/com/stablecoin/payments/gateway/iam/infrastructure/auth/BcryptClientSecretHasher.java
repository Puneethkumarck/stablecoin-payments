package com.stablecoin.payments.gateway.iam.infrastructure.auth;

import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptClientSecretHasher implements ClientSecretHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Override
    public String hash(String rawSecret) {
        return encoder.encode(rawSecret);
    }

    @Override
    public boolean matches(String rawSecret, String hash) {
        return encoder.matches(rawSecret, hash);
    }
}
