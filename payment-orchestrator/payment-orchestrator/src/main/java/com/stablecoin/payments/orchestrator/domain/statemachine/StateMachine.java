package com.stablecoin.payments.orchestrator.domain.statemachine;

import java.util.List;

public class StateMachine<S, T> {

    private final List<StateTransition<S, T>> transitions;

    public StateMachine(List<StateTransition<S, T>> transitions) {
        this.transitions = List.copyOf(transitions);
    }

    public S transition(S currentState, T trigger) {
        return transitions.stream()
                .filter(t -> t.from().equals(currentState) && t.trigger().equals(trigger))
                .map(StateTransition::to)
                .findFirst()
                .orElseThrow(() -> StateMachineException.invalidTransition(currentState, trigger));
    }

    public boolean canTransition(S currentState, T trigger) {
        return transitions.stream()
                .anyMatch(t -> t.from().equals(currentState) && t.trigger().equals(trigger));
    }
}
