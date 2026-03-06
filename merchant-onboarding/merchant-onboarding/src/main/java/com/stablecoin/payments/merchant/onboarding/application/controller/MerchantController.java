package com.stablecoin.payments.merchant.onboarding.application.controller;

import com.stablecoin.payments.merchant.onboarding.api.request.ActivateMerchantRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.ApproveCorridorRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.CloseMerchantRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.DocumentUploadRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.MerchantApplicationRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.SuspendMerchantRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.UpdateMerchantRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.UpdateRateLimitTierRequest;
import com.stablecoin.payments.merchant.onboarding.api.response.CorridorResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.DocumentUploadResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.KybStatusResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.MerchantApplicationResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.MerchantResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.PageResponse;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.MerchantCommandHandler;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantCommandHandler commandHandler;
    private final MerchantRequestResponseMapper mapper;

    @GetMapping
    @PreAuthorize("hasAuthority('merchant:read')")
    @Operation(summary = "List merchants with pagination and optional status filter")
    public PageResponse<MerchantResponse> listMerchants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) MerchantStatus status) {
        var result = commandHandler.listMerchants(status, page, size);
        var merchants = result.content().stream()
                .map(mapper::toMerchantResponse)
                .toList();
        return new PageResponse<>(merchants, new PageResponse.Page(
                result.pageNumber(), result.pageSize(), result.totalElements(), result.totalPages()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('merchant:write')")
    public MerchantApplicationResponse apply(@Valid @RequestBody MerchantApplicationRequest request) {
        var command = mapper.toApplyMerchantCommand(request);
        var merchant = commandHandler.apply(command);
        return mapper.toApplicationResponse(merchant);
    }

    @GetMapping("/{merchantId}")
    @PreAuthorize("hasAuthority('merchant:read')")
    public MerchantResponse findById(@PathVariable UUID merchantId) {
        var merchant = commandHandler.findById(merchantId);
        return mapper.toMerchantResponse(merchant);
    }

    @PatchMapping("/{merchantId}")
    @PreAuthorize("hasAuthority('merchant:write')")
    public MerchantResponse updateMerchant(
            @PathVariable UUID merchantId,
            @Valid @RequestBody UpdateMerchantRequest request) {
        var address = request.registeredAddress() != null
                ? mapper.toBusinessAddress(request.registeredAddress())
                : null;
        var merchant = commandHandler.updateMerchant(
                merchantId, request.tradingName(), request.websiteUrl(), address);
        return mapper.toMerchantResponse(merchant);
    }

    @PostMapping("/{merchantId}/kyb/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('admin')")
    public void startKyb(@PathVariable UUID merchantId) {
        commandHandler.startKyb(merchantId);
    }

    @GetMapping("/{merchantId}/kyb")
    @PreAuthorize("hasAuthority('merchant:read')")
    public KybStatusResponse getKybStatus(@PathVariable UUID merchantId) {
        var result = commandHandler.getKybStatus(merchantId);
        return mapper.toKybStatusResponse(result);
    }

    @PostMapping("/{merchantId}/activate")
    @PreAuthorize("hasAuthority('admin')")
    public MerchantResponse activate(
            @PathVariable UUID merchantId,
            @Valid @RequestBody ActivateMerchantRequest request) {
        var merchant = commandHandler.activate(merchantId, request.approvedBy(), request.scopes());
        return mapper.toMerchantResponse(merchant);
    }

    @PostMapping("/{merchantId}/suspend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('admin')")
    public void suspend(
            @PathVariable UUID merchantId,
            @Valid @RequestBody SuspendMerchantRequest request) {
        commandHandler.suspend(merchantId, request.reason(), request.suspendedBy());
    }

    @PostMapping("/{merchantId}/reactivate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('admin')")
    public void reactivate(@PathVariable UUID merchantId) {
        commandHandler.reactivate(merchantId);
    }

    @PostMapping("/{merchantId}/close")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('admin')")
    public void close(
            @PathVariable UUID merchantId,
            @RequestBody(required = false) CloseMerchantRequest request) {
        var reason = request != null ? request.reason() : null;
        var closedBy = request != null ? request.closedBy() : null;
        commandHandler.close(merchantId, reason, closedBy);
    }

    @PostMapping("/{merchantId}/corridors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('admin')")
    public CorridorResponse approveCorridor(
            @PathVariable UUID merchantId,
            @Valid @RequestBody ApproveCorridorRequest request,
            @RequestHeader("X-Approved-By") UUID approvedBy) {
        var corridor = commandHandler.approveCorridor(
                merchantId, request.sourceCountry(), request.targetCountry(),
                request.currencies(), request.maxAmountUsd(), request.expiresAt(), approvedBy);
        return mapper.toCorridorResponse(corridor);
    }

    @PostMapping("/{merchantId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('merchant:write')")
    public DocumentUploadResponse uploadDocument(
            @PathVariable UUID merchantId,
            @Valid @RequestBody DocumentUploadRequest request) {
        var result = commandHandler.uploadDocument(merchantId, request.documentType(), request.fileName());
        return mapper.toDocumentUploadResponse(result);
    }

    @PatchMapping("/{merchantId}/rate-limit-tier")
    @PreAuthorize("hasAuthority('admin')")
    public MerchantResponse updateRateLimitTier(
            @PathVariable UUID merchantId,
            @Valid @RequestBody UpdateRateLimitTierRequest request) {
        var merchant = commandHandler.updateRateLimitTier(merchantId, request.newTier());
        return mapper.toMerchantResponse(merchant);
    }
}
