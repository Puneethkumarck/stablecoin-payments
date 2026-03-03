package com.stablecoin.payments.merchant.iam.domain.statemachine;

public record StateTransition<S, T>(S from, T trigger, S to) {}
