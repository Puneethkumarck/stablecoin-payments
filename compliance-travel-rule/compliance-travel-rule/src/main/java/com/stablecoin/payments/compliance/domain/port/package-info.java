/**
 * Outbound port interfaces for the Compliance domain.
 * <p>
 * Persistence ports: {@link com.stablecoin.payments.compliance.domain.port.ComplianceCheckRepository},
 * {@link com.stablecoin.payments.compliance.domain.port.CustomerRiskProfileRepository}.
 * <p>
 * Provider ports (Anti-Corruption Layer): {@link com.stablecoin.payments.compliance.domain.port.KycProvider},
 * {@link com.stablecoin.payments.compliance.domain.port.SanctionsProvider},
 * {@link com.stablecoin.payments.compliance.domain.port.AmlProvider},
 * {@link com.stablecoin.payments.compliance.domain.port.TravelRuleProvider}.
 * <p>
 * Event port: {@link com.stablecoin.payments.compliance.domain.port.EventPublisher}.
 */
package com.stablecoin.payments.compliance.domain.port;
