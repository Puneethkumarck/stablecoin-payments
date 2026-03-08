/**
 * Generic state machine for {@code Payment} state transitions.
 * <p>
 * {@link com.stablecoin.payments.orchestrator.domain.statemachine.StateMachine} validates
 * transitions between {@link com.stablecoin.payments.orchestrator.domain.model.PaymentState}
 * states using {@link com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger} triggers.
 *
 * @see com.stablecoin.payments.orchestrator.domain.model.Payment
 */
package com.stablecoin.payments.orchestrator.domain.statemachine;
