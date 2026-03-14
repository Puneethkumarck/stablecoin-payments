package com.stablecoin.payments.merchant.onboarding.application.config;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.CompanyRegistryProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.DocumentStore;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.KybProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.OnboardingWorkflowPort;
import com.stablecoin.payments.merchant.onboarding.infrastructure.document.MockDocumentStoreAdapter;
import com.stablecoin.payments.merchant.onboarding.infrastructure.kyb.MockCompanyRegistryAdapter;
import com.stablecoin.payments.merchant.onboarding.infrastructure.kyb.MockKybAdapter;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.adapter.MockOnboardingWorkflowAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers fallback (mock/in-memory) adapters for outbound ports when no real implementation is available.
 * <p>
 * Activated when {@code app.fallback-adapters.enabled=true}. In production, real adapters are activated
 * via their own {@code @ConditionalOnProperty} flags and this config is not loaded.
 * <p>
 * <b>When does each adapter win?</b>
 * <ul>
 * <li>Local dev / integration tests — {@code app.fallback-adapters.enabled=true} → fallbacks registered</li>
 * <li>Production — property absent or {@code false} → real adapters used instead</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "true")
public class FallbackAdaptersConfig {

  @Bean
  KybProvider mockKybProvider() {
    return new MockKybAdapter();
  }

  @Bean
  CompanyRegistryProvider mockCompanyRegistryProvider() {
    return new MockCompanyRegistryAdapter();
  }

  @Bean
  DocumentStore mockDocumentStore() {
    return new MockDocumentStoreAdapter();
  }

  @Bean
  OnboardingWorkflowPort mockOnboardingWorkflow() {
    return new MockOnboardingWorkflowAdapter();
  }
}
