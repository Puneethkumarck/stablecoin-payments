/**
 * Domain services for compliance orchestration.
 * <p>
 * {@link com.stablecoin.payments.compliance.domain.service.ComplianceCheckCommandHandler} orchestrates
 * the full compliance pipeline (KYC, sanctions, AML, risk scoring, travel rule), coordinates
 * persistence and event publishing through domain ports.
 * <p>
 * {@link com.stablecoin.payments.compliance.domain.service.ComplianceCheckService} provides
 * state-transition helper methods for individual sub-checks.
 * <p>
 * {@link com.stablecoin.payments.compliance.domain.service.RiskScoringService} calculates risk scores
 * from multiple factors.
 */
package com.stablecoin.payments.compliance.domain.service;
