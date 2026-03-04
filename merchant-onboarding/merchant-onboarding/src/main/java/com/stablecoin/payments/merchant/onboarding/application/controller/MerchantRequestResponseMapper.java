package com.stablecoin.payments.merchant.onboarding.application.controller;

import com.stablecoin.payments.merchant.onboarding.api.request.MerchantApplicationRequest;
import com.stablecoin.payments.merchant.onboarding.api.response.CorridorResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.DocumentUploadResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.KybStatusResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.MerchantApplicationResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.MerchantResponse;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.Merchant;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.command.ApplyMerchantCommand;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.ApprovedCorridor;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.BeneficialOwner;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.BusinessAddress;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.DocumentUploadResult;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.EntityType;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatusResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface MerchantRequestResponseMapper {

    @Mapping(target = "status", expression = "java(merchant.getStatus().name())")
    @Mapping(target = "kybStatus", expression = "java(merchant.getKybStatus().name())")
    @Mapping(target = "riskTier", expression = "java(merchant.getRiskTier() != null ? merchant.getRiskTier().name() : null)")
    @Mapping(target = "rateLimitTier", expression = "java(merchant.getRateLimitTier().name())")
    @Mapping(target = "entityType", expression = "java(merchant.getEntityType().name())")
    MerchantResponse toMerchantResponse(Merchant merchant);

    @Mapping(target = "status", expression = "java(merchant.getStatus().name())")
    @Mapping(target = "kybStatus", expression = "java(merchant.getKybStatus().name())")
    MerchantApplicationResponse toApplicationResponse(Merchant merchant);

    @Mapping(target = "active", source = "isActive")
    CorridorResponse toCorridorResponse(ApprovedCorridor corridor);

    KybStatusResponse toKybStatusResponse(KybStatusResult result);

    DocumentUploadResponse toDocumentUploadResponse(DocumentUploadResult result);

    BusinessAddress toBusinessAddress(MerchantApplicationRequest.BusinessAddressDto dto);

    List<BeneficialOwner> toBeneficialOwners(List<MerchantApplicationRequest.BeneficialOwnerDto> dtos);

    @Mapping(target = "isPoliticallyExposed", source = "isPoliticallyExposed")
    BeneficialOwner toBeneficialOwner(MerchantApplicationRequest.BeneficialOwnerDto dto);

    default ApplyMerchantCommand toApplyMerchantCommand(MerchantApplicationRequest request) {
        return new ApplyMerchantCommand(
                request.legalName(),
                request.tradingName(),
                request.registrationNumber(),
                request.registrationCountry(),
                EntityType.valueOf(request.entityType()),
                request.websiteUrl(),
                request.primaryCurrency(),
                toBusinessAddress(request.registeredAddress()),
                toBeneficialOwners(request.beneficialOwners()),
                request.requestedCorridors()
        );
    }
}
