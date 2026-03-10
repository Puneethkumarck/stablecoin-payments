package com.stablecoin.payments.offramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.infrastructure.persistence.entity.PayoutOrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface PayoutOrderEntityUpdater {

    default void updateEntity(@MappingTarget PayoutOrderEntity entity, PayoutOrder order) {
        if (order == null) {
            return;
        }
        entity.setPaymentId(order.paymentId());
        entity.setCorrelationId(order.correlationId());
        entity.setTransferId(order.transferId());
        entity.setPayoutType(order.payoutType());
        entity.setStablecoin(order.stablecoin() != null ? order.stablecoin().ticker() : null);
        entity.setRedeemedAmount(order.redeemedAmount());
        entity.setTargetCurrency(order.targetCurrency());
        entity.setFiatAmount(order.fiatAmount());
        entity.setAppliedFxRate(order.appliedFxRate());
        entity.setRecipientId(order.recipientId());
        entity.setRecipientAccountHash(order.recipientAccountHash());
        entity.setPaymentRail(order.paymentRail());
        entity.setOffRampPartnerId(order.offRampPartner() != null ? order.offRampPartner().partnerId() : null);
        entity.setOffRampPartnerName(order.offRampPartner() != null ? order.offRampPartner().partnerName() : null);
        entity.setStatus(order.status());
        entity.setPartnerReference(order.partnerReference());
        entity.setPartnerSettledAt(order.partnerSettledAt());
        entity.setFailureReason(order.failureReason());
        entity.setErrorCode(order.errorCode());
        entity.setBankAccountNumber(order.bankAccount() != null ? order.bankAccount().accountNumber() : null);
        entity.setBankCode(order.bankAccount() != null ? order.bankAccount().bankCode() : null);
        entity.setBankAccountType(order.bankAccount() != null ? order.bankAccount().accountType().name() : null);
        entity.setBankCountry(order.bankAccount() != null ? order.bankAccount().country() : null);
        entity.setMobileMoneyProvider(order.mobileMoneyAccount() != null ? order.mobileMoneyAccount().provider().name() : null);
        entity.setMobileMoneyPhone(order.mobileMoneyAccount() != null ? order.mobileMoneyAccount().phoneNumber() : null);
        entity.setMobileMoneyCountry(order.mobileMoneyAccount() != null ? order.mobileMoneyAccount().country() : null);
        entity.setUpdatedAt(order.updatedAt());
    }
}
