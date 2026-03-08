package com.stablecoin.payments.orchestrator.infrastructure.persistence.mapper;

import com.stablecoin.payments.orchestrator.domain.model.Payment;
import com.stablecoin.payments.orchestrator.infrastructure.persistence.entity.PaymentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Map;

@Mapper
public interface PaymentEntityUpdater {

    default void updateEntity(@MappingTarget PaymentEntity entity, Payment payment) {
        if (payment == null) {
            return;
        }
        entity.setCorrelationId(payment.correlationId());
        entity.setState(payment.state());
        entity.setSenderId(payment.senderId());
        entity.setRecipientId(payment.recipientId());
        entity.setSourceAmount(payment.sourceAmount() != null ? payment.sourceAmount().amount() : null);
        entity.setSourceCurrency(payment.sourceCurrency());
        entity.setTargetCurrency(payment.targetCurrency());
        entity.setTargetAmount(payment.targetAmount() != null ? payment.targetAmount().amount() : null);
        entity.setFxQuoteId(payment.lockedFxRate() != null ? payment.lockedFxRate().quoteId() : null);
        entity.setLockedFxRate(payment.lockedFxRate() != null ? payment.lockedFxRate().rate() : null);
        entity.setFxRateLockedAt(payment.lockedFxRate() != null ? payment.lockedFxRate().lockedAt() : null);
        entity.setFxRateExpiresAt(payment.lockedFxRate() != null ? payment.lockedFxRate().expiresAt() : null);
        entity.setFxRateProvider(payment.lockedFxRate() != null ? payment.lockedFxRate().provider() : null);
        entity.setSourceCountry(payment.corridor() != null ? payment.corridor().sourceCountry() : null);
        entity.setTargetCountry(payment.corridor() != null ? payment.corridor().targetCountry() : null);
        entity.setChainId(payment.chainSelected() != null ? payment.chainSelected().value() : null);
        entity.setTxHash(payment.txHash());
        entity.setErrorMessage(payment.failureReason());
        entity.setMetadata(payment.metadata() != null ? payment.metadata() : Map.of());
        entity.setUpdatedAt(payment.updatedAt());
        entity.setExpiresAt(payment.expiresAt());
    }
}
