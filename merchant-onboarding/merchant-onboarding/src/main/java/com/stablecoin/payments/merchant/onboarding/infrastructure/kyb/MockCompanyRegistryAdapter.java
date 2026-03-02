package com.stablecoin.payments.merchant.onboarding.infrastructure.kyb;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.CompanyRegistryProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * In-memory company registry for local development and fallback. Registered as a bean only when no other
 * {@link CompanyRegistryProvider} is available.
 *
 * @see FallbackAdaptersConfig
 */
@Slf4j
public class MockCompanyRegistryAdapter implements CompanyRegistryProvider {

  @Override
  public Optional<CompanyProfile> lookup(String registrationNumber, String country) {
    log.debug("[MOCK-REGISTRY] Looking up company registrationNumber={} country={}", registrationNumber, country);
    return Optional.of(new CompanyProfile("Mock Company Ltd", registrationNumber, country, "active", "ltd",
        "2020-01-01", "10 Mock Street, London, EC1A 1BB"));
  }
}
