package com.stablecoin.payments.gateway.iam.infrastructure.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecureClientSecretGenerator")
class SecureClientSecretGeneratorTest {

    private final SecureClientSecretGenerator generator = new SecureClientSecretGenerator();

    @Test
    @DisplayName("should generate non-blank hex secret")
    void shouldGenerateNonBlankSecret() {
        var secret = generator.generate();

        assertThat(secret).isNotBlank();
        assertThat(secret).hasSize(64); // 32 bytes = 64 hex chars
        assertThat(secret).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("should generate unique secrets")
    void shouldGenerateUniqueSecrets() {
        var secret1 = generator.generate();
        var secret2 = generator.generate();

        assertThat(secret1).isNotEqualTo(secret2);
    }
}
