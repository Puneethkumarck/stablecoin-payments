package com.stablecoin.payments.custody.domain.statemachine;

public record StateTransition<S, T>(S from, T trigger, S to) {}
