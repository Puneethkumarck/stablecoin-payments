package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity;

import com.stablecoin.payments.merchant.onboarding.domain.exceptions.MerchantNotFoundException;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.CompanyRegistryProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.KybProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.MerchantRepository;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.RiskTierCalculator;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RiskTier;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.KybResultSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantOnboardingActivitiesImpl implements MerchantOnboardingActivities {

  private final MerchantRepository merchantRepository;
  private final KybProvider kybProvider;
  private final RiskTierCalculator riskTierCalculator;
  private final CompanyRegistryProvider companyRegistryProvider;

  @Override
  @Transactional(readOnly = true)
  public String verifyCompanyRegistry(UUID merchantId) {
    var merchant = merchantRepository.findById(merchantId)
        .orElseThrow(() -> MerchantNotFoundException.withId(merchantId));

    var result = companyRegistryProvider.lookup(merchant.getRegistrationNumber(), merchant.getRegistrationCountry());

    if (result.isEmpty()) {
      log.info("[ACTIVITY] Company registry lookup returned empty merchantId={} country={}", merchantId,
          merchant.getRegistrationCountry());
      return "NOT_FOUND";
    }

    var profile = result.get();
    log.info("[ACTIVITY] Company registry verified merchantId={} companyName={} status={}", merchantId,
        profile.companyName(), profile.companyStatus());
    return profile.companyStatus();
  }

  @Override
  @Transactional
  public String startKyb(UUID merchantId) {
    var merchant = merchantRepository.findById(merchantId)
        .orElseThrow(() -> MerchantNotFoundException.withId(merchantId));

    merchant.startKyb();

    var kyb = kybProvider.submit(merchant.getMerchantId(), merchant.getLegalName(), merchant.getRegistrationNumber(),
        merchant.getRegistrationCountry());

    merchantRepository.save(merchant);
    log.info("[ACTIVITY] KYB started merchantId={} providerRef={}", merchantId, kyb.providerRef());
    return kyb.providerRef();
  }

  @Override
  @Transactional
  public void processKybResult(UUID merchantId, KybResultSignal kybResult) {
    var merchant = merchantRepository.findById(merchantId)
        .orElseThrow(() -> MerchantNotFoundException.withId(merchantId));

    var status = KybStatus.valueOf(kybResult.status());
    if (status == KybStatus.PASSED) {
      var riskTier = riskTierCalculator.calculate(kybResult.riskSignals());
      merchant.kybPassed(riskTier);
    } else if (status == KybStatus.FAILED) {
      merchant.kybFailed();
    } else if (status == KybStatus.MANUAL_REVIEW) {
      merchant.kybFlaggedForManualReview();
    }

    merchantRepository.save(merchant);
    log.info("[ACTIVITY] KYB result processed merchantId={} status={}", merchantId, status);
  }

  @Override
  public String calculateRiskTier(Map<String, Object> riskSignals) {
    var tier = riskTierCalculator.calculate(riskSignals);
    log.info("[ACTIVITY] Risk tier calculated tier={}", tier);
    return tier.name();
  }

  @Override
  @Transactional
  public void markKybPassed(UUID merchantId, String riskTier) {
    var merchant = merchantRepository.findById(merchantId)
        .orElseThrow(() -> MerchantNotFoundException.withId(merchantId));

    merchant.kybPassed(RiskTier.valueOf(riskTier));
    merchantRepository.save(merchant);
    log.info("[ACTIVITY] Merchant KYB passed merchantId={} riskTier={}", merchantId, riskTier);
  }

  @Override
  @Transactional
  public void rejectMerchant(UUID merchantId, String reason) {
    var merchant = merchantRepository.findById(merchantId)
        .orElseThrow(() -> MerchantNotFoundException.withId(merchantId));

    merchant.kybFailed();
    merchantRepository.save(merchant);
    log.info("[ACTIVITY] Merchant rejected merchantId={} reason={}", merchantId, reason);
  }

  @Override
  public void notifyOpsTeam(UUID merchantId) {
    log.info("[ACTIVITY] Notifying ops team for manual review merchantId={}", merchantId);
    // TODO: integrate with S9 Notifications service
  }

  @Override
  public void sendDocumentReminder(UUID merchantId, List<String> missingDocumentTypes) {
    log.info("[ACTIVITY] Sending document reminder merchantId={} missing={}", merchantId, missingDocumentTypes);
    // TODO: integrate with S9 Notifications service
  }

  @Override
  public void escalateReview(UUID merchantId) {
    log.info("[ACTIVITY] Escalating manual review merchantId={}", merchantId);
    // TODO: integrate with S9 Notifications service
  }
}
