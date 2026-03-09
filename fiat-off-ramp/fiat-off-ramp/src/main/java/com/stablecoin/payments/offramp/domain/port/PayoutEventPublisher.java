package com.stablecoin.payments.offramp.domain.port;

public interface PayoutEventPublisher {

    void publish(Object event);
}
