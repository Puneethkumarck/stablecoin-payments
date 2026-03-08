package com.stablecoin.payments.onramp.domain.port;

public interface CollectionEventPublisher {

    void publish(Object event);
}
