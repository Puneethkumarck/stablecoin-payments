package com.stablecoin.payments.orchestrator.domain.statemachine;

public record StateTransition<S, T>(S from, T trigger, S to) {}
