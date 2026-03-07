package com.stablecoin.payments.compliance.domain.statemachine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StateMachine")
class StateMachineTest {

    private StateMachine<String, String> stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new StateMachine<>(List.of(
                new StateTransition<>("IDLE", "START", "RUNNING"),
                new StateTransition<>("RUNNING", "STOP", "STOPPED"),
                new StateTransition<>("RUNNING", "PAUSE", "PAUSED"),
                new StateTransition<>("PAUSED", "RESUME", "RUNNING")
        ));
    }

    @Nested
    @DisplayName("transition()")
    class Transition {

        @Test
        @DisplayName("should return correct next state for valid transition")
        void should_returnNextState_when_transitionIsValid() {
            var result = stateMachine.transition("IDLE", "START");

            assertThat(result).isEqualTo("RUNNING");
        }

        @Test
        @DisplayName("should return correct state for multi-step transitions")
        void should_supportMultiStepTransitions() {
            var running = stateMachine.transition("IDLE", "START");
            var paused = stateMachine.transition(running, "PAUSE");
            var resumed = stateMachine.transition(paused, "RESUME");

            assertThat(resumed).isEqualTo("RUNNING");
        }

        @Test
        @DisplayName("should throw StateMachineException for invalid transition")
        void should_throwStateMachineException_when_transitionIsInvalid() {
            assertThatThrownBy(() -> stateMachine.transition("IDLE", "STOP"))
                    .isInstanceOf(StateMachineException.class)
                    .hasMessageContaining("IDLE")
                    .hasMessageContaining("STOP");
        }

        @Test
        @DisplayName("should throw StateMachineException for unknown state")
        void should_throwStateMachineException_when_stateIsUnknown() {
            assertThatThrownBy(() -> stateMachine.transition("UNKNOWN", "START"))
                    .isInstanceOf(StateMachineException.class)
                    .hasMessageContaining("UNKNOWN");
        }

        @Test
        @DisplayName("should throw StateMachineException for unknown trigger")
        void should_throwStateMachineException_when_triggerIsUnknown() {
            assertThatThrownBy(() -> stateMachine.transition("IDLE", "UNKNOWN"))
                    .isInstanceOf(StateMachineException.class)
                    .hasMessageContaining("UNKNOWN");
        }
    }

    @Nested
    @DisplayName("canTransition()")
    class CanTransition {

        @Test
        @DisplayName("should return true for valid transition")
        void should_returnTrue_when_transitionIsValid() {
            assertThat(stateMachine.canTransition("IDLE", "START")).isTrue();
        }

        @Test
        @DisplayName("should return true for another valid transition")
        void should_returnTrue_when_anotherValidTransition() {
            assertThat(stateMachine.canTransition("RUNNING", "PAUSE")).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid transition")
        void should_returnFalse_when_transitionIsInvalid() {
            assertThat(stateMachine.canTransition("IDLE", "STOP")).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown state")
        void should_returnFalse_when_stateIsUnknown() {
            assertThat(stateMachine.canTransition("UNKNOWN", "START")).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown trigger")
        void should_returnFalse_when_triggerIsUnknown() {
            assertThat(stateMachine.canTransition("IDLE", "NONEXISTENT")).isFalse();
        }

        @Test
        @DisplayName("should return false for stopped state with no outgoing transitions")
        void should_returnFalse_when_noOutgoingTransitions() {
            assertThat(stateMachine.canTransition("STOPPED", "START")).isFalse();
        }
    }
}
