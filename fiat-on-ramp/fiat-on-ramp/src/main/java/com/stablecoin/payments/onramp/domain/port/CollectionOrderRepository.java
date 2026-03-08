package com.stablecoin.payments.onramp.domain.port;

import com.stablecoin.payments.onramp.domain.model.CollectionOrder;

import java.util.Optional;
import java.util.UUID;

public interface CollectionOrderRepository {

    CollectionOrder save(CollectionOrder order);

    Optional<CollectionOrder> findById(UUID collectionId);

    Optional<CollectionOrder> findByPaymentId(UUID paymentId);

    Optional<CollectionOrder> findByPspReference(String pspReference);
}
