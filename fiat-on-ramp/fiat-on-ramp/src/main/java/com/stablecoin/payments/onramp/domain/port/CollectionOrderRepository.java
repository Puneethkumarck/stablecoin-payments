package com.stablecoin.payments.onramp.domain.port;

import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.CollectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionOrderRepository {

    CollectionOrder save(CollectionOrder order);

    Optional<CollectionOrder> findById(UUID collectionId);

    Optional<CollectionOrder> findByPaymentId(UUID paymentId);

    Optional<CollectionOrder> findByPspReference(String pspReference);

    List<CollectionOrder> findByStatusAndNotReconciled(CollectionStatus status);

    List<CollectionOrder> findExpiredByStatus(CollectionStatus status, Instant before);
}
