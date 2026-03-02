package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import java.util.Optional;

/**
 * Outbound port for company registry lookups. Used to validate merchant registration number against official registries
 * (Companies House for GB, SEC EDGAR for US).
 */
public interface CompanyRegistryProvider {

  /**
   * Looks up a company by registration number and country. Returns company profile data if found, empty if not found or
   * country not supported.
   */
  Optional<CompanyProfile> lookup(String registrationNumber, String country);

  record CompanyProfile(String companyName, String registrationNumber, String country, String companyStatus,
      String companyType, String dateOfCreation, String registeredOfficeAddress) {
  }
}
