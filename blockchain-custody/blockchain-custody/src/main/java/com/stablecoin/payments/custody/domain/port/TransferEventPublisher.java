package com.stablecoin.payments.custody.domain.port;

public interface TransferEventPublisher {

    void publish(Object event);
}
