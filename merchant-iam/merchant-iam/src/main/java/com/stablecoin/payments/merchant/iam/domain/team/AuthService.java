package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.exceptions.InvalidCredentialsException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.MfaRequiredException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.RoleNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.UserNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;
import com.stablecoin.payments.merchant.iam.domain.team.model.Role;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles authentication: login, MFA verify, token issue, and logout.
 * Returns domain objects only — controllers map to API response DTOs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILURES = 5;

    private final MerchantUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserSessionRepository sessionRepository;
    private final EmailHasher emailHasher;
    private final PasswordHasher passwordHasher;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final MfaProvider mfaProvider;
    private final LoginAttemptTracker loginAttemptTracker;
    private final MfaChallengeStore mfaChallengeStore;

    public record LoginResult(MerchantUser user, Role role, String accessToken, String refreshToken) {}

    public record MfaChallengeResult(String challengeId, int expiresInSeconds) {}

    public record MfaSetupResult(String secret, String provisioningUri) {}

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Validates credentials, enforces brute-force lockout.
     * Returns tokens on success; throws {@link MfaRequiredException} if MFA is enabled.
     */
    @Transactional
    public LoginResult login(UUID merchantId, String email, String password) {
        var emailHash = emailHasher.hash(email);

        if (loginAttemptTracker.isLockedOut(emailHash)) {
            log.warn("Login blocked — account locked out emailHash={}", emailHash);
            throw InvalidCredentialsException.invalidEmailOrPassword();
        }

        var user = userRepository.findByMerchantIdAndEmailHash(merchantId, emailHash)
                .orElseThrow(() -> {
                    loginAttemptTracker.recordFailure(emailHash);
                    return InvalidCredentialsException.invalidEmailOrPassword();
                });

        if (user.status() != UserStatus.ACTIVE) {
            throw InvalidCredentialsException.invalidEmailOrPassword();
        }

        if (!passwordHasher.verify(password, user.passwordHash())) {
            var failures = loginAttemptTracker.recordFailure(emailHash);
            log.info("Login failed emailHash={} failures={}", emailHash, failures);
            throw InvalidCredentialsException.invalidEmailOrPassword();
        }

        loginAttemptTracker.clearFailures(emailHash);

        if (user.mfaEnabled()) {
            var challengeId = mfaChallengeStore.store(user.userId(), merchantId, emailHash);
            throw MfaRequiredException.forUser(user.userId(), UUID.fromString(
                    challengeId.length() >= 36 ? challengeId.substring(0, 36) : UUID.randomUUID().toString()));
        }

        return issueTokens(user, false);
    }

    /**
     * Verifies a TOTP code using the stored MFA challenge.
     * Consumes the challenge (one-time use).
     */
    @Transactional
    public LoginResult verifyMfa(String challengeId, String totpCode) {
        var challenge = mfaChallengeStore.consume(challengeId)
                .orElseThrow(InvalidCredentialsException::invalidEmailOrPassword);

        var user = userRepository.findById(challenge.userId())
                .orElseThrow(InvalidCredentialsException::invalidEmailOrPassword);

        if (user.status() != UserStatus.ACTIVE || !user.mfaEnabled()) {
            throw InvalidCredentialsException.invalidEmailOrPassword();
        }

        if (!mfaProvider.verify(user.mfaSecretRef(), totpCode)) {
            loginAttemptTracker.recordFailure(challenge.emailHash());
            throw InvalidCredentialsException.invalidEmailOrPassword();
        }

        loginAttemptTracker.clearFailures(challenge.emailHash());
        return issueTokens(user, true);
    }

    // ── Refresh token ────────────────────────────────────────────────────────

    public record RefreshResult(String accessToken, int expiresIn) {}

    /**
     * Exchanges a valid refresh token for a new access token.
     * Validates the refresh JWT signature, expiry, and that the user is still active.
     */
    @Transactional(readOnly = true)
    public RefreshResult refreshToken(String refreshTokenValue) {
        var parsed = jwtTokenIssuer.parseRefreshToken(refreshTokenValue);

        var user = userRepository.findById(parsed.userId())
                .orElseThrow(InvalidCredentialsException::invalidEmailOrPassword);

        if (user.status() != UserStatus.ACTIVE) {
            throw InvalidCredentialsException.invalidEmailOrPassword();
        }

        var role = roleRepository.findById(user.roleId())
                .orElseThrow(() -> RoleNotFoundException.withId(user.roleId()));

        var accessToken = jwtTokenIssuer.issueAccessToken(user, role, user.mfaEnabled());
        log.info("Access token refreshed userId={}", user.userId());
        return new RefreshResult(accessToken, 3600);
    }

    // ── MFA setup ─────────────────────────────────────────────────────────────

    @Transactional
    public MfaSetupResult setupMfa(UUID userId, String email) {
        var secret = mfaProvider.generateSecret();
        var provisioningUri = mfaProvider.generateProvisioningUri(email, secret);
        log.info("MFA setup initiated userId={}", userId);
        return new MfaSetupResult(secret, provisioningUri);
    }

    @Transactional
    public MerchantUser activateMfa(UUID userId, String secret, String totpCode) {
        if (!mfaProvider.verify(secret, totpCode)) {
            throw InvalidCredentialsException.invalidEmailOrPassword();
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));
        var updated = user.enableMfa(secret);
        var saved = userRepository.save(updated);
        log.info("MFA activated userId={}", userId);
        return saved;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Role findRoleForUser(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> RoleNotFoundException.withId(roleId));
    }

    public String jwksJson() {
        return jwtTokenIssuer.jwksJson();
    }

    @Transactional
    public void logout(UUID userId) {
        sessionRepository.revokeAllByUserId(userId, "logout");
        log.info("Logged out userId={}", userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LoginResult issueTokens(MerchantUser user, boolean mfaVerified) {
        var role = roleRepository.findById(user.roleId())
                .orElseThrow(() -> RoleNotFoundException.withId(user.roleId()));
        var sessionId = UUID.randomUUID();
        var accessToken = jwtTokenIssuer.issueAccessToken(user, role, mfaVerified);
        var refreshToken = jwtTokenIssuer.issueRefreshToken(user.userId(), sessionId);
        var updated = userRepository.save(user.recordLogin());
        log.info("Tokens issued userId={} mfaVerified={}", user.userId(), mfaVerified);
        return new LoginResult(updated, role, accessToken, refreshToken);
    }
}
