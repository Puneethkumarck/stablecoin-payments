package com.stablecoin.payments.merchant.iam.domain;

public interface EventPublisher<T> {
    void publish(T event);
}
