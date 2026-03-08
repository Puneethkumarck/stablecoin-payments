package com.stablecoin.payments.compliance.application.controller;

import com.stablecoin.payments.compliance.api.response.CustomerRiskProfileResponse;
import com.stablecoin.payments.compliance.application.service.ComplianceCheckApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/customers")
@RequiredArgsConstructor
public class CustomerRiskProfileController {

    private final ComplianceCheckApplicationService complianceCheckApplicationService;

    @GetMapping("/{customerId}/risk-profile")
    public CustomerRiskProfileResponse getRiskProfile(@PathVariable UUID customerId) {
        log.info("GET /v1/customers/{}/risk-profile", customerId);
        return complianceCheckApplicationService.getCustomerRiskProfile(customerId);
    }
}
