/**
 * Generic state machine for {@code ComplianceCheck} status transitions.
 * <p>
 * {@link com.stablecoin.payments.compliance.domain.statemachine.StateMachine} validates
 * transitions between {@link com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus}
 * states using {@link com.stablecoin.payments.compliance.domain.model.ComplianceCheckTrigger} triggers.
 *
 * @see com.stablecoin.payments.compliance.domain.model.ComplianceCheck
 */
package com.stablecoin.payments.compliance.domain.statemachine;
