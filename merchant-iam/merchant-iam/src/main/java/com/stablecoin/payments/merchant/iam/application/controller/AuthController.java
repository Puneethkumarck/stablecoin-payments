package com.stablecoin.payments.merchant.iam.application.controller;

import com.stablecoin.payments.merchant.iam.api.request.AcceptInvitationRequest;
import com.stablecoin.payments.merchant.iam.api.request.LoginRequest;
import com.stablecoin.payments.merchant.iam.api.request.MfaVerifyRequest;
import com.stablecoin.payments.merchant.iam.api.response.DataResponse;
import com.stablecoin.payments.merchant.iam.api.response.LoginResponse;
import com.stablecoin.payments.merchant.iam.api.response.MfaChallengeResponse;
import com.stablecoin.payments.merchant.iam.api.response.UserResponse;
import com.stablecoin.payments.merchant.iam.application.controller.mapper.IamResponseMapper;
import com.stablecoin.payments.merchant.iam.application.security.UserAuthentication;
import com.stablecoin.payments.merchant.iam.domain.exceptions.MfaRequiredException;
import com.stablecoin.payments.merchant.iam.domain.team.AuthService;
import com.stablecoin.payments.merchant.iam.domain.team.MerchantTeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final MerchantTeamService merchantTeamService;
    private final IamResponseMapper mapper;

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * POST /v1/merchants/{merchantId}/auth/login
     * Returns LoginResponse on success, or MfaChallengeResponse if MFA is enabled.
     */
    @PostMapping("/merchants/{merchantId}/auth/login")
    public DataResponse<?> login(
            @PathVariable UUID merchantId,
            @Valid @RequestBody LoginRequest request) {
        log.info("Login attempt merchantId={}", merchantId);
        try {
            var result = authService.login(merchantId, request.email(), request.password());
            return DataResponse.of(buildLoginResponse(result));
        } catch (MfaRequiredException ex) {
            log.info("MFA required userId={}", ex.getUserId());
            // challengeId is stored in the session UUID field (see AuthService.login)
            return DataResponse.of(new MfaChallengeResponse(
                    true, ex.getSessionId().toString(), 300));
        }
    }

    /**
     * POST /v1/merchants/{merchantId}/auth/mfa/verify
     * Verifies TOTP code against the stored challenge; returns tokens on success.
     * The {@code mfaChallengeId} from the login MFA challenge response is passed as {@code totpCode}.
     */
    @PostMapping("/merchants/{merchantId}/auth/mfa/verify")
    public DataResponse<LoginResponse> verifyMfa(
            @PathVariable UUID merchantId,
            @Valid @RequestBody MfaVerifyRequest request) {
        log.info("MFA verify challengeId={}", request.mfaChallengeId());
        var result = authService.verifyMfa(request.mfaChallengeId(), request.totpCode());
        return DataResponse.of(buildLoginResponse(result));
    }

    // ── Invitation acceptance ─────────────────────────────────────────────────

    /**
     * POST /v1/invitations/{token}/accept
     * No auth required — invitation token in URL serves as credential.
     */
    @PostMapping("/invitations/{token}/accept")
    public DataResponse<UserResponse> acceptInvitation(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest request) {
        log.info("Accept invitation token={}...", token.substring(0, Math.min(8, token.length())));
        var activated = merchantTeamService.acceptInvitation(
                token, request.fullName(), request.password());
        var role = merchantTeamService.findRole(activated.roleId());
        return DataResponse.of(mapper.toUserResponse(activated, role));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * POST /v1/auth/logout
     * Revokes all sessions for the authenticated user.
     */
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UserAuthentication userAuth) {
            log.info("Logout request userId={}", userAuth.userId());
            authService.logout(userAuth.userId());
        } else {
            log.warn("Logout called without valid authentication");
        }
    }

    // ── JWKS ──────────────────────────────────────────────────────────────────

    /**
     * GET /v1/.well-known/jwks.json
     * Publishes S13's ES256 public key. S10 (API Gateway) fetches this to validate tokens.
     */
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String jwks() {
        return authService.jwksJson();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LoginResponse buildLoginResponse(AuthService.LoginResult result) {
        var permissions = result.role().permissions().stream()
                .map(p -> p.namespace() + ":" + p.action())
                .toList();
        var userInfo = new LoginResponse.UserInfo(
                result.user().userId(), result.user().merchantId(),
                result.user().fullName(), result.role().roleName(), permissions);
        return new LoginResponse(
                result.accessToken(), result.refreshToken(), "Bearer", 3600, userInfo);
    }
}
