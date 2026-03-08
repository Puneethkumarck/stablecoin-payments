package com.stablecoin.payments.compliance.client;

import com.stablecoin.payments.compliance.api.request.InitiateComplianceCheckRequest;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "compliance-service", url = "${app.services.compliance.url}")
public interface ComplianceCheckClient {

    @PostMapping(value = "/v1/compliance/check", consumes = "application/json", produces = "application/json")
    ComplianceCheckResponse initiateCheck(@RequestBody InitiateComplianceCheckRequest request);

    @GetMapping(value = "/v1/compliance/checks/{checkId}", produces = "application/json")
    ComplianceCheckResponse getCheck(@PathVariable("checkId") UUID checkId);
}
