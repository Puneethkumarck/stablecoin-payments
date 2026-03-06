package com.stablecoin.payments.merchant.iam.infrastructure.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpMfaProviderTest {

    private final TotpMfaProvider provider = new TotpMfaProvider();

    @Test
    void generates_non_blank_secret() {
        var secret = provider.generateSecret();
        assertThat(secret).isNotBlank();
        assertThat(secret.length()).isGreaterThanOrEqualTo(32);
    }

    @Test
    void secrets_are_unique() {
        assertThat(provider.generateSecret()).isNotEqualTo(provider.generateSecret());
    }

    @Test
    void provisioning_uri_contains_issuer_and_email() {
        var secret = provider.generateSecret();
        var uri = provider.generateProvisioningUri("user@test.com", secret);

        assertThat(uri).contains("otpauth://totp/");
        assertThat(uri).contains("user%40test.com");
        assertThat(uri).contains("StableBridge");
        assertThat(uri).contains("secret=");
    }

    @Test
    void invalid_totp_code_returns_false() {
        var secret = provider.generateSecret();
        assertThat(provider.verify(secret, "000000")).isFalse();
        assertThat(provider.verify(secret, "999999")).isFalse();
    }

    @Test
    void verify_returns_false_for_wrong_length_code() {
        var secret = provider.generateSecret();
        assertThat(provider.verify(secret, "12345")).isFalse();
        assertThat(provider.verify(secret, "1234567")).isFalse();
    }
}
