package com.stablecoin.payments.onramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.onramp.domain.model.Refund;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.RefundEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface RefundEntityUpdater {

    default void updateEntity(@MappingTarget RefundEntity entity, Refund refund) {
        if (refund == null) {
            return;
        }
        entity.setStatus(refund.status());
        entity.setPspRefundRef(refund.pspRefundRef());
        entity.setFailureReason(refund.failureReason());
        entity.setCompletedAt(refund.completedAt());
    }
}
