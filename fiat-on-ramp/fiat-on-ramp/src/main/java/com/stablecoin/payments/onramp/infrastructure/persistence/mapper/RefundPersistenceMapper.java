package com.stablecoin.payments.onramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.Refund;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.RefundEntity;
import org.mapstruct.Mapper;

@Mapper
public interface RefundPersistenceMapper {

    default RefundEntity toEntity(Refund refund) {
        if (refund == null) {
            return null;
        }
        return RefundEntity.builder()
                .refundId(refund.refundId())
                .collectionId(refund.collectionId())
                .paymentId(refund.paymentId())
                .refundAmount(refund.refundAmount() != null ? refund.refundAmount().amount() : null)
                .currency(refund.refundAmount() != null ? refund.refundAmount().currency() : null)
                .reason(refund.reason())
                .status(refund.status())
                .pspRefundRef(refund.pspRefundRef())
                .failureReason(refund.failureReason())
                .initiatedAt(refund.initiatedAt())
                .completedAt(refund.completedAt())
                .build();
    }

    default Refund toDomain(RefundEntity entity) {
        if (entity == null) {
            return null;
        }

        Money refundAmount = null;
        if (entity.getRefundAmount() != null && entity.getCurrency() != null) {
            refundAmount = new Money(entity.getRefundAmount(), entity.getCurrency());
        }

        return new Refund(
                entity.getRefundId(),
                entity.getCollectionId(),
                entity.getPaymentId(),
                refundAmount,
                entity.getReason(),
                entity.getStatus(),
                entity.getPspRefundRef(),
                entity.getInitiatedAt(),
                entity.getCompletedAt(),
                entity.getFailureReason()
        );
    }
}
