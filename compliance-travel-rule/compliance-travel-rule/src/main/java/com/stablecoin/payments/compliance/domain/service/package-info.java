/**
 * Domain services for compliance orchestration.
 * <p>
 * {@link com.stablecoin.payments.compliance.domain.service.ComplianceCheckService} orchestrates the
 * sub-check pipeline (KYC, sanctions, AML, risk scoring, travel rule).
 * <p>
 * {@link com.stablecoin.payments.compliance.domain.service.RiskScoringService} calculates risk scores
 * from multiple factors.
 */
package com.stablecoin.payments.compliance.domain.service;
