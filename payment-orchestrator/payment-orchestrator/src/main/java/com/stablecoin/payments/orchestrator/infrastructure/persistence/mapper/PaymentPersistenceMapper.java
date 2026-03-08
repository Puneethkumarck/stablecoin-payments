package com.stablecoin.payments.orchestrator.infrastructure.persistence.mapper;

import com.stablecoin.payments.orchestrator.domain.model.ChainId;
import com.stablecoin.payments.orchestrator.domain.model.Corridor;
import com.stablecoin.payments.orchestrator.domain.model.FxRate;
import com.stablecoin.payments.orchestrator.domain.model.Money;
import com.stablecoin.payments.orchestrator.domain.model.Payment;
import com.stablecoin.payments.orchestrator.infrastructure.persistence.entity.PaymentEntity;
import org.mapstruct.Mapper;

import java.util.Map;

@Mapper
public interface PaymentPersistenceMapper {

    default PaymentEntity toEntity(Payment payment) {
        if (payment == null) {
            return null;
        }
        return PaymentEntity.builder()
                .paymentId(payment.paymentId())
                .idempotencyKey(payment.idempotencyKey())
                .correlationId(payment.correlationId())
                .state(payment.state())
                .senderId(payment.senderId())
                .recipientId(payment.recipientId())
                .sourceAmount(payment.sourceAmount() != null ? payment.sourceAmount().amount() : null)
                .sourceCurrency(payment.sourceCurrency())
                .targetCurrency(payment.targetCurrency())
                .targetAmount(payment.targetAmount() != null ? payment.targetAmount().amount() : null)
                .fxQuoteId(payment.lockedFxRate() != null ? payment.lockedFxRate().quoteId() : null)
                .lockedFxRate(payment.lockedFxRate() != null ? payment.lockedFxRate().rate() : null)
                .fxRateLockedAt(payment.lockedFxRate() != null ? payment.lockedFxRate().lockedAt() : null)
                .fxRateExpiresAt(payment.lockedFxRate() != null ? payment.lockedFxRate().expiresAt() : null)
                .fxRateProvider(payment.lockedFxRate() != null ? payment.lockedFxRate().provider() : null)
                .sourceCountry(payment.corridor() != null ? payment.corridor().sourceCountry() : null)
                .targetCountry(payment.corridor() != null ? payment.corridor().targetCountry() : null)
                .chainId(payment.chainSelected() != null ? payment.chainSelected().value() : null)
                .txHash(payment.txHash())
                .errorMessage(payment.failureReason())
                .metadata(payment.metadata() != null ? payment.metadata() : Map.of())
                .createdAt(payment.createdAt())
                .updatedAt(payment.updatedAt())
                .expiresAt(payment.expiresAt())
                .build();
    }

    default Payment toDomain(PaymentEntity entity) {
        if (entity == null) {
            return null;
        }

        Money sourceAmount = null;
        if (entity.getSourceAmount() != null && entity.getSourceCurrency() != null) {
            sourceAmount = new Money(entity.getSourceAmount(), entity.getSourceCurrency());
        }

        Money targetAmount = null;
        if (entity.getTargetAmount() != null && entity.getTargetCurrency() != null) {
            targetAmount = new Money(entity.getTargetAmount(), entity.getTargetCurrency());
        }

        FxRate fxRate = null;
        if (entity.getFxQuoteId() != null && entity.getLockedFxRate() != null) {
            fxRate = new FxRate(
                    entity.getFxQuoteId(),
                    entity.getSourceCurrency(),
                    entity.getTargetCurrency(),
                    entity.getLockedFxRate(),
                    entity.getFxRateLockedAt(),
                    entity.getFxRateExpiresAt(),
                    entity.getFxRateProvider()
            );
        }

        Corridor corridor = null;
        if (entity.getSourceCountry() != null && entity.getTargetCountry() != null) {
            corridor = new Corridor(entity.getSourceCountry(), entity.getTargetCountry());
        }

        ChainId chainId = null;
        if (entity.getChainId() != null) {
            chainId = new ChainId(entity.getChainId());
        }

        return new Payment(
                entity.getPaymentId(),
                entity.getIdempotencyKey(),
                entity.getCorrelationId(),
                entity.getState(),
                entity.getSenderId(),
                entity.getRecipientId(),
                sourceAmount,
                entity.getSourceCurrency(),
                entity.getTargetCurrency(),
                fxRate,
                targetAmount,
                corridor,
                chainId,
                entity.getTxHash(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getExpiresAt(),
                entity.getMetadata() != null ? entity.getMetadata() : Map.of()
        );
    }
}
