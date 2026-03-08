package com.stablecoin.payments.onramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.CollectionOrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface CollectionOrderEntityUpdater {

    default void updateEntity(@MappingTarget CollectionOrderEntity entity, CollectionOrder order) {
        if (order == null) {
            return;
        }
        entity.setPaymentId(order.paymentId());
        entity.setCorrelationId(order.correlationId());
        entity.setAmount(order.amount() != null ? order.amount().amount() : null);
        entity.setCurrency(order.amount() != null ? order.amount().currency() : null);
        entity.setSourceCountry(order.paymentRail() != null ? order.paymentRail().country() : null);
        entity.setPaymentRail(order.paymentRail() != null ? order.paymentRail().rail().name() : null);
        entity.setPspName(order.psp() != null ? order.psp().pspName() : null);
        entity.setPspId(order.psp() != null ? order.psp().pspId() : null);
        entity.setPspReference(order.pspReference());
        entity.setSenderAccountHash(order.senderAccount() != null ? order.senderAccount().accountNumberHash() : null);
        entity.setSenderBankCode(order.senderAccount() != null ? order.senderAccount().bankCode() : null);
        entity.setSenderAccountType(order.senderAccount() != null ? order.senderAccount().accountType().name() : null);
        entity.setSenderCountry(order.senderAccount() != null ? order.senderAccount().country() : null);
        entity.setStatus(order.status());
        entity.setSettledAmount(order.collectedAmount() != null ? order.collectedAmount().amount() : null);
        entity.setSettledAt(order.pspSettledAt());
        entity.setFailureReason(order.failureReason());
        entity.setErrorCode(order.errorCode());
        entity.setExpiresAt(order.expiresAt());
        entity.setUpdatedAt(order.updatedAt());
    }
}
