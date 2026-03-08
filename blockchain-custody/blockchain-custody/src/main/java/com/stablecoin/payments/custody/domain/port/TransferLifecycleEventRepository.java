package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.TransferLifecycleEvent;

import java.util.List;
import java.util.UUID;

public interface TransferLifecycleEventRepository {

    TransferLifecycleEvent save(TransferLifecycleEvent event);

    List<TransferLifecycleEvent> findByTransferId(UUID transferId);
}
