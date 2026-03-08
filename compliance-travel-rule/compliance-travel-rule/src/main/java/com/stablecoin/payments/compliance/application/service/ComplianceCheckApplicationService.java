package com.stablecoin.payments.compliance.application.service;

import com.stablecoin.payments.compliance.api.request.InitiateComplianceCheckRequest;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.compliance.api.response.CustomerRiskProfileResponse;
import com.stablecoin.payments.compliance.application.mapper.ComplianceCheckResponseMapper;
import com.stablecoin.payments.compliance.domain.model.Money;
import com.stablecoin.payments.compliance.domain.service.ComplianceCheckCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Thin application service that maps API DTOs to domain objects and delegates
 * all business logic to the {@link ComplianceCheckCommandHandler}.
 */
@Service
@RequiredArgsConstructor
public class ComplianceCheckApplicationService {

    private final ComplianceCheckCommandHandler commandHandler;
    private final ComplianceCheckResponseMapper responseMapper;

    @Transactional
    public ComplianceCheckResponse initiateCheck(InitiateComplianceCheckRequest request) {
        var check = commandHandler.initiateCheck(
                request.paymentId(), request.senderId(), request.recipientId(),
                new Money(request.amount(), request.currency()),
                request.sourceCountry(), request.targetCountry(), request.targetCurrency());
        return responseMapper.toResponse(check);
    }

    @Transactional(readOnly = true)
    public ComplianceCheckResponse getCheck(UUID checkId) {
        return responseMapper.toResponse(commandHandler.getCheck(checkId));
    }

    @Transactional(readOnly = true)
    public CustomerRiskProfileResponse getCustomerRiskProfile(UUID customerId) {
        return responseMapper.toResponse(commandHandler.getCustomerRiskProfile(customerId));
    }
}
