package com.stablecoin.payments.merchant.onboarding.infrastructure.kyb;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.CompanyRegistryProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Companies House API adapter — free, real UK company data.
 * <p>
 * API docs: <a href="https://developer.company-information.service.gov.uk/">Companies House Developer Hub</a> Rate
 * limit: 600 requests per 5-minute window. Authentication: Basic auth with API key as username, empty password.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.company-registry.provider", havingValue = "companies-house")
public class CompaniesHouseAdapter implements CompanyRegistryProvider {

  private final RestClient restClient;

  public CompaniesHouseAdapter(CompaniesHouseProperties properties) {
    var basicAuth = Base64.getEncoder().encodeToString((properties.apiKey() + ":").getBytes(StandardCharsets.UTF_8));

    this.restClient = RestClient.builder().baseUrl(properties.baseUrl())
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth).build();
  }

  @Override
  public Optional<CompanyProfile> lookup(String registrationNumber, String country) {
    if (!"GB".equalsIgnoreCase(country)) {
      log.debug("[COMPANIES-HOUSE] Skipping non-GB country={}", country);
      return Optional.empty();
    }

    log.info("[COMPANIES-HOUSE] Looking up company registrationNumber={}", registrationNumber);

    try {
      @SuppressWarnings("unchecked")
      var response = restClient.get().uri("/company/{companyNumber}", registrationNumber).retrieve().body(Map.class);

      if (response == null) {
        return Optional.empty();
      }

      var companyName = (String) response.get("company_name");
      var companyNumber = (String) response.get("company_number");
      var companyStatus = (String) response.get("company_status");
      var companyType = (String) response.get("type");
      var dateOfCreation = (String) response.get("date_of_creation");

      @SuppressWarnings("unchecked")
      var address = (Map<String, Object>) response.get("registered_office_address");
      var addressStr = formatAddress(address);

      log.info("[COMPANIES-HOUSE] Found company={} status={}", companyName, companyStatus);

      return Optional.of(
          new CompanyProfile(companyName, companyNumber, "GB", companyStatus, companyType, dateOfCreation, addressStr));
    } catch (Exception ex) {
      log.error("[COMPANIES-HOUSE] Lookup failed registrationNumber={}", registrationNumber, ex);
      return Optional.empty();
    }
  }

  private String formatAddress(Map<String, Object> address) {
    if (address == null) {
      return null;
    }
    var sb = new StringBuilder();
    appendIfPresent(sb, address, "address_line_1");
    appendIfPresent(sb, address, "address_line_2");
    appendIfPresent(sb, address, "locality");
    appendIfPresent(sb, address, "region");
    appendIfPresent(sb, address, "postal_code");
    appendIfPresent(sb, address, "country");
    return sb.toString().replaceAll(", $", "");
  }

  private void appendIfPresent(StringBuilder sb, Map<String, Object> map, String key) {
    var val = map.get(key);
    if (val != null && !val.toString().isBlank()) {
      if (!sb.isEmpty()) {
        sb.append(", ");
      }
      sb.append(val);
    }
  }
}
