package com.stablecoin.payments.merchant.onboarding.application.config;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.CompanyRegistryProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.DocumentStore;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.KybProvider;
import com.stablecoin.payments.merchant.onboarding.infrastructure.document.MockDocumentStoreAdapter;
import com.stablecoin.payments.merchant.onboarding.infrastructure.kyb.MockCompanyRegistryAdapter;
import com.stablecoin.payments.merchant.onboarding.infrastructure.kyb.MockKybAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers fallback (mock/in-memory) adapters for outbound ports when no real implementation is available.
 * <p>
 * {@code @ConditionalOnMissingBean} on {@code @Bean} methods is evaluated <b>after</b> all component scanning, so it
 * reliably detects real adapters such as {@code @Profile("sandbox")} beans.
 * <p>
 * <b>When does each adapter win?</b>
 * <ul>
 * <li>Default / local / test — no real adapter scanned → fallback registered</li>
 * <li>Sandbox profile — {@code OnfidoKybAdapter} + {@code CompaniesHouseAdapter} scanned → fallbacks skipped</li>
 * <li>Integration tests — {@code @MockBean} overrides → fallbacks skipped</li>
 * </ul>
 */
@Configuration
public class FallbackAdaptersConfig {

  @Bean
  @ConditionalOnMissingBean(KybProvider.class)
  KybProvider mockKybProvider() {
    return new MockKybAdapter();
  }

  @Bean
  @ConditionalOnMissingBean(CompanyRegistryProvider.class)
  CompanyRegistryProvider mockCompanyRegistryProvider() {
    return new MockCompanyRegistryAdapter();
  }

  @Bean
  @ConditionalOnMissingBean(DocumentStore.class)
  DocumentStore mockDocumentStore() {
    return new MockDocumentStoreAdapter();
  }
}
