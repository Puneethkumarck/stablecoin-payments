package com.stablecoin.payments.compliance.domain.port;

public interface EventPublisher<T> {
    void publish(T event);
}
