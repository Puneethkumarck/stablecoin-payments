package com.stablecoin.payments.merchant.iam.application.controller;

import com.stablecoin.payments.merchant.iam.api.request.CreateRoleRequest;
import com.stablecoin.payments.merchant.iam.api.request.UpdateRoleRequest;
import com.stablecoin.payments.merchant.iam.api.response.DataResponse;
import com.stablecoin.payments.merchant.iam.api.response.PageResponse;
import com.stablecoin.payments.merchant.iam.api.response.RoleResponse;
import com.stablecoin.payments.merchant.iam.application.controller.mapper.IamResponseMapper;
import com.stablecoin.payments.merchant.iam.application.security.UserAuthentication;
import com.stablecoin.payments.merchant.iam.domain.team.MerchantTeamService;
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
@RequestMapping("/v1/merchants/{merchantId}/roles")
@RequiredArgsConstructor
@Validated
public class RolesController {

    private final MerchantTeamService merchantTeamService;
    private final IamResponseMapper mapper;

    @GetMapping
    public PageResponse<RoleResponse> listRoles(
            @PathVariable UUID merchantId,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.debug("List roles merchantId={}", merchantId);
        var roles = merchantTeamService.listRoles(merchantId, includeInactive);
        var total = roles.size();
        var paged = roles.stream()
                .skip((long) page * size).limit(size)
                .map(mapper::toRoleResponse)
                .toList();
        var totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(paged, new PageResponse.Page(page, size, total, totalPages));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataResponse<RoleResponse> createRole(
            @PathVariable UUID merchantId,
            @Valid @RequestBody CreateRoleRequest request) {
        log.info("Create role merchantId={} roleName={}", merchantId, request.roleName());
        var role = merchantTeamService.createRole(
                merchantId, request.roleName(), request.description(),
                request.permissions(), resolveCallerId());
        return DataResponse.of(mapper.toRoleResponse(role));
    }

    @PatchMapping("/{roleId}")
    public DataResponse<RoleResponse> updateRole(
            @PathVariable UUID merchantId,
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        log.info("Update role merchantId={} roleId={}", merchantId, roleId);
        var role = merchantTeamService.updateRole(merchantId, roleId, request.permissions());
        return DataResponse.of(mapper.toRoleResponse(role));
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(
            @PathVariable UUID merchantId,
            @PathVariable UUID roleId) {
        log.info("Delete role merchantId={} roleId={}", merchantId, roleId);
        merchantTeamService.deleteRole(merchantId, roleId);
    }

    private UUID resolveCallerId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UserAuthentication userAuth) {
            return userAuth.userId();
        }
        throw new IllegalStateException("No authenticated user in SecurityContext");
    }
}
