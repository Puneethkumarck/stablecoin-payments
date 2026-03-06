package com.stablecoin.payments.merchant.iam.infrastructure.auth;

import com.stablecoin.payments.merchant.iam.domain.team.MfaProvider;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TotpMfaProvider implements MfaProvider {

    private static final String ISSUER = "StableBridge";
    private static final int DIGITS = 6;
    private static final int PERIOD = 30;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());

    @Override
    public String generateSecret() {
        return secretGenerator.generate();
    }

    @Override
    public String generateProvisioningUri(String email, String secret) {
        var qrData = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(DIGITS)
                .period(PERIOD)
                .build();
        return qrData.getUri();
    }

    @Override
    public boolean verify(String secret, String totpCode) {
        return codeVerifier.isValidCode(secret, totpCode);
    }
}
