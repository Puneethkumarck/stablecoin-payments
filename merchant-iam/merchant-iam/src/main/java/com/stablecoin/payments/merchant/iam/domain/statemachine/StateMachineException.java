package com.stablecoin.payments.merchant.iam.domain.statemachine;

public class StateMachineException extends RuntimeException {

    private StateMachineException(String message) {
        super(message);
    }

    public static <S, T> StateMachineException invalidTransition(S state, T trigger) {
        return new StateMachineException(
                "Invalid transition: state=%s trigger=%s".formatted(state, trigger));
    }
}
