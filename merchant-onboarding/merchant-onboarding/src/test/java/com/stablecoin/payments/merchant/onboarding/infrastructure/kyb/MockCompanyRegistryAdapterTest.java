package com.stablecoin.payments.merchant.onboarding.infrastructure.kyb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockCompanyRegistryAdapter")
class MockCompanyRegistryAdapterTest {

  private final MockCompanyRegistryAdapter adapter = new MockCompanyRegistryAdapter();

  @Test
  @DisplayName("should return mock company profile for any registration")
  void shouldReturnMockCompanyProfile() {
    var result = adapter.lookup("12345678", "GB");

    assertThat(result).isPresent();
    var profile = result.get();
    assertThat(profile.companyName()).isEqualTo("Mock Company Ltd");
    assertThat(profile.registrationNumber()).isEqualTo("12345678");
    assertThat(profile.country()).isEqualTo("GB");
    assertThat(profile.companyStatus()).isEqualTo("active");
  }

  @Test
  @DisplayName("should echo back registration number and country")
  void shouldEchoBackRegistrationNumberAndCountry() {
    var result = adapter.lookup("US-98765", "US");

    assertThat(result).isPresent();
    assertThat(result.get().registrationNumber()).isEqualTo("US-98765");
    assertThat(result.get().country()).isEqualTo("US");
  }

  @Test
  @DisplayName("should always return present result")
  void shouldAlwaysReturnPresentResult() {
    assertThat(adapter.lookup("any", "any")).isPresent();
    assertThat(adapter.lookup("", "")).isPresent();
  }
}
