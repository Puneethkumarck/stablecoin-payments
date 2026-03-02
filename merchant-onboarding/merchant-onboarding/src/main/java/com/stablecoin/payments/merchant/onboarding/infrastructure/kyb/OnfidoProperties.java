package com.stablecoin.payments.merchant.onboarding.infrastructure.kyb;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "onfido")
public record OnfidoProperties(String apiToken, String baseUrl, String webhookSecret, String region) {

  public OnfidoProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://api.eu.onfido.com/v3.6";
    }
    if (region == null || region.isBlank()) {
      region = "EU";
    }
  }
}
