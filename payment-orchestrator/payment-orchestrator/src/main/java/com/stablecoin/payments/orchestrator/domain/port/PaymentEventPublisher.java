package com.stablecoin.payments.orchestrator.domain.port;

public interface PaymentEventPublisher {

    void publish(Object event);
}
