package com.stablecoin.payments.merchant.onboarding.infrastructure.kyb;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "companies-house")
public record CompaniesHouseProperties(String apiKey, String baseUrl) {

  public CompaniesHouseProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://api.company-information.service.gov.uk";
    }
  }
}
