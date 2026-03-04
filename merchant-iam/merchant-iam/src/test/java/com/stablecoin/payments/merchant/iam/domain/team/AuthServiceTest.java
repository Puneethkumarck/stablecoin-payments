package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.exceptions.InvalidCredentialsException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.RoleNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;
import com.stablecoin.payments.merchant.iam.domain.team.model.Role;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.AuthProvider;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.BuiltInRole;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock private MerchantUserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserSessionRepository sessionRepository;
    @Mock private EmailHasher emailHasher;
    @Mock private PasswordHasher passwordHasher;
    @Mock private JwtTokenIssuer jwtTokenIssuer;
    @Mock private MfaProvider mfaProvider;
    @Mock private LoginAttemptTracker loginAttemptTracker;
    @Mock private MfaChallengeStore mfaChallengeStore;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, roleRepository, sessionRepository,
                emailHasher, passwordHasher, jwtTokenIssuer,
                mfaProvider, loginAttemptTracker, mfaChallengeStore);
    }

    private MerchantUser buildActiveUser() {
        return MerchantUser.builder()
                .userId(USER_ID).merchantId(MERCHANT_ID)
                .email("admin@test.com").emailHash("hash")
                .fullName("Admin").status(UserStatus.ACTIVE)
                .roleId(ROLE_ID).authProvider(AuthProvider.LOCAL)
                .mfaEnabled(false)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .activatedAt(Instant.now())
                .build();
    }

    private Role buildRole() {
        return Role.builder()
                .roleId(ROLE_ID).merchantId(MERCHANT_ID)
                .roleName("ADMIN").description("Admin")
                .builtin(true).active(true)
                .permissions(new ArrayList<>(BuiltInRole.ADMIN.defaultPermissions()))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    @Nested
    class RefreshToken {

        @Test
        void shouldRefreshTokenForActiveUser() {
            var parsed = new JwtTokenIssuer.ParsedRefreshToken(
                    UUID.randomUUID(), USER_ID, SESSION_ID,
                    Instant.now().plusSeconds(3600).getEpochSecond());
            given(jwtTokenIssuer.parseRefreshToken("refresh-jwt")).willReturn(parsed);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(buildActiveUser()));
            given(roleRepository.findById(ROLE_ID)).willReturn(Optional.of(buildRole()));
            given(jwtTokenIssuer.issueAccessToken(any(), any(), anyBoolean())).willReturn("new-access-token");

            var result = authService.refreshToken("refresh-jwt");

            assertThat(result.accessToken()).isEqualTo("new-access-token");
            assertThat(result.expiresIn()).isEqualTo(3600);
        }

        @Test
        void shouldRejectRefreshForInactiveUser() {
            var suspended = buildActiveUser().suspend();
            var parsed = new JwtTokenIssuer.ParsedRefreshToken(
                    UUID.randomUUID(), USER_ID, SESSION_ID,
                    Instant.now().plusSeconds(3600).getEpochSecond());
            given(jwtTokenIssuer.parseRefreshToken("refresh-jwt")).willReturn(parsed);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(suspended));

            assertThatThrownBy(() -> authService.refreshToken("refresh-jwt"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void shouldRejectRefreshForUnknownUser() {
            var parsed = new JwtTokenIssuer.ParsedRefreshToken(
                    UUID.randomUUID(), USER_ID, SESSION_ID,
                    Instant.now().plusSeconds(3600).getEpochSecond());
            given(jwtTokenIssuer.parseRefreshToken("refresh-jwt")).willReturn(parsed);
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken("refresh-jwt"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void shouldRejectRefreshWhenRoleMissing() {
            var parsed = new JwtTokenIssuer.ParsedRefreshToken(
                    UUID.randomUUID(), USER_ID, SESSION_ID,
                    Instant.now().plusSeconds(3600).getEpochSecond());
            given(jwtTokenIssuer.parseRefreshToken("refresh-jwt")).willReturn(parsed);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(buildActiveUser()));
            given(roleRepository.findById(ROLE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken("refresh-jwt"))
                    .isInstanceOf(RoleNotFoundException.class);
        }

        @Test
        void shouldPropagateInvalidRefreshToken() {
            given(jwtTokenIssuer.parseRefreshToken("bad-token"))
                    .willThrow(new IllegalArgumentException("Invalid refresh token"));

            assertThatThrownBy(() -> authService.refreshToken("bad-token"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid refresh token");
        }
    }

    @Nested
    class SetupMfa {

        @Test
        void shouldReturnMfaSetupResult() {
            given(mfaProvider.generateSecret()).willReturn("JBSWY3DPEHPK3PXP");
            given(mfaProvider.generateProvisioningUri("admin@test.com", "JBSWY3DPEHPK3PXP"))
                    .willReturn("otpauth://totp/Test:admin@test.com?secret=JBSWY3DPEHPK3PXP");

            var result = authService.setupMfa(USER_ID, "admin@test.com");

            assertThat(result.secret()).isEqualTo("JBSWY3DPEHPK3PXP");
            assertThat(result.provisioningUri()).contains("otpauth://totp/");
        }
    }

    @Nested
    class ActivateMfa {

        @Test
        void shouldActivateMfaWhenTotpValid() {
            var user = buildActiveUser();
            given(mfaProvider.verify("secret", "123456")).willReturn(true);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var result = authService.activateMfa(USER_ID, "secret", "123456");

            assertThat(result.mfaEnabled()).isTrue();
        }

        @Test
        void shouldRejectActivationWithInvalidTotp() {
            given(mfaProvider.verify("secret", "000000")).willReturn(false);

            assertThatThrownBy(() -> authService.activateMfa(USER_ID, "secret", "000000"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }
}
