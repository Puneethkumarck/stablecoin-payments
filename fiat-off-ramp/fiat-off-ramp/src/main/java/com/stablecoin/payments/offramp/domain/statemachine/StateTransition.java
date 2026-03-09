package com.stablecoin.payments.offramp.domain.statemachine;

public record StateTransition<S, T>(S from, T trigger, S to) {}
