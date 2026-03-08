package com.stablecoin.payments.compliance.application.controller;

import com.stablecoin.payments.compliance.api.request.InitiateComplianceCheckRequest;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.compliance.application.service.ComplianceCheckApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/v1/compliance")
@RequiredArgsConstructor
public class ComplianceCheckController {

    private final ComplianceCheckApplicationService complianceCheckApplicationService;

    @PostMapping("/check")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ComplianceCheckResponse initiateCheck(
            @Valid @RequestBody InitiateComplianceCheckRequest request) {
        log.info("POST /v1/compliance/check paymentId={}", request.paymentId());
        return complianceCheckApplicationService.initiateCheck(request);
    }

    @GetMapping("/checks/{checkId}")
    public ComplianceCheckResponse getCheck(@PathVariable UUID checkId) {
        log.info("GET /v1/compliance/checks/{}", checkId);
        return complianceCheckApplicationService.getCheck(checkId);
    }
}
