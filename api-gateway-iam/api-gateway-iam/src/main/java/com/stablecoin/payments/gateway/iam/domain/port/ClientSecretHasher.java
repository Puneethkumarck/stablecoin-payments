package com.stablecoin.payments.gateway.iam.domain.port;

public interface ClientSecretHasher {

    String hash(String rawSecret);

    boolean matches(String rawSecret, String hash);
}
