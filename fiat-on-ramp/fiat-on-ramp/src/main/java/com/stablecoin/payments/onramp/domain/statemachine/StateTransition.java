package com.stablecoin.payments.onramp.domain.statemachine;

public record StateTransition<S, T>(S from, T trigger, S to) {
}
