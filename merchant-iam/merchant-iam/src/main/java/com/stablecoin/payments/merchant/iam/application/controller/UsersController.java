package com.stablecoin.payments.merchant.iam.application.controller;

import com.stablecoin.payments.merchant.iam.api.request.ChangeUserRoleRequest;
import com.stablecoin.payments.merchant.iam.api.request.DeactivateUserRequest;
import com.stablecoin.payments.merchant.iam.api.request.InviteUserRequest;
import com.stablecoin.payments.merchant.iam.api.request.SuspendUserRequest;
import com.stablecoin.payments.merchant.iam.api.response.ChangeRoleResponse;
import com.stablecoin.payments.merchant.iam.api.response.DataResponse;
import com.stablecoin.payments.merchant.iam.api.response.InvitationResponse;
import com.stablecoin.payments.merchant.iam.api.response.PageResponse;
import com.stablecoin.payments.merchant.iam.api.response.ReactivateUserResponse;
import com.stablecoin.payments.merchant.iam.api.response.SuspendUserResponse;
import com.stablecoin.payments.merchant.iam.api.response.UserResponse;
import com.stablecoin.payments.merchant.iam.application.controller.mapper.IamResponseMapper;
import com.stablecoin.payments.merchant.iam.application.security.UserAuthentication;
import com.stablecoin.payments.merchant.iam.domain.team.MerchantTeamService;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/merchants/{merchantId}/users")
@RequiredArgsConstructor
@Validated
public class UsersController {

    private final MerchantTeamService merchantTeamService;
    private final IamResponseMapper mapper;

    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public DataResponse<InvitationResponse> inviteUser(
            @PathVariable UUID merchantId,
            @Valid @RequestBody InviteUserRequest request) {
        log.info("Invite user merchantId={} email={}", merchantId, request.email());
        var result = merchantTeamService.inviteUserWithRole(
                merchantId, request.email(), request.fullName(),
                request.roleId(), resolveCallerId(), "Merchant");
        return DataResponse.of(mapper.toInvitationResponse(
                result.inviteResult().invitation(), result.roleName()));
    }

    @GetMapping
    public PageResponse<UserResponse> listUsers(
            @PathVariable UUID merchantId,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.debug("List users merchantId={}", merchantId);
        var usersWithRoles = merchantTeamService.listUsersWithRoles(merchantId, status);
        var total = usersWithRoles.size();
        var paged = usersWithRoles.stream()
                .skip((long) page * size).limit(size)
                .map(uwr -> mapper.toUserResponse(uwr.user(), uwr.role()))
                .toList();
        var totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(paged, new PageResponse.Page(page, size, total, totalPages));
    }

    @PatchMapping("/{userId}/role")
    public DataResponse<ChangeRoleResponse> changeRole(
            @PathVariable UUID merchantId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeUserRoleRequest request) {
        log.info("Change role userId={} newRoleId={}", userId, request.roleId());
        var result = merchantTeamService.changeUserRole(merchantId, userId, request.roleId(), resolveCallerId());
        return DataResponse.of(mapper.toChangeRoleResponse(result));
    }

    @PostMapping("/{userId}/suspend")
    public DataResponse<SuspendUserResponse> suspendUser(
            @PathVariable UUID merchantId,
            @PathVariable UUID userId,
            @Valid @RequestBody SuspendUserRequest request) {
        log.info("Suspend user userId={}", userId);
        var suspended = merchantTeamService.suspendUser(merchantId, userId, request.reason(), resolveCallerId());
        return DataResponse.of(mapper.toSuspendUserResponse(suspended));
    }

    @PostMapping("/{userId}/reactivate")
    public DataResponse<ReactivateUserResponse> reactivateUser(
            @PathVariable UUID merchantId,
            @PathVariable UUID userId) {
        log.info("Reactivate user userId={}", userId);
        var reactivated = merchantTeamService.reactivateUser(merchantId, userId);
        return DataResponse.of(mapper.toReactivateUserResponse(reactivated));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateUser(
            @PathVariable UUID merchantId,
            @PathVariable UUID userId,
            @Valid @RequestBody DeactivateUserRequest request) {
        log.info("Deactivate user userId={}", userId);
        merchantTeamService.deactivateUser(merchantId, userId, request.reason(), resolveCallerId());
    }

    private UUID resolveCallerId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UserAuthentication userAuth) {
            return userAuth.userId();
        }
        throw new IllegalStateException("No authenticated user in SecurityContext");
    }
}
