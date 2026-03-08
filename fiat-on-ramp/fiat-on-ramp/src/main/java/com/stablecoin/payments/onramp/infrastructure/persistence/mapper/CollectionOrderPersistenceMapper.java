package com.stablecoin.payments.onramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.onramp.domain.model.AccountType;
import com.stablecoin.payments.onramp.domain.model.BankAccount;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.PaymentRail;
import com.stablecoin.payments.onramp.domain.model.PaymentRailType;
import com.stablecoin.payments.onramp.domain.model.PspIdentifier;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.CollectionOrderEntity;
import org.mapstruct.Mapper;

@Mapper
public interface CollectionOrderPersistenceMapper {

    default CollectionOrderEntity toEntity(CollectionOrder order) {
        if (order == null) {
            return null;
        }
        return CollectionOrderEntity.builder()
                .collectionId(order.collectionId())
                .paymentId(order.paymentId())
                .correlationId(order.correlationId())
                .amount(order.amount() != null ? order.amount().amount() : null)
                .currency(order.amount() != null ? order.amount().currency() : null)
                .sourceCountry(order.paymentRail() != null ? order.paymentRail().country() : null)
                .paymentRail(order.paymentRail() != null ? order.paymentRail().rail().name() : null)
                .pspName(order.psp() != null ? order.psp().pspName() : null)
                .pspId(order.psp() != null ? order.psp().pspId() : null)
                .pspReference(order.pspReference())
                .senderAccountHash(order.senderAccount() != null ? order.senderAccount().accountNumberHash() : null)
                .senderBankCode(order.senderAccount() != null ? order.senderAccount().bankCode() : null)
                .senderAccountType(order.senderAccount() != null ? order.senderAccount().accountType().name() : null)
                .senderCountry(order.senderAccount() != null ? order.senderAccount().country() : null)
                .status(order.status())
                .settledAmount(order.collectedAmount() != null ? order.collectedAmount().amount() : null)
                .settledAt(order.pspSettledAt())
                .failureReason(order.failureReason())
                .errorCode(order.errorCode())
                .expiresAt(order.expiresAt())
                .createdAt(order.createdAt())
                .updatedAt(order.updatedAt())
                .build();
    }

    default CollectionOrder toDomain(CollectionOrderEntity entity) {
        if (entity == null) {
            return null;
        }

        Money amount = null;
        if (entity.getAmount() != null && entity.getCurrency() != null) {
            amount = new Money(entity.getAmount(), entity.getCurrency());
        }

        PaymentRail paymentRail = null;
        if (entity.getPaymentRail() != null && entity.getSourceCountry() != null && entity.getCurrency() != null) {
            paymentRail = new PaymentRail(
                    PaymentRailType.valueOf(entity.getPaymentRail()),
                    entity.getSourceCountry(),
                    entity.getCurrency()
            );
        }

        PspIdentifier psp = null;
        if (entity.getPspId() != null && entity.getPspName() != null) {
            psp = new PspIdentifier(entity.getPspId(), entity.getPspName());
        }

        BankAccount senderAccount = null;
        if (entity.getSenderAccountHash() != null && entity.getSenderBankCode() != null
                && entity.getSenderAccountType() != null && entity.getSenderCountry() != null) {
            senderAccount = new BankAccount(
                    entity.getSenderAccountHash(),
                    entity.getSenderBankCode(),
                    AccountType.valueOf(entity.getSenderAccountType()),
                    entity.getSenderCountry()
            );
        }

        Money collectedAmount = null;
        if (entity.getSettledAmount() != null && entity.getCurrency() != null) {
            collectedAmount = new Money(entity.getSettledAmount(), entity.getCurrency());
        }

        return new CollectionOrder(
                entity.getCollectionId(),
                entity.getPaymentId(),
                entity.getCorrelationId(),
                amount,
                paymentRail,
                psp,
                senderAccount,
                entity.getStatus(),
                collectedAmount,
                entity.getPspReference(),
                entity.getSettledAt(),
                entity.getFailureReason(),
                entity.getErrorCode(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getExpiresAt()
        );
    }
}
