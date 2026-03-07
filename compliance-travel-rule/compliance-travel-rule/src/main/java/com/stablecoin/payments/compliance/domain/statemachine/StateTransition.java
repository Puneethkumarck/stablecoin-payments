package com.stablecoin.payments.compliance.domain.statemachine;

public record StateTransition<S, T>(S from, T trigger, S to) {}
