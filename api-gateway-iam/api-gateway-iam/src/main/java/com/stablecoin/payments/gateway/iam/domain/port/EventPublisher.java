package com.stablecoin.payments.gateway.iam.domain.port;

public interface EventPublisher<T> {

    void publish(T event);
}
